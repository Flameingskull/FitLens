package com.fitlens.companion.ui

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

/**
 * Analysis (#90), opened from the day log's menu. FitNotes-style navigation (#79) made it its own destination
 * rather than one side of the old Training tab.
 */
@Composable
fun AnalysisScreen(snap: Snapshot, nav: Nav) {
    Column(Modifier.fillMaxSize()) {
        PlainTopBar("Analysis")
        if (snap.sets.isEmpty()) {
            EmptyState("Nothing to analyse yet", "Log a workout, or import a FitNotes backup from Settings, and your training totals, breakdown and records appear here.")
        } else {
            AnalysisHub(snap, nav)
        }
    }
}

/**
 * The Analysis hub (#90, the #58 hub): Workouts (#51), Breakdown (#52) and Records (#54).
 * The filter is held here so the Breakdown can open a category or exercise in Workouts.
 */
@Composable
fun AnalysisHub(snap: Snapshot, nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(Analysis.Filter()) }
    Column(Modifier.fillMaxSize()) {
        FitTabRow(titles = listOf("Workouts", "Breakdown", "Records"), selected = tab, onSelect = { tab = it })
        when (tab) {
            2 -> RecordsBoard(snap, nav)
            1 -> BreakdownTab(snap, nav) { f ->
                filter = f
                tab = 0
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

/** All training, one category or one exercise, chosen with the shared searchable picker. */
@Composable
internal fun AnalysisFilterChips(snap: Snapshot, filter: Analysis.Filter, onFilter: (Analysis.Filter) -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = filter.categoryId == null && filter.exerciseId == null,
            onClick = { onFilter(Analysis.Filter()) },
            label = { Text("All training") }
        )
        FilterChip(
            selected = filter.categoryId != null,
            onClick = { picking = "category" },
            label = { Text(if (filter.categoryId != null) filterLabel(snap, filter) else "Category…") }
        )
        FilterChip(
            selected = filter.exerciseId != null,
            onClick = { picking = "exercise" },
            label = { Text(if (filter.exerciseId != null) filterLabel(snap, filter) else "Exercise…") }
        )
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
internal fun AnalysisNote(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val bars = remember(totals, metric, snap.weightUnit, avgDuration) {
        totals.orEmpty().map { BarDatum(Analysis.shortLabel(it, period), shown(valueOf(it))) }
    }
    val partial = totals?.lastOrNull()?.current == true

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SegmentedSwitch(
            options = Analysis.Period.entries.map { it.label },
            selected = periodIdx,
            onSelect = { periodIdx = it },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Analysis.Metric.entries.forEachIndexed { i, m ->
                FilterChip(selected = metricIdx == i, onClick = { metricIdx = i }, label = { Text(m.label) })
            }
        }
        if (metric == Analysis.Metric.Duration) {
            SegmentedSwitch(
                options = listOf("Total", "Average per workout"),
                selected = if (durationAvg) 1 else 0,
                onSelect = { durationAvg = it == 1 },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        AnalysisFilterChips(snap, filter, onFilter)
        Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GraphOptionChips(rangeIdx, { rangeIdx = it }, showTrend, { showTrend = !showTrend })
            }
            ExpandGraphButton { fullScreen = true }
        }

        when {
            totals == null -> AnalysisNote("Working it out…")
            totals.all { valueOf(it) <= 0 } -> EmptyState(
                "Nothing to show",
                "No ${metric.label.lowercase()} for ${filterLabel(snap, filter).lowercase()} in this range. " +
                    "Try a longer range or another filter."
            )
            else -> {
                BarChart(
                    bars,
                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    selected = sel,
                    onSelect = { sel = it; showDays = false; ChartHints.tapped() },
                    yFormat = fmt,
                    unit = unit,
                    onExpand = { ChartHints.expanded(); fullScreen = true },
                    showTrend = showTrend,
                    lastIsPartial = partial
                )
                ChartHint(Modifier.padding(horizontal = 16.dp))

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
                    val change = prev?.let {
                        " · ${fmtSigned(shown(valueOf(t)) - shown(valueOf(it)), if (metric == Analysis.Metric.Duration && !avgDuration) 1 else 0)} " +
                            "vs the ${period.name.lowercase()} before"
                    } ?: ""
                    AnalysisNote(withUnit(shown(valueOf(t))) + change)
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
                if (showTrend) trendOf(bars.mapIndexed { i, b -> ChartPoint(i.toLong(), b.value, "") })?.let { tr ->
                    AnalysisNote("Trend: ${fmtSigned(tr.slope, 1)} ${if (unit.isEmpty()) metric.label.lowercase() else unit} per ${period.name.lowercase()}.")
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
            BarChart(
                bars,
                height = h,
                selected = sel,
                onSelect = { sel = it },
                yFormat = fmt,
                unit = unit,
                viewport = vp,
                onExpand = resetZoom,
                showTrend = showTrend,
                lastIsPartial = partial
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
        FilterChip(selected = showTrend, onClick = { showTrend = !showTrend }, label = { Text("Trend") })
        Text(" ")
        FilterChip(selected = fromZero, onClick = { fromZero = !fromZero }, label = { Text("From zero") })
        Text(" ", Modifier.weight(1f))
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
