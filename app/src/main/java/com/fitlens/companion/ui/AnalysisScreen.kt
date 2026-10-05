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
import androidx.compose.runtime.LaunchedEffect
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
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.data.Settings
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
import android.content.res.Resources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R

/**
 * Analysis (#90), opened from the day log's menu. FitNotes-style navigation (#79) made it its own destination
 * rather than one side of the old Training tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(snap: Snapshot, nav: Nav) {
    // Opens on the Overview when graphs are pinned there (#55), otherwise on Workouts, as FitNotes does.
    var tab by rememberSaveable {
        mutableIntStateOf(if (PinnedGraph.decode(Settings.portable.value.pinnedGraphs).isNotEmpty()) TAB_OVERVIEW else TAB_WORKOUTS)
    }
    var addingGoal by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = stringResource(R.string.an_title),
            onBack = LocalNavBack.current,
            // Goals has + to add a goal for any exercise, as in FitNotes (#90).
            actions = if (tab == TAB_GOALS && snap.sets.isNotEmpty()) {
                listOf(TopBarAction(Icons.Filled.Add, stringResource(R.string.ex_add_goal)) { addingGoal = true })
            } else emptyList()
        )
        if (snap.sets.isEmpty()) {
            EmptyState(stringResource(R.string.an_empty_title), stringResource(R.string.an_empty_body))
        } else {
            AnalysisHub(snap, nav, tab, { tab = it }, addingGoal) { addingGoal = false }
        }
    }
}

private const val TAB_OVERVIEW = 0
private const val TAB_WORKOUTS = 1
private const val TAB_BREAKDOWN = 2
private const val TAB_EXERCISES = 3
private const val TAB_GOALS = 4
private const val TAB_RECORDS = 5

/**
 * The Analysis hub (#90, the #58 hub), with FitNotes's tabs: Workouts (#51), Breakdown (#52), Exercises (#22's graphs
 * for any exercise), Goals (every exercise goal) and Records (#54), after FitLens's Overview of pinned graphs (#55).
 * The filter is held here so the Breakdown can open a category or exercise in Workouts.
 */
@Composable
fun AnalysisHub(snap: Snapshot, nav: Nav, tab: Int, onTab: (Int) -> Unit, addingGoal: Boolean, onAddingGoalDone: () -> Unit) {
    var filter by remember { mutableStateOf(Analysis.Filter()) }
    Column(Modifier.fillMaxSize()) {
        FitTabRow(
            titles = listOf(
                stringResource(R.string.an_tab_overview), stringResource(R.string.an_tab_workouts),
                stringResource(R.string.an_tab_breakdown), stringResource(R.string.an_tab_exercises),
                stringResource(R.string.ex_tab_goals), stringResource(R.string.ex_tab_records)
            ),
            selected = tab,
            onSelect = onTab
        )
        when (tab) {
            TAB_RECORDS -> RecordsBoard(snap, nav)
            TAB_GOALS -> AnalysisGoalsTab(snap, nav, addingGoal, onAddingGoalDone)
            TAB_EXERCISES -> AnalysisExercisesTab(snap, nav)
            // A pinned card opens its graph, with its settings, in Exercises.
            TAB_OVERVIEW -> AnalysisOverviewTab(snap) { pin ->
                if (pin.totals) {
                    filter = pin.totalsFilter
                    TotalsChoice.pin = pin
                    onTab(TAB_WORKOUTS)
                } else {
                    openPinnedGraph(pin)
                    onTab(TAB_EXERCISES)
                }
            }
            TAB_BREAKDOWN -> BreakdownTab(snap, nav) { f ->
                filter = f
                onTab(TAB_WORKOUTS)
            }
            else -> WorkoutsTab(snap, nav, filter) { filter = it }
        }
    }
}

/** The name of what a filter covers, for chips and summaries. */
internal fun filterLabel(res: Resources, snap: Snapshot, f: Analysis.Filter): String = when {
    f.exerciseId != null -> snap.exercises[f.exerciseId]?.name ?: res.getString(R.string.ex_fallback)
    f.categoryId != null -> snap.categories[f.categoryId]?.name ?: res.getString(R.string.lib_category)
    else -> res.getString(R.string.an_all_training)
}

