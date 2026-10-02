package com.fitlens.companion.ui

import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.fmtNum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

// Shared chart components (#50). Every graph in FitLens is drawn by one of these, so they look and behave alike.
// Colours come only from LocalChartColors (ui/Theme.kt).

data class ChartPoint(val x: Long, val y: Double, val date: String)

/**
 * How a graph is drawn (owner, 2026-10-02, #137; bar charts are allowed again, reversing #116). Every kind shares the
 * grid, baseline and colours: [LINE] is a red line over the gold fill, [AREA] the fill with a thin red edge and no
 * markers, [BAR] gold columns with red tops, [STEP] a red step line over the fill. The user picks one per graph and
 * it's remembered ([rememberChartKind]).
 */
enum class ChartKind(val key: String, val label: String) {
    LINE("line", "Line"),
    BAR("bar", "Bar"),
    AREA("area", "Area"),
    STEP("step", "Step");

    companion object {
        fun of(key: String?): ChartKind? = entries.firstOrNull { it.key == key }

        /** The remembered choices, "graph=kind;graph=kind" in `PortableSettings.graphKinds`; unknown kinds are skipped. */
        fun decode(s: String?): Map<String, ChartKind> =
            s.orEmpty().split(';').mapNotNull { part ->
                val at = part.lastIndexOf('=')
                if (at <= 0) null else of(part.substring(at + 1))?.let { part.substring(0, at) to it }
            }.toMap()

        fun encode(m: Map<String, ChartKind>): String? =
            m.entries.filter { it.key.isNotBlank() }.joinToString(";") { "${it.key.replace(";", ",")}=${it.value.key}" }.ifEmpty { null }
    }
}

/**
 * The chart kind chosen for the graph [graphId] (#137), and a function that remembers a new choice. Choices travel in
 * `.fitlens` backups with the other preferences. A graph never chosen is drawn as [default].
 */
@Composable
fun rememberChartKind(graphId: String, default: ChartKind = ChartKind.LINE): Pair<ChartKind, (ChartKind) -> Unit> {
    val prefs by Settings.portable.collectAsState()
    val kind = ChartKind.decode(prefs.graphKinds)[graphId] ?: default
    return kind to { k ->
        Settings.updatePortable { p -> p.copy(graphKinds = ChartKind.encode(ChartKind.decode(p.graphKinds) + (graphId to k))) }
    }
}

/** One series on a [FitChart]. Series 2 onwards get their own colour and marker shape, so colour is never the only cue. */
data class LineSeries(val label: String, val points: List<ChartPoint>)

/** The selected point: which series, and the point's index within it. */
data class ChartSelection(val series: Int, val index: Int)

/** A least-squares trend line, y = intercept + slope × x, with x in epoch days. */
data class TrendLine(val slope: Double, val intercept: Double) {
    fun at(x: Long): Double = intercept + slope * x

    /** The change over an average month (30.44 days). */
    val perMonth: Double get() = slope * 30.44
}

/** The least-squares trend through [points], or null when there aren't two distinct days to fit. */
fun trendOf(points: List<ChartPoint>): TrendLine? {
    if (points.size < 2) return null
    val n = points.size.toDouble()
    val mx = points.sumOf { it.x.toDouble() } / n
    val my = points.sumOf { it.y } / n
    var sxx = 0.0
    var sxy = 0.0
    points.forEach { p ->
        val dx = p.x - mx
        sxx += dx * dx
        sxy += dx * (p.y - my)
    }
    if (sxx == 0.0) return null
    val slope = sxy / sxx
    return TrendLine(slope, my - slope * mx)
}

/**
 * The part of a chart that's shown, as fractions of the whole (0..1): [from]..[to] of the time range, and
 * [yFrom]..[yTo] of the value range the visible points need, from the bottom (#96). Full screen changes it with pinch
 * and pan; everywhere else it's the whole chart.
 */
data class ChartViewport(val from: Float = 0f, val to: Float = 1f, val yFrom: Float = 0f, val yTo: Float = 1f) {
    val isFull: Boolean get() = from <= 0f && to >= 1f && valuesFull
    val valuesFull: Boolean get() = yFrom <= 0f && yTo >= 1f

    /**
     * Zooms the time axis by [zoom] around [centroid] and pans it by [pan], both as fractions of the visible width.
     * Dragging right (a positive [pan]) moves back in time.
     */
    fun transform(centroid: Float, pan: Float, zoom: Float): ChartViewport {
        val (start, end) = zoomRange(from, to, centroid, pan, zoom, MIN_SPAN)
        return copy(from = start, to = end)
    }

