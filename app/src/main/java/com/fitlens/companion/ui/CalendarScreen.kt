@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.TopBarAction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The calendar (#87, #9), laid out after FitNotes: a month grid (swipe or arrows for other months) with category dots,
 * a gold ring for today and the selected day filled imperial purple, the month's workout count, and the selected
 * day's workout below with Open day. Tapping the selected day again opens it too. The list view is All days.
 */
@Composable
fun CalendarScreen(snap: Snapshot, nav: Nav) {
    val today = Dates.today()
    var selected by rememberSaveable { mutableStateOf(today) }
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.from(LocalDate.now()).toString()) }
    val month = YearMonth.parse(monthStr)
    val colors = LocalChartColors.current
    val shift by rememberUpdatedState<(Long) -> Unit>({ n -> monthStr = month.plusMonths(n).toString() })

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = Dates.monthYear(month.atDay(1)),
            onBack = { nav.pop() },
            actions = listOf(
                TopBarAction(Icons.Filled.Home, "Go to today") {
                    selected = today
                    monthStr = YearMonth.from(LocalDate.now()).toString()
                },
                TopBarAction(Icons.Filled.List, "List of every day") { nav.push(Screen.Timeline) }
            )
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { shift(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                MonthCount(snap, month, Modifier.weight(1f))
                IconButton(onClick = { shift(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            // Weekday header, starting on the chosen first day of the week (#7).
            val weekStart = DayOfWeek.of(snap.weekStart)
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
                (0L until 7L).map { weekStart.plus(it) }.forEach { d ->
                    Text(
                        d.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                        Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // A horizontal swipe on the grid moves a month, as in FitNotes.
            Column(
                Modifier.padding(horizontal = Spacing.sm).pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            val threshold = 72.dp.toPx()
                            if (total > threshold) shift(-1) else if (total < -threshold) shift(1)
                            total = 0f
                        },
                        onDragCancel = { total = 0f }
                    ) { change, amount ->
                        change.consume()
                        total += amount
                    }
                }
            ) {
                val first = month.atDay(1)
                val offset = (first.dayOfWeek.value - weekStart.value + 7) % 7
                val days = month.lengthOfMonth()
                val rows = (offset + days + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val dayNum = r * 7 + c - offset + 1
                            Box(Modifier.weight(1f).aspectRatio(0.8f).padding(2.dp)) {
                                if (dayNum in 1..days) {
                                    val date = month.atDay(dayNum).format(Dates.ISO)
                                    DayCell(snap, date, dayNum, date == selected, colors.accent, colors.series) {
                                        // A second tap on the selected day opens it, like "Go!" in FitNotes (#9).
                                        if (selected == date) nav.home(date) else selected = date
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Dot(colors.accent, 6.dp); Text(" Photo    ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Dot(colors.series, 6.dp); Text(" Measurement    ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Other dots: categories", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GoldHairline()
            SelectedDay(snap, selected) { nav.home(selected) }
        }
    }
}

/** "12 workouts this month", with the month's photo count. */
@Composable
private fun MonthCount(snap: Snapshot, month: YearMonth, modifier: Modifier) {
    val prefix = month.toString()
    val workouts = snap.setsByDate.keys.count { it.startsWith(prefix) }
    val photos = snap.photosByDate.filterKeys { it.startsWith(prefix) }.values.sumOf { it.size }
    Text(
        (if (workouts == 1) "1 workout" else "$workouts workouts") + " this month" +
            if (photos > 0) " · $photos photo${if (photos == 1) "" else "s"}" else "",
        modifier,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DayCell(snap: Snapshot, date: String, dayNum: Int, isSelected: Boolean, photoColor: Color, measureColor: Color, onClick: () -> Unit) {
    val photos = snap.photosByDate[date]
    val hasRecords = snap.recordsByDate.containsKey(date)
    val sets = snap.setsByDate[date]
    val isToday = date == Dates.today()
    val shape = FitShapes.row
    val cats = remember(sets) {
        sets?.map { it.exerciseId }?.distinct()?.mapNotNull { snap.categoryOf(it) }?.distinctBy { it.id }?.take(3).orEmpty()
    }
    val spoken = buildString {
        append(Dates.long(date))
        if (sets != null) append(", workout, ").append(sets.map { it.exerciseId }.distinct().size).append(" exercises")
        if (!photos.isNullOrEmpty()) append(", photo")
        if (hasRecords) append(", measurements")
        if (isToday) append(", today")
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(
                when {
                    isSelected -> Brand.ImperialPurple
                    sets != null -> Brand.SurfaceHigh
                    else -> Brand.Surface
                }
            )
            .then(if (isToday) Modifier.border(2.dp, Brand.Gold, shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken; this.selected = isSelected }
    ) {
        if (!photos.isNullOrEmpty() && !isSelected) {
            PhotoThumb(snap, photos.first(), Modifier.fillMaxSize(), sizePx = 200)
            Box(Modifier.fillMaxSize().background(Brand.Black.copy(alpha = 0.45f)))
        }
        Text(
            "$dayNum",
            Modifier.padding(4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (sets != null) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) Brand.GoldLight else Brand.Ivory
        )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (!photos.isNullOrEmpty()) Dot(photoColor, 6.dp)
            if (hasRecords) Dot(measureColor, 6.dp)
            cats.forEach { Dot(categoryColour(it.colour), 6.dp) }
        }
    }
}

/** The selected day below the grid: its exercises, body values and photos, and Open day (#87). */
@Composable
private fun SelectedDay(snap: Snapshot, date: String, onOpen: () -> Unit) {
    val sets = snap.setsByDate[date].orEmpty()
    val records = snap.recordsByDate[date].orEmpty()
    val photos = snap.photosByDate[date].orEmpty()
    val byExercise = remember(sets) { sets.groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.position } } }
    val secs = snap.workoutTimes[date].orEmpty().sumOf { Dates.secondsBetween(it.start, it.end) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(Dates.long(date), style = MaterialTheme.typography.titleLarge)
        if (sets.isEmpty() && records.isEmpty() && photos.isEmpty()) {
            Text("Nothing logged on this day.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sets.isNotEmpty()) {
            Text(
                listOfNotNull(
                    if (secs > 0) fmtDuration(secs.toInt()) else null,
                    "${byExercise.size} exercise${if (byExercise.size == 1) "" else "s"}",
                    "${sets.size} set${if (sets.size == 1) "" else "s"}"
                ).joinToString("  ·  ").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        byExercise.forEach { (exId, exSets) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(categoryColour(snap.categoryOf(exId)?.colour ?: 0), 8.dp)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(snap.exercises[exId]?.name ?: "Exercise", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        exSets.joinToString(", ") { describeSet(snap, it.weightKg, it.reps, it.distance, it.durationSec) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (exSets.any { it.isPr }) Text("PR", style = MaterialTheme.typography.labelMedium, color = Brand.Gold)
            }
        }
        if (records.isNotEmpty()) {
            Text(
                records.joinToString("  ·  ") { "${it.name} ${fmtNum(it.value)} ${it.unit}".trim() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (photos.isNotEmpty()) {
            Text(
                "${photos.size} progress photo${if (photos.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row).padding(top = Spacing.sm)) {
            Text(if (sets.isEmpty()) "Open day to log" else "Open day")
        }
    }
}
