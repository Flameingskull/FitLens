@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.OptionsMenu
import com.fitlens.companion.ui.design.MenuAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.DropdownPill
import com.fitlens.companion.ui.design.SegmentedSwitch
import com.fitlens.companion.video.FrameRenderer
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.state.ToggleableState
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Workout tools on the day log: the workout time and timer (#12, #84). Times are stored in `workout_time` in
 * FitNotes's format (`yyyy-MM-dd HH:mm:ss`). A running timer is simply a start with no finish yet.
 */
object WorkoutClock {
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun now(): String = LocalDateTime.now().format(STAMP)

    fun stamp(date: String, time: LocalTime): String = "$date ${time.withNano(0).format(DateTimeFormatter.ofPattern("HH:mm:ss"))}"

    /** The start of the timer running on [date], or null when there isn't one. */
    fun running(snap: Snapshot, date: String): String? =
        snap.workoutTimes[date]?.firstOrNull { it.start.isNotBlank() && it.end.isBlank() }?.start

    /** Stops the timer on [date] at the current time, with Undo. */
    fun stop(date: String, start: String) {
        AppScope.scope.launch {
            try {
                val end = now()
                Workouts.setWorkoutTime(date, start, end)
                val secs = Dates.secondsBetween(start, end).toInt()
                UiEvents.show("Workout timer stopped at ${fmtDuration(secs)}", "Undo") {
                    AppScope.scope.launch { Workouts.setWorkoutTime(date, start, null) }
                }
            } catch (e: WorkoutDataException) {
                UiEvents.show(e.message ?: "The timer couldn't be stopped.")
            }
        }
    }
}

/** Seconds since [start], updated once a second while shown. */
@Composable
fun rememberElapsed(start: String): Long {
    var elapsed by remember(start) { mutableLongStateOf(Dates.secondsBetween(start, WorkoutClock.now())) }
    LaunchedEffect(start) {
        while (true) {
            elapsed = Dates.secondsBetween(start, WorkoutClock.now())
            delay(1000)
        }
    }
    return elapsed
}

/**
 * Workout Time, as FitNotes's (#84, #148): the date with a ⋮ menu, Start Time and End Time fields (each opens a time
 * picker), Start Timer (or Stop Timer while one runs), then Close and Save. FitLens adds the live duration. A day with
 * more than one imported time range is replaced by the single range saved here.
 */
@Composable
fun WorkoutTimeSheet(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val times = snap.workoutTimes[date].orEmpty()
    val first = times.firstOrNull()
    var start by remember { mutableStateOf(first?.start?.takeIf { it.isNotBlank() }) }
    var end by remember { mutableStateOf(first?.end?.takeIf { it.isNotBlank() }) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: start, false: end
    val isToday = date == Dates.today()
    val s = start
    val e = end
    val liveSecs = if (s != null && e == null && isToday) rememberElapsed(s) else null
    val secs = liveSecs ?: if (s != null && e != null) Dates.secondsBetween(s, e) else 0L
    val askNotify = rememberNotificationAsk()

    fun save(newStart: String?, newEnd: String?) {
        onDismiss()
        AppScope.scope.launch {
            try {
                Workouts.setWorkoutTime(date, newStart, newEnd)
            } catch (ex: WorkoutDataException) {
                UiEvents.show(ex.message ?: "That time couldn't be saved.")
            }
        }
    }

    FitSheet(
        title = "Workout time",
        onDismiss = onDismiss,
        dismissLabel = "Close",
        confirmLabel = "Save",
        confirmEnabled = s != null && (e == null || e >= s),
        onConfirm = { save(s, e) }
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Dates.long(date), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OptionsMenu(
                options = emptyList(),
                description = "Workout time options",
                actions = listOf(
                    MenuAction("Clear the time", enabled = times.isNotEmpty() || s != null) { save(null, null) },
                    MenuAction("End time now", enabled = s != null && isToday) { end = WorkoutClock.now() }
                )
            )
        }
        GoldHairline()
        TimeField("Start Time", s) { picking = true }
        TimeField("End Time", e, enabled = s != null) { picking = false }
        if (secs > 0) {
            Text(
                "Duration ${fmtDuration(secs.toInt())}",
                style = MaterialTheme.typography.titleMedium,
                color = Brand.Gold
            )
        }
        if (isToday) {
            when {
                s == null || e != null -> GoldButton(
                    onClick = { askNotify(); save(WorkoutClock.now(), null); UiEvents.show("Workout timer started") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row)
                ) { Text("Start Timer") }
                else -> GoldButton(
                    onClick = { onDismiss(); WorkoutClock.stop(date, s) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row)
                ) { Text("Stop Timer") }
            }
        }
        if (times.size > 1) {
            Text(
                "This day has ${times.size} time ranges from FitNotes. Saving replaces them with the one above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    picking?.let { isStart ->
        val current = (if (isStart) s else e)?.let { Dates.dateTime(it)?.toLocalTime() } ?: LocalTime.now()
        val state = rememberTimePickerState(current.hour, current.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(if (isStart) "Start time" else "End time") },
            text = { TimePicker(state = state, colors = fitTimePickerColors()) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = WorkoutClock.stamp(date, LocalTime.of(state.hour, state.minute))
                    if (isStart) start = picked else end = picked
                    picking = null
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } }
        )
    }
}

/** One of Workout Time's fields: its label in bold, then the time over an underline, as FitNotes's text field. */
@Composable
private fun TimeField(label: String, stamp: String?, enabled: Boolean = true, onClick: () -> Unit) {
    val time = stamp?.drop(11)?.take(5)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .clickable(enabled = enabled, onClickLabel = "Set $label") { onClick() }
            .alpha(if (enabled) 1f else 0.45f)
            .semantics(mergeDescendants = true) { stateDescription = time ?: "Not set" }
            .padding(vertical = Spacing.xs)
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            time ?: "",
            Modifier.fillMaxWidth().padding(top = Spacing.xs, start = Spacing.xs),
            style = MaterialTheme.typography.titleLarge,
            color = Brand.Gold
        )
        GoldHairline()
    }
}

