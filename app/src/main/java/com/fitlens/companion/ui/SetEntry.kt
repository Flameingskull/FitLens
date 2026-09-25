@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.StepperField
import com.fitlens.companion.ui.design.SetRow as SetRowView
import kotlin.math.max
import kotlinx.coroutines.launch

/**
 * Logging sets for one exercise on one day (#16): fields that follow the exercise type, +/- steppers, auto-fill
 * from last time, per-set comments, and Save / Update / Delete with an undo.
 *
 * Deferred on purpose: drag to reorder needs a stored position that `workout_set` doesn't have yet, and the gold
 * PR trophy waits for #23 — nothing here writes `is_pr`, so an imported FitNotes flag is still the only one shown.
 */

/** Global fallback increments. Per-exercise increments are #15, the global weight setting is #7. */
private const val DEFAULT_WEIGHT_STEP = 2.5
private const val DISTANCE_STEP = 0.5
private const val DURATION_STEP = 15

private fun num(s: String): Double = s.trim().replace(',', '.').toDoubleOrNull() ?: 0.0

/** Accepts `90`, `1:30` or `1:02:03`. */
private fun parseDuration(s: String): Int {
    val t = s.trim()
    if (t.isEmpty()) return 0
    val parts = t.split(":")
    return try {
        when (parts.size) {
            1 -> num(parts[0]).toInt()
            2 -> parts[0].trim().toInt() * 60 + parts[1].trim().toInt()
            else -> parts[0].trim().toInt() * 3600 + parts[1].trim().toInt() * 60 + parts[2].trim().toInt()
        }
    } catch (e: NumberFormatException) {
        0
    }
}

