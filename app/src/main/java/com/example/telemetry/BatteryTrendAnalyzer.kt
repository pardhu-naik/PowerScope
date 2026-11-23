package com.example.telemetry

import com.example.data.local.entity.BatteryLogEntity
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TrendWindow(val label: String, val durationMs: Long?) {
    DAYS_7("7 Days", 7L * 24L * 60L * 60L * 1000L),
    DAYS_14("14 Days", 14L * 24L * 60L * 60L * 1000L),
    DAYS_30("30 Days", 30L * 24L * 60L * 60L * 1000L),
    ALL_TIME("All Time", null)
}

enum class TrendDirection(val label: String) {
    IMPROVING("Improving"),
    STABLE("Stable"),
    DECLINING("Declining"),
    INSUFFICIENT_DATA("Gathering Data")
}

enum class InsightSeverity {
    GOOD,
    INFO,
    WARNING,
    ALERT
}

data class BatteryInsight(
    val id: String,
    val title: String,
    val metricBadge: String,
    val description: String,
    val severity: InsightSeverity
)

data class ChargingCycleSummary(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val startPercent: Int,
    val endPercent: Int,
    val durationMs: Long,
    val avgChargeCurrentMa: Float,
    val avgChargePowerMw: Float,
    val peakTempCelsius: Float,
    val estimatedFullChargeMs: Long?
) {
    val percentGained: Int get() = (endPercent - startPercent).coerceAtLeast(0)
    val chargeRatePctPerHour: Float
        get() = if (durationMs > 60_000L && percentGained > 0) {
            percentGained / (durationMs / 3_600_000f)
        } else {
            0f
        }
}

data class TrendBucketPoint(
    val label: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val avgDischargePowerMw: Float,
    val avgTempCelsius: Float,
    val avgVoltageMv: Int,
    val sampleCount: Int
)

data class BatteryTrendReport(
    val sampleCount: Int,
    val spanHours: Float,
    val performanceScore: Int,
    val overallDirection: TrendDirection,
    val headlineSummary: String,
    val subSummary: String,
    val avgDischargePowerMw: Float,
    val dischargePowerDriftPct: Float?,
    val avgDrainPctPerHour: Float?,
    val avgTempCelsius: Float,
    val peakTempCelsius: Float,
    val tempDriftCelsius: Float?,
    val thermalStressSharePct: Float,
    val avgVoltageMv: Int,
    val screenOnAvgPowerMw: Float?,
    val standbyAvgPowerMw: Float?,
    val chargingCycles: List<ChargingCycleSummary>,
    val avgChargeCycleDurationMs: Long?,
    val avgEstimatedFullChargeMs: Long?,
    val chargeDurationDriftPct: Float?,
    val avgChargePowerMw: Float?,
    val trendBuckets: List<TrendBucketPoint>,
    val regressionStartMw: Float?,
    val regressionEndMw: Float?,
    val insights: List<BatteryInsight>
)

class BatteryTrendAnalyzer {

