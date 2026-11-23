package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.entity.AppUsageLogEntity
import com.example.data.local.entity.BatteryLogEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CsvExporter {

    suspend fun exportToCsvAndCreateShareIntent(
        context: Context,
        batteryLogs: List<BatteryLogEntity>,
        appUsageLogs: List<AppUsageLogEntity>
    ): Intent = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val csvFile = File(exportDir, "powerscope_report_$stamp.csv")

        csvFile.bufferedWriter().use { writer ->
            writer.appendLine("# PowerScope Battery & Activity Report")
            writer.appendLine("# Generated: ${Formatters.formatCsvTimestamp(System.currentTimeMillis())}")
            writer.appendLine()
            writer.appendLine("[BATTERY_LOGS]")
            writer.appendLine("timestamp,battery_percent,voltage_mv,current_ma,power_mw,temp_c,is_charging,screen_on,power_save_mode")
            for (log in batteryLogs) {
                writer.appendLine(
                    listOf(
                        Formatters.formatCsvTimestamp(log.timestamp),
                        log.batteryPercent,
                        log.voltageMv,
                        String.format(Locale.US, "%.2f", log.currentMa),
                        String.format(Locale.US, "%.2f", log.powerMw),
                        String.format(Locale.US, "%.1f", log.temperatureCelsius),
                        log.isCharging,
                        log.screenOn,
                        log.powerSaveMode
                    ).joinToString(",")
                )
            }

            writer.appendLine()
            writer.appendLine("[APP_DRAIN_LOGS]")
            writer.appendLine("window_start,window_end,package_name,app_name,foreground_ms,background_service_ms,wifi_bytes,mobile_bytes,estimated_mah")
            for (app in appUsageLogs) {
                val safeAppName = "\"${app.appName.replace("\"", "\"\"")}\""
                writer.appendLine(
                    listOf(
                        Formatters.formatCsvTimestamp(app.windowStart),
                        Formatters.formatCsvTimestamp(app.windowEnd),
                        app.packageName,
                        safeAppName,
                        app.foregroundMs,
                        app.backgroundServiceMs,
                        app.wifiBytes,
                        app.mobileBytes,
                        String.format(Locale.US, "%.3f", app.estimatedMah)
                    ).joinToString(",")
                )
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PowerScope Power Telemetry Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        Intent.createChooser(shareIntent, "Export PowerScope Telemetry CSV")
    }
}
