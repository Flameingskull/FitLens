package com.fitlens.companion.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Exercise
import com.fitlens.companion.data.Records
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.ui.design.PickerItem
import com.fitlens.companion.ui.design.SearchablePicker
import com.fitlens.companion.ui.design.SegmentedSwitch

private const val MAX_REPS = 15
private val LABEL_W = 56.dp
private val CELL_W = 96.dp
private val CELL_H = 40.dp

/** One cell: the weight in kg, the set behind it, and whether that set had exactly this many reps. */
private data class RecordCell(val kg: Double, val set: SetRow, val direct: Boolean)

/** One column: an exercise and its 1RM to 15RM cells (null where there's no record). */
private class RecordColumn(val exercise: Exercise, val cells: List<RecordCell?>, val lastDate: String)

private enum class BoardSort(val label: String) { Category("Category order"), Name("Name"), Recent("Recently trained") }

/**
 * The records board (#54): 1RM to 15RM for many exercises side by side, exercises as columns. A cell set by a set of
 * exactly that many reps is bright; one carried over from a heavier lift at more reps is dimmed and marked with an
 * arrow, so the difference never rests on colour alone. The first column and the header row stay put while the
 * grid scrolls. Uses the same superseding rule and estimator as each exercise's Records tab ([Records]).
 */
@Composable
fun RecordsBoard(snap: Snapshot, nav: Nav) {
    var estimated by rememberSaveable { mutableStateOf(false) }
    var sortIdx by rememberSaveable { mutableIntStateOf(0) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var chosen by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var picking by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    val sort = BoardSort.entries[sortIdx]

    val columns = rememberChartData(snap, estimated, sort, categoryId, chosen) {
        buildColumns(snap, estimated, sort, categoryId, chosen)
    }

    Column(Modifier.fillMaxSize()) {
        SegmentedSwitch(
            options = listOf("Actual", "Estimated"),
            selected = if (estimated) 1 else 0,
            onSelect = { estimated = it == 1; selected = null },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = categoryId == null && chosen.isEmpty(),
                onClick = { categoryId = null; chosen = emptySet() },
                label = { Text("All") }
            )
            FilterChip(
                selected = categoryId != null,
                onClick = { picking = "category" },
                label = { Text(categoryId?.let { snap.categories[it]?.name } ?: "Category…") }
            )
            FilterChip(
                selected = chosen.isNotEmpty(),
                onClick = { picking = "exercises" },
                label = { Text(if (chosen.isEmpty()) "Choose exercises…" else "${chosen.size} exercises") }
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BoardSort.entries.forEachIndexed { i, s ->
                FilterChip(selected = sortIdx == i, onClick = { sortIdx = i }, label = { Text(s.label) })
            }
        }

        val cols = columns
        when {
            cols == null -> AnalysisNote("Working it out…")
            cols.isEmpty() -> EmptyState(
                "No records yet",
                "Records come from sets with both a weight and reps. Try another filter, or log a few sets."
            )
            else -> {
                // The selected cell's set, with a way to its workout.
                val sel = selected?.let { (exId, r) -> cols.firstOrNull { it.exercise.id == exId }?.let { c -> Triple(c, r, c.cells[r - 1]) } }
                val c = sel?.third
                if (sel != null && c != null) {
                    val (col, r, _) = sel
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${col.exercise.name} · ${r}RM: ${cellText(snap, c)} — " +
                                "${snap.fmtWeight(c.set.weightKg)} ${snap.weightUnit} × ${c.set.reps}, ${Dates.medium(c.set.date)}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { nav.push(Screen.Day(c.set.date.take(10))) }) { Text("Open workout") }
                    }
                }
                RecordGrid(snap, cols, estimated, selected) { selected = it }
            }
        }
    }

    when (picking) {
        "category" -> {
            val items = remember(snap) {
                val used = snap.setsByExercise.keys.mapNotNull { snap.exercises[it]?.categoryId }.toSet()
                snap.categoriesSorted.filter { it.id in used }.map { PickerItem(it.id, it.name) }
            }
            SearchablePicker(
                title = "Category",
                items = items,
                onDismiss = { picking = null },
                onPick = { ids ->
                    ids.firstOrNull()?.let { categoryId = it; chosen = emptySet() }
                    picking = null
                },
                searchLabel = "Search categories"
            )
        }
        "exercises" -> {
            val items = remember(snap) {
                snap.exercisesSorted
                    .filter { e -> snap.setsByExercise[e.id].orEmpty().any { it.weightKg > 0 && it.reps > 0 } }
                    .map { PickerItem(it.id, it.name, section = snap.categories[it.categoryId]?.name) }
            }
            SearchablePicker(
                title = "Exercises to compare",
                items = items,
                onDismiss = { picking = null },
                onPick = { ids ->
                    chosen = ids.toSet()
                    categoryId = null
                    picking = null
                },
                multiSelect = true,
                searchLabel = "Search exercises"
            )
        }
    }
}