    fun analyze(rawLogs: List<BatteryLogEntity>): BatteryTrendReport {
        val logs = rawLogs.sortedBy { it.timestamp }
        if (logs.isEmpty()) {
            return emptyReport()
        }

        val firstTs = logs.first().timestamp
        val lastTs = logs.last().timestamp
        val spanHours = ((lastTs - firstTs).coerceAtLeast(0L) / 3_600_000.0).toFloat()

        val dischargingLogs = logs.filter { !it.isCharging }
        val avgTemp = logs.map { it.temperatureCelsius }.average().toFloat()
        val peakTemp = logs.maxOf { it.temperatureCelsius }
        val avgVoltage = logs.map { it.voltageMv }.average().roundToInt()
        val thermalStressCount = logs.count { it.temperatureCelsius >= THERMAL_STRESS_C }
        val thermalStressSharePct = (thermalStressCount * 100f) / logs.size

        val avgDischargePowerMw = if (dischargingLogs.isNotEmpty()) {
            dischargingLogs.map { it.powerMw.toDouble() }.average().toFloat()
        } else {
            logs.map { it.powerMw.toDouble() }.average().toFloat()
        }

        val screenOnDischarging = dischargingLogs.filter { it.screenOn }
        val standbyDischarging = dischargingLogs.filter { !it.screenOn }
        val screenOnAvgPowerMw = screenOnDischarging
            .takeIf { it.isNotEmpty() }
            ?.map { it.powerMw.toDouble() }
            ?.average()
            ?.toFloat()
        val standbyAvgPowerMw = standbyDischarging
            .takeIf { it.isNotEmpty() }
            ?.map { it.powerMw.toDouble() }
            ?.average()
            ?.toFloat()

        val avgDrainPctPerHour = computeAverageDischargeRatePctPerHour(logs)

        // Compare first half vs second half of discharging telemetry for long-term drift
        val dischargePowerDriftPct = computeHalfDriftPercent(
            dischargingLogs.map { it.powerMw }
        )
        val tempDriftCelsius = computeHalfDriftDelta(
            logs.map { it.temperatureCelsius }
        )

        val chargingCycles = detectChargingCycles(logs)
        val avgChargeCycleDurationMs = chargingCycles
            .takeIf { it.isNotEmpty() }
            ?.map { it.durationMs.toDouble() }
            ?.average()
            ?.toLong()

        val fullChargeEstimates = chargingCycles.mapNotNull { it.estimatedFullChargeMs }
        val avgEstimatedFullChargeMs = fullChargeEstimates
            .takeIf { it.isNotEmpty() }
            ?.map { it.toDouble() }
            ?.average()
            ?.toLong()

        val avgChargePowerMw = chargingCycles
            .map { it.avgChargePowerMw }
            .filter { it > 0f }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()

        val chargeDurationDriftPct = computeHalfDriftPercent(
            fullChargeEstimates.map { it.toFloat() },
            minPoints = 2
        )

        val trendBuckets = buildTrendBuckets(logs)
        val (regStart, regEnd) = computeLinearRegressionEndpoints(
            trendBuckets.map { it.avgDischargePowerMw }
        )

        val (score, direction) = evaluateOverallHealth(
            sampleCount = logs.size,
            dischargePowerDriftPct = dischargePowerDriftPct,
            tempDriftCelsius = tempDriftCelsius,
            thermalStressSharePct = thermalStressSharePct,
            avgTemp = avgTemp,
            chargeDurationDriftPct = chargeDurationDriftPct
        )

        val (headline, subSummary) = buildVerdictCopy(
            sampleCount = logs.size,
            direction = direction,
            dischargePowerDriftPct = dischargePowerDriftPct,
            avgChargeCycleDurationMs = avgChargeCycleDurationMs,
            thermalStressSharePct = thermalStressSharePct
        )

        val insights = buildInsights(
            logs = logs,
            direction = direction,
            dischargePowerDriftPct = dischargePowerDriftPct,
            avgDrainPctPerHour = avgDrainPctPerHour,
            avgTemp = avgTemp,
            peakTemp = peakTemp,
            tempDriftCelsius = tempDriftCelsius,
            thermalStressSharePct = thermalStressSharePct,
            chargingCycles = chargingCycles,
            avgChargeCycleDurationMs = avgChargeCycleDurationMs,
            avgEstimatedFullChargeMs = avgEstimatedFullChargeMs,
            chargeDurationDriftPct = chargeDurationDriftPct,
            screenOnAvgPowerMw = screenOnAvgPowerMw,
            standbyAvgPowerMw = standbyAvgPowerMw
        )

        return BatteryTrendReport(
            sampleCount = logs.size,
            spanHours = spanHours,
            performanceScore = score,
            overallDirection = direction,
            headlineSummary = headline,
            subSummary = subSummary,
            avgDischargePowerMw = avgDischargePowerMw,
            dischargePowerDriftPct = dischargePowerDriftPct,
            avgDrainPctPerHour = avgDrainPctPerHour,
            avgTempCelsius = avgTemp,
            peakTempCelsius = peakTemp,
            tempDriftCelsius = tempDriftCelsius,
            thermalStressSharePct = thermalStressSharePct,
            avgVoltageMv = avgVoltage,
            screenOnAvgPowerMw = screenOnAvgPowerMw,
            standbyAvgPowerMw = standbyAvgPowerMw,
            chargingCycles = chargingCycles,
            avgChargeCycleDurationMs = avgChargeCycleDurationMs,
            avgEstimatedFullChargeMs = avgEstimatedFullChargeMs,
            chargeDurationDriftPct = chargeDurationDriftPct,
            avgChargePowerMw = avgChargePowerMw,
            trendBuckets = trendBuckets,
            regressionStartMw = regStart,
            regressionEndMw = regEnd,
            insights = insights
        )
    }

