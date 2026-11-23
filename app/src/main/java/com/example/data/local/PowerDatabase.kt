package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AppUsageLogDao
import com.example.data.local.dao.BatteryLogDao
import com.example.data.local.entity.AppUsageLogEntity
import com.example.data.local.entity.BatteryLogEntity

@Database(
    entities = [
        BatteryLogEntity::class,
        AppUsageLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PowerDatabase : RoomDatabase() {
    abstract fun batteryLogDao(): BatteryLogDao
    abstract fun appUsageLogDao(): AppUsageLogDao

    companion object {
        @Volatile
        private var INSTANCE: PowerDatabase? = null

        fun getInstance(context: Context): PowerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PowerDatabase::class.java,
                    "powerscope_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
