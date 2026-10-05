@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import androidx.compose.material3.HorizontalDivider
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.currentWidthBucket
import com.fitlens.companion.ui.design.WidthBucket
import com.fitlens.companion.ui.design.GoldButton
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.TopBarAction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The calendar (#87, #9), laid out after FitNotes: a month grid (swipe or arrows for other months) with category dots,
 * a gold ring for today and the selected day filled gold dusk, the month's workout count, and the selected
 * day's workout below with Open day. Tapping the selected day again opens it too. The list view is All days.
 * The filter (#9) dims every day without a matching set and counts the matches.
 */
@Composable
fun CalendarScreen(snap: Snapshot, nav: Nav) {
    val today = Dates.today()
    var selected by rememberSaveable { mutableStateOf(today) }
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.from(LocalDate.now()).toString()) }
    val month = YearMonth.parse(monthStr)
    val colors = LocalChartColors.current
    val shift by rememberUpdatedState<(Long) -> Unit>({ n -> monthStr = month.plusMonths(n).toString() })
    val device by Settings.device.collectAsState()
    val filter = remember(device.calendarFilter) { CalendarFilter.decode(device.calendarFilter) }
    val matches = rememberDerived("calendarFilter", snap.trainingKey, filter) { filter.days(snap) }.orEmpty()
    var filtering by remember { mutableStateOf(false) }
    var overview by remember { mutableStateOf<Long?>(null) }
    var sharing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = Dates.monthYear(month.atDay(1)),
            onBack = { nav.pop() },
            actions = listOf(
                TopBarAction(Icons.Filled.Home, stringResource(R.string.day_go_today)) {
                    selected = today
                    monthStr = YearMonth.from(LocalDate.now()).toString()
                },
                TopBarAction(Icons.Filled.Search, stringResource(if (filter.active) R.string.cal_change_filter else R.string.cal_filter_days)) { filtering = true },
                TopBarAction(Icons.Filled.List, stringResource(R.string.cal_every_day)) { nav.push(Screen.Timeline) }
            ),
            // Share the selected day's workout (#87), offered only when that day has sets to share.
            overflow = if (snap.setsByDate[selected].isNullOrEmpty()) emptyList()
                else listOf(MenuAction(stringResource(R.string.cal_share_day, Dates.medium(selected))) { sharing = true })
        )
        if (filter.active) FilterBar(snap, filter, month, matches, onEdit = { filtering = true }) {
            Settings.updateDevice { it.copy(calendarFilter = null) }
        }
        // The month and its legend. On wide screens (#87) the selected day sits beside it rather than below.
        val monthGrid: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { shift(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cal_previous_month))
                }
                MonthCount(snap, month, Modifier.weight(1f))
                IconButton(onClick = { shift(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cal_next_month))
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
                                    val dim = filter.active && date !in matches
                                    DayCell(snap, date, dayNum, date == selected, colors.accent, colors.series, dim) {
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
                Dot(colors.accent, 6.dp); Text(" " + stringResource(R.string.cal_legend_photo) + "    ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Dot(colors.series, 6.dp); Text(" " + stringResource(R.string.cal_legend_measurement) + "    ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.cal_legend_other), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val dayPanel: @Composable () -> Unit = {
            SelectedDay(snap, selected, onOverview = { overview = it }) { nav.home(selected) }
        }
        if (currentWidthBucket() == WidthBucket.Expanded) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { monthGrid() }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    GoldHairline()
                    dayPanel()
                }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                monthGrid()
                GoldHairline()
                dayPanel()
            }
        }
    }
    overview?.let { id -> ExerciseOverviewSheet(snap, nav, id, selected) { overview = null } }
    if (sharing) ShareWorkoutSheet(snap, selected) { sharing = false }
    if (filtering) {
        CalendarFilterSheet(
            snap,
            filter,
            onApply = { f -> Settings.updateDevice { it.copy(calendarFilter = f.encode()) } },
            onDismiss = { filtering = false }
        )
    }
}

/** The filter in words, the matches this month and in all, and Clear (#9). Tap to change it. */
@Composable
private fun FilterBar(snap: Snapshot, filter: CalendarFilter, month: YearMonth, matches: Set<String>, onEdit: () -> Unit, onClear: () -> Unit) {
    val prefix = month.toString()
    val inMonth = matches.count { it.startsWith(prefix) }
    val words = filter.describe(LocalContext.current.resources, snap)
    val changeLabel = stringResource(R.string.cal_change_filter)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brand.Graphite)
            .clickable(onClickLabel = changeLabel, onClick = onEdit)
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(words, style = MaterialTheme.typography.bodyMedium, color = Brand.GoldLight, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                (pluralStringResource(R.plurals.cal_days_month, inMonth, inMonth) + "  ·  " +
                    pluralStringResource(R.plurals.cal_days_all, matches.size, matches.size)).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClear, modifier = Modifier.heightIn(min = Spacing.touch)) { Text(stringResource(R.string.cal_clear)) }
    }
}

