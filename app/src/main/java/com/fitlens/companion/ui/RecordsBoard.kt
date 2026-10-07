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
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
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
import com.fitlens.companion.ui.design.DropdownPill

private const val MAX_REPS = 15
private val LABEL_W = 56.dp
private val CELL_W = 96.dp
private val CELL_H = 40.dp

/** One cell: the weight in kg, the set behind it, and whether that set had exactly this many reps. */
private data class RecordCell(val kg: Double, val set: SetRow, val direct: Boolean)

/** One column: an exercise and its 1RM to 15RM cells (null where there's no record). */
private class RecordColumn(val exercise: Exercise, val cells: List<RecordCell?>, val lastDate: String)

private enum class BoardSort(@StringRes val label: Int) { Category(R.string.rb_sort_category), Name(R.string.rb_sort_name), Recent(R.string.rb_sort_recent) }

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

    // The 1RM formula and rep limit are in the key too, so a Settings change isn't hidden by the cache (#60).
    val formula = Records.chosen()
    val columns = rememberDerived(
        "recordsBoard", snap.trainingKey, estimated, sort, categoryId, chosen, formula, Records.maxRepsFor(formula)
    ) {
        buildColumns(snap, estimated, sort, categoryId, chosen)
    }

    Column(Modifier.fillMaxSize()) {
        // One compact row (#115): actual or estimated, which exercises, and their order.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DropdownPill(
                label = stringResource(R.string.ex_tab_records),
                options = listOf(stringResource(R.string.rb_actual), stringResource(R.string.rb_estimated)),
                selected = if (estimated) 1 else 0
            ) { estimated = it == 1; selected = null }
            DropdownPill(
                label = stringResource(R.string.an_tab_exercises),
                options = listOf(
                    stringResource(R.string.lib_all_exercises),
                    categoryId?.let { snap.categories[it]?.name } ?: stringResource(R.string.rb_a_category),
                    if (chosen.isEmpty()) stringResource(R.string.rb_choose_exercises) else pluralStringResource(R.plurals.lib_exercises, chosen.size, chosen.size)
                ),
                selected = when {
                    categoryId != null -> 1
                    chosen.isNotEmpty() -> 2
                    else -> 0
                }
            ) {
                when (it) {
                    0 -> { categoryId = null; chosen = emptySet() }
                    1 -> picking = "category"
                    else -> picking = "exercises"
                }
            }
            DropdownPill(
                label = stringResource(R.string.rb_sort),
                options = BoardSort.entries.map { stringResource(it.label) },
                selected = sortIdx
            ) { sortIdx = it }
        }

        val cols = columns
        when {
            cols == null -> AnalysisNote(stringResource(R.string.ex_working))
            cols.isEmpty() -> EmptyState(stringResource(R.string.rb_empty_title), stringResource(R.string.rb_empty_body))
            else -> {
                // The selected cell's set, with a way to its workout.
                val sel = selected?.let { (exId, r) -> cols.firstOrNull { it.exercise.id == exId }?.let { c -> Triple(c, r, c.cells[r - 1]) } }
                val c = sel?.third
                if (sel != null && c != null) {
                    val (col, r, _) = sel
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(
                                R.string.rb_selected, col.exercise.name, r, cellText(snap, c),
                                "${snap.fmtWeight(c.set.weightKg)} ${snap.weightUnit}", c.set.reps, Dates.medium(c.set.date)
                            ),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { nav.push(Screen.Day(c.set.date.take(10))) }) { Text(stringResource(R.string.rb_open_workout)) }
                    }
                }
                RecordGrid(snap, cols, estimated, selected) { selected = it }
            }
        }
    }

    when (picking) {
        "category" -> {
            val items = remember(snap.libraryKey, snap.setsKey) {
                val used = snap.setsByExercise.keys.mapNotNull { snap.exercises[it]?.categoryId }.toSet()
                snap.categoriesSorted.filter { it.id in used }.map { PickerItem(it.id, it.name) }
            }
            SearchablePicker(
                title = stringResource(R.string.lib_category),
                items = items,
                onDismiss = { picking = null },
                onPick = { ids ->
                    ids.firstOrNull()?.let { categoryId = it; chosen = emptySet() }
                    picking = null
                },
                searchLabel = stringResource(R.string.an_search_categories)
            )
        }
        "exercises" -> {
            val items = remember(snap.libraryKey, snap.setsKey) {
                snap.exercisesSorted
                    .filter { e -> snap.setsByExercise[e.id].orEmpty().any { it.weightKg > 0 && it.reps > 0 } }
                    .map { PickerItem(it.id, it.name, section = snap.categories[it.categoryId]?.name) }
            }
            SearchablePicker(
                title = stringResource(R.string.rb_exercises_to_compare),
                items = items,
                onDismiss = { picking = null },
                onPick = { ids ->
                    chosen = ids.toSet()
                    categoryId = null
                    picking = null
                },
                multiSelect = true,
                searchLabel = stringResource(R.string.ex_search_exercises)
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
                        stringResource(R.string.ex_record_rm, r),
                        Modifier.width(LABEL_W).padding(start = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GridRow(snap, cols, r, estimated, selected, hScroll, onSelect)
                }
            }
            AnalysisNote(stringResource(if (estimated) R.string.rb_note_estimated else R.string.rb_note_actual))
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
    val res = LocalContext.current.resources
    Row(Modifier.horizontalScroll(hScroll)) {
        cols.forEach { c ->
            val cell = c.cells[r - 1]
            val isSel = selected == (c.exercise.id to r)
            val description = when {
                cell == null -> res.getString(R.string.rb_cd_none, c.exercise.name, r)
                estimated -> res.getString(R.string.rb_cd_estimated, c.exercise.name, r, spokenWeight(res, snap, cell.kg))
                cell.direct -> res.getString(R.string.rb_cd_direct, c.exercise.name, r, spokenWeight(res, snap, cell.kg), Dates.medium(cell.set.date))
                else -> res.getString(R.string.rb_cd_carried, c.exercise.name, r, spokenWeight(res, snap, cell.kg), cell.set.reps)
            }
            Box(
                Modifier
                    .width(CELL_W)
                    .height(CELL_H)
                    .then(if (isSel) Modifier.background(Brand.GoldDusk) else Modifier)
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

private fun spokenWeight(res: Resources, snap: Snapshot, kg: Double): String =
    res.getString(if (snap.weightUnit == "lbs") R.string.rb_pounds else R.string.rb_kilograms, snap.fmtWeight(kg))

private fun buildColumns(
    snap: Snapshot,
    estimated: Boolean,
    sort: BoardSort,
    categoryId: Long?,
    chosen: Set<Long>
): List<RecordColumn> {
    val cols = snap.statSetsByExercise.mapNotNull { (id, all) ->
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