/** All training, one category or one exercise, as one compact dropdown (#115) that opens the searchable picker. */
@Composable
internal fun AnalysisFilterChips(snap: Snapshot, filter: Analysis.Filter, onFilter: (Analysis.Filter) -> Unit) {
    val res = LocalContext.current.resources
    var picking by remember { mutableStateOf<String?>(null) }
    val current = when {
        filter.exerciseId != null -> 2
        filter.categoryId != null -> 1
        else -> 0
    }
    DropdownPill(
        stringResource(R.string.an_training),
        // FitNotes's FILTER choices (#145): No Filter, Category, Exercise.
        listOf(
            stringResource(R.string.an_no_filter),
            if (current == 1) filterLabel(res, snap, filter) else stringResource(R.string.lib_category),
            if (current == 2) filterLabel(res, snap, filter) else stringResource(R.string.ex_fallback)
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
                title = stringResource(R.string.lib_category),
                items = items,
                onDismiss = { picking = null },
                onPick = { ids ->
                    ids.firstOrNull()?.let { onFilter(Analysis.Filter(categoryId = it)) }
                    picking = null
                },
                searchLabel = stringResource(R.string.an_search_categories)
            )
        }
        "exercise" -> {
            val items = remember(snap) {
                snap.exercisesSorted.filter { snap.setsByExercise[it.id].orEmpty().isNotEmpty() }
                    .map { PickerItem(it.id, it.name, section = snap.categories[it.categoryId]?.name) }
            }
            SearchablePicker(
                title = stringResource(R.string.ex_fallback),
                items = items,
                onDismiss = { picking = null },
                onPick = { ids ->
                    ids.firstOrNull()?.let { onFilter(Analysis.Filter(exerciseId = it)) }
                    picking = null
                },
                searchLabel = stringResource(R.string.ex_search_exercises)
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

/** A Workouts pin (#55) waiting to be opened in the Workouts tab, used once. */
private object TotalsChoice {
    var pin: PinnedGraph? = null
}

/** A period's value: its total, or for average duration the total over its timed workouts (#12). */
internal fun totalsValue(t: Analysis.PeriodTotal, avgDuration: Boolean): Double =
    if (avgDuration) (if (t.timed > 0) t.value / t.timed else 0.0) else t.value

/** A value as shown: volume in the display unit, total duration in hours and average duration in minutes. */
internal fun totalsShown(snap: Snapshot, metric: Analysis.Metric, avgDuration: Boolean, v: Double): Double = when {
    metric == Analysis.Metric.Volume -> snap.weight(v)
    avgDuration -> v / 60.0
    metric == Analysis.Metric.Duration -> v / 3600.0
    else -> v
}

/** The unit a Workouts graph shows; empty for counts, which read "12 sets". */
internal fun totalsUnit(res: Resources, snap: Snapshot, metric: Analysis.Metric, avgDuration: Boolean): String = when {
    metric == Analysis.Metric.Volume -> snap.weightUnit
    avgDuration -> res.getString(R.string.an_unit_min)
    metric == Analysis.Metric.Duration -> res.getString(R.string.an_unit_h)
    else -> ""
}

/** A shown value with its unit ([totalsUnit], or the count's name): "1,240 kg", "3.5 h", "12 sets". */
internal fun totalsText(res: Resources, metric: Analysis.Metric, avgDuration: Boolean, unit: String, v: Double): String {
    val digits = if (metric == Analysis.Metric.Duration && !avgDuration) 1 else 0
    return res.getString(R.string.an_value_unit, fmtNum(v, digits), unit.ifEmpty { metric.word(res) })
}

/** FitNotes's name for a Workouts graph (#145): "Volume Per Week", "Workout Duration Per Month". */
internal fun totalsGraphName(res: Resources, metric: Analysis.Metric, period: Analysis.Period): String {
    val what = if (metric == Analysis.Metric.Duration) res.getString(R.string.an_workout_duration) else metric.text(res)
    return res.getString(
        when (period) {
            Analysis.Period.Week -> R.string.an_per_week
            Analysis.Period.Month -> R.string.an_per_month
            Analysis.Period.Year -> R.string.an_per_year
        },
        what
    )
}

/** A pinned Workouts graph's metric and period (#55), or null when the pin names ones this build doesn't know. */
internal fun totalsOf(pin: PinnedGraph): Pair<Analysis.Metric, Analysis.Period>? {
    if (!pin.totals) return null
    val m = Analysis.Metric.entries.firstOrNull { it.name == pin.graph.substringBefore('/') } ?: return null
    val p = Analysis.Period.entries.firstOrNull { it.name == pin.graph.substringAfter('/') } ?: return null
    return m to p
}

/** The first day range [rangeIdx] of [RANGES] covers, counted back from today, or null for all time. */
internal fun rangeFrom(rangeIdx: Int): String? {
    val days = RANGES[rangeIdx.coerceIn(RANGES.indices)].second
    return if (days > 0) LocalDate.now().minusDays(days).format(Dates.ISO) else null
}

/** One point per period at its first day (#116), in the shown unit. It goes over the whole history: call it off the main thread. */
internal fun totalsPoints(
    snap: Snapshot, metric: Analysis.Metric, period: Analysis.Period, filter: Analysis.Filter, from: String?, avgDuration: Boolean
): List<ChartPoint> =
    Analysis.totals(snap, metric, period, filter, from).map {
        ChartPoint(it.start.toEpochDay(), totalsShown(snap, metric, avgDuration, totalsValue(it, avgDuration)), it.start.format(Dates.ISO))
    }

@Composable
private fun WorkoutsTab(snap: Snapshot, nav: Nav, filter: Analysis.Filter, onFilter: (Analysis.Filter) -> Unit) {
    val res = LocalContext.current.resources
    // A pinned Workouts graph opened from the Overview (#55) sets the graph, range and option once.
    val opened = remember { TotalsChoice.pin.also { TotalsChoice.pin = null }?.let { p -> totalsOf(p)?.let { p to it } } }
    var periodIdx by rememberSaveable { mutableIntStateOf(opened?.second?.second?.ordinal ?: 0) }
    var metricIdx by rememberSaveable { mutableIntStateOf(opened?.second?.first?.ordinal ?: 0) }
    var rangeIdx by rememberSaveable { mutableIntStateOf(opened?.first?.range ?: 1) }
    var showTrend by rememberSaveable { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    var showDays by remember { mutableStateOf(false) }
    // Duration as the period's total, or as the average length of its timed workouts (#12).
    var durationAvg by rememberSaveable { mutableStateOf(opened?.first?.average ?: false) }
    // A body measurement's average per period over the totals (#56), on its own axis; kept while the screen is open.
    var overlayName by rememberSaveable { mutableStateOf<String?>(null) }
    var pickOverlay by remember { mutableStateOf(false) }
    val period = Analysis.Period.entries[periodIdx]
    val metric = Analysis.Metric.entries[metricIdx]
    val from = rangeFrom(rangeIdx)
    var sel by remember(period, metric, rangeIdx, filter) { mutableStateOf<Int?>(null) }

    val totals = rememberDerived("analysisTotals", snap.trainingKey, metric, period, filter, from) {
        Analysis.totals(snap, metric, period, filter, from)
    }
    val avgDuration = metric == Analysis.Metric.Duration && durationAvg
    fun valueOf(t: Analysis.PeriodTotal): Double = totalsValue(t, avgDuration)
    // Volume in the display unit, total duration in hours and average duration in minutes; counts as they are.
    fun shown(v: Double): Double = totalsShown(snap, metric, avgDuration, v)
    val unit = totalsUnit(res, snap, metric, avgDuration)
    // Pinned to the Analysis overview (#55), one pin per graph and filter, kept in step with its range and option.
    val pins = rememberPins()
    val pinNow = PinnedGraph(
        filter.exerciseId ?: 0L, "${metric.name}/${period.name}", rangeIdx,
        totals = true, categoryId = filter.categoryId ?: 0L, average = avgDuration
    )
    val pinned = pins.any { it.sameGraph(pinNow) }
    LaunchedEffect(pinned, pinNow) {
        if (pinned) updatePins { list -> list.map { if (it.sameGraph(pinNow) && it != pinNow) pinNow else it } }
    }
    val fmt: (Double) -> String = if (metric == Analysis.Metric.Duration && !avgDuration) { v -> fmtNum(v, 1) } else { v -> fmtNum(v, 0) }
    fun withUnit(v: Double) = res.getString(R.string.an_value_unit, fmt(v), unit.ifEmpty { metric.word(res) })
    // One point per period at its first day (#116): a line shows progression over time better than bars.
    val points = remember(totals, metric, snap.weightUnit, avgDuration) {
        totals.orEmpty().map { ChartPoint(it.start.toEpochDay(), shown(valueOf(it)), it.start.format(Dates.ISO)) }
    }
    val series = remember(points, metric) { listOf(LineSeries(metric.text(res), points)) }
    val overlayDef = overlayName?.let { n -> snap.allMeasurements.firstOrNull { it.name == n } }
    val overlay = overlayDef?.let { d ->
        remember(snap.bodyKey, d.name, period, from) {
            val avgs = Analysis.periodAverages(snap.dailySeries(d.name), period, from)
            LineSeries(
                res.getString(R.string.an_overlay_average, d.name, period.word(res)),
                avgs.map { (start, v) -> ChartPoint(start.toEpochDay(), v, start.format(Dates.ISO)) }
            )
        }
    }
    val overlayUnit = overlayDef?.unit.orEmpty()
    /** The overlay's average in the period starting [start], when it has one. */
    fun overlayIn(start: String): String? = overlay?.let { o ->
        o.points.firstOrNull { it.date == start }?.let { q -> res.getString(R.string.an_label_value, o.label, "${fmtNum(q.y, 1)} $overlayUnit".trimEnd()) }
    }
    val partial = totals?.lastOrNull()?.current == true
    // Line, bar, area or step, remembered for each measure (#137).
    val (kind, setKind) = rememberChartKind("analysis:${metric.name}")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        // Everything that shapes the graph in two compact rows (#115): what, per what, for which training; then the
        // range, options and full screen.
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // FitNotes's one list of graphs (#145): every measure per week, then per month, then per year.
            val metrics = Analysis.Metric.entries
            val periods = Analysis.Period.entries
            DropdownPill(
                stringResource(R.string.ex_graph),
                periods.flatMap { p -> metrics.map { m -> totalsGraphName(res, m, p) } },
                periodIdx * metrics.size + metricIdx
            ) { i -> periodIdx = i / metrics.size; metricIdx = i % metrics.size }
            AnalysisFilterChips(snap, filter, onFilter)
        }
        GraphOptionChips(
            rangeIdx, { rangeIdx = it },
            showTrend, { showTrend = !showTrend },
            kind = kind, onKind = setKind,
            extra = if (metric == Analysis.Metric.Duration) {
                listOf(ToggleOption(stringResource(R.string.an_average_per_workout), durationAvg) { durationAvg = !durationAvg })
            } else emptyList(),
            actions = listOf(
                MenuAction(stringResource(if (overlayName == null) R.string.ex_overlay_add else R.string.ex_overlay_change)) { pickOverlay = true }
            ),
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

        when {
            totals == null -> AnalysisNote(stringResource(R.string.ex_working))
            totals.all { valueOf(it) <= 0 } -> EmptyState(
                stringResource(R.string.an_nothing_title),
                stringResource(R.string.an_nothing_body, metric.word(res), filterLabel(res, snap, filter).lowercase())
            )
            else -> {
                FitChart(
                    series,
                    Modifier.padding(horizontal = 8.dp),
                    kind = kind,
                    selected = sel?.let { ChartSelection(0, it) },
                    onSelect = { sel = it.index; showDays = false; ChartHints.tapped() },
                    yFormat = fmt,
                    unit = unit,
                    showTrend = showTrend,
                    trendSkipLast = partial,
                    yFromZero = true,
                    onExpand = { ChartHints.expanded(); fullScreen = true },
                    overlay = overlay,
                    overlayUnit = overlayUnit
                )
                ChartHint(Modifier.padding(horizontal = 16.dp))
                if (partial) AnalysisNote(stringResource(R.string.an_last_partial, period.word(res)))

                // The selected period: its dates, value, change and the workouts in it.
                val t = sel?.let { totals.getOrNull(it) }
                if (t != null) {
                    val prev = sel?.let { totals.getOrNull(it - 1) }
                    Text(
                        if (t.current) stringResource(R.string.an_so_far, Analysis.longLabel(t, period)) else Analysis.longLabel(t, period),
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    // The change in the metric's own unit, with the value before, never a bare number (owner, 2026-10-02).
                    val change = prev?.let {
                        val signed = fmtSigned(shown(valueOf(t)) - shown(valueOf(it)), if (metric == Analysis.Metric.Duration && !avgDuration) 1 else 0)
                        res.getString(
                            R.string.an_change_vs, res.getString(R.string.an_value_unit, signed, unit.ifEmpty { metric.word(res) }),
                            period.word(res), withUnit(shown(valueOf(it)))
                        )
                    } ?: ""
                    val delta = prev?.let { shown(valueOf(t)) - shown(valueOf(it)) } ?: 0.0
                    AnalysisNote(withUnit(shown(valueOf(t))) + change, deltaColour(delta, MaterialTheme.colorScheme.onSurfaceVariant))
                    if (overlay != null) AnalysisNote(overlayIn(t.start.format(Dates.ISO)) ?: stringResource(R.string.an_overlay_none, overlayName.orEmpty(), period.word(res)))
                    if (t.days.isNotEmpty()) {
                        TextButton(onClick = { showDays = !showDays }, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text(if (showDays) stringResource(R.string.an_hide_workouts) else pluralStringResource(R.plurals.an_open_workouts, t.days.size, t.days.size))
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
                SectionTitle(stringResource(R.string.an_summary))
                AnalysisNote(
                    stringResource(
                        if (complete.size < totals.size) R.string.an_summary_partial else R.string.an_summary_line,
                        withUnit(avg), period.word(res), Analysis.longLabel(best, period), withUnit(shown(valueOf(best)))
                    )
                )
                when (metric) {
                    Analysis.Metric.Volume, Analysis.Metric.Reps ->
                        AnalysisNote(stringResource(R.string.an_time_not_counted, metric.word(res)))
                    Analysis.Metric.Duration -> {
                        val all = totals.sumOf { it.days.size }
                        val timed = totals.sumOf { it.timed }
                        AnalysisNote(stringResource(R.string.an_timed_only, timed, all))
                        DurationPerWorkout(snap, nav, filter, from)
                    }
                    else -> {}
                }
                if (period == Analysis.Period.Week) AnalysisNote(stringResource(R.string.an_week_start, Analysis.weekStartName()))
            }
        }
    }

    if (fullScreen && !totals.isNullOrEmpty()) {
        FullScreenChart(
            res.getString(R.string.an_title_dot, metric.text(res), filterLabel(res, snap, filter)),
            onDismiss = { fullScreen = false },
            controls = { GraphOptionChips(rangeIdx, { rangeIdx = it }, showTrend, { showTrend = !showTrend }, kind = kind, onKind = setKind) },
            valueZoom = kind != ChartKind.BAR,
            footer = {
                sel?.let { totals.getOrNull(it) }?.let { t ->
                    Text(
                        res.getString(R.string.an_label_value, Analysis.longLabel(t, period), withUnit(shown(valueOf(t)))),
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
                selected = sel?.let { ChartSelection(0, it) },
                onSelect = { sel = it.index },
                yFormat = fmt,
                unit = unit,
                showTrend = showTrend,
                trendSkipLast = partial,
                yFromZero = true,
                viewport = vp,
                onExpand = resetZoom,
                overlay = overlay,
                overlayUnit = overlayUnit
            )
        }
    }
    if (pickOverlay) {
        BodyOverlaySheet(snap, overlayName, onPick = { overlayName = it; pickOverlay = false }) { pickOverlay = false }
    }
}

/**
 * Every timed workout's length in minutes as a line over the range (#12), under Analysis → Workouts → Duration: the
 * per-workout graph FitNotes has, with the usual trend, full screen and tap for details.
 */
@Composable
private fun DurationPerWorkout(snap: Snapshot, nav: Nav, filter: Analysis.Filter, from: String?) {
    val res = LocalContext.current.resources
    val min = stringResource(R.string.an_unit_min)
    var showTrend by rememberSaveable { mutableStateOf(false) }
    var fromZero by rememberSaveable { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    var sel by remember(filter, from) { mutableStateOf<Int?>(null) }
    val points = rememberDerived("durationPerWorkout", snap.trainingKey, filter, from) {
        snap.setsByDate.entries
            .filter { (d, sets) -> (from == null || d >= from) && sets.any { filter.matches(snap, it) } }
            .mapNotNull { (d, _) ->
                val secs = Analysis.workoutSeconds(snap, d)
                if (secs > 0) ChartPoint(Dates.epochDay(d), secs / 60.0, d) else null
            }
            .sortedBy { it.x }
    } ?: emptyList()
    val series = listOf(LineSeries(stringResource(R.string.an_workout_length), points))
    val (kind, setKind) = rememberChartKind("analysis:workout-length")
    SectionTitle(stringResource(R.string.an_each_workout))
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        // Line, bar, area or step (#137).
        DropdownPill(stringResource(R.string.an_chart_type), ChartKind.entries.map { it.label }, kind.ordinal) { i -> setKind(ChartKind.entries[i]) }
        Spacer(Modifier.weight(1f))
        OptionsMenu(
            listOf(
                ToggleOption(stringResource(R.string.an_trend), showTrend) { showTrend = !showTrend },
                ToggleOption(stringResource(R.string.an_from_zero), fromZero) { fromZero = !fromZero }
            )
        )
        ExpandGraphButton { fullScreen = true }
    }
    FitChart(
        series,
        Modifier.padding(horizontal = 8.dp),
        kind = kind,
        selected = sel?.let { ChartSelection(0, it) },
        onSelect = { sel = it.index; ChartHints.tapped() },
        yFormat = { fmtNum(it, 0) },
        unit = min,
        showTrend = showTrend,
        yFromZero = fromZero,
        onExpand = { ChartHints.expanded(); fullScreen = true }
    )
    val picked = sel?.let { points.getOrNull(it) }
    if (picked != null) {
        AnalysisNote(stringResource(R.string.an_date_duration, Dates.long(picked.date), fmtDuration((picked.y * 60).toInt())))
        NearestPhotoThumb(snap, nav, picked.date, Modifier.padding(horizontal = 16.dp))
    } else if (points.isNotEmpty()) AnalysisNote(pluralStringResource(R.plurals.an_timed_average, points.size, points.size, fmtDuration((points.sumOf { it.y } / points.size * 60).toInt())))
    if (fullScreen) {
        FullScreenChart(
            res.getString(R.string.an_title_dot, res.getString(R.string.an_workout_length), filterLabel(res, snap, filter)),
            onDismiss = { fullScreen = false },
            valueZoom = kind != ChartKind.BAR,
            footer = {
                sel?.let { points.getOrNull(it) }?.let { p ->
                    Text(
                        res.getString(R.string.an_label_value, Dates.long(p.date), fmtDuration((p.y * 60).toInt())),
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
                selected = sel?.let { ChartSelection(0, it) },
                onSelect = { sel = it.index },
                yFormat = { fmtNum(it, 0) },
                unit = min,
                showTrend = showTrend,
                yFromZero = fromZero,
                viewport = vp,
                onExpand = resetZoom
            )
        }
    }
}
