package com.fitlens.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import com.fitlens.companion.ui.design.FitIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The day's exercises in workout order (by their first set's position, #70). */
fun dayExercises(snap: Snapshot, date: String): List<Long> =
    snap.setsByDate[date].orEmpty().groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.position } }.map { it.key }

/**
 * The day's exercises as they're shown (#18): workout order, except that a superset's exercises sit together where
 * its first one comes.
 */
fun displayOrder(snap: Snapshot, date: String): List<Long> {
    val sets = snap.setsByDate[date].orEmpty()
    val groupOf = sets.groupBy { it.exerciseId }.mapValues { e -> e.value.maxOf { it.superset } }
    val out = ArrayList<Long>()
    dayExercises(snap, date).forEach { ex ->
        if (ex in out) return@forEach
        val g = groupOf[ex] ?: 0
        if (g == 0) out.add(ex) else dayExercises(snap, date).filter { groupOf[it] == g }.forEach { if (it !in out) out.add(it) }
    }
    return out
}

/** The superset number of [exId] on [date], 0 when none (#18). */
fun supersetOf(snap: Snapshot, date: String, exId: Long): Int =
    snap.setsByDate[date].orEmpty().filter { it.exerciseId == exId }.maxOfOrNull { it.superset } ?: 0

/** The day's supersets as letters in the order they're shown: group number to "A", "B"… (#18). */
fun supersetLetters(snap: Snapshot, date: String): Map<Int, String> =
    displayOrder(snap, date).map { supersetOf(snap, date, it) }.filter { it > 0 }.distinct()
        .mapIndexed { i, g -> g to ('A' + i).toString() }.toMap()

/** The exercises of superset [group] on [date], in the order they're shown. */
fun supersetMembers(snap: Snapshot, date: String, group: Int): List<Long> =
    if (group == 0) emptyList() else displayOrder(snap, date).filter { supersetOf(snap, date, it) == group }

/**
 * [order] (the day's exercises as shown) with the one at [at] moved one place up ([by] −1) or down (+1) (#85). Within
 * a superset it swaps with the neighbouring member. Otherwise its block (its whole superset, or just itself) passes
 * the neighbouring block, so a move never splits a superset or drops an exercise into one.
 */
fun moveInOrder(order: List<Long>, groupOf: (Long) -> Int, at: Int, by: Int): List<Long> {
    val to = at + by
    if (at !in order.indices || to !in order.indices) return order
    val g = groupOf(order[at])
    if (g > 0 && groupOf(order[to]) == g) {
        return order.toMutableList().also { it[at] = order[to]; it[to] = order[at] }
    }
    fun blockAt(i: Int): IntRange {
        val bg = groupOf(order[i])
        if (bg == 0) return i..i
        var start = i
        while (start > 0 && groupOf(order[start - 1]) == bg) start--
        var end = i
        while (end < order.lastIndex && groupOf(order[end + 1]) == bg) end++
        return start..end
    }
    val mine = blockAt(at)
    val other = blockAt(to)
    val first = if (by < 0) other else mine
    val second = if (by < 0) mine else other
    return order.subList(0, first.first) + order.slice(second) + order.slice(first) + order.subList(second.last + 1, order.size)
}

/** Stores [order] (exercises on [date]) as the day's order in one write, each exercise's sets as a block (#70). */
fun saveExerciseOrder(snap: Snapshot, date: String, order: List<Long>): Job {
    val sets = snap.setsByDate[date].orEmpty()
    val ids = order.flatMap { ex -> sets.filter { it.exerciseId == ex }.map { it.id } }
    return AppScope.scope.launch { Workouts.reorderDay(ids) }
}

/** Moves exercise [exId] on [date] one place up ([by] −1) or down (+1) as shown, keeping supersets together. */
fun moveExercise(snap: Snapshot, date: String, exId: Long, by: Int) {
    val order = displayOrder(snap, date)
    val moved = moveInOrder(order, { supersetOf(snap, date, it) }, order.indexOf(exId), by)
    if (moved != order) saveExerciseOrder(snap, date, moved)
}

/**
 * Moves set [setId] one place up ([by] −1) or down (+1) among its exercise's sets on [date] (#70). Returns false when
 * it's already at that end.
 */
fun moveSet(snap: Snapshot, date: String, setId: Long, by: Int): Boolean {
    val day = snap.setsByDate[date].orEmpty().toMutableList()
    val at = day.indexOfFirst { it.id == setId }
    if (at < 0) return false
    val ex = day[at].exerciseId
    val mine = day.indices.filter { day[it].exerciseId == ex }
    val k = mine.indexOf(at) + by
    if (k !in mine.indices) return false
    val other = mine[k]
    val tmp = day[at]; day[at] = day[other]; day[other] = tmp
    val ids = day.map { it.id }
    AppScope.scope.launch { Workouts.reorderDay(ids) }
    return true
}

/**
 * The workout drawer (#85, #17), FitNotes's training navigation panel: the day's summary and every exercise in
 * workout order with its set count, the current one picked out. Tap an exercise to jump to it, drag its handle to
 * reorder (TalkBack: Move up / Move down), add another, or go back to the day log.
 */
