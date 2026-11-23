package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Formatters {

    fun formatDuration(ms: Long): String {
        if (ms < 1_000L) return "0s"
        val totalSeconds = ms / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun formatHoursMinutes(hours: Float): String {
        if (!hours.isFinite() || hours <= 0f) return "Calculating..."
        val clamped = hours.coerceAtMost(99.9f)
        val wholeHours = clamped.toInt()
        val minutes = ((clamped - wholeHours) * 60f).toInt()
        return if (wholeHours > 0) "${wholeHours}h ${minutes}m" else "${minutes}m"
    }

    fun formatBytes(bytes: Long): String {
        val absBytes = abs(bytes)
        return when {
            absBytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", absBytes / 1_073_741_824.0)
            absBytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", absBytes / 1_048_576.0)
            absBytes >= 1_024L -> String.format(Locale.US, "%.0f KB", absBytes / 1_024.0)
            else -> "$absBytes B"
        }
    }

    fun formatCurrentMa(currentMa: Float): String {
        val sign = if (currentMa > 0.5f) "+" else ""
        return String.format(Locale.US, "%s%.0f mA", sign, currentMa)
    }

    fun formatPowerMw(powerMw: Float): String {
        return if (powerMw >= 1000f) {
            String.format(Locale.US, "%.2f W (%.0f mW)", powerMw / 1000f, powerMw)
        } else {
            String.format(Locale.US, "%.0f mW", powerMw)
        }
    }

    fun formatPowerShort(powerMw: Float): String = String.format(Locale.US, "%.0f mW", powerMw)

    fun formatVoltageV(voltageMv: Int): String = String.format(Locale.US, "%.2f V", voltageMv / 1000f)

    fun formatTempC(tempC: Float): String = String.format(Locale.US, "%.1f°C", tempC)

    fun formatMah(mah: Float): String = String.format(Locale.US, "%.1f mAh", mah)

    fun formatPercent(value: Float, decimals: Int = 1): String =
        String.format(Locale.US, "%.${decimals}f%%", value)

    fun formatShortTime(timestamp: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

    fun formatDateTime(timestamp: Long): String =
        SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    fun formatCsvTimestamp(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
}
