package com.fitlens.companion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Analysis
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import com.fitlens.companion.ui.design.DateRangePickerDialog
import com.fitlens.companion.ui.design.SegmentedSwitch
import com.fitlens.companion.ui.design.StatTile
import com.fitlens.companion.ui.design.DropdownPill

/**
 * Breakdown (#52): how training splits by category or exercise for one workout, week, month, year, all time or a
 * custom range. The donut's legend is its accessible version. A slice can be compared with the period before, and
 * opened in the Workouts tab ([onOpen]) or, for an exercise, on its own screen.
 */
@Composable
fun BreakdownTab(snap: Snapshot, nav: Nav, onOpen: (Analysis.Filter) -> Unit) {
    val res = LocalContext.current.resources
    var measureIdx by rememberSaveable { mutableIntStateOf(0) }
    var groupIdx by rememberSaveable { mutableIntStateOf(0) }
    var spanIdx by rememberSaveable { mutableIntStateOf(Analysis.Span.Month.ordinal) }
    var windowIdx by remember { mutableIntStateOf(0) }
    var custom by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pickingCustom by remember { mutableStateOf(false) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    val measure = Analysis.Measure.entries[measureIdx]
    val group = Analysis.GroupBy.entries[groupIdx]
    val span = Analysis.Span.entries[spanIdx]

    // Worked out off the main thread and cached (#60); a photo or body write doesn't redo them.
    val windowList = rememberDerived("breakdownWindows", snap.trainingKey, span) { Analysis.windows(snap, span) }
    val windows = windowList.orEmpty()
    val window: Analysis.DateWindow? = if (span == Analysis.Span.Custom) {
        custom?.let { (a, b) -> Analysis.DateWindow(a, b, "${Dates.medium(a)} – ${Dates.medium(b)}") }
    } else {
        windows.getOrNull(windowIdx.coerceIn(0, (windows.size - 1).coerceAtLeast(0)))
    }
    var sel by remember(measure, group, window) { mutableIntStateOf(0) }

    val sliceResult = rememberDerived("breakdownSlices", snap.trainingKey, measure, group, window) {
        window?.let { Analysis.breakdown(snap, measure, group, it.from, it.to) }.orEmpty()
    }
    val slices = remember(sliceResult, group, res) { sliceResult.orEmpty().map { it.copy(label = it.text(res, group)) } }

    // Only on the first visit: after that the cached result shows straight away.
    val working = (windowList == null && span != Analysis.Span.Custom) || (window != null && sliceResult == null)
    fun shown(v: Double) = if (measure == Analysis.Measure.Volume) snap.weight(v) else v
    fun withUnit(v: Double): String = when (measure) {
        Analysis.Measure.Volume -> "${fmtNum(snap.weight(v), 0)} ${snap.weightUnit}"
        else -> measure.count(res, fmtNum(v, 0), v)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        // One compact row (#115): what's measured, how it's split, and over what span.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // FitNotes's one BREAKDOWN list (#145): each measure by category, then by exercise.
            val measures = Analysis.Measure.entries
            val groups = Analysis.GroupBy.entries
            DropdownPill(
                label = stringResource(R.string.an_tab_breakdown),
                options = groups.flatMap { g ->
                    measures.map { m ->
                        if (m == Analysis.Measure.Volume) res.getString(R.string.an_bd_volume, g.text(res))
                        else res.getString(R.string.an_bd_number, m.text(res), g.text(res))
                    }
                },
                selected = groupIdx * measures.size + measureIdx
            ) { i -> groupIdx = i / measures.size; measureIdx = i % measures.size }
            DropdownPill(
                label = stringResource(R.string.an_span),
                options = Analysis.Span.entries.map { it.text(res) },
                selected = spanIdx
            ) { i ->
                spanIdx = i
                windowIdx = 0
                if (Analysis.Span.entries[i] == Analysis.Span.Custom) pickingCustom = true
            }
        }

        // The period: newest first, with only periods that hold training.
        if (span == Analysis.Span.Custom) {
            TextButton(onClick = { pickingCustom = true }, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(window?.label ?: stringResource(R.string.an_choose_dates))
            }
        } else if (windows.isNotEmpty() && span != Analysis.Span.All) {
            // FitNotes's DATE dropdown (#127): every period that holds training, newest first.
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DropdownPill(
                    label = stringResource(R.string.an_date),
                    options = windows.map { it.text(res, span) },
                    selected = windowIdx.coerceIn(0, windows.lastIndex)
                ) { i -> windowIdx = i }
            }
        } else if (window != null) {
            Text(window.text(res, span), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
        }

        if (working) {
            AnalysisNote(stringResource(R.string.ex_working))
        } else if (window == null || slices.isEmpty()) {
            EmptyState(
                stringResource(R.string.an_bd_empty_title),
                stringResource(if (span == Analysis.Span.Custom && custom == null) R.string.an_bd_choose_range else R.string.an_bd_no_training)
            )
        } else {
            val donut = slices.map { DonutSlice(it.label, shown(it.value)) }
            val donutFormat: (Double) -> String = { v ->
                if (measure == Analysis.Measure.Volume) "${fmtNum(v, 0)} ${snap.weightUnit}"
                else measure.count(res, fmtNum(v, 0), v)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.End) {
                ExpandGraphButton { fullScreen = true }
            }
            // FitNotes's up and down arrows beside the donut step through the slices (#127).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    donut,
                    selected = sel,
                    onSelect = { sel = it },
                    modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                    valueFormat = donutFormat,
                    onExpand = { ChartHints.expanded(); fullScreen = true }
                )
                Column(Modifier.padding(end = 4.dp)) {
                    IconButton(onClick = { sel = (sel - 1 + donut.size) % donut.size }, enabled = donut.size > 1) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.an_previous_slice), tint = Brand.Gold)
                    }
                    IconButton(onClick = { sel = (sel + 1) % donut.size }, enabled = donut.size > 1) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.an_next_slice), tint = Brand.Gold)
                    }
                }
            }
            if (fullScreen) {
                FullScreenDonut(
                    res.getString(R.string.an_bd_full, measure.text(res), group.text(res).lowercase(), window.text(res, span)),
                    donut,
                    selected = sel,
                    onSelect = { sel = it },
                    onDismiss = { fullScreen = false },
                    valueFormat = donutFormat
                )
            }

            // The selected slice against the same period before it (a FitLens extra, to spot imbalances).
            val slice = slices.getOrNull(sel)
            if (slice != null && slice.id != null) {
                val prev = Analysis.previousWindow(snap, span, window)
                if (prev != null) {
                    val before = Analysis.setsIn(snap, prev.from, prev.to).filter { s ->
                        if (group == Analysis.GroupBy.Exercise) s.exerciseId == slice.id
                        else snap.exercises[s.exerciseId]?.categoryId == slice.id
                    }
                    val was = Analysis.measure(before, measure)
                    val diff = slice.value - was
                    fun amount(v: Double, signed: Boolean): String {
                        val n = if (measure == Analysis.Measure.Volume) snap.weight(v) else v
                        val num = if (signed) fmtSigned(n, 0) else fmtNum(n, 0)
                        return if (measure == Analysis.Measure.Volume) "$num ${snap.weightUnit}" else measure.count(res, num, abs(n))
                    }
                    AnalysisNote(
                        stringResource(R.string.an_bd_compare, slice.label, amount(slice.value, false), amount(diff, true), span.before(res), amount(was, false))
                    )
                }
                Row(Modifier.padding(horizontal = 4.dp)) {
                    TextButton(onClick = {
                        onOpen(
                            if (group == Analysis.GroupBy.Exercise) Analysis.Filter(exerciseId = slice.id)
                            else Analysis.Filter(categoryId = slice.id)
                        )
                    }) { Text(stringResource(R.string.an_bd_totals_for, slice.label)) }
                    if (group == Analysis.GroupBy.Exercise) {
                        TextButton(onClick = { nav.push(Screen.SetEntry(Dates.today(), slice.id, page = 2)) }) { Text(stringResource(R.string.an_open_exercise)) }
                    }
                }
            }

            // Totals for the whole period, whatever the grouping.
            val sets = remember(snap.trainingKey, window) { Analysis.setsIn(snap, window.from, window.to) }
            SectionTitle(stringResource(R.string.an_this_period))
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.an_metric_workouts), fmtNum(Analysis.measure(sets, Analysis.Measure.Workouts), 0), Modifier.weight(1f))
                StatTile(stringResource(R.string.an_metric_sets), fmtNum(Analysis.measure(sets, Analysis.Measure.Sets), 0), Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.an_metric_reps), fmtNum(Analysis.measure(sets, Analysis.Measure.Reps), 0), Modifier.weight(1f))
                StatTile(stringResource(R.string.an_metric_volume), withUnit(Analysis.measure(sets, Analysis.Measure.Volume)), Modifier.weight(1f))
            }
            if (slices.size >= 8) AnalysisNote(stringResource(R.string.an_bd_other))
            AnalysisNote(stringResource(R.string.an_bd_volume_note))
        }
    }

    if (pickingCustom) {
        DateRangePickerDialog(
            initialFrom = custom?.first,
            initialTo = custom?.second,
            onDismiss = { pickingCustom = false },
            onPicked = { a, b ->
                custom = a to b
                pickingCustom = false
            }
        )
    }
}