@Composable
fun SetEntryScreen(snap: Snapshot, nav: Nav, date: String, exerciseId: Long) {
    val ex = snap.exercises[exerciseId]
    val allSets = snap.setsByExercise[exerciseId] ?: emptyList()
    val sets = remember(snap, date, exerciseId) { allSets.filter { it.date == date } }
    val type = ex?.type ?: ExerciseTypes.WEIGHT_REPS

    // Which fields to show. The exercise type decides, but anything already logged for this exercise is always
    // editable, so an imported exercise with an unexpected type can still be corrected. Full type handling is #14.
    val showDistance = ExerciseTypes.usesDistance(type) || allSets.any { it.distance > 0 }
    val showDuration = ExerciseTypes.usesDuration(type) || allSets.any { it.durationSec > 0 }
    val showReps = ExerciseTypes.usesReps(type) || allSets.any { it.reps > 0 }
    val showWeight = ExerciseTypes.usesWeight(type) || allSets.any { it.weightKg != 0.0 } ||
        (!showDistance && !showDuration && !showReps)

    val prefs by Settings.portable.collectAsState()

    // Auto-fill: what was last logged today, otherwise the first set of the previous workout for this exercise.
    // "Leave empty" in Settings → Workout & logging turns it off (#97).
    val fillFromLast = prefs.autofillSource != PortableSettings.AUTOFILL_EMPTY
    val template = remember(snap, date, exerciseId, fillFromLast) {
        if (!fillFromLast) null else {
            val today = allSets.lastOrNull { it.date == date }
            val previousDay = allSets.filter { it.date < date }.maxByOrNull { it.date }?.date
            today ?: previousDay?.let { d -> allSets.firstOrNull { it.date == d } }
        }
    }

    var selected by remember(date, exerciseId) { mutableStateOf<Long?>(null) }
    var weight by remember(date, exerciseId) { mutableStateOf("") }
    var reps by remember(date, exerciseId) { mutableStateOf("") }
    var distance by remember(date, exerciseId) { mutableStateOf("") }
    var duration by remember(date, exerciseId) { mutableStateOf("") }
    var comment by remember(date, exerciseId) { mutableStateOf("") }
    // The exact kilograms the weight field was filled from, and the text it was filled with. Weights are stored in
    // kilograms but shown rounded in the user's unit, so converting the displayed text back on every save quietly
    // rewrote the stored value for anyone using pounds (#75). Only convert when the text has actually been edited.
    var loadedWeightText by remember(date, exerciseId) { mutableStateOf("") }
    var loadedWeightKg by remember(date, exerciseId) { mutableStateOf<Double?>(null) }
    var deleting by remember { mutableStateOf<SetRow?>(null) }
    var editExercise by remember { mutableStateOf(false) }

    val weightStep = prefs.weightIncrementKg ?: DEFAULT_WEIGHT_STEP

    // "Keep screen on" while logging, switched in Settings → Workout & logging (#97).
    val view = LocalView.current
    val keepOn = prefs.keepScreenOn
    DisposableEffect(view, keepOn) {
        if (keepOn) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(selected, sets, template) {
        val chosen = selected?.let { id -> sets.firstOrNull { it.id == id } }
        if (selected != null && chosen == null) {
            selected = null
        } else {
            val source = chosen ?: template
            weight = source?.weightKg?.takeIf { it != 0.0 }?.let { fmtNum(snap.weight(it), 2) } ?: ""
            loadedWeightText = weight
            loadedWeightKg = source?.weightKg
            reps = source?.reps?.takeIf { it > 0 }?.toString() ?: ""
            distance = source?.distance?.takeIf { it > 0 }?.let { fmtNum(it, 2) } ?: ""
            duration = source?.durationSec?.takeIf { it > 0 }?.let { fmtDuration(it) } ?: ""
            comment = chosen?.comment ?: ""
        }
    }

    val haptic = LocalHapticFeedback.current

    fun save() {
        // Untouched field: keep the stored kilograms exactly as they were, rather than round-tripping the
        // two-decimal display value back through the unit conversion (#75).
        val kg = if (weight == loadedWeightText) loadedWeightKg ?: 0.0 else snap.toKg(num(weight))
        val r = reps.trim().toIntOrNull() ?: 0
        val dist = num(distance)
        val dur = parseDuration(duration)
        if (kg == 0.0 && r == 0 && dist == 0.0 && dur == 0) {
            UiEvents.show("Enter something to save.")
            return
        }
        val note = comment.trim().ifBlank { null }
        val chosen = selected?.let { id -> sets.firstOrNull { it.id == id } }
        AppScope.scope.launch {
            try {
                if (chosen == null) {
                    val id = Workouts.addSet(exerciseId, date, kg, r, dist, dur, note)
                    // The PR mark was decided as the set was saved; the reloaded snapshot carries it (#23).
                    val isPr = Store.snapshot.value?.setsByExercise?.get(exerciseId)?.any { it.id == id && it.isPr } == true
                    if (isPr && Settings.currentPortable().celebratePrs) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        UiEvents.show("New personal record: ${snap.fmtWeight(kg)} ${snap.weightUnit} × $r")
                    }
                } else {
                    Workouts.updateSet(
                        chosen.copy(weightKg = kg, reps = r, distance = dist, durationSec = dur, comment = note)
                    )
                    // With auto-select next on, the following set of the day is selected, ready to adjust (#97).
                    val next = if (Settings.currentPortable().autoSelectNext) {
                        sets.getOrNull(sets.indexOfFirst { it.id == chosen.id } + 1)
                    } else null
                    selected = next?.id
                    UiEvents.show(if (next == null) "Set updated" else "Set updated. Next set selected.")
                }
            } catch (e: WorkoutDataException) {
                UiEvents.show(e.message ?: "That set couldn't be saved.")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(ex?.name ?: "Exercise", onBack = { nav.pop() }) {
            IconButton(onClick = { editExercise = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit this exercise")
            }
            IconButton(onClick = { nav.push(Screen.ExerciseDetail(exerciseId)) }, enabled = allSets.isNotEmpty()) {
                Icon(Icons.Filled.List, contentDescription = "History and graphs")
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    Dates.long(date).uppercase(),
                    Modifier.padding(start = 16.dp, top = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!ex?.notes.isNullOrBlank()) {
                    ExerciseNotes(ex!!.notes!!, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }

            // ---------- Entry ----------
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showWeight) {
                        StepperField(
                            label = "Weight (${snap.weightUnit})",
                            value = weight,
                            onValue = { weight = it },
                            onStep = { dir -> weight = fmtNum(max(0.0, num(weight) + dir * weightStep), 2) }
                        )
                    }
                    if (showReps) {
                        StepperField(
                            label = "Reps",
                            value = reps,
                            onValue = { reps = it },
                            onStep = { dir -> reps = max(0, (reps.trim().toIntOrNull() ?: 0) + dir).toString() },
                            keyboard = KeyboardType.Number
                        )
                    }
                    if (showDistance) {
                        StepperField(
                            label = "Distance",
                            value = distance,
                            onValue = { distance = it },
                            onStep = { dir -> distance = fmtNum(max(0.0, num(distance) + dir * DISTANCE_STEP), 2) }
                        )
                    }
                    if (showDuration) {
                        StepperField(
                            label = "Time (mm:ss)",
                            value = duration,
                            onValue = { duration = it },
                            onStep = { dir ->
                                val next = max(0, parseDuration(duration) + dir * DURATION_STEP)
                                duration = if (next == 0) "" else fmtDuration(next)
                            },
                            keyboard = KeyboardType.Text
                        )
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Set comment (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (selected == null) {
                        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text("Save set", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { save() }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Update") }
                            OutlinedButton(
                                onClick = { deleting = sets.firstOrNull { it.id == selected } },
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) { Text("Delete") }
                        }
                        TextButton(onClick = { selected = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("New set instead")
                        }
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(top = 14.dp)); SectionTitle("Today’s sets") }

            if (sets.isEmpty()) {
                item {
                    Text(
                        when {
                            !fillFromLast -> "No sets yet today."
                            template == null -> "Nothing logged for this exercise yet."
                            else -> "No sets yet today — the fields are filled in from last time."
                        },
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            sets.forEachIndexed { i, s ->
                item(key = "s${s.id}") {
                    val isSelected = selected == s.id
                    SetRowView(
                        index = i + 1,
                        summary = describeSet(snap, s.weightKg, s.reps, s.distance, s.durationSec),
                        comment = s.comment,
                        isPr = s.isPr,
                        selected = isSelected,
                        onClick = { selected = if (selected == s.id) null else s.id },
                        trailingHint = if (isSelected) "Selected" else "Edit"
                    )
                }
            }
            if (sets.isNotEmpty()) {
                item {
                    val volume = sets.sumOf { it.weightKg * it.reps }
                    Text(
                        "${sets.size} set${if (sets.size == 1) "" else "s"}" +
                            if (volume > 0) " · volume ${fmtNum(snap.weight(volume), 0)} ${snap.weightUnit}" else "",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    deleting?.let { s ->
        ConfirmDialog(
            title = "Delete this set?",
            text = describeSet(snap, s.weightKg, s.reps, s.distance, s.durationSec),
            onDismiss = { deleting = null }
        ) {
            selected = null
            AppScope.scope.launch {
                Workouts.deleteSet(s.id)
                UiEvents.show("Set deleted", "Undo") {
                    AppScope.scope.launch {
                        try {
                            // The whole row goes back (isPr included, #69), and restoring an imported set also
                            // clears the skip rule its delete left behind (#76).
                            Workouts.addSets(listOf(s))
                        } catch (e: Exception) {
                            UiEvents.show("Couldn't undo that: ${e.message}")
                        }
                    }
                }
            }
        }
    }
    if (editExercise && ex != null) {
        ExerciseEditorDialog(snap, existing = ex, initialCategoryId = ex.categoryId, onDismiss = { editExercise = false })
    }
}
