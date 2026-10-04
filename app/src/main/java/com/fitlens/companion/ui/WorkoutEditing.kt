@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.ui.platform.LocalContext
import com.fitlens.companion.ui.design.GlassOutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.WorkoutTime
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

/**
 * FitNotes's Copy Workout dialog (#148): one menu entry, three choices, each with a line of explanation. Every choice
 * opens its FitLens sheet, which keeps the review step and Undo.
 */
@Composable
fun CopyWorkoutSheet(hasSets: Boolean, hasWorkout: Boolean, onDismiss: () -> Unit, onChoose: (CopyChoice) -> Unit) {
    FitSheet(title = stringResource(R.string.wk_copy_title), onDismiss = onDismiss) {
        CopyChoiceRow(stringResource(R.string.wk_copy_this), stringResource(R.string.wk_copy_this_detail), hasSets) { onChoose(CopyChoice.COPY_THIS) }
        GoldHairline()
        CopyChoiceRow(stringResource(R.string.wk_move_this), stringResource(R.string.wk_move_this_detail), hasWorkout) { onChoose(CopyChoice.MOVE_THIS) }
        GoldHairline()
        CopyChoiceRow(stringResource(R.string.wk_copy_previous), stringResource(R.string.wk_copy_previous_detail), true) { onChoose(CopyChoice.COPY_PREVIOUS) }
    }
}

enum class CopyChoice { COPY_THIS, MOVE_THIS, COPY_PREVIOUS }

@Composable
private fun CopyChoiceRow(title: String, summary: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = Spacing.sm)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * FitNotes's set checklist (#148), shared by Share Workout and Create Workout: Select All, then each exercise as an
 * uppercase heading with its own checkbox, then its sets with a checkbox each. An exercise's box is part-ticked when
 * only some of its sets are.
 */
