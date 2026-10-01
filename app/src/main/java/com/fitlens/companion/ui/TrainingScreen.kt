package com.fitlens.companion.ui

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
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.DateRangePickerDialog
import com.fitlens.companion.ui.design.FitTabRow
import com.fitlens.companion.ui.design.StepperField
import androidx.compose.ui.text.input.KeyboardType
import com.fitlens.companion.ui.design.SetRow as SetRowView

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
            (if (type > ExerciseTypes.TIME && ExerciseTypes.usesReps(type)) listOf(GRAPH_WORKOUT_REPS) else emptyList())
    } else {
        listOf(
            GRAPH_E1RM, GRAPH_MAX_WEIGHT, GRAPH_WORKOUT_VOLUME, GRAPH_WORKOUT_REPS, GRAPH_MAX_REPS,
            GRAPH_MAX_VOLUME, GRAPH_WEIGHT_FOR_REPS, GRAPH_RECORDS
        )
    }

internal const val GRAPH_E1RM = "Estimated 1RM"
internal const val GRAPH_MAX_WEIGHT = "Max weight"
internal const val GRAPH_WORKOUT_VOLUME = "Workout volume"
internal const val GRAPH_WORKOUT_REPS = "Workout reps"
internal const val GRAPH_MAX_REPS = "Max reps"
internal const val GRAPH_MAX_VOLUME = "Max volume"
internal const val GRAPH_WEIGHT_FOR_REPS = "Max weight for reps"
internal const val GRAPH_RECORDS = "Personal records"
internal const val GRAPH_LONGEST = "Longest set"
internal const val GRAPH_TOTAL_TIME = "Total time"
internal const val GRAPH_DISTANCE = "Distance"

/**
 * One graph: [fn] gives a day's value from its sets, or [series] gives the whole line at once for a graph that
 * depends on earlier days (Personal records).
 */
private data class GraphType(
    val label: String,
    val fn: (List<SetRow>) -> Double,
    val isWeight: Boolean,
    val isTime: Boolean = false,
    val series: ((Map<String, List<SetRow>>) -> List<Pair<String, Double>>)? = null
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
    Column(Modifier.fillMaxSize()) {
        BackTopBar(ex?.name ?: "Exercise", onBack = { nav.pop() }) {
            // The 1RM calculator (#28), starting from the exercise's best estimated set.
            if (!timeBased) TextButton(onClick = { calculator = true }) { Text("1RM") }
        }
        FitTabRow(titles = listOf("Records", "Stats", "Goals"), selected = tab, onSelect = { tab = it })
        when (tab) {
            0 -> RecordsTab(snap, statSets, timeBased)
            1 -> ExerciseStatsTab(snap, nav, exId, statSets, timeBased)
            else -> GoalsTab(snap, exId, timeBased)
        }
    }
    if (calculator) OneRepMaxSheet(snap, statSets.maxByOrNull { Records.oneRepMax(it) }) { calculator = false }
}

