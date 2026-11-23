package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.BatterySnapshot
import com.example.data.model.EstimatedAppDrain
import com.example.data.repository.PowerRepository
import com.example.ui.components.AppIconImage
import com.example.ui.components.BatteryHeroGauge
import com.example.ui.theme.LocalPowerScopeColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.DashboardViewModel
import com.example.util.Formatters

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToAppDrain: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = state.snapshot

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.dashboard_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (snapshot == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item {
                    HeroTelemetryCard(
                        snapshot = snapshot,
                        estimatedHours = state.estimatedTimeRemainingHours
                    )
                }

                item {
                    QuickSystemStatsRow(
                        snapshot = snapshot,
                        isSampling = state.isSampling,
                        statusMessage = state.statusMessage,
                        onSampleNow = viewModel::captureSnapshotNow
                    )
                }

                item {
                    TopConsumersPreviewCard(
                        consumers = state.topConsumers,
                        repository = viewModel.repository,
                        onViewAll = onNavigateToAppDrain
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroTelemetryCard(
    snapshot: BatterySnapshot,
    estimatedHours: Float?
) {
    val ext = LocalPowerScopeColors.current
    val stateColor = if (snapshot.isCharging) ext.emerald else ext.cyan

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ext.cardBorder.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BatteryHeroGauge(
                    batteryPercent = snapshot.batteryPercent,
                    isCharging = snapshot.isCharging,
                    subtitle = if (snapshot.isCharging) snapshot.plugType.label else snapshot.chargeState.label
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = stateColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (snapshot.isCharging) {
                                stringResource(R.string.dashboard_charging)
                            } else {
                                stringResource(R.string.dashboard_discharging)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = stateColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = Formatters.formatPowerMw(snapshot.powerMw),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (snapshot.isCharging) {
                            "Est. full: ${estimatedHours?.let { Formatters.formatHoursMinutes(it) } ?: "Calculating..."}"
                        } else {
                            "Est. left: ${estimatedHours?.let { Formatters.formatHoursMinutes(it) } ?: "Calculating..."}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Cap: ${Formatters.formatMah(snapshot.remainingChargeUah / 1_000f)} / ${snapshot.estimatedCapacityMah.toInt()} mAh",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryMetricTile(
                    label = stringResource(R.string.dashboard_metric_current),
                    value = Formatters.formatCurrentMa(snapshot.currentMa),
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    label = stringResource(R.string.dashboard_metric_voltage),
                    value = Formatters.formatVoltageV(snapshot.voltageMv),
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricTile(
                    label = stringResource(R.string.dashboard_metric_temp),
                    value = Formatters.formatTempC(snapshot.temperatureCelsius),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TelemetryMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickSystemStatsRow(
    snapshot: BatterySnapshot,
    isSampling: Boolean,
    statusMessage: String?,
    onSampleNow: () -> Unit
) {
    val ext = LocalPowerScopeColors.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ext.cardBorder.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusBadgeChip(
                    icon = Icons.Rounded.Smartphone,
                    label = stringResource(R.string.dashboard_status_screen),
                    value = if (snapshot.isScreenInteractive) "Active" else "Off",
                    highlight = snapshot.isScreenInteractive,
                    modifier = Modifier.weight(1f)
                )
                StatusBadgeChip(
                    icon = Icons.Rounded.EnergySavingsLeaf,
                    label = stringResource(R.string.dashboard_status_saver),
                    value = if (snapshot.isPowerSaveMode) "ON" else "OFF",
                    highlight = snapshot.isPowerSaveMode,
                    modifier = Modifier.weight(1f)
                )
                StatusBadgeChip(
                    icon = Icons.Rounded.Thermostat,
                    label = stringResource(R.string.dashboard_status_doze),
                    value = if (snapshot.isDeviceIdleMode) "Idle" else "Awake",
                    highlight = snapshot.isDeviceIdleMode,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onSampleNow,
                enabled = !isSampling,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("log_snapshot_button")
            ) {
                if (isSampling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dashboard_sampling))
                } else {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dashboard_log_snapshot))
                }
            }

            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StatusBadgeChip(
    icon: ImageVector,
    label: String,
    value: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    val ext = LocalPowerScopeColors.current
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlight) ext.amber else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TopConsumersPreviewCard(
    consumers: List<EstimatedAppDrain>,
    repository: PowerRepository,
    onViewAll: () -> Unit
) {
    val ext = LocalPowerScopeColors.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ext.cardBorder.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = ext.amber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.dashboard_top_consumers),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onViewAll,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("see_all_consumers_button")
                ) {
                    Text(stringResource(R.string.dashboard_see_all))
                }
            }

            if (consumers.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_empty_consumers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                consumers.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconImage(
                            packageName = app.packageName,
                            repository = repository,
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "FG ${Formatters.formatDuration(app.foregroundMs)} • Net ${Formatters.formatBytes(app.wifiBytes + app.mobileBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = Formatters.formatMah(app.estimatedMah),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = Formatters.formatPercent(app.drainPercentageShare),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
