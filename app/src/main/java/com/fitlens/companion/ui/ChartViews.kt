package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.ToggleOption
import com.fitlens.companion.ui.design.OptionsMenu
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.DropdownPill
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
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
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitlens.companion.data.Analysis
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.fmtNum
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// More shared chart components (#50): a donut for breakdowns, the compact graph controls and the full-screen viewer
// every chart can open.

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
    onExpand: (() -> Unit)? = null,
    legend: Boolean = true
) {
    val total = slices.sumOf { it.value.coerceAtLeast(0.0) }
    if (slices.isEmpty() || total <= 0) {
        ChartEmpty(modifier, diameter)
        return
    }
    val colors = LocalChartColors.current
    val sel = selected.coerceIn(0, slices.lastIndex)
    fun share(i: Int) = slices[i].value.coerceAtLeast(0.0) / total
    // Largest-remainder rounding, so the legend always adds up to 100% (#52).
    val percents = remember(slices) { Analysis.percents(slices.map { it.value }) }
    fun pct(i: Int) = "${percents[i]}%"

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
                    // The figure itself, not just its share (owner, 2026-10-02).
                    Text(valueFormat(slices[sel].value), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
            TextButton(onClick = { onSelect((sel + 1) % slices.size) }) { Text("Next") }
        }
        if (legend) DonutLegend(slices, sel, onSelect, valueFormat)
    }
}