    /**
     * Zooms the values by [zoom] around [centroid] (a fraction of the height from the bottom) and pans them by [pan]
     * (a fraction of the height). A positive [pan], dragging down, shows higher values (#96).
     */
    fun transformY(centroid: Float, pan: Float, zoom: Float): ChartViewport {
        val (start, end) = zoomRange(yFrom, yTo, centroid, -pan, zoom, MIN_Y_SPAN)
        return copy(yFrom = start, yTo = end)
    }

    private companion object {
        const val MIN_SPAN = 0.02f
        /** Values zoom in to a twentieth of their range at most, so a line never becomes a few flat pixels. */
        const val MIN_Y_SPAN = 0.05f

        fun zoomRange(from: Float, to: Float, centroid: Float, pan: Float, zoom: Float, minSpan: Float): Pair<Float, Float> {
            val span = to - from
            val c = centroid.coerceIn(0f, 1f)
            val newSpan = (span / zoom.coerceAtLeast(0.01f)).coerceIn(minSpan, 1f)
            val anchor = from + c * span
            val start = (anchor - c * newSpan - pan * newSpan).coerceIn(0f, 1f - newSpan)
            return start to start + newSpan
        }
    }
}

/**
 * Works out chart data off the main thread, once per set of [keys] (normally the data snapshot and the chart's
 * options), so years of history stay smooth. Returns null until the first result is ready, then keeps showing the
 * last result while a new one is worked out.
 */
@Composable
fun <T> rememberChartData(vararg keys: Any?, compute: () -> T): T? {
    val state = produceState<T?>(null, *keys) {
        value = withContext(Dispatchers.Default) { compute() }
    }
    return state.value
}

/** The colour of series [i]: the brand palette in order, then the palette again, lighter. */
fun ChartColors.seriesColor(i: Int): Color {
    val base = palette[i % palette.size]
    return if ((i / palette.size) % 2 == 1) base.copy(alpha = 0.6f) else base
}

