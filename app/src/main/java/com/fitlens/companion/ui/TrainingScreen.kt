package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.FitIcons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.semantics
import com.fitlens.companion.ui.design.ToggleOption
import com.fitlens.companion.ui.design.DropdownPill
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.CustomType
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MeasureUnits
import com.fitlens.companion.data.Records
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.SetRow
import kotlinx.coroutines.launch
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import androidx.compose.foundation.layout.heightIn
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.DistanceUnits
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.DateRangePickerDialog
import com.fitlens.companion.ui.design.FitTabRow
import com.fitlens.companion.ui.design.StepperField
import androidx.compose.ui.text.input.KeyboardType
import com.fitlens.companion.ui.design.SetRow as SetRowView
import com.fitlens.companion.ui.design.PeriodDropdown
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.SearchablePicker

/** Estimated one-rep max in kg (see [Records.factor] for the formula). */
fun e1rm(s: SetRow): Double = Records.oneRepMax(s)

internal fun isTimeBased(snap: Snapshot, exId: Long, sets: List<SetRow>): Boolean =
    ExerciseTypes.timeBased(snap.exercises[exId]?.type ?: 0, sets.any { it.weightKg != 0.0 || it.reps != 0 })

/**
 * The graph names an exercise offers, in order: the same lists [ExerciseGraphPane] builds (#15 stores the index). A
 * time-based FitLens type that also records weight or reps (#14) gets those graphs after the time ones, and FitNotes's
 * extra graphs (#22) come after the older ones, so a saved default graph keeps pointing at the same graph. The names
 * are FitNotes's (#22): "Estimated 1RM", "Workout volume", "Workout reps".
 */
fun graphLabels(type: Int, timeBased: Boolean): List<String> =
    if (ExerciseTypes.isCustom(type)) customGraphLabels(type)
    else if (timeBased) {
        listOf(GRAPH_LONGEST, GRAPH_TOTAL_TIME, GRAPH_DISTANCE) +
            (if (type > ExerciseTypes.TIME && ExerciseTypes.usesWeight(type)) listOf(GRAPH_MAX_WEIGHT) else emptyList()) +
            (if (type > ExerciseTypes.TIME && ExerciseTypes.usesReps(type)) listOf(GRAPH_WORKOUT_REPS) else emptyList()) +
            // FitNotes's cardio graphs (#22), in the exercise's distance unit (#7). Appended, so saved defaults hold.
            (if (ExerciseTypes.usesDistance(type)) listOf(GRAPH_MAX_DISTANCE) else emptyList()) +
            (if (ExerciseTypes.usesDistance(type) && ExerciseTypes.usesDuration(type)) listOf(GRAPH_MAX_SPEED, GRAPH_MAX_PACE) else emptyList())
    } else {
        listOf(
            GRAPH_E1RM, GRAPH_MAX_WEIGHT, GRAPH_WORKOUT_VOLUME, GRAPH_WORKOUT_REPS, GRAPH_MAX_REPS,
            GRAPH_MAX_VOLUME, GRAPH_WEIGHT_FOR_REPS, GRAPH_RECORDS, GRAPH_RELATIVE_STRENGTH
        )
    }

/**
 * A user-defined type's graphs (#14), from what it records: the weight-and-reps list when it has both, otherwise the
 * graphs for each value it has, then its own metric's best and total.
 */
private fun customGraphLabels(type: Int): List<String> {
    val w = ExerciseTypes.usesWeight(type)
    val r = ExerciseTypes.usesReps(type)
    val d = ExerciseTypes.usesDistance(type)
    val t = ExerciseTypes.usesDuration(type)
    return buildList {
        if (w && r) {
            addAll(listOf(
                GRAPH_E1RM, GRAPH_MAX_WEIGHT, GRAPH_WORKOUT_VOLUME, GRAPH_WORKOUT_REPS, GRAPH_MAX_REPS,
                GRAPH_MAX_VOLUME, GRAPH_WEIGHT_FOR_REPS, GRAPH_RECORDS, GRAPH_RELATIVE_STRENGTH
            ))
        } else {
            if (w) add(GRAPH_MAX_WEIGHT)
            if (r) { add(GRAPH_WORKOUT_REPS); add(GRAPH_MAX_REPS) }
        }
        if (t) { add(GRAPH_LONGEST); add(GRAPH_TOTAL_TIME) }
        if (d) { add(GRAPH_DISTANCE); add(GRAPH_MAX_DISTANCE) }
        if (d && t) { add(GRAPH_MAX_SPEED); add(GRAPH_MAX_PACE) }
        ExerciseTypes.metricOf(type)?.let { addAll(metricGraphLabels(it)) }
    }
}

/** A custom metric's graphs (#14): "Best Height" (the day's best set) and "Total Height" (the day's sum). */
internal fun metricGraphLabels(m: CustomType): List<String> =
    m.metricName?.let { listOf("Best $it", "Total $it") }.orEmpty()

/** The custom metric of exercise [exId]'s type, if it has one (#14). */
private fun metricFor(snap: Snapshot, exId: Long): CustomType? =
    snap.exercises[exId]?.let { ExerciseTypes.metricOf(it.type) }

internal const val GRAPH_E1RM = "Estimated 1RM"
internal const val GRAPH_MAX_WEIGHT = "Max Weight"
internal const val GRAPH_WORKOUT_VOLUME = "Workout Volume"
internal const val GRAPH_WORKOUT_REPS = "Workout Reps"
internal const val GRAPH_MAX_REPS = "Max Reps"
internal const val GRAPH_MAX_VOLUME = "Max Volume"
internal const val GRAPH_WEIGHT_FOR_REPS = "Max Weight for Reps"
internal const val GRAPH_RECORDS = "Personal Records"
/** Estimated 1RM divided by the nearest bodyweight (#56). Appended, so saved default graphs hold. */
internal const val GRAPH_RELATIVE_STRENGTH = "Relative Strength"
/** FitNotes's name for the longest single set (#22); "Longest set" before 1.0.66. */
internal const val GRAPH_LONGEST = "Max Time"
internal const val GRAPH_TOTAL_TIME = "Total Time"
internal const val GRAPH_DISTANCE = "Distance"
internal const val GRAPH_MAX_DISTANCE = "Max Distance"
internal const val GRAPH_MAX_SPEED = "Max Speed"
internal const val GRAPH_MAX_PACE = "Max Pace"