/** A donut's legend (#52), kept in step with the selection: colour, label, value and share. Tap a row to select it. */
@Composable
fun DonutLegend(
    slices: List<DonutSlice>,
    selected: Int,
    onSelect: (Int) -> Unit,
    valueFormat: (Double) -> String = { fmtNum(it, 0) },
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) return
    val colors = LocalChartColors.current
    val sel = selected.coerceIn(0, slices.lastIndex)
    val percents = remember(slices) { Analysis.percents(slices.map { it.value }) }
    fun pct(i: Int) = "${percents[i]}%"
    Column(modifier) {
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

/** The Material "fullscreen" glyph (four corners), drawn here because the core icon set doesn't include it. */
val FullscreenIcon: ImageVector = materialIcon(name = "FitLens.Fullscreen") {
    materialPath {
        moveTo(7f, 14f); horizontalLineTo(5f); verticalLineTo(19f); horizontalLineTo(10f); verticalLineTo(17f); horizontalLineTo(7f); close()
        moveTo(5f, 10f); horizontalLineTo(7f); verticalLineTo(7f); horizontalLineTo(10f); verticalLineTo(5f); horizontalLineTo(5f); close()
        moveTo(17f, 17f); horizontalLineTo(14f); verticalLineTo(19f); horizontalLineTo(19f); verticalLineTo(14f); horizontalLineTo(17f); close()
        moveTo(14f, 5f); verticalLineTo(7f); horizontalLineTo(17f); verticalLineTo(10f); horizontalLineTo(19f); verticalLineTo(5f); close()
    }
}

/** The visible full-screen button for a graph (#96), placed next to its chips. */
@Composable
fun ExpandGraphButton(onClick: () -> Unit) {
    IconButton(onClick = { ChartHints.expanded(); onClick() }) {
        Icon(FullscreenIcon, contentDescription = "Show graph full screen", tint = MaterialTheme.colorScheme.primary)
    }
}

/** Remembers, per phone, whether the user has found tap-for-details and full screen, so the hint can go away. */
object ChartHints {
    fun tapped() {
        if (!Settings.current().chartTapSeen) Settings.updateDevice { it.copy(chartTapSeen = true) }
    }

    fun expanded() {
        if (!Settings.current().chartExpandSeen) Settings.updateDevice { it.copy(chartExpandSeen = true) }
    }
}

/** "Tap a point for details. Double tap to expand." under a graph, until the user has done both once. */
@Composable
fun ChartHint(modifier: Modifier = Modifier) {
    val device by Settings.device.collectAsState()
    if (!(device.chartTapSeen && device.chartExpandSeen)) {
        Text(
            "Tap a point for details. Double tap to expand.",
            modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The full-screen viewer every chart can open (#50, #96): the whole screen, landscape allowed, pinch to zoom, drag to
 * pan, a reset control (or double tap), and the chart's own tap-for-details. A pinch is split by direction: across
 * zooms the time axis and, when [valueZoom] is on (line charts), up and down zooms the values. [chart] draws the chart
 * for the current zoom at the height available, and gets a reset function to use as its double tap. The zoom survives
 * rotation. TalkBack users get zoom and move actions instead of gestures. [controls] sits under the top bar, usually
 * [GraphOptionChips], so the range and options can change without leaving full screen.
 */
@Composable
fun FullScreenChart(
    title: String,
    onDismiss: () -> Unit,
    controls: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    /** Lets a vertical pinch or drag zoom and move the values. Off for bar charts, which always start at zero. */
    valueZoom: Boolean = true,
    chart: @Composable (viewport: ChartViewport, height: Dp, resetZoom: () -> Unit) -> Unit
) {
    var from by rememberSaveable { mutableFloatStateOf(0f) }
    var to by rememberSaveable { mutableFloatStateOf(1f) }
    var yFrom by rememberSaveable { mutableFloatStateOf(0f) }
    var yTo by rememberSaveable { mutableFloatStateOf(1f) }
    val viewport = ChartViewport(from, to, yFrom, yTo)
    val set: (ChartViewport) -> Unit = { v -> from = v.from; to = v.to; yFrom = v.yFrom; yTo = v.yTo }
    val reset: () -> Unit = { set(ChartViewport()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                BackTopBar(title, onBack = onDismiss, backLabel = "Close full screen") {
                    IconButton(onClick = reset, enabled = !viewport.isFull) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset zoom")
                    }
                }
                controls()
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp)
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("Zoom in") { set(viewport.transform(0.5f, 0f, 2f)); true },
                                CustomAccessibilityAction("Zoom out") { set(viewport.transform(0.5f, 0f, 0.5f)); true },
                                CustomAccessibilityAction("Move earlier") { set(viewport.transform(0.5f, 0.5f, 1f)); true },
                                CustomAccessibilityAction("Move later") { set(viewport.transform(0.5f, -0.5f, 1f)); true }
                            ) + if (!valueZoom) emptyList<CustomAccessibilityAction>() else listOf(
                                CustomAccessibilityAction("Zoom in on values") { set(viewport.transformY(0.5f, 0f, 2f)); true },
                                CustomAccessibilityAction("Zoom out on values") { set(viewport.transformY(0.5f, 0f, 0.5f)); true },
                                CustomAccessibilityAction("Show higher values") { set(viewport.transformY(0.5f, 0.5f, 1f)); true },
                                CustomAccessibilityAction("Show lower values") { set(viewport.transformY(0.5f, -0.5f, 1f)); true }
                            )
                        }
                        .pointerInput(valueZoom) {
                            detectAxisTransformGestures { centroid, pan, zoomX, zoomY ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                var v = ChartViewport(from, to, yFrom, yTo).transform(centroid.x / w, pan.x / w, zoomX)
                                if (valueZoom) v = v.transformY(1f - centroid.y / h, pan.y / h, zoomY)
                                set(v)
                            }
                        }
                ) {
                    // Leave room for a legend under line charts.
                    chart(viewport, (maxHeight - 56.dp).coerceAtLeast(160.dp), reset)
                }
                footer()
                Text(
                    if (valueZoom) "Pinch across to zoom the timeline, up and down to zoom the values. Drag to move, tap for details."
                    else "Pinch to zoom, drag to move along the timeline, tap for details.",
                    Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * The full-screen view of a donut (#96). A donut has no timeline to zoom, so this gives it room instead: in portrait
 * it grows to the screen's width with its legend below, and in landscape its legend sits beside it.
 */
@Composable
fun FullScreenDonut(
    title: String,
    slices: List<DonutSlice>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    valueFormat: (Double) -> String = { fmtNum(it, 0) }
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                BackTopBar(title, onBack = onDismiss, backLabel = "Close full screen")
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                    // The Previous and Next buttons either side of the donut take about 150dp of the width.
                    if (maxWidth > maxHeight) {
                        val d = minOf(maxHeight - 16.dp, maxWidth / 2 - 150.dp).coerceAtLeast(140.dp)
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            DonutChart(slices, selected, onSelect, Modifier.weight(1f), diameter = d, valueFormat = valueFormat, legend = false)
                            DonutLegend(
                                slices, selected, onSelect, valueFormat,
                                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                            )
                        }
                    } else {
                        val d = (maxWidth - 150.dp).coerceIn(140.dp, 400.dp)
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            DonutChart(slices, selected, onSelect, diameter = d, valueFormat = valueFormat)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A graph's controls in one compact row (#115): any [leading] dropdowns (the graph type, say), the range as a dropdown,
 * the on/off options (Trend, From zero and [extra]) in a ⋮ menu, and [trailing] (usually [ExpandGraphButton]). It
 * takes one line instead of three rows of chips, so the graph gets the room. Also used inside [FullScreenChart] (#96).
 */
@Composable
fun GraphOptionChips(
    rangeIdx: Int,
    onRange: (Int) -> Unit,
    showTrend: Boolean,
    onTrend: () -> Unit,
    fromZero: Boolean = false,
    onFromZero: (() -> Unit)? = null,
    extra: List<ToggleOption> = emptyList(),
    onShare: (() -> Unit)? = null,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        DropdownPill("Range", RANGES.map { rangeName(it.first) }, rangeIdx, onSelect = onRange)
        Spacer(Modifier.weight(1f))
        OptionsMenu(
            listOfNotNull(
                ToggleOption("Trend line", showTrend, onTrend),
                onFromZero?.let { ToggleOption("Start from zero", fromZero, it) }
            ) + extra,
            // Share the graph as a branded image (#22).
            actions = listOfNotNull(onShare?.let { MenuAction("Share graph as image", onClick = it) })
        )
        trailing()
    }
}

/** A range preset's name in a menu: "1M" reads as "1 month". */
internal fun rangeName(short: String): String = when (short) {
    "1M" -> "1 month"; "3M" -> "3 months"; "6M" -> "6 months"; "1Y" -> "1 year"; "All" -> "All time"; else -> short
}

/**
 * Pinch and drag for [FullScreenChart] (#96). It works like detectTransformGestures, with the same touch slop so a tap
 * still reaches the chart, but measures the pinch along each axis: [onGesture] gets the horizontal and vertical zoom
 * apart, so pinching across zooms time and pinching up and down zooms values. An axis the fingers barely span (two
 * fingers side by side have almost no height between them) keeps a zoom of 1, so it doesn't jump.
 */
private suspend fun PointerInputScope.detectAxisTransformGestures(
    onGesture: (centroid: Offset, pan: Offset, zoomX: Float, zoomY: Float) -> Unit
) {
    val minSpread = 24.dp.toPx()
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val zoomMotion = abs(1 - zoom) * event.calculateCentroidSize(useCurrent = false)
                    if (zoomMotion > touchSlop || pan.getDistance() > touchSlop) pastTouchSlop = true
                }
                if (pastTouchSlop) {
                    val down = event.changes.filter { it.pressed && it.previousPressed }
                    fun spread(values: List<Float>): Float {
                        val mid = values.average().toFloat()
                        return values.map { abs(it - mid) }.average().toFloat()
                    }
                    fun axisZoom(axis: (Offset) -> Float): Float {
                        if (down.size < 2) return 1f
                        val before = spread(down.map { axis(it.previousPosition) })
                        val now = spread(down.map { axis(it.position) })
                        return if (before < minSpread || now < minSpread) 1f else now / before
                    }
                    val zoomX = axisZoom { it.x }
                    val zoomY = axisZoom { it.y }
                    if (zoomX != 1f || zoomY != 1f || panChange != Offset.Zero) {
                        onGesture(event.calculateCentroid(useCurrent = false), panChange, zoomX, zoomY)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}
