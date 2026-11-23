package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AppUsageLogEntity
import kotlinx.coroutines.flow.Flow

data class AggregatedAppDrainRow(
    val packageName: String,
    val appName: String,
    val totalForegroundMs: Long,
    val totalBackgroundMs: Long,
    val totalWifiBytes: Long,
    val totalMobileBytes: Long,
    val totalEstimatedMah: Float
)

@Dao
interface AppUsageLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AppUsageLogEntity>)

    @Query(
        """
        SELECT
            packageName,
            MAX(appName) AS appName,
            SUM(foregroundMs) AS totalForegroundMs,
            SUM(backgroundServiceMs) AS totalBackgroundMs,
            SUM(wifiBytes) AS totalWifiBytes,
            SUM(mobileBytes) AS totalMobileBytes,
            SUM(estimatedMah) AS totalEstimatedMah
        FROM app_usage_logs
        WHERE windowEnd >= :sinceMs
        GROUP BY packageName
        ORDER BY totalEstimatedMah DESC
        """
    )
    fun observeAggregatedSince(sinceMs: Long): Flow<List<AggregatedAppDrainRow>>

    @Query("SELECT * FROM app_usage_logs WHERE windowEnd >= :sinceMs ORDER BY windowEnd DESC")
    suspend fun getLogsSince(sinceMs: Long): List<AppUsageLogEntity>

    @Query("DELETE FROM app_usage_logs WHERE windowEnd < :olderThanMs")
    suspend fun deleteOlderThan(olderThanMs: Long): Int
}
