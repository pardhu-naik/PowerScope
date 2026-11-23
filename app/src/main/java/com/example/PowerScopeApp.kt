package com.example

import android.app.Application
import com.example.data.local.PowerDatabase
import com.example.data.repository.PowerRepository
import com.example.telemetry.ActivityMonitorManager
import com.example.telemetry.BatteryTelemetryReader
import com.example.telemetry.PowerEstimator
import com.example.worker.PowerMonitorWorker

class PowerScopeApp : Application() {

    lateinit var database: PowerDatabase
        private set

    lateinit var powerRepository: PowerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = PowerDatabase.getInstance(this)
        powerRepository = PowerRepository(
            context = this,
            database = database,
            telemetryReader = BatteryTelemetryReader(this),
            activityMonitor = ActivityMonitorManager(this),
            powerEstimator = PowerEstimator()
        )
        runCatching { PowerMonitorWorker.schedulePeriodic(this) }
    }
}
