package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.telemetry.BatteryInsight
import com.example.telemetry.BatteryTrendReport
import com.example.telemetry.InsightSeverity
import com.example.telemetry.TrendDirection
import com.example.telemetry.TrendWindow
import com.example.ui.components.ChargingCycleDurationChart
import com.example.ui.components.EfficiencySlopeChart
import com.example.ui.components.HealthScoreArcBadge
import com.example.ui.theme.LocalPowerScopeColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.viewmodel.TrendsViewModel
import com.example.util.Formatters
import kotlin.math.roundToInt

@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val report = state.report
    val ext = LocalPowerScopeColors.current

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
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.trends_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(R.string.trends_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = viewModel::captureSampleNow,
                        enabled = !state.isSampling,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("trends_sample_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.app_drain_sample_now)
                        )
                    }
                }
            }

            // Lookback Window Chips
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrendWindow.entries.forEach { window ->
                        FilterChip(
                            selected = state.selectedWindow == window,
                            onClick = { viewModel.selectWindow(window) },
                            label = { Text(window.label) },
                            modifier = Modifier.testTag("trend_window_${window.name.lowercase()}")
                        )
                    }
                }
            }

            // Hero Battery Health & Trend Verdict Card
            item {
                TrendVerdictHeroCard(report = report)
            }

            // Long-Term Key Metrics Grid (2x2)
            item {
                LongTermKpiGrid(report = report)
            }

            // Discharge Efficiency & Regression Trendline Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
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
                            Text(
                                text = stringResource(R.string.trends_efficiency_chart_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            report.dischargePowerDriftPct?.let { drift ->
                                val sign = if (drift >= 0f) "+" else ""
                                val pillColor = when {
                                    drift > 8f -> ext.coral
                                    drift < -8f -> ext.emerald
                                    else -> ext.cyan
                                }
                                Surface(
                                    color = pillColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${sign}${drift.roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = pillColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.trends_efficiency_chart_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        EfficiencySlopeChart(
                            buckets = report.trendBuckets,
                            regressionStartMw = report.regressionStartMw,
                            regressionEndMw = report.regressionEndMw,
                            direction = report.overallDirection
                        )
                    }
                }
            }

            // Charging Cycle Duration Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.BatteryChargingFull,
                                    contentDescription = null,
                                    tint = ext.emerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.trends_charging_cycles_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "${report.chargingCycles.size} detected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CycleSummaryTile(
                                label = stringResource(R.string.trends_avg_cycle_duration),
                                value = report.avgChargeCycleDurationMs?.let {
                                    Formatters.formatDuration(it)
                                } ?: "--",
                                modifier = Modifier.weight(1f)
                            )
                            CycleSummaryTile(
                                label = stringResource(R.string.trends_est_full_charge),
                                value = report.avgEstimatedFullChargeMs?.let {
                                    Formatters.formatDuration(it)
                                } ?: "--",
                                modifier = Modifier.weight(1f)
                            )
                            CycleSummaryTile(
                                label = stringResource(R.string.trends_avg_charge_power),
                                value = report.avgChargePowerMw?.let {
                                    Formatters.formatPowerShort(it)
                                } ?: "--",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        ChargingCycleDurationChart(cycles = report.chargingCycles)
                    }
                }
            }

            // Key Insights Header
            item {
                Text(
                    text = stringResource(R.string.trends_insights_section),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(report.insights, key = { it.id }) { insight ->
                BatteryInsightCard(insight = insight)
            }
        }
    }
}

@Composable
private fun TrendVerdictHeroCard(report: BatteryTrendReport) {
    val ext = LocalPowerScopeColors.current
    val badgeColor = when (report.overallDirection) {
        TrendDirection.IMPROVING -> ext.emerald
        TrendDirection.STABLE -> ext.cyan
        TrendDirection.DECLINING -> ext.coral
        TrendDirection.INSUFFICIENT_DATA -> ext.amber
    }
    val trendIcon = when (report.overallDirection) {
        TrendDirection.IMPROVING -> Icons.Rounded.TrendingUp
        TrendDirection.STABLE -> Icons.Rounded.TrendingFlat
        TrendDirection.DECLINING -> Icons.Rounded.TrendingDown
        TrendDirection.INSUFFICIENT_DATA -> Icons.Rounded.Info
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ext.cardBorder.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trend_verdict_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HealthScoreArcBadge(
                score = report.performanceScore,
                direction = report.overallDirection,
                size = 98.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = trendIcon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = report.overallDirection.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                Text(
                    text = report.headlineSummary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = report.subSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LongTermKpiGrid(report: BatteryTrendReport) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiStatCard(
                label = stringResource(R.string.trends_kpi_avg_draw),
                value = if (report.sampleCount > 0) Formatters.formatPowerShort(report.avgDischargePowerMw) else "--",
                caption = report.avgDrainPctPerHour?.let {
                    String.format(java.util.Locale.US, "%.1f%%/hr drain", it)
                } ?: "${report.sampleCount} samples",
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                label = stringResource(R.string.trends_kpi_thermal),
                value = if (report.sampleCount > 0) Formatters.formatTempC(report.avgTempCelsius) else "--",
                caption = if (report.sampleCount > 0) {
                    "Peak ${Formatters.formatTempC(report.peakTempCelsius)} • ${report.thermalStressSharePct.roundToInt()}% warm"
                } else {
                    "No thermal logs"
                },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiStatCard(
                label = stringResource(R.string.trends_kpi_voltage),
                value = if (report.avgVoltageMv > 0) Formatters.formatVoltageV(report.avgVoltageMv) else "--",
                caption = if (report.spanHours >= 1f) {
                    String.format(java.util.Locale.US, "%.1fh logged span", report.spanHours)
                } else {
                    "Current baseline"
                },
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                label = stringResource(R.string.trends_kpi_standby_vs_active),
                value = report.standbyAvgPowerMw?.let { Formatters.formatPowerShort(it) } ?: "--",
                caption = report.screenOnAvgPowerMw?.let {
                    "Active ${Formatters.formatPowerShort(it)}"
                } ?: "Idle draw pending",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiStatCard(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier
) {
    val ext = LocalPowerScopeColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ext.cardBorder.copy(alpha = 0.65f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CycleSummaryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BatteryInsightCard(insight: BatteryInsight) {
    val ext = LocalPowerScopeColors.current
    val accent = when (insight.severity) {
        InsightSeverity.GOOD -> ext.emerald
        InsightSeverity.INFO -> ext.cyan
        InsightSeverity.WARNING -> ext.amber
        InsightSeverity.ALERT -> ext.coral
    }
    val icon = when (insight.severity) {
        InsightSeverity.GOOD -> Icons.Rounded.CheckCircle
        InsightSeverity.INFO -> Icons.Rounded.Info
        InsightSeverity.WARNING, InsightSeverity.ALERT -> Icons.Rounded.Warning
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trend_insight_${insight.id}")
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
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = insight.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = insight.metricBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = insight.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
