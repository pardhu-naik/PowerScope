package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AppSortOrder
import com.example.data.model.EstimatedAppDrain
import com.example.data.model.TimeRangeFilter
import com.example.data.repository.PowerRepository
import com.example.ui.components.AppDrainStackedBar
import com.example.ui.components.AppIconImage
import com.example.ui.theme.LocalPowerScopeColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.AppDrainViewModel
import com.example.util.Formatters
import com.example.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrainScreen(
    viewModel: AppDrainViewModel,
    hasUsageAccess: Boolean
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ext = LocalPowerScopeColors.current
    var selectedApp by remember { mutableStateOf<EstimatedAppDrain?>(null) }

    val availableFilters = listOf(
        TimeRangeFilter.SINCE_LAST_CHARGE,
        TimeRangeFilter.LAST_24_HOURS,
        TimeRangeFilter.LAST_7_DAYS
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_drain_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Attributed total: ${Formatters.formatMah(state.totalDrainMah)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = viewModel::refreshNow,
                        enabled = !state.isRefreshing,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sample_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.app_drain_sample_now)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableFilters.forEach { filter ->
                        FilterChip(
                            selected = state.selectedFilter == filter,
                            onClick = { viewModel.selectFilter(filter) },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("drain_filter_${filter.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AppSortOrder.entries.forEachIndexed { index, order ->
                        SegmentedButton(
                            selected = state.sortOrder == order,
                            onClick = { viewModel.selectSortOrder(order) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AppSortOrder.entries.size
                            ),
                            label = { Text(order.label) },
                            modifier = Modifier.testTag("drain_sort_${order.name.lowercase()}")
                        )
                    }
                }

                if (!hasUsageAccess) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ext.amber.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, ext.amber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.DataUsage,
                                    contentDescription = null,
                                    tint = ext.amber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.app_drain_permission_banner_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = stringResource(R.string.app_drain_permission_banner_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    context.startActivity(PermissionHelper.createUsageAccessSettingsIntent())
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("app_drain_grant_usage_button")
                            ) {
                                Text(stringResource(R.string.onboarding_grant_usage))
                            }
                        }
                    }
                }
            }

            if (state.rankedApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.app_drain_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.rankedApps, key = { it.packageName }) { app ->
                        AppDrainItemCard(
                            app = app,
                            repository = viewModel.repository,
                            onClick = { selectedApp = app }
                        )
                    }
                }
            }
        }
    }

    val activeSheetApp = selectedApp
    if (activeSheetApp != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedApp = null },
            sheetState = sheetState
        ) {
            AppResourceBreakdownSheet(
                app = activeSheetApp,
                repository = viewModel.repository
            )
        }
    }
}

@Composable
private fun AppDrainItemCard(
    app: EstimatedAppDrain,
    repository: PowerRepository,
    onClick: () -> Unit
) {
    val ext = LocalPowerScopeColors.current
    val fgScore = app.foregroundMs / 1000f
    val bgScore = (app.backgroundServiceMs / 1000f) * 0.25f
    val netScore = (app.mobileBytes / 1_048_576f) * 4.5f + (app.wifiBytes / 1_048_576f) * 1.2f
    val sumScore = (fgScore + bgScore + netScore).coerceAtLeast(0.001f)
    val shareScale = (app.drainPercentageShare / 100f).coerceIn(0.02f, 1f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ext.cardBorder.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("app_drain_card_${app.packageName}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(
                    packageName = app.packageName,
                    repository = repository,
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatMah(app.estimatedMah),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = Formatters.formatPercent(app.drainPercentageShare),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            AppDrainStackedBar(
                foregroundFraction = (fgScore / sumScore) * shareScale,
                backgroundFraction = (bgScore / sumScore) * shareScale,
                networkFraction = (netScore / sumScore) * shareScale
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "FG: ${Formatters.formatDuration(app.foregroundMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "BG: ${Formatters.formatDuration(app.backgroundServiceMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Data: ${Formatters.formatBytes(app.wifiBytes + app.mobileBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppResourceBreakdownSheet(
    app: EstimatedAppDrain,
    repository: PowerRepository
) {
    val fgScore = app.foregroundMs / 1000f
    val bgScore = (app.backgroundServiceMs / 1000f) * 0.25f
    val netScore = (app.mobileBytes / 1_048_576f) * 4.5f + (app.wifiBytes / 1_048_576f) * 1.2f
    val sumScore = (fgScore + bgScore + netScore).coerceAtLeast(0.001f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(
                packageName = app.packageName,
                repository = repository,
                size = 48.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = app.appName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.app_drain_breakdown_composition),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppDrainStackedBar(
            foregroundFraction = fgScore / sumScore,
            backgroundFraction = bgScore / sumScore,
            networkFraction = netScore / sumScore,
            height = 12.dp
        )

        BreakdownRow(stringResource(R.string.app_drain_label_power), Formatters.formatMah(app.estimatedMah))
        BreakdownRow(stringResource(R.string.app_drain_label_share), Formatters.formatPercent(app.drainPercentageShare, 2))
        BreakdownRow(stringResource(R.string.app_drain_label_fg), Formatters.formatDuration(app.foregroundMs))
        BreakdownRow(stringResource(R.string.app_drain_label_bg), Formatters.formatDuration(app.backgroundServiceMs))
        BreakdownRow(stringResource(R.string.app_drain_label_wifi), Formatters.formatBytes(app.wifiBytes))
        BreakdownRow(stringResource(R.string.app_drain_label_mobile), Formatters.formatBytes(app.mobileBytes))

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
            fontWeight = FontWeight.SemiBold
        )
    }
}
