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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Records
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.DateRangePickerDialog

/** Estimated one-rep max in kg (see [Records.factor] for the formula). */
fun e1rm(s: SetRow): Double = Records.oneRepMax(s)

private fun isTimeBased(snap: Snapshot, exId: Long, sets: List<SetRow>): Boolean {
    val type = snap.exercises[exId]?.type ?: 0
    return type != 0 && sets.all { it.weightKg == 0.0 && it.reps == 0 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(snap: Snapshot, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    val rows = remember(snap, query) {
        snap.setsByExercise.keys.mapNotNull { snap.exercises[it] }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedWith(compareBy({ snap.categories[it.categoryId]?.sortOrder ?: 99 }, { snap.categories[it.categoryId]?.name ?: "" }, { it.categoryId }, { it.name }))
    }
    Column(Modifier.fillMaxSize()) {
        PlainTopBar("Training") { LibraryAction(nav) }
        if (snap.sets.isEmpty()) {
            EmptyState(
                "No workouts yet",
                "Build your exercise library and log your first set, or import a FitNotes backup from the Sync tab."
            ) {
                Button(onClick = { nav.push(Screen.Library) }) { Text("Open exercise library") }
            }
        } else {
            val workoutDays = snap.setsByDate.size
            Text(
                "$workoutDays workouts · ${snap.sets.size} sets · ${snap.setsByExercise.size} exercises",
                Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Search exercises") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                var lastCat: Long? = null
                rows.forEach { ex ->
                    if (ex.categoryId != lastCat) {
                        lastCat = ex.categoryId
                        val cat = snap.categories[ex.categoryId]
                        item(key = "c${ex.categoryId}") {
                            Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                // categoryColour treats 0 as "no colour chosen"; Color(0) would be fully transparent (#73).
                                Dot(categoryColour(cat?.colour ?: 0), 10.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(cat?.name ?: "Other", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item(key = "x${ex.id}") {
                        val sets = snap.setsByExercise[ex.id] ?: emptyList()
                        val days = sets.map { it.date }.distinct()
                        val best = sets.maxOfOrNull { e1rm(it) } ?: 0.0
                        val sub = buildList {
                            add("${days.size} workouts")
                            add("last ${Dates.medium(days.max())}")
                            if (best > 0) add("est. 1RM ${snap.fmtWeight(best)} ${snap.weightUnit}")
                        }.joinToString(" · ")
                        Column(Modifier.fillMaxWidth().clickable { nav.push(Screen.ExerciseDetail(ex.id)) }.padding(horizontal = 34.dp, vertical = 8.dp)) {
                            Text(ex.name, style = MaterialTheme.typography.bodyLarge)
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private data class GraphType(val label: String, val fn: (List<SetRow>) -> Double, val isWeight: Boolean, val isTime: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(snap: Snapshot, nav: Nav, exId: Long) {
    val ex = snap.exercises[exId]
    val sets = snap.setsByExercise[exId] ?: emptyList()
    val timeBased = isTimeBased(snap, exId, sets)
    val graphTypes = remember(timeBased) {
        if (timeBased) listOf(
            GraphType("Longest set", { l -> l.maxOf { it.durationSec }.toDouble() }, false, true),
            GraphType("Total time", { l -> l.sumOf { it.durationSec }.toDouble() }, false, true),
            GraphType("Distance", { l -> l.sumOf { it.distance } }, false)
        ) else listOf(
            GraphType("Est. 1RM", { l -> l.maxOf { e1rm(it) } }, true),
            GraphType("Max weight", { l -> l.maxOf { it.weightKg } }, true),
            GraphType("Volume", { l -> l.sumOf { it.weightKg * it.reps } }, true),
            GraphType("Total reps", { l -> l.sumOf { it.reps }.toDouble() }, false),
            GraphType("Max reps", { l -> l.maxOf { it.reps }.toDouble() }, false)
        )
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var gIdx by rememberSaveable { mutableIntStateOf(0) }
    var rangeIdx by rememberSaveable { mutableIntStateOf(4) }
    var sel by remember(gIdx, rangeIdx) { mutableStateOf<Int?>(null) }
    val g = graphTypes[gIdx.coerceIn(0, graphTypes.lastIndex)]
    val byDate = remember(sets) { sets.groupBy { it.date }.toSortedMap() }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(ex?.name ?: "Exercise", onBack = { nav.pop() })
        TabRow(selectedTabIndex = tab) {
            listOf("Graph", "History", "Records").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        graphTypes.forEachIndexed { i, t -> FilterChip(selected = gIdx == i, onClick = { gIdx = i }, label = { Text(t.label) }) }
                    }
                    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RANGES.forEachIndexed { i, r -> FilterChip(selected = rangeIdx == i, onClick = { rangeIdx = i }, label = { Text(r.first) }) }
                    }
                }
                item {
                    val daily = byDate.entries.map { (d, l) ->
                        val raw = g.fn(l)
                        ChartPoint(Dates.epochDay(d), if (g.isWeight) snap.weight(raw) else if (g.isTime) raw / 60.0 else raw, d)
                    }.filter { it.y > 0 }
                    val shown = inRange(daily, RANGES[rangeIdx].second) { it.date }
                    val photoDays = remember(snap) { snap.photosByDate.keys.map { Dates.epochDay(it) }.toSet() }
                    LineChart(shown, Modifier.padding(horizontal = 8.dp), photoDays = photoDays, selected = sel, onSelect = { sel = it })
                    val unit = if (g.isWeight) snap.weightUnit else if (g.isTime) "min" else ""
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
            1 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                byDate.entries.reversed().forEach { (d, l) ->
                    item(key = d) {
                        Column(Modifier.fillMaxWidth().clickable { nav.push(Screen.Day(d)) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Dates.long(d), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (snap.photosByDate.containsKey(d)) Dot(LocalChartColors.current.accent)
                            }
                            l.forEachIndexed { i, s ->
                                Row(Modifier.padding(start = 8.dp, top = 2.dp)) {
                                    Text("${i + 1}", Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(describeSet(snap, s.weightKg, s.reps, s.distance, s.durationSec), Modifier.weight(1f))
                                    if (s.isPr) Text("PR", color = LocalChartColors.current.accent, fontWeight = FontWeight.Bold)
                                }
                                if (!s.comment.isNullOrBlank()) Text("“${s.comment}”", Modifier.padding(start = 32.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            else -> RecordsTab(snap, sets, timeBased)
        }
    }
}

@Composable
private fun RecordsTab(snap: Snapshot, allSets: List<SetRow>, timeBased: Boolean) {
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
