package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.BatteryLogEntity
import com.example.data.model.ChartOverlayMetric
import com.example.data.model.TimeRangeFilter
import com.example.data.repository.PowerRepository
import com.example.util.CsvExporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val selectedFilter: TimeRangeFilter = TimeRangeFilter.LAST_24_HOURS,
    val overlayMetric: ChartOverlayMetric = ChartOverlayMetric.POWER_MW,
    val logs: List<BatteryLogEntity> = emptyList(),
    val isExporting: Boolean = false,
    val exportMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: PowerRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(TimeRangeFilter.LAST_24_HOURS)
    private val overlayMetricFlow = MutableStateFlow(ChartOverlayMetric.POWER_MW)
    private val exportingFlow = MutableStateFlow(false)
    private val exportMessageFlow = MutableStateFlow<String?>(null)

    private val logsFlow = filterFlow.flatMapLatest { filter ->
        flow {
            emit(repository.resolveStartTimestamp(filter))
        }.flatMapLatest { sinceMs ->
            repository.observeBatteryLogsSince(sinceMs)
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        filterFlow,
        overlayMetricFlow,
        logsFlow,
        exportingFlow,
        exportMessageFlow
    ) { filter, metric, logs, exporting, msg ->
        HistoryUiState(
            selectedFilter = filter,
            overlayMetric = metric,
            logs = logs,
            isExporting = exporting,
            exportMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HistoryUiState()
    )

    fun selectFilter(filter: TimeRangeFilter) {
        filterFlow.value = filter
    }

    fun selectOverlayMetric(metric: ChartOverlayMetric) {
        overlayMetricFlow.value = metric
    }

    fun exportCsv(context: Context, onShareIntentReady: (Intent) -> Unit) {
        if (exportingFlow.value) return
        viewModelScope.launch {
            exportingFlow.value = true
            exportMessageFlow.value = null
            runCatching {
                val sinceMs = repository.resolveStartTimestamp(filterFlow.value)
                val batteryLogs = repository.getBatteryLogsForExport(sinceMs)
                val appLogs = repository.getAppUsageLogsForExport(sinceMs)
                CsvExporter.exportToCsvAndCreateShareIntent(context, batteryLogs, appLogs)
            }.onSuccess { shareIntent ->
                onShareIntentReady(shareIntent)
            }.onFailure { err ->
                exportMessageFlow.value = "Export failed: ${err.localizedMessage ?: "Unknown error"}"
            }
            exportingFlow.value = false
        }
    }

    fun clearMessage() {
        exportMessageFlow.value = null
    }
}