/**
 * Shares the workout on [date] through Android's share sheet (#11, #84), as plain text or as a branded image (black
 * and gold, drawn by [ShareImages]). A checklist of exercises and their sets, all ticked, picks what's shared;
 * options cover the date, duration, comment and PR marks. The image includes one of the day's progress photos only
 * when it's chosen. Body values are never included. Laid out as FitNotes's (#148): the set checklist, with the
 * options behind Options.
 */
@Composable
fun ShareWorkoutSheet(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sets = snap.setsByDate[date].orEmpty()
    val exercises = remember(sets) { sets.groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.position } }.map { it.key } }
    // Set-level selection (#11): ticking an exercise ticks all of its sets.
    var ticked by remember(date) { mutableStateOf(sets.map { it.id }.toSet()) }
    var asImage by remember { mutableStateOf(false) }
    var withDate by remember { mutableStateOf(true) }
    var withDuration by remember { mutableStateOf(true) }
    var withComment by remember { mutableStateOf(true) }
    var withPrs by remember { mutableStateOf(true) }
    // The day's progress photos; none goes on the image unless one is picked (#11).
    val photos = snap.photosByDate[date].orEmpty()
    var photoIdx by remember(date) { mutableIntStateOf(-1) }

    fun setsOf(exId: Long) = sets.filter { it.exerciseId == exId }
    val chosen = exercises.filter { exId -> setsOf(exId).any { it.id in ticked } }
    val durationSecs = snap.workoutTimes[date].orEmpty().sumOf { Dates.secondsBetween(it.start, it.end) }

    fun text(): String = buildString {
        if (withDate) append("Workout · ").append(Dates.long(date)).append('\n')
        if (withDuration && durationSecs > 0) append("Duration ").append(fmtDuration(durationSecs.toInt())).append('\n')
        chosen.forEach { exId ->
            append('\n').append(snap.exercises[exId]?.name ?: "Exercise").append('\n')
            setsOf(exId).filter { it.id in ticked }.forEachIndexed { i, s ->
                append("  ").append(i + 1).append(". ").append(describeSet(snap, s.weightKg, s.reps, s.distance, s.durationSec, s.exerciseId))
                if (withPrs && s.isPr) append("  (PR)")
                if (!s.comment.isNullOrBlank()) append("  “").append(s.comment).append('”')
                append('\n')
            }
            // The exercise's own comment follows its sets when comments are included (#107).
            if (withComment) snap.exerciseComments[date.take(10)]?.get(exId)?.let { append("  “").append(it).append("”\n") }
        }
        if (withComment) snap.workoutComments[date]?.forEach { append('\n').append('“').append(it).append("”\n") }
        append("\nLogged with FitLens")
    }

    /** The image's content, without the photo, which is loaded off the main thread while the image is drawn. */
    fun card(): ShareImages.WorkoutCard {
        val n = ticked.size
        val subtitle = listOfNotNull(
            "Workout",
            fmtDuration(durationSecs.toInt()).takeIf { withDuration && durationSecs > 0 },
            "$n set${if (n == 1) "" else "s"}"
        ).joinToString("  ·  ")
        return ShareImages.WorkoutCard(
            title = if (withDate) Dates.long(date) else "Workout",
            subtitle = subtitle,
            exercises = chosen.map { exId ->
                ShareImages.CardExercise(
                    snap.exercises[exId]?.name ?: "Exercise",
                    setsOf(exId).filter { it.id in ticked }.map { s ->
                        describeSet(snap, s.weightKg, s.reps, s.distance, s.durationSec, s.exerciseId) to (withPrs && s.isPr)
                    },
                    if (withComment) snap.exerciseComments[date.take(10)]?.get(exId) else null
                )
            },
            comments = if (withComment) snap.workoutComments[date].orEmpty() else emptyList(),
            photo = null
        )
    }

    // FitNotes's Share Workout (#148): the set checklist, then Cancel, Options and Share. FitLens's extras (text or
    // image, what to include, a progress photo) live behind Options.
    var options by remember { mutableStateOf(false) }
    FitSheet(
        title = "Share workout",
        onDismiss = onDismiss,
        confirmLabel = "Share",
        confirmEnabled = ticked.isNotEmpty(),
        secondaryLabel = "Options",
        onSecondary = { options = true },
        onConfirm = {
            if (asImage) {
                val c = card()
                val photo = photos.getOrNull(photoIdx)?.let { snap.photoFile(it) }
                onDismiss()
                ShareImages.share(ctx, "Creating workout image…", ShareImages.fileName("workout", date)) {
                    val bmp = photo?.let { FrameRenderer.loadBitmap(it, 1000, 1300) }
                    try {
                        ShareImages.renderWorkout(ShareImages.WorkoutCard(c.title, c.subtitle, c.exercises, c.comments, bmp))
                    } finally {
                        bmp?.recycle()
                    }
                }
            } else {
                val body = text()
                onDismiss()
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, body)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Workout · ${Dates.long(date)}")
                }
                ctx.startActivity(android.content.Intent.createChooser(send, "Share workout"))
            }
        }
    ) {
        SetChecklist(snap, date, ticked) { ticked = it }
    }
    if (options) {
        FitSheet(title = "Share options", onDismiss = { options = false }, dismissLabel = "Done") {
            SectionLabel("Share as")
            SegmentedSwitch(options = listOf("Text", "Image"), selected = if (asImage) 1 else 0, onSelect = { asImage = it == 1 })
            SectionLabel("Include")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                FilterChip(selected = withDate, onClick = { withDate = !withDate }, label = { Text("Date") })
                FilterChip(selected = withDuration, onClick = { withDuration = !withDuration }, label = { Text("Duration") })
                FilterChip(selected = withComment, onClick = { withComment = !withComment }, label = { Text("Comment") })
                FilterChip(selected = withPrs, onClick = { withPrs = !withPrs }, label = { Text("PR marks") })
            }
            // A progress photo goes on the image only when one is chosen here (#11).
            if (asImage && photos.isNotEmpty()) {
                SectionLabel("Progress photo")
                DropdownPill(
                    "Progress photo",
                    listOf("No photo") + photos.mapIndexed { i, p -> "Photo ${i + 1}" + if (p.pose.isNotBlank()) " · ${p.pose}" else "" },
                    photoIdx + 1
                ) { i -> photoIdx = i - 1 }
            }
        }
    }
}