    private fun computeAverageDischargeRatePctPerHour(logs: List<BatteryLogEntity>): Float? {
        var totalDropPct = 0.0
        var totalHours = 0.0

        for (i in 1 until logs.size) {
            val prev = logs[i - 1]
            val curr = logs[i]
            if (!prev.isCharging && !curr.isCharging) {
                val deltaMs = curr.timestamp - prev.timestamp
                if (deltaMs in MIN_INTERVAL_MS..MAX_CONTIGUOUS_GAP_MS) {
                    val drop = prev.batteryPercent - curr.batteryPercent
                    if (drop >= 0) {
                        totalDropPct += drop
                        totalHours += deltaMs / 3_600_000.0
                    }
                }
            }
        }

        if (totalHours < 0.05) return null
        return (totalDropPct / totalHours).toFloat()
    }

    private fun detectChargingCycles(logs: List<BatteryLogEntity>): List<ChargingCycleSummary> {
        val cycles = mutableListOf<ChargingCycleSummary>()
        val currentSession = mutableListOf<BatteryLogEntity>()

        fun flushSession() {
            if (currentSession.size < 2) {
                currentSession.clear()
                return
            }
            val first = currentSession.first()
            val last = currentSession.last()
            val durationMs = (last.timestamp - first.timestamp).coerceAtLeast(0L)
            val gained = (last.batteryPercent - first.batteryPercent).coerceAtLeast(0)

            if (durationMs >= MIN_CYCLE_DURATION_MS || gained >= 2) {
                val avgCurrent = currentSession.map { abs(it.currentMa).toDouble() }.average().toFloat()
                val avgPower = currentSession.map { it.powerMw.toDouble() }.average().toFloat()
                val peakTemp = currentSession.maxOf { it.temperatureCelsius }
                val estFullMs = if (gained >= 5 && durationMs >= MIN_CYCLE_DURATION_MS) {
                    ((durationMs.toDouble() / gained.toDouble()) * 100.0).toLong()
                } else {
                    null
                }
                cycles += ChargingCycleSummary(
                    startTimestamp = first.timestamp,
                    endTimestamp = last.timestamp,
                    startPercent = first.batteryPercent,
                    endPercent = last.batteryPercent,
                    durationMs = durationMs,
                    avgChargeCurrentMa = avgCurrent,
                    avgChargePowerMw = avgPower,
                    peakTempCelsius = peakTemp,
                    estimatedFullChargeMs = estFullMs
                )
            }
            currentSession.clear()
        }

        for (log in logs) {
            if (log.isCharging) {
                val prev = currentSession.lastOrNull()
                if (prev != null && (log.timestamp - prev.timestamp) > MAX_CONTIGUOUS_GAP_MS) {
                    flushSession()
                }
                currentSession += log
            } else {
                flushSession()
            }
        }
        flushSession()

        return cycles
    }

    private fun buildTrendBuckets(logs: List<BatteryLogEntity>): List<TrendBucketPoint> {
        if (logs.size < 2) return emptyList()
        val desiredBuckets = logs.size.coerceAtMost(MAX_BUCKETS)
        val bucketSize = (logs.size.toFloat() / desiredBuckets).coerceAtLeast(1f)
        val result = mutableListOf<TrendBucketPoint>()

        var startIdx = 0
        var bucketIdx = 0
        while (startIdx < logs.size) {
            val endIdx = if (bucketIdx == desiredBuckets - 1) {
                logs.size
            } else {
                ((bucketIdx + 1) * bucketSize).roundToInt().coerceIn(startIdx + 1, logs.size)
            }
            val slice = logs.subList(startIdx, endIdx)
            val dischargeSlice = slice.filter { !it.isCharging }.ifEmpty { slice }
            val avgMw = dischargeSlice.map { it.powerMw.toDouble() }.average().toFloat()
            val avgTemp = slice.map { it.temperatureCelsius.toDouble() }.average().toFloat()
            val avgMv = slice.map { it.voltageMv.toDouble() }.average().roundToInt()

            result += TrendBucketPoint(
                label = "#${bucketIdx + 1}",
                startTimestamp = slice.first().timestamp,
                endTimestamp = slice.last().timestamp,
                avgDischargePowerMw = avgMw,
                avgTempCelsius = avgTemp,
                avgVoltageMv = avgMv,
                sampleCount = slice.size
            )
            startIdx = endIdx
            bucketIdx++
        }
        return result
    }

