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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.fitlens.companion.data.Routine
import com.fitlens.companion.data.RoutineDay
import com.fitlens.companion.data.Routines
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.TopBarAction
import kotlinx.coroutines.launch

/**
 * Routines (#21, screens #91): the list, and the editor for one routine's named days, each using a saved workout
 * (#100). Starting a day happens from the library's routine switcher and from Add workout on the day log.
 */

private fun daysOf(n: Int) = "$n day${if (n == 1) "" else "s"}"

/** The saved workout a routine day uses, as one line. */
private fun dayLine(snap: Snapshot, d: RoutineDay): String =
    snap.savedWorkoutsById[d.workoutId]?.let { w ->
        "${w.name} · ${w.exercises.size} exercise${if (w.exercises.size == 1) "" else "s"}"
    } ?: "No workout chosen yet"

// ---------------------------------------------------------------------------------------------------------
// The list
// ---------------------------------------------------------------------------------------------------------

@Composable
fun RoutinesScreen(snap: Snapshot, nav: Nav) {
    var deleting by remember { mutableStateOf<Routine?>(null) }
    Column(Modifier.fillMaxSize()) {
        PlainTopBar("Routines") {
            IconButton(onClick = { nav.push(Screen.RoutineEditor(0L)) }) {
                Icon(Icons.Filled.Add, contentDescription = "New routine")
            }
        }
        if (snap.routines.isEmpty()) {
            EmptyState(
                "No routines yet",
                "A routine is your saved workouts split into days you name, such as Push, Pull and Legs. FitLens then " +
                    "suggests the next day each time you train."
            ) {
                Button(onClick = { nav.push(Screen.RoutineEditor(0L)) }) { Text("Create a routine") }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                items(snap.routines, key = { it.id }) { r ->
                    val next = Routines.nextDay(snap, r)
                    ListRowWithMenu(
                        title = r.name,
                        subtitle = daysOf(r.days.size) + (next?.let { " · next: ${it.name}" } ?: ""),
                        onClick = { nav.push(Screen.RoutineEditor(r.id)) },
                        menu = listOf(
                            MenuAction("Edit") { nav.push(Screen.RoutineEditor(r.id)) },
                            MenuAction("Duplicate") {
                                AppScope.scope.launch {
                                    Routines.copy(r, "${r.name} (copy)")
                                    UiEvents.show("Duplicated ${r.name}")
                                }
                            },
                            MenuAction("Delete") { deleting = r }
                        )
                    )
                    GoldHairline()
                }
            }
        }
    }
    deleting?.let { r ->
        ConfirmSheet(
            title = "Delete ${r.name}?",
            message = "The routine and its days go. Its saved workouts, and every day you've logged, are kept.",
            confirmLabel = "Delete routine",
            onDismiss = { deleting = null },
            onConfirm = {
                AppScope.scope.launch {
                    Routines.delete(r.id)
                    UiEvents.show("Deleted ${r.name}", "Undo") { AppScope.scope.launch { Routines.copy(r, r.name) } }
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------------------------------------
// The editor
// ---------------------------------------------------------------------------------------------------------

private data class DaySlot(val key: Long, val day: RoutineDay)

/**
 * Creates ([id] 0) or edits a routine: its name, notes and days in order, each named by the user and using a saved
 * workout. Nothing is written until Save; Back with unsaved changes asks first.
 */
@Composable
fun RoutineEditorScreen(snap: Snapshot, nav: Nav, id: Long) {
    val original = remember(id) { snap.routinesById[id] ?: Routine(0L, "") }
    var name by remember(id) { mutableStateOf(original.name) }
    var notes by remember(id) { mutableStateOf(original.notes.orEmpty()) }
    var nextKey by remember(id) { mutableStateOf(original.days.size.toLong()) }
    val slots = remember(id) {
        mutableStateListOf<DaySlot>().apply { original.days.forEachIndexed { i, d -> add(DaySlot(i.toLong(), d)) } }
    }
    var editing by remember { mutableStateOf<DaySlot?>(null) }
    var copying by remember { mutableStateOf<RoutineDay?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    val draft = Routine(original.id, name, notes, original.sortOrder, slots.map { it.day })
    val dirty = draft.name != original.name || draft.notes.orEmpty() != original.notes.orEmpty() || draft.days != original.days

    fun save() {
        val r = draft
        if (r.name.isBlank()) {
            UiEvents.show("Enter a name for the routine.")
            return
        }
        nav.pop()
        AppScope.scope.launch {
            try {
                val saved = Routines.save(r)
                // A new routine becomes the one the library and Add workout show (#21).
                if (r.id == 0L) Settings.updatePortable { it.copy(lastRoutineId = saved) }
                UiEvents.show("Saved ${r.name.trim()}")
            } catch (e: WorkoutDataException) {
                UiEvents.show(e.message ?: "That routine couldn't be saved.")
            }
        }
    }
    fun move(from: Int, to: Int) {
        if (to in slots.indices) slots.add(to, slots.removeAt(from))
    }
    BackHandler(enabled = dirty) { confirmLeave = true }

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = if (id == 0L) "New routine" else "Edit routine",
            onBack = { if (dirty) confirmLeave = true else nav.pop() },
            actions = listOf(TopBarAction(Icons.Filled.Check, "Save routine", enabled = name.isNotBlank()) { save() })
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            item(key = "fields") {
                Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Name, for example Push Pull Legs") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SectionTitle("Days")
                if (slots.isEmpty()) {
                    Text(
                        "Add a day for each workout in the routine, in the order you do them.",
                        Modifier.padding(horizontal = Spacing.lg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            slots.forEachIndexed { i, slot ->
                item(key = slot.key) {
                    val others = snap.routines.filter { it.id != original.id }
                    ListRowWithMenu(
                        title = slot.day.name.ifBlank { "Day ${i + 1}" },
                        subtitle = dayLine(snap, slot.day),
                        onClick = { editing = slot },
                        menu = listOfNotNull(
                            MenuAction("Edit") { editing = slot },
                            if (others.isNotEmpty() && slot.day.workoutId > 0L) MenuAction("Copy to another routine") { copying = slot.day } else null,
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
                    onClick = { editing = DaySlot(-1L, RoutineDay(0L, "Day ${slots.size + 1}", 0L)) },
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md).heightIn(min = Spacing.touch)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Add day")
                }
            }
        }
        GoldHairline()
        Button(
            onClick = { save() },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md).heightIn(min = Spacing.row)
        ) { Text("Save routine") }
    }

    editing?.let { slot ->
        RoutineDaySheet(snap, nav, slot.day, onDismiss = { editing = null }) { day ->
            val at = slots.indexOfFirst { it.key == slot.key }
            if (at >= 0) slots[at] = slot.copy(day = day) else {
                slots.add(DaySlot(nextKey, day))
                nextKey += 1
            }
            editing = null
        }
    }
    copying?.let { day ->
        FitSheet(title = "Copy ${day.name} to", onDismiss = { copying = null }) {
            snap.routines.filter { it.id != original.id }.forEach { r ->
                Text(
                    r.name,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.row)
                        .clickable(onClickLabel = "Copy to ${r.name}") {
                            copying = null
                            AppScope.scope.launch {
                                Routines.addDay(r.id, day.name, day.workoutId)
                                UiEvents.show("Copied ${day.name} to ${r.name}")
                            }
                        }
                        .padding(vertical = Spacing.md),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
    if (confirmLeave) {
        ConfirmSheet(
            title = "Discard your changes?",
            message = "This routine has changes that haven't been saved.",
            confirmLabel = "Discard changes",
            onDismiss = { confirmLeave = false },
            onConfirm = { nav.pop() }
        )
    }
}

/** Names a routine day and chooses the saved workout it uses. */
@Composable
private fun RoutineDaySheet(snap: Snapshot, nav: Nav, initial: RoutineDay, onDismiss: () -> Unit, onDone: (RoutineDay) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var workoutId by remember { mutableStateOf(initial.workoutId) }
    FitSheet(
        title = if (initial.id == 0L && initial.workoutId == 0L) "New day" else "Edit day",
        onDismiss = onDismiss,
        confirmLabel = "Done",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onDone(initial.copy(name = name.trim(), workoutId = workoutId)) }
    ) {
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Day name, for example Push or Monday") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Text("WORKOUT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (snap.savedWorkouts.isEmpty()) {
            Text(
                "You haven't saved any workouts yet. Create them in Workouts, or save a day you've logged, then choose one here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { onDismiss(); nav.push(Screen.SavedWorkouts) }) { Text("Open Workouts") }
        }
        snap.savedWorkouts.forEach { w ->
            val on = w.id == workoutId
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.row)
                    .clickable(onClickLabel = "Use ${w.name}") { workoutId = w.id }
                    .semantics(mergeDescendants = true) { selected = on },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(w.name, style = MaterialTheme.typography.bodyLarge, color = if (on) Brand.Gold else MaterialTheme.colorScheme.onSurface)
                    Text(
                        w.exercises.mapNotNull { snap.exercises[it.exerciseId]?.name }.joinToString(", ").ifBlank { "No exercises yet" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = Brand.Gold)
            }
        }
    }
}
