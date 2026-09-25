package com.fitlens.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitlens.companion.data.fmtNum
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// More shared chart components (#50): bars for period totals, a donut for breakdowns, and the full-screen viewer
// every chart can open.

/** One bar: a period such as a week or month, its short axis label, and its total. */
data class BarDatum(val label: String, val value: Double)

/**
 * Bars for period totals. The y axis always starts at zero. An empty period is a zero-height bar (a short mark on the
 * axis), never a line dropping to zero. Tap a bar to select it; the screen shows its total and can open the period.
 * Double tap calls [onExpand] when it's given.
 */
@Composable
fun BarChart(
    bars: List<BarDatum>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    selected: Int? = null,
    onSelect: (Int) -> Unit = {},
    yFormat: (Double) -> String = { fmtNum(it, 0) },
    unit: String = "",
    viewport: ChartViewport = ChartViewport(),
    onExpand: (() -> Unit)? = null
) {
    if (bars.isEmpty()) {
        ChartEmpty(modifier, height)
        return
    }
    val colors = LocalChartColors.current
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = Brand.Hairline.copy(alpha = 0.35f)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = textColor)

    val n = bars.size
    val first = floor(viewport.from * n).toInt().coerceIn(0, n - 1)
    val last = (ceil(viewport.to * n).toInt() - 1).coerceIn(first, n - 1)
    val shownBars = first..last
    val maxV = shownBars.maxOf { bars[it].value }.coerceAtLeast(0.0)
    val step = niceStep(if (maxV > 0) maxV else 1.0)
    val hi = (ceil(maxV / step) * step).coerceAtLeast(step)

    val u = if (unit.isBlank()) "" else " $unit"
    val description = "Bar chart, ${bars.first().label} to ${bars.last().label}. " +
        "Highest ${yFormat(bars.maxOf { it.value })}$u, latest ${yFormat(bars.last().value)}$u."
    val selectedText = selected?.let { bars.getOrNull(it) }?.let { "${it.label}: ${yFormat(it.value)}$u" }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = description
                if (selectedText != null) stateDescription = selectedText
                liveRegion = LiveRegionMode.Polite
            }
            .pointerInput(bars, viewport) {
                detectTapGestures(
                    onDoubleTap = if (onExpand != null) { _ -> onExpand() } else null,
                    onTap = { off ->
                        val left = 44.dp.toPx()
                        val right = size.width - 12.dp.toPx()
                        val slot = (right - left) / shownBars.count()
                        val i = first + ((off.x - left) / slot).toInt()
                        if (off.x >= left && i in shownBars) onSelect(i)
                    }
                )
            }
    ) {
        val left = 44.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        fun py(y: Double) = bottom - (y / hi).toFloat() * (bottom - top)

        var t = 0.0
        var guard = 0
        while (t <= hi + step / 2 && guard++ < 20) {
            val y = py(t)
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
            val layout = measurer.measure(yFormat(t), labelStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
            t += step
        }
        val slot = (right - left) / shownBars.count()
        val barW = slot * 0.7f
        // Label every k-th bar so labels never overlap.
        val widest = shownBars.maxOf { measurer.measure(bars[it].label, labelStyle).size.width }.toFloat()
        val every = ceil((widest + 8.dp.toPx()) / slot).toInt().coerceAtLeast(1)
        shownBars.forEachIndexed { k, i ->
            val b = bars[i]
            val x = left + k * slot + (slot - barW) / 2f
            val isSel = i == selected
            if (b.value > 0) {
                val y = py(b.value)
                drawRect(colors.seriesColor(0), topLeft = Offset(x, y), size = Size(barW, bottom - y))
                if (isSel) drawRect(Brand.GoldLight, topLeft = Offset(x, y), size = Size(barW, bottom - y), style = Stroke(width = 2.dp.toPx()))
            } else {
                // An empty period: a zero-height bar, shown as a short mark on the axis.
                drawLine(textColor.copy(alpha = 0.5f), Offset(x, bottom), Offset(x + barW, bottom), strokeWidth = 2.dp.toPx())
            }
            if (isSel && b.value <= 0) {
                drawRect(Brand.GoldLight, topLeft = Offset(x, bottom - 4.dp.toPx()), size = Size(barW, 4.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
            }
            if (k % every == 0) {
                val layout = measurer.measure(b.label, labelStyle)
                val lx = (x + barW / 2f - layout.size.width / 2f).coerceIn(left, right - layout.size.width)
                drawText(layout, topLeft = Offset(lx, bottom + 5.dp.toPx()))
            }
        }
    }
}

/** One segment of a [DonutChart]. */
data class DonutSlice(val label: String, val value: Double)

/**
 * A donut for breakdowns (by category or exercise). The [selected] segment is raised with a gold outline and named
 * in the centre with its share. Previous and next step through the segments, and the legend below stays in step: tap
 * a row or a segment to select it.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 220.dp,
    valueFormat: (Double) -> String = { fmtNum(it, 0) },
    onExpand: (() -> Unit)? = null
) {
    val total = slices.sumOf { it.value.coerceAtLeast(0.0) }
    if (slices.isEmpty() || total <= 0) {
        ChartEmpty(modifier, diameter)
        return
    }
    val colors = LocalChartColors.current
    val sel = selected.coerceIn(0, slices.lastIndex)
    fun share(i: Int) = slices[i].value.coerceAtLeast(0.0) / total
    fun pct(i: Int) = "${(share(i) * 100).roundToInt()}%"

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSelect((sel - 1 + slices.size) % slices.size) }) { Text("Previous") }
            Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier
                        .size(diameter)
                        .semantics {
                            contentDescription = "Breakdown. " + slices.indices.joinToString(", ") { "${slices[it].label} ${pct(it)}" }
                            stateDescription = "${slices[sel].label}, ${pct(sel)}"
                            liveRegion = LiveRegionMode.Polite
                        }
                        .pointerInput(slices) {
                            detectTapGestures(
                                onDoubleTap = if (onExpand != null) { _ -> onExpand() } else null,
                                onTap = { off ->
                                    val c = Offset(size.width / 2f, size.height / 2f)
                                    val r = hypot(off.x - c.x, off.y - c.y)
                                    val outer = size.width / 2f
                                    if (r < outer * 0.45f || r > outer) return@detectTapGestures
                                    // Angle clockwise from 12 o'clock, in degrees.
                                    val deg = (Math.toDegrees(atan2((off.y - c.y).toDouble(), (off.x - c.x).toDouble())) + 90.0 + 360.0) % 360.0
                                    var acc = 0.0
                                    for (i in slices.indices) {
                                        acc += share(i) * 360.0
                                        if (deg < acc) { onSelect(i); break }
                                    }
                                }
                            )
                        }
                ) {
                    val thick = size.minDimension * 0.18f
                    val raise = 6.dp.toPx()
                    val radius = size.minDimension / 2f - thick / 2f - raise
                    var start = -90f
                    slices.indices.forEach { i ->
                        val sweep = (share(i) * 360.0).toFloat()
                        if (sweep > 0f) {
                            val mid = Math.toRadians((start + sweep / 2f).toDouble())
                            val shift = if (i == sel) Offset((cos(mid) * raise).toFloat(), (sin(mid) * raise).toFloat()) else Offset.Zero
                            val topLeft = Offset(center.x - radius, center.y - radius) + shift
                            val arcSize = Size(radius * 2f, radius * 2f)
                            if (i == sel) {
                                drawArc(Brand.Gold, start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width = thick + 4.dp.toPx()))
                            }
                            drawArc(colors.seriesColor(i), start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width = thick))
                        }
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(diameter * 0.55f)) {
                    Text(
                        slices[sel].label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    Text(pct(sel), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(onClick = { onSelect((sel + 1) % slices.size) }) { Text("Next") }
        }
        // The legend, kept in step with the selection.
        slices.forEachIndexed { i, s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(12.dp)) { drawCircle(colors.seriesColor(i)) }
                Spacer(Modifier.width(10.dp))
                Text(
                    s.label, Modifier.weight(1f),
                    fontWeight = if (i == sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text("${valueFormat(s.value)} · ${pct(i)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The full-screen viewer every chart can open (#50): the whole screen, landscape allowed, pinch to zoom the time
 * axis, drag to pan, a reset control, and the chart's own tap-for-details. [chart] draws the chart for the current
 * zoom at the height available.
 */
@Composable
fun FullScreenChart(
    title: String,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit = {},
    chart: @Composable (viewport: ChartViewport, height: Dp) -> Unit
) {
    var viewport by remember { mutableStateOf(ChartViewport()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                BackTopBar(title, onBack = onDismiss) {
                    IconButton(onClick = { viewport = ChartViewport() }, enabled = !viewport.isFull) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset zoom")
                    }
                }
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp)
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                viewport = viewport.transform(centroid.x / w, pan.x / w, zoom)
                            }
                        }
                ) {
                    // Leave room for a legend under line charts.
                    chart(viewport, (maxHeight - 56.dp).coerceAtLeast(160.dp))
                }
                footer()
                Text(
                    "Pinch to zoom, drag to move along the timeline, tap for details.",
                    Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
