package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PowerRepository
import com.example.telemetry.BatteryTrendAnalyzer
import com.example.telemetry.BatteryTrendReport
import com.example.telemetry.TrendWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrendsUiState(
    val selectedWindow: TrendWindow = TrendWindow.DAYS_7,
    val report: BatteryTrendReport = BatteryTrendAnalyzer().analyze(emptyList()),
    val isSampling: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModel(
    private val repository: PowerRepository,
    private val analyzer: BatteryTrendAnalyzer = BatteryTrendAnalyzer()
) : ViewModel() {

    private val windowFlow = MutableStateFlow(TrendWindow.DAYS_7)
    private val isSamplingFlow = MutableStateFlow(false)

    private val reportFlow = windowFlow.flatMapLatest { window ->
        val durationMs = window.durationMs
        if (durationMs == null) {
            repository.observeAllBatteryLogs()
        } else {
            val sinceMs = System.currentTimeMillis() - durationMs
            repository.observeBatteryLogsSince(sinceMs)
        }
    }.map { logs ->
        analyzer.analyze(logs)
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<TrendsUiState> = combine(
        windowFlow,
        reportFlow,
        isSamplingFlow
    ) { window, report, sampling ->
        TrendsUiState(
            selectedWindow = window,
            report = report,
            isSampling = sampling
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TrendsUiState()
    )

    fun selectWindow(window: TrendWindow) {
        windowFlow.value = window
    }

    fun captureSampleNow() {
        if (isSamplingFlow.value) return
        viewModelScope.launch {
            isSamplingFlow.value = true
            runCatching { repository.captureAndPersistSnapshot() }
            isSamplingFlow.value = false
        }
    }
}
