package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.ToggleOption
import com.fitlens.companion.ui.design.DropdownPill
import androidx.compose.material3.ExperimentalMaterial3Api
import com.fitlens.companion.ui.design.TopBarAction
import com.fitlens.companion.ui.design.FitTopBar
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Analysis
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import com.fitlens.companion.ui.design.FitTabRow
import com.fitlens.companion.ui.design.PickerItem
import com.fitlens.companion.ui.design.SearchablePicker
import com.fitlens.companion.ui.design.SegmentedSwitch
import java.time.LocalDate
import com.fitlens.companion.ui.design.OptionsMenu
import androidx.compose.foundation.layout.Spacer

/**
 * Analysis (#90), opened from the day log's menu. FitNotes-style navigation (#79) made it its own destination
 * rather than one side of the old Training tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(snap: Snapshot, nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_WORKOUTS) }
    var addingGoal by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = "Analysis",
            onBack = LocalNavBack.current,
            // Goals has + to add a goal for any exercise, as in FitNotes (#90).
            actions = if (tab == TAB_GOALS && snap.sets.isNotEmpty()) {
                listOf(TopBarAction(Icons.Filled.Add, "Add a goal") { addingGoal = true })
            } else emptyList()
        )
        if (snap.sets.isEmpty()) {
            EmptyState("Nothing to analyse yet", "Log a workout, or import a FitNotes backup from Settings, and your training totals, breakdown and records appear here.")
        } else {
            AnalysisHub(snap, nav, tab, { tab = it }, addingGoal) { addingGoal = false }
        }
    }
}

private const val TAB_WORKOUTS = 0
private const val TAB_BREAKDOWN = 1
private const val TAB_EXERCISES = 2
private const val TAB_GOALS = 3
private const val TAB_RECORDS = 4

/**
 * The Analysis hub (#90, the #58 hub), with FitNotes's tabs: Workouts (#51), Breakdown (#52), Exercises (#22's graphs
 * for any exercise), Goals (every exercise goal) and Records (#54). The filter is held here so the Breakdown can open
 * a category or exercise in Workouts.
 */
@Composable
fun AnalysisHub(snap: Snapshot, nav: Nav, tab: Int, onTab: (Int) -> Unit, addingGoal: Boolean, onAddingGoalDone: () -> Unit) {
    var filter by remember { mutableStateOf(Analysis.Filter()) }
    Column(Modifier.fillMaxSize()) {
        FitTabRow(
            titles = listOf("Workouts", "Breakdown", "Exercises", "Goals", "Records"),
            selected = tab,
            onSelect = onTab
        )
        when (tab) {
            TAB_RECORDS -> RecordsBoard(snap, nav)
            TAB_GOALS -> AnalysisGoalsTab(snap, nav, addingGoal, onAddingGoalDone)
            TAB_EXERCISES -> AnalysisExercisesTab(snap, nav)
            TAB_BREAKDOWN -> BreakdownTab(snap, nav) { f ->
                filter = f
                onTab(TAB_WORKOUTS)
            }
            else -> WorkoutsTab(snap, nav, filter) { filter = it }
        }
    }
}

/** The name of what a filter covers, for chips and summaries. */
internal fun filterLabel(snap: Snapshot, f: Analysis.Filter): String = when {
    f.exerciseId != null -> snap.exercises[f.exerciseId]?.name ?: "Exercise"
    f.categoryId != null -> snap.categories[f.categoryId]?.name ?: "Category"
    else -> "All training"
}

