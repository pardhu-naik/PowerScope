package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_usage_logs",
    indices = [
        Index(value = ["windowEnd"]),
        Index(value = ["packageName"])
    ]
)
data class AppUsageLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val windowStart: Long,
    val windowEnd: Long,
    val packageName: String,
    val appName: String,
    val foregroundMs: Long,
    val backgroundServiceMs: Long,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val estimatedMah: Float
)
