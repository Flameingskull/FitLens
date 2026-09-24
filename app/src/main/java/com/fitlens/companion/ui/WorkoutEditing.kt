@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import kotlinx.coroutines.launch

/**
 * Editing a whole workout (#10): its comment, copying it to another day, copying a previous one into it, moving it
 * and deleting it. Everything here goes through [Workouts], which keeps FitNotes merge rules intact.
 *
 * Deferred: dragging exercises into a different order needs a stored position that `workout_set` doesn't have.
 */

/** Adds, edits or removes the comment on a whole workout. */
@Composable
fun WorkoutCommentDialog(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val existing = remember(snap, date) { snap.workoutComments[date]?.joinToString("\n\n").orEmpty() }
    var text by remember(date) { mutableStateOf(existing) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workout comment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    Dates.long(date),
                    style = MaterialTheme.typography.labelMedium,
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
                        "This day had more than one comment from FitNotes. Saving replaces them with the single " +
                            "comment above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = text
                onDismiss()
                AppScope.scope.launch {
                    try {
                        Workouts.setWorkoutComment(date, value)
                    } catch (e: WorkoutDataException) {
                        UiEvents.show(e.message ?: "That comment couldn't be saved.")
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (existing.isNotBlank()) {
                    TextButton(onClick = {
                        onDismiss()
                        AppScope.scope.launch { Workouts.setWorkoutComment(date, null) }
                    }) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Confirms deleting everything logged on a day, and offers an undo afterwards. */
@Composable
fun DeleteWorkoutDialog(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val sets = remember(snap, date) { snap.setsByDate[date].orEmpty() }
    val comment = remember(snap, date) { snap.workoutComments[date]?.joinToString("\n\n") }
    val time = remember(snap, date) { snap.workoutTimes[date]?.firstOrNull() }
    val exercises = remember(sets) { sets.map { it.exerciseId }.distinct().size }

    ConfirmDialog(
        title = "Delete this workout?",
        text = "${sets.size} set${if (sets.size == 1) "" else "s"} across $exercises exercise${if (exercises == 1) "" else "s"}" +
            (if (comment.isNullOrBlank()) "" else ", and the workout comment") +
            ", will be removed from ${Dates.medium(date)}. Photos and measurements on this day are kept.",
        onDismiss = onDismiss
    ) {
        AppScope.scope.launch {
            Workouts.deleteWorkout(date)
            UiEvents.show("Workout deleted", "Undo") {
                AppScope.scope.launch {
                    Workouts.addSets(sets)
                    if (!comment.isNullOrBlank()) Workouts.setWorkoutComment(date, comment)
                    if (time != null) Workouts.setWorkoutTime(date, time.start, time.end)
                }
            }
        }
    }
}

/** Copies this workout to another day, or moves it there. Both merge into whatever is already on that day. */
@Composable
fun CopyOrMoveWorkoutDialog(date: String, move: Boolean, onDismiss: () -> Unit) {
    PickDateDialog(initial = date, onDismiss = onDismiss) { target ->
        if (target == date) {
            UiEvents.show(if (move) "That's the same day." else "Pick a different day to copy to.")
        } else {
            AppScope.scope.launch {
                try {
                    if (move) {
                        val moved = Workouts.moveWorkout(date, target)
                        UiEvents.show("Moved $moved set${if (moved == 1) "" else "s"} to ${Dates.medium(target)}")
                    } else {
                        val copied = Workouts.copyWorkout(date, target)
                        UiEvents.show("Copied $copied set${if (copied == 1) "" else "s"} to ${Dates.medium(target)}")
                    }
                } catch (e: WorkoutDataException) {
                    UiEvents.show(e.message ?: "That didn't work.")
                }
            }
        }
    }
}

/**
 * Copies a previous workout into this day. Step one picks the day, step two ticks the exercises to bring across.
 * Copies are always added to this day — nothing already here is replaced.
 */
@Composable
fun CopyPreviousWorkoutDialog(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    var source by remember { mutableStateOf<String?>(null) }
    var checked by remember(source) { mutableStateOf(emptySet<Long>()) }

    val days = remember(snap, date) {
        snap.setsByDate.keys.filter { it != date }.sortedDescending().take(60)
    }
    val sourceExercises = remember(source, snap) {
        source?.let { d -> snap.setsByDate[d].orEmpty().map { it.exerciseId }.distinct() }.orEmpty()
    }
    LaunchedEffect(source) { checked = sourceExercises.toSet() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source == null) "Copy a previous workout" else "What to copy") },
        text = {
            Column(Modifier.heightIn(max = 440.dp)) {
                val chosen = source
                if (chosen == null) {
                    if (days.isEmpty()) {
                        Text("There are no other workouts to copy from yet.")
                    } else {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            days.forEach { d ->
                                item(key = d) {
                                    val dSets = snap.setsByDate[d].orEmpty()
                                    val names = dSets.map { snap.exercises[it.exerciseId]?.name ?: "Exercise" }
                                        .distinct().joinToString(", ")
                                    Column(
                                        Modifier.fillMaxWidth().clickable { source = d }.padding(vertical = 8.dp)
                                    ) {
                                        Text(Dates.long(d), style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "${dSets.size} sets · $names",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "From ${Dates.long(chosen)} into ${Dates.medium(date)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        sourceExercises.forEach { exId ->
                            val exSets = snap.setsByDate[chosen].orEmpty().filter { it.exerciseId == exId }
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    checked = if (exId in checked) checked - exId else checked + exId
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = exId in checked, onCheckedChange = {
                                    checked = if (it) checked + exId else checked - exId
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(snap.exercises[exId]?.name ?: "Exercise #$exId")
                                    Text(
                                        exSets.joinToString(", ") {
                                            describeSet(snap, it.weightKg, it.reps, it.distance, it.durationSec)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Text(
                            "Set comments come across too. Personal-record marks don't: a copy isn't the day the " +
                                "record was set.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            val chosen = source
            if (chosen != null) {
                TextButton(onClick = {
                    val ids = snap.setsByDate[chosen].orEmpty().filter { it.exerciseId in checked }.map { it.id }
                    if (ids.isEmpty()) {
                        UiEvents.show("Tick at least one exercise.")
                    } else {
                        onDismiss()
                        AppScope.scope.launch {
                            try {
                                val copied = Workouts.copyWorkout(chosen, date, ids)
                                UiEvents.show("Copied $copied set${if (copied == 1) "" else "s"} into ${Dates.medium(date)}")
                            } catch (e: WorkoutDataException) {
                                UiEvents.show(e.message ?: "That didn't work.")
                            }
                        }
                    }
                }) { Text("Copy") }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (source != null) TextButton(onClick = { source = null }) { Text("Back") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