    private fun computeLinearRegressionEndpoints(values: List<Float>): Pair<Float?, Float?> {
        if (values.size < 2) return null to null
        val n = values.size.toDouble()
        val meanX = (n - 1.0) / 2.0
        val meanY = values.map { it.toDouble() }.average()

        var numerator = 0.0
        var denominator = 0.0
        values.forEachIndexed { idx, y ->
            val dx = idx - meanX
            numerator += dx * (y - meanY)
            denominator += dx * dx
        }
        if (denominator == 0.0) return meanY.toFloat() to meanY.toFloat()

        val slope = numerator / denominator
        val intercept = meanY - slope * meanX
        val startVal = intercept.coerceAtLeast(0.0).toFloat()
        val endVal = (intercept + slope * (n - 1.0)).coerceAtLeast(0.0).toFloat()
        return startVal to endVal
    }

    private fun computeHalfDriftPercent(values: List<Float>, minPoints: Int = 4): Float? {
        if (values.size < minPoints) return null
        val mid = values.size / 2
        val earlyAvg = values.subList(0, mid).map { it.toDouble() }.average()
        val recentAvg = values.subList(mid, values.size).map { it.toDouble() }.average()
        if (earlyAvg <= 1.0) return null
        return (((recentAvg - earlyAvg) / earlyAvg) * 100.0).toFloat()
    }

    private fun computeHalfDriftDelta(values: List<Float>, minPoints: Int = 4): Float? {
        if (values.size < minPoints) return null
        val mid = values.size / 2
        val earlyAvg = values.subList(0, mid).map { it.toDouble() }.average()
        val recentAvg = values.subList(mid, values.size).map { it.toDouble() }.average()
        return (recentAvg - earlyAvg).toFloat()
    }

    private fun evaluateOverallHealth(
        sampleCount: Int,
        dischargePowerDriftPct: Float?,
        tempDriftCelsius: Float?,
        thermalStressSharePct: Float,
        avgTemp: Float,
        chargeDurationDriftPct: Float?
    ): Pair<Int, TrendDirection> {
        if (sampleCount < 4) {
            val base = (92 - (thermalStressSharePct * 0.3f).roundToInt()).coerceIn(50, 96)
            return base to TrendDirection.INSUFFICIENT_DATA
        }

        var score = 92f

        if (dischargePowerDriftPct != null) {
            when {
                dischargePowerDriftPct > 18f -> score -= 22f
                dischargePowerDriftPct > 8f -> score -= 12f
                dischargePowerDriftPct < -8f -> score += 5f
            }
        }

        if (thermalStressSharePct > 25f) {
            score -= 18f
        } else if (thermalStressSharePct > 10f) {
            score -= 9f
        }

        if (avgTemp > 38f) score -= 10f
        if (tempDriftCelsius != null && tempDriftCelsius > 2.5f) score -= 8f
        if (chargeDurationDriftPct != null && chargeDurationDriftPct > 15f) score -= 10f

        val finalScore = score.roundToInt().coerceIn(25, 100)

        val direction = when {
            (dischargePowerDriftPct != null && dischargePowerDriftPct > 10f) ||
                (chargeDurationDriftPct != null && chargeDurationDriftPct > 15f) ||
                (tempDriftCelsius != null && tempDriftCelsius > 3f) -> TrendDirection.DECLINING

            (dischargePowerDriftPct != null && dischargePowerDriftPct < -8f) &&
                thermalStressSharePct < 15f -> TrendDirection.IMPROVING

            else -> TrendDirection.STABLE
        }

        return finalScore to direction
    }

    private fun buildVerdictCopy(
        sampleCount: Int,
        direction: TrendDirection,
        dischargePowerDriftPct: Float?,
        avgChargeCycleDurationMs: Long?,
        thermalStressSharePct: Float
    ): Pair<String, String> {
        if (sampleCount < 4) {
            return "Building your battery baseline" to
                "Logged $sampleCount sample${if (sampleCount == 1) "" else "s"} so far. Log at least 4 samples across discharge and charging sessions to unlock multi-period trend slopes."
        }

        val headline = when (direction) {
            TrendDirection.DECLINING -> "Battery efficiency decreasing over time"
            TrendDirection.IMPROVING -> "Battery efficiency improving over time"
            TrendDirection.STABLE -> "Battery performance holding steady"
            TrendDirection.INSUFFICIENT_DATA -> "Gathering battery trend data"
        }

        val parts = mutableListOf<String>()
        if (dischargePowerDriftPct != null) {
            val sign = if (dischargePowerDriftPct >= 0f) "+" else ""
            parts += "Discharge power shifted ${sign}${dischargePowerDriftPct.roundToInt()}% vs. earlier period"
        }
        if (avgChargeCycleDurationMs != null && avgChargeCycleDurationMs > 0L) {
            val mins = (avgChargeCycleDurationMs / 60_000L).coerceAtLeast(1L)
            parts += "avg charging cycle lasts ${formatMinutesBrief(mins)}"
        }
        if (thermalStressSharePct >= 15f) {
            parts += "${thermalStressSharePct.roundToInt()}% of samples exceeded 38°C"
        }

        val sub = if (parts.isEmpty()) {
            "Analyzed $sampleCount telemetry snapshots for power, thermal, and charge stability."
        } else {
            parts.joinToString(" • ").replaceFirstChar { it.uppercase() } + "."
        }
        return headline to sub
    }

