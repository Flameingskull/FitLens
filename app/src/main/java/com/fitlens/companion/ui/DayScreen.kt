package com.fitlens.companion.ui

import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.SetRow as LoggedSet
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import com.fitlens.companion.ui.design.DayNavigator
import com.fitlens.companion.ui.design.ExerciseCard
import com.fitlens.companion.ui.design.FitIcons
import com.fitlens.companion.ui.design.SetCommentSheet
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.SearchablePicker
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
    // Open sheets and dialogs come back after rotation or a restart in the background (#37).
    val state = viewModel<DayState>()
    var addMeasurement by state.saved("addMeasurement", false)
    var editComment by state.saved("editComment", false)
    var copyPrevious by state.saved("copyPrevious", false)
    var copyToDay by state.saved("copyToDay", false)
    var moveToDay by state.saved("moveToDay", false)
    var deleteWorkout by state.saved("deleteWorkout", false)
    // Workouts (#100, #106): add a workout day (or replace the day's sets with one), or save this day as a workout day.
    var addWorkout by state.saved("addWorkout", false)
    var replaceWorkout by state.saved("replaceWorkout", false)
    var saveAsWorkout by state.saved("saveAsWorkout", false)
    var editTime by state.saved("editTime", false)
    var share by state.saved("share", false)
    var copyChooser by state.saved("copyChooser", false)
    var restSheet by state.saved("restSheet", false)
    val running = WorkoutClock.running(snap, date)
    val res = LocalContext.current.resources
    val importForDay = rememberPhotoImporter(forcedDate = date)
    val hasWorkout = sets.isNotEmpty() || snap.workoutComments.containsKey(date)
    // Remembers which way the last step went, so the page slides in from the matching side.
    var forward by remember { mutableStateOf(true) }

    fun go(d: String) {
        if (d == date) return
        forward = d > date
        nav.replace(Screen.Day(d))
    }
    // The swipe detector is set up once, so it reads the current day through this.
    val step by rememberUpdatedState<(Long) -> Unit>({ days -> go(shiftDay(date, days)) })

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = stringResource(if (nav.atHome) R.string.app_name else R.string.day_title),
            onBack = if (nav.atHome) null else ({ nav.pop() }),
            centered = false,
            // FitNotes's app icon before its title (#141).
            brandMark = nav.atHome,
            actions = listOf(
                TopBarAction(Icons.Filled.DateRange, stringResource(R.string.day_calendar)) { nav.push(Screen.Calendar) },
                TopBarAction(Icons.Filled.Add, stringResource(R.string.day_add_exercise)) { nav.push(Screen.Library(date)) }
            ),
            // A running rest stays in view after going back to the day (#109).
            trailing = { RestTimerButton(onOpen = { restSheet = true }, onlyWhileRunning = true) },
            overflow = listOfNotNull(
                if (date != Dates.today()) MenuAction(stringResource(R.string.day_go_today)) { go(Dates.today()) } else null,
                MenuAction(stringResource(R.string.day_add_workout)) { addWorkout = true },
                MenuAction(stringResource(R.string.day_replace_workout), enabled = sets.isNotEmpty()) { replaceWorkout = true },
                MenuAction(stringResource(R.string.day_create_workout), enabled = sets.isNotEmpty()) { saveAsWorkout = true },
                MenuAction(stringResource(if (running != null) R.string.day_stop_timer else R.string.day_workout_time)) {
                    if (running != null) WorkoutClock.stop(res, date, running) else editTime = true
                },
                // FitNotes's single Copy Workout entry (#148) offers copy, move and copy previous.
                MenuAction(stringResource(R.string.day_copy_workout)) { copyChooser = true },
                MenuAction(stringResource(R.string.day_share_workout), enabled = sets.isNotEmpty()) { share = true },
                MenuAction(stringResource(R.string.day_delete_workout), enabled = hasWorkout) { deleteWorkout = true },
                MenuAction(stringResource(R.string.day_add_photos)) { importForDay() },
                MenuAction(stringResource(R.string.day_add_measurement)) { addMeasurement = true },
                MenuAction(stringResource(R.string.day_menu_analysis)) { nav.push(Screen.Analysis) },
                MenuAction(stringResource(R.string.day_menu_body)) { nav.push(Screen.Body) },
                MenuAction(stringResource(R.string.day_menu_photos)) { nav.push(Screen.Photos) },
                MenuAction(stringResource(R.string.day_menu_settings)) { nav.push(Screen.SettingsHome) }
            )
        )
        DayNavigator(
            date = date,
            onPrevious = { go(shiftDay(date, -1)) },
            onNext = { go(shiftDay(date, 1)) },
            onPickDate = { d -> go(d) },
            onToday = { go(Dates.today()) }
        )
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
    if (copyChooser) {
        CopyWorkoutSheet(hasSets = sets.isNotEmpty(), hasWorkout = hasWorkout, onDismiss = { copyChooser = false }) { choice ->
            copyChooser = false
            when (choice) {
                CopyChoice.COPY_THIS -> copyToDay = true
                CopyChoice.MOVE_THIS -> moveToDay = true
                CopyChoice.COPY_PREVIOUS -> copyPrevious = true
            }
        }
    }
    if (copyPrevious) CopyPreviousWorkoutSheet(snap, date) { copyPrevious = false }
    if (copyToDay) CopyOrMoveWorkoutSheet(snap, date, move = false) { copyToDay = false }
    if (moveToDay) CopyOrMoveWorkoutSheet(snap, date, move = true) { moveToDay = false }
    if (deleteWorkout) DeleteWorkoutSheet(snap, date) { deleteWorkout = false }
    if (addWorkout) AddWorkoutSheet(snap, nav, date, replace = false) { addWorkout = false }
    if (replaceWorkout) AddWorkoutSheet(snap, nav, date, replace = true) { replaceWorkout = false }
    if (saveAsWorkout) SaveAsWorkoutSheet(snap, nav, date) { saveAsWorkout = false }
    if (editTime) WorkoutTimeSheet(snap, date) { editTime = false }
    if (share) ShareWorkoutSheet(snap, date) { share = false }
    if (restSheet) RestTimerSheet { restSheet = false }
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
    // Exercises as shown: workout order, with each superset's exercises together (#18).
    val byExercise = remember(sets) {
        val grouped = sets.groupBy { it.exerciseId }
        displayOrder(snap, date).mapNotNull { ex -> grouped[ex]?.let { ex to it } }
    }
    val letters = remember(sets) { supersetLetters(snap, date) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xl)) {
        // As in FitNotes: the day's body values in one card at the top.
        if (records.isNotEmpty()) {
            item(key = "body") {
                BodyValuesCard(
                    snap = snap,
                    records = records.sortedWith(compareBy({ defOrder(snap, it.name) }, { it.time })),
                    onOpen = { r -> nav.push(Screen.BodyMeasurement(r.name)) },
                    onDelete = { deleteRecord = it }
                )
            }
        }
        // The day's progress photos (a FitLens extra).
        if (photos.isNotEmpty()) {
            item(key = "photos") { PhotoStrip(snap, nav, photos, onAddPhoto) }
        }
        if (sets.isEmpty()) {
            item(key = "empty") {
                val nothing = records.isEmpty() && photos.isEmpty()
                EmptyDay(
                    showEmptyText = nothing,
                    compact = !nothing,
                    modifier = if (nothing) Modifier.fillParentMaxHeight(0.9f) else Modifier,
                    onAddWorkout = onAddWorkout,
                    onAddExercise = onAddExercise,
                    onCopyPrevious = onCopyPrevious
                )
            }
        }
        byExercise.forEach { (exId, exSets) ->
            item(key = "e$exId") {
                val group = exSets.maxOf { it.superset }
                val firstOfGroup = group > 0 && byExercise.firstOrNull { (_, s) -> s.maxOf { it.superset } == group }?.first == exId
                if (firstOfGroup) {
                    Text(
                        stringResource(R.string.day_superset_heading, letters[group] ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand.Gold,
                        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.sm)
                    )
                }
                ExerciseOnDay(snap, nav, date, exId, exSets, showCategories, setsShown)
            }
        }
        // The workout's time, totals and comment, under the exercises so the day reads as FitNotes's does.
        if (sets.isNotEmpty() || comments.isNotEmpty() || WorkoutClock.running(snap, date) != null) {
            item(key = "summary") {
                val times = snap.workoutTimes[date]
                val total = times?.sumOf { workoutSeconds(it.start, it.end) } ?: 0L
                // A running workout timer counts up here once a second (#12).
                val live = WorkoutClock.running(snap, date)?.let { start -> rememberElapsed(start) }
                val info = listOfNotNull(
                    live?.let { "● ${fmtDuration(it.toInt())}" } ?: if (total > 0) fmtDuration(total.toInt()) else null,
                    if (Settings.currentPortable().markComplete) stringResource(R.string.day_sets_done, sets.count { it.done }, sets.size)
                    else pluralStringResource(R.plurals.day_sets, sets.size, sets.size),
                    stringResource(R.string.day_volume, fmtNum(snap.weight(sets.sumOf { it.weightKg * it.reps }), 0), snap.weightUnit)
                ).joinToString("  ·  ")
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    Text(info.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // The workout comment is the day's own note on the session as a whole (owner, 2026-10-03), under a
                    // heading so it reads apart from the exercises' comments.
                    if (comments.isNotEmpty() || sets.isNotEmpty()) {
                        com.fitlens.companion.ui.design.SectionLabel(stringResource(R.string.day_workout_comment), Modifier.padding(top = Spacing.md))
                    }
                    val editLabel = stringResource(R.string.day_edit_workout_comment)
                    comments.forEach {
                        Text(
                            "“$it”",
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Spacing.touch)
                                .clickable(onClickLabel = editLabel, onClick = onEditComment)
                                .padding(vertical = Spacing.sm),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic
                        )
                    }
                    // A comment is added on the day's workout itself, not from the menu.
                    if (comments.isEmpty() && sets.isNotEmpty()) {
                        TextButton(
                            onClick = onEditComment,
                            modifier = Modifier.heightIn(min = Spacing.touch),
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) {
                            Text(stringResource(R.string.day_add_workout_comment), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    deleteRecord?.let { r ->
        ConfirmDialog(
            stringResource(R.string.day_delete_measurement_title),
            stringResource(R.string.day_delete_measurement_body, r.name, fmtNum(r.value), r.unit),
            confirm = stringResource(R.string.day_delete),
            onDismiss = { deleteRecord = null }) {
            AppScope.scope.launch { Store.deleteRecord(r.id) }
        }
    }
}

/** The day's progress photos as a compact strip, with an Add photo tile at the end (FitLens extra, #81). */
@Composable
private fun PhotoStrip(snap: Snapshot, nav: Nav, photos: List<Photo>, onAddPhoto: () -> Unit) {
    val openLabel = stringResource(R.string.day_open_photo)
    val addLabel = stringResource(R.string.day_add_photos)
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        itemsIndexed(photos, key = { _, p -> p.id }) { i, p ->
            PhotoThumb(
                snap, p,
                Modifier.height(120.dp).width(90.dp).clickable(onClickLabel = openLabel) {
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
                    .clickable(onClickLabel = addLabel, onClick = onAddPhoto)
                    .semantics(mergeDescendants = true) { contentDescription = addLabel },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Brand.Gold)
                Text(stringResource(R.string.day_add_photo_tile), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The body values logged on the day, as FitNotes shows them (owner, 2026-10-02): one card, a row per measurement with
 * its name on the left and its value and unit on the right, hairlines between. The change since the value before sits
 * small under the value (#120). Tapping a row opens that measurement; a value added in FitLens can be deleted with a
 * long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BodyValuesCard(snap: Snapshot, records: List<MRecord>, onOpen: (MRecord) -> Unit, onDelete: (MRecord) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .background(Brand.Surface, FitShapes.card)
            .border(1.dp, Brand.Hairline, FitShapes.card)
    ) {
        records.forEachIndexed { i, r ->
            val prev = remember(snap, r.id) { snap.recordsByName[r.name]?.lastOrNull { it.date < r.date } }
            val def = snap.allMeasurements.firstOrNull { it.name == r.name }
            val change = prev?.let { changeText(LocalContext.current.resources, it, r) }
            val openLabel = stringResource(R.string.day_open_named, r.name)
            val deleteLabel = stringResource(R.string.day_delete_value)
            if (i > 0) HorizontalDivider(color = Brand.Hairline)
            // Name and value share one line, and the change gets a line of its own under them, so nothing is squeezed
            // into breaking mid-word (#128).
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.row)
                    .combinedClickable(
                        onClickLabel = openLabel,
                        onClick = { onOpen(r) },
                        onLongClickLabel = if (r.source == "manual") deleteLabel else null,
                        onLongClick = if (r.source == "manual") ({ onDelete(r) }) else null
                    )
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = Spacing.md)
                    )
                    Text(
                        fmtNum(r.value),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (r.unit.isNotBlank()) {
                        Text(
                            " ${r.unit}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                if (change != null && prev != null) {
                    Text(
                        change,
                        style = MaterialTheme.typography.bodySmall,
                        color = changeColour(def, prev.value, r.value),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs)
                    )
                }
            }
        }
    }
}

/**
 * The empty day, laid out as FitNotes's (owner, 2026-10-02): "Workout log empty" when the day has nothing at all, and
 * the ways to start as gold icons over their labels, towards the bottom of the screen. Adding one exercise is never
 * labelled as starting a workout: a workout is a group of exercises (owner decision on #79), so "Add workout" adds a
 * saved one (#100).
 */
@Composable
private fun EmptyDay(
    showEmptyText: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onAddWorkout: () -> Unit,
    onAddExercise: () -> Unit,
    onCopyPrevious: () -> Unit
) {
    if (compact) {
        // Under the day's body values or photos (#128): the three actions in one row, in view without scrolling.
        Row(
            modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.lg),
            verticalAlignment = Alignment.Top
        ) {
            EmptyDayAction(Icons.Filled.Add, stringResource(R.string.day_add_exercise), onAddExercise, Modifier.weight(1f))
            EmptyDayAction(Icons.Filled.List, stringResource(R.string.day_add_workout), onAddWorkout, Modifier.weight(1f))
            EmptyDayAction(FitIcons.Copy, stringResource(R.string.day_copy_previous), onCopyPrevious, Modifier.weight(1f))
        }
        return
    }
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        if (showEmptyText) {
            Text(stringResource(R.string.day_log_empty), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        EmptyDayAction(Icons.Filled.Add, stringResource(R.string.day_add_exercise), onAddExercise)
        EmptyDayAction(Icons.Filled.List, stringResource(R.string.day_add_workout), onAddWorkout)
        EmptyDayAction(FitIcons.Copy, stringResource(R.string.day_copy_previous), onCopyPrevious)
        Spacer(Modifier.height(Spacing.lg))
    }
}

/** One of the empty day's actions: a gold icon over its label, as FitNotes draws them. */
@Composable
private fun EmptyDayAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Column(
        modifier
            .clip(FitShapes.row)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Brand.Gold, modifier = Modifier.size(36.dp))
        // Whole words only: a narrow column wraps between words, centred, never inside one (#128).
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs, end = Spacing.xs)
        )
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
    val res = LocalContext.current.resources
    val name = snap.exercises[exId]?.name ?: stringResource(R.string.day_exercise_fallback, exId)
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var swapping by remember { mutableStateOf(false) }
    var grouping by remember { mutableStateOf(false) }
    var overview by remember { mutableStateOf(false) }
    var commenting by remember { mutableStateOf(false) }
    val exerciseComment = snap.exerciseComments[date.take(10)]?.get(exId)
    val markComplete = Settings.portable.collectAsState().value.markComplete
    val group = exSets.maxOfOrNull { it.superset } ?: 0
    val limit = if (setsShown == 0 || expanded) exSets.size else minOf(setsShown, exSets.size)
    // Each value in its own labelled column (#101), chosen by the exercise type.
    val fields = setFields(snap, exId, exSets)
    // A superset's exercises share a gold bar, as FitNotes colours its groups (#18).
    val colour = if (group > 0) Brand.Gold else if (showCategories) categoryColour(snap.categoryOf(exId)?.colour ?: 0) else Brand.Gold
    ExerciseCard(
        name = name,
        categoryColor = colour,
        onClick = { nav.push(Screen.SetEntry(date, exId)) },
        // The exercise's comment in this workout sits under its sets (#107).
        comment = exerciseComment,
        // FitNotes ticks the exercise once all its sets are done ("Mark sets complete", #19).
        done = exSets.isNotEmpty() && exSets.all { it.done },
        // With "Mark sets complete" on, an unfinished exercise shows how many of its sets are done ("2/4").
        setsDone = exSets.count { it.done },
        setsTotal = if (markComplete) exSets.size else null,
        menu = listOf(
            MenuAction(stringResource(R.string.day_ex_log_sets)) { nav.push(Screen.SetEntry(date, exId)) },
            MenuAction(stringResource(if (exerciseComment.isNullOrBlank()) R.string.day_ex_add_comment else R.string.day_ex_edit_comment)) { commenting = true },
            MenuAction(stringResource(R.string.day_ex_history)) { nav.push(Screen.SetEntry(date, exId, page = 1)) },
            MenuAction(stringResource(R.string.day_ex_overview)) { overview = true },
            MenuAction(stringResource(R.string.day_ex_records)) { nav.push(Screen.ExerciseDetail(exId)) },
            MenuAction(stringResource(R.string.day_move_up), enabled = displayOrder(snap, date).indexOf(exId) > 0) { moveExercise(snap, date, exId, -1) },
            MenuAction(stringResource(R.string.day_move_down), enabled = displayOrder(snap, date).let { it.indexOf(exId) in 0 until it.lastIndex }) {
                moveExercise(snap, date, exId, 1)
            },
            MenuAction(stringResource(R.string.day_ex_superset), enabled = dayExercises(snap, date).size > 1) { grouping = true },
            MenuAction(stringResource(R.string.day_remove_superset), enabled = group > 0) {
                AppScope.scope.launch { Workouts.ungroupExercise(date, exId) }
            },
            MenuAction(stringResource(R.string.day_ex_swap)) { swapping = true },
            MenuAction(stringResource(R.string.day_ex_remove)) { confirmDelete = true }
        )
    ) {
        exSets.take(limit).forEachIndexed { i, s ->
            val marks = setMarks(s)
            SetRow(
                index = i + 1,
                summary = describeSet(res, snap, s.weightKg, s.reps, s.distance, s.durationSec, s.exerciseId),
                cells = setCells(res, snap, fields, s),
                comment = s.comment,
                isPr = s.isPr,
                framed = false,
                // FitNotes lists a day's sets without numbers (#112); TalkBack still says "Set 2".
                showIndex = false,
                badge = marks.badge,
                badgeSpoken = marks.badgeSpoken,
                effort = marks.effort,
                effortSpoken = marks.effortSpoken
            )
        }
        val hidden = exSets.size - limit
        if (hidden > 0) {
            TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(pluralStringResource(R.plurals.day_more_sets, hidden, hidden), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (grouping) {
        SearchablePicker(
            title = stringResource(R.string.day_superset_title, name),
            items = exercisePickerItems(snap).filter { it.id != exId && it.id in dayExercises(snap, date) },
            multiSelect = true,
            onDismiss = { grouping = false },
            onPick = { ids ->
                grouping = false
                if (ids.isNotEmpty()) AppScope.scope.launch { Workouts.groupExercises(date, ids + exId) }
            }
        )
    }
    if (overview) ExerciseOverviewSheet(snap, nav, exId, date) { overview = false }
    if (swapping) {
        // Swaps the exercise for today only: its sets on this day move to the chosen one (#100). A saved workout's
        // exercise is swapped for good in the workout editor.
        SearchablePicker(
            title = stringResource(R.string.day_swap_title, name),
            items = exercisePickerItems(snap).filter { it.id != exId },
            searchLabel = stringResource(R.string.day_search_exercises),
            onDismiss = { swapping = false },
            onPick = { ids ->
                swapping = false
                ids.firstOrNull()?.let { to ->
                    val toName = snap.exercises[to]?.name ?: res.getString(R.string.day_swap_new_fallback)
                    AppScope.scope.launch {
                        try {
                            val moved = Workouts.swapExercise(date, exId, to)
                            UiEvents.show(res.getString(R.string.day_swapped, name, toName), res.getString(R.string.undo)) {
                                AppScope.scope.launch { Workouts.setExerciseOf(moved, exId) }
                            }
                        } catch (e: WorkoutDataException) {
                            UiEvents.show(e.message ?: res.getString(R.string.day_swap_failed))
                        }
                    }
                }
            }
        )
    }
    if (commenting) {
        SetCommentSheet(
            describe = "$name · ${Dates.medium(date)}",
            initial = exerciseComment,
            onSave = { text -> AppScope.scope.launch { Workouts.setExerciseComment(date, exId, text) } },
            onDismiss = { commenting = false },
            title = stringResource(R.string.day_exercise_comment),
            detailed = true,
            earlier = earlierExerciseComments(snap, exId, date, 5)
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.day_remove_title, name),
            pluralStringResource(R.plurals.day_remove_body, exSets.size, exSets.size, Dates.medium(date)),
            confirm = stringResource(R.string.day_remove),
            onDismiss = { confirmDelete = false }
        ) {
            val removed = exSets
            val removedComment = exerciseComment
            AppScope.scope.launch {
                Workouts.deleteHistory(date, date, setOf(exId))
                UiEvents.show(res.getString(R.string.day_removed, name), res.getString(R.string.undo)) {
                    AppScope.scope.launch {
                        try {
                            Workouts.addSets(removed)
                            if (!removedComment.isNullOrBlank()) Workouts.setExerciseComment(date, exId, removedComment)
                        } catch (e: Exception) {
                            UiEvents.show(res.getString(R.string.day_undo_failed, e.message ?: e.javaClass.simpleName))
                        }
                    }
                }
            }
        }
    }
}

fun defOrder(snap: Snapshot, name: String): Int =
    snap.measurementDefs.firstOrNull { it.name == name }?.sortOrder ?: 999

fun describeSet(
    res: android.content.res.Resources, snap: Snapshot, weightKg: Double, reps: Int, distance: Double, duration: Int, exerciseId: Long? = null,
    /** A custom type's metric (#14), shown with its unit. */
    metric: Double? = null
): String {
    val parts = ArrayList<String>()
    // A bodyweight set has no weight to show; "0 kg x 10 reps" read as though the weight had been lost (#74).
    if (weightKg != 0.0) parts.add("${snap.fmtWeight(weightKg, exerciseId)} ${snap.weightUnitOf(exerciseId)}")
    if (reps > 0) parts.add(res.getQuantityString(R.plurals.reps_count, reps, reps))
    // Distances carry their exercise's unit, or the global one when the exercise isn't known (#7).
    if (distance > 0) parts.add("${fmtNum(distance)} ${exerciseId?.let { snap.distanceUnit(it) } ?: snap.globalDistanceUnit}")
    if (duration > 0) parts.add(fmtDuration(duration))
    if (metric != null) {
        val m = exerciseId?.let { snap.exercises[it] }?.let { ExerciseTypes.metricOf(it.type) }
        parts.add(listOfNotNull(fmtNum(metric, 2), m?.metricUnit, m?.metricName?.lowercase()).joinToString(" "))
    }
    return parts.joinToString(" · ").ifBlank { "—" }
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
fun AddMeasurementDialog(snap: Snapshot, date: String, initialName: String? = null, onDismiss: () -> Unit) {
    // Now a sheet, shared with editing a value (#88).
    MeasurementEntrySheet(snap, date, initialName = initialName, onDismiss = onDismiss)
}