/** An exercise's graph: type, range and options, full screen, trend and goal line (#82's Graph tab, #50, #96). */
@Composable
fun ExerciseGraphPane(snap: Snapshot, nav: Nav, exId: Long) {
    val ex = snap.exercises[exId]
    val sets = snap.setsByExercise[exId] ?: emptyList()
    val timeBased = isTimeBased(snap, exId, sets)
    val type = ex?.type ?: ExerciseTypes.WEIGHT_REPS
    var repsFor by remember(exId) { mutableIntStateOf(RepsForGraph.get(exId)) }
    val graphTypes = remember(timeBased, type, repsFor) {
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
            GraphType(GRAPH_RECORDS, { 0.0 }, true, series = { byDate -> Records.recordProgress(byDate) })
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
    val g = graphTypes[gIdx.coerceIn(0, graphTypes.lastIndex)]
    // A goal for this graph can be drawn as a line (#25), in the graph's own unit.
    var showGoal by rememberSaveable { mutableStateOf(true) }
    val goalTarget = goalKindForGraph(g.label)?.let { k ->
        snap.goalsByExercise[exId]?.firstOrNull { it.kind == k }?.let { goalShown(snap, k, it.target) }
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
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                graphTypes.forEachIndexed { i, t -> FilterChip(selected = gIdx == i, onClick = { gIdx = i }, label = { Text(t.label) }) }
            }
            // "Max weight for reps" (#22): which rep count, 1 to 15, kept for this exercise while the app is open.
            if (g.label == GRAPH_WEIGHT_FOR_REPS) {
                StepperField(
                    label = "Reps",
                    value = repsFor.toString(),
                    onValue = { v -> v.trim().toIntOrNull()?.coerceIn(1, Records.MAX_REPS)?.let { repsFor = it; RepsForGraph.set(exId, it) } },
                    onStep = { dir -> (repsFor + dir).coerceIn(1, Records.MAX_REPS).let { repsFor = it; RepsForGraph.set(exId, it) } },
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            if (g.label == GRAPH_RECORDS) {
                Text(
                    "Each point is a day you set a personal record, at the best estimated 1RM of your records so far.",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RANGES.forEachIndexed { i, r -> FilterChip(selected = rangeIdx == i, onClick = { rangeIdx = i }, label = { Text(r.first) }) }
            }
            // Graph options (#50): a least-squares trend, and the y axis from zero.
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = showTrend, onClick = { showTrend = !showTrend }, label = { Text("Trend") })
                FilterChip(selected = fromZero, onClick = { fromZero = !fromZero }, label = { Text("From zero") })
                if (goalTarget != null) FilterChip(selected = showGoal, onClick = { showGoal = !showGoal }, label = { Text("Goal") })
                Spacer(Modifier.weight(1f))
                ExpandGraphButton { fullScreen = true }
            }
        }
        item {
            // Worked out off the main thread, once per snapshot and graph type (#50).
            val daily = rememberChartData(statByDate, g, snap.weightUnit) {
                val raw = g.series?.invoke(statByDate) ?: statByDate.entries.map { (d, l) -> d to g.fn(l) }
                raw.map { (d, v) ->
                    ChartPoint(Dates.epochDay(d), if (g.isWeight) snap.weight(v) else if (g.isTime) v / 60.0 else v, d)
                }.filter { it.y > 0 }
            } ?: emptyList()
            val shown = inRange(daily, RANGES[rangeIdx].second) { it.date }
            val photoDays = remember(snap) { snap.photosByDate.keys.map { Dates.epochDay(it) }.toSet() }
            val unit = if (g.isWeight) snap.weightUnit else if (g.isTime) "min" else ""
            LineChart(
                listOf(LineSeries(g.label, shown)),
                Modifier.padding(horizontal = 8.dp),
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
                            fromZero, { fromZero = !fromZero }
                        )
                    },
                    footer = {
                        sel?.let { shown.getOrNull(it) }?.let { p ->
                            Text(
                                "${Dates.long(p.date)}: ${fmtNum(p.y, 1)} $unit",
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                ) { vp, h, resetZoom ->
                    LineChart(
                        listOf(LineSeries(g.label, shown)),
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
                    "Trend: ${if (tr.perMonth >= 0) "+" else ""}${fmtNum(tr.perMonth, 1)} $unit per month",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val p = sel?.let { shown.getOrNull(it) }
            if (p != null) {
                Text(
                    "${Dates.long(p.date)}: ${fmtNum(p.y, 1)} $unit  ·  open day →",
                    Modifier.fillMaxWidth().clickable { nav.push(Screen.Day(p.date)) }.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            } else if (shown.isNotEmpty()) {
                Text(
                    "${g.label}: ${fmtNum(shown.first().y, 1)} → ${fmtNum(shown.last().y, 1)} $unit (best ${fmtNum(shown.maxOf { it.y }, 1)})",
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Dates.long(d), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (snap.photosByDate.containsKey(d)) Dot(LocalChartColors.current.accent)
                    }
                    // The day's totals (#22): volume and reps for strength, distance and time for cardio.
                    Text(
                        dayTotals(snap, l).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    l.forEachIndexed { i, s ->
                        val marks = setMarks(s)
                        SetRowView(
                            index = i + 1,
                            summary = describeSet(snap, s.weightKg, s.reps, s.distance, s.durationSec),
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
                    // Repeat this day's sets today (#22), with Undo.
                    val today = Dates.today()
                    if (d.take(10) != today) {
                        TextButton(onClick = { copyToToday(d, l.map { it.id }) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Copy to today")
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** One day's totals for an exercise's history: sets, then reps and volume, or distance and time (#22). */
private fun dayTotals(snap: Snapshot, sets: List<SetRow>): String {
    val parts = mutableListOf("${sets.size} set${if (sets.size == 1) "" else "s"}")
    val reps = sets.sumOf { it.reps }
    val volume = sets.sumOf { it.weightKg * it.reps }
    val distance = sets.sumOf { it.distance }
    val time = sets.sumOf { it.durationSec }
    if (reps > 0) parts += "$reps reps"
    if (volume > 0) parts += "${fmtNum(snap.weight(volume), 0)} ${snap.weightUnit} volume"
    if (distance > 0) parts += "${fmtNum(distance)} distance"
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
    // -1 means the Custom range in customFrom..customTo.
    var periodIdx by rememberSaveable { mutableIntStateOf(Records.Period.ALL.ordinal) }
    var customFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var customTo by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val period = Records.Period.entries.getOrNull(periodIdx)
    val from = customFrom
    val to = customTo
    val sets = remember(allSets, period, from, to) {
        if (period != null) Records.inPeriod(allSets, period)
        else if (from != null && to != null) Records.between(allSets, from, to)
        else allSets
    }
    if (picking) {
        DateRangePickerDialog(
            initialFrom = customFrom,
            initialTo = customTo,
            onDismiss = { picking = false },
            onPicked = { f, t -> customFrom = f; customTo = t; periodIdx = -1 }
        )
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Records.Period.entries.forEach { p ->
                    FilterChip(selected = period == p, onClick = { periodIdx = p.ordinal }, label = { Text(p.label) })
                }
                val customLabel = if (period == null && from != null && to != null) "${Dates.short(from)} – ${Dates.short(to)}" else "Custom"
                FilterChip(selected = period == null, onClick = { picking = true }, label = { Text(customLabel) })
            }
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
        item {
            val days = sets.map { it.date }.distinct().sorted()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    LabelValue("Workouts", "${days.size}", Modifier.weight(1f))
                    LabelValue("Sets", "${sets.size}", Modifier.weight(1f))
                    LabelValue("Reps", "${sets.sumOf { it.reps }}", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    LabelValue("First", days.firstOrNull()?.let { Dates.medium(it) } ?: "—", Modifier.weight(1f))
                    LabelValue("Last", days.lastOrNull()?.let { Dates.medium(it) } ?: "—", Modifier.weight(1f))
                    if (timeBased) LabelValue("Longest", fmtDuration(sets.maxOfOrNull { it.durationSec } ?: 0), Modifier.weight(1f))
                    else LabelValue("Volume", "${fmtNum(snap.weight(sets.sumOf { it.weightKg * it.reps }), 0)} ${snap.weightUnit}", Modifier.weight(1f))
                }
            }
            HorizontalDivider()
        }
        if (!timeBased) {
            val best = sets.maxOfOrNull { Records.oneRepMax(it) } ?: 0.0
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Reps", Modifier.weight(0.6f), style = MaterialTheme.typography.labelLarge)
                    Text("Actual best", Modifier.weight(1.4f), style = MaterialTheme.typography.labelLarge)
                    Text("Estimated", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                }
            }
            items((1..Records.MAX_REPS).toList()) { r ->
                val actual = Records.repMax(sets, r)
                val est = Records.weightFor(best, r)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("${r}RM", Modifier.weight(0.6f))
                    Text(
                        actual?.let {
                            // A record set by a higher-rep set shows its reps, e.g. "100 kg × 5".
                            val reps = if (it.reps > r) " × ${it.reps}" else ""
                            "${snap.fmtWeight(it.weightKg)} ${snap.weightUnit}$reps · ${Dates.short(it.date)}"
                        } ?: "—",
                        Modifier.weight(1.4f)
                    )
                    Text(if (est > 0) "${fmtNum(snap.weight(est), 1)} ${snap.weightUnit}" else "—", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
