@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.PlannedExercise
import com.fitlens.companion.data.Routine
import com.fitlens.companion.data.RoutineDay
import com.fitlens.companion.data.Routines
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.PickerItem
import com.fitlens.companion.ui.design.SearchablePicker
import com.fitlens.companion.ui.design.SegmentedSwitch
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The day log's workout sheets (#100, merged into one model by #106): **Add workout** and **Replace this workout**
 * (one day of a workout, or exercises chosen on the spot), and **Save as a workout day**. Logging a day is
 * [logWorkoutDay], shared with the library's **Log all**.
 */

private fun howMany(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

/** A row of single-choice chips that scrolls sideways, for picking one of [options] (id to label). */
@Composable
private fun ChoiceChips(options: List<Pair<Long, String>>, selected: Long, onSelect: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { (id, label) ->
            FilterChip(selected = id == selected, onClick = { onSelect(id) }, label = { Text(label, maxLines = 1) })
        }
    }
}

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

/** A day's exercises as one line, for example "Bench Press, Squat and 3 more". */
fun exerciseLine(snap: Snapshot, exercises: List<PlannedExercise>): String {
    val names = exercises.map { snap.exercises[it.exerciseId]?.name ?: "Exercise" }
    return when {
        names.isEmpty() -> "No exercises yet"
        names.size <= 3 -> names.joinToString(", ")
        else -> names.take(2).joinToString(", ") + " and ${names.size - 2} more"
    }
}

private fun weekdayOf(date: String): String =
    Dates.parse(date)?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "Day 1"

/**
 * Logs [exercises] on [date] in one go, like FitNotes's **Log all** (#106), and offers Undo. [workoutId] and [dayId]
 * record where the date came from, for the next-day suggestion. [replace] first removes the date's sets. [saveAs], when
 * given, is saved first as a new workout and the date points at its first day. Returns the exercises with no sets to
 * add (nothing to copy yet, or "Don't populate any sets"), so the caller can open them to log by hand.
 */
fun logWorkoutDay(
    snap: Snapshot,
    date: String,
    label: String,
    exercises: List<PlannedExercise>,
    workoutId: Long,
    dayId: Long?,
    replace: Boolean = false,
    saveAs: Routine? = null
): List<Long> {
    val resolved = exercises.map { it to Routines.resolve(snap, it, date) }
    val rows = resolved.flatMap { (p, sets) -> sets.map { p.exerciseId to it } }
    val toOpen = resolved.filter { it.second.isEmpty() }.map { it.first.exerciseId }.distinct()
    val old = if (replace) snap.setsByDate[date].orEmpty() else emptyList<SetRow>()
    val oldComments = if (replace) snap.exerciseComments[date].orEmpty() else emptyMap()
    val groups = exercises.filter { it.superset > 0 }.associate { it.exerciseId to it.superset }
    // The day's prescribed rest (#138) is kept on the date, for the rest timer.
    val rests = exercises.associate { it.exerciseId to it.rest }.filterValues { !it.isEmpty }
    AppScope.scope.launch {
        try {
            if (replace && old.isNotEmpty()) Workouts.deleteHistory(date, date, emptySet())
            var wid = workoutId
            var did = dayId
            if (saveAs != null) {
                // Saved first, so the date can remember the new workout (#21).
                wid = Routines.save(saveAs)
                did = Store.snapshot.value?.routinesById?.get(wid)?.days?.firstOrNull()?.id
            }
            val ids = if (rows.isNotEmpty() || rests.isNotEmpty()) Workouts.logPlanned(date, rows, wid, did, groups, rests) else emptyList()
            UiEvents.show("$label added: ${howMany(ids.size, "set")}", "Undo") {
                AppScope.scope.launch {
                    try {
                        Workouts.deleteSets(ids)
                        if (old.isNotEmpty()) Workouts.addSets(old)
                        if (oldComments.isNotEmpty()) Workouts.setExerciseComments(date, oldComments)
                    } catch (e: Exception) {
                        UiEvents.show("Couldn't undo that: ${e.message}")
                    }
                }
            }
        } catch (e: WorkoutDataException) {
            UiEvents.show(e.message ?: "That workout couldn't be added.")
        }
    }
    return toOpen
}

// ---------------------------------------------------------------------------------------------------------
// Adding a workout to a day
// ---------------------------------------------------------------------------------------------------------