internal fun niceStep(range: Double, targetTicks: Int = 4): Double {
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

/** Series 1 is a circle, then a square, a triangle and a diamond. */
internal fun DrawScope.marker(shape: Int, c: Offset, r: Float, color: Color, style: DrawStyle = Fill) {
    when (shape % 4) {
        0 -> drawCircle(color = color, radius = r, center = c, style = style)
        1 -> drawRect(color = color, topLeft = Offset(c.x - r * 0.9f, c.y - r * 0.9f), size = Size(r * 1.8f, r * 1.8f), style = style)
        2 -> drawPath(Path().apply {
            moveTo(c.x, c.y - r * 1.15f); lineTo(c.x + r * 1.1f, c.y + r * 0.85f); lineTo(c.x - r * 1.1f, c.y + r * 0.85f); close()
        }, color = color, style = style)
        else -> drawPath(Path().apply {
            moveTo(c.x, c.y - r * 1.2f); lineTo(c.x + r * 1.2f, c.y); lineTo(c.x, c.y + r * 1.2f); lineTo(c.x - r * 1.2f, c.y); close()
        }, color = color, style = style)
    }
}

@Composable
internal fun ChartEmpty(modifier: Modifier, height: Dp) {
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text("No data in this range", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A graph's height on its screen (#115): about 45% of the screen's height, at least 260dp and at most 480dp, so the data
 * has the room rather than the controls around it.
 */
@Composable
fun graphHeight(): Dp = (LocalConfiguration.current.screenHeightDp * 0.45f).dp.coerceIn(260.dp, 480.dp)

/**
 * The chart over time (#50, #137), with one or more [series], drawn as [kind]: line, bar, area or step.
 *
 * - Tap to select the nearest point; double tap calls [onExpand] (full screen) when it's given.
 * - A legend appears for more than one series or when [showTrend] is on. Tapping a series in it hides or shows it.
 * - [showTrend] adds a dashed least-squares trend per series. [yFromZero] starts the y axis at zero.
 * - The line joins every point, however far apart, so it's never broken (owner, #116).
 * - Days with progress photos get a tick on the time axis and a ring on their point.
 * - Style (1.0.71): value and time grid lines behind a red line, with a translucent gold fill under the first series.
 * - [viewport] shows part of the time range (full screen zoom).
 */
@Composable
fun FitChart(
    series: List<LineSeries>,
    modifier: Modifier = Modifier,
    kind: ChartKind = ChartKind.LINE,
    /** Unspecified sizes the graph from the screen ([graphHeight]). */
    height: Dp = Dp.Unspecified,
    photoDays: Set<Long> = emptySet(),
    goal: Double? = null,
    selected: ChartSelection? = null,
    onSelect: (ChartSelection) -> Unit = {},
    yFormat: (Double) -> String = { fmtNum(it, 1) },
    unit: String = "",
    showTrend: Boolean = false,
    yFromZero: Boolean = false,
    viewport: ChartViewport = ChartViewport(),
    onExpand: (() -> Unit)? = null
) {
    var hidden by remember(series.size) { mutableStateOf(emptySet<Int>()) }
    val visible = series.indices.filter { it !in hidden && series[it].points.isNotEmpty() }
    val all = visible.flatMap { series[it].points }
    val plotHeight = if (height == Dp.Unspecified) graphHeight() else height
    Column(modifier) {
        if (all.isEmpty()) {
            ChartEmpty(Modifier, plotHeight)
        } else {
            LinePlot(series, visible, kind, plotHeight, photoDays, goal, selected, onSelect, yFormat, unit, showTrend, yFromZero, viewport, onExpand)
        }
        if (series.size > 1 || showTrend) {
            ChartLegend(series.map { it.label }, hidden, showTrend) { i ->
                hidden = if (i in hidden) hidden - i else hidden + i
            }
        }
    }
}

@Composable
private fun LinePlot(
    series: List<LineSeries>,
    visible: List<Int>,
    kind: ChartKind,
    height: Dp,
    photoDays: Set<Long>,
    goal: Double?,
    selected: ChartSelection?,
    onSelect: (ChartSelection) -> Unit,
    yFormat: (Double) -> String,
    unit: String,
    showTrend: Boolean,
    yFromZero: Boolean,
    viewport: ChartViewport,
    onExpand: (() -> Unit)?
) {
    val colors = LocalChartColors.current
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    // The grid sits behind the series: visible enough to read values against, quiet enough not to compete.
    val gridColor = Brand.Ivory.copy(alpha = 0.10f)
    val axisColor = Brand.Gold.copy(alpha = 0.45f)
    val surface = MaterialTheme.colorScheme.background
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = textColor, letterSpacing = TextUnit.Unspecified)

    val all = visible.flatMap { series[it].points }
    val dataMin = all.minOf { it.x }
    val dataMax = all.maxOf { it.x }
    val fullMin = if (dataMax == dataMin) dataMin - 1 else dataMin
    val fullMax = if (dataMax == dataMin) dataMax + 1 else dataMax
    val fullSpan = (fullMax - fullMin).toDouble()
    val xMin = fullMin + viewport.from * fullSpan
    val xMax = fullMin + viewport.to * fullSpan
    val inView = all.filter { it.x >= xMin - 1 && it.x <= xMax + 1 }.ifEmpty { all }
    var yMin = inView.minOf { it.y }
    var yMax = inView.maxOf { it.y }
    if (goal != null && goal > 0) { yMin = minOf(yMin, goal); yMax = maxOf(yMax, goal) }
    // Bars always stand on zero, so their heights compare truly (#137).
    if (yFromZero || kind == ChartKind.BAR) yMin = minOf(yMin, 0.0)
    if (yMax - yMin < 1e-9) { yMin -= 1; yMax += 1 }
    val fitStep = niceStep(yMax - yMin)
    val fitLo = floor(yMin / fitStep) * fitStep
    val fitHi = ceil(yMax / fitStep) * fitStep
    // Zoomed values (#96) show exactly the chosen part of the fitted range, with gridlines at its own nice steps.
    val valuesZoomed = !viewport.valuesFull
    val lo = if (valuesZoomed) fitLo + viewport.yFrom * (fitHi - fitLo) else fitLo
    val hi = if (valuesZoomed) fitLo + viewport.yTo * (fitHi - fitLo) else fitHi
    val step = if (valuesZoomed) niceStep(hi - lo) else fitStep
    val xFmt = if (xMax - xMin < 150) DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    else DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())

    // Bars (#137) need room at each end for half a column, and a width that leaves gaps between them.
    val barSlots = if (kind != ChartKind.BAR) 0 else
        visible.maxOfOrNull { si -> series[si].points.count { it.x >= xMin && it.x <= xMax } }?.coerceAtLeast(1) ?: 1
    fun barWidth(plotWidth: Float, maxPx: Float): Float =
        if (kind != ChartKind.BAR) 0f else (plotWidth / barSlots * 0.7f).coerceIn(2f, maxPx)

    val trends = remember(series, visible, showTrend) {
        if (!showTrend) emptyMap() else visible.mapNotNull { i -> trendOf(series[i].points)?.let { i to it } }.toMap()
    }

    // What TalkBack reads: each series' range, low, high and latest value, then the selected point.
    val u = if (unit.isBlank()) "" else " $unit"
    val description = visible.joinToString(". ") { i ->
        val pts = series[i].points
        "${series[i].label}: ${Dates.medium(pts.first().date)} to ${Dates.medium(pts.last().date)}, " +
            "lowest ${yFormat(pts.minOf { it.y })}$u, highest ${yFormat(pts.maxOf { it.y })}$u, latest ${yFormat(pts.last().y)}$u"
    }
    val selectedPoint = selected?.let { s -> series.getOrNull(s.series)?.points?.getOrNull(s.index)?.let { s to it } }
    val selectedText = selectedPoint?.let { (s, p) -> "${series[s.series].label}, ${Dates.long(p.date)}: ${yFormat(p.y)}$u" }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = "Graph. $description"
                if (selectedText != null) stateDescription = selectedText
                liveRegion = LiveRegionMode.Polite
            }
            .pointerInput(series, visible, viewport, lo, hi, kind) {
                detectTapGestures(
                    onDoubleTap = if (onExpand != null) { _ -> onExpand() } else null,
                    onTap = { off ->
                        val inset = barWidth(size.width - 56.dp.toPx(), 28.dp.toPx()) / 2f
                        val left = 44.dp.toPx() + inset
                        val right = size.width - 12.dp.toPx() - inset
                        val top = 8.dp.toPx()
                        val bottom = size.height - 22.dp.toPx()
                        var best: ChartSelection? = null
                        var bestD = Float.MAX_VALUE
                        visible.forEach { si ->
                            series[si].points.forEachIndexed { i, p ->
                                if (p.x < xMin || p.x > xMax) return@forEachIndexed
                                val x = left + ((p.x - xMin) / (xMax - xMin)).toFloat() * (right - left)
                                val y = bottom - ((p.y - lo) / (hi - lo)).toFloat() * (bottom - top)
                                // Mostly by time: the vertical distance only settles between series on the same day.
                                val d = hypot(x - off.x, (y - off.y) * 0.35f)
                                if (d < bestD) { bestD = d; best = ChartSelection(si, i) }
                            }
                        }
                        best?.let(onSelect)
                    }
                )
            }
    ) {
        val left = 44.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val barW = barWidth(right - left, 28.dp.toPx())
        // Points sit inside the plot by half a bar, so the first and last columns aren't cut off (#137).
        val inset = barW / 2f
        fun px(x: Long) = left + inset + ((x - xMin) / (xMax - xMin)).toFloat() * (right - left - 2 * inset)
        fun py(y: Double) = bottom - ((y - lo) / (hi - lo)).toFloat() * (bottom - top)
        /** The series' outline: straight segments, or for [ChartKind.STEP] level until the next point, then up or down. */
        fun outline(pts: List<ChartPoint>): Path = Path().apply {
            pts.forEachIndexed { i, p ->
                val x = px(p.x)
                val y = py(p.y)
                if (i == 0) moveTo(x, y)
                else {
                    if (kind == ChartKind.STEP) lineTo(x, py(pts[i - 1].y))
                    lineTo(x, y)
                }
            }
        }

        // Grid and y labels. Zoomed, they start at the first whole step and stay inside the plot.
        var t = if (valuesZoomed) ceil(lo / step - 1e-9) * step else lo
        val gridTop = if (valuesZoomed) hi + step * 1e-6 else hi + step / 2
        var guard = 0
        while (t <= gridTop && guard++ < 20) {
            val y = py(t)
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(yFormat(t), labelStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
            t += step
        }
        // Time grid: four even columns, and a fine gold baseline along the time axis.
        for (k in 1..3) {
            val x = left + (right - left) * k / 4f
            drawLine(gridColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
        }
        drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
        // x labels: first and last, plus the middle when it fits without touching them.
        val first = measurer.measure(LocalDate.ofEpochDay(xMin.toLong()).format(xFmt), labelStyle)
        val last = measurer.measure(LocalDate.ofEpochDay(xMax.toLong()).format(xFmt), labelStyle)
        val mid = measurer.measure(LocalDate.ofEpochDay(((xMin + xMax) / 2).toLong()).format(xFmt), labelStyle)
        val labelY = bottom + 5.dp.toPx()
        drawText(first, topLeft = Offset(left, labelY))
        drawText(last, topLeft = Offset(right - last.size.width, labelY))
        val midX = (left + right) / 2f - mid.size.width / 2f
        val gap = 8.dp.toPx()
        if (midX > left + first.size.width + gap && midX + mid.size.width < right - last.size.width - gap) {
            drawText(mid, topLeft = Offset(midX, labelY))
        }

        clipRect(left - 8.dp.toPx(), top - 8.dp.toPx(), right + 8.dp.toPx(), bottom + 1f) {
            if (goal != null && goal > 0) {
                val gy = py(goal)
                drawLine(
                    colors.goal, Offset(left, gy), Offset(right, gy), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            }
            photoDays.forEach { d ->
                if (d >= xMin && d <= xMax) {
                    val x = px(d)
                    drawLine(colors.accent, Offset(x, bottom - 7.dp.toPx()), Offset(x, bottom), strokeWidth = 2.dp.toPx())
                }
            }
            // A soft gold fill under the first visible series only, so several lines never muddy each other. Bars carry
            // their own fill.
            if (kind != ChartKind.BAR) visible.firstOrNull()?.let { si ->
                val pts = series[si].points
                val area = outline(pts)
                area.lineTo(px(pts.last().x), bottom)
                area.lineTo(px(pts.first().x), bottom)
                area.close()
                drawPath(area, Brush.verticalGradient(listOf(colors.fill, colors.fill.copy(alpha = 0.08f)), startY = top, endY = bottom))
            }
            visible.forEachIndexed { k, si ->
                val pts = series[si].points
                val color = colors.seriesColor(si)
                if (kind == ChartKind.BAR) {
                    // Gold columns with a red top (#137); several series stand side by side within each slot.
                    val sub = barW / visible.size
                    val body = if (k == 0) colors.fill.copy(alpha = 0.6f) else color.copy(alpha = 0.35f)
                    pts.forEach { p ->
                        if (p.x < xMin || p.x > xMax) return@forEach
                        val cx = px(p.x) + (k - (visible.size - 1) / 2f) * sub
                        val yTop = py(p.y).coerceIn(top, bottom)
                        drawRect(body, topLeft = Offset(cx - sub / 2f, yTop), size = Size(sub * 0.92f, bottom - yTop))
                        drawLine(color, Offset(cx - sub / 2f, yTop), Offset(cx - sub / 2f + sub * 0.92f, yTop), strokeWidth = 2.5.dp.toPx())
                    }
                } else {
                    val width = if (kind == ChartKind.AREA) 1.5.dp.toPx() else 2.5.dp.toPx()
                    drawPath(outline(pts), color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                trends[si]?.let { tr ->
                    val a = pts.first().x
                    val b = pts.last().x
                    drawLine(
                        color.copy(alpha = 0.8f), Offset(px(a), py(tr.at(a))), Offset(px(b), py(tr.at(b))),
                        strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                }
                val shownCount = pts.count { it.x >= xMin && it.x <= xMax }
                // Markers belong to the line and step kinds; area and bar show only photo days (#137).
                val showMarkers = (kind == ChartKind.LINE || kind == ChartKind.STEP) && (shownCount <= 60 || si > 0)
                pts.forEach { p ->
                    if (p.x < xMin || p.x > xMax) return@forEach
                    val c = Offset(px(p.x), py(p.y))
                    val hasPhoto = p.x in photoDays
                    if (showMarkers || hasPhoto) {
                        marker(si, c, 5.dp.toPx(), surface)
                        marker(si, c, 4.dp.toPx(), color)
                    }
                    if (hasPhoto) drawCircle(colors.accent, radius = 6.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
                }
            }
            selectedPoint?.let { (s, p) ->
                val c = Offset(px(p.x), py(p.y))
                drawLine(textColor.copy(alpha = 0.6f), Offset(c.x, top), Offset(c.x, bottom), strokeWidth = 1.dp.toPx())
                marker(s.series, c, 7.dp.toPx(), surface)
                marker(s.series, c, 6.dp.toPx(), colors.seriesColor(s.series))
            }
        }
    }
}

/** The legend under a chart: each series with its colour and marker; tapping one hides or shows it. */
@Composable
internal fun ChartLegend(labels: List<String>, hidden: Set<Int>, showTrend: Boolean, onToggle: (Int) -> Unit) {
    val colors = LocalChartColors.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { i, label ->
            val on = i !in hidden
            Row(
                Modifier
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(i) })
                    .alpha(if (on) 1f else 0.4f)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(14.dp)) { marker(i, center, size.minDimension / 2.6f, colors.seriesColor(i)) }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (showTrend) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(width = 18.dp, height = 14.dp)) {
                    drawLine(
                        colors.seriesColor(0), Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text("Trend", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
