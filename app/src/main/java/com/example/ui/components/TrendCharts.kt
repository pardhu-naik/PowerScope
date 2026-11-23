package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.telemetry.ChargingCycleSummary
import com.example.telemetry.TrendBucketPoint
import com.example.telemetry.TrendDirection
import com.example.ui.theme.LocalPowerScopeColors
import com.example.ui.theme.MonoFontFamily
import com.example.util.Formatters
import kotlin.math.floor

@Composable
fun HealthScoreArcBadge(
    score: Int,
    direction: TrendDirection,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    val ext = LocalPowerScopeColors.current
    val targetFraction = if (direction == TrendDirection.INSUFFICIENT_DATA && score == 0) {
        0f
    } else {
        (score.coerceIn(0, 100) / 100f)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "healthScoreArc"
    )

    val ringColor = when (direction) {
        TrendDirection.IMPROVING -> ext.emerald
        TrendDirection.STABLE -> ext.cyan
        TrendDirection.DECLINING -> ext.coral
        TrendDirection.INSUFFICIENT_DATA -> ext.amber
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = if (score > 0) {
                    "Battery performance score $score out of 100, ${direction.label}"
                } else {
                    "Battery performance score gathering data"
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            val strokePx = 9.dp.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (animatedProgress > 0f) {
                drawArc(
                    color = ringColor.copy(alpha = 0.2f),
                    startAngle = 135f,
                    sweepAngle = 270f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx + 5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(ringColor.copy(alpha = 0.65f), ringColor)
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (score > 0) "$score" else "--",
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.trends_score_label),
                style = MaterialTheme.typography.labelSmall,
                color = ringColor
            )
        }
    }
}

