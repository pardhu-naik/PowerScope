package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppSortOrder
import com.example.data.model.EstimatedAppDrain
import com.example.data.model.TimeRangeFilter
import com.example.data.repository.PowerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppDrainUiState(
    val selectedFilter: TimeRangeFilter = TimeRangeFilter.LAST_24_HOURS,
    val sortOrder: AppSortOrder = AppSortOrder.ESTIMATED_MAH,
    val rankedApps: List<EstimatedAppDrain> = emptyList(),
    val totalDrainMah: Float = 0f,
    val isRefreshing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppDrainViewModel(
    val repository: PowerRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(TimeRangeFilter.LAST_24_HOURS)
    private val sortOrderFlow = MutableStateFlow(AppSortOrder.ESTIMATED_MAH)
    private val refreshingFlow = MutableStateFlow(false)

    private val appsFlow = filterFlow.flatMapLatest { filter ->
        flow {
            val sinceMs = repository.resolveStartTimestamp(filter)
            emit(sinceMs)
        }.flatMapLatest { sinceMs ->
            repository.observeAggregatedAppDrainSince(sinceMs)
        }
    }

    val uiState: StateFlow<AppDrainUiState> = combine(
        filterFlow,
        sortOrderFlow,
        appsFlow,
        refreshingFlow
    ) { filter, sort, apps, refreshing ->
        val sorted = when (sort) {
            AppSortOrder.ESTIMATED_MAH -> apps.sortedByDescending { it.estimatedMah }
            AppSortOrder.ACTIVE_TIME -> apps.sortedByDescending { it.foregroundMs + it.backgroundServiceMs }
        }
        AppDrainUiState(
            selectedFilter = filter,
            sortOrder = sort,
            rankedApps = sorted,
            totalDrainMah = sorted.sumOf { it.estimatedMah.toDouble() }.toFloat(),
            isRefreshing = refreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AppDrainUiState()
    )

    fun selectFilter(filter: TimeRangeFilter) {
        filterFlow.value = filter
    }

    fun selectSortOrder(order: AppSortOrder) {
        sortOrderFlow.value = order
    }

    fun refreshNow() {
        if (refreshingFlow.value) return
        viewModelScope.launch {
            refreshingFlow.value = true
            runCatching { repository.captureAndPersistSnapshot() }
            refreshingFlow.value = false
        }
    }
}