/** What Add workout is about to log: one day of a workout, or exercises chosen on the spot ([workout] null). */
private data class Choice(val workout: Routine?, val day: RoutineDay)

/**
 * Adds a whole workout day to [date] (#100, #106): a day of one of the user's workouts (the one the library shows
 * first, its next day marked), or exercises chosen on the spot, which can be saved as a new workout. A review lists
 * every exercise and the sets it adds; everything is logged at once, with Undo. [replace] first removes the date's
 * sets. Exercises with nothing to add are opened one after another, to be logged by hand.
 */
@Composable
fun AddWorkoutSheet(snap: Snapshot, nav: Nav, date: String, replace: Boolean, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf<Choice?>(null) }
    var building by remember { mutableStateOf(false) }
    val current = snap.routinesById[Settings.currentPortable().lastRoutineId]
    val ordered = listOfNotNull(current) + snap.routines.filter { it.id != current?.id }

    if (building) {
        SearchablePicker(
            title = "Choose exercises",
            items = exercisePickerItems(snap),
            multiSelect = true,
            searchLabel = "Search exercises",
            onDismiss = { building = false },
            onPick = { ids ->
                building = false
                chosen = Choice(null, RoutineDay(0L, weekdayOf(date), ids.map { PlannedExercise(it, Routines.FILL_LAST) }))
            }
        )
        return
    }

    val choice = chosen
    if (choice != null) {
        ReviewWorkoutSheet(snap, nav, date, choice, replace, onBack = { chosen = null }, onDismiss = onDismiss)
        return
    }

    FitSheet(title = if (replace) "Replace this workout" else "Add workout", onDismiss = onDismiss) {
        Text(
            if (replace) "The sets on ${Dates.medium(date)} are replaced by the workout day you choose. You can undo it."
            else "Choose a day from one of your workouts, or pick exercises for today.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ordered.forEach { r ->
            val next = Routines.nextDay(snap, r)
            Text(r.name.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            r.days.forEach { d ->
                val usable = d.exercises.isNotEmpty()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.row)
                        .clickable(enabled = usable, onClickLabel = "Choose ${d.name}") { chosen = Choice(r, d) }
                        .padding(vertical = Spacing.sm)
                ) {
                    Text(
                        d.name + if (d.id == next?.id) "  ·  NEXT" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (d.id == next?.id) Brand.Gold else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        exerciseLine(snap, d.exercises),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            GoldHairline()
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.row)
                .clickable(onClickLabel = "Choose exercises") { building = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Brand.Gold)
            Spacer(Modifier.width(Spacing.md))
            Text("Choose exercises", style = MaterialTheme.typography.titleMedium)
        }
        if (snap.routines.isEmpty()) {
            TextButton(onClick = { onDismiss(); nav.push(Screen.WorkoutEditor(0L)) }) { Text("Create a workout") }
        }
    }
}

