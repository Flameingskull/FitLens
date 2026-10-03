package com.fitlens.companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Analysis
import com.fitlens.companion.data.Records
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.ui.design.DragHandle
import com.fitlens.companion.ui.design.FitIcons
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.OverflowMenu

/**
 * A graph pinned to Analysis → Overview (#55): an exercise's graph by name, its range (an index into [RANGES]), the
 * exercises it's compared with (#53) and whether the comparison is relative. Its chart kind is the graph's own (#137).
 *
 * A [totals] pin is an Analysis → Workouts graph (#51) instead: [graph] is "Metric/Period" (`Analysis.Metric` and
 * `Analysis.Period` names), [exerciseId] or [categoryId] its filter (0 for none) and [average] the average-duration
 * option. Pins live in `PortableSettings.pinnedGraphs`, so they travel in `.fitlens` backups with no schema change.
 */
data class PinnedGraph(
    val exerciseId: Long,
    val graph: String,
    val range: Int = 4,
    val compare: List<Long> = emptyList(),
    val relative: Boolean = false,
    val totals: Boolean = false,
    val categoryId: Long = 0,
    val average: Boolean = false
) {
    /** One pin per graph (and, for totals, per filter): pinning it again replaces its settings. */
    fun sameGraph(o: PinnedGraph): Boolean =
        totals == o.totals && exerciseId == o.exerciseId && graph == o.graph && categoryId == o.categoryId

    /** A Workouts pin's filter: one exercise, one category or all training. */
    val totalsFilter: Analysis.Filter
        get() = Analysis.Filter(categoryId = categoryId.takeIf { it > 0 }, exerciseId = exerciseId.takeIf { it > 0 })

    companion object {
        /**
         * One pin per line, "exercise|graph|range|compared ids|relative|totals|category|average" (the last three
         * optional). A line that doesn't parse (or a later build's format) is skipped, so a bad value can never break
         * the Overview.
         */
        fun decode(s: String?): List<PinnedGraph> =
            s.orEmpty().lines().mapNotNull { line ->
                val p = line.split('|')
                if (p.size < 3) return@mapNotNull null
                val id = p[0].toLongOrNull() ?: return@mapNotNull null
                val graph = p[1].takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PinnedGraph(
                    id, graph,
                    p[2].toIntOrNull()?.takeIf { it in RANGES.indices } ?: 4,
                    p.getOrNull(3).orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { it != id }.distinct().take(GraphCompare.MAX - 1),
                    p.getOrNull(4) == "1",
                    totals = p.getOrNull(5) == "1",
                    categoryId = p.getOrNull(6)?.toLongOrNull() ?: 0,
                    average = p.getOrNull(7) == "1"
                )
            }.distinctBy { listOf(it.totals, it.exerciseId, it.graph, it.categoryId) }

        fun encode(pins: List<PinnedGraph>): String? =
            pins.joinToString("\n") { p ->
                listOf(
                    p.exerciseId.toString(), p.graph.replace("|", " ").replace("\n", " "), p.range.toString(),
                    p.compare.joinToString(","), if (p.relative) "1" else "0",
                    if (p.totals) "1" else "0", p.categoryId.toString(), if (p.average) "1" else "0"
                ).joinToString("|")
            }.ifEmpty { null }
    }
}

/**
 * The exercises each exercise's graph is compared with (#53), remembered per exercise and in backups:
 * "id=id,id;id=id". [MAX] exercises at most on one graph, the exercise itself included.
 */
object GraphCompare {
    const val MAX = 5

    fun decode(s: String?): Map<Long, List<Long>> =
        s.orEmpty().split(';').mapNotNull { part ->
            val at = part.indexOf('=')
            if (at <= 0) return@mapNotNull null
            val id = part.substring(0, at).toLongOrNull() ?: return@mapNotNull null
            val others = part.substring(at + 1).split(',').mapNotNull { it.toLongOrNull() }.filter { it != id }.distinct().take(MAX - 1)
            if (others.isEmpty()) null else id to others
        }.toMap()

    fun encode(m: Map<Long, List<Long>>): String? =
        m.entries.filter { it.value.isNotEmpty() }.joinToString(";") { "${it.key}=${it.value.joinToString(",")}" }.ifEmpty { null }
}

/** The pinned graphs, in order, as the Overview shows them. */
@Composable
fun rememberPins(): List<PinnedGraph> {
    val prefs by Settings.portable.collectAsState()
    return PinnedGraph.decode(prefs.pinnedGraphs)
}

/** Changes the pinned graphs, keeping them in backups (#55). */
fun updatePins(change: (List<PinnedGraph>) -> List<PinnedGraph>) {
    Settings.updatePortable { p -> p.copy(pinnedGraphs = PinnedGraph.encode(change(PinnedGraph.decode(p.pinnedGraphs)))) }
}

/** The exercises exercise [exId]'s graph is compared with (#53), and a function that remembers a new list. */
@Composable
fun rememberCompare(exId: Long): Pair<List<Long>, (List<Long>) -> Unit> {
    val prefs by Settings.portable.collectAsState()
    val list = GraphCompare.decode(prefs.graphCompare)[exId].orEmpty()
    return list to { ids -> saveCompare(exId, ids) }
}

/** Remembers [ids] as the exercises exercise [exId]'s graph is compared with (#53); an empty list clears it. */
fun saveCompare(exId: Long, ids: List<Long>) {
    Settings.updatePortable { p ->
        p.copy(graphCompare = GraphCompare.encode(GraphCompare.decode(p.graphCompare) + (exId to ids.filter { it != exId }.distinct().take(GraphCompare.MAX - 1))))
    }
}

