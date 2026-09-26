package com.fitlens.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.data.fmtDuration
import kotlinx.coroutines.launch

/** The day's exercises in workout order (by their first set's position, #70). */
fun dayExercises(snap: Snapshot, date: String): List<Long> =
    snap.setsByDate[date].orEmpty().groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.position } }.map { it.key }

/**
 * Moves exercise [exId] on [date] one place up ([by] −1) or down (+1), carrying its sets as a block (#70).
 * Stores the whole day's new order in one write.
 */
fun moveExercise(snap: Snapshot, date: String, exId: Long, by: Int) {
    val order = dayExercises(snap, date).toMutableList()
    val at = order.indexOf(exId)
    val to = at + by
    if (at < 0 || to !in order.indices) return
    order.add(to, order.removeAt(at))
    val sets = snap.setsByDate[date].orEmpty()
    val ids = order.flatMap { ex -> sets.filter { it.exerciseId == ex }.map { it.id } }
    AppScope.scope.launch { Workouts.reorderDay(ids) }
}

/**
 * The workout drawer (#85, #17), FitNotes's training navigation panel: the day's summary and every exercise in
 * workout order with its set count, the current one picked out. Tap an exercise to jump to it, move it up or down,
 * add another, or go back to the day log.
 */
@Composable
fun WorkoutDrawer(
    snap: Snapshot,
    date: String,
    current: Long,
    onOpen: (Long) -> Unit,
    onAddExercise: () -> Unit,
    onDayLog: () -> Unit
) {
    val sets = snap.setsByDate[date].orEmpty()
    val logged = dayExercises(snap, date)
    // The exercise being logged is listed even before its first set.
    val order = if (current in logged) logged else logged + current
    val secs = snap.workoutTimes[date].orEmpty().sumOf { Dates.secondsBetween(it.start, it.end) }
    Column(Modifier.fillMaxHeight().padding(vertical = Spacing.md)) {
        Text(relativeLabel(date), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.lg))
        Text(
            listOfNotNull(
                "${order.size} exercise${if (order.size == 1) "" else "s"}",
                "${sets.size} set${if (sets.size == 1) "" else "s"}",
                if (secs > 0) fmtDuration(secs.toInt()) else null
            ).joinToString("  ·  ").uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
        )
        GoldHairline(Modifier.padding(vertical = Spacing.sm))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(order, key = { _, id -> id }) { i, exId ->
                val name = snap.exercises[exId]?.name ?: "Exercise"
                val count = sets.count { it.exerciseId == exId }
                val isCurrent = exId == current
                val hasSets = count > 0
                val canUp = hasSets && i > 0
                val canDown = hasSets && i < logged.lastIndex
                val actions = listOfNotNull(
                    if (canUp) CustomAccessibilityAction("Move up") { moveExercise(snap, date, exId, -1); true } else null,
                    if (canDown) CustomAccessibilityAction("Move down") { moveExercise(snap, date, exId, 1); true } else null
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.row)
                        .background(if (isCurrent) Brand.ImperialPurple.copy(alpha = 0.45f) else Brand.Onyx)
                        .clickable(onClickLabel = "Open $name") { onOpen(exId) }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$name, $count set${if (count == 1) "" else "s"}" + if (isCurrent) ", current" else ""
                            selected = isCurrent
                            if (actions.isNotEmpty()) customActions = actions
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(4.dp).heightIn(min = Spacing.row).background(categoryColour(snap.categoryOf(exId)?.colour ?: 0)))
                    Column(Modifier.weight(1f).padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (isCurrent) Brand.GoldLight else MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (hasSets) "$count set${if (count == 1) "" else "s"}" else "No sets yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasSets) {
                        IconButton(onClick = { moveExercise(snap, date, exId, -1) }, enabled = canUp) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move $name up")
                        }
                        IconButton(onClick = { moveExercise(snap, date, exId, 1) }, enabled = canDown) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move $name down")
                        }
                    }
                }
            }
        }
        GoldHairline()
        TextButton(onClick = onAddExercise, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row).padding(horizontal = Spacing.sm)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Add exercise", modifier = Modifier.weight(1f))
        }
        TextButton(onClick = onDayLog, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row).padding(horizontal = Spacing.sm)) {
            Text("Back to the day log", modifier = Modifier.weight(1f))
        }
    }
}

private fun relativeLabel(date: String): String = com.fitlens.companion.ui.design.relativeDayLabel(date)
