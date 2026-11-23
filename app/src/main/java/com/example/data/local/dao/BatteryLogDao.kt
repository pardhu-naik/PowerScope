package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BatteryLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: BatteryLogEntity): Long

    @Query("SELECT * FROM battery_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): BatteryLogEntity?

    @Query("SELECT * FROM battery_logs ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<BatteryLogEntity?>

    @Query("SELECT * FROM battery_logs WHERE timestamp >= :sinceMs ORDER BY timestamp ASC")
    fun observeLogsSince(sinceMs: Long): Flow<List<BatteryLogEntity>>

    @Query("SELECT * FROM battery_logs ORDER BY timestamp ASC")
    fun observeAllLogs(): Flow<List<BatteryLogEntity>>

    @Query("SELECT * FROM battery_logs WHERE timestamp >= :sinceMs ORDER BY timestamp ASC")
    suspend fun getLogsSince(sinceMs: Long): List<BatteryLogEntity>

    @Query(
        """
        SELECT timestamp FROM battery_logs
        WHERE isCharging = 1 AND batteryPercent >= 95
        ORDER BY timestamp DESC
        LIMIT 1
        """
    )
    suspend fun getLastFullChargeTimestamp(): Long?

    @Query(
        """
        SELECT timestamp FROM battery_logs
        WHERE isCharging = 1
        ORDER BY timestamp DESC
        LIMIT 1
        """
    )
    suspend fun getLastChargingTimestamp(): Long?

    @Query(
        """
        SELECT AVG(ABS(currentMa)) FROM (
            SELECT currentMa FROM battery_logs
            WHERE isCharging = 0 AND currentMa < 0
            ORDER BY timestamp DESC
            LIMIT :sampleCount
        )
        """
    )
    fun observeAverageDischargeCurrentMa(sampleCount: Int = 20): Flow<Float?>

    @Query("DELETE FROM battery_logs WHERE timestamp < :olderThanMs")
    suspend fun deleteOlderThan(olderThanMs: Long): Int
}