/** Speed per hour in km or mi ("km/h", "mph"); metres, as swimmers and rowers count them, per minute (#22). */
internal fun speedUnit(distUnit: String): String = when (distUnit) {
    DistanceUnits.M -> "m/min"
    DistanceUnits.MI -> "mph"
    else -> "$distUnit/h"
}

/** A set's speed in [speedUnit], or 0 when it has no distance or time. */
internal fun speedOf(s: SetRow, distUnit: String): Double =
    if (s.distance <= 0 || s.durationSec <= 0) 0.0
    else s.distance / s.durationSec * (if (distUnit == DistanceUnits.M) 60.0 else 3600.0)

/** A set's pace in seconds per km or mi, or per 100 m (as [pace] shows it), or 0 when it has no distance or time. */
internal fun paceSecondsOf(s: SetRow, distUnit: String): Double =
    if (s.distance <= 0 || s.durationSec <= 0) 0.0
    else s.durationSec / s.distance * (if (distUnit == DistanceUnits.M) 100.0 else 1.0)

/**
 * One graph: [fn] gives a day's value from its sets, or [series] gives the whole line at once for a graph that
 * depends on earlier days (Personal records).
 */
private data class GraphType(
    val label: String,
    val fn: (List<SetRow>) -> Double,
    val isWeight: Boolean,
    val isTime: Boolean = false,
    val series: ((Map<String, List<SetRow>>) -> List<Pair<String, Double>>)? = null,
    /** Pace: a lower value is better, and values read as m:ss (#22). */
    val lowerIsBetter: Boolean = false
)

/** The rep count each exercise's "Max weight for reps" graph shows (#22), kept while the app is open. */
private object RepsForGraph {
    private val chosen = HashMap<Long, Int>()
    fun get(exId: Long): Int = chosen[exId] ?: 5
    fun set(exId: Long, reps: Int) { chosen[exId] = reps }
}

/**
 * An exercise's records, stats and goals (#89). Its graph and history now sit on the exercise screen's tabs (#82), as in
 * FitNotes; this screen opens from the exercise screen's top bar and the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(snap: Snapshot, nav: Nav, exId: Long, initialTab: Int = 0) {
    val ex = snap.exercises[exId]
    val sets = snap.setsByExercise[exId] ?: emptyList()
    val timeBased = isTimeBased(snap, exId, sets)
    // Records leave out warm-ups unless Settings counts them (#43).
    val statSets = snap.statSetsByExercise[exId] ?: emptyList()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var calculator by remember { mutableStateOf(false) }
    var e1rmSettings by remember { mutableStateOf(false) }
    var addGoal by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        // FitNotes's bar follows the tab (#142): the calculator on Records, + on Goals.
        BackTopBar(ex?.name ?: stringResource(R.string.ex_fallback), onBack = { nav.pop() }) {
            if (tab == 0 && !timeBased) {
                IconButton(onClick = { calculator = true }) { Icon(FitIcons.Calculate, contentDescription = stringResource(R.string.ex_calculator)) }
                // FitNotes's gear beside it opens the Estimated 1RM Settings (#148).
                IconButton(onClick = { e1rmSettings = true }) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.ex_e1rm_settings)) }
            }
            if (tab == 2) IconButton(onClick = { addGoal = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ex_add_goal)) }
        }
        FitTabRow(
            titles = listOf(stringResource(R.string.ex_tab_records), stringResource(R.string.ex_tab_stats), stringResource(R.string.ex_tab_goals)),
            selected = tab,
            onSelect = { tab = it }
        )
        when (tab) {
            0 -> RecordsTab(snap, statSets, timeBased)
            1 -> ExerciseStatsTab(snap, nav, exId, statSets, timeBased)
            else -> GoalsTab(snap, exId, timeBased, addRequested = addGoal, onAddHandled = { addGoal = false }, showAddButton = false)
        }
    }
    if (calculator) OneRepMaxSheet(snap, statSets.maxByOrNull { Records.oneRepMax(it) }) { calculator = false }
    if (e1rmSettings) EstimatedOneRmSettingsSheet { e1rmSettings = false }
}

/** Every exercise graph by name (#22), for [ExerciseGraphPane], comparisons (#53) and pinned cards (#55). */
private fun graphTypes(repsFor: Int, distUnit: String, metric: CustomType? = null): Map<String, GraphType> = (listOf(
    GraphType(GRAPH_LONGEST, { l -> l.maxOf { it.durationSec }.toDouble() }, false, true),
    GraphType(GRAPH_TOTAL_TIME, { l -> l.sumOf { it.durationSec }.toDouble() }, false, true),
    GraphType(GRAPH_DISTANCE, { l -> l.sumOf { it.distance } }, false),
    GraphType(GRAPH_E1RM, { l -> l.maxOf { e1rm(it) } }, true),
    GraphType(GRAPH_MAX_WEIGHT, { l -> l.maxOf { it.weightKg } }, true),
    GraphType(GRAPH_WORKOUT_VOLUME, { l -> l.sumOf { it.weightKg * it.reps } }, true),
    GraphType(GRAPH_WORKOUT_REPS, { l -> l.sumOf { it.reps }.toDouble() }, false),
    GraphType(GRAPH_MAX_REPS, { l -> l.maxOf { it.reps }.toDouble() }, false),
    // FitNotes's extra graphs (#22): the best single set, the heaviest at a chosen rep count, and the records.
    GraphType(GRAPH_MAX_VOLUME, { l -> l.maxOf { it.weightKg * it.reps } }, true),
    GraphType(GRAPH_WEIGHT_FOR_REPS, { l -> Records.maxWeightForReps(l, repsFor) }, true),
    GraphType(GRAPH_RECORDS, { 0.0 }, true, series = { byDate -> Records.recordProgress(byDate) }),
    // Needs body values as well as sets, so [exerciseGraphPoints] works it out with [relativeStrength] (#56).
    GraphType(GRAPH_RELATIVE_STRENGTH, { 0.0 }, false),
    // Cardio (#22): the farthest set, the fastest set as speed, and the fastest set as pace.
    GraphType(GRAPH_MAX_DISTANCE, { l -> l.maxOf { it.distance } }, false),
    GraphType(GRAPH_MAX_SPEED, { l -> l.maxOf { speedOf(it, distUnit) } }, false),
    GraphType(
        GRAPH_MAX_PACE,
        { l -> l.map { paceSecondsOf(it, distUnit) }.filter { it > 0 }.minOrNull() ?: 0.0 },
        false, isTime = true, lowerIsBetter = true
    )
) + (metric?.let { m ->
    // A custom type's own metric (#14). Sets without a value are left out rather than counted as 0.
    val (best, total) = metricGraphLabels(m)
    listOf(
        GraphType(best, { l -> l.mapNotNull { it.metric }.maxOrNull() ?: 0.0 }, false),
        GraphType(total, { l -> l.sumOf { it.metric ?: 0.0 } }, false)
    )
} ?: emptyList())).associateBy { it.label }

