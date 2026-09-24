package com.fitlens.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Snapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(snap: Snapshot, nav: Nav) {
    val initial = snap.allDates.firstOrNull()?.let { Dates.parse(it) } ?: LocalDate.now()
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.from(initial).toString()) }
    val month = YearMonth.parse(monthStr)
    val colors = LocalChartColors.current

    Column(Modifier.fillMaxSize()) {
        PlainTopBar("Calendar")
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthStr = month.minusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                Dates.monthYear(month.atDay(1)),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clickable { monthStr = YearMonth.from(initial).toString() }
            )
            IconButton(onClick = { monthStr = month.plusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        // Weekday header (Monday first)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            DayOfWeek.values().forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            val first = month.atDay(1)
            val offset = first.dayOfWeek.value - 1
            val days = month.lengthOfMonth()
            val cells = offset + days
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(0.72f).padding(2.dp)) {
                            if (dayNum in 1..days) {
                                val date = month.atDay(dayNum).format(Dates.ISO)
                                // Any day opens, whether or not it has data: that's how a workout gets logged on
                                // a day FitLens hasn't seen before (#10).
                                DayCell(snap, date, dayNum, colors.accent, colors.series) {
                                    nav.push(Screen.Day(date))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.padding(8.dp))
            // Legend
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                Dot(colors.accent); Text(" Photo   ", style = MaterialTheme.typography.labelMedium)
                Dot(colors.series); Text(" Measurement", style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "Other dots show workout categories. Bold numbers are workout days.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            MonthSummary(snap, month)
        }
    }
}

@Composable
private fun DayCell(snap: Snapshot, date: String, dayNum: Int, photoColor: Color, measureColor: Color, onClick: () -> Unit) {
    val photos = snap.photosByDate[date]
    val hasRecords = snap.recordsByDate.containsKey(date)
    val sets = snap.setsByDate[date]
    val isToday = date == Dates.today()
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.fillMaxSize().clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (snap.allDates.contains(date)) 0.9f else 0.35f))
            .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
    ) {
        if (!photos.isNullOrEmpty()) {
            PhotoThumb(snap, photos.first(), Modifier.fillMaxSize(), sizePx = 200)
            Box(Modifier.fillMaxSize().background(Color(0x55000000)))
        }
        Text(
            "$dayNum",
            Modifier.padding(4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (sets != null) FontWeight.Bold else FontWeight.Normal,
            color = if (!photos.isNullOrEmpty()) Color.White else MaterialTheme.colorScheme.onSurface
        )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (!photos.isNullOrEmpty()) Dot(photoColor, 6.dp)
            if (hasRecords) Dot(measureColor, 6.dp)
            sets?.map { it.exerciseId }?.distinct()?.mapNotNull { snap.categoryOf(it) }?.distinctBy { it.id }?.take(3)?.forEach {
                Dot(Color(it.colour), 6.dp)
            }
        }
    }
}

@Composable
private fun MonthSummary(snap: Snapshot, month: YearMonth) {
    val prefix = month.toString()
    val workoutDays = snap.setsByDate.keys.count { it.startsWith(prefix) }
    val photoDays = snap.photosByDate.keys.count { it.startsWith(prefix) }
    val photoCount = snap.photosByDate.filterKeys { it.startsWith(prefix) }.values.sumOf { it.size }
    val bw = snap.bodyweightName?.let { name ->
        val list = snap.dailySeries(name).filter { it.date.startsWith(prefix) }
        if (list.size >= 2) "${name}: ${com.fitlens.companion.data.fmtNum(list.first().value)} → ${com.fitlens.companion.data.fmtNum(list.last().value)} ${list.last().unit}" else null
    }
    Column(Modifier.padding(8.dp)) {
        Text("This month", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text("$workoutDays workouts · $photoCount photos on $photoDays days", style = MaterialTheme.typography.bodyMedium)
        if (bw != null) Text(bw, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.width(1.dp))
}