    private fun buildInsights(
        logs: List<BatteryLogEntity>,
        direction: TrendDirection,
        dischargePowerDriftPct: Float?,
        avgDrainPctPerHour: Float?,
        avgTemp: Float,
        peakTemp: Float,
        tempDriftCelsius: Float?,
        thermalStressSharePct: Float,
        chargingCycles: List<ChargingCycleSummary>,
        avgChargeCycleDurationMs: Long?,
        avgEstimatedFullChargeMs: Long?,
        chargeDurationDriftPct: Float?,
        screenOnAvgPowerMw: Float?,
        standbyAvgPowerMw: Float?
    ): List<BatteryInsight> {
        val list = mutableListOf<BatteryInsight>()

        // 1. Long-term discharge / health trajectory
        if (dischargePowerDriftPct != null) {
            val sign = if (dischargePowerDriftPct >= 0f) "+" else ""
            val severity = when {
                dischargePowerDriftPct > 15f -> InsightSeverity.ALERT
                dischargePowerDriftPct > 8f -> InsightSeverity.WARNING
                dischargePowerDriftPct < -8f -> InsightSeverity.GOOD
                else -> InsightSeverity.INFO
            }
            val title = when {
                dischargePowerDriftPct > 8f -> "Battery health decreasing over time"
                dischargePowerDriftPct < -8f -> "Power efficiency improving"
                else -> "Consistent long-term discharge draw"
            }
            val drainSuffix = avgDrainPctPerHour?.let {
                " Average discharge pace is ${String.format(java.util.Locale.US, "%.1f%%/hr", it)}."
            } ?: ""
            list += BatteryInsight(
                id = "health_drift",
                title = title,
                metricBadge = "${sign}${dischargePowerDriftPct.roundToInt()}% mW",
                description = "Recent discharging snapshots show a ${sign}${dischargePowerDriftPct.roundToInt()}% change in average power draw compared to earlier samples in this window.$drainSuffix",
                severity = severity
            )
        } else {
            list += BatteryInsight(
                id = "health_baseline",
                title = "Baseline calibration in progress",
                metricBadge = "${logs.size} logs",
                description = "Keep background monitoring active or tap Sample Now during normal usage to compare early vs. recent battery efficiency.",
                severity = InsightSeverity.INFO
            )
        }

        // 2. Charging cycle duration & trend
        if (chargingCycles.isNotEmpty() && avgChargeCycleDurationMs != null) {
            val avgMins = (avgChargeCycleDurationMs / 60_000L).coerceAtLeast(1L)
            val fullSuffix = avgEstimatedFullChargeMs?.let {
                " Projected 0–100% full charge takes ~${formatMinutesBrief((it / 60_000L).coerceAtLeast(1L))}."
            } ?: ""
            val driftSuffix = chargeDurationDriftPct?.let { drift ->
                when {
                    drift > 12f -> " Recent cycles are taking ${drift.roundToInt()}% longer than earlier ones."
                    drift < -12f -> " Recent cycles completed ${abs(drift).roundToInt()}% faster."
                    else -> " Charging duration has remained steady across cycles."
                }
            } ?: ""
            val severity = when {
                chargeDurationDriftPct != null && chargeDurationDriftPct > 15f -> InsightSeverity.WARNING
                else -> InsightSeverity.GOOD
            }
            list += BatteryInsight(
                id = "charge_cycles",
                title = "Charging cycle duration",
                metricBadge = formatMinutesBrief(avgMins),
                description = "Detected ${chargingCycles.size} charging session${if (chargingCycles.size == 1) "" else "s"} averaging ${formatMinutesBrief(avgMins)}.$fullSuffix$driftSuffix",
                severity = severity
            )
        } else {
            list += BatteryInsight(
                id = "charge_cycles_pending",
                title = "Charging cycle tracking ready",
                metricBadge = "0 cycles",
                description = "Plug in your device across at least two logged snapshots to automatically measure charging cycle duration and speed trends.",
                severity = InsightSeverity.INFO
            )
        }

        // 3. Thermal stress & heat trend
        val thermalSeverity = when {
            peakTemp >= 43f || thermalStressSharePct >= 25f -> InsightSeverity.ALERT
            peakTemp >= 39f || thermalStressSharePct >= 10f -> InsightSeverity.WARNING
            else -> InsightSeverity.GOOD
        }
        val tempTrendNote = tempDriftCelsius?.let { delta ->
            when {
                delta >= 2f -> " Operating temperatures rose by +${String.format(java.util.Locale.US, "%.1f°C", delta)} recently."
                delta <= -2f -> " Device is running ${String.format(java.util.Locale.US, "%.1f°C", abs(delta))} cooler recently."
                else -> " Thermal profile remained consistent."
            }
        } ?: ""
        list += BatteryInsight(
            id = "thermal_profile",
            title = if (thermalSeverity == InsightSeverity.GOOD) "Healthy thermal operating range" else "Elevated thermal stress detected",
            metricBadge = String.format(java.util.Locale.US, "%.1f°C avg", avgTemp),
            description = "Peak battery temperature reached ${String.format(java.util.Locale.US, "%.1f°C", peakTemp)} with ${thermalStressSharePct.roundToInt()}% of samples at or above 38°C.$tempTrendNote",
            severity = thermalSeverity
        )

        // 4. Screen-on vs. standby drain contrast
        if (screenOnAvgPowerMw != null && standbyAvgPowerMw != null && standbyAvgPowerMw > 1f) {
            val ratio = screenOnAvgPowerMw / standbyAvgPowerMw
            val highStandby = standbyAvgPowerMw > 450f
            list += BatteryInsight(
                id = "standby_ratio",
                title = if (highStandby) "Elevated standby power drain" else "Screen-on vs. standby balance",
                metricBadge = String.format(java.util.Locale.US, "%.0f mW idle", standbyAvgPowerMw),
                description = "Active screen draws ~${screenOnAvgPowerMw.roundToInt()} mW vs. ${standbyAvgPowerMw.roundToInt()} mW in standby (${String.format(java.util.Locale.US, "%.1fx", ratio)} active ratio).",
                severity = if (highStandby) InsightSeverity.WARNING else InsightSeverity.GOOD
            )
        }

        // Keep direction-relevant alert near top
        return if (direction == TrendDirection.DECLINING) list else list
    }

