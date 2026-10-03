package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.FitIcons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.fitlens.companion.data.Dates
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
    if (timeBased) {
        listOf(GRAPH_LONGEST, GRAPH_TOTAL_TIME, GRAPH_DISTANCE) +
            (if (type > ExerciseTypes.TIME && ExerciseTypes.usesWeight(type)) listOf(GRAPH_MAX_WEIGHT) else emptyList()) +
            (if (type > ExerciseTypes.TIME && ExerciseTypes.usesReps(type)) listOf(GRAPH_WORKOUT_REPS) else emptyList()) +
            // FitNotes's cardio graphs (#22), in the exercise's distance unit (#7). Appended, so saved defaults hold.
            (if (ExerciseTypes.usesDistance(type)) listOf(GRAPH_MAX_DISTANCE) else emptyList()) +
            (if (ExerciseTypes.usesDistance(type) && ExerciseTypes.usesDuration(type)) listOf(GRAPH_MAX_SPEED, GRAPH_MAX_PACE) else emptyList())
    } else {
        listOf(
            GRAPH_E1RM, GRAPH_MAX_WEIGHT, GRAPH_WORKOUT_VOLUME, GRAPH_WORKOUT_REPS, GRAPH_MAX_REPS,
            GRAPH_MAX_VOLUME, GRAPH_WEIGHT_FOR_REPS, GRAPH_RECORDS
        )
    }

internal const val GRAPH_E1RM = "Estimated 1RM"
internal const val GRAPH_MAX_WEIGHT = "Max Weight"
internal const val GRAPH_WORKOUT_VOLUME = "Workout Volume"
internal const val GRAPH_WORKOUT_REPS = "Workout Reps"
internal const val GRAPH_MAX_REPS = "Max Reps"
internal const val GRAPH_MAX_VOLUME = "Max Volume"
internal const val GRAPH_WEIGHT_FOR_REPS = "Max Weight for Reps"
internal const val GRAPH_RECORDS = "Personal Records"
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
        BackTopBar(ex?.name ?: "Exercise", onBack = { nav.pop() }) {
            if (tab == 0 && !timeBased) {
                IconButton(onClick = { calculator = true }) { Icon(FitIcons.Calculate, contentDescription = "1RM calculator") }
                // FitNotes's gear beside it opens the Estimated 1RM Settings (#148).
                IconButton(onClick = { e1rmSettings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Estimated 1RM settings") }
            }
            if (tab == 2) IconButton(onClick = { addGoal = true }) { Icon(Icons.Filled.Add, contentDescription = "Add a goal") }
        }
        FitTabRow(titles = listOf("Records", "Stats", "Goals"), selected = tab, onSelect = { tab = it })
        when (tab) {
            0 -> RecordsTab(snap, statSets, timeBased)
            1 -> ExerciseStatsTab(snap, nav, exId, statSets, timeBased)
            else -> GoalsTab(snap, exId, timeBased, addRequested = addGoal, onAddHandled = { addGoal = false }, showAddButton = false)
        }
    }
    if (calculator) OneRepMaxSheet(snap, statSets.maxByOrNull { Records.oneRepMax(it) }) { calculator = false }
    if (e1rmSettings) EstimatedOneRmSettingsSheet { e1rmSettings = false }
}

