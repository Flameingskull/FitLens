@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.OverflowMenu
import com.fitlens.companion.ui.design.StepperField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.PlannedExercise
import com.fitlens.companion.data.PlannedSet
import com.fitlens.companion.data.Routine
import com.fitlens.companion.data.RoutineDay
import com.fitlens.companion.data.Routines
import com.fitlens.companion.data.SetTypes
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.SearchablePicker
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SegmentedSwitch
import com.fitlens.companion.ui.design.TopBarAction
import com.fitlens.companion.ui.design.raisedGlass
import kotlinx.coroutines.launch

/**
 * The workout editor (#106, FitNotes's routine editor): a workout's name and notes, then its days as cards. Each day
 * has **+** (Add exercise) and a menu (rename, duplicate, move, copy to another workout, delete); each exercise row
 * opens how its sets are filled, and its menu handles supersets, swapping and removing. Nothing is written until
 * Save; Back with unsaved changes asks first.
 */

/** One exercise in the editor, with a stable [key] so moving keeps each row with its exercise. */
private data class Slot(val key: Long, val planned: PlannedExercise)

/** One day in the editor. [id] is the saved day's id (0 for a new day), so logged dates keep pointing at it. */
private data class DayDraft(val key: Long, val id: Long, val name: String, val slots: List<Slot>)