/**
 * Exercise [exId]'s points on graph [label], one per day, with weights in exercise [unitOf]'s unit (its own, or the
 * exercise it's compared with, #53). Graphs leave out warm-ups unless Settings counts them (#43). It goes over the
 * exercise's whole history, so screens call it off the main thread through [rememberDerived] (#60).
 */
internal fun exerciseGraphPoints(snap: Snapshot, exId: Long, label: String, repsFor: Int, unitOf: Long = exId): List<ChartPoint> {
    if (label == GRAPH_RELATIVE_STRENGTH) return relativeStrength(snap, exId).points
    val g = graphTypes(repsFor, snap.distanceUnit(exId), metricFor(snap, exId))[label] ?: return emptyList()
    val byDate = snap.statSetsByExercise[exId].orEmpty().groupBy { it.date }.toSortedMap()
    val raw = g.series?.invoke(byDate) ?: byDate.entries.map { (d, l) -> d to g.fn(l) }
    return raw.map { (d, v) ->
        ChartPoint(Dates.epochDay(d), if (g.isWeight) snap.weight(v, unitOf) else if (g.isTime) v / 60.0 else v, d)
    }.filter { it.y > 0 }
}

/** How far from a training day a bodyweight may be and still count for its relative strength (#56). */
internal const val BODYWEIGHT_DAYS = 14

/**
 * An exercise's relative strength (#56): each day's best estimated 1RM divided by the bodyweight logged nearest that
 * day, within [BODYWEIGHT_DAYS]. Days with no bodyweight that close are listed in [missing] (ISO dates), never given
 * a guessed value.
 */
internal class RelativeStrength(val points: List<ChartPoint>, val missing: List<String>)

internal fun relativeStrength(snap: Snapshot, exId: Long): RelativeStrength {
    val byDate = snap.statSetsByExercise[exId].orEmpty().groupBy { it.date }.toSortedMap()
    val bw = snap.bodyweightName
    val points = ArrayList<ChartPoint>()
    val missing = ArrayList<String>()
    for ((d, l) in byDate) {
        val best = l.maxOf { e1rm(it) }
        if (best <= 0) continue
        // Body values are held in the unit they're shown in; the ratio needs both in kilograms.
        val kg = bw?.let { snap.valueNear(it, d, BODYWEIGHT_DAYS) }?.first
            ?.let { r -> MeasureUnits.convert(r.value, r.unit, "kg") }
        if (kg == null || kg <= 0) missing += d
        else points += ChartPoint(Dates.epochDay(d), best / kg, d)
    }
    return RelativeStrength(points, missing)
}

/** The exercises in [compare] (#53) that can share exercise [exId]'s graph [label]: they exist and offer that graph. */
internal fun comparable(snap: Snapshot, exId: Long, label: String, compare: List<Long>): List<Long> =
    compare.filter { it != exId && snap.exercises.containsKey(it) && label in graphLabelsFor(snap, it) }.take(GraphCompare.MAX - 1)

/**
 * Graph [label] for exercise [exId] and the exercises it's compared with (#53), one series each, over range
 * [rangeIdx] of [RANGES] measured back from the latest day any of them was logged. [relative] shows each series as a
 * percentage of its first value in the range, so lifts of very different weights can be compared.
 */
internal fun comparedSeries(
    snap: Snapshot, exId: Long, label: String, compare: List<Long>, rangeIdx: Int, relative: Boolean,
    repsFor: Int = 5
): List<LineSeries> {
    val ids = listOf(exId) + comparable(snap, exId, label, compare)
    val all = ids.map { id -> id to exerciseGraphPoints(snap, id, label, repsFor, unitOf = exId) }
    val days = RANGES[rangeIdx.coerceIn(RANGES.indices)].second
    val end = all.mapNotNull { it.second.lastOrNull()?.x }.maxOrNull()
    return all.map { (id, pts) ->
        val shown = if (days <= 0 || end == null) pts else pts.filter { it.x >= end - days }
        val base = shown.firstOrNull()?.y
        val y = if (relative && base != null && base > 0) shown.map { it.copy(y = it.y / base * 100.0) } else shown
        LineSeries(snap.exercises[id]?.name ?: "Exercise", y)
    }
}

/** Graph [label]'s unit for exercise [exId]: its weight or distance unit, minutes, speed or pace. */
internal fun graphUnit(snap: Snapshot, exId: Long, label: String): String {
    val distUnit = snap.distanceUnit(exId)
    val metric = metricFor(snap, exId)
    val g = graphTypes(5, distUnit, metric)[label] ?: return ""
    return when {
        metric != null && label in metricGraphLabels(metric) -> metric.metricUnit.orEmpty()
        label == GRAPH_RELATIVE_STRENGTH -> "× bodyweight"
        g.isWeight -> snap.weightUnitOf(exId)
        label == GRAPH_MAX_PACE -> if (distUnit == DistanceUnits.M) "/100 m" else "/$distUnit"
        g.isTime -> "min"
        label == GRAPH_DISTANCE || label == GRAPH_MAX_DISTANCE -> distUnit
        label == GRAPH_MAX_SPEED -> speedUnit(distUnit)
        else -> ""
    }
}

/** A graph value with its unit; pace reads as minutes and seconds, "5:12 /km" (#22). Percentages read "104.5%". */
internal fun graphValueText(label: String, v: Double, unit: String): String = when {
    unit == "%" -> "${fmtNum(v, 1)}%"
    label == GRAPH_RELATIVE_STRENGTH -> "${fmtNum(v, 2)} $unit"
    label == GRAPH_MAX_PACE -> "${fmtDuration((v * 60).roundToInt())} $unit"
    else -> "${fmtNum(v, 1)} $unit".trim()
}

/**
 * An exercise's graph: type, range and options, full screen, trend and goal line (#82's Graph tab, #50, #96). It can
 * compare up to four more exercises (#53) and be pinned to the Analysis overview (#55); [initial] opens it as a
 * pinned graph was saved.
 */