/** An exercise's graph: type, range and options, full screen, trend and goal line (#82's Graph tab, #50, #96). */
@Composable
fun ExerciseGraphPane(snap: Snapshot, nav: Nav, exId: Long) {
    val ex = snap.exercises[exId]
    val sets = snap.setsByExercise[exId] ?: emptyList()
    val timeBased = isTimeBased(snap, exId, sets)
    val type = ex?.type ?: ExerciseTypes.WEIGHT_REPS
    var repsFor by remember(exId) { mutableIntStateOf(RepsForGraph.get(exId)) }
    val distUnit = snap.distanceUnit(exId)
    val graphTypes = remember(timeBased, type, repsFor, distUnit) {
        val all = listOf(
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
            // Cardio (#22): the farthest set, the fastest set as speed, and the fastest set as pace.
            GraphType(GRAPH_MAX_DISTANCE, { l -> l.maxOf { it.distance } }, false),
            GraphType(GRAPH_MAX_SPEED, { l -> l.maxOf { speedOf(it, distUnit) } }, false),
            GraphType(
                GRAPH_MAX_PACE,
                { l -> l.map { paceSecondsOf(it, distUnit) }.filter { it > 0 }.minOrNull() ?: 0.0 },
                false, isTime = true, lowerIsBetter = true
            )
        ).associateBy { it.label }
        graphLabels(type, timeBased).map { all.getValue(it) }
    }
    // Opens on the exercise's default graph when one is set (#15).
    var gIdx by rememberSaveable { mutableIntStateOf(ex?.defaultGraph?.takeIf { it >= 0 } ?: 0) }
    var rangeIdx by rememberSaveable { mutableIntStateOf(4) }
    var sel by remember(gIdx, rangeIdx) { mutableStateOf<Int?>(null) }
    var showTrend by rememberSaveable { mutableStateOf(false) }
    var fromZero by rememberSaveable { mutableStateOf(false) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    // Set by the ⋮ menu's "Share graph as image" (#22); the graph item below draws and shares what it shows.
    var shareRequested by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val g = graphTypes[gIdx.coerceIn(0, graphTypes.lastIndex)]
    // Line, bar, area or step, remembered for each graph (#137).
    val (kind, setKind) = rememberChartKind("exercise:${g.label}")
    // A goal for this graph can be drawn as a line (#25), in the graph's own unit.
    var showGoal by rememberSaveable { mutableStateOf(true) }
    val goalTarget = goalKindForGraph(g.label)?.let { k ->
        snap.goalsByExercise[exId]?.firstOrNull { it.kind == k }?.let { goalShown(snap, k, it.target, exId) }
    }
    val goalLine = if (showGoal) goalTarget else null
    // Graphs leave out warm-ups unless Settings counts them (#43); History shows every set.
    val statSets = snap.statSetsByExercise[exId] ?: emptyList()
    val statByDate = remember(statSets) { statSets.groupBy { it.date }.toSortedMap() }

    if (statSets.isEmpty()) {
        EmptyState("No graph yet", "Log a set of this exercise and its progress appears here.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            // One compact row (#115): the graph, its rep count where it has one, the range, options and full screen.
            GraphOptionChips(
                rangeIdx, { rangeIdx = it },
                showTrend, { showTrend = !showTrend },
                fromZero, { fromZero = !fromZero },
                extra = if (goalTarget != null) listOf(ToggleOption("Goal line", showGoal) { showGoal = !showGoal }) else emptyList(),
                onShare = { shareRequested = true },
                kind = kind, onKind = setKind,
                leading = {
                    DropdownPill("Graph", graphTypes.map { it.label }, gIdx.coerceIn(0, graphTypes.lastIndex)) { gIdx = it }
                    // "Max weight for reps" (#22): which rep count, 1 to 15, kept for this exercise while the app is open.
                    if (g.label == GRAPH_WEIGHT_FOR_REPS) {
                        DropdownPill("Reps", (1..Records.MAX_REPS).map { "$it reps" }, repsFor - 1) { i ->
                            repsFor = i + 1
                            RepsForGraph.set(exId, i + 1)
                        }
                    }
                },
                trailing = { ExpandGraphButton { fullScreen = true } }
            )
            if (g.label == GRAPH_RECORDS) {
                Text(
                    "Each point is a day you set a personal record, at the best estimated 1RM of your records so far.",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            // Worked out off the main thread, once per snapshot and graph type (#50).
            val daily = rememberChartData(statByDate, g, snap.weightUnitOf(exId)) {
                val raw = g.series?.invoke(statByDate) ?: statByDate.entries.map { (d, l) -> d to g.fn(l) }
                raw.map { (d, v) ->
                    ChartPoint(Dates.epochDay(d), if (g.isWeight) snap.weight(v, exId) else if (g.isTime) v / 60.0 else v, d)
                }.filter { it.y > 0 }
            } ?: emptyList()
            val shown = inRange(daily, RANGES[rangeIdx].second) { it.date }
            val photoDays = remember(snap) { snap.photosByDate.keys.map { Dates.epochDay(it) }.toSet() }
            val unit = when {
                g.isWeight -> snap.weightUnitOf(exId)
                g.label == GRAPH_MAX_PACE -> if (distUnit == DistanceUnits.M) "/100 m" else "/$distUnit"
                g.isTime -> "min"
                g.label == GRAPH_DISTANCE || g.label == GRAPH_MAX_DISTANCE -> distUnit
                g.label == GRAPH_MAX_SPEED -> speedUnit(distUnit)
                else -> ""
            }
            // A value with its unit; pace reads as minutes and seconds, "5:12 /km" (#22).
            fun show(v: Double): String =
                if (g.lowerIsBetter) "${fmtDuration((v * 60).roundToInt())} $unit" else "${fmtNum(v, 1)} $unit".trim()
            val summary = if (shown.isEmpty()) "" else {
                val best = if (g.lowerIsBetter) shown.minOf { it.y } else shown.maxOf { it.y }
                "${g.label}: ${show(shown.first().y)} → ${show(shown.last().y)} (best ${show(best)})"
            }
            LaunchedEffect(shareRequested) {
                if (!shareRequested) return@LaunchedEffect
                shareRequested = false
                if (shown.isEmpty()) { UiEvents.show("Nothing to share in this range"); return@LaunchedEffect }
                val name = ex?.name ?: "Exercise"
                val image = ShareImages.GraphImage(
                    title = name,
                    graph = g.label,
                    range = rangeName(RANGES[rangeIdx].first),
                    points = shown,
                    format = { v -> show(v) },
                    summary = summary,
                    trend = if (showTrend) trendOf(shown) else null,
                    goal = goalLine,
                    kind = kind
                )
                ShareImages.share(ctx, "Creating graph image…", ShareImages.fileName(name, g.label)) { ShareImages.renderGraph(image) }
            }
            FitChart(
                listOf(LineSeries(g.label, shown)),
                Modifier.padding(horizontal = 8.dp),
                kind = kind,
                photoDays = photoDays,
                selected = sel?.let { ChartSelection(0, it) },
                onSelect = { sel = it.index; ChartHints.tapped() },
                unit = unit,
                showTrend = showTrend,
                yFromZero = fromZero,
                goal = goalLine,
                onExpand = { ChartHints.expanded(); fullScreen = true }
            )
            ChartHint()
            if (fullScreen) {
                FullScreenChart(
                    "${ex?.name ?: "Exercise"} · ${g.label}",
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
                        sel?.let { shown.getOrNull(it) }?.let { p ->
                            Text(
                                "${Dates.long(p.date)}: ${show(p.y)}",
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                ) { vp, h, resetZoom ->
                    FitChart(
                        listOf(LineSeries(g.label, shown)),
                        kind = kind,
                        height = h,
                        photoDays = photoDays,
                        selected = sel?.let { ChartSelection(0, it) },
                        onSelect = { sel = it.index },
                        unit = unit,
                        showTrend = showTrend,
                        yFromZero = fromZero,
                        goal = goalLine,
                        viewport = vp,
                        onExpand = resetZoom
                    )
                }
            }
            if (showTrend) trendOf(shown)?.let { tr ->
                Text(
                    "Trend: ${if (tr.perMonth >= 0) "+" else ""}${fmtNum(tr.perMonth, 1)} ${if (g.lowerIsBetter) "min $unit" else unit} per month",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
                    color = deltaColour(tr.perMonth, MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
            val p = sel?.let { shown.getOrNull(it) }
            if (p != null) {
                Text(
                    "${Dates.long(p.date)}: ${show(p.y)}  ·  open day →",
                    Modifier.fillMaxWidth().clickable { nav.push(Screen.Day(p.date)) }.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            } else if (shown.isNotEmpty()) {
                Text(
                    summary,
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge
                )
            }
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
    if (sets.isEmpty()) {
        EmptyState("No history yet", "Every day you log this exercise appears here, newest first.")
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
                            summary = describeSet(snap, s.weightKg, s.reps, s.distance, s.durationSec, s.exerciseId),
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
                            dayTotals(snap, exId, l),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (d.take(10) != Dates.today()) {
                            TextButton(onClick = { copyToToday(d, l.map { it.id }) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Copy to today")
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
private fun dayTotals(snap: Snapshot, exId: Long, sets: List<SetRow>): String {
    val parts = mutableListOf("${sets.size} set${if (sets.size == 1) "" else "s"}")
    val reps = sets.sumOf { it.reps }
    val volume = sets.sumOf { it.weightKg * it.reps }
    val distance = sets.sumOf { it.distance }
    val time = sets.sumOf { it.durationSec }
    if (reps > 0) parts += "$reps reps"
    if (volume > 0) parts += "${fmtNum(snap.weight(volume, exId), 0)} ${snap.weightUnitOf(exId)} volume"
    if (distance > 0) parts += "${fmtNum(distance)} ${snap.distanceUnit(exId)}"
    if (time > 0) parts += fmtDuration(time)
    return parts.joinToString("  ·  ")
}

/** Copies [ids] (one day's sets of an exercise) from [date] to today, and offers Undo (#22). */
private fun copyToToday(date: String, ids: List<Long>) {
    AppScope.scope.launch {
        try {
            val copies = Workouts.copyWorkout(date, Dates.today(), ids)
            UiEvents.show("Copied ${copies.size} set${if (copies.size == 1) "" else "s"} to today", "Undo") {
                AppScope.scope.launch { Workouts.deleteSets(copies) }
            }
        } catch (e: WorkoutDataException) {
            UiEvents.show(e.message ?: "Couldn't copy those sets.")
        }
    }
}

@Composable
internal fun RecordsTab(snap: Snapshot, allSets: List<SetRow>, timeBased: Boolean) {
    // Weights in the exercise's own unit (#7); the sets are all one exercise's.
    val exId = allSets.firstOrNull()?.exerciseId
    // FitNotes's TYPE (#142): actual records, or estimated ones from the best estimated 1RM.
    var estimated by rememberSaveable { mutableStateOf(false) }
    // -1 means the Custom range in customFrom..customTo.
    var periodIdx by rememberSaveable { mutableIntStateOf(Records.Period.ALL.ordinal) }
    var customFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var customTo by rememberSaveable { mutableStateOf<String?>(null) }
    val period = Records.Period.entries.getOrNull(periodIdx)
    val from = customFrom
    val to = customTo
    val sets = remember(allSets, period, from, to) {
        if (period != null) Records.inPeriod(allSets, period)
        else if (from != null && to != null) Records.between(allSets, from, to)
        else allSets
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            // FitNotes's TYPE and PERIOD rows (#142), as compact dropdowns.
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!timeBased) {
                    DropdownPill("Type", listOf("Actual personal records", "Estimated personal records"), if (estimated) 1 else 0) {
                        estimated = it == 1
                    }
                }
                PeriodDropdown(
                    label = "Period",
                    options = Records.Period.entries.map { it.label },
                    selected = period?.ordinal ?: -1,
                    custom = if (from != null && to != null) from to to else null,
                    onSelect = { periodIdx = it },
                    onCustom = { f, t -> customFrom = f; customTo = t; periodIdx = -1 }
                )
            }
            GoldHairline()
        }
        if (sets.isEmpty()) {
            item {
                Text(
                    "No sets in this period.", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@LazyColumn
        }
        if (timeBased) {
            // Timed exercises have no rep maxes: the longest set is their record.
            sets.maxByOrNull { it.durationSec }?.takeIf { it.durationSec > 0 }?.let { longest ->
                item { RecordRow("Longest", fmtDuration(longest.durationSec), "", Dates.medium(longest.date), superseded = false) }
            }
        } else {
            val unit = snap.weightUnitOf(exId)
            if (estimated) {
                // Every rep count from the best estimated 1RM, dated by the set it came from; past 10 reps it's
                // approximate (#139).
                val bestSet = sets.maxByOrNull { Records.oneRepMax(it) }
                val best = bestSet?.let { Records.oneRepMax(it) } ?: 0.0
                items((1..Records.MAX_REPS).toList()) { r ->
                    val est = Records.weightFor(best, r)
                    if (est > 0 && bestSet != null) {
                        RecordRow("${r}RM", (if (Records.approximate(r)) "≈ " else "") + fmtNum(snap.weight(est, exId), 1), unit, Dates.medium(bestSet.date), superseded = false)
                    }
                }
            } else {
                // As FitNotes lists them: each rep count with its record and date. A record held by a heavier or equal
                // set of more reps is greyed, since that set is the one to beat.
                items((1..Records.MAX_REPS).toList()) { r ->
                    Records.repMax(sets, r)?.let { actual ->
                        RecordRow(
                            "${r}RM",
                            snap.fmtWeight(actual.weightKg, exId) + if (actual.reps > r) " × ${actual.reps}" else "",
                            unit,
                            Dates.medium(actual.date),
                            superseded = actual.reps > r
                        )
                    }
                }
            }
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
