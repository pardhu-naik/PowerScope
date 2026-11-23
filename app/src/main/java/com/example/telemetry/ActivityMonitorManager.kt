package com.example.telemetry

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Build
import com.example.data.model.AppActivityRecord
import com.example.util.PermissionHelper
import java.util.Locale

class ActivityMonitorManager(private val context: Context) {

    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val networkStatsManager: NetworkStatsManager? =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    private val packageManager = context.packageManager

    private val majorSystemPackages = setOf(
        "com.android.systemui",
        "com.google.android.gms",
        "com.android.bluetooth",
        "com.android.phone",
        "com.android.vending",
        "com.google.android.youtube",
        "com.android.chrome"
    )

    fun queryWindowActivity(windowStart: Long, windowEnd: Long): List<AppActivityRecord> {
        if (windowEnd <= windowStart || !PermissionHelper.hasUsageStatsPermission(context)) {
            return emptyList()
        }

        val defaultLauncher = resolveDefaultLauncherPackage()
        val foregroundMap = mutableMapOf<String, Long>()
        val backgroundServiceMap = mutableMapOf<String, Long>()

        collectUsageEvents(windowStart, windowEnd, foregroundMap, backgroundServiceMap)

        val aggregated = usageStatsManager?.queryAndAggregateUsageStats(windowStart, windowEnd).orEmpty()
        val windowDuration = (windowEnd - windowStart).coerceAtLeast(1L)

        for ((pkg, stats) in aggregated) {
            if (!foregroundMap.containsKey(pkg) && stats.lastTimeUsed >= windowStart) {
                val fg = stats.totalTimeInForeground.coerceAtMost(windowDuration)
                if (fg > 0L) {
                    foregroundMap[pkg] = fg
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fgs = stats.totalTimeForegroundServiceUsed.coerceAtMost(windowDuration)
                val existing = backgroundServiceMap[pkg] ?: 0L
                if (fgs > existing && stats.lastTimeUsed >= windowStart) {
                    backgroundServiceMap[pkg] = fgs
                }
            }
        }

        @Suppress("DEPRECATION")
        val wifiBytesByUid = queryNetworkBytesByUid(ConnectivityManager.TYPE_WIFI, windowStart, windowEnd)
        @Suppress("DEPRECATION")
        val mobileBytesByUid = queryNetworkBytesByUid(ConnectivityManager.TYPE_MOBILE, windowStart, windowEnd)

        val candidatePackages = (foregroundMap.keys + backgroundServiceMap.keys).toMutableSet()

        val activeNetworkUids = (wifiBytesByUid.keys + mobileBytesByUid.keys).filter { uid ->
            (wifiBytesByUid[uid] ?: 0L) + (mobileBytesByUid[uid] ?: 0L) >= 16_384L
        }
        for (uid in activeNetworkUids) {
            packageManager.getPackagesForUid(uid)?.firstOrNull()?.let { candidatePackages.add(it) }
        }

        val results = mutableListOf<AppActivityRecord>()
        for (pkg in candidatePackages) {
            if (shouldExcludePackage(pkg, defaultLauncher)) continue

            val appInfo = getApplicationInfoCompat(pkg)
            val uid = appInfo?.uid ?: -1
            if (appInfo != null) {
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (isSystem && !isUpdatedSystem && pkg !in majorSystemPackages) {
                    val fgCheck = foregroundMap[pkg] ?: 0L
                    val netCheck = (wifiBytesByUid[uid] ?: 0L) + (mobileBytesByUid[uid] ?: 0L)
                    if (fgCheck < 5_000L && netCheck < 256_000L) continue
                }
            }

            val fgMs = (foregroundMap[pkg] ?: 0L).coerceIn(0L, windowDuration)
            val bgMs = (backgroundServiceMap[pkg] ?: 0L).coerceIn(0L, windowDuration)
            val wifiBytes = if (uid > 0) wifiBytesByUid[uid] ?: 0L else 0L
            val mobileBytes = if (uid > 0) mobileBytesByUid[uid] ?: 0L else 0L

            if (fgMs == 0L && bgMs == 0L && wifiBytes == 0L && mobileBytes == 0L) continue

            val label = if (appInfo != null) {
                runCatching { packageManager.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(deriveFriendlyName(pkg))
            } else {
                deriveFriendlyName(pkg)
            }

            results += AppActivityRecord(
                packageName = pkg,
                appName = label,
                uid = uid,
                foregroundMs = fgMs,
                backgroundServiceMs = bgMs,
                wifiBytes = wifiBytes,
                mobileBytes = mobileBytes
            )
        }

        return results
    }

    fun loadAppIcon(packageName: String): Drawable? {
        return runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    private fun collectUsageEvents(
        windowStart: Long,
        windowEnd: Long,
        foregroundMap: MutableMap<String, Long>,
        backgroundServiceMap: MutableMap<String, Long>
    ) {
        val events = runCatching { usageStatsManager?.queryEvents(windowStart, windowEnd) }.getOrNull() ?: return
        val event = UsageEvents.Event()
        val fgStartTimes = mutableMapOf<String, Long>()
        val fgsStartTimes = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val ts = event.timeStamp
            val type = event.eventType

            @Suppress("DEPRECATION")
            val isFgStart = type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == UsageEvents.Event.ACTIVITY_RESUMED)

            @Suppress("DEPRECATION")
            val isFgEnd = type == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    (type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED))

            val isFgsStart = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                type == UsageEvents.Event.FOREGROUND_SERVICE_START

            val isFgsStop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                type == UsageEvents.Event.FOREGROUND_SERVICE_STOP

            when {
                isFgStart -> fgStartTimes.putIfAbsent(pkg, ts)
                isFgEnd -> {
                    val start = fgStartTimes.remove(pkg) ?: windowStart
                    val delta = (ts - start).coerceAtLeast(0L)
                    if (delta > 0L) {
                        foregroundMap[pkg] = (foregroundMap[pkg] ?: 0L) + delta
                    }
                }
                isFgsStart -> fgsStartTimes.putIfAbsent(pkg, ts)
                isFgsStop -> {
                    val start = fgsStartTimes.remove(pkg) ?: windowStart
                    val delta = (ts - start).coerceAtLeast(0L)
                    if (delta > 0L) {
                        backgroundServiceMap[pkg] = (backgroundServiceMap[pkg] ?: 0L) + delta
                    }
                }
            }
        }

        for ((pkg, start) in fgStartTimes) {
            val delta = (windowEnd - start).coerceAtLeast(0L)
            if (delta > 0L) foregroundMap[pkg] = (foregroundMap[pkg] ?: 0L) + delta
        }
        for ((pkg, start) in fgsStartTimes) {
            val delta = (windowEnd - start).coerceAtLeast(0L)
            if (delta > 0L) backgroundServiceMap[pkg] = (backgroundServiceMap[pkg] ?: 0L) + delta
        }
    }

