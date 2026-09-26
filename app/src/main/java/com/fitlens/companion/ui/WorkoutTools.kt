@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

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
import androidx.compose.ui.semantics.Role
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.ui.design.FitSheet
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
 * The workout's start and finish (#84): set either with a time picker, start the timer now, or stop it. The duration
 * shows live. A day with more than one imported time range is replaced by the single range saved here.
 */
@Composable
fun WorkoutTimeSheet(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val times = snap.workoutTimes[date].orEmpty()
    val first = times.firstOrNull()
    var start by remember { mutableStateOf(first?.start?.takeIf { it.isNotBlank() }) }
    var end by remember { mutableStateOf(first?.end?.takeIf { it.isNotBlank() }) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: start, false: finish
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
        confirmLabel = "Save time",
        confirmEnabled = s != null && (e == null || e >= s),
        onConfirm = { save(s, e) }
    ) {
        Text(Dates.long(date).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (secs > 0) fmtDuration(secs.toInt()) else "—",
            style = MaterialTheme.typography.displaySmall,
            color = Brand.Gold
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) {
                Text("Start ${s?.drop(11)?.take(5) ?: "--:--"}")
            }
            OutlinedButton(onClick = { picking = false }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch), enabled = s != null) {
                Text("Finish ${e?.drop(11)?.take(5) ?: "--:--"}")
            }
        }
        if (isToday) {
            when {
                s == null || e != null -> Button(
                    onClick = { askNotify(); save(WorkoutClock.now(), null); UiEvents.show("Workout timer started") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row)
                ) { Text("Start timer now") }
                else -> Button(
                    onClick = { onDismiss(); WorkoutClock.stop(date, s) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row)
                ) { Text("Stop timer") }
            }
        }
        if (times.size > 1) {
            Text(
                "This day has ${times.size} time ranges from FitNotes. Saving replaces them with the one above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (s != null) {
            TextButton(onClick = { save(null, null) }, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text("Clear the time", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    picking?.let { isStart ->
        val current = (if (isStart) s else e)?.let { Dates.dateTime(it)?.toLocalTime() } ?: LocalTime.now()
        val state = rememberTimePickerState(current.hour, current.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(if (isStart) "Start time" else "Finish time") },
            text = { TimePicker(state = state) },
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

/**
 * Shares the workout on [date] as plain text through Android's share sheet (#11, #84): a checklist of exercises, all
 * ticked, and options for the date, duration, comment and PR marks. Body values are never included.
 */
@Composable
fun ShareWorkoutSheet(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sets = snap.setsByDate[date].orEmpty()
    val exercises = remember(sets) { sets.groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.id } }.map { it.key } }
    var ticked by remember(date) { mutableStateOf(exercises.toSet()) }
    var withDate by remember { mutableStateOf(true) }
    var withDuration by remember { mutableStateOf(true) }
    var withComment by remember { mutableStateOf(true) }
    var withPrs by remember { mutableStateOf(true) }

    fun text(): String = buildString {
        if (withDate) append("Workout · ").append(Dates.long(date)).append('\n')
        if (withDuration) {
            val secs = snap.workoutTimes[date].orEmpty().sumOf { Dates.secondsBetween(it.start, it.end) }
            if (secs > 0) append("Duration ").append(fmtDuration(secs.toInt())).append('\n')
        }
        exercises.filter { it in ticked }.forEach { exId ->
            append('\n').append(snap.exercises[exId]?.name ?: "Exercise").append('\n')
            sets.filter { it.exerciseId == exId }.forEachIndexed { i, s ->
                append("  ").append(i + 1).append(". ").append(describeSet(snap, s.weightKg, s.reps, s.distance, s.durationSec))
                if (withPrs && s.isPr) append("  (PR)")
                if (!s.comment.isNullOrBlank()) append("  “").append(s.comment).append('”')
                append('\n')
            }
        }
        if (withComment) snap.workoutComments[date]?.forEach { append('\n').append('“').append(it).append("”\n") }
        append("\nLogged with FitLens")
    }

    FitSheet(
        title = "Share workout",
        onDismiss = onDismiss,
        confirmLabel = "Share",
        confirmEnabled = ticked.isNotEmpty(),
        onConfirm = {
            val body = text()
            onDismiss()
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, body)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Workout · ${Dates.long(date)}")
            }
            ctx.startActivity(android.content.Intent.createChooser(send, "Share workout"))
        }
    ) {
        Text("INCLUDE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            FilterChip(selected = withDate, onClick = { withDate = !withDate }, label = { Text("Date") })
            FilterChip(selected = withDuration, onClick = { withDuration = !withDuration }, label = { Text("Duration") })
            FilterChip(selected = withComment, onClick = { withComment = !withComment }, label = { Text("Comment") })
            FilterChip(selected = withPrs, onClick = { withPrs = !withPrs }, label = { Text("PR marks") })
        }
        Text("EXERCISES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        exercises.forEach { exId ->
            val on = exId in ticked
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.row)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { ticked = if (it) ticked + exId else ticked - exId }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = on, onCheckedChange = null)
                Text(
                    "${snap.exercises[exId]?.name ?: "Exercise"} · ${sets.count { it.exerciseId == exId }} sets",
                    Modifier.padding(start = Spacing.sm)
                )
            }
        }
    }
}