@Composable
fun ExerciseGraphPane(snap: Snapshot, nav: Nav, exId: Long, initial: PinnedGraph? = null) {
    val ex = snap.exercises[exId]
    val sets = snap.setsByExercise[exId] ?: emptyList()
    val timeBased = isTimeBased(snap, exId, sets)
    val type = ex?.type ?: ExerciseTypes.WEIGHT_REPS
    var repsFor by remember(exId) { mutableIntStateOf(RepsForGraph.get(exId)) }
    val distUnit = snap.distanceUnit(exId)
    // A custom type's graphs follow its definition (#14), so an edit to the type is in the key.
    val labels = remember(timeBased, type, ExerciseTypes.custom[type]) { graphLabels(type, timeBased) }
    // Opens on the pinned graph, or on the exercise's default graph when one is set (#15).
    var gIdx by rememberSaveable {
        mutableIntStateOf(
            initial?.let { labels.indexOf(it.graph) }?.takeIf { it >= 0 } ?: ex?.defaultGraph?.takeIf { it >= 0 } ?: 0
        )
    }
    var rangeIdx by rememberSaveable { mutableIntStateOf(initial?.range ?: 4) }
    var showTrend by rememberSaveable { mutableStateOf(false) }
    var fromZero by rememberSaveable { mutableStateOf(false) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    // Compare (#53): the other exercises on this graph, remembered per exercise, and whether values are relative.
    val (compare, setCompare) = rememberCompare(exId)
    var relative by rememberSaveable { mutableStateOf(initial?.relative ?: false) }
    var comparing by remember { mutableStateOf<String?>(null) }
    // A body measurement drawn over the graph on a second axis (#56), kept while the screen is open.
    var overlayName by rememberSaveable(exId) { mutableStateOf<String?>(null) }
    var pickOverlay by remember { mutableStateOf(false) }
    // The first of two points whose nearest photos open in Compare (#56), waiting for the second tap.
    var photoFrom by rememberSaveable(exId) { mutableStateOf<String?>(null) }
    // Set by the ⋮ menu's "Share graph as image" (#22); the graph item below draws and shares what it shows.
    var shareRequested by remember { mutableStateOf(false) }
    // Body values leave this screen only when chosen for that share (#56): asked whenever the graph carries them.
    var askShareBody by remember { mutableStateOf(false) }
    var shareBody by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val res = ctx.resources
    val label = labels[gIdx.coerceIn(0, labels.lastIndex)]
    val metric = metricFor(snap, exId)
    val g = remember(label, repsFor, distUnit, metric) { graphTypes(repsFor, distUnit, metric).getValue(label) }
    val others = comparable(snap, exId, label, compare)
    val isRelative = relative && others.isNotEmpty()
    var sel by remember(gIdx, rangeIdx, others, isRelative) { mutableStateOf<ChartSelection?>(null) }
    // Line, bar, area or step, remembered for each graph (#137).
    val (kind, setKind) = rememberChartKind("exercise:${g.label}")
    // A goal for this graph can be drawn as a line (#25), in the graph's own unit; not on percentages.
    var showGoal by rememberSaveable { mutableStateOf(true) }
    val goalTarget = if (isRelative) null else goalKindForGraph(g.label)?.let { k ->
        snap.goalsByExercise[exId]?.firstOrNull { it.kind == k }?.let { goalShown(snap, k, it.target, exId) }
    }
    val goalLine = if (showGoal) goalTarget else null
    // Pinned to the Analysis overview (#55). A pinned graph keeps its pin in step with its range and comparison.
    val pins = rememberPins()
    val pinned = pins.any { it.exerciseId == exId && it.graph == g.label }
    val pinNow = PinnedGraph(exId, g.label, rangeIdx, others, isRelative)
    LaunchedEffect(pinned, pinNow) {
        if (pinned) updatePins { list -> list.map { if (it.sameGraph(pinNow) && it != pinNow) pinNow else it } }
    }

    if (snap.statSetsByExercise[exId].isNullOrEmpty()) {
        EmptyState(stringResource(R.string.ex_graph_empty_title), stringResource(R.string.ex_graph_empty_body))
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            // One compact row (#115): the graph, its rep count where it has one, the range, options, pin and full screen.
            GraphOptionChips(
                rangeIdx, { rangeIdx = it },
                showTrend, { showTrend = !showTrend },
                fromZero, { fromZero = !fromZero },
                extra = listOfNotNull(
                    if (goalTarget != null) ToggleOption(stringResource(R.string.ex_goal_line), showGoal) { showGoal = !showGoal } else null,
                    if (others.isNotEmpty()) ToggleOption(stringResource(R.string.ex_relative), relative) { relative = !relative } else null
                ),
                onShare = {
                    if (overlayName != null || g.label == GRAPH_RELATIVE_STRENGTH) askShareBody = true
                    else { shareBody = false; shareRequested = true }
                },
                actions = listOf(
                    MenuAction(stringResource(R.string.ex_compare_menu)) { comparing = "sheet" },
                    MenuAction(stringResource(if (overlayName == null) R.string.ex_overlay_add else R.string.ex_overlay_change)) { pickOverlay = true }
                ),
                kind = kind, onKind = setKind,
                leading = {
                    DropdownPill(stringResource(R.string.ex_graph), labels.map { graphName(res, it) }, gIdx.coerceIn(0, labels.lastIndex)) { gIdx = it }
                    // "Max weight for reps" (#22): which rep count, 1 to 15, kept for this exercise while the app is open.
                    if (g.label == GRAPH_WEIGHT_FOR_REPS) {
                        DropdownPill(stringResource(R.string.ex_reps), (1..Records.MAX_REPS).map { res.getQuantityString(R.plurals.reps_count, it, it) }, repsFor - 1) { i ->
                            repsFor = i + 1
                            RepsForGraph.set(exId, i + 1)
                        }
                    }
                },
                trailing = {
                    PinGraphButton(pinned) {
                        if (pinned) {
                            updatePins { list -> list.filterNot { it.sameGraph(pinNow) } }
                            UiEvents.show(res.getString(R.string.ex_unpinned))
                        } else {
                            updatePins { list -> list.filterNot { it.sameGraph(pinNow) } + pinNow }
                            UiEvents.show(res.getString(R.string.ex_pinned))
                        }
                    }
                    ExpandGraphButton { fullScreen = true }
                }
            )
            if (g.label == GRAPH_RELATIVE_STRENGTH) {
                Text(
                    if (snap.bodyweightName == null) stringResource(R.string.ex_relative_needs_bw)
                    else stringResource(R.string.ex_relative_explain, BODYWEIGHT_DAYS),
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (g.label == GRAPH_RECORDS) {
                Text(
                    stringResource(R.string.ex_records_explain),
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val skipped = compare.filter { it != exId && it !in others }.mapNotNull { snap.exercises[it]?.name }
            if (skipped.isNotEmpty()) {
                Text(
                    stringResource(R.string.ex_not_shown, skipped.joinToString(", "), graphName(res, g.label)),
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            // Worked out off the main thread and cached across screens (#50, #60). The 1RM formula is in the key, so
            // a Settings change isn't hidden by the cache.
            val formula = Records.chosen()
            val isBodyRatio = g.label == GRAPH_RELATIVE_STRENGTH
            // Relative strength also depends on body values (#56); other graphs ignore body writes.
            val bodyKey = if (isBodyRatio) snap.bodyKey else null
            val loaded = rememberDerived(
                "exerciseGraph", snap.trainingKey, bodyKey, exId, g.label, repsFor, others, rangeIdx, isRelative,
                formula, Records.maxRepsFor(formula)
            ) { comparedSeries(snap, exId, g.label, others, rangeIdx, isRelative, repsFor) }
            val series = loaded ?: listOf(LineSeries(g.label, emptyList()))
            val shown = series.first().points
            // Training days with no bodyweight close enough: marked, never guessed (#56).
            val gapDays: Set<Long> = if (!isBodyRatio) emptySet() else rememberDerived(
                "relativeGaps", snap.trainingKey, snap.bodyKey, exId, formula, Records.maxRepsFor(formula)
            ) { relativeStrength(snap, exId).missing.map { Dates.epochDay(it) }.toSet() } ?: emptySet()
            val yFormat: (Double) -> String = if (isBodyRatio && !isRelative) { v -> fmtNum(v, 2) } else { v -> fmtNum(v, 1) }
            // The body overlay (#56): the chosen measurement's daily values, in the unit it's shown in.
            val overlayDef = overlayName?.let { n -> snap.allMeasurements.firstOrNull { it.name == n } }
            val overlay = overlayDef?.let { d ->
                remember(snap.bodyKey, d.name) {
                    LineSeries(d.name, snap.dailySeries(d.name).map { ChartPoint(Dates.epochDay(it.date), it.value, it.date) })
                }
            }
            val overlayUnit = overlayDef?.unit.orEmpty()
            /** The overlay's value on or nearest [date], with its date when it isn't the same day. */
            fun overlayAt(date: String): String? {
                val o = overlay ?: return null
                val x = Dates.epochDay(date)
                val q = o.points.minByOrNull { kotlin.math.abs(it.x - x) } ?: return null
                return "${o.label} ${fmtNum(q.y, 1)} $overlayUnit".trim() + if (q.x != x) " (${Dates.medium(q.date)})" else ""
            }
            val photoDays = remember(snap.photosByDate) { snap.photosByDate.keys.map { Dates.epochDay(it) }.toSet() }
            val unit = if (isRelative) "%" else graphUnit(snap, exId, g.label)
            // Pace is graphed in minutes per distance, so its trend reads "min /km" (#152).
            val trendUnit = if (g.label == GRAPH_MAX_PACE && !isRelative) "min $unit" else unit
            fun show(v: Double): String = graphValueText(g.label, v, unit)
            // Each series from its first value to its last, with its best (#53 lists every series).
            fun line(s: LineSeries, name: String): String? {
                val pts = s.points
                if (pts.isEmpty()) return null
                val best = if (g.lowerIsBetter) pts.minOf { it.y } else pts.maxOf { it.y }
                return res.getString(R.string.ex_series_line, name, show(pts.first().y), show(pts.last().y), show(best))
            }
            val summary = if (series.size == 1) line(series[0], graphName(res, g.label)).orEmpty()
            else series.mapNotNull { line(it, it.label) }.joinToString("\n")
            // Tapping a date shows every series' value on or nearest that date (#53).
            fun valuesOnly(p: ChartPoint): String =
                if (series.size == 1) show(p.y)
                else series.mapNotNull { s ->
                    s.points.minByOrNull { kotlin.math.abs(it.x - p.x) }?.let { q ->
                        "${s.label} ${show(q.y)}" + if (q.x != p.x) " (${Dates.medium(q.date)})" else ""
                    }
                }.joinToString("\n")
            fun valuesAt(p: ChartPoint): String = valuesOnly(p) + (overlayAt(p.date)?.let { "\n$it" } ?: "")
            val p = sel?.let { series.getOrNull(it.series)?.points?.getOrNull(it.index) }
            LaunchedEffect(shareRequested) {
                if (!shareRequested) return@LaunchedEffect
                shareRequested = false
                if (shown.isEmpty()) { UiEvents.show(res.getString(R.string.ex_share_nothing)); return@LaunchedEffect }
                val name = ex?.name ?: res.getString(R.string.ex_fallback)
                // The same fit as the graph on screen shows, over the whole range (#152).
                val trend = if (showTrend) trendOf(shown) else null
                val image = ShareImages.GraphImage(
                    title = name,
                    graph = graphName(res, g.label),
                    range = rangeName(res, RANGES[rangeIdx].first),
                    points = shown,
                    format = { v -> show(v) },
                    // The body overlay goes in as a line of figures only when chosen for this share (#56).
                    summary = line(series[0], graphName(res, g.label)).orEmpty() + (overlay?.takeIf { shareBody }?.let { o ->
                        val lo = shown.minOf { it.x }
                        val hi = shown.maxOf { it.x }
                        val inRange = o.points.filter { q -> q.x in lo..hi }
                        if (inRange.isEmpty()) null
                        else "\n${o.label}: ${fmtNum(inRange.first().y, 1)} → ${fmtNum(inRange.last().y, 1)} $overlayUnit".trimEnd()
                    } ?: ""),
                    trend = trend,
                    trendNote = trend?.let { res.getString(R.string.ex_trend, trendText(it, { v -> fmtNum(v, 1) }, trendUnit)) },
                    goal = goalLine,
                    kind = kind
                )
                ShareImages.share(ctx, res.getString(R.string.ex_share_creating), ShareImages.fileName(name, g.label)) { ShareImages.renderGraph(image) }
            }
            if (loaded == null) {
                // A first visit to a long history; later visits come from the cache at once.
                Text(
                    stringResource(R.string.ex_working),
                    Modifier.fillMaxWidth().heightIn(min = graphHeight()).padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FitChart(
                    series,
                    Modifier.padding(horizontal = 8.dp),
                    kind = kind,
                    photoDays = photoDays,
                    selected = sel,
                    onSelect = { pick ->
                        val from = photoFrom
                        val to = series.getOrNull(pick.series)?.points?.getOrNull(pick.index)?.date
                        sel = pick
                        ChartHints.tapped()
                        if (from != null && to != null) {
                            photoFrom = null
                            comparePhotosOn(res, snap, nav, from, to)
                        }
                    },
                    unit = unit,
                    showTrend = showTrend,
                    trendUnit = trendUnit,
                    yFromZero = fromZero,
                    goal = goalLine,
                    onExpand = { ChartHints.expanded(); fullScreen = true },
                    yFormat = yFormat,
                    gapDays = gapDays,
                    overlay = overlay,
                    overlayUnit = overlayUnit
                )
            }
            ChartHint()
            if (fullScreen) {
                FullScreenChart(
                    "${ex?.name ?: stringResource(R.string.ex_fallback)} · ${graphName(res, g.label)}",
                    onDismiss = { fullScreen = false },
                    controls = {
                        GraphOptionChips(
                            rangeIdx, { rangeIdx = it },
                            showTrend, { showTrend = !showTrend },
                            fromZero, { fromZero = !fromZero },
                            kind = kind, onKind = setKind
                        )
                    },
                    valueZoom = kind != ChartKind.BAR,
                    footer = {
                        p?.let { pt ->
                            Text(
                                "${Dates.long(pt.date)}: ${valuesAt(pt)}",
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                ) { vp, h, resetZoom ->
                    FitChart(
                        series,
                        kind = kind,
                        height = h,
                        photoDays = photoDays,
                        selected = sel,
                        onSelect = { sel = it },
                        unit = unit,
                        showTrend = showTrend,
                        trendUnit = trendUnit,
                        yFromZero = fromZero,
                        goal = goalLine,
                        viewport = vp,
                        onExpand = resetZoom,
                        yFormat = yFormat,
                        gapDays = gapDays,
                        overlay = overlay,
                        overlayUnit = overlayUnit
                    )
                }
            }
            if (p != null) {
                Text(
                    stringResource(R.string.ex_point_open_day, Dates.long(p.date), valuesAt(p)),
                    Modifier.fillMaxWidth().clickable { nav.push(Screen.Day(p.date)) }.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                // What the user looked like then (#56), and Compare with the photos near a second point.
                NearestPhotoThumb(snap, nav, p.date, Modifier.padding(horizontal = 16.dp))
                if (photoFrom == null && nearestPhoto(snap, p.date, GRAPH_PHOTO_DAYS) != null) {
                    TextButton(onClick = { photoFrom = p.date }, Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.ex_compare_photo))
                    }
                }
            }
            photoFrom?.let { from ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.ex_tap_second, Dates.medium(from)),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { photoFrom = null }) { Text(stringResource(R.string.cancel)) }
                }
            }
            if (p == null && summary.isNotEmpty()) {
                Text(
                    summary,
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
    if (askShareBody) {
        // Relative strength is worked out from bodyweight, so sharing it at all shares a body value.
        val ratio = g.label == GRAPH_RELATIVE_STRENGTH
        FitSheet(
            title = stringResource(if (ratio) R.string.ex_share_ratio_title else R.string.ex_share_body_title),
            onDismiss = { askShareBody = false },
            confirmLabel = if (ratio) stringResource(R.string.ex_share_it)
            else stringResource(R.string.ex_include, overlayName ?: stringResource(R.string.ex_them)),
            onConfirm = { askShareBody = false; shareBody = true; shareRequested = true },
            secondaryLabel = if (ratio) null else stringResource(R.string.ex_leave_out),
            onSecondary = if (ratio) null else ({ askShareBody = false; shareBody = false; shareRequested = true })
        ) {
            Text(
                if (ratio) stringResource(R.string.ex_share_ratio_body)
                else stringResource(R.string.ex_share_body_body, overlayName ?: stringResource(R.string.ex_the_overlay)),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    if (pickOverlay) {
        BodyOverlaySheet(snap, overlayName, onPick = { overlayName = it; pickOverlay = false }) { pickOverlay = false }
    }
    when (comparing) {
        "sheet" -> CompareSheet(
            snap, exId, g.label, compare,
            onChange = setCompare,
            onAdd = { comparing = "pick" },
            onDismiss = { comparing = null }
        )
        "pick" -> {
            // exercisePickerItems is composable (category colours), so it's filtered here rather than remembered.
            val items = exercisePickerItems(snap)
                .filter { it.id != exId && it.id !in compare && snap.setsByExercise[it.id].orEmpty().isNotEmpty() }
            SearchablePicker(
                title = stringResource(R.string.ex_compare_with),
                items = items,
                onDismiss = { comparing = "sheet" },
                onPick = { ids ->
                    setCompare((compare + ids).distinct().take(GraphCompare.MAX - 1))
                    if (compare.size + ids.size > GraphCompare.MAX - 1) UiEvents.show(res.getString(R.string.ex_compare_max, GraphCompare.MAX))
                    comparing = "sheet"
                },
                multiSelect = true,
                searchLabel = stringResource(R.string.ex_search_exercises)
            )
        }
    }
}

/**
 * Two graph points' nearest progress photos (#56), each within [GRAPH_PHOTO_DAYS], side by side in Compare, the
 * earlier one first. Says why when a point has no photo near it, or both points share the same one.
 */
private fun comparePhotosOn(res: Resources, snap: Snapshot, nav: Nav, a: String, b: String) {
    val (first, second) = if (a <= b) a to b else b to a
    val pa = nearestPhoto(snap, first, GRAPH_PHOTO_DAYS)
    val pb = nearestPhoto(snap, second, GRAPH_PHOTO_DAYS)
    when {
        pa == null || pb == null -> {
            val missing = if (pa == null) first else second
            UiEvents.show(res.getString(R.string.ex_no_photo_near, GRAPH_PHOTO_DAYS, Dates.medium(missing)))
        }
        pa.id == pb.id -> UiEvents.show(res.getString(R.string.ex_same_photo))
        else -> nav.push(Screen.Compare(pa.id, pb.id))
    }
}

/**
 * Overlay (#56): which body measurement to draw over a training graph on its second axis, or none. Only measurements
 * with values are offered. The overlay stays on this screen: shared graph images leave body values out.
 */
@Composable
internal fun BodyOverlaySheet(snap: Snapshot, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val names = snap.usedMeasurements.map { it.name }.filter { snap.recordsByName[it].orEmpty().isNotEmpty() }
    FitSheet(
        title = stringResource(R.string.ex_overlay_title),
        onDismiss = onDismiss,
        secondaryLabel = if (current != null) stringResource(R.string.ex_none) else null,
        onSecondary = if (current != null) ({ onPick(null) }) else null
    ) {
        if (names.isEmpty()) {
            Text(
                stringResource(R.string.ex_overlay_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        names.forEach { n ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(n) }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    n, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (n == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (n == current) Text(stringResource(R.string.ex_overlay_shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Text(
            stringResource(R.string.ex_overlay_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Compare (#53): the exercises on this graph, the exercise itself first. Add exercise opens the picker, × removes
 * one and Clear removes them all. Up to [GraphCompare.MAX] in all.
 */
@Composable
private fun CompareSheet(
    snap: Snapshot,
    exId: Long,
    graph: String,
    compare: List<Long>,
    onChange: (List<Long>) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    FitSheet(
        title = stringResource(R.string.ex_compare_title),
        onDismiss = onDismiss,
        // Changes apply as they're made, so the sheet only needs closing.
        dismissLabel = stringResource(R.string.ex_done),
        secondaryLabel = if (compare.isNotEmpty()) stringResource(R.string.ex_clear) else null,
        onSecondary = if (compare.isNotEmpty()) ({ onChange(emptyList()) }) else null
    ) {
        Text(
            stringResource(R.string.ex_compare_intro, GraphCompare.MAX),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val offered = remember(snap, compare) { compare.associateWith { graphLabelsFor(snap, it) } }
        Text(snap.exercises[exId]?.name ?: stringResource(R.string.ex_fallback), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
        compare.mapNotNull { id -> snap.exercises[id]?.name?.let { id to it } }.forEach { (id, name) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    if (graph !in offered[id].orEmpty()) {
                        Text(
                            stringResource(R.string.ex_compare_no_graph, graph),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { onChange(compare - id) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ex_compare_remove, name))
                }
            }
        }
        if (compare.size < GraphCompare.MAX - 1) {
            TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ex_add_exercise)) }
        }
    }
}

/** An exercise's history, newest day first, each day opening its log (#82's History tab). */
@Composable
fun ExerciseHistoryPane(snap: Snapshot, nav: Nav, exId: Long) {
    val sets = snap.setsByExercise[exId] ?: emptyList()
    val byDate = remember(sets) { sets.groupBy { it.date }.toSortedMap() }
    // The same labelled columns as the day log (#101), fixed for the whole history so days line up.
    val fields = remember(snap, exId, sets) { setFields(snap, exId, sets) }
    val res = LocalContext.current.resources
    if (sets.isEmpty()) {
        EmptyState(stringResource(R.string.ex_history_empty_title), stringResource(R.string.ex_history_empty_body))
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        byDate.entries.reversed().forEach { (d, l) ->
            item(key = d) {
                Column(Modifier.fillMaxWidth().clickable { nav.push(Screen.Day(d)) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // FitNotes's day heading (#122, #142): "MONDAY, SEPTEMBER 28" over a rule, then the sets.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(historyDay(d), Modifier.weight(1f))
                        if (snap.photosByDate.containsKey(d)) Dot(LocalChartColors.current.accent)
                    }
                    l.forEachIndexed { i, s ->
                        val marks = setMarks(s)
                        SetRowView(
                            index = i + 1,
                            summary = describeSet(res, snap, s.weightKg, s.reps, s.distance, s.durationSec, s.exerciseId),
                            cells = setCells(snap, fields, s),
                            comment = s.comment,
                            isPr = s.isPr,
                            framed = false,
                            showIndex = false,
                            badge = marks.badge,
                            badgeSpoken = marks.badgeSpoken,
                            effort = marks.effort,
                            effortSpoken = marks.effortSpoken,
                            // Tapping a set opens it on the exercise screen, selected for Update or Delete (#22).
                            onClick = { nav.push(Screen.SetEntry(d, exId, setId = s.id)) }
                        )
                    }
                    // The exercise's comment in that day's workout (#107).
                    snap.exerciseComments[d.take(10)]?.get(exId)?.let { note ->
                        Text(
                            "“$note”",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    // FitLens's extras under the sets: the day's totals (#22) and Copy to today, with Undo.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            dayTotals(res, snap, exId, l),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (d.take(10) != Dates.today()) {
                            TextButton(onClick = { copyToToday(res, d, l.map { it.id }) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.ex_copy_today))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A day heading as FitNotes writes it (#142): "MONDAY, SEPTEMBER 28", with the year when it isn't this one. Used by
 * every History list and the calendar's selected day (#144).
 */
internal fun historyDay(date: String): String {
    val d = Dates.parse(date) ?: return date
    val pattern = if (d.year == java.time.LocalDate.now().year) "EEEE, MMMM d" else "EEEE, MMMM d, yyyy"
    return d.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault())).uppercase()
}

/** One day's totals for an exercise's history: sets, then reps and volume, or distance and time (#22). */
private fun dayTotals(res: Resources, snap: Snapshot, exId: Long, sets: List<SetRow>): String {
    val parts = mutableListOf(res.getQuantityString(R.plurals.sets_count, sets.size, sets.size))
    val reps = sets.sumOf { it.reps }
    val volume = sets.sumOf { it.weightKg * it.reps }
    val distance = sets.sumOf { it.distance }
    val time = sets.sumOf { it.durationSec }
    if (reps > 0) parts += res.getQuantityString(R.plurals.reps_count, reps, reps)
    if (volume > 0) parts += res.getString(R.string.ex_volume, fmtNum(snap.weight(volume, exId), 0), snap.weightUnitOf(exId))
    if (distance > 0) parts += "${fmtNum(distance)} ${snap.distanceUnit(exId)}"
    if (time > 0) parts += fmtDuration(time)
    return parts.joinToString("  ·  ")
}

/** Copies [ids] (one day's sets of an exercise) from [date] to today, and offers Undo (#22). */
private fun copyToToday(res: Resources, date: String, ids: List<Long>) {
    AppScope.scope.launch {
        try {
            val copies = Workouts.copyWorkout(date, Dates.today(), ids)
            UiEvents.show(res.getQuantityString(R.plurals.ex_copied, copies.size, copies.size), res.getString(R.string.undo)) {
                AppScope.scope.launch { Workouts.deleteSets(copies) }
            }
        } catch (e: WorkoutDataException) {
            UiEvents.show(e.message ?: res.getString(R.string.ex_copy_failed))
        }
    }
}

/** One row of an exercise's Records tab, worked out off the main thread (#60). */
private data class RecordLine(val label: String, val value: String, val unit: String, val date: String, val superseded: Boolean)

/**
 * The Records tab's rows for one exercise's [sets] (#142): the longest set for a timed exercise, otherwise each rep
 * count's record, actual or [estimated] from the best estimated 1RM.
 */
private fun recordLines(res: Resources, snap: Snapshot, sets: List<SetRow>, timeBased: Boolean, estimated: Boolean): List<RecordLine> {
    val exId = sets.firstOrNull()?.exerciseId
    // A custom type's own metric (#14): its best set is a record, whatever else the type records.
    val metric = exId?.let { metricFor(snap, it) }
    val metricLine = metric?.let { m ->
        sets.filter { it.metric != null }.maxByOrNull { it.metric!! }?.let { best ->
            RecordLine(res.getString(R.string.ex_record_best, m.metricName?.lowercase().orEmpty()), fmtNum(best.metric!!, 2), m.metricUnit.orEmpty(), Dates.medium(best.date), superseded = false)
        }
    }
    if (timeBased) {
        // Timed exercises have no rep maxes: the longest set is their record, with the farthest for distance types.
        val longest = sets.maxByOrNull { it.durationSec }?.takeIf { it.durationSec > 0 }?.let { l ->
            RecordLine(res.getString(R.string.ex_record_longest), fmtDuration(l.durationSec), "", Dates.medium(l.date), superseded = false)
        }
        val farthest = if (exId != null && ExerciseTypes.isCustom(snap.exercises[exId]?.type ?: 0)) {
            sets.maxByOrNull { it.distance }?.takeIf { it.distance > 0 }?.let { f ->
                RecordLine(res.getString(R.string.ex_record_farthest), fmtNum(f.distance, 2), snap.distanceUnit(f.exerciseId), Dates.medium(f.date), superseded = false)
            }
        } else null
        return listOfNotNull(longest, farthest, metricLine)
    }
    val unit = snap.weightUnitOf(exId)
    if (estimated) {
        // Every rep count from the best estimated 1RM, dated by the set it came from; past 10 reps it's approximate (#139).
        val bestSet = sets.maxByOrNull { Records.oneRepMax(it) } ?: return emptyList()
        val best = Records.oneRepMax(bestSet)
        return (1..Records.MAX_REPS).mapNotNull { r ->
            val est = Records.weightFor(best, r)
            if (est <= 0) null
            else RecordLine(res.getString(R.string.ex_record_rm, r), (if (Records.approximate(r)) "≈ " else "") + fmtNum(snap.weight(est, exId), 1), unit, Dates.medium(bestSet.date), superseded = false)
        }
    }
    // As FitNotes lists them: each rep count with its record and date. A record held by a heavier or equal set of more
    // reps is greyed, since that set is the one to beat.
    return listOfNotNull(metricLine) + (1..Records.MAX_REPS).mapNotNull { r ->
        Records.repMax(sets, r)?.let { actual ->
            RecordLine(
                res.getString(R.string.ex_record_rm, r),
                snap.fmtWeight(actual.weightKg, exId) + if (actual.reps > r) " × ${actual.reps}" else "",
                unit,
                Dates.medium(actual.date),
                superseded = actual.reps > r
            )
        }
    }
}

@Composable
internal fun RecordsTab(snap: Snapshot, allSets: List<SetRow>, timeBased: Boolean) {
    val exId = allSets.firstOrNull()?.exerciseId
    val res = LocalContext.current.resources
    // FitNotes's TYPE (#142): actual records, or estimated ones from the best estimated 1RM.
    var estimated by rememberSaveable { mutableStateOf(false) }
    // -1 means the Custom range in customFrom..customTo.
    var periodIdx by rememberSaveable { mutableIntStateOf(Records.Period.ALL.ordinal) }
    var customFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var customTo by rememberSaveable { mutableStateOf<String?>(null) }
    val period = Records.Period.entries.getOrNull(periodIdx)
    val from = customFrom
    val to = customTo
    // Worked out off the main thread and cached (#60); the formula and rep limit are in the key for estimates.
    val formula = Records.chosen()
    val lines = rememberDerived(
        "exerciseRecords", snap.trainingKey, exId, timeBased, estimated, periodIdx, from, to, formula, Records.maxRepsFor(formula)
    ) {
        val sets = if (period != null) Records.inPeriod(allSets, period)
        else if (from != null && to != null) Records.between(allSets, from, to)
        else allSets
        recordLines(res, snap, sets, timeBased, estimated)
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            // FitNotes's TYPE and PERIOD rows (#142), as compact dropdowns.
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!timeBased) {
                    DropdownPill(
                        stringResource(R.string.ex_record_type),
                        listOf(stringResource(R.string.ex_records_actual), stringResource(R.string.ex_records_estimated)),
                        if (estimated) 1 else 0
                    ) {
                        estimated = it == 1
                    }
                }
                PeriodDropdown(
                    label = stringResource(R.string.ex_period),
                    options = Records.Period.entries.map { it.text(LocalContext.current.resources) },
                    selected = period?.ordinal ?: -1,
                    custom = if (from != null && to != null) from to to else null,
                    onSelect = { periodIdx = it },
                    onCustom = { f, t -> customFrom = f; customTo = t; periodIdx = -1 }
                )
            }
            GoldHairline()
        }
        when {
            lines == null -> item {
                Text(stringResource(R.string.ex_working), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            lines.isEmpty() -> item {
                Text(
                    stringResource(R.string.ex_records_none), Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> items(lines) { l -> RecordRow(l.label, l.value, l.unit, l.date, l.superseded) }
        }
    }
}

/**
 * One record as FitNotes lists it (#142): the rep count on the left, then the value with its unit and the date under
 * it. A [superseded] record (held by a set of more reps) is greyed.
 */
@Composable
private fun RecordRow(label: String, value: String, unit: String, date: String, superseded: Boolean) {
    val tint = if (superseded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, color = tint, maxLines = 1)
                if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.bodyMedium, color = tint)
            }
            Text(date, style = MaterialTheme.typography.bodyMedium, color = tint)
        }
    }
}