/** "12 workouts this month", with the month's photo count. */
@Composable
private fun MonthCount(snap: Snapshot, month: YearMonth, modifier: Modifier) {
    val prefix = month.toString()
    val workouts = snap.setsByDate.keys.count { it.startsWith(prefix) }
    val photos = snap.photosByDate.filterKeys { it.startsWith(prefix) }.values.sumOf { it.size }
    Text(
        pluralStringResource(R.plurals.cal_workouts_month, workouts, workouts) +
            if (photos > 0) " · " + pluralStringResource(R.plurals.cal_photos, photos, photos) else "",
        modifier,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DayCell(
    snap: Snapshot, date: String, dayNum: Int, isSelected: Boolean, photoColor: Color, measureColor: Color,
    dimmed: Boolean, onClick: () -> Unit
) {
    val photos = snap.photosByDate[date]
    val hasRecords = snap.recordsByDate.containsKey(date)
    val sets = snap.setsByDate[date]
    val isToday = date == Dates.today()
    val shape = FitShapes.row
    val cats = remember(sets) {
        sets?.map { it.exerciseId }?.distinct()?.mapNotNull { snap.categoryOf(it) }?.distinctBy { it.id }?.take(3).orEmpty()
    }
    val res = LocalContext.current.resources
    val spoken = listOfNotNull(
        Dates.long(date),
        sets?.map { it.exerciseId }?.distinct()?.size?.let { n -> res.getQuantityString(R.plurals.cal_spoken_workout, n, n) },
        if (!photos.isNullOrEmpty()) res.getString(R.string.cal_spoken_photo) else null,
        if (hasRecords) res.getString(R.string.cal_spoken_measurements) else null,
        if (isToday) res.getString(R.string.cal_spoken_today) else null,
        if (dimmed) res.getString(R.string.cal_spoken_dimmed) else null
    ).joinToString(", ")
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(
                when {
                    isSelected -> Brand.GoldDusk
                    sets != null -> Brand.SurfaceHigh
                    else -> Brand.Surface
                }
            )
            .then(if (isToday) Modifier.border(2.dp, Brand.Gold, shape) else Modifier)
            // With a filter on, days without a matching set fade back so the matches stand out (#9).
            .alpha(if (dimmed && !isSelected) 0.3f else 1f)
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
private fun SelectedDay(snap: Snapshot, date: String, onOverview: (Long) -> Unit, onOpen: () -> Unit) {
    val sets = snap.setsByDate[date].orEmpty()
    val records = snap.recordsByDate[date].orEmpty()
    val photos = snap.photosByDate[date].orEmpty()
    val byExercise = remember(sets) { sets.groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.position } } }
    val secs = snap.workoutTimes[date].orEmpty().sumOf { Dates.secondsBetween(it.start, it.end) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // The selected day under a FitNotes heading (#144).
        SectionLabel(historyDay(date))
        if (sets.isEmpty() && records.isEmpty() && photos.isEmpty()) {
            Text(stringResource(R.string.cal_nothing_logged), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sets.isNotEmpty()) {
            Text(
                listOfNotNull(
                    if (secs > 0) fmtDuration(secs.toInt()) else null,
                    pluralStringResource(R.plurals.cal_exercises, byExercise.size, byExercise.size),
                    pluralStringResource(R.plurals.day_sets, sets.size, sets.size)
                ).joinToString("  ·  ").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // As FitNotes shows the chosen day (#144): the body values as name / value rows, then each exercise under its
        // name in capitals over a rule, with its sets in columns. Tapping an exercise opens its overview (#26).
        records.forEach { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch), verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(fmtNum(r.value), style = MaterialTheme.typography.titleMedium)
                if (r.unit.isNotBlank()) Text(" ${r.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = Brand.Hairline)
        }
        val overviewLabel = stringResource(R.string.cal_show_overview)
        byExercise.forEach { (exId, exSets) ->
            val fields = setFields(snap, exId, exSets)
            Column(Modifier.fillMaxWidth().clickable(onClickLabel = overviewLabel) { onOverview(exId) }.padding(top = Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(snap.exercises[exId]?.name ?: stringResource(R.string.drawer_exercise_fallback), Modifier.weight(1f))
                    if (exSets.any { it.isPr }) Text(" " + stringResource(R.string.cal_pr), style = MaterialTheme.typography.labelMedium, color = Brand.Gold)
                }
                exSets.forEachIndexed { i, s ->
                    val marks = setMarks(s)
                    com.fitlens.companion.ui.design.SetRow(
                        index = i + 1,
                        summary = describeSet(LocalContext.current.resources, snap, s.weightKg, s.reps, s.distance, s.durationSec, s.exerciseId),
                        cells = setCells(LocalContext.current.resources, snap, fields, s),
                        comment = s.comment,
                        isPr = s.isPr,
                        framed = false,
                        showIndex = false,
                        badge = marks.badge,
                        badgeSpoken = marks.badgeSpoken,
                        effort = marks.effort,
                        effortSpoken = marks.effortSpoken
                    )
                }
                // The exercise's comment in this workout (#107).
                snap.exerciseComments[date.take(10)]?.get(exId)?.let { note ->
                    Text("\u201C$note\u201D", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (photos.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.cal_progress_photos, photos.size, photos.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GoldButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row).padding(top = Spacing.sm)) {
            Text(stringResource(if (sets.isEmpty()) R.string.cal_open_day_log else R.string.cal_open_day))
        }
    }
}
