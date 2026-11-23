package com.example.telemetry

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.data.model.BatteryChargeState
import com.example.data.model.BatterySnapshot
import com.example.data.model.PlugType
import kotlin.math.abs

class BatteryTelemetryReader(private val context: Context) {

    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val cachedDesignCapacityMah: Float by lazy { resolveDesignCapacityMah() }

    fun readSnapshot(): BatterySnapshot {
        val now = System.currentTimeMillis()
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val batteryPercent = if (level >= 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }

        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageMv = when {
            rawVoltage in 1..10 -> rawVoltage * 1000
            rawVoltage > 0 -> rawVoltage
            else -> 3850
        }

        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val temperatureCelsius = rawTemp / 10f

        val statusInt = intent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        val chargeState = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> BatteryChargeState.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryChargeState.DISCHARGING
            BatteryManager.BATTERY_STATUS_FULL -> BatteryChargeState.FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryChargeState.NOT_CHARGING
            else -> BatteryChargeState.UNKNOWN
        }

        val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val plugType = when {
            (pluggedInt and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> PlugType.AC
            (pluggedInt and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> PlugType.USB
            (pluggedInt and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> PlugType.WIRELESS
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (pluggedInt and BatteryManager.BATTERY_PLUGGED_DOCK) != 0 -> PlugType.DOCK
            else -> PlugType.NONE
        }

        val isCharging = chargeState == BatteryChargeState.CHARGING ||
            (chargeState == BatteryChargeState.FULL && plugType != PlugType.NONE)

        val normalizedCurrentUa = readNormalizedCurrentMicroAmps(isCharging, chargeState)
        val currentMa = normalizedCurrentUa / 1000f

        val powerMw = ((abs(normalizedCurrentUa).toDouble() * voltageMv.toDouble()) / 1_000_000.0).toFloat()

        val remainingChargeUah = readRemainingChargeMicroAmpHours(batteryPercent)

        return BatterySnapshot(
            timestamp = now,
            batteryPercent = batteryPercent,
            voltageMv = voltageMv,
            temperatureCelsius = temperatureCelsius,
            chargeState = chargeState,
            plugType = plugType,
            isCharging = isCharging,
            currentMicroAmps = normalizedCurrentUa,
            currentMa = currentMa,
            powerMw = powerMw,
            remainingChargeUah = remainingChargeUah,
            estimatedCapacityMah = cachedDesignCapacityMah,
            isScreenInteractive = powerManager.isInteractive,
            isPowerSaveMode = powerManager.isPowerSaveMode,
            isDeviceIdleMode = powerManager.isDeviceIdleMode
        )
    }

    private fun readNormalizedCurrentMicroAmps(
        isCharging: Boolean,
        state: BatteryChargeState
    ): Long {
        val rawNow = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val rawAvg = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)

        val raw = when {
            rawNow != Long.MIN_VALUE && rawNow != Int.MIN_VALUE.toLong() && rawNow != 0L -> rawNow
            rawAvg != Long.MIN_VALUE && rawAvg != Int.MIN_VALUE.toLong() && rawAvg != 0L -> rawAvg
            else -> 0L
        }

        if (raw == 0L) return 0L

        val magnitudeUa = if (abs(raw) in 1L..8_000L) {
            abs(raw) * 1_000L
        } else {
            abs(raw)
        }

        return when {
            isCharging -> magnitudeUa
            state == BatteryChargeState.DISCHARGING || state == BatteryChargeState.NOT_CHARGING -> -magnitudeUa
            else -> if (raw < 0) -magnitudeUa else magnitudeUa
        }
    }

    private fun readRemainingChargeMicroAmpHours(batteryPercent: Int): Long {
        val rawCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (rawCounter > 0L && rawCounter != Long.MIN_VALUE && rawCounter != Int.MIN_VALUE.toLong()) {
            return if (rawCounter < 20_000L) rawCounter * 1_000L else rawCounter
        }

        val fallbackMah = cachedDesignCapacityMah * (batteryPercent / 100f)
        return (fallbackMah * 1_000f).toLong()
    }

    @SuppressLint("PrivateApi")
    private fun resolveDesignCapacityMah(): Float {
        try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val instance = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val capacity = powerProfileClass.getMethod("getBatteryCapacity").invoke(instance) as? Double
            if (capacity != null && capacity > 500.0 && capacity < 25_000.0) {
                return capacity.toFloat()
            }
        } catch (_: Throwable) {
            // Hidden API may be restricted on newer Android builds; fallback below
        }

        val chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val capacityPct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (chargeCounter > 0L && chargeCounter != Long.MIN_VALUE && capacityPct in 5..100) {
            val remainingMah = if (chargeCounter < 20_000L) chargeCounter.toFloat() else chargeCounter / 1_000f
            val inferred = (remainingMah / (capacityPct / 100f))
            if (inferred in 1_000f..15_000f) return inferred
        }

        return DEFAULT_CAPACITY_MAH
    }

    companion object {
        private const val DEFAULT_CAPACITY_MAH = 4_500f
    }
}
