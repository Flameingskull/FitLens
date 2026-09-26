package com.fitlens.companion.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.SetRow as LoggedSet
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import com.fitlens.companion.ui.design.DayNavigator
import com.fitlens.companion.ui.design.ExerciseCard
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.SetRow
import com.fitlens.companion.ui.design.TopBarAction
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch

/** [date] moved by [days] calendar days, as an ISO date. */
private fun shiftDay(date: String, days: Long): String =
    Dates.parse(date)?.plusDays(days)?.format(Dates.ISO) ?: date

/**
 * The day log (#81, #8): the home screen, laid out like FitNotes's training log in the FitLens look.
 *
 * At the root of the stack it is home: the "FitLens" title, Calendar, + (add an exercise) and the menu that reaches
 * every other screen (#79). Pushed from elsewhere (a calendar day, a record), it gets a back arrow instead.
 * The arrows and a swipe anywhere on the page move one calendar day, empty days included, so a workout can be
 * logged on any of them. The day's photos and body values sit above the workout when there are any.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(snap: Snapshot, nav: Nav, date: String) {
    val prefs by Settings.portable.collectAsState()
    val sets = snap.setsByDate[date] ?: emptyList()
    // The nearest days with data either side, for the menu's jumps. allDates is newest first.
    val older = snap.allDates.firstOrNull { it < date }
    val newer = snap.allDates.lastOrNull { it > date }
    var addMeasurement by remember { mutableStateOf(false) }
    var editComment by remember { mutableStateOf(false) }
    var copyPrevious by remember { mutableStateOf(false) }
    var copyToDay by remember { mutableStateOf(false) }
    var moveToDay by remember { mutableStateOf(false) }
    var deleteWorkout by remember { mutableStateOf(false) }
    // Saved workouts (#100): add one (or replace the day's with one), or save this day as one.
    var addWorkout by remember { mutableStateOf(false) }
    var replaceWorkout by remember { mutableStateOf(false) }
    var saveAsWorkout by remember { mutableStateOf(false) }
    val importForDay = rememberPhotoImporter(forcedDate = date)
    val hasWorkout = sets.isNotEmpty() || snap.workoutComments.containsKey(date)
    // Remembers which way the last step went, so the page slides in from the matching side.
    var forward by remember { mutableStateOf(true) }

    fun go(d: String) {
        if (d == date) return
        forward = d > date
        nav.stack[nav.stack.lastIndex] = Screen.Day(d)
    }
    // The swipe detector is set up once, so it reads the current day through this.
    val step by rememberUpdatedState<(Long) -> Unit>({ days -> go(shiftDay(date, days)) })

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = if (nav.atHome) "FitLens" else "Training log",
            onBack = if (nav.atHome) null else ({ nav.pop() }),
            centered = false,
            actions = listOf(
                TopBarAction(Icons.Filled.DateRange, "Calendar") { nav.push(Screen.Calendar) },
                TopBarAction(Icons.Filled.Add, "Add exercise") { nav.push(Screen.Library(date)) }
            ),
            overflow = listOf(
                MenuAction("Add workout") { addWorkout = true },
                MenuAction("Replace this workout", enabled = sets.isNotEmpty()) { replaceWorkout = true },
                MenuAction("Save as a workout", enabled = sets.isNotEmpty()) { saveAsWorkout = true },
                MenuAction(if (snap.workoutComments.containsKey(date)) "Edit workout comment" else "Workout comment") { editComment = true },
                MenuAction("Copy previous workout") { copyPrevious = true },
                MenuAction("Copy this workout to another day", enabled = sets.isNotEmpty()) { copyToDay = true },
                MenuAction("Move this workout to another day", enabled = hasWorkout) { moveToDay = true },
                MenuAction("Delete this workout", enabled = hasWorkout) { deleteWorkout = true },
                MenuAction("Add photos to this day") { importForDay() },
                MenuAction("Add measurement") { addMeasurement = true },
                MenuAction("Previous day with data", enabled = older != null) { older?.let { go(it) } },
                MenuAction("Next day with data", enabled = newer != null) { newer?.let { go(it) } },
                MenuAction("Workouts") { nav.push(Screen.SavedWorkouts) },
                MenuAction("Analysis") { nav.push(Screen.Analysis) },
                MenuAction("Body tracker") { nav.push(Screen.Body) },
                MenuAction("Photos") { nav.push(Screen.Photos) },
                MenuAction("All days") { nav.push(Screen.Timeline) },
                MenuAction("Exercise library") { nav.push(Screen.Library(date)) },
                MenuAction("Settings") { nav.push(Screen.SettingsHome) }
            )
        )
        DayNavigator(
            date = date,
            onPrevious = { go(shiftDay(date, -1)) },
            onNext = { go(shiftDay(date, 1)) },
            onPickDate = { d -> go(d) },
            onToday = { go(Dates.today()) }
        )
        if (date != Dates.today()) {
            TextButton(
                onClick = { go(Dates.today()) },
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = Spacing.touch)
            ) { Text("BACK TO TODAY", style = MaterialTheme.typography.labelMedium) }
        }
        // A horizontal swipe anywhere on the page steps a day (#8). Vertical scrolling and the photo strip's own
        // horizontal scroll consume their drags first, so they are never mistaken for a swipe.
        Box(
            Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        val threshold = 72.dp.toPx()
                        if (total > threshold) step(-1L) else if (total < -threshold) step(1L)
                        total = 0f
                    },
                    onDragCancel = { total = 0f }
                ) { change, amount ->
                    change.consume()
                    total += amount
                }
            }
        ) {
            AnimatedContent(
                targetState = date,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(Motion.STANDARD)) { it / 4 * dir } + fadeIn(tween(Motion.STANDARD))) togetherWith
                        (slideOutHorizontally(tween(Motion.STANDARD)) { -it / 4 * dir } + fadeOut(tween(Motion.FAST)))
                },
                label = "day"
            ) { shown ->
                DayContent(
                    snap = snap,
                    nav = nav,
                    date = shown,
                    showCategories = prefs.homeShowCategories,
                    setsShown = prefs.homeSetsShown,
                    onAddPhoto = importForDay,
                    onEditComment = { editComment = true },
                    onAddExercise = { nav.push(Screen.Library(date)) },
                    onAddWorkout = { addWorkout = true },
                    onCopyPrevious = { copyPrevious = true }
                )
            }
        }
    }

    if (addMeasurement) AddMeasurementDialog(snap, date) { addMeasurement = false }
    if (editComment) WorkoutCommentSheet(snap, date) { editComment = false }
    if (copyPrevious) CopyPreviousWorkoutSheet(snap, date) { copyPrevious = false }
    if (copyToDay) CopyOrMoveWorkoutSheet(snap, date, move = false) { copyToDay = false }
    if (moveToDay) CopyOrMoveWorkoutSheet(snap, date, move = true) { moveToDay = false }
    if (deleteWorkout) DeleteWorkoutSheet(snap, date) { deleteWorkout = false }
    if (addWorkout) AddWorkoutSheet(snap, nav, date, replace = false) { addWorkout = false }
    if (replaceWorkout) AddWorkoutSheet(snap, nav, date, replace = true) { replaceWorkout = false }
    if (saveAsWorkout) SaveAsWorkoutSheet(snap, date) { saveAsWorkout = false }
}

/** One day's log: photo strip, body values, the workout summary and its exercise cards, or the empty-day actions. */
@Composable
private fun DayContent(
    snap: Snapshot,
    nav: Nav,
    date: String,
    showCategories: Boolean,
    setsShown: Int,
    onAddPhoto: () -> Unit,
    onEditComment: () -> Unit,
    onAddExercise: () -> Unit,
    onAddWorkout: () -> Unit,
    onCopyPrevious: () -> Unit
) {
    val photos = snap.photosByDate[date] ?: emptyList()
    val records = snap.recordsByDate[date] ?: emptyList()
    val sets = snap.setsByDate[date] ?: emptyList()
    val comments = snap.workoutComments[date].orEmpty()
    var deleteRecord by remember { mutableStateOf<MRecord?>(null) }
    // Exercises in the order they were first logged that day.
    val byExercise = remember(sets) { sets.groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.id } } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xl)) {
        if (photos.isNotEmpty()) {
            item(key = "photos") { PhotoStrip(snap, nav, photos, onAddPhoto) }
        }
        if (records.isNotEmpty()) {
            item(key = "body") {
                BodyValuesCard(
                    snap = snap,
                    records = records.sortedWith(compareBy({ defOrder(snap, it.name) }, { it.time })),
                    onOpen = { nav.push(Screen.Body) },
                    onDelete = { deleteRecord = it }
                )
            }
        }
        if (sets.isNotEmpty() || comments.isNotEmpty()) {
            item(key = "summary") {
                val times = snap.workoutTimes[date]
                val total = times?.sumOf { workoutSeconds(it.start, it.end) } ?: 0L
                val info = listOfNotNull(
                    if (total > 0) fmtDuration(total.toInt()) else null,
                    "${sets.size} set${if (sets.size == 1) "" else "s"}",
                    "${fmtNum(snap.weight(sets.sumOf { it.weightKg * it.reps }), 0)} ${snap.weightUnit} volume"
                ).joinToString("  ·  ")
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    Text(info.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    comments.forEach {
                        Text(
                            "“$it”",
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Spacing.touch)
                                .clickable(onClickLabel = "Edit workout comment", onClick = onEditComment)
                                .padding(vertical = Spacing.sm),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
        if (sets.isEmpty()) {
            item(key = "empty") { EmptyDay(onAddWorkout, onAddExercise, onCopyPrevious) }
        }
        byExercise.forEach { (exId, exSets) ->
            item(key = "e$exId") {
                ExerciseOnDay(snap, nav, date, exId, exSets, showCategories, setsShown)
            }
        }
    }

    deleteRecord?.let { r ->
        ConfirmDialog("Delete measurement?", "${r.name} ${fmtNum(r.value)} ${r.unit} (added in FitLens)", onDismiss = { deleteRecord = null }) {
            AppScope.scope.launch { Store.deleteRecord(r.id) }
        }
    }
}

/** The day's progress photos as a compact strip, with an Add photo tile at the end (FitLens extra, #81). */
@Composable
private fun PhotoStrip(snap: Snapshot, nav: Nav, photos: List<Photo>, onAddPhoto: () -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        itemsIndexed(photos, key = { _, p -> p.id }) { i, p ->
            PhotoThumb(
                snap, p,
                Modifier.height(120.dp).width(90.dp).clickable(onClickLabel = "Open photo") {
                    nav.push(Screen.PhotoViewer(photos.map { it.id }, i))
                },
                sizePx = 360
            )
        }
        item(key = "add") {
            Column(
                Modifier
                    .height(120.dp)
                    .width(90.dp)
                    .border(1.dp, Brand.Hairline, FitShapes.row)
                    .clickable(onClickLabel = "Add photos to this day", onClick = onAddPhoto)
                    .semantics(mergeDescendants = true) { contentDescription = "Add photos to this day" },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Brand.Gold)
                Text("ADD PHOTO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The body values logged on the day, one row per measurement with its value on the right (#81). */
@Composable
private fun BodyValuesCard(snap: Snapshot, records: List<MRecord>, onOpen: () -> Unit, onDelete: (MRecord) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .background(Brand.Surface, FitShapes.card)
            .border(1.dp, Brand.Hairline, FitShapes.card)
            .clickable(onClickLabel = "Open the body tracker", onClick = onOpen)
            .padding(vertical = Spacing.xs)
    ) {
        Text(
            "BODY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.xs)
        )
        records.forEach { r ->
            val prev = remember(snap, r.id) { snap.recordsByName[r.name]?.lastOrNull { it.date < r.date } }
            val change = prev?.let { "${fmtSigned(r.value - it.value)} since ${Dates.short(it.date)}" }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.touch)
                    .padding(start = Spacing.md, end = if (r.source == "manual") 0.dp else Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(r.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${fmtNum(r.value)} ${r.unit}", style = MaterialTheme.typography.titleMedium)
                    if (change != null) {
                        Text(change, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (r.source == "manual") {
                    IconButton(onClick = { onDelete(r) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${r.name} measurement")
                    }
                }
            }
        }
    }
}

/**
 * The empty day (#81): a quiet message and the ways to start. Adding one exercise is never labelled as starting a
 * workout: a workout is a group of exercises (owner decision on #79), so "Add workout" adds a saved one (#100).
 */
@Composable
private fun EmptyDay(onAddWorkout: () -> Unit, onAddExercise: () -> Unit, onCopyPrevious: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text("No workout logged", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            "Add a saved workout, add exercises one at a time, or copy a workout you've logged before.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.sm))
        Button(onClick = onAddWorkout, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Add workout")
        }
        OutlinedButton(onClick = onAddExercise, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row)) {
            Text("Add exercise")
        }
        OutlinedButton(onClick = onCopyPrevious, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row)) {
            Text("Copy previous workout")
        }
    }
}

/** One exercise on the day: its card with the sets, trimmed to the "sets shown" setting (#8). */
@Composable
private fun ExerciseOnDay(
    snap: Snapshot,
    nav: Nav,
    date: String,
    exId: Long,
    exSets: List<LoggedSet>,
    showCategories: Boolean,
    setsShown: Int
) {
    val name = snap.exercises[exId]?.name ?: "Exercise #$exId"
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val limit = if (setsShown == 0 || expanded) exSets.size else minOf(setsShown, exSets.size)
    val colour = if (showCategories) categoryColour(snap.categoryOf(exId)?.colour ?: 0) else Brand.Hairline
    ExerciseCard(
        name = name,
        categoryColor = colour,
        onClick = { nav.push(Screen.SetEntry(date, exId)) },
        menu = listOf(
            MenuAction("Log sets") { nav.push(Screen.SetEntry(date, exId)) },
            MenuAction("History and graph") { nav.push(Screen.SetEntry(date, exId, page = 1)) },
            MenuAction("Records and goals") { nav.push(Screen.ExerciseDetail(exId)) },
            MenuAction("Remove from this workout") { confirmDelete = true }
        )
    ) {
        exSets.take(limit).forEachIndexed { i, s ->
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
        val hidden = exSets.size - limit
        if (hidden > 0) {
            TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text("+$hidden more set${if (hidden == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Remove $name from this workout?",
            "Its ${exSets.size} set${if (exSets.size == 1) "" else "s"} on ${Dates.medium(date)} will be deleted. " +
                "The exercise stays in your library.",
            confirm = "Remove",
            onDismiss = { confirmDelete = false }
        ) {
            val removed = exSets
            AppScope.scope.launch {
                Workouts.deleteHistory(date, date, setOf(exId))
                UiEvents.show("$name removed from this workout", "Undo") {
                    AppScope.scope.launch {
                        try {
                            Workouts.addSets(removed)
                        } catch (e: Exception) {
                            UiEvents.show("Couldn't undo that: ${e.message}")
                        }
                    }
                }
            }
        }
    }
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

