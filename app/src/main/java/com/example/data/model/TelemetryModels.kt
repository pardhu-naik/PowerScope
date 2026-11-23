package com.example.data.model

enum class PlugType(val label: String) {
    NONE("Battery"),
    AC("AC Charger"),
    USB("USB"),
    WIRELESS("Wireless"),
    DOCK("Dock")
}

enum class BatteryChargeState(val label: String) {
    CHARGING("Charging"),
    DISCHARGING("Discharging"),
    FULL("Full"),
    NOT_CHARGING("Not Charging"),
    UNKNOWN("Unknown")
}

data class BatterySnapshot(
    val timestamp: Long,
    val batteryPercent: Int,
    val voltageMv: Int,
    val temperatureCelsius: Float,
    val chargeState: BatteryChargeState,
    val plugType: PlugType,
    val isCharging: Boolean,
    val currentMicroAmps: Long,
    val currentMa: Float,
    val powerMw: Float,
    val remainingChargeUah: Long,
    val estimatedCapacityMah: Float,
    val isScreenInteractive: Boolean,
    val isPowerSaveMode: Boolean,
    val isDeviceIdleMode: Boolean
)

data class AppActivityRecord(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val foregroundMs: Long,
    val backgroundServiceMs: Long,
    val wifiBytes: Long,
    val mobileBytes: Long
)

data class EstimatedAppDrain(
    val packageName: String,
    val appName: String,
    val foregroundMs: Long,
    val backgroundServiceMs: Long,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val estimatedMah: Float,
    val drainPercentageShare: Float
)

enum class TimeRangeFilter(val label: String, val durationMs: Long?) {
    LAST_1_HOUR("1 Hour", 60L * 60L * 1000L),
    LAST_24_HOURS("24 Hours", 24L * 60L * 60L * 1000L),
    LAST_7_DAYS("7 Days", 7L * 24L * 60L * 60L * 1000L),
    SINCE_LAST_CHARGE("Since Charge", null)
}

enum class AppSortOrder(val label: String) {
    ESTIMATED_MAH("Estimated mAh"),
    ACTIVE_TIME("Active Time")
}

enum class ChartOverlayMetric(val label: String, val unit: String) {
    POWER_MW("Power Draw", "mW"),
    TEMP_C("Temperature", "°C")
}