    private fun queryNetworkBytesByUid(
        networkType: Int,
        windowStart: Long,
        windowEnd: Long
    ): Map<Int, Long> {
        val manager = networkStatsManager ?: return emptyMap()
        val bytesByUid = mutableMapOf<Int, Long>()
        try {
            val stats = manager.querySummary(networkType, null, windowStart, windowEnd)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                if (uid > 0) {
                    val total = bucket.rxBytes + bucket.txBytes
                    if (total > 0L) {
                        bytesByUid[uid] = (bytesByUid[uid] ?: 0L) + total
                    }
                }
            }
            stats.close()
        } catch (_: SecurityException) {
            // Gracefully degrade if carrier restricts subscriber-less cellular query
        } catch (_: Exception) {
            // Ignore transient binder failures
        }
        return bytesByUid
    }

    private fun shouldExcludePackage(packageName: String, defaultLauncher: String?): Boolean {
        if (packageName == defaultLauncher) return true
        if (packageName == "android") return true
        if (packageName.startsWith("com.android.providers.")) return true
        return false
    }

    private fun resolveDefaultLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName
    }

    private fun getApplicationInfoCompat(packageName: String): ApplicationInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
        }.getOrNull()
    }

    private fun deriveFriendlyName(packageName: String): String {
        val segment = packageName.substringAfterLast('.')
        return segment.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }.ifBlank { packageName }
    }
}