@Composable
fun WorkoutDrawer(
    snap: Snapshot,
    date: String,
    current: Long,
    onOpen: (Long) -> Unit,
    onAddExercise: () -> Unit,
    onDayLog: () -> Unit,
    /** Add to superset (#124): group exercises of the day with the current one. */
    onAddToSuperset: () -> Unit = {}
) {
    val sets = snap.setsByDate[date].orEmpty()
    val shown = displayOrder(snap, date)
    // While a handle is dragged the rows follow this local order; it's saved once, when the finger lifts (#85).
    var dragOrder by remember { mutableStateOf<List<Long>?>(null) }
    LaunchedEffect(shown) { dragOrder = null }
    val logged = dragOrder ?: shown
    val groups = remember(sets) { sets.groupBy { it.exerciseId }.mapValues { e -> e.value.maxOf { it.superset } } }
    val groupOf: (Long) -> Int = { groups[it] ?: 0 }
    fun dragStep(exId: Long, by: Int) {
        val cur = dragOrder ?: shown
        dragOrder = moveInOrder(cur, groupOf, cur.indexOf(exId), by)
    }
    fun dragEnd() {
        val final = dragOrder ?: return
        if (final == shown) dragOrder = null
        else saveExerciseOrder(snap, date, final).invokeOnCompletion { dragOrder = null }
    }
    val letters = supersetLetters(snap, date)
    // The exercise being logged is listed even before its first set.
    val order = if (current in logged) logged else logged + current
    val secs = snap.workoutTimes[date].orEmpty().sumOf { Dates.secondsBetween(it.start, it.end) }
    Column(Modifier.fillMaxHeight().padding(vertical = Spacing.md)) {
        // FitNotes's header (#124): how many exercises, and how to reorder them.
        Text(
            "${order.size} EXERCISE${if (order.size == 1) "" else "S"}",
            style = MaterialTheme.typography.titleSmall,
            color = Brand.Gold,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
        Text(
            listOfNotNull(
                "Drag a handle to reorder",
                relativeLabel(date),
                "${sets.size} set${if (sets.size == 1) "" else "s"}",
                if (secs > 0) fmtDuration(secs.toInt()) else null
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
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
                        .background(if (isCurrent) Brand.Gold.copy(alpha = 0.16f) else Brand.Onyx)
                        .clickable(onClickLabel = "Open $name") { onOpen(exId) }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$name, $count set${if (count == 1) "" else "s"}" + if (isCurrent) ", current" else ""
                            selected = isCurrent
                            if (actions.isNotEmpty()) customActions = actions
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val group = supersetOf(snap, date, exId)
                    Box(Modifier.width(4.dp).heightIn(min = Spacing.row).background(if (group > 0) Brand.Gold else categoryColour(snap.categoryOf(exId)?.colour ?: 0)))
                    Column(Modifier.weight(1f).padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (isCurrent) Brand.GoldLight else MaterialTheme.colorScheme.onSurface)
                        Text(
                            (if (!hasSets) "No sets yet"
                            else if (com.fitlens.companion.data.Settings.currentPortable().markComplete)
                                "${sets.count { it.exerciseId == exId && it.done }}/$count sets done"
                            else "$count set${if (count == 1) "" else "s"}") +
                                (letters[group]?.let { "  ·  Superset $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasSets) {
                        // Supersets from the drawer (#18): join the next exercise, or leave the group.
                        val nextEx = logged.getOrNull(logged.indexOf(exId) + 1)
                        com.fitlens.companion.ui.design.OverflowMenu(
                            listOf(
                                com.fitlens.companion.ui.design.MenuAction("Superset with the next exercise", enabled = nextEx != null) {
                                    nextEx?.let { n -> AppScope.scope.launch { Workouts.groupExercises(date, listOf(exId, n)) } }
                                },
                                com.fitlens.companion.ui.design.MenuAction("Remove from superset", enabled = group > 0) {
                                    AppScope.scope.launch { Workouts.ungroupExercise(date, exId) }
                                }
                            ),
                            description = "Superset options for $name"
                        )
                        if (logged.size > 1) DragHandle(name, onStep = { by -> dragStep(exId, by) }, onEnd = { dragEnd() })
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
        TextButton(onClick = onAddToSuperset, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row).padding(horizontal = Spacing.sm)) {
            Icon(FitIcons.Link, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Add to superset", modifier = Modifier.weight(1f))
        }
        TextButton(onClick = onDayLog, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row).padding(horizontal = Spacing.sm)) {
            Icon(Icons.Filled.Home, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Home", modifier = Modifier.weight(1f))
        }
    }
}

/**
 * A 48dp drag handle (#85): dragging it by a row's height calls [onStep] with −1 (up) or +1 (down), and [onEnd] when
 * the finger lifts or the drag is cancelled. TalkBack users move rows with the row's custom actions instead.
 */
@Composable
private fun DragHandle(name: String, onStep: (Int) -> Unit, onEnd: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val end by rememberUpdatedState(onEnd)
    Box(
        Modifier
            .size(Spacing.touch)
            .semantics { contentDescription = "Drag to reorder $name" }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { total = 0f; end() },
                    onDragCancel = { total = 0f; end() }
                ) { change, dragAmount ->
                    change.consume()
                    total += dragAmount
                    val stepPx = Spacing.row.toPx()
                    if (total <= -stepPx) {
                        step(-1)
                        total += stepPx
                    } else if (total >= stepPx) {
                        step(1)
                        total -= stepPx
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun relativeLabel(date: String): String = com.fitlens.companion.ui.design.relativeDayLabel(date)