/** All training, one category or one exercise, as one compact dropdown (#115) that opens the searchable picker. */
@Composable
internal fun AnalysisFilterChips(snap: Snapshot, filter: Analysis.Filter, onFilter: (Analysis.Filter) -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }
    val current = when {
        filter.exerciseId != null -> 2
        filter.categoryId != null -> 1
        else -> 0
    }
    DropdownPill(
        "Training",
        listOf(
            "All training",
            if (current == 1) filterLabel(snap, filter) else "A category…",
            if (current == 2) filterLabel(snap, filter) else "An exercise…"
        ),
        current
    ) { i ->
        when (i) {
            0 -> onFilter(Analysis.Filter())
            1 -> picking = "category"
            else -> picking = "exercise"
        }
    }
    when (picking) {
        "category" -> {
            val items = remember(snap) {
                val used = snap.setsByExercise.keys.mapNotNull { snap.exercises[it]?.categoryId }.toSet()
                // A category without a chosen colour (0) gets no dot rather than a transparent one (#73).
                snap.categoriesSorted.filter { it.id in used }
                    .map { PickerItem(it.id, it.name, color = if (it.colour == 0) null else Color(it.colour)) }
            }
            SearchablePicker(
                title = "Category",
                items = items,
                onDismiss = { picking = null },
                onPick = { ids ->
                    ids.firstOrNull()?.let { onFilter(Analysis.Filter(categoryId = it)) }
                    picking = null
                },
                searchLabel = "Search categories"
            )
        }
        "exercise" -> {
            val items = remember(snap) {
                snap.exercisesSorted.filter { snap.setsByExercise[it.id].orEmpty().isNotEmpty() }
                    .map { PickerItem(it.id, it.name, section = snap.categories[it.categoryId]?.name) }
            }
            SearchablePicker(
                title = "Exercise",
                items = items,
                onDismiss = { picking = null },
                onPick = { ids ->
                    ids.firstOrNull()?.let { onFilter(Analysis.Filter(exerciseId = it)) }
                    picking = null
                },
                searchLabel = "Search exercises"
            )
        }
    }
}

@Composable
internal fun AnalysisNote(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}

// ---------- Workouts: totals by week, month or year (#51) ----------

