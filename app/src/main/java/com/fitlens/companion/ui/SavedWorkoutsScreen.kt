@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.PlannedExercise
import com.fitlens.companion.data.PlannedSet
import com.fitlens.companion.data.RoutineDay
import com.fitlens.companion.data.Routines
import com.fitlens.companion.data.SavedWorkout
import com.fitlens.companion.data.SavedWorkouts
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.SetTypes
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.PickerItem
import com.fitlens.companion.ui.design.SearchablePicker
import com.fitlens.companion.ui.design.SegmentedSwitch
import com.fitlens.companion.ui.design.TopBarAction
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Saved workouts (#100): the list, the editor, the sets of one exercise, adding a workout to a day and saving a logged
 * day as a workout. A **workout** is a group of exercises with prescribed sets; adding one to a day logs every set at
 * once, as FitNotes's "Log All" does, and the user then updates each set as they train.
 */

private fun howMany(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

/** Every exercise as a picker row, filed under its category. */
@Composable
fun exercisePickerItems(snap: Snapshot): List<PickerItem> = snap.exercisesSorted.map { ex ->
    val cat = snap.categories[ex.categoryId]
    PickerItem(
        id = ex.id,
        title = ex.name,
        subtitle = snap.lastUsedByExercise[ex.id]?.let { "Last ${Dates.medium(it)}" } ?: "Not logged yet",
        section = cat?.name ?: "Uncategorised",
        color = categoryColour(cat?.colour ?: 0)
    )
}

/** A saved workout's exercises as one line, for example "Bench Press, Squat and 3 more". */
private fun exerciseLine(snap: Snapshot, w: SavedWorkout): String {
    val names = w.exercises.map { snap.exercises[it.exerciseId]?.name ?: "Exercise" }
    return when {
        names.isEmpty() -> "No exercises yet"
        names.size <= 3 -> names.joinToString(", ")
        else -> names.take(2).joinToString(", ") + " and ${names.size - 2} more"
    }
}

// ---------------------------------------------------------------------------------------------------------
// The list
// ---------------------------------------------------------------------------------------------------------

@Composable
fun SavedWorkoutsScreen(snap: Snapshot, nav: Nav) {
    var deleting by remember { mutableStateOf<SavedWorkout?>(null) }
    Column(Modifier.fillMaxSize()) {
        PlainTopBar("Workouts") {
            IconButton(onClick = { nav.push(Screen.SavedWorkoutEditor(0L)) }) {
                Icon(Icons.Filled.Add, contentDescription = "New workout")
            }
        }
        if (snap.savedWorkouts.isEmpty()) {
            EmptyState(
                "No saved workouts yet",
                "A workout is a group of exercises with their sets. Save one here, or save a day you've logged from " +
                    "its menu, then add the whole workout to any day in one go."
            ) {
                Button(onClick = { nav.push(Screen.SavedWorkoutEditor(0L)) }) { Text("Create a workout") }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                items(snap.savedWorkouts, key = { it.id }) { w ->
                    ListRowWithMenu(
                        title = w.name,
                        subtitle = "${howMany(w.exercises.size, "exercise")} · ${exerciseLine(snap, w)}",
                        onClick = { nav.push(Screen.SavedWorkoutEditor(w.id)) },
                        menu = listOf(
                            MenuAction("Edit") { nav.push(Screen.SavedWorkoutEditor(w.id)) },
                            MenuAction("Copy") {
                                AppScope.scope.launch {
                                    SavedWorkouts.save(w.copy(id = 0L, name = "${w.name} (copy)"))
                                    UiEvents.show("Copied ${w.name}")
                                }
                            },
                            MenuAction("Delete") { deleting = w }
                        )
                    )
                    GoldHairline()
                }
            }
        }
    }
    deleting?.let { w ->
        ConfirmSheet(
            title = "Delete ${w.name}?",
            message = "The saved workout goes. Days you've already logged with it keep their sets.",
            confirmLabel = "Delete workout",
            onDismiss = { deleting = null },
            onConfirm = {
                AppScope.scope.launch {
                    SavedWorkouts.delete(w.id)
                    UiEvents.show("Deleted ${w.name}", "Undo") {
                        AppScope.scope.launch { SavedWorkouts.save(w.copy(id = 0L)) }
                    }
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------------------------------------
// The editor
// ---------------------------------------------------------------------------------------------------------

/** One exercise in the editor, with a stable [key] so dragging keeps each row with its exercise. */
private data class Slot(val key: Long, val planned: PlannedExercise)

/**
 * Creates or edits a saved workout ([id] 0 is a new one): its name and notes, and its exercises in order, each with
 * its sets. Nothing is written until Save; Back with unsaved changes asks first.
 */
@Composable
fun SavedWorkoutEditorScreen(snap: Snapshot, nav: Nav, id: Long) {
    val original = remember(id) { snap.savedWorkoutsById[id] ?: SavedWorkout(0L, "") }
    var name by remember(id) { mutableStateOf(original.name) }
    var notes by remember(id) { mutableStateOf(original.notes.orEmpty()) }
    var nextKey by remember(id) { mutableStateOf(original.exercises.size.toLong()) }
    val slots = remember(id) {
        mutableStateListOf<Slot>().apply { original.exercises.forEachIndexed { i, p -> add(Slot(i.toLong(), p)) } }
    }
    var adding by remember { mutableStateOf(false) }
    var editingSets by remember { mutableStateOf<Slot?>(null) }
    var swapping by remember { mutableStateOf<Slot?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    val draft = SavedWorkout(original.id, name, notes, original.sortOrder, slots.map { it.planned })
    val dirty = draft.name != original.name || draft.notes.orEmpty() != original.notes.orEmpty() ||
        draft.exercises != original.exercises

    fun save() {
        val w = draft
        if (w.name.isBlank()) {
            UiEvents.show("Enter a name for the workout.")
            return
        }
        nav.pop()
        AppScope.scope.launch {
            try {
                SavedWorkouts.save(w)
                UiEvents.show("Saved ${w.name.trim()}")
            } catch (e: WorkoutDataException) {
                UiEvents.show(e.message ?: "That workout couldn't be saved.")
            }
        }
    }
    fun leave() { if (dirty) confirmLeave = true else nav.pop() }
    fun move(from: Int, to: Int) {
        if (to in slots.indices) slots.add(to, slots.removeAt(from))
    }
    BackHandler(enabled = dirty) { confirmLeave = true }

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = if (id == 0L) "New workout" else "Edit workout",
            onBack = { leave() },
            actions = listOf(TopBarAction(Icons.Filled.Check, "Save workout", enabled = name.isNotBlank()) { save() })
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            item(key = "fields") {
                Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Name, for example Push A") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SectionTitle("Exercises")
                if (slots.isEmpty()) {
                    Text(
                        "Add the exercises this workout is made of. Tap one to set its sets.",
                        Modifier.padding(horizontal = Spacing.lg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            slots.forEachIndexed { i, slot ->
                item(key = slot.key) {
                    val ex = snap.exercises[slot.planned.exerciseId]
                    ListRowWithMenu(
                        title = ex?.name ?: "Exercise",
                        subtitle = planSummary(snap, slot.planned),
                        leading = { Dot(categoryColour(snap.categoryOf(slot.planned.exerciseId)?.colour ?: 0), Spacing.md) },
                        onClick = { editingSets = slot },
                        menu = listOf(
                            MenuAction("Sets") { editingSets = slot },
                            MenuAction("Swap exercise") { swapping = slot },
                            MenuAction("Remove") { slots.remove(slot) }
                        ),
                        onMoveUp = if (i > 0) ({ move(i, i - 1) }) else null,
                        onMoveDown = if (i < slots.lastIndex) ({ move(i, i + 1) }) else null
                    )
                    GoldHairline()
                }
            }
            item(key = "add") {
                OutlinedButton(
                    onClick = { adding = true },
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md).heightIn(min = Spacing.touch)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Add exercises")
                }
            }
        }
        GoldHairline()
        Button(
            onClick = { save() },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md).heightIn(min = Spacing.row)
        ) { Text("Save workout") }
    }

    if (adding) {
        SearchablePicker(
            title = "Add exercises",
            items = exercisePickerItems(snap),
            multiSelect = true,
            searchLabel = "Search exercises",
            onDismiss = { adding = false },
            onPick = { ids ->
                adding = false
                ids.forEach { exId ->
                    slots.add(Slot(nextKey, PlannedExercise(exId, SavedWorkouts.FILL_LAST)))
                    nextKey += 1
                }
            }
        )
    }
    swapping?.let { slot ->
        SearchablePicker(
            title = "Swap ${snap.exercises[slot.planned.exerciseId]?.name ?: "exercise"} for",
            items = exercisePickerItems(snap).filter { it.id != slot.planned.exerciseId },
            onDismiss = { swapping = null },
            onPick = { ids ->
                swapping = null
                val at = slots.indexOfFirst { it.key == slot.key }
                ids.firstOrNull()?.let { to ->
                    if (at >= 0) slots[at] = slot.copy(planned = slot.planned.copy(exerciseId = to))
                }
            }
        )
    }
    editingSets?.let { slot ->
        PlannedSetsSheet(snap, slot.planned, onDismiss = { editingSets = null }) { updated ->
            val at = slots.indexOfFirst { it.key == slot.key }
            if (at >= 0) slots[at] = slot.copy(planned = updated)
            editingSets = null
        }
    }
    if (confirmLeave) {
        ConfirmSheet(
            title = "Discard your changes?",
            message = "This workout has changes that haven't been saved.",
            confirmLabel = "Discard changes",
            onDismiss = { confirmLeave = false },
            onConfirm = { nav.pop() }
        )
    }
}

/** How an exercise's sets read in the editor. */
private fun planSummary(snap: Snapshot, p: PlannedExercise): String =
    if (p.fill == SavedWorkouts.FILL_LAST) {
        val last = SavedWorkouts.resolve(snap, p, "9999-12-31")
        if (last.isEmpty()) "As last time · not logged yet, set its sets" else "As last time · ${SavedWorkouts.describe(snap, last)}"
    } else {
        SavedWorkouts.describe(snap, p.sets.filter { !it.isEmpty })
    }

// ---------------------------------------------------------------------------------------------------------
// One exercise's sets
// ---------------------------------------------------------------------------------------------------------

/** A prescribed set as the user types it, in their own units. */
private data class SetDraft(val weight: String = "", val reps: String = "", val distance: String = "", val time: String = "", val type: Int = SetTypes.WORKING)

private fun num(s: String): Double = s.trim().replace(',', '.').toDoubleOrNull() ?: 0.0

/** Accepts `90`, `1:30` or `1:02:03`. */
private fun seconds(s: String): Int {
    val parts = s.trim().split(":").map { it.trim().toIntOrNull() ?: 0 }
    return when (parts.size) {
        0 -> 0
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        else -> parts[0] * 3600 + parts[1] * 60 + parts[2]
    }
}

/**
 * Chooses how an exercise's sets are filled when the workout is added to a day: as last time, or these prescribed
 * sets. The fields follow the exercise's type, as on the Track tab.
 */
@Composable
private fun PlannedSetsSheet(snap: Snapshot, planned: PlannedExercise, onDismiss: () -> Unit, onDone: (PlannedExercise) -> Unit) {
    val ex = snap.exercises[planned.exerciseId]
    val type = ex?.type ?: ExerciseTypes.WEIGHT_REPS
    val last = remember(snap, planned.exerciseId) {
        SavedWorkouts.resolve(snap, planned.copy(fill = SavedWorkouts.FILL_LAST), "9999-12-31")
    }
    var fill by remember { mutableStateOf(planned.fill) }
    val start = planned.sets.ifEmpty { last }.ifEmpty { listOf(PlannedSet()) }
    val rows = remember {
        mutableStateListOf<SetDraft>().apply {
            start.forEach { s ->
                add(
                    SetDraft(
                        weight = s.weightKg.takeIf { it != 0.0 }?.let { fmtNum(snap.weight(it), 2) } ?: "",
                        reps = s.reps.takeIf { it > 0 }?.toString() ?: "",
                        distance = s.distance.takeIf { it > 0 }?.let { fmtNum(it, 2) } ?: "",
                        time = s.durationSec.takeIf { it > 0 }?.let { fmtDuration(it) } ?: "",
                        type = s.setType
                    )
                )
            }
        }
    }

    FitSheet(
        title = ex?.name ?: "Sets",
        onDismiss = onDismiss,
        confirmLabel = "Done",
        onConfirm = {
            val sets = rows.map {
                PlannedSet(snap.toKg(num(it.weight)), it.reps.trim().toIntOrNull() ?: 0, num(it.distance), seconds(it.time), it.type)
            }.filter { !it.isEmpty }
            onDone(planned.copy(fill = fill, sets = if (fill == SavedWorkouts.FILL_PLANNED) sets else planned.sets))
        }
    ) {
        SegmentedSwitch(
            options = listOf("As last time", "These sets"),
            selected = if (fill == SavedWorkouts.FILL_PLANNED) 1 else 0,
            onSelect = { fill = if (it == 1) SavedWorkouts.FILL_PLANNED else SavedWorkouts.FILL_LAST }
        )
        if (fill == SavedWorkouts.FILL_LAST) {
            Text(
                if (last.isEmpty()) "It hasn't been logged yet, so there's nothing to repeat. Choose These sets to plan them."
                else "Each time you add the workout, this exercise repeats what you did the last time: ${SavedWorkouts.describe(snap, last)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            rows.forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("${i + 1}", Modifier.width(Spacing.lg), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ExerciseTypes.usesWeight(type)) {
                        SmallField(r.weight, snap.weightUnit, KeyboardType.Decimal, Modifier.weight(1f)) { rows[i] = r.copy(weight = it) }
                    }
                    if (ExerciseTypes.usesReps(type)) {
                        SmallField(r.reps, "reps", KeyboardType.Number, Modifier.weight(1f)) { rows[i] = r.copy(reps = it) }
                    }
                    if (ExerciseTypes.usesDistance(type)) {
                        SmallField(r.distance, "dist", KeyboardType.Decimal, Modifier.weight(1f)) { rows[i] = r.copy(distance = it) }
                    }
                    if (ExerciseTypes.usesDuration(type)) {
                        SmallField(r.time, "m:ss", KeyboardType.Text, Modifier.weight(1f)) { rows[i] = r.copy(time = it) }
                    }
                    IconButton(onClick = { rows.removeAt(i) }, enabled = rows.size > 1) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove set ${i + 1}")
                    }
                }
            }
            TextButton(
                onClick = { rows.add(rows.lastOrNull() ?: SetDraft()) },
                modifier = Modifier.heightIn(min = Spacing.touch)
            ) { Text("Add set") }
        }
    }
}

@Composable
private fun SmallField(value: String, label: String, keyboard: KeyboardType, modifier: Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier
    )
}

// ---------------------------------------------------------------------------------------------------------
// Adding a workout to a day
// ---------------------------------------------------------------------------------------------------------

/**
 * Adds a whole workout to [date] (#100): a saved workout, or one built on the spot from several exercises (which can
 * be saved for next time). A review lists every exercise and the sets it adds; everything is logged at once, with
 * Undo. [replace] first removes the day's sets, to swap one workout for another. Exercises with nothing to add yet
 * are opened one after another, so they can be logged by hand.
 */
@Composable
fun AddWorkoutSheet(snap: Snapshot, nav: Nav, date: String, replace: Boolean, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf<SavedWorkout?>(null) }
    // The routine day chosen, when the workout came from a routine (#21).
    var chosenDay by remember { mutableStateOf<Long?>(null) }
    var building by remember { mutableStateOf(false) }
    val workout = chosen
    val routine = snap.routinesById[Settings.currentPortable().lastRoutineId] ?: snap.routines.firstOrNull()

    if (building) {
        SearchablePicker(
            title = "Build a workout",
            items = exercisePickerItems(snap),
            multiSelect = true,
            searchLabel = "Search exercises",
            onDismiss = { building = false },
            onPick = { ids ->
                building = false
                chosen = SavedWorkout(0L, "", exercises = ids.map { PlannedExercise(it, SavedWorkouts.FILL_LAST) })
            }
        )
        return
    }

    if (workout == null) {
        FitSheet(title = if (replace) "Replace this workout" else "Add workout", onDismiss = onDismiss) {
            Text(
                if (replace) "The sets on ${Dates.medium(date)} are replaced by the workout you choose. You can undo it."
                else "Choose a saved workout, or build one from your exercises.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // The current routine first, with its next day marked, as FitNotes suggests it (#21).
            if (routine != null && routine.days.isNotEmpty()) {
                val next = Routines.nextDay(snap, routine)
                Text(
                    "ROUTINE · ${routine.name.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                routine.days.forEach { d ->
                    val w = snap.savedWorkoutsById[d.workoutId]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.row)
                            .clickable(enabled = w != null, onClickLabel = "Choose ${d.name}") { chosen = w; chosenDay = d.id }
                            .padding(vertical = Spacing.sm)
                    ) {
                        Text(
                            d.name + if (d.id == next?.id) "  ·  NEXT" else "",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (d.id == next?.id) Brand.Gold else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            w?.let { "${it.name} · ${exerciseLine(snap, it)}" } ?: "No workout chosen for this day yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                GoldHairline()
                Text("SAVED WORKOUTS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            snap.savedWorkouts.forEach { w ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.row)
                        .clickable(onClickLabel = "Choose ${w.name}") { chosen = w; chosenDay = null }
                        .padding(vertical = Spacing.sm)
                ) {
                    Text(w.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${howMany(w.exercises.size, "exercise")} · ${exerciseLine(snap, w)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                GoldHairline()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.row)
                    .clickable(onClickLabel = "Build a new workout") { building = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Brand.Gold)
                Spacer(Modifier.width(Spacing.md))
                Text("Build a new workout", style = MaterialTheme.typography.titleMedium)
            }
            if (snap.savedWorkouts.isEmpty()) {
                TextButton(onClick = { onDismiss(); nav.push(Screen.SavedWorkouts) }) { Text("Create saved workouts") }
            }
        }
        return
    }

    ReviewWorkoutSheet(snap, nav, date, workout, replace, chosenDay, onBack = { chosen = null; chosenDay = null }, onDismiss = onDismiss)
}

/**
 * Starts [day] of a routine on [date] (#21): the same review as Add workout, then everything is logged and the day
 * remembers the routine day it was, for the next-day suggestion. [onLogged] runs once it's confirmed.
 */
@Composable
fun StartRoutineDaySheet(snap: Snapshot, nav: Nav, date: String, day: RoutineDay, onDismiss: () -> Unit, onLogged: () -> Unit) {
    val workout = snap.savedWorkoutsById[day.workoutId] ?: return
    ReviewWorkoutSheet(snap, nav, date, workout, replace = false, routineDayId = day.id, onBack = onDismiss, onDismiss = onDismiss, onLogged = onLogged)
}

/** The review step: every exercise with the sets it adds, ticked to start with. */
@Composable
private fun ReviewWorkoutSheet(
    snap: Snapshot,
    nav: Nav,
    date: String,
    workout: SavedWorkout,
    replace: Boolean,
    routineDayId: Long?,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onLogged: () -> Unit = {}
) {
    val resolved = remember(snap, workout, date) { workout.exercises.map { it to SavedWorkouts.resolve(snap, it, date) } }
    var ticked by remember(workout) { mutableStateOf(workout.exercises.indices.toSet()) }
    val isNew = workout.id == 0L
    var saveIt by remember(workout) { mutableStateOf(isNew) }
    val weekday = Dates.parse(date)?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "My"
    var newName by remember(workout) { mutableStateOf("$weekday workout") }
    val rows = resolved.filterIndexed { i, _ -> i in ticked }.flatMap { (p, sets) -> sets.map { p.exerciseId to it } }
    val toOpen = resolved.filterIndexed { i, (_, sets) -> i in ticked && sets.isEmpty() }.map { it.first.exerciseId }.distinct()

    FitSheet(
        title = if (isNew) "New workout" else workout.name,
        onDismiss = onDismiss,
        confirmLabel = when {
            rows.isNotEmpty() -> (if (replace) "Replace with " else "Add ") + howMany(rows.size, "set")
            else -> "Add exercises"
        },
        confirmEnabled = ticked.isNotEmpty() && (!isNew || !saveIt || newName.isNotBlank()),
        onConfirm = {
            onDismiss()
            val old = if (replace) snap.setsByDate[date].orEmpty() else emptyList<SetRow>()
            val toSave = if (isNew && saveIt) {
                workout.copy(name = newName, exercises = workout.exercises.filterIndexed { i, _ -> i in ticked })
            } else null
            AppScope.scope.launch {
                try {
                    if (replace && old.isNotEmpty()) Workouts.deleteHistory(date, date, emptySet())
                    // A workout saved on the spot is saved first, so the day can remember it (#21).
                    val savedId = if (toSave != null) SavedWorkouts.save(toSave) else workout.id
                    val ids = if (rows.isNotEmpty()) Workouts.logPlanned(date, rows, savedId, routineDayId) else emptyList()
                    val label = if (isNew) "Workout" else workout.name
                    UiEvents.show("$label added: ${howMany(ids.size, "set")}", "Undo") {
                        AppScope.scope.launch {
                            try {
                                Workouts.deleteSets(ids)
                                if (old.isNotEmpty()) Workouts.addSets(old)
                            } catch (e: Exception) {
                                UiEvents.show("Couldn't undo that: ${e.message}")
                            }
                        }
                    }
                } catch (e: WorkoutDataException) {
                    UiEvents.show(e.message ?: "That workout couldn't be added.")
                }
            }
            // Exercises with no sets to add open one after another, to be logged by hand.
            onLogged()
            if (toOpen.isNotEmpty()) nav.push(Screen.SetEntry(date, toOpen.first(), toOpen.drop(1)))
        }
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("‹ Choose another") }
        Text(
            (if (replace) "REPLACES THE WORKOUT ON " else "ADDS TO ") + Dates.long(date).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        resolved.forEachIndexed { i, (p, sets) ->
            val on = i in ticked
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.row)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { ticked = if (it) ticked + i else ticked - i }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = on, onCheckedChange = null)
                Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
                    Text(snap.exercises[p.exerciseId]?.name ?: "Exercise", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (sets.isEmpty()) "Nothing to repeat yet: it opens for you to log" else SavedWorkouts.describe(snap, sets),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (isNew) {
            GoldHairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.touch)
                    .toggleable(value = saveIt, role = Role.Checkbox, onValueChange = { saveIt = it }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = saveIt, onCheckedChange = null)
                Text("Save it for next time", Modifier.padding(start = Spacing.sm))
            }
            if (saveIt) {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it }, label = { Text("Workout name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (replace && snap.setsByDate[date].orEmpty().isNotEmpty()) {
            Text(
                "The ${howMany(snap.setsByDate[date].orEmpty().size, "set")} already on this day are removed first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Saving a logged day
// ---------------------------------------------------------------------------------------------------------

/** Saves the workout logged on [date] as a saved workout (#100), with these sets or "as last time". */
@Composable
fun SaveAsWorkoutSheet(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val weekday = Dates.parse(date)?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "My"
    var name by remember { mutableStateOf("$weekday workout") }
    var fill by remember { mutableStateOf(SavedWorkouts.FILL_PLANNED) }
    val exercises = remember(snap, date) { SavedWorkouts.fromDay(snap, date, SavedWorkouts.FILL_PLANNED) }

    FitSheet(
        title = "Save as a workout",
        onDismiss = onDismiss,
        confirmLabel = "Save workout",
        confirmEnabled = name.isNotBlank() && exercises.isNotEmpty(),
        onConfirm = {
            val w = SavedWorkout(0L, name, exercises = exercises.map { it.copy(fill = fill) })
            onDismiss()
            AppScope.scope.launch {
                try {
                    val id = SavedWorkouts.save(w)
                    UiEvents.show("Saved ${w.name.trim()}", "Undo") { AppScope.scope.launch { SavedWorkouts.delete(id) } }
                } catch (e: WorkoutDataException) {
                    UiEvents.show(e.message ?: "That workout couldn't be saved.")
                }
            }
        }
    ) {
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Workout name") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Text("NEXT TIME, EACH EXERCISE USES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SegmentedSwitch(
            options = listOf("These sets", "As last time"),
            selected = if (fill == SavedWorkouts.FILL_PLANNED) 0 else 1,
            onSelect = { fill = if (it == 0) SavedWorkouts.FILL_PLANNED else SavedWorkouts.FILL_LAST }
        )
        exercises.forEach { p ->
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                Text(snap.exercises[p.exerciseId]?.name ?: "Exercise", style = MaterialTheme.typography.bodyLarge)
                Text(SavedWorkouts.describe(snap, p.sets), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
