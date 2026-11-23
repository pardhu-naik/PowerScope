package com.example.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.PowerScopeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PowerStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    Intent.ACTION_BOOT_COMPLETED -> {
                        PowerMonitorWorker.schedulePeriodic(context)
                    }

                    Intent.ACTION_POWER_CONNECTED,
                    Intent.ACTION_POWER_DISCONNECTED,
                    Intent.ACTION_BATTERY_LOW -> {
                        val app = context.applicationContext as? PowerScopeApp
                        app?.powerRepository?.captureAndPersistSnapshot()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