@Composable
private fun RecordGrid(
    snap: Snapshot,
    cols: List<RecordColumn>,
    estimated: Boolean,
    selected: Pair<Long, Int>?,
    onSelect: (Pair<Long, Int>) -> Unit
) {
    // One horizontal position shared by the header and every row, so the columns stay aligned as they scroll.
    val hScroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        GoldHairline()
        Row {
            Box(Modifier.width(LABEL_W))
            Row(Modifier.horizontalScroll(hScroll)) {
                cols.forEach { c ->
                    Text(
                        c.exercise.name,
                        Modifier.width(CELL_W).padding(horizontal = 6.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        GoldHairline()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            for (r in 1..MAX_REPS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${r}RM",
                        Modifier.width(LABEL_W).padding(start = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GridRow(snap, cols, r, estimated, selected, hScroll, onSelect)
                }
            }
            AnalysisNote(
                if (estimated) "Estimated from each exercise's best set, with the same formula as its Records tab."
                else "Bright values were set with exactly that many reps. Dimmed values marked ↑ carry over from a " +
                    "heavier lift at more reps."
            )
        }
    }
}

@Composable
private fun GridRow(
    snap: Snapshot,
    cols: List<RecordColumn>,
    r: Int,
    estimated: Boolean,
    selected: Pair<Long, Int>?,
    hScroll: ScrollState,
    onSelect: (Pair<Long, Int>) -> Unit
) {
    Row(Modifier.horizontalScroll(hScroll)) {
        cols.forEach { c ->
            val cell = c.cells[r - 1]
            val isSel = selected == (c.exercise.id to r)
            val description = when {
                cell == null -> "${c.exercise.name}, $r rep max, none"
                estimated -> "${c.exercise.name}, $r rep max, estimated ${spokenWeight(snap, cell.kg)}"
                cell.direct -> "${c.exercise.name}, $r rep max, ${spokenWeight(snap, cell.kg)}, set directly on ${Dates.medium(cell.set.date)}"
                else -> "${c.exercise.name}, $r rep max, ${spokenWeight(snap, cell.kg)}, carried over from ${cell.set.reps} reps"
            }
            Box(
                Modifier
                    .width(CELL_W)
                    .height(CELL_H)
                    .then(if (isSel) Modifier.background(Brand.ImperialPurple) else Modifier)
                    .clickable(enabled = cell != null) { onSelect(c.exercise.id to r) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) {
                if (cell == null) {
                    Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val bright = estimated || cell.direct
                    Text(
                        cellText(snap, cell) + if (bright) "" else " ↑",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (bright) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (bright) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun cellText(snap: Snapshot, c: RecordCell): String = snap.fmtWeight(c.kg)

private fun spokenWeight(snap: Snapshot, kg: Double): String =
    "${snap.fmtWeight(kg)} ${if (snap.weightUnit == "lbs") "pounds" else "kilograms"}"

private fun buildColumns(
    snap: Snapshot,
    estimated: Boolean,
    sort: BoardSort,
    categoryId: Long?,
    chosen: Set<Long>
): List<RecordColumn> {
    val cols = snap.setsByExercise.mapNotNull { (id, all) ->
        val ex = snap.exercises[id] ?: return@mapNotNull null
        if (categoryId != null && ex.categoryId != categoryId) return@mapNotNull null
        if (chosen.isNotEmpty() && id !in chosen) return@mapNotNull null
        val sets = all.filter { it.weightKg > 0 && it.reps > 0 }
        if (sets.isEmpty()) return@mapNotNull null
        val cells = if (estimated) {
            val best = sets.maxBy { Records.oneRepMax(it) }
            val oneRm = Records.oneRepMax(best)
            (1..MAX_REPS).map { r -> Records.weightFor(oneRm, r).takeIf { it > 0 }?.let { RecordCell(it, best, best.reps == r) } }
        } else {
            (1..MAX_REPS).map { r -> Records.repMax(sets, r)?.let { RecordCell(it.weightKg, it, it.reps == r) } }
        }
        RecordColumn(ex, cells, sets.maxOf { it.date.take(10) })
    }
    return when (sort) {
        BoardSort.Name -> cols.sortedBy { it.exercise.name.lowercase() }
        BoardSort.Recent -> cols.sortedByDescending { it.lastDate }
        BoardSort.Category -> cols.sortedWith(
            compareBy<RecordColumn>(
                { snap.categories[it.exercise.categoryId]?.sortOrder ?: 999 },
                { snap.categories[it.exercise.categoryId]?.name ?: "" },
                { it.exercise.name.lowercase() }
            )
        )
    }
}
