package com.betteryou.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ChartPoint(val x: Instant, val y: Double)

data class ChartSeries(val label: String, val color: Color, val points: List<ChartPoint>)

data class BarEntry(val label: String, val value: Double, val color: Color, val valueLabel: String? = null)

private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

fun formatShortDate(instant: Instant): String = shortDate.format(instant.atZone(ZoneId.systemDefault()))

/** Line chart over time with a fixed integer y-range. */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    yRange: IntRange = 1..5,
    height: Dp = 200.dp,
    yLabel: (Int) -> String = { it.toString() },
    showLegend: Boolean = series.size > 1,
    description: String = "Line chart",
) {
    val allX = series.flatMap { s -> s.points.map { it.x.toEpochMilli() } }
    if (allX.isEmpty()) return
    val minX = allX.min()
    val maxX = allX.max()

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
        ) {
            val yLabels = yRange.map { textMeasurer.measure(yLabel(it), labelStyle) }
            val labelGap = 8.dp.toPx()
            val left = yLabels.maxOf { it.size.width } + labelGap
            val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx()
            val xLabelHeight = textMeasurer.measure("0", labelStyle).size.height
            val bottom = size.height - xLabelHeight - 6.dp.toPx()

            fun xPos(x: Long): Float =
                if (maxX == minX) (left + right) / 2 else left + (x - minX).toFloat() / (maxX - minX) * (right - left)

            fun yPos(y: Double): Float =
                bottom - ((y - yRange.first) / (yRange.last - yRange.first)).toFloat() * (bottom - top)

            yRange.forEachIndexed { index, value ->
                val y = yPos(value.toDouble())
                drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                val label = yLabels[index]
                drawText(label, topLeft = Offset(left - labelGap - label.size.width, y - label.size.height / 2f))
            }

            val ticks = if (minX == maxX) listOf(minX) else listOf(minX, maxX)
            ticks.forEach { x ->
                val label = textMeasurer.measure(formatShortDate(Instant.ofEpochMilli(x)), labelStyle)
                val tx = (xPos(x) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(tx, bottom + 6.dp.toPx()))
            }

            series.forEach { s ->
                val points = s.points.sortedBy { it.x }.map { Offset(xPos(it.x.toEpochMilli()), yPos(it.y)) }
                if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path,
                        s.color,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                points.forEach { drawCircle(s.color, radius = 4.dp.toPx(), center = it) }
            }
        }
        if (showLegend) Legend(series.map { it.label to it.color })
    }
}

@Composable
fun Legend(entries: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        entries.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Dot(color)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Vertical bar chart with a label under each bar. */
@Composable
fun BarChart(
    entries: List<BarEntry>,
    maxValue: Double,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    description: String = "Bar chart",
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { entry ->
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (entry.valueLabel != null) {
                        Text(entry.valueLabel, style = MaterialTheme.typography.labelSmall)
                    }
                    val fraction = if (maxValue <= 0) 0.0 else entry.value / maxValue
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceIn(0.02, 1.0).toFloat())
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(Brush.verticalGradient(listOf(entry.color, entry.color.copy(alpha = 0.55f)))),
                    )
                }
                Text(
                    entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
