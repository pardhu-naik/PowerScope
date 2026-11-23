package com.example

import com.example.data.local.entity.BatteryLogEntity
import com.example.telemetry.BatteryTrendAnalyzer
import com.example.telemetry.InsightSeverity
import com.example.telemetry.TrendDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryTrendAnalyzerTest {

    private val analyzer = BatteryTrendAnalyzer()

    @Test
    fun `empty logs return insufficient data report`() {
        val report = analyzer.analyze(emptyList())
        assertEquals(0, report.sampleCount)
        assertEquals(TrendDirection.INSUFFICIENT_DATA, report.overallDirection)
        assertTrue(report.chargingCycles.isEmpty())
        assertTrue(report.insights.isNotEmpty())
    }

    @Test
    fun `rising discharge power flags declining battery health trend`() {
        val baseTs = 1_700_000_000_000L
        val hourMs = 3_600_000L

        // Early half draws ~900 mW, recent half draws ~1600 mW (+77% drift)
        val logs = listOf(
            sample(baseTs + 0 * hourMs, 95, 900f, 30f, isCharging = false),
            sample(baseTs + 1 * hourMs, 90, 920f, 31f, isCharging = false),
            sample(baseTs + 2 * hourMs, 85, 880f, 31f, isCharging = false),
            sample(baseTs + 3 * hourMs, 78, 1550f, 35f, isCharging = false),
            sample(baseTs + 4 * hourMs, 69, 1620f, 37f, isCharging = false),
            sample(baseTs + 5 * hourMs, 59, 1680f, 39f, isCharging = false)
        )

        val report = analyzer.analyze(logs)

        assertEquals(TrendDirection.DECLINING, report.overallDirection)
        assertNotNull(report.dischargePowerDriftPct)
        assertTrue((report.dischargePowerDriftPct ?: 0f) > 50f)
        assertTrue(report.insights.any { it.id == "health_drift" && it.severity == InsightSeverity.ALERT })
    }

    @Test
    fun `detects charging cycles and computes average cycle duration`() {
        val baseTs = 1_700_000_000_000L
        val minMs = 60_000L

        val logs = listOf(
            // Cycle 1: 30 minutes, 20% -> 50% (+30%) -> est 100 min full charge
            sample(baseTs + 0 * minMs, 20, 8000f, 34f, isCharging = true),
            sample(baseTs + 15 * minMs, 35, 8200f, 35f, isCharging = true),
            sample(baseTs + 30 * minMs, 50, 7900f, 36f, isCharging = true),
            // Discharging gap
            sample(baseTs + 60 * minMs, 45, 1100f, 31f, isCharging = false),
            sample(baseTs + 90 * minMs, 40, 1050f, 30f, isCharging = false),
            // Cycle 2: 40 minutes, 40% -> 80% (+40%) -> est 100 min full charge
            sample(baseTs + 120 * minMs, 40, 7800f, 35f, isCharging = true),
            sample(baseTs + 140 * minMs, 60, 7600f, 37f, isCharging = true),
            sample(baseTs + 160 * minMs, 80, 7400f, 38f, isCharging = true)
        )

        val report = analyzer.analyze(logs)

        assertEquals(2, report.chargingCycles.size)
        assertEquals(35 * minMs, report.avgChargeCycleDurationMs)
        assertEquals(100 * minMs, report.avgEstimatedFullChargeMs)
        assertTrue(report.insights.any { it.id == "charge_cycles" })
    }

    private fun sample(
        timestamp: Long,
        percent: Int,
        powerMw: Float,
        tempC: Float,
        isCharging: Boolean
    ) = BatteryLogEntity(
        timestamp = timestamp,
        batteryPercent = percent,
        voltageMv = 3950,
        currentMa = if (isCharging) powerMw / 3.95f else -(powerMw / 3.95f),
        powerMw = powerMw,
        temperatureCelsius = tempC,
        isCharging = isCharging,
        screenOn = !isCharging,
        powerSaveMode = false
    )
}
