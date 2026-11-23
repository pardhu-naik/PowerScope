package com.example.data.repository

import android.content.Context
import android.graphics.drawable.Drawable
import com.example.data.local.PowerDatabase
import com.example.data.local.entity.AppUsageLogEntity
import com.example.data.local.entity.BatteryLogEntity
import com.example.data.model.BatterySnapshot
import com.example.data.model.EstimatedAppDrain
import com.example.data.model.TimeRangeFilter
import com.example.telemetry.ActivityMonitorManager
import com.example.telemetry.BatteryTelemetryReader
import com.example.telemetry.PowerEstimator
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class PowerRepository(
    context: Context,
    private val database: PowerDatabase,
    private val telemetryReader: BatteryTelemetryReader,
    private val activityMonitor: ActivityMonitorManager,
    private val powerEstimator: PowerEstimator
) {
    private val batteryDao = database.batteryLogDao()
    private val appUsageDao = database.appUsageLogDao()

    private val iconCache = ConcurrentHashMap<String, Drawable?>()

    @Volatile
    private var lastCapturedSnapshot: BatterySnapshot? = null

    fun observeLiveBatteryTelemetry(intervalMs: Long = 2_000L): Flow<BatterySnapshot> = flow {
        while (currentCoroutineContext().isActive) {
            emit(telemetryReader.readSnapshot())
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    fun observeAverageDischargeCurrentMa(): Flow<Float?> =
        batteryDao.observeAverageDischargeCurrentMa(sampleCount = 24)

    suspend fun resolveStartTimestamp(filter: TimeRangeFilter): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        when (filter) {
            TimeRangeFilter.LAST_1_HOUR,
            TimeRangeFilter.LAST_24_HOURS,
            TimeRangeFilter.LAST_7_DAYS -> now - (filter.durationMs ?: DEFAULT_24H_MS)

            TimeRangeFilter.SINCE_LAST_CHARGE -> {
                batteryDao.getLastFullChargeTimestamp()
                    ?: batteryDao.getLastChargingTimestamp()
                    ?: (now - DEFAULT_24H_MS)
            }
        }
    }

    fun observeBatteryLogsSince(sinceMs: Long): Flow<List<BatteryLogEntity>> =
        batteryDao.observeLogsSince(sinceMs)

    fun observeAllBatteryLogs(): Flow<List<BatteryLogEntity>> =
        batteryDao.observeAllLogs()

    fun observeAggregatedAppDrainSince(sinceMs: Long): Flow<List<EstimatedAppDrain>> =
        appUsageDao.observeAggregatedSince(sinceMs).map { rows ->
            val totalMah = rows.sumOf { it.totalEstimatedMah.toDouble() }.toFloat()
            rows.map { row ->
                val share = if (totalMah > 0f) (row.totalEstimatedMah / totalMah) * 100f else 0f
                EstimatedAppDrain(
                    packageName = row.packageName,
                    appName = row.appName,
                    foregroundMs = row.totalForegroundMs,
                    backgroundServiceMs = row.totalBackgroundMs,
                    wifiBytes = row.totalWifiBytes,
                    mobileBytes = row.totalMobileBytes,
                    estimatedMah = row.totalEstimatedMah,
                    drainPercentageShare = share
                )
            }
        }

    suspend fun captureAndPersistSnapshot(defaultWindowMs: Long = DEFAULT_SAMPLE_WINDOW_MS): BatterySnapshot =
        withContext(Dispatchers.IO) {
            val current = telemetryReader.readSnapshot()
            val previousEntity = batteryDao.getLatest()
            val now = current.timestamp

            val windowStart = previousEntity?.timestamp
                ?.takeIf { it < now && (now - it) <= MAX_SAMPLE_WINDOW_MS }
                ?: (now - defaultWindowMs)

            batteryDao.insert(
                BatteryLogEntity(
                    timestamp = current.timestamp,
                    batteryPercent = current.batteryPercent,
                    voltageMv = current.voltageMv,
                    currentMa = current.currentMa,
                    powerMw = current.powerMw,
                    temperatureCelsius = current.temperatureCelsius,
                    isCharging = current.isCharging,
                    screenOn = current.isScreenInteractive,
                    powerSaveMode = current.isPowerSaveMode
                )
            )

            val activities = activityMonitor.queryWindowActivity(windowStart, now)
            if (activities.isNotEmpty()) {
                val previousSnapshot = lastCapturedSnapshot ?: previousEntity?.let { prev ->
                    BatterySnapshot(
                        timestamp = prev.timestamp,
                        batteryPercent = prev.batteryPercent,
                        voltageMv = prev.voltageMv,
                        temperatureCelsius = prev.temperatureCelsius,
                        chargeState = current.chargeState,
                        plugType = current.plugType,
                        isCharging = prev.isCharging,
                        currentMicroAmps = (prev.currentMa * 1000f).toLong(),
                        currentMa = prev.currentMa,
                        powerMw = prev.powerMw,
                        remainingChargeUah = ((current.estimatedCapacityMah * (prev.batteryPercent / 100f)) * 1000f).toLong(),
                        estimatedCapacityMah = current.estimatedCapacityMah,
                        isScreenInteractive = prev.screenOn,
                        isPowerSaveMode = prev.powerSaveMode,
                        isDeviceIdleMode = false
                    )
                }

                val intervalMs = (now - windowStart).coerceAtLeast(1_000L)
                val intervalDrainMah = powerEstimator.computeIntervalDrainMah(
                    previous = previousSnapshot,
                    current = current,
                    intervalDurationMs = intervalMs
                )

                val attributed = powerEstimator.attributePowerDrain(
                    totalDrainMah = intervalDrainMah,
                    activities = activities
                )

                if (attributed.isNotEmpty()) {
                    appUsageDao.insertAll(
                        attributed.map { drain ->
                            AppUsageLogEntity(
                                windowStart = windowStart,
                                windowEnd = now,
                                packageName = drain.packageName,
                                appName = drain.appName,
                                foregroundMs = drain.foregroundMs,
                                backgroundServiceMs = drain.backgroundServiceMs,
                                wifiBytes = drain.wifiBytes,
                                mobileBytes = drain.mobileBytes,
                                estimatedMah = drain.estimatedMah
                            )
                        }
                    )
                }
            }

            lastCapturedSnapshot = current
            pruneOldRecords(now)
            current
        }

    suspend fun getBatteryLogsForExport(sinceMs: Long): List<BatteryLogEntity> =
        withContext(Dispatchers.IO) { batteryDao.getLogsSince(sinceMs) }

    suspend fun getAppUsageLogsForExport(sinceMs: Long): List<AppUsageLogEntity> =
        withContext(Dispatchers.IO) { appUsageDao.getLogsSince(sinceMs) }

    suspend fun getAppIcon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        if (iconCache.containsKey(packageName)) {
            return@withContext iconCache[packageName]
        }
        val drawable = activityMonitor.loadAppIcon(packageName)
        if (drawable != null) {
            iconCache[packageName] = drawable
        }
        drawable
    }

    private suspend fun pruneOldRecords(nowMs: Long) {
        val cutoff = nowMs - RETENTION_MS
        batteryDao.deleteOlderThan(cutoff)
        appUsageDao.deleteOlderThan(cutoff)
    }

    companion object {
        private const val DEFAULT_SAMPLE_WINDOW_MS = 15L * 60L * 1000L
        private const val MAX_SAMPLE_WINDOW_MS = 6L * 60L * 60L * 1000L
        private const val DEFAULT_24H_MS = 24L * 60L * 60L * 1000L
        private const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
    }
}