@Composable
fun WorkoutEditorScreen(snap: Snapshot, nav: Nav, id: Long) {
    val original = remember(id) { snap.routinesById[id] ?: Routine(0L, "", days = listOf(RoutineDay(0L, "Day 1"))) }
    var name by remember(id) { mutableStateOf(original.name) }
    var notes by remember(id) { mutableStateOf(original.notes.orEmpty()) }
    // Stable keys for days and rows; a plain counter, since handing one out needn't recompose anything.
    val keys = remember(id) { longArrayOf(0L) }
    fun key(): Long = keys[0]++
    val days = remember(id) {
        mutableStateListOf<DayDraft>().apply {
            original.days.forEach { d -> add(DayDraft(key(), d.id, d.name, d.exercises.map { Slot(key(), it) })) }
        }
    }
    var adding by remember { mutableStateOf<Long?>(null) }
    var editingSets by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var swapping by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var renaming by remember { mutableStateOf<Long?>(null) }
    var copying by remember { mutableStateOf<Long?>(null) }
    // The day whose "Set rest for every exercise" sheet is open (#138).
    var restingDay by remember { mutableStateOf<Long?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val draft = Routine(
        original.id, name, notes, original.sortOrder,
        days.mapIndexed { i, d -> RoutineDay(d.id, d.name.ifBlank { "Day ${i + 1}" }, d.slots.map { it.planned }) }
    )
    val dirty = draft.name != original.name || draft.notes.orEmpty() != original.notes.orEmpty() || draft.days != original.days

    fun updateDay(dayKey: Long, change: (DayDraft) -> DayDraft) {
        val at = days.indexOfFirst { it.key == dayKey }
        if (at >= 0) days[at] = change(days[at])
    }
    fun updateSlot(dayKey: Long, slotKey: Long, change: (PlannedExercise) -> PlannedExercise) = updateDay(dayKey) { d ->
        d.copy(slots = d.slots.map { if (it.key == slotKey) it.copy(planned = change(it.planned)) else it })
    }
    fun save() {
        val r = draft
        if (r.name.isBlank()) {
            UiEvents.show("Enter a name for the workout.")
            return
        }
        nav.pop()
        AppScope.scope.launch {
            try {
                val saved = Routines.save(r)
                // A new workout becomes the one the library shows (#21).
                if (r.id == 0L) Settings.updatePortable { it.copy(lastRoutineId = saved) }
                UiEvents.show("Saved ${r.name.trim()}")
            } catch (e: WorkoutDataException) {
                UiEvents.show(e.message ?: "That workout couldn't be saved.")
            }
        }
    }
    BackHandler(enabled = dirty) { confirmLeave = true }

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = if (id == 0L) "New workout" else "Edit workout",
            onBack = { if (dirty) confirmLeave = true else nav.pop() },
            actions = listOf(TopBarAction(Icons.Filled.Check, "Save workout", enabled = name.isNotBlank()) { save() }),
            overflow = if (original.id == 0L) emptyList() else listOf(
                MenuAction("Duplicate workout") {
                    AppScope.scope.launch {
                        Routines.copy(original, "${original.name} (copy)")
                        UiEvents.show("Duplicated ${original.name}")
                    }
                },
                MenuAction("Delete workout") { confirmDelete = true }
            )
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
            }
            days.forEachIndexed { di, day ->
                item(key = "day-${day.key}") {
                    DayCard(
                        snap = snap,
                        day = day,
                        label = day.name.ifBlank { "Day ${di + 1}" },
                        canCopy = snap.routines.any { it.id != original.id },
                        onAdd = { adding = day.key },
                        onRename = { renaming = day.key },
                        onDuplicate = {
                            days.add(di + 1, day.copy(key = key(), id = 0L, name = "${day.name} (copy)", slots = day.slots.map { Slot(key(), it.planned) }))
                        },
                        onMoveUp = if (di > 0) ({ days.add(di - 1, days.removeAt(di)) }) else null,
                        onMoveDown = if (di < days.lastIndex) ({ days.add(di + 1, days.removeAt(di)) }) else null,
                        onCopy = { copying = day.key },
                        onRest = { restingDay = day.key },
                        onDelete = { days.removeAt(di) },
                        onSets = { editingSets = day.key to it },
                        onSwap = { swapping = day.key to it },
                        onChange = { slots -> updateDay(day.key) { it.copy(slots = slots) } }
                    )
                }
            }
            item(key = "add-day") {
                // FitNotes's "TAP TO CREATE A NEW DAY" bar under the day cards (#143, its screenshots 26 and 27).
                Text(
                    "TAP TO CREATE A NEW DAY",
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        .raisedGlass(FitShapes.row, elevation = 2.dp, inset = 6.dp)
                        .clickable(onClickLabel = "Create a new day") { days.add(DayDraft(key(), 0L, "Day ${days.size + 1}", emptyList())) }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        GoldHairline()
        GoldButton(
            onClick = { save() },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md).heightIn(min = Spacing.row)
        ) { Text("Save workout") }
    }

    adding?.let { dayKey ->
        SearchablePicker(
            title = "Add exercise",
            items = exercisePickerItems(snap),
            multiSelect = true,
            searchLabel = "Search exercises",
            onDismiss = { adding = null },
            onPick = { ids ->
                adding = null
                val added = ids.map { Slot(key(), PlannedExercise(it, Routines.FILL_LAST)) }
                updateDay(dayKey) { it.copy(slots = it.slots + added) }
                // One exercise at a time asks straight away how its sets are filled, as FitNotes does.
                added.singleOrNull()?.let { editingSets = dayKey to it.key }
            }
        )
    }
    fun slotAt(at: Pair<Long, Long>?): Slot? =
        at?.let { (dayKey, slotKey) -> days.firstOrNull { it.key == dayKey }?.slots?.firstOrNull { it.key == slotKey } }
    val swapAt = swapping
    val swapSlot = slotAt(swapAt)
    if (swapAt != null && swapSlot != null) {
        val (dayKey, slotKey) = swapAt
        val current: Slot = swapSlot
        SearchablePicker(
            title = "Swap ${snap.exercises[current.planned.exerciseId]?.name ?: "exercise"} for",
            items = exercisePickerItems(snap).filter { it.id != current.planned.exerciseId },
            onDismiss = { swapping = null },
            onPick = { ids ->
                swapping = null
                ids.firstOrNull()?.let { to -> updateSlot(dayKey, slotKey) { it.copy(exerciseId = to) } }
            }
        )
    }
    val setsAt = editingSets
    val setsSlot = slotAt(setsAt)
    if (setsAt != null && setsSlot != null) {
        val (dayKey, slotKey) = setsAt
        PlannedSetsSheet(snap, setsSlot.planned, onDismiss = { editingSets = null }) { updated ->
            updateSlot(dayKey, slotKey) { updated }
            editingSets = null
        }
    }
    renaming?.let { dayKey ->
        val day = days.firstOrNull { it.key == dayKey }
        var dayName by remember(dayKey) { mutableStateOf(day?.name.orEmpty()) }
        FitSheet(
            title = "Rename day",
            onDismiss = { renaming = null },
            confirmLabel = "Rename day",
            confirmEnabled = dayName.isNotBlank(),
            onConfirm = {
                updateDay(dayKey) { it.copy(name = dayName.trim()) }
                renaming = null
            }
        ) {
            OutlinedTextField(
                value = dayName, onValueChange = { dayName = it }, label = { Text("Day name, for example Monday or Push Day") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
    }
    val restDay = restingDay?.let { dayKey -> days.firstOrNull { it.key == dayKey } }
    if (restDay != null) {
        val day: DayDraft = restDay
        DayRestSheet(day.name.ifBlank { "this day" }, onDismiss = { restingDay = null }) { rest, after ->
            // One rest for every exercise of the day (#138): it replaces each exercise's and each set's own.
            updateDay(day.key) { d ->
                d.copy(slots = d.slots.map { s ->
                    s.copy(planned = s.planned.copy(restSeconds = rest, restAfterSeconds = after, sets = s.planned.sets.map { it.copy(restSeconds = null) }))
                })
            }
            restingDay = null
        }
    }
    val copyDay = copying?.let { dayKey -> days.firstOrNull { it.key == dayKey } }
    if (copyDay != null) {
        val day: DayDraft = copyDay
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
                                val added = Routines.addDay(r.id, day.name, day.slots.map { it.planned })
                                UiEvents.show("Copied ${day.name} to ${r.name}", "Undo") {
                                    AppScope.scope.launch { Routines.deleteDay(added) }
                                }
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
            message = "This workout has changes that haven't been saved.",
            confirmLabel = "Discard changes",
            onDismiss = { confirmLeave = false },
            onConfirm = { nav.pop() }
        )
    }
    if (confirmDelete) {
        ConfirmSheet(
            title = "Delete ${original.name}?",
            message = "The workout and its days go. Every day you've already logged keeps its sets.",
            confirmLabel = "Delete workout",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                nav.pop()
                AppScope.scope.launch {
                    Routines.delete(original.id)
                    UiEvents.show("Deleted ${original.name}", "Undo") {
                        AppScope.scope.launch { Routines.copy(original, original.name) }
                    }
                }
            }
        )
    }
}

/** One day as a card: its name with + and a menu, then its exercises in order. */
@Composable
private fun DayCard(
    snap: Snapshot,
    day: DayDraft,
    label: String,
    canCopy: Boolean,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onCopy: () -> Unit,
    onRest: () -> Unit,
    onDelete: () -> Unit,
    onSets: (Long) -> Unit,
    onSwap: (Long) -> Unit,
    onChange: (List<Slot>) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val slots = day.slots
    // FitNotes's day card (#143, its screenshots 26 and 33): the day's name with + and ⋮ over a rule, then each
    // exercise with how its sets are filled, its ⋮ and a drag handle.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .raisedGlass(FitShapes.card)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add exercise to $label") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for $label") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOfNotNull(
                        MenuAction("Rename day", onClick = onRename),
                        MenuAction("Duplicate day", onClick = onDuplicate),
                        onMoveUp?.let { MenuAction("Move up", onClick = it) },
                        onMoveDown?.let { MenuAction("Move down", onClick = it) },
                        if (canCopy && slots.isNotEmpty()) MenuAction("Copy to another workout", onClick = onCopy) else null,
                        if (slots.isNotEmpty()) MenuAction("Set rest for every exercise", onClick = onRest) else null,
                        MenuAction("Delete day", onClick = onDelete)
                    ).forEach { a ->
                        DropdownMenuItem(text = { Text(a.label) }, onClick = { menu = false; a.onClick() })
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brand.Gold.copy(alpha = 0.7f)))
        if (slots.isEmpty()) {
            Text(
                "You haven't added any exercises yet",
                Modifier.padding(Spacing.lg),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val groups = slots.map { it.planned.superset }.filter { it > 0 }.distinct()
        slots.forEachIndexed { i, slot ->
            fun withPlanned(at: Int, p: PlannedExercise) = slots.toMutableList().also { it[at] = slots[at].copy(planned = p) }
            ListRowWithMenu(
                title = snap.exercises[slot.planned.exerciseId]?.name ?: "Exercise",
                subtitle = planSummary(snap, slot.planned) +
                    (if (slot.planned.superset > 0) "  ·  Superset ${'A' + groups.indexOf(slot.planned.superset)}" else "") +
                    // Its prescribed rest on a line of its own, "Rest 90 s · then 2 min" (#138).
                    (restSummary(slot.planned)?.let { "\n$it" } ?: ""),
                leading = { Dot(categoryColour(snap.categoryOf(slot.planned.exerciseId)?.colour ?: 0), Spacing.md) },
                onClick = { onSets(slot.key) },
                menu = listOf(
                    MenuAction("Sets") { onSets(slot.key) },
                    MenuAction("Superset with the next exercise", enabled = i < slots.lastIndex) {
                        val next = slots[i + 1].planned
                        val g = slot.planned.superset.takeIf { it > 0 } ?: next.superset.takeIf { it > 0 }
                            ?: ((slots.maxOfOrNull { it.planned.superset } ?: 0) + 1)
                        onChange(withPlanned(i, slot.planned.copy(superset = g)).also { it[i + 1] = it[i + 1].copy(planned = next.copy(superset = g)) })
                    },
                    MenuAction("Remove from superset", enabled = slot.planned.superset > 0) {
                        val g = slot.planned.superset
                        val out = withPlanned(i, slot.planned.copy(superset = 0))
                        // A group left with one exercise dissolves.
                        if (out.count { it.planned.superset == g } < 2) {
                            out.indices.filter { out[it].planned.superset == g }
                                .forEach { j -> out[j] = out[j].copy(planned = out[j].planned.copy(superset = 0)) }
                        }
                        onChange(out)
                    },
                    MenuAction("Swap exercise") { onSwap(slot.key) },
                    MenuAction("Remove") { onChange(slots.filter { it.key != slot.key }) }
                ),
                onMoveUp = if (i > 0) ({ onChange(slots.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }) else null,
                onMoveDown = if (i < slots.lastIndex) ({ onChange(slots.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }) else null
            )
        }
    }
}

/** How an exercise's sets read in the editor and on the library's day cards. Its rest is [restSummary] (#138). */
fun planSummary(snap: Snapshot, p: PlannedExercise): String {
    return when (p.fill) {
        Routines.FILL_NONE -> "No sets: log them as you go"
        Routines.FILL_PLANNED -> Routines.describe(snap, p.sets.filter { !it.isEmpty }, p.exerciseId)
        else -> {
            val last = Routines.resolve(snap, p, "9999-12-31")
            if (last.isEmpty()) "Copy previous sets · not logged yet" else "Copy previous sets · ${Routines.describe(snap, last, p.exerciseId)}"
        }
    }
}

/** A rest length as the editor shows it: "90 s", "2 min", "2 min 30 s" (#138). */
fun restLabel(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}

/**
 * An exercise's prescribed rest in one line (#138), "Rest 90 s · then 2 min": the rest between its sets (one length,
 * or "varies" when its sets each have their own), then the rest before the next exercise. Null when none is set.
 */
fun restSummary(p: PlannedExercise): String? {
    // "Copy previous rest" (#151) is one choice for the whole exercise.
    if (Routines.copiesRest(p)) return "Copy previous rest"
    val setRests = if (p.fill == Routines.FILL_PLANNED) p.sets.mapNotNull { it.restSeconds }.distinct() else emptyList()
    val between = when {
        setRests.size > 1 -> "Rest varies"
        setRests.size == 1 -> "Rest ${restLabel(setRests[0])}"
        p.restSeconds != null -> "Rest ${restLabel(p.restSeconds)}"
        else -> null
    }
    val after = p.restAfterSeconds?.let { "then ${restLabel(it)}" }
    return listOfNotNull(between, after).joinToString(" · ").ifEmpty { null }
}

// ---------------------------------------------------------------------------------------------------------
// One exercise's sets
// ---------------------------------------------------------------------------------------------------------

/** A prescribed set as the user types it, in their own units. */
private data class SetDraft(
    val weight: String = "",
    val reps: String = "",
    val distance: String = "",
    val time: String = "",
    val type: Int = SetTypes.WORKING,
    /** The rest after this set in seconds, or m:ss; blank for none of its own (#138). */
    val rest: String = "",
    /** The custom type's metric (#155); blank copies it from last time. */
    val metric: String = ""
)

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
 * Chooses how an exercise's sets are filled when its day is logged, as FitNotes asks (#106): **Copy previous sets**,
 * **Use predefined sets** (a list of sets; a blank weight or reps copies it from last time), or **Don't populate any
 * sets**. The fields follow the exercise's type, as on the Track tab.
 *
 * Below them, its prescribed rest (#138): one rest for every set ("Same rest for every set", the only choice unless
 * its sets are predefined) or a rest per set, and the rest before the next exercise. "Default" leaves either one
 * unset, so the exercise's own rest and then the global one apply.
 */
@Composable
private fun PlannedSetsSheet(snap: Snapshot, planned: PlannedExercise, onDismiss: () -> Unit, onDone: (PlannedExercise) -> Unit) {
    val ex = snap.exercises[planned.exerciseId]
    val type = ex?.type ?: ExerciseTypes.WEIGHT_REPS
    // A user-defined type's own metric (#14), predefined like the other values (#155).
    val metricDef = ExerciseTypes.metricOf(type)
    val last = remember(snap, planned.exerciseId) {
        Routines.resolve(snap, planned.copy(fill = Routines.FILL_LAST), "9999-12-31")
    }
    var fill by remember { mutableStateOf(planned.fill) }
    val start = planned.sets.ifEmpty { last }.ifEmpty { listOf(PlannedSet()) }
    val rows = remember {
        mutableStateListOf<SetDraft>().apply {
            start.forEach { s ->
                add(
                    SetDraft(
                        weight = s.weightKg.takeIf { it != 0.0 }?.let { fmtNum(snap.weight(it, planned.exerciseId), 2) } ?: "",
                        reps = s.reps.takeIf { it > 0 }?.toString() ?: "",
                        distance = s.distance.takeIf { it > 0 }?.let { fmtNum(it, 2) } ?: "",
                        time = s.durationSec.takeIf { it > 0 }?.let { fmtDuration(it) } ?: "",
                        type = s.setType,
                        rest = s.restSeconds?.toString() ?: "",
                        metric = s.metric?.let { fmtNum(it, 2) } ?: ""
                    )
                )
            }
        }
    }
    val modes = listOf(Routines.FILL_LAST, Routines.FILL_PLANNED, Routines.FILL_NONE)
    // Prescribed rest (#138). Per-set rests only exist for predefined sets.
    // "Copy previous rest" (#151) takes every rest from the exercise's most recent workout, as "Copy previous sets"
    // does for its sets.
    var copyRest by remember { mutableStateOf(Routines.copiesRest(planned)) }
    var sameRest by remember { mutableStateOf(planned.sets.none { it.restSeconds != null }) }
    var rest by remember { mutableStateOf(planned.restSeconds?.takeIf { it > 0 }) }
    var restAfter by remember { mutableStateOf(planned.restAfterSeconds?.takeIf { it > 0 }) }
    val fallbackRest = ex?.restSeconds ?: Settings.currentPortable().restSeconds
    val perSet = fill == Routines.FILL_PLANNED && !sameRest && !copyRest
    // What "Copy previous rest" would copy today, in the same words as the exercise row.
    val previousRest = remember(snap, planned.exerciseId) {
        val copying = planned.copy(fill = Routines.FILL_LAST, restSeconds = Routines.REST_PREVIOUS, restAfterSeconds = Routines.REST_PREVIOUS)
        val r = Routines.resolveRest(snap, copying, "9999-12-31")
        restSummary(
            PlannedExercise(
                planned.exerciseId, Routines.FILL_PLANNED, Routines.resolve(snap, copying, "9999-12-31"),
                restSeconds = r.restSeconds, restAfterSeconds = r.restAfterSeconds
            )
        )
    }

    FitSheet(
        title = ex?.name ?: "Sets",
        onDismiss = onDismiss,
        confirmLabel = "Done",
        onConfirm = {
            // Set type rides along with each row; a row counts when it has any value.
            val sets = rows.map {
                PlannedSet(
                    snap.toKg(num(it.weight), planned.exerciseId), it.reps.trim().toIntOrNull() ?: 0, num(it.distance), seconds(it.time), it.type,
                    restSeconds = if (perSet) seconds(it.rest).takeIf { r -> r in REST_MIN..REST_MAX } else null,
                    // Blank is no value; 0 is a real one for a custom metric (#155).
                    metric = if (metricDef != null) it.metric.trim().replace(',', '.').toDoubleOrNull() else null
                )
            }.filter { !it.isEmpty }
            onDone(
                planned.copy(
                    fill = fill,
                    sets = if (fill == Routines.FILL_PLANNED) sets else planned.sets.map { it.copy(restSeconds = null) },
                    restSeconds = if (copyRest) Routines.REST_PREVIOUS else if (perSet) null else rest,
                    restAfterSeconds = if (copyRest) Routines.REST_PREVIOUS else restAfter
                )
            )
        }
    ) {
        Text(
            "How would you like the sets for this exercise to be populated?",
            style = MaterialTheme.typography.bodyLarge
        )
        FillChoice(
            "Copy previous sets",
            "Automatically copy sets from the exercise's most recent workout" +
                if (last.isEmpty()) ". It hasn't been logged yet, so there's nothing to copy yet." else ": ${Routines.describe(snap, last, planned.exerciseId)}.",
            fill == Routines.FILL_LAST
        ) { fill = Routines.FILL_LAST }
        FillChoice("Use predefined sets", "Define exactly how sets should be populated in each workout", fill == Routines.FILL_PLANNED) {
            fill = Routines.FILL_PLANNED
        }
        FillChoice("Don't populate any sets", "Record sets for this exercise on-the-fly during each workout", fill == Routines.FILL_NONE) {
            fill = Routines.FILL_NONE
        }
        if (fill == Routines.FILL_PLANNED) {
            Text(
                "Leave a field blank if you want the value of that set to automatically copy between workouts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val unit = snap.weightUnitOf(planned.exerciseId)
            val wStep = ex?.weightStepKg?.let { snap.weight(it, planned.exerciseId) } ?: 2.5
            rows.forEachIndexed { i, r ->
                // FitNotes's SET N block (its screenshot 20): the set's number and ⋮, then a stepper per field.
                Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text("SET ${i + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    OverflowMenu(
                        listOf(
                            MenuAction("Duplicate set") { rows.add(i + 1, r) },
                            MenuAction("Remove set", enabled = rows.size > 1) { rows.removeAt(i) }
                        ),
                        description = "Options for set ${i + 1}"
                    )
                }
                HorizontalDivider(color = Brand.Hairline)
                fun step(text: String, by: Double, digits: Int) = fmtNum(kotlin.math.max(0.0, num(text) + by), digits).let { if (it == "0") "" else it }
                if (ExerciseTypes.usesWeight(type)) {
                    StepperField("Weight ($unit)", r.weight, { rows[i] = rows[i].copy(weight = it) }, { d -> rows[i] = rows[i].copy(weight = step(rows[i].weight, d * wStep, 2)) })
                }
                if (ExerciseTypes.usesReps(type)) {
                    StepperField("Reps", r.reps, { rows[i] = rows[i].copy(reps = it) }, { d -> rows[i] = rows[i].copy(reps = step(rows[i].reps, d.toDouble(), 0)) }, keyboard = KeyboardType.Number)
                }
                if (ExerciseTypes.usesDistance(type)) {
                    StepperField("Distance (${snap.distanceUnit(planned.exerciseId)})", r.distance, { rows[i] = rows[i].copy(distance = it) }, { d -> rows[i] = rows[i].copy(distance = step(rows[i].distance, d * 0.5, 2)) })
                }
                if (ExerciseTypes.usesDuration(type)) {
                    StepperField("Time (m:ss)", r.time, { rows[i] = rows[i].copy(time = it) }, { d ->
                        val next = kotlin.math.max(0, seconds(rows[i].time) + d * 15)
                        rows[i] = rows[i].copy(time = if (next == 0) "" else fmtDuration(next))
                    }, keyboard = KeyboardType.Text)
                }
                if (metricDef != null) {
                    // As on the Track tab: the metric's name and unit, stepping by 1.
                    val metricUnit = metricDef.metricUnit?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    StepperField(metricDef.metricName.orEmpty() + metricUnit, r.metric, { rows[i] = rows[i].copy(metric = it) }, { d ->
                        rows[i] = rows[i].copy(metric = fmtNum(kotlin.math.max(0.0, num(rows[i].metric) + d), 2))
                    })
                }
                if (perSet) {
                    StepperField("Rest (seconds)", r.rest, { t -> rows[i] = rows[i].copy(rest = t.filter { c -> c.isDigit() }) }, { d ->
                        rows[i] = rows[i].copy(rest = (kotlin.math.max(0, (rows[i].rest.toIntOrNull() ?: 0) + d * 5)).takeIf { it > 0 }?.toString() ?: "")
                    }, keyboard = KeyboardType.Number)
                }
            }
            // FitNotes's ADD SET + row.
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.touch)
                    .clickable(onClickLabel = "Add set") { rows.add(rows.lastOrNull() ?: SetDraft()) }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ADD SET", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(Icons.Filled.Add, contentDescription = null, tint = Brand.Gold)
            }
        }
        SectionLabel("Rest", Modifier.padding(top = Spacing.md))
        // The same choice as the sets above (#151): copy last time's rest, or set it here.
        FillChoice(
            "Copy previous rest",
            "Automatically copy the rest from the exercise's most recent workout" +
                (previousRest?.let { ": ${it.replaceFirstChar { c -> c.lowercase() }}." }
                    ?: ". It had no rest of its own, so its usual rest of ${restLabel(fallbackRest)} applies."),
            copyRest
        ) { copyRest = true }
        FillChoice("Set the rest", "Choose the rest for this exercise in every workout", !copyRest) { copyRest = false }
        if (!copyRest) {
            if (fill == Routines.FILL_PLANNED) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Same rest for every set", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = sameRest, onCheckedChange = { sameRest = it })
                }
            }
            if (perSet) {
                Text(
                    "Each set's rest is in the rest column above. A blank one uses ${restLabel(fallbackRest)}, this exercise's usual rest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                RestLengthStepper(
                    seconds = rest ?: fallbackRest,
                    onChange = { rest = it },
                    label = "Between sets (seconds)",
                    isDefault = rest == null,
                    onDefault = { rest = null }
                )
            }
            RestLengthStepper(
                seconds = restAfter ?: rest ?: fallbackRest,
                onChange = { restAfter = it },
                label = "Before the next exercise (seconds)",
                isDefault = restAfter == null,
                onDefault = { restAfter = null }
            )
        }
    }
}

