package com.fitlens.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlens.companion.data.fmtNum
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class ChartPoint(val x: Long, val y: Double, val date: String)

private fun niceStep(range: Double, targetTicks: Int = 4): Double {
    if (range <= 0) return 1.0
    val raw = range / targetTicks
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    val nice = when {
        norm < 1.5 -> 1.0
        norm < 3 -> 2.0
        norm < 7 -> 5.0
        else -> 10.0
    }
    return nice * mag
}

/**
 * Single-series line chart over time. Tap to select the nearest point.
 * Days with progress photos get an orange tick on the time axis and an orange ring on their point.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    photoDays: Set<Long> = emptySet(),
    goal: Double? = null,
    selected: Int? = null,
    onSelect: (Int) -> Unit = {},
    yFormat: (Double) -> String = { fmtNum(it, 1) }
) {
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("No data in this range", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val colors = LocalChartColors.current
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val surface = MaterialTheme.colorScheme.background
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = textColor)

    val x0 = points.first().x
    val x1 = points.last().x
    val xMin = if (x1 == x0) x0 - 1 else x0
    val xMax = if (x1 == x0) x1 + 1 else x1
    var yMin = points.minOf { it.y }
    var yMax = points.maxOf { it.y }
    if (goal != null && goal > 0) { yMin = minOf(yMin, goal); yMax = maxOf(yMax, goal) }
    if (yMax - yMin < 1e-9) { yMin -= 1; yMax += 1 }
    val step = niceStep(yMax - yMin)
    val lo = floor(yMin / step) * step
    val hi = ceil(yMax / step) * step
    val spanDays = xMax - xMin
    val xFmt = if (spanDays < 150) DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    else DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points) {
                detectTapGestures { off ->
                    val left = 44.dp.toPx()
                    val right = size.width - 12.dp.toPx()
                    val frac = ((off.x - left) / (right - left)).coerceIn(0f, 1f)
                    val xv: Double = xMin.toDouble() + frac.toDouble() * (xMax - xMin).toDouble()
                    var best = 0
                    var bestD = Double.MAX_VALUE
                    points.forEachIndexed { i, p ->
                        val d: Double = abs(p.x.toDouble() - xv)
                        if (d < bestD) { bestD = d; best = i }
                    }
                    onSelect(best)
                }
            }
    ) {
        val left = 44.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        fun px(x: Long) = left + ((x - xMin).toFloat() / (xMax - xMin).toFloat()) * (right - left)
        fun py(y: Double) = (bottom - ((y - lo) / (hi - lo)).toFloat() * (bottom - top))

        // Grid + y labels
        var t = lo
        var guard = 0
        while (t <= hi + step / 2 && guard++ < 20) {
            val y = py(t)
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
            val layout = measurer.measure(yFormat(t), labelStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
            t += step
        }
        // x labels: first, middle, last
        val xs = listOf(xMin, (xMin + xMax) / 2, xMax)
        xs.forEachIndexed { i, xv ->
            val label = LocalDate.ofEpochDay(xv).format(xFmt)
            val layout = measurer.measure(label, labelStyle)
            val cx = px(xv)
            val lx = when (i) {
                0 -> cx
                2 -> cx - layout.size.width
                else -> cx - layout.size.width / 2f
            }
            drawText(layout, topLeft = Offset(lx, bottom + 5.dp.toPx()))
        }
        // Goal line
        if (goal != null && goal > 0) {
            val gy = py(goal)
            drawLine(
                colors.goal, Offset(left, gy), Offset(right, gy), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
        }
        // Photo ticks on the time axis
        photoDays.forEach { d ->
            if (d in xMin..xMax) {
                val x = px(d)
                drawLine(colors.accent, Offset(x, bottom - 7.dp.toPx()), Offset(x, bottom), strokeWidth = 2.dp.toPx())
            }
        }
        // Series line
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = px(p.x)
            val y = py(p.y)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, colors.series, style = Stroke(width = 2.dp.toPx()))
        // Markers (only when not crowded), photo days always ringed
        val showMarkers = points.size <= 60
        points.forEach { p ->
            val c = Offset(px(p.x), py(p.y))
            val hasPhoto = p.x in photoDays
            if (showMarkers || hasPhoto) {
                drawCircle(surface, radius = 5.dp.toPx(), center = c)
                drawCircle(colors.series, radius = 4.dp.toPx(), center = c)
            }
            if (hasPhoto) drawCircle(colors.accent, radius = 6.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
        }
        // Selection
        if (selected != null && selected in points.indices) {
            val p = points[selected]
            val c = Offset(px(p.x), py(p.y))
            drawLine(textColor.copy(alpha = 0.6f), Offset(c.x, top), Offset(c.x, bottom), strokeWidth = 1.dp.toPx())
            drawCircle(surface, radius = 7.dp.toPx(), center = c)
            drawCircle(colors.series, radius = 6.dp.toPx(), center = c)
        }
    }
}
