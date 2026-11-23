package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "battery_logs",
    indices = [Index(value = ["timestamp"])]
)
data class BatteryLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long,
    val batteryPercent: Int,
    val voltageMv: Int,
    val currentMa: Float,
    val powerMw: Float,
    val temperatureCelsius: Float,
    val isCharging: Boolean,
    val screenOn: Boolean,
    val powerSaveMode: Boolean
)
