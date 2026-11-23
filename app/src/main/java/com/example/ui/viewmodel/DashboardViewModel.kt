package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BatterySnapshot
import com.example.data.model.EstimatedAppDrain
import com.example.data.repository.PowerRepository
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val snapshot: BatterySnapshot? = null,
    val estimatedTimeRemainingHours: Float? = null,
    val topConsumers: List<EstimatedAppDrain> = emptyList(),
    val isSampling: Boolean = false,
    val statusMessage: String? = null
)

class DashboardViewModel(
    val repository: PowerRepository
) : ViewModel() {

    private val isSamplingFlow = MutableStateFlow(false)
    private val statusMessageFlow = MutableStateFlow<String?>(null)

    private val since24h = System.currentTimeMillis() - (24L * 60L * 60L * 1000L)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeLiveBatteryTelemetry(intervalMs = 2_000L),
        repository.observeAverageDischargeCurrentMa(),
        repository.observeAggregatedAppDrainSince(since24h),
        isSamplingFlow,
        statusMessageFlow
    ) { snapshot, avgDischargeMa, topApps, isSampling, statusMsg ->
        val remainingMah = snapshot.remainingChargeUah / 1_000f
        val estimatedHours = when {
            snapshot.isCharging -> {
                val missingMah = (snapshot.estimatedCapacityMah - remainingMah).coerceAtLeast(0f)
                val chargeRateMa = snapshot.currentMa.coerceAtLeast(0f)
                if (chargeRateMa > 50f) missingMah / chargeRateMa else null
            }

            else -> {
                val dischargeRateMa = when {
                    avgDischargeMa != null && avgDischargeMa > 20f -> avgDischargeMa
                    snapshot.currentMa < -20f -> abs(snapshot.currentMa)
                    else -> null
                }
                if (dischargeRateMa != null && remainingMah > 0f) {
                    remainingMah / dischargeRateMa
                } else {
                    null
                }
            }
        }

        DashboardUiState(
            snapshot = snapshot,
            estimatedTimeRemainingHours = estimatedHours,
            topConsumers = topApps.take(3),
            isSampling = isSampling,
            statusMessage = statusMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DashboardUiState()
    )

    fun captureSnapshotNow() {
        if (isSamplingFlow.value) return
        viewModelScope.launch {
            isSamplingFlow.value = true
            statusMessageFlow.value = null
            runCatching {
                repository.captureAndPersistSnapshot()
            }.onSuccess {
                statusMessageFlow.value = "Snapshot logged successfully"
            }.onFailure { err ->
                statusMessageFlow.value = "Snapshot failed: ${err.localizedMessage ?: "Unknown error"}"
            }
            isSamplingFlow.value = false
        }
    }

    fun clearStatusMessage() {
        statusMessageFlow.value = null
    }
}
