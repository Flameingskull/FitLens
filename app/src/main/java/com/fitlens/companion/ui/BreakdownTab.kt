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

/**
 * Breakdown (#52): how training splits by category or exercise for one workout, week, month, year, all time or a
 * custom range. The donut's legend is its accessible version. A slice can be compared with the period before, and
 * opened in the Workouts tab ([onOpen]) or, for an exercise, on its own screen.
 */
@Composable
fun BreakdownTab(snap: Snapshot, nav: Nav, onOpen: (Analysis.Filter) -> Unit) {
    var measureIdx by rememberSaveable { mutableIntStateOf(0) }
    var groupIdx by rememberSaveable { mutableIntStateOf(0) }
    var spanIdx by rememberSaveable { mutableIntStateOf(Analysis.Span.Month.ordinal) }
    var windowIdx by remember { mutableIntStateOf(0) }
    var custom by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pickingCustom by remember { mutableStateOf(false) }
    val measure = Analysis.Measure.entries[measureIdx]
    val group = Analysis.GroupBy.entries[groupIdx]
    val span = Analysis.Span.entries[spanIdx]

    val windows = remember(snap, span) { Analysis.windows(snap, span) }
    val window: Analysis.DateWindow? = if (span == Analysis.Span.Custom) {
        custom?.let { (a, b) -> Analysis.DateWindow(a, b, "${Dates.medium(a)} – ${Dates.medium(b)}") }
    } else {
        windows.getOrNull(windowIdx.coerceIn(0, (windows.size - 1).coerceAtLeast(0)))
    }
    var sel by remember(measure, group, window) { mutableIntStateOf(0) }

    val slices = remember(snap, measure, group, window) {
        window?.let { Analysis.breakdown(snap, measure, group, it.from, it.to) }.orEmpty()
    }
    fun shown(v: Double) = if (measure == Analysis.Measure.Volume) snap.weight(v) else v
    fun withUnit(v: Double): String = when (measure) {
        Analysis.Measure.Volume -> "${fmtNum(snap.weight(v), 0)} ${snap.weightUnit}"
        else -> "${fmtNum(v, 0)} ${if (v == 1.0) measure.unitOne else measure.unitMany}"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Analysis.Measure.entries.forEachIndexed { i, m ->
                FilterChip(selected = measureIdx == i, onClick = { measureIdx = i }, label = { Text(m.label) })
            }
        }
        SegmentedSwitch(
            options = Analysis.GroupBy.entries.map { "By ${it.label.lowercase()}" },
            selected = groupIdx,
            onSelect = { groupIdx = it },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Analysis.Span.entries.forEachIndexed { i, s ->
                FilterChip(
                    selected = spanIdx == i,
                    onClick = {
                        spanIdx = i
                        windowIdx = 0
                        if (s == Analysis.Span.Custom) pickingCustom = true
                    },
                    label = { Text(s.label) }
                )
            }
        }

        // The period stepper: newest first, with only periods that hold training.
        if (span == Analysis.Span.Custom) {
            TextButton(onClick = { pickingCustom = true }, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(window?.label ?: "Choose dates")
            }
        } else if (windows.isNotEmpty() && span != Analysis.Span.All) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { windowIdx++ }, enabled = windowIdx < windows.lastIndex) { Text("‹ Earlier") }
                Text(
                    window?.label ?: "",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { windowIdx-- }, enabled = windowIdx > 0) { Text("Later ›") }
            }
        } else if (window != null) {
            Text(window.label, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
        }

        if (window == null || slices.isEmpty()) {
            EmptyState(
                "Nothing to break down",
                if (span == Analysis.Span.Custom && custom == null) "Choose a date range to see how your training splits up."
                else "No training in this period."
            )
        } else {
            DonutChart(
                slices.map { DonutSlice(it.label, shown(it.value)) },
                selected = sel,
                onSelect = { sel = it },
                modifier = Modifier.padding(vertical = 8.dp),
                valueFormat = { v ->
                    if (measure == Analysis.Measure.Volume) "${fmtNum(v, 0)} ${snap.weightUnit}"
                    else "${fmtNum(v, 0)} ${if (v == 1.0) measure.unitOne else measure.unitMany}"
                }
            )

            // The selected slice against the same period before it (a FitLens extra, to spot imbalances).
            val slice = slices.getOrNull(sel)
            if (slice != null && slice.id != null) {
                val prev = Analysis.previousWindow(snap, span, window)
                if (prev != null) {
                    val before = Analysis.setsIn(snap, prev.from, prev.to).filter { s ->
                        if (group == Analysis.GroupBy.Exercise) s.exerciseId == slice.id
                        else snap.exercises[s.exerciseId]?.categoryId == slice.id
                    }
                    val diff = slice.value - Analysis.measure(before, measure)
                    val what = if (span == Analysis.Span.Workout) "the workout before" else "the ${span.label.lowercase()} before"
                    val diffText = if (measure == Analysis.Measure.Volume) "${fmtSigned(snap.weight(diff), 0)} ${snap.weightUnit}"
                    else "${fmtSigned(diff, 0)} ${measure.unitMany}"
                    AnalysisNote("${slice.label}: $diffText vs $what.")
                }
                Row(Modifier.padding(horizontal = 4.dp)) {
                    TextButton(onClick = {
                        onOpen(
                            if (group == Analysis.GroupBy.Exercise) Analysis.Filter(exerciseId = slice.id)
                            else Analysis.Filter(categoryId = slice.id)
                        )
                    }) { Text("Totals for ${slice.label}") }
                    if (group == Analysis.GroupBy.Exercise) {
                        TextButton(onClick = { nav.push(Screen.ExerciseDetail(slice.id)) }) { Text("Open exercise") }
                    }
                }
            }

            // Totals for the whole period, whatever the grouping.
            val sets = remember(snap, window) { Analysis.setsIn(snap, window.from, window.to) }
            SectionTitle("This period")
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Workouts", fmtNum(Analysis.measure(sets, Analysis.Measure.Workouts), 0), Modifier.weight(1f))
                StatTile("Sets", fmtNum(Analysis.measure(sets, Analysis.Measure.Sets), 0), Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Reps", fmtNum(Analysis.measure(sets, Analysis.Measure.Reps), 0), Modifier.weight(1f))
                StatTile("Volume", withUnit(Analysis.measure(sets, Analysis.Measure.Volume)), Modifier.weight(1f))
            }
            if (slices.size >= 8) AnalysisNote("The smallest groups are combined as Other.")
            AnalysisNote("Volume counts sets with both a weight and reps.")
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