/** The review step: every exercise with the sets it adds, ticked to start with. */
@Composable
private fun ReviewWorkoutSheet(
    snap: Snapshot,
    nav: Nav,
    date: String,
    choice: Choice,
    replace: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val exercises = choice.day.exercises
    val resolved = remember(snap, choice, date) { exercises.map { Routines.resolve(snap, it, date) } }
    var ticked by remember(choice) { mutableStateOf(exercises.indices.toSet()) }
    val isNew = choice.workout == null
    var saveIt by remember(choice) { mutableStateOf(false) }
    var newName by remember(choice) { mutableStateOf("${weekdayOf(date)} workout") }
    val setCount = resolved.filterIndexed { i, _ -> i in ticked }.sumOf { it.size }

    FitSheet(
        title = choice.workout?.let { "${it.name} · ${choice.day.name}" } ?: "Chosen exercises",
        onDismiss = onDismiss,
        confirmLabel = when {
            setCount > 0 -> (if (replace) "Replace with " else "Add ") + howMany(setCount, "set")
            else -> "Add exercises"
        },
        confirmEnabled = ticked.isNotEmpty() && (!saveIt || newName.isNotBlank()),
        onConfirm = {
            onDismiss()
            val picked = exercises.filterIndexed { i, _ -> i in ticked }
            val saveAs = if (isNew && saveIt) Routine(0L, newName, days = listOf(choice.day.copy(exercises = picked))) else null
            val toOpen = logWorkoutDay(
                snap, date, choice.workout?.name ?: "Workout", picked,
                workoutId = choice.workout?.id ?: 0L, dayId = choice.day.id.takeIf { it > 0L }, replace = replace, saveAs = saveAs
            )
            if (toOpen.isNotEmpty()) nav.push(Screen.SetEntry(date, toOpen.first(), toOpen.drop(1)))
        }
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("‹ Choose another") }
        Text(
            (if (replace) "REPLACES THE WORKOUT ON " else "ADDS TO ") + Dates.long(date).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        exercises.forEachIndexed { i, p ->
            val sets = resolved[i]
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
                        if (sets.isEmpty()) "No sets to add: it opens for you to log" else Routines.describe(snap, sets, p.exerciseId),
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
                Text("Save as a new workout", Modifier.padding(start = Spacing.sm))
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

/**
 * Saves the workout logged on [date] as a workout day (#100, #106): a new workout, a new day of an existing one, or
 * in place of an existing day's exercises. Each exercise keeps these sets as predefined sets or copies the previous
 * time. Everything can be undone.
 */
@Composable
fun SaveAsWorkoutSheet(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val weekday = weekdayOf(date)
    val exercises = remember(snap, date) { Routines.fromDate(snap, date, Routines.FILL_PLANNED) }
    var fill by remember { mutableStateOf(Routines.FILL_PLANNED) }
    // 0 saves a new workout; otherwise the workout the day joins, as a new day (dayId 0) or in place of a day.
    var routineId by remember { mutableStateOf(0L) }
    var dayId by remember { mutableStateOf(0L) }
    var name by remember { mutableStateOf("$weekday workout") }
    var dayName by remember { mutableStateOf(weekday) }
    val target = snap.routinesById[routineId]

    FitSheet(
        title = "Save as a workout day",
        onDismiss = onDismiss,
        confirmLabel = when {
            target == null -> "Save workout"
            dayId > 0L -> "Replace day"
            else -> "Add day"
        },
        confirmEnabled = exercises.isNotEmpty() && (target != null || name.isNotBlank()),
        onConfirm = {
            val planned = exercises.map { it.copy(fill = fill) }
            val into = target
            val intoDay = into?.days?.firstOrNull { it.id == dayId }
            val newName = name
            val newDayName = dayName
            onDismiss()
            AppScope.scope.launch {
                try {
                    when {
                        into == null -> {
                            val id = Routines.save(Routine(0L, newName, days = listOf(RoutineDay(0L, newDayName, planned))))
                            UiEvents.show("Saved ${newName.trim()}", "Undo") { AppScope.scope.launch { Routines.delete(id) } }
                        }
                        intoDay != null -> {
                            Routines.setDayExercises(intoDay.id, planned)
                            UiEvents.show("Replaced ${intoDay.name} in ${into.name}", "Undo") {
                                AppScope.scope.launch { Routines.setDayExercises(intoDay.id, intoDay.exercises) }
                            }
                        }
                        else -> {
                            val added = Routines.addDay(into.id, newDayName, planned)
                            UiEvents.show("Added $newDayName to ${into.name}", "Undo") { AppScope.scope.launch { Routines.deleteDay(added) } }
                        }
                    }
                } catch (e: WorkoutDataException) {
                    UiEvents.show(e.message ?: "That workout couldn't be saved.")
                }
            }
        }
    ) {
        if (snap.routines.isNotEmpty()) {
            Text("SAVE TO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceChips(listOf(0L to "New workout") + snap.routines.map { it.id to it.name }, routineId) { routineId = it; dayId = 0L }
            if (target != null) {
                ChoiceChips(listOf(0L to "As a new day") + target.days.map { it.id to "Instead of ${it.name}" }, dayId) { dayId = it }
            }
        }
        if (target == null) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Workout name") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
        if (dayId == 0L) {
            OutlinedTextField(
                value = dayName, onValueChange = { dayName = it }, label = { Text("Day name, for example Monday or Push Day") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
        Text("NEXT TIME, EACH EXERCISE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SegmentedSwitch(
            options = listOf("Uses these sets", "Copies previous"),
            selected = if (fill == Routines.FILL_PLANNED) 0 else 1,
            onSelect = { fill = if (it == 0) Routines.FILL_PLANNED else Routines.FILL_LAST }
        )
        exercises.forEach { p ->
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                Text(snap.exercises[p.exerciseId]?.name ?: "Exercise", style = MaterialTheme.typography.bodyLarge)
                Text(Routines.describe(snap, p.sets, p.exerciseId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