    private fun formatMinutesBrief(minutes: Long): String {
        val h = minutes / 60L
        val m = minutes % 60L
        return if (h > 0L) "${h}h ${m}m" else "${m}m"
    }

    private fun emptyReport(): BatteryTrendReport = BatteryTrendReport(
        sampleCount = 0,
        spanHours = 0f,
        performanceScore = 0,
        overallDirection = TrendDirection.INSUFFICIENT_DATA,
        headlineSummary = "No battery history logged yet",
        subSummary = "Capture snapshots on the Dashboard or keep background monitoring running to analyze long-term battery health and charging cycle duration.",
        avgDischargePowerMw = 0f,
        dischargePowerDriftPct = null,
        avgDrainPctPerHour = null,
        avgTempCelsius = 0f,
        peakTempCelsius = 0f,
        tempDriftCelsius = null,
        thermalStressSharePct = 0f,
        avgVoltageMv = 0,
        screenOnAvgPowerMw = null,
        standbyAvgPowerMw = null,
        chargingCycles = emptyList(),
        avgChargeCycleDurationMs = null,
        avgEstimatedFullChargeMs = null,
        chargeDurationDriftPct = null,
        avgChargePowerMw = null,
        trendBuckets = emptyList(),
        regressionStartMw = null,
        regressionEndMw = null,
        insights = listOf(
            BatteryInsight(
                id = "empty_start",
                title = "Start building your power baseline",
                metricBadge = "0 samples",
                description = "Tap \"Sample Now\" above to log your first telemetry reading. PowerScope automatically records a snapshot every 15 minutes and on every charger plug/unplug event.",
                severity = InsightSeverity.INFO
            )
        )
    )

    companion object {
        private const val THERMAL_STRESS_C = 38.0f
        private const val MIN_INTERVAL_MS = 30_000L
        private const val MAX_CONTIGUOUS_GAP_MS = 4L * 60L * 60L * 1000L
        private const val MIN_CYCLE_DURATION_MS = 60_000L
        private const val MAX_BUCKETS = 10
    }
}