/** The star on a graph's option row (#55): gold and filled when the graph is pinned to the Overview. */
@Composable
fun PinGraphButton(pinned: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (pinned) Icons.Filled.Star else FitIcons.StarOutline,
            contentDescription = if (pinned) "Unpin from the Analysis overview" else "Pin to the Analysis overview",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Analysis → Overview (#55): every pinned graph as a compact card (its chart, the latest value and the change over its
 * range). Tapping a card opens the graph with its settings in Analysis → Exercises; the card's ⋮ moves or unpins it,
 * and its handle drags it. Pins whose exercise or graph no longer exists are left out.
 */
@Composable
fun AnalysisOverviewTab(snap: Snapshot, onOpen: (PinnedGraph) -> Unit) {
    val all = rememberPins()
    val pins = all.filter { p ->
        if (p.totals) {
            totalsOf(p) != null && (p.exerciseId == 0L || snap.exercises.containsKey(p.exerciseId)) &&
                (p.categoryId == 0L || snap.categories.containsKey(p.categoryId))
        } else snap.exercises.containsKey(p.exerciseId) && p.graph in graphLabelsFor(snap, p.exerciseId)
    }
    if (pins.isEmpty()) {
        EmptyState(
            "No pinned graphs yet",
            "Tap the star on a Workouts graph, or on any exercise graph here in Exercises or on the exercise's Graph tab. It appears here with its latest value and change."
        )
        return
    }
    fun move(p: PinnedGraph, by: Int) = updatePins { list ->
        val from = list.indexOfFirst { it.sameGraph(p) }
        val to = from + by
        if (from < 0 || to !in list.indices) list else list.toMutableList().also { it.add(to, it.removeAt(from)) }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(pins, key = { _, p -> "${p.totals}:${p.exerciseId}:${p.categoryId}:${p.graph}" }) { i, p ->
            PinnedGraphCard(
                snap, p,
                onOpen = { onOpen(p) },
                onUnpin = { updatePins { list -> list.filterNot { it.sameGraph(p) } } },
                onMoveUp = if (i > 0) ({ move(p, -1) }) else null,
                onMoveDown = if (i < pins.lastIndex) ({ move(p, 1) }) else null
            )
        }
    }
}

/** The graph names exercise [exId] offers (#22), as its graph pane lists them. */
internal fun graphLabelsFor(snap: Snapshot, exId: Long): List<String> =
    graphLabels(snap.exercises[exId]?.type ?: 0, isTimeBased(snap, exId, snap.setsByExercise[exId].orEmpty()))

private val CARD_HEIGHT = 140.dp

@Composable
private fun PinnedGraphCard(
    snap: Snapshot,
    pin: PinnedGraph,
    onOpen: () -> Unit,
    onUnpin: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    // A Workouts pin (#51) or an exercise graph, with its comparison (#53).
    val totals = totalsOf(pin)
    val avgDuration = totals?.first == Analysis.Metric.Duration && pin.average
    val title = if (totals != null) "${totalsGraphName(totals.first, totals.second)} · ${filterLabel(snap, pin.totalsFilter)}"
    else "${snap.exercises[pin.exerciseId]?.name ?: "Exercise"} · ${pin.graph}"
    val formula = Records.chosen()
    val series = rememberDerived(
        "pinnedGraph", snap.trainingKey, pin, formula, Records.maxRepsFor(formula)
    ) {
        if (totals != null) {
            val pts = totalsPoints(snap, totals.first, totals.second, pin.totalsFilter, rangeFrom(pin.range), avgDuration)
            listOf(LineSeries(totals.first.label, pts))
        } else comparedSeries(snap, pin.exerciseId, pin.graph, pin.compare, pin.range, pin.relative)
    }
    val (kind, _) = rememberChartKind(if (totals != null) "analysis:${totals.first.name}" else "exercise:${pin.graph}")
    val unit = when {
        totals != null -> totalsUnit(snap, totals.first, avgDuration)
        pin.relative -> "%"
        else -> graphUnit(snap, pin.exerciseId, pin.graph)
    }
    fun show(v: Double): String =
        if (totals != null) totalsText(totals.first, avgDuration, unit, v) else graphValueText(pin.graph, v, unit)
    val main = series?.firstOrNull()?.points.orEmpty()
    val summary = when {
        series == null -> "Working it out…"
        main.isEmpty() -> "No data in this range"
        else -> {
            // The latest value, then the change in its own unit from the first in the range (never a bare number).
            val first = main.first().y
            val last = main.last().y
            val change = last - first
            val sign = if (change >= 0) "+" else "−"
            "Latest ${show(last)} · $sign${show(kotlin.math.abs(change))} from ${show(first)} (${rangeName(RANGES[pin.range].first)})"
        }
    }
    val moveActions = listOfNotNull(
        onMoveUp?.let { f -> CustomAccessibilityAction("Move up") { f(); true } },
        onMoveDown?.let { f -> CustomAccessibilityAction("Move down") { f(); true } }
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open $title", onClick = onOpen)
            .semantics { if (moveActions.isNotEmpty()) customActions = moveActions }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            OverflowMenu(
                listOfNotNull(
                    onMoveUp?.let { MenuAction("Move up", onClick = it) },
                    onMoveDown?.let { MenuAction("Move down", onClick = it) },
                    MenuAction("Unpin", onClick = onUnpin)
                ),
                description = "Options for $title"
            )
            if (onMoveUp != null || onMoveDown != null) DragHandle(title, CARD_HEIGHT + 64.dp, onMoveUp, onMoveDown)
        }
        if (series != null) {
            FitChart(
                series,
                Modifier.padding(end = 12.dp),
                kind = kind,
                height = CARD_HEIGHT,
                // Tapping the chart opens the graph too.
                onSelect = { onOpen() },
                unit = unit
            )
        }
        GoldHairline(Modifier.padding(top = 8.dp, end = 12.dp))
    }
}