@Composable
private fun WorkoutsTab(snap: Snapshot, nav: Nav, filter: Analysis.Filter, onFilter: (Analysis.Filter) -> Unit) {
    var periodIdx by rememberSaveable { mutableIntStateOf(0) }
    var metricIdx by rememberSaveable { mutableIntStateOf(0) }
    var rangeIdx by rememberSaveable { mutableIntStateOf(1) }
    var showTrend by rememberSaveable { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    var showDays by remember { mutableStateOf(false) }
    // Duration as the period's total, or as the average length of its timed workouts (#12).
    var durationAvg by rememberSaveable { mutableStateOf(false) }
    val period = Analysis.Period.entries[periodIdx]
    val metric = Analysis.Metric.entries[metricIdx]
    val days = RANGES[rangeIdx].second
    val from = if (days > 0) LocalDate.now().minusDays(days).format(Dates.ISO) else null
    var sel by remember(period, metric, rangeIdx, filter) { mutableStateOf<Int?>(null) }

    val totals = rememberChartData(snap, metric, period, filter, from) {
        Analysis.totals(snap, metric, period, filter, from)
    }
    val avgDuration = metric == Analysis.Metric.Duration && durationAvg
    // A period's value: its total, or for average duration the total over its timed workouts (#12).
    fun valueOf(t: Analysis.PeriodTotal): Double = if (avgDuration) (if (t.timed > 0) t.value / t.timed else 0.0) else t.value
    // Volume in the display unit, total duration in hours and average duration in minutes; counts as they are.
    fun shown(v: Double): Double = when {
        metric == Analysis.Metric.Volume -> snap.weight(v)
        avgDuration -> v / 60.0
        metric == Analysis.Metric.Duration -> v / 3600.0
        else -> v
    }
    val unit = when {
        metric == Analysis.Metric.Volume -> snap.weightUnit
        avgDuration -> "min"
        metric == Analysis.Metric.Duration -> "h"
        else -> ""
    }
    val fmt: (Double) -> String = if (metric == Analysis.Metric.Duration && !avgDuration) { v -> fmtNum(v, 1) } else { v -> fmtNum(v, 0) }
    fun withUnit(v: Double) = fmt(v) + if (unit.isEmpty()) " ${metric.label.lowercase()}" else " $unit"
    // One point per period at its first day (#116): a line shows progression over time better than bars.
    val points = remember(totals, metric, snap.weightUnit, avgDuration) {
        totals.orEmpty().map { ChartPoint(it.start.toEpochDay(), shown(valueOf(it)), it.start.format(Dates.ISO)) }
    }
    val series = remember(points, metric) { listOf(LineSeries(metric.label, points)) }
    val partial = totals?.lastOrNull()?.current == true

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        // Everything that shapes the graph in two compact rows (#115): what, per what, for which training; then the
        // range, options and full screen.
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DropdownPill("Measure", Analysis.Metric.entries.map { it.label }, metricIdx) { metricIdx = it }
            DropdownPill("Per", Analysis.Period.entries.map { "per ${it.label.lowercase()}" }, periodIdx) { periodIdx = it }
            AnalysisFilterChips(snap, filter, onFilter)
        }
        GraphOptionChips(
            rangeIdx, { rangeIdx = it },
            showTrend, { showTrend = !showTrend },
            extra = if (metric == Analysis.Metric.Duration) {
                listOf(ToggleOption("Average per workout", durationAvg) { durationAvg = !durationAvg })
            } else emptyList(),
            trailing = { ExpandGraphButton { fullScreen = true } }
        )

        when {
            totals == null -> AnalysisNote("Working it out…")
            totals.all { valueOf(it) <= 0 } -> EmptyState(
                "Nothing to show",
                "No ${metric.label.lowercase()} for ${filterLabel(snap, filter).lowercase()} in this range. " +
                    "Try a longer range or another filter."
            )
            else -> {
                LineChart(
                    series,
                    Modifier.padding(horizontal = 8.dp),
                    selected = sel?.let { ChartSelection(0, it) },
                    onSelect = { sel = it.index; showDays = false; ChartHints.tapped() },
                    yFormat = fmt,
                    unit = unit,
                    showTrend = showTrend,
                    yFromZero = true,
                    onExpand = { ChartHints.expanded(); fullScreen = true }
                )
                ChartHint(Modifier.padding(horizontal = 16.dp))
                if (partial) AnalysisNote("The last point is this ${period.name.lowercase()}, still in progress.")

                // The selected period: its dates, value, change and the workouts in it.
                val t = sel?.let { totals.getOrNull(it) }
                if (t != null) {
                    val prev = sel?.let { totals.getOrNull(it - 1) }
                    val soFar = if (t.current) " (so far)" else ""
                    Text(
                        Analysis.longLabel(t, period) + soFar,
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    // The change in the metric's own unit, with the value before, never a bare number (owner, 2026-10-02).
                    val change = prev?.let {
                        val unitWord = if (unit.isEmpty()) " ${metric.label.lowercase()}" else " $unit"
                        " · ${fmtSigned(shown(valueOf(t)) - shown(valueOf(it)), if (metric == Analysis.Metric.Duration && !avgDuration) 1 else 0)}" +
                            "$unitWord vs the ${period.name.lowercase()} before (${withUnit(shown(valueOf(it)))})"
                    } ?: ""
                    val delta = prev?.let { shown(valueOf(t)) - shown(valueOf(it)) } ?: 0.0
                    AnalysisNote(withUnit(shown(valueOf(t))) + change, deltaColour(delta, MaterialTheme.colorScheme.onSurfaceVariant))
                    if (t.days.isNotEmpty()) {
                        TextButton(onClick = { showDays = !showDays }, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text(if (showDays) "Hide workouts" else "Open ${t.days.size} ${if (t.days.size == 1) "workout" else "workouts"}")
                        }
                        if (showDays) t.days.reversed().forEach { d ->
                            Text(
                                Dates.long(d),
                                Modifier.fillMaxWidth().clickable { nav.push(Screen.Day(d)) }.padding(horizontal = 24.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                // Summary: the average over complete periods, and the best period.
                val complete = totals.filter { !it.current }
                val avgOver = complete.ifEmpty { totals }
                val avg = avgOver.sumOf { shown(valueOf(it)) } / avgOver.size
                val best = totals.maxBy { valueOf(it) }
                SectionTitle("Summary")
                AnalysisNote(
                    "Average ${withUnit(avg)} per ${period.name.lowercase()}" +
                        (if (complete.size < totals.size) " (not counting this ${period.name.lowercase()}, still in progress)" else "") +
                        ". Best: ${Analysis.longLabel(best, period)}, ${withUnit(shown(valueOf(best)))}."
                )
                if (showTrend) trendOf(points.mapIndexed { i, p -> ChartPoint(i.toLong(), p.y, p.date) })?.let { tr ->
                    AnalysisNote(
                        "Trend: ${fmtSigned(tr.slope, 1)} ${if (unit.isEmpty()) metric.label.lowercase() else unit} per ${period.name.lowercase()}.",
                        deltaColour(tr.slope, MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                when (metric) {
                    Analysis.Metric.Volume, Analysis.Metric.Reps ->
                        AnalysisNote("Time and distance sets aren't counted in ${metric.label.lowercase()}.")
                    Analysis.Metric.Duration -> {
                        val all = totals.sumOf { it.days.size }
                        val timed = totals.sumOf { it.timed }
                        AnalysisNote("Only workouts with a start and finish time count: $timed of $all here.")
                        DurationPerWorkout(snap, filter, from)
                    }
                    else -> {}
                }
                if (period == Analysis.Period.Week) AnalysisNote("Weeks start on ${Analysis.weekStartName()}, as set in Units & display.")
            }
        }
    }

    if (fullScreen && !totals.isNullOrEmpty()) {
        FullScreenChart(
            "${metric.label} · ${filterLabel(snap, filter)}",
            onDismiss = { fullScreen = false },
            controls = { GraphOptionChips(rangeIdx, { rangeIdx = it }, showTrend, { showTrend = !showTrend }) },
            footer = {
                sel?.let { totals.getOrNull(it) }?.let { t ->
                    Text(
                        "${Analysis.longLabel(t, period)}: ${withUnit(shown(valueOf(t)))}",
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        ) { vp, h, resetZoom ->
            LineChart(
                series,
                height = h,
                selected = sel?.let { ChartSelection(0, it) },
                onSelect = { sel = it.index },
                yFormat = fmt,
                unit = unit,
                showTrend = showTrend,
                yFromZero = true,
                viewport = vp,
                onExpand = resetZoom
            )
        }
    }
}

/**
 * Every timed workout's length in minutes as a line over the range (#12), under Analysis → Workouts → Duration: the
 * per-workout graph FitNotes has, with the usual trend, full screen and tap for details.
 */
@Composable
private fun DurationPerWorkout(snap: Snapshot, filter: Analysis.Filter, from: String?) {
    var showTrend by rememberSaveable { mutableStateOf(false) }
    var fromZero by rememberSaveable { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    var sel by remember(filter, from) { mutableStateOf<Int?>(null) }
    val points = rememberChartData(snap, filter, from) {
        snap.setsByDate.entries
            .filter { (d, sets) -> (from == null || d >= from) && sets.any { filter.matches(snap, it) } }
            .mapNotNull { (d, _) ->
                val secs = Analysis.workoutSeconds(snap, d)
                if (secs > 0) ChartPoint(Dates.epochDay(d), secs / 60.0, d) else null
            }
            .sortedBy { it.x }
    } ?: emptyList()
    val series = listOf(LineSeries("Workout length", points))
    SectionTitle("Each workout")
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        OptionsMenu(
            listOf(
                ToggleOption("Trend", showTrend) { showTrend = !showTrend },
                ToggleOption("From zero", fromZero) { fromZero = !fromZero }
            )
        )
        ExpandGraphButton { fullScreen = true }
    }
    LineChart(
        series,
        Modifier.padding(horizontal = 8.dp),
        selected = sel?.let { ChartSelection(0, it) },
        onSelect = { sel = it.index; ChartHints.tapped() },
        yFormat = { fmtNum(it, 0) },
        unit = "min",
        showTrend = showTrend,
        yFromZero = fromZero,
        onExpand = { ChartHints.expanded(); fullScreen = true }
    )
    val picked = sel?.let { points.getOrNull(it) }
    if (picked != null) AnalysisNote("${Dates.long(picked.date)}: ${fmtDuration((picked.y * 60).toInt())}.")
    else if (points.isNotEmpty()) AnalysisNote("${points.size} timed workouts, ${fmtDuration((points.sumOf { it.y } / points.size * 60).toInt())} on average.")
    if (fullScreen) {
        FullScreenChart(
            "Workout length · ${filterLabel(snap, filter)}",
            onDismiss = { fullScreen = false },
            footer = {
                sel?.let { points.getOrNull(it) }?.let { p ->
                    Text(
                        "${Dates.long(p.date)}: ${fmtDuration((p.y * 60).toInt())}",
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        ) { vp, h, resetZoom ->
            LineChart(
                series,
                height = h,
                selected = sel?.let { ChartSelection(0, it) },
                onSelect = { sel = it.index },
                yFormat = { fmtNum(it, 0) },
                unit = "min",
                showTrend = showTrend,
                yFromZero = fromZero,
                viewport = vp,
                onExpand = resetZoom
            )
        }
    }
}