@Composable
internal fun SetChecklist(snap: Snapshot, date: String, ticked: Set<Long>, onTicked: (Set<Long>) -> Unit) {
    val sets = snap.setsByDate[date].orEmpty()
    val byExercise = remember(sets) {
        sets.groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.position } }
            .map { (exId, own) -> exId to own.sortedBy { it.position } }
    }
    val all = remember(sets) { sets.map { it.id }.toSet() }
    val res = LocalContext.current.resources
    ChecklistHeading(stringResource(R.string.wk_select_all), stateOf(ticked, all), bold = false) {
        onTicked(if (all.all { it in ticked }) ticked - all else ticked + all)
    }
    byExercise.forEach { (exId, own) ->
        val ids = own.map { it.id }.toSet()
        ChecklistHeading((snap.exercises[exId]?.name ?: stringResource(R.string.ex_fallback)).uppercase(), stateOf(ticked, ids), bold = true) {
            onTicked(if (ids.all { it in ticked }) ticked - ids else ticked + ids)
        }
        val fields = setFields(snap, exId, own)
        own.forEachIndexed { i, s ->
            val on = s.id in ticked
            val cells = setCells(snap, fields, s)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.touch)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onTicked(if (it) ticked + s.id else ticked - s.id) })
                    .semantics(mergeDescendants = true) {
                        contentDescription = res.getString(R.string.wk_set_spoken, i + 1, cells.joinToString(", ") { it.spoken }) +
                            if (s.isPr) res.getString(R.string.wk_pr_spoken) else ""
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(0.6f))
                cells.forEach { c ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                        Text(c.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (c.unit.isNotEmpty()) {
                            Text(" ${c.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

private fun stateOf(ticked: Set<Long>, ids: Set<Long>): ToggleableState {
    val n = ids.count { it in ticked }
    return when {
        n == 0 -> ToggleableState.Off
        n == ids.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
}

@Composable
private fun ChecklistHeading(title: String, state: ToggleableState, bold: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.row)
                .triStateToggleable(state = state, role = Role.Checkbox, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TriStateCheckbox(state = state, onClick = null)
        }
        GoldHairline()
    }
}

/** Adds, edits or removes the comment on a whole workout. */
@Composable
fun WorkoutCommentSheet(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val existing = remember(snap, date) { snap.workoutComments[date]?.joinToString("\n\n").orEmpty() }
    var text by remember(date) { mutableStateOf(existing) }
    val res = LocalContext.current.resources

    FitSheet(
        title = stringResource(R.string.wk_comment_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.wk_comment_save),
        confirmEnabled = text.trim() != existing.trim(),
        onConfirm = {
            val value = text
            onDismiss()
            AppScope.scope.launch {
                try {
                    Workouts.setWorkoutComment(date, value)
                } catch (e: WorkoutDataException) {
                    UiEvents.show(e.message ?: res.getString(R.string.set_comment_failed))
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
            label = { Text(stringResource(R.string.wk_comment_hint)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
        )
        if ((snap.workoutComments[date]?.size ?: 0) > 1) {
            Text(
                stringResource(R.string.wk_comment_many),
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
                        UiEvents.show(res.getString(R.string.wk_comment_deleted), res.getString(R.string.undo)) {
                            AppScope.scope.launch { Workouts.setWorkoutComment(date, existing) }
                        }
                    }
                },
                modifier = Modifier.heightIn(min = Spacing.touch)
            ) { Text(stringResource(R.string.wk_comment_delete), color = MaterialTheme.colorScheme.error) }
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
    val res = LocalContext.current.resources

    ConfirmSheet(
        title = stringResource(R.string.wk_delete_title),
        message = stringResource(
            R.string.wk_delete_body,
            pluralStringResource(R.plurals.sets_count, sets.size, sets.size),
            pluralStringResource(R.plurals.lib_exercises, exercises, exercises)
        ) + (if (comment.isNullOrBlank()) "" else stringResource(R.string.wk_delete_comment)) +
            (if (times.isEmpty()) "" else stringResource(R.string.wk_delete_times)) +
            stringResource(R.string.wk_delete_end, Dates.medium(date)),
        confirmLabel = stringResource(R.string.wk_delete_confirm),
        onDismiss = onDismiss,
        onConfirm = { deleteWithUndo(res, date, sets, comment, times, snap.exerciseComments[date].orEmpty()) }
    )
}

/** Deletes the workout on [date], then offers to put back its sets, comment and times. */
private fun deleteWithUndo(res: Resources, date: String, sets: List<SetRow>, comment: String?, times: List<WorkoutTime>, exerciseComments: Map<Long, String>) {
    AppScope.scope.launch {
        Workouts.deleteWorkout(date)
        UiEvents.show(res.getString(R.string.wk_deleted), res.getString(R.string.undo)) {
            AppScope.scope.launch {
                try {
                    Workouts.addSets(sets)
                    if (!comment.isNullOrBlank()) Workouts.setWorkoutComment(date, comment)
                    if (times.isNotEmpty()) Workouts.setWorkoutTimes(date, times)
                    if (exerciseComments.isNotEmpty()) Workouts.setExerciseComments(date, exerciseComments)
                } catch (e: Exception) {
                    UiEvents.show(res.getString(R.string.day_undo_failed, e.message.orEmpty()))
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
    val res = LocalContext.current.resources

    FitSheet(
        title = stringResource(if (move) R.string.wk_move_title else R.string.wk_copy_title),
        onDismiss = onDismiss,
        confirmLabel = when {
            move -> stringResource(R.string.wk_move_title)
            else -> pluralStringResource(R.plurals.wk_copy_sets, ids.size, ids.size)
        },
        confirmEnabled = chosen != null && chosen != date && (move || ids.isNotEmpty()),
        onConfirm = {
            if (chosen != null) {
                onDismiss()
                if (move) moveWithUndo(res, snap, date, chosen) else copyWithUndo(res, date, chosen, ids)
            }
        }
    ) {
        Text(
            stringResource(R.string.wk_from, Dates.long(date)).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(stringResource(R.string.wk_to_which_day), style = MaterialTheme.typography.titleMedium)
        val today = Dates.today()
        val quick = listOf(-1L, 0L, 1L).map { shift(today, it) }.filter { it != date }.distinct()
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            quick.forEach { d ->
                FilterChip(selected = chosen == d, onClick = { target = d }, label = { Text(relativeDayLabel(d)) })
            }
        }
        GlassOutlinedButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = Spacing.touch)) {
            Text(if (chosen == null || chosen in quick) stringResource(R.string.wk_choose_date) else Dates.long(chosen))
        }
        if (chosen != null) {
            val already = snap.setsByDate[chosen].orEmpty().size
            if (already > 0) {
                Text(
                    pluralStringResource(R.plurals.wk_already_has, already, Dates.medium(chosen), already),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        GoldHairline()
        if (move) {
            Text(
                stringResource(R.string.wk_move_note),
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
    val res = LocalContext.current.resources

    FitSheet(
        title = stringResource(if (from == null) R.string.wk_copy_previous_title else R.string.wk_what_to_copy),
        onDismiss = onDismiss,
        confirmLabel = if (from == null) null else pluralStringResource(R.plurals.wk_copy_sets, ids.size, ids.size),
        confirmEnabled = ids.isNotEmpty(),
        onConfirm = if (from == null) null else ({
            onDismiss()
            copyWithUndo(res, from, date, ids)
        })
    ) {
        if (from == null) {
            Text(
                stringResource(R.string.wk_into, Dates.long(date)).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (days.isEmpty()) {
                Text(stringResource(R.string.wk_nothing_to_copy), style = MaterialTheme.typography.bodyMedium)
            }
            days.forEach { d ->
                val dSets = snap.setsByDate[d].orEmpty()
                val names = dSets.map { snap.exercises[it.exerciseId]?.name ?: res.getString(R.string.ex_fallback) }.distinct()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.row)
                        .clickable(onClickLabel = stringResource(R.string.wk_choose_this)) { source = d }
                        .padding(vertical = Spacing.sm)
                ) {
                    Text(Dates.long(d), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        pluralStringResource(R.plurals.lib_exercises, names.size, names.size) + " · " + names.joinToString(", "),
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
                Text(stringResource(R.string.wk_another))
            }
            Text(
                stringResource(R.string.wk_from_into, Dates.long(from), Dates.medium(date)).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ExerciseChecklist(snap, from, sourceExercises, checked) { exId, on ->
                checked = if (on) checked + exId else checked - exId
            }
            Text(
                stringResource(R.string.wk_copy_note),
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
    val res = LocalContext.current.resources
    Text(snap.exercises[exId]?.name ?: stringResource(R.string.ex_fallback), style = MaterialTheme.typography.bodyLarge)
    Text(
        exSets.joinToString(", ") { describeSet(res, snap, it.weightKg, it.reps, it.distance, it.durationSec, it.exerciseId) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

private fun shift(date: String, days: Long): String = Dates.parse(date)?.plusDays(days)?.format(Dates.ISO) ?: date

/** Copies [ids] from [from] into [to], then offers to remove exactly the copies again. */
private fun copyWithUndo(res: Resources, from: String, to: String, ids: List<Long>) {
    AppScope.scope.launch {
        try {
            val copies = Workouts.copyWorkout(from, to, ids)
            UiEvents.show(res.getQuantityString(R.plurals.wk_copied_to, copies.size, copies.size, Dates.medium(to)), res.getString(R.string.undo)) {
                AppScope.scope.launch { Workouts.deleteSets(copies) }
            }
        } catch (e: WorkoutDataException) {
            UiEvents.show(e.message ?: res.getString(R.string.wk_failed))
        }
    }
}

/**
 * Moves the workout on [from] to [to]. Undo moves the same sets back and restores both days' comments and times as
 * they were, since a move can merge them on the day it lands.
 */
private fun moveWithUndo(res: Resources, snap: Snapshot, from: String, to: String) {
    val setIds = snap.setsByDate[from].orEmpty().map { it.id }
    val fromComment = snap.workoutComments[from]?.joinToString("\n\n")
    val toComment = snap.workoutComments[to]?.joinToString("\n\n")
    val fromTimes = snap.workoutTimes[from].orEmpty()
    val toTimes = snap.workoutTimes[to].orEmpty()
    val fromExerciseComments = snap.exerciseComments[from].orEmpty()
    val toExerciseComments = snap.exerciseComments[to].orEmpty()
    AppScope.scope.launch {
        try {
            val moved = Workouts.moveWorkout(from, to)
            UiEvents.show(res.getQuantityString(R.plurals.wk_moved_to, moved, moved, Dates.medium(to)), res.getString(R.string.undo)) {
                AppScope.scope.launch {
                    try {
                        Workouts.moveSets(setIds, from)
                        Workouts.setWorkoutComment(from, fromComment)
                        Workouts.setWorkoutComment(to, toComment)
                        Workouts.setWorkoutTimes(from, fromTimes)
                        Workouts.setWorkoutTimes(to, toTimes)
                        Workouts.setExerciseComments(from, fromExerciseComments)
                        Workouts.setExerciseComments(to, toExerciseComments)
                    } catch (e: Exception) {
                        UiEvents.show(res.getString(R.string.day_undo_failed, e.message.orEmpty()))
                    }
                }
            }
        } catch (e: WorkoutDataException) {
            UiEvents.show(e.message ?: res.getString(R.string.wk_failed))
        }
    }
}
