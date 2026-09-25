package com.fitlens.companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import com.fitlens.companion.ui.design.DayNavigator
import com.fitlens.companion.ui.design.SetRow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(snap: Snapshot, nav: Nav, date: String) {
    val photos = snap.photosByDate[date] ?: emptyList()
    val records = snap.recordsByDate[date] ?: emptyList()
    val sets = snap.setsByDate[date] ?: emptyList()
    // The nearest days with data either side. Compared by value rather than by position, so a day picked from the
    // calendar that has nothing on it yet can still step to its neighbours. allDates is newest first.
    val older = snap.allDates.firstOrNull { it < date }
    val newer = snap.allDates.lastOrNull { it > date }
    var addMeasurement by remember { mutableStateOf(false) }
    var deleteRecord by remember { mutableStateOf<MRecord?>(null) }
    val importForDay = rememberPhotoImporter(forcedDate = date)
    // Workout editing (#10)
    var menu by remember { mutableStateOf(false) }
    var pickExercise by remember { mutableStateOf(false) }
    var editComment by remember { mutableStateOf(false) }
    var copyPrevious by remember { mutableStateOf(false) }
    var copyToDay by remember { mutableStateOf(false) }
    var moveToDay by remember { mutableStateOf(false) }
    var deleteWorkout by remember { mutableStateOf(false) }
    val hasWorkout = sets.isNotEmpty() || snap.workoutComments.containsKey(date)

    fun go(d: String) { nav.stack[nav.stack.lastIndex] = Screen.Day(d) }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Day", onBack = { nav.pop() }) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Workout options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Add exercise") },
                        onClick = { menu = false; pickExercise = true }
                    )
                    DropdownMenuItem(
                        text = { Text(if (snap.workoutComments.containsKey(date)) "Edit workout comment" else "Add workout comment") },
                        onClick = { menu = false; editComment = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy a previous workout here") },
                        onClick = { menu = false; copyPrevious = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy this workout to another day") },
                        onClick = { menu = false; copyToDay = true },
                        enabled = sets.isNotEmpty()
                    )
                    DropdownMenuItem(
                        text = { Text("Move this workout to another day") },
                        onClick = { menu = false; moveToDay = true },
                        enabled = hasWorkout
                    )
                    DropdownMenuItem(
                        text = { Text("Delete this workout") },
                        onClick = { menu = false; deleteWorkout = true },
                        enabled = hasWorkout
                    )
                    DropdownMenuItem(
                        text = { Text("Exercise library") },
                        onClick = { menu = false; nav.push(Screen.Library) }
                    )
                }
            }
        }
        DayNavigator(
            date = date,
            onPrevious = if (older != null) ({ go(older) }) else null,
            onNext = if (newer != null) ({ go(newer) }) else null,
            onPickDate = { d -> if (d != date) go(d) },
            onToday = { val today = Dates.today(); if (today != date) go(today) },
            previousDescription = "Previous day with data",
            nextDescription = "Next day with data"
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            // ---------- Photos ----------
            item { SectionTitle("Progress photos") }
            if (photos.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(photos, key = { _, p -> p.id }) { i, p ->
                            Column {
                                PhotoThumb(
                                    snap, p,
                                    Modifier.height(300.dp).width(225.dp).clickable {
                                        nav.push(Screen.PhotoViewer(photos.map { it.id }, i))
                                    },
                                    sizePx = 900
                                )
                                Text(
                                    listOfNotNull(p.pose.ifBlank { null }, p.takenAt?.let { formatTime(it) }).joinToString(" · ").ifBlank { " " },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    val nearest = remember(snap, date) { nearestPhoto(snap, date) }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("No photo on this date.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val nd = nearest?.date
                        if (nearest != null && nd != null) {
                            val days = Dates.epochDay(nd) - Dates.epochDay(date)
                            Row(
                                Modifier.padding(top = 8.dp).clickable { nav.push(Screen.Day(nd)) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PhotoThumb(snap, nearest, Modifier.height(96.dp).width(72.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Nearest photo: ${abs(days)} day${if (abs(days) == 1L) "" else "s"} ${if (days < 0) "earlier" else "later"}\n${Dates.medium(nd)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = importForDay, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add photos to this day")
                }
            }

            // ---------- Body ----------
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); SectionTitle("Body tracker") }
            if (records.isEmpty()) {
                item { Text("No measurements on this date.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            records.sortedWith(compareBy({ defOrder(snap, it.name) }, { it.time })).forEach { r ->
                item(key = "r${r.id}") {
                    val prev = remember(snap, r.id) {
                        snap.recordsByName[r.name]?.lastOrNull { it.date < r.date }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge)
                            val sub = listOfNotNull(
                                r.time.take(5).ifBlank { null },
                                prev?.let { "${fmtSigned(r.value - it.value)} since ${Dates.short(it.date)}" },
                                if (r.source == "manual") "added in FitLens" else null
                            ).joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!r.comment.isNullOrBlank()) Text("“${r.comment}”", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${fmtNum(r.value)} ${r.unit}", style = MaterialTheme.typography.titleMedium)
                        if (r.source == "manual") {
                            IconButton(onClick = { deleteRecord = r }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                        } else Spacer(Modifier.width(12.dp))
                    }
                }
            }
            item {
                OutlinedButton(onClick = { addMeasurement = true }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add measurement")
                }
            }

            // ---------- Workout ----------
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); SectionTitle("Workout") }
            if (hasWorkout) {
                item {
                    val times = snap.workoutTimes[date]
                    val total = times?.sumOf { workoutSeconds(it.start, it.end) } ?: 0L
                    val info = listOfNotNull(
                        if (total > 0) "Duration ${fmtDuration(total.toInt())}" else null,
                        "${sets.size} sets",
                        "Volume ${fmtNum(snap.weight(sets.sumOf { it.weightKg * it.reps }), 0)} ${snap.weightUnit}"
                    ).joinToString(" · ")
                    Text(info, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    snap.workoutComments[date]?.forEach {
                        Text(
                            "“$it”",
                            Modifier.fillMaxWidth().clickable { editComment = true }.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                item {
                    Text(
                        "Nothing logged on this day yet.",
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            run {
                val byExercise = sets.groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.id } }
                byExercise.forEach { (exId, exSets) ->
                    item(key = "e$exId") {
                        val ex = snap.exercises[exId]
                        val cat = snap.categoryOf(exId)
                        Column(
                            Modifier.fillMaxWidth().clickable { nav.push(Screen.SetEntry(date, exId)) }.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Dot(categoryColour(cat?.colour ?: 0), 10.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(ex?.name ?: "Exercise #$exId", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Edit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            exSets.forEachIndexed { i, s ->
                                val marks = setMarks(s)
                                SetRow(
                                    index = i + 1,
                                    summary = describeSet(snap, s.weightKg, s.reps, s.distance, s.durationSec),
                                    comment = s.comment,
                                    isPr = s.isPr,
                                    framed = false,
                                    badge = marks.badge,
                                    badgeSpoken = marks.badgeSpoken,
                                    effort = marks.effort,
                                    effortSpoken = marks.effortSpoken
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { pickExercise = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add exercise")
                    }
                    OutlinedButton(onClick = { copyPrevious = true }) { Text("Copy previous") }
                }
            }
        }
    }

    if (addMeasurement) AddMeasurementDialog(snap, date) { addMeasurement = false }
    deleteRecord?.let { r ->
        ConfirmDialog("Delete measurement?", "${r.name} ${fmtNum(r.value)} ${r.unit} (added in FitLens)", onDismiss = { deleteRecord = null }) {
            AppScope.scope.launch { Store.deleteRecord(r.id) }
        }
    }
    if (pickExercise) {
        ExercisePickerDialog(snap, onDismiss = { pickExercise = false }) { exId ->
            pickExercise = false
            nav.push(Screen.SetEntry(date, exId))
        }
    }
    if (editComment) WorkoutCommentDialog(snap, date) { editComment = false }
    if (copyPrevious) CopyPreviousWorkoutDialog(snap, date) { copyPrevious = false }
    if (copyToDay) CopyOrMoveWorkoutDialog(date, move = false) { copyToDay = false }
    if (moveToDay) CopyOrMoveWorkoutDialog(date, move = true) { moveToDay = false }
    if (deleteWorkout) DeleteWorkoutDialog(snap, date) { deleteWorkout = false }
}

fun defOrder(snap: Snapshot, name: String): Int =
    snap.measurementDefs.firstOrNull { it.name == name }?.sortOrder ?: 999

fun describeSet(snap: Snapshot, weightKg: Double, reps: Int, distance: Double, duration: Int): String {
    val parts = ArrayList<String>()
    // A bodyweight set has no weight to show; "0 kg x 10 reps" read as though the weight had been lost (#74).
    if (weightKg != 0.0) parts.add("${snap.fmtWeight(weightKg)} ${snap.weightUnit}")
    if (reps > 0) parts.add("$reps reps")
    if (distance > 0) parts.add("${fmtNum(distance)} dist")
    if (duration > 0) parts.add(fmtDuration(duration))
    return parts.joinToString(" × ").ifBlank { "—" }
}

fun formatTime(iso: String): String? =
    Dates.dateTime(iso)?.toLocalTime()?.format(DateTimeFormatter.ofPattern("HH:mm"))

private fun workoutSeconds(start: String, end: String): Long = Dates.secondsBetween(start, end)

fun nearestPhoto(snap: Snapshot, date: String, windowDays: Int = 30): com.fitlens.companion.data.Photo? {
    val d = Dates.epochDay(date)
    return snap.datedPhotos.minByOrNull { abs(Dates.epochDay(it.date!!) - d) }
        ?.takeIf { abs(Dates.epochDay(it.date!!) - d) <= windowDays }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeasurementDialog(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val names = remember(snap) {
        (snap.usedMeasurements.map { it.name } + snap.measurementDefs.filter { it.enabled }.map { it.name }).distinct()
    }
    var name by remember { mutableStateOf(names.firstOrNull() ?: "Bodyweight") }
    var value by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var theDate by remember { mutableStateOf(date) }
    var timeText by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    val unit = snap.measurementDefs.firstOrNull { it.name == name }?.unit
        ?: snap.recordsByName[name]?.lastOrNull()?.unit ?: ""
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add measurement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEach { n -> FilterChip(selected = n == name, onClick = { name = n }, label = { Text(n) }) }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Measurement") }, singleLine = true)
                OutlinedTextField(
                    value = value, onValueChange = { value = it }, label = { Text("Value ${if (unit.isNotBlank()) "($unit)" else ""}") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment (optional)") })
                TextButton(onClick = { pickDate = true }) { Text("Date: ${Dates.medium(theDate)}") }
                OutlinedTextField(
                    value = timeText, onValueChange = { timeText = it }, label = { Text("Time (HH:mm)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    "Tip: also log it in FitNotes. When a FitNotes backup containing the same value is imported, this entry is merged automatically.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = value.replace(',', '.').toDoubleOrNull()
                if (v != null && name.isNotBlank()) {
                    // The time typed in, or now when it can't be read.
                    val time = runCatching { LocalTime.parse(timeText.trim()) }.getOrNull()
                        ?.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    val n = name.trim()
                    val c = comment.ifBlank { null }
                    val d = theDate
                    AppScope.scope.launch { Store.addManualRecord(n, unit, d, time, v, c) }
                    onDismiss()
                } else UiEvents.show("Enter a number")
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (pickDate) PickDateDialog(theDate, onDismiss = { pickDate = false }) { theDate = it }
}