@Composable
fun EfficiencySlopeChart(
    buckets: List<TrendBucketPoint>,
    regressionStartMw: Float?,
    regressionEndMw: Float?,
    direction: TrendDirection,
    modifier: Modifier = Modifier
) {
    val ext = LocalPowerScopeColors.current
    var selectedIdx by remember(buckets) { mutableIntStateOf(buckets.lastIndex.coerceAtLeast(0)) }

    if (buckets.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(14.dp)
                )
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.trends_chart_need_more_samples),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val clampedIdx = selectedIdx.coerceIn(0, buckets.lastIndex)
    val selectedBucket = buckets[clampedIdx]

    val maxMw = (buckets.maxOf { it.avgDischargePowerMw }
        .coerceAtLeast(regressionStartMw ?: 0f)
        .coerceAtLeast(regressionEndMw ?: 0f) * 1.18f)
        .coerceAtLeast(100f)

    val barColor = ext.cyan
    val selectedBarColor = ext.emerald
    val slopeColor = when (direction) {
        TrendDirection.DECLINING -> ext.coral
        TrendDirection.IMPROVING -> ext.emerald
        else -> ext.amber
    }
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Formatters.formatDateTime(selectedBucket.endTimestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrendLegendDot(
                    color = selectedBarColor,
                    text = Formatters.formatPowerShort(selectedBucket.avgDischargePowerMw)
                )
                TrendLegendDot(
                    color = ext.amber,
                    text = Formatters.formatTempC(selectedBucket.avgTempCelsius)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(185.dp)
                .pointerInput(buckets) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 0.999f)
                        selectedIdx = floor(ratio * buckets.size).toInt().coerceIn(0, buckets.lastIndex)
                    }
                }
        ) {
            val padTop = 12.dp.toPx()
            val padBottom = 10.dp.toPx()
            val padH = 8.dp.toPx()
            val chartW = size.width - padH * 2
            val chartH = size.height - padTop - padBottom

            val gridDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            for (i in 0..3) {
                val y = padTop + chartH * (i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(padH, y),
                    end = Offset(padH + chartW, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = gridDash
                )
            }

            val slotW = chartW / buckets.size
            val barW = (slotW * 0.56f).coerceIn(8.dp.toPx(), 34.dp.toPx())
            val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

            buckets.forEachIndexed { idx, point ->
                val norm = (point.avgDischargePowerMw / maxMw).coerceIn(0.04f, 1f)
                val barH = chartH * norm
                val x = padH + idx * slotW + (slotW - barW) / 2f
                val y = padTop + chartH - barH
                val active = idx == clampedIdx

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = if (active) {
                            listOf(selectedBarColor, selectedBarColor.copy(alpha = 0.45f))
                        } else {
                            listOf(barColor.copy(alpha = 0.8f), barColor.copy(alpha = 0.25f))
                        },
                        startY = y,
                        endY = padTop + chartH
                    ),
                    topLeft = Offset(x, y),
                    size = Size(barW, barH),
                    cornerRadius = corner
                )
            }

            // Linear regression trendline across bucket centers
            if (regressionStartMw != null && regressionEndMw != null) {
                val startX = padH + slotW / 2f
                val endX = padH + (buckets.lastIndex) * slotW + slotW / 2f
                val startY = padTop + chartH * (1f - (regressionStartMw / maxMw).coerceIn(0f, 1f))
                val endY = padTop + chartH * (1f - (regressionEndMw / maxMw).coerceIn(0f, 1f))

                drawLine(
                    color = slopeColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
                drawCircle(color = slopeColor, radius = 4.dp.toPx(), center = Offset(startX, startY))
                drawCircle(color = slopeColor, radius = 4.dp.toPx(), center = Offset(endX, endY))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Formatters.formatShortTime(buckets.first().startTimestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TrendLegendDot(
                color = slopeColor,
                text = stringResource(R.string.trends_regression_legend)
            )
            Text(
                text = Formatters.formatShortTime(buckets.last().endTimestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ChargingCycleDurationChart(
    cycles: List<ChargingCycleSummary>,
    modifier: Modifier = Modifier
) {
    val ext = LocalPowerScopeColors.current
    val recentCycles = remember(cycles) { cycles.takeLast(8) }
    var selectedIdx by remember(recentCycles) { mutableIntStateOf(recentCycles.lastIndex.coerceAtLeast(0)) }

    if (recentCycles.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(14.dp)
                )
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.trends_cycles_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val clampedIdx = selectedIdx.coerceIn(0, recentCycles.lastIndex)
    val selectedCycle = recentCycles[clampedIdx]
    val maxDurationMs = recentCycles.maxOf { it.durationMs }.coerceAtLeast(60_000L)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selectedCycle.startPercent}% -> ${selectedCycle.endPercent}% (+${selectedCycle.percentGained}%)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrendLegendDot(
                    color = ext.emerald,
                    text = Formatters.formatDuration(selectedCycle.durationMs)
                )
                TrendLegendDot(
                    color = if (selectedCycle.peakTempCelsius >= 39f) ext.coral else ext.amber,
                    text = Formatters.formatTempC(selectedCycle.peakTempCelsius)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
                .pointerInput(recentCycles) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 0.999f)
                        selectedIdx = floor(ratio * recentCycles.size).toInt().coerceIn(0, recentCycles.lastIndex)
                    }
                }
        ) {
            val padTop = 10.dp.toPx()
            val padBottom = 8.dp.toPx()
            val padH = 8.dp.toPx()
            val chartW = size.width - padH * 2
            val chartH = size.height - padTop - padBottom

            val slotW = chartW / recentCycles.size
            val barW = (slotW * 0.58f).coerceIn(10.dp.toPx(), 38.dp.toPx())
            val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

            recentCycles.forEachIndexed { idx, cycle ->
                val norm = (cycle.durationMs.toFloat() / maxDurationMs.toFloat()).coerceIn(0.08f, 1f)
                val barH = chartH * norm
                val x = padH + idx * slotW + (slotW - barW) / 2f
                val y = padTop + chartH - barH

                val baseColor = when {
                    cycle.peakTempCelsius >= 40f -> ext.coral
                    cycle.peakTempCelsius >= 36f -> ext.amber
                    else -> ext.emerald
                }
                val alphaTop = if (idx == clampedIdx) 1f else 0.6f
                val alphaBottom = if (idx == clampedIdx) 0.5f else 0.2f

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(baseColor.copy(alpha = alphaTop), baseColor.copy(alpha = alphaBottom)),
                        startY = y,
                        endY = padTop + chartH
                    ),
                    topLeft = Offset(x, y),
                    size = Size(barW, barH),
                    cornerRadius = radius
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Cycle ${clampedIdx + 1} of ${recentCycles.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = Formatters.formatDateTime(selectedCycle.startTimestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrendLegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
