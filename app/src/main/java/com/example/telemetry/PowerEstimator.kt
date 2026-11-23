package com.example.telemetry

import com.example.data.model.AppActivityRecord
import com.example.data.model.BatterySnapshot
import com.example.data.model.EstimatedAppDrain
import kotlin.math.abs

class PowerEstimator {

    fun computeIntervalDrainMah(
        previous: BatterySnapshot?,
        current: BatterySnapshot,
        intervalDurationMs: Long
    ): Float {
        if (current.isCharging || intervalDurationMs <= 0L) return 0f

        val durationHours = intervalDurationMs.toDouble() / 3_600_000.0

        if (previous != null && !previous.isCharging &&
            previous.remainingChargeUah > 0L && current.remainingChargeUah > 0L
        ) {
            val deltaUah = previous.remainingChargeUah - current.remainingChargeUah
            if (deltaUah > 0L) {
                return (deltaUah / 1_000.0).toFloat()
            }
        }

        val prevCurrent = if (previous != null && !previous.isCharging && previous.currentMa < 0f) {
            abs(previous.currentMa)
        } else {
            abs(current.currentMa)
        }
        val currCurrent = if (current.currentMa < 0f) abs(current.currentMa) else 0f
        val avgDischargeMa = (prevCurrent + currCurrent) / 2f

        if (avgDischargeMa > 1f) {
            return (avgDischargeMa * durationHours).toFloat()
        }

        if (previous != null && previous.batteryPercent > current.batteryPercent) {
            val pctDrop = previous.batteryPercent - current.batteryPercent
            return current.estimatedCapacityMah * (pctDrop / 100f)
        }

        val baselineMa = if (current.isScreenInteractive) 240f else 45f
        return (baselineMa * durationHours).toFloat()
    }

    fun attributePowerDrain(
        totalDrainMah: Float,
        activities: List<AppActivityRecord>
    ): List<EstimatedAppDrain> {
        if (activities.isEmpty()) return emptyList()

        val scored = activities.map { record ->
            val fgSeconds = record.foregroundMs / 1000.0
            val bgSeconds = record.backgroundServiceMs / 1000.0
            val mobileMb = record.mobileBytes / BYTES_PER_MB
            val wifiMb = record.wifiBytes / BYTES_PER_MB

            val score = (fgSeconds * WEIGHT_FOREGROUND_PER_SEC) +
                (bgSeconds * WEIGHT_BACKGROUND_PER_SEC) +
                (mobileMb * WEIGHT_MOBILE_PER_MB) +
                (wifiMb * WEIGHT_WIFI_PER_MB)

            record to score.coerceAtLeast(0.0)
        }.filter { it.second > 0.0 }

        val totalScore = scored.sumOf { it.second }
        if (totalScore <= 0.0) return emptyList()

        val effectiveDrainMah = totalDrainMah.coerceAtLeast(0f)

        return scored
            .map { (record, score) ->
                val share = (score / totalScore).toFloat()
                EstimatedAppDrain(
                    packageName = record.packageName,
                    appName = record.appName,
                    foregroundMs = record.foregroundMs,
                    backgroundServiceMs = record.backgroundServiceMs,
                    wifiBytes = record.wifiBytes,
                    mobileBytes = record.mobileBytes,
                    estimatedMah = effectiveDrainMah * share,
                    drainPercentageShare = share * 100f
                )
            }
            .sortedByDescending { it.estimatedMah }
    }

    companion object {
        private const val BYTES_PER_MB = 1_048_576.0
        private const val WEIGHT_FOREGROUND_PER_SEC = 1.00
        private const val WEIGHT_BACKGROUND_PER_SEC = 0.25
        private const val WEIGHT_MOBILE_PER_MB = 4.50
        private const val WEIGHT_WIFI_PER_MB = 1.20
    }
}