/**
 * "Set rest for every exercise" on a day's menu (#138): one rest between sets and one before the next exercise, for
 * every exercise of the day. "Default" leaves either unset; "Copy previous rest" (#151) copies each one's last rest.
 */
@Composable
private fun DayRestSheet(dayName: String, onDismiss: () -> Unit, onDone: (Int?, Int?) -> Unit) {
    val fallback = Settings.currentPortable().restSeconds
    var rest by remember { mutableStateOf<Int?>(null) }
    var after by remember { mutableStateOf<Int?>(null) }
    // "Copy previous rest" (#151) for the whole day: each exercise takes its rest from its own last workout.
    var copyRest by remember { mutableStateOf(false) }
    FitSheet(
        title = "Rest for $dayName",
        onDismiss = onDismiss,
        confirmLabel = "Set for every exercise",
        onConfirm = { if (copyRest) onDone(Routines.REST_PREVIOUS, Routines.REST_PREVIOUS) else onDone(rest, after) }
    ) {
        Text(
            "Replaces the rest set on each exercise and set of this day. Default uses each exercise's usual rest.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FillChoice(
            "Copy previous rest",
            "Each exercise copies the rest from its most recent workout",
            copyRest
        ) { copyRest = true }
        FillChoice("Set the rest", "One rest for every exercise of this day", !copyRest) { copyRest = false }
        if (!copyRest) {
            RestLengthStepper(
                seconds = rest ?: fallback,
                onChange = { rest = it },
                label = "Between sets (seconds)",
                isDefault = rest == null,
                onDefault = { rest = null }
            )
            RestLengthStepper(
                seconds = after ?: rest ?: fallback,
                onChange = { after = it },
                label = "Before the next exercise (seconds)",
                isDefault = after == null,
                onDefault = { after = null }
            )
        }
    }
}

/** One of FitNotes's fill choices (its screenshot 23): a radio button, the choice and what it does. */
@Composable
private fun FillChoice(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
