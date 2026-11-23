package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.example.data.local.entity.BatteryLogEntity
import com.example.data.model.ChartOverlayMetric
import com.example.ui.theme.LocalPowerScopeColors
import com.example.ui.theme.MonoFontFamily
import com.example.util.Formatters
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun BatteryHeroGauge(
    batteryPercent: Int,
    isCharging: Boolean,
    subtitle: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (batteryPercent.coerceIn(0, 100) / 100f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "batteryGauge"
    )

    val ext = LocalPowerScopeColors.current
    val gaugeColor = when {
        isCharging -> ext.emerald
        batteryPercent <= 15 -> ext.coral
        batteryPercent <= 30 -> ext.amber
        else -> ext.cyan
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = "Battery $batteryPercent percent, $subtitle"
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val strokePx = 13.dp.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val innerTickR = (this.size.minDimension / 2f) - strokePx - 5.dp.toPx()
            val outerTickR = innerTickR - 4.dp.toPx()

            // Instrument precision ticks along the 240-degree sweep
            for (step in 0..24) {
                val angleDeg = 150.0 + (240.0 * step / 24.0)
                val rad = angleDeg * PI / 180.0
                val isMajor = step % 6 == 0
                val rEnd = if (isMajor) outerTickR - 2.dp.toPx() else outerTickR
                drawLine(
                    color = if (isMajor) gaugeColor.copy(alpha = 0.55f) else tickColor,
                    start = Offset(
                        (center.x + innerTickR * cos(rad)).toFloat(),
                        (center.y + innerTickR * sin(rad)).toFloat()
                    ),
                    end = Offset(
                        (center.x + rEnd * cos(rad)).toFloat(),
                        (center.y + rEnd * sin(rad)).toFloat()
                    ),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Background track (240° sweep)
            drawArc(
                color = trackColor,
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Soft outer glow
            drawArc(
                color = gaugeColor.copy(alpha = 0.18f),
                startAngle = 150f,
                sweepAngle = 240f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx + 6.dp.toPx(), cap = StrokeCap.Round)
            )

            // Active progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(gaugeColor.copy(alpha = 0.65f), gaugeColor)
                ),
                startAngle = 150f,
                sweepAngle = 240f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$batteryPercent%",
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = gaugeColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun AppDrainStackedBar(
    foregroundFraction: Float,
    backgroundFraction: Float,
    networkFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val ext = LocalPowerScopeColors.current
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius, size = size)

        val fgW = (size.width * foregroundFraction.coerceIn(0f, 1f))
        val bgW = (size.width * backgroundFraction.coerceIn(0f, 1f - foregroundFraction.coerceIn(0f, 1f)))
        val netW = (size.width * networkFraction.coerceIn(0f, 1f)).coerceAtMost((size.width - fgW - bgW).coerceAtLeast(0f))

        var x = 0f
        if (fgW > 0f) {
            drawRoundRect(
                color = ext.emerald,
                topLeft = Offset(x, 0f),
                size = Size(fgW, size.height),
                cornerRadius = radius
            )
            x += fgW
        }
        if (bgW > 0f) {
            drawRoundRect(
                color = ext.cyan,
                topLeft = Offset(x, 0f),
                size = Size(bgW, size.height),
                cornerRadius = radius
            )
            x += bgW
        }
        if (netW > 0f) {
            drawRoundRect(
                color = ext.amber,
                topLeft = Offset(x, 0f),
                size = Size(netW, size.height),
                cornerRadius = radius
            )
        }
    }
}

@Composable
fun DualMetricTimelineChart(
    logs: List<BatteryLogEntity>,
    overlayMetric: ChartOverlayMetric,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(logs) { mutableIntStateOf(logs.lastIndex.coerceAtLeast(-1)) }
    val ext = LocalPowerScopeColors.current

    if (logs.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(16.dp)
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.history_chart_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val batteryLineColor = ext.emerald
    val overlayLineColor = if (overlayMetric == ChartOverlayMetric.POWER_MW) ext.amber else ext.coral
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val scrubLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    val overlayValues = logs.map {
        if (overlayMetric == ChartOverlayMetric.POWER_MW) it.powerMw else it.temperatureCelsius
    }
    val overlayMin = (overlayValues.minOrNull() ?: 0f).coerceAtMost(
        if (overlayMetric == ChartOverlayMetric.POWER_MW) 0f else 20f
    )
    val overlayMax = (overlayValues.maxOrNull() ?: 100f).let { max ->
        if (max - overlayMin < 1f) overlayMin + 10f else max * 1.1f
    }

    val clampedIdx = selectedIndex.coerceIn(0, logs.lastIndex)
    val selectedLog = logs[clampedIdx]

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
                text = Formatters.formatDateTime(selectedLog.timestamp),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendChip(color = batteryLineColor, text = "${selectedLog.batteryPercent}%")
                val overlayText = if (overlayMetric == ChartOverlayMetric.POWER_MW) {
                    Formatters.formatPowerShort(selectedLog.powerMw)
                } else {
                    Formatters.formatTempC(selectedLog.temperatureCelsius)
                }
                LegendChip(color = overlayLineColor, text = overlayText)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .pointerInput(logs) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        selectedIndex = (ratio * logs.lastIndex).roundToInt().coerceIn(0, logs.lastIndex)
                    }
                }
                .pointerInput(logs) {
                    detectHorizontalDragGestures { change, _ ->
                        val ratio = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        selectedIndex = (ratio * logs.lastIndex).roundToInt().coerceIn(0, logs.lastIndex)
                    }
                }
        ) {
            val padTop = 12.dp.toPx()
            val padBottom = 16.dp.toPx()
            val padHorizontal = 8.dp.toPx()
            val chartW = size.width - padHorizontal * 2
            val chartH = size.height - padTop - padBottom

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            for (i in 0..4) {
                val y = padTop + chartH * (i / 4f)
                drawLine(
                    color = gridColor,
                    start = Offset(padHorizontal, y),
                    end = Offset(padHorizontal + chartW, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
            }

            fun xFor(index: Int): Float =
                padHorizontal + (index.toFloat() / logs.lastIndex.coerceAtLeast(1)) * chartW

            fun yForBattery(pct: Int): Float =
                padTop + chartH * (1f - (pct.coerceIn(0, 100) / 100f))

            fun yForOverlay(value: Float): Float {
                val normalized = ((value - overlayMin) / (overlayMax - overlayMin)).coerceIn(0f, 1f)
                return padTop + chartH * (1f - normalized)
            }

            val batteryPath = Path()
            val batteryFillPath = Path()
            val overlayPath = Path()

            logs.forEachIndexed { idx, log ->
                val x = xFor(idx)
                val yBat = yForBattery(log.batteryPercent)
                val yOv = yForOverlay(overlayValues[idx])

                if (idx == 0) {
                    batteryPath.moveTo(x, yBat)
                    batteryFillPath.moveTo(x, padTop + chartH)
                    batteryFillPath.lineTo(x, yBat)
                    overlayPath.moveTo(x, yOv)
                } else {
                    batteryPath.lineTo(x, yBat)
                    batteryFillPath.lineTo(x, yBat)
                    overlayPath.lineTo(x, yOv)
                }
            }

            batteryFillPath.lineTo(xFor(logs.lastIndex), padTop + chartH)
            batteryFillPath.close()

            drawPath(
                path = batteryFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(batteryLineColor.copy(alpha = 0.25f), Color.Transparent),
                    startY = padTop,
                    endY = padTop + chartH
                )
            )

            drawPath(
                path = batteryPath,
                color = batteryLineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            drawPath(
                path = overlayPath,
                color = overlayLineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val selX = xFor(clampedIdx)
            val selYBat = yForBattery(selectedLog.batteryPercent)
            val selYOv = yForOverlay(overlayValues[clampedIdx])

            drawLine(
                color = scrubLineColor,
                start = Offset(selX, padTop),
                end = Offset(selX, padTop + chartH),
                strokeWidth = 1.dp.toPx()
            )
            drawCircle(color = batteryLineColor, radius = 5.dp.toPx(), center = Offset(selX, selYBat))
            drawCircle(color = overlayLineColor, radius = 5.dp.toPx(), center = Offset(selX, selYOv))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Formatters.formatShortTime(logs.first().timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = Formatters.formatShortTime(logs[logs.size / 2].timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = Formatters.formatShortTime(logs.last().timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendChip(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
