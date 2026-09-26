@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.relativeDayLabel
import kotlinx.coroutines.launch

/**
 * Editing a whole workout (#10), as FitLens bottom sheets (#84): its comment, copying it to another day, copying a
 * previous one into it, moving it and deleting it. Each keeps FitNotes's steps, and every change can be undone from
 * the snackbar. Everything goes through [Workouts], which keeps the FitNotes merge rules intact.
 */

private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

/** Adds, edits or removes the comment on a whole workout. */
@Composable
fun WorkoutCommentSheet(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val existing = remember(snap, date) { snap.workoutComments[date]?.joinToString("\n\n").orEmpty() }
    var text by remember(date) { mutableStateOf(existing) }

    FitSheet(
        title = "Workout comment",
        onDismiss = onDismiss,
        confirmLabel = "Save comment",
        confirmEnabled = text.trim() != existing.trim(),
        onConfirm = {
            val value = text
            onDismiss()
            AppScope.scope.launch {
                try {
                    Workouts.setWorkoutComment(date, value)
                } catch (e: WorkoutDataException) {
                    UiEvents.show(e.message ?: "That comment couldn't be saved.")
                }
            }
        }
    ) {
        Text(
            Dates.long(date).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("How did it go?") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
        )
        if ((snap.workoutComments[date]?.size ?: 0) > 1) {
            Text(
                "This day had more than one comment from FitNotes. Saving replaces them with the single comment above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (existing.isNotBlank()) {
            TextButton(
                onClick = {
                    onDismiss()
                    AppScope.scope.launch {
                        Workouts.setWorkoutComment(date, null)
                        UiEvents.show("Comment deleted", "Undo") {
                            AppScope.scope.launch { Workouts.setWorkoutComment(date, existing) }
                        }
                    }
                },
                modifier = Modifier.heightIn(min = Spacing.touch)
            ) { Text("Delete comment", color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Confirms deleting everything logged on a day, and offers an undo afterwards. */
@Composable
fun DeleteWorkoutSheet(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val sets = remember(snap, date) { snap.setsByDate[date].orEmpty() }
    val comment = remember(snap, date) { snap.workoutComments[date]?.joinToString("\n\n") }
    // Every time row, not just the first: a day can carry more than one and undo has to put them all back (#69).
    val times = remember(snap, date) { snap.workoutTimes[date].orEmpty() }
    val exercises = remember(sets) { sets.map { it.exerciseId }.distinct().size }

    ConfirmSheet(
        title = "Delete this workout?",
        message = "${plural(sets.size, "set")} across ${plural(exercises, "exercise")}" +
            (if (comment.isNullOrBlank()) "" else ", and the workout comment") +
            (if (times.isEmpty()) "" else ", and the start and finish times") +
            ", will be removed from ${Dates.medium(date)}. Photos and measurements on this day are kept.",
        confirmLabel = "Delete workout",
        onDismiss = onDismiss
    ) {
        AppScope.scope.launch {
            Workouts.deleteWorkout(date)
            UiEvents.show("Workout deleted", "Undo") {
                AppScope.scope.launch {
                    try {
                        Workouts.addSets(sets)
                        if (!comment.isNullOrBlank()) Workouts.setWorkoutComment(date, comment)
                        if (times.isNotEmpty()) Workouts.setWorkoutTimes(date, times)
                    } catch (e: Exception) {
                        UiEvents.show("Couldn't undo that: ${e.message}")
                    }
                }
            }
        }
    }
}

/**
 * Copies this workout to another day, or moves it there (#84). Step one chooses the day; step two reviews what goes.
 * A copy can leave exercises out; a move takes the whole workout, with its comment and times. Both merge into
 * whatever is already on that day, and both can be undone.
 */
@Composable
fun CopyOrMoveWorkoutSheet(snap: Snapshot, date: String, move: Boolean, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val exercises = remember(snap, date) { snap.setsByDate[date].orEmpty().map { it.exerciseId }.distinct() }
    var checked by remember(date) { mutableStateOf(exercises.toSet()) }
    val ids = snap.setsByDate[date].orEmpty().filter { move || it.exerciseId in checked }.map { it.id }
    val chosen = target

    FitSheet(
        title = if (move) "Move workout" else "Copy workout",
        onDismiss = onDismiss,
        confirmLabel = when {
            move -> "Move workout"
            else -> "Copy ${plural(ids.size, "set")}"
        },
        confirmEnabled = chosen != null && chosen != date && (move || ids.isNotEmpty()),
        onConfirm = {
            if (chosen != null) {
                onDismiss()
                if (move) moveWithUndo(snap, date, chosen) else copyWithUndo(date, chosen, ids)
            }
        }
    ) {
        Text(
            "FROM ${Dates.long(date).uppercase()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("To which day?", style = MaterialTheme.typography.titleMedium)
        val today = Dates.today()
        val quick = listOf(-1L, 0L, 1L).map { shift(today, it) }.filter { it != date }.distinct()
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            quick.forEach { d ->
                FilterChip(selected = chosen == d, onClick = { target = d }, label = { Text(relativeDayLabel(d)) })
            }
        }
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = Spacing.touch)) {
            Text(if (chosen == null || chosen in quick) "Choose a date" else Dates.long(chosen))
        }
        if (chosen != null) {
            val already = snap.setsByDate[chosen].orEmpty().size
            if (already > 0) {
                Text(
                    "${Dates.medium(chosen)} already has ${plural(already, "set")}. These are added to them; nothing is replaced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        GoldHairline()
        if (move) {
            Text(
                "The whole workout moves: every exercise below, with the workout comment and times.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            exercises.forEach { exId -> ExerciseSummary(snap, date, exId) }
        } else {
            ExerciseChecklist(snap, date, exercises, checked) { exId, on -> checked = if (on) checked + exId else checked - exId }
        }
    }
    if (picking) PickDateDialog(chosen ?: date, onDismiss = { picking = false }) { target = it }
}

/**
 * Copies a previous workout into this day (#84). Step one picks the workout, step two ticks the exercises to bring
 * across. Copies are always added to this day; nothing already here is replaced.
 */
@Composable
fun CopyPreviousWorkoutSheet(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    var source by remember { mutableStateOf<String?>(null) }
    val days = remember(snap, date) { snap.setsByDate.keys.filter { it != date }.sortedDescending().take(60) }
    val sourceExercises = remember(source, snap) {
        source?.let { d -> snap.setsByDate[d].orEmpty().map { it.exerciseId }.distinct() }.orEmpty()
    }
    var checked by remember(source) { mutableStateOf(sourceExercises.toSet()) }
    val from = source
    val ids = from?.let { d -> snap.setsByDate[d].orEmpty().filter { it.exerciseId in checked }.map { it.id } }.orEmpty()

    FitSheet(
        title = if (from == null) "Copy previous workout" else "What to copy",
        onDismiss = onDismiss,
        confirmLabel = if (from == null) null else "Copy ${plural(ids.size, "set")}",
        confirmEnabled = ids.isNotEmpty(),
        onConfirm = if (from == null) null else ({
            onDismiss()
            copyWithUndo(from, date, ids)
        })
    ) {
        if (from == null) {
            Text(
                "INTO ${Dates.long(date).uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (days.isEmpty()) {
                Text("There are no other workouts to copy from yet.", style = MaterialTheme.typography.bodyMedium)
            }
            days.forEach { d ->
                val dSets = snap.setsByDate[d].orEmpty()
                val names = dSets.map { snap.exercises[it.exerciseId]?.name ?: "Exercise" }.distinct()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.row)
                        .clickable(onClickLabel = "Choose this workout") { source = d }
                        .padding(vertical = Spacing.sm)
                ) {
                    Text(Dates.long(d), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${plural(names.size, "exercise")} · ${names.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                GoldHairline()
            }
        } else {
            TextButton(onClick = { source = null }, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text("‹ Another workout")
            }
            Text(
                "FROM ${Dates.long(from).uppercase()} INTO ${Dates.medium(date).uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ExerciseChecklist(snap, from, sourceExercises, checked) { exId, on ->
                checked = if (on) checked + exId else checked - exId
            }
            Text(
                "Set comments come across too. Personal-record marks don't: a copy isn't the day the record was set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The review step's checklist: one row per exercise with its sets, all ticked to start with. */
@Composable
private fun ExerciseChecklist(
    snap: Snapshot,
    day: String,
    exercises: List<Long>,
    checked: Set<Long>,
    onToggle: (Long, Boolean) -> Unit
) {
    exercises.forEach { exId ->
        val on = exId in checked
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.row)
                .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(exId, it) }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = on, onCheckedChange = null)
            Column(Modifier.weight(1f).padding(start = Spacing.sm)) { ExerciseLines(snap, day, exId) }
        }
    }
}

@Composable
private fun ExerciseSummary(snap: Snapshot, day: String, exId: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) { ExerciseLines(snap, day, exId) }
}

@Composable
private fun ExerciseLines(snap: Snapshot, day: String, exId: Long) {
    val exSets = snap.setsByDate[day].orEmpty().filter { it.exerciseId == exId }
    Text(snap.exercises[exId]?.name ?: "Exercise #$exId", style = MaterialTheme.typography.bodyLarge)
    Text(
        exSets.joinToString(", ") { describeSet(snap, it.weightKg, it.reps, it.distance, it.durationSec) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

private fun shift(date: String, days: Long): String = Dates.parse(date)?.plusDays(days)?.format(Dates.ISO) ?: date

/** Copies [ids] from [from] into [to], then offers to remove exactly the copies again. */
private fun copyWithUndo(from: String, to: String, ids: List<Long>) {
    AppScope.scope.launch {
        try {
            val copies = Workouts.copyWorkout(from, to, ids)
            UiEvents.show("Copied ${plural(copies.size, "set")} to ${Dates.medium(to)}", "Undo") {
                AppScope.scope.launch { Workouts.deleteSets(copies) }
            }
        } catch (e: WorkoutDataException) {
            UiEvents.show(e.message ?: "That didn't work.")
        }
    }
}

/**
 * Moves the workout on [from] to [to]. Undo moves the same sets back and restores both days' comments and times as
 * they were, since a move can merge them on the day it lands.
 */
private fun moveWithUndo(snap: Snapshot, from: String, to: String) {
    val setIds = snap.setsByDate[from].orEmpty().map { it.id }
    val fromComment = snap.workoutComments[from]?.joinToString("\n\n")
    val toComment = snap.workoutComments[to]?.joinToString("\n\n")
    val fromTimes = snap.workoutTimes[from].orEmpty()
    val toTimes = snap.workoutTimes[to].orEmpty()
    AppScope.scope.launch {
        try {
            val moved = Workouts.moveWorkout(from, to)
            UiEvents.show("Moved ${plural(moved, "set")} to ${Dates.medium(to)}", "Undo") {
                AppScope.scope.launch {
                    try {
                        Workouts.moveSets(setIds, from)
                        Workouts.setWorkoutComment(from, fromComment)
                        Workouts.setWorkoutComment(to, toComment)
                        Workouts.setWorkoutTimes(from, fromTimes)
                        Workouts.setWorkoutTimes(to, toTimes)
                    } catch (e: Exception) {
                        UiEvents.show("Couldn't undo that: ${e.message}")
                    }
                }
            }
        } catch (e: WorkoutDataException) {
            UiEvents.show(e.message ?: "That didn't work.")
        }
    }
}
