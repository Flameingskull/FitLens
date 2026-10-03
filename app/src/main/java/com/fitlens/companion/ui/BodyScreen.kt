package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.DropdownPill
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.TopBarAction
import com.fitlens.companion.ui.design.FitTabRow
import com.fitlens.companion.ui.design.FitTopBar
import androidx.compose.foundation.background
import androidx.compose.runtime.key
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import com.fitlens.companion.ui.design.SectionLabel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import com.fitlens.companion.ui.design.SetCell
import com.fitlens.companion.ui.design.SetRow as SetRowView

val RANGES = listOf("1M" to 30L, "3M" to 91L, "6M" to 182L, "1Y" to 365L, "All" to 0L)

/** Points for a date range; range 0 = all. The range is anchored on the latest data point. */
fun <T> inRange(items: List<T>, range: Long, dateOf: (T) -> String): List<T> {
    if (range <= 0 || items.isEmpty()) return items
    val end = Dates.epochDay(dateOf(items.last()))
    return items.filter { Dates.epochDay(dateOf(it)) >= end - range }
}

/**
 * The Body Tracker, laid out as FitNotes's (its screenshots of 2026-09-23, #144): one screen with TRACK, HISTORY and
 * GRAPH tabs across every measurement. Track lists each enabled measurement with how long ago it was last logged and
 * its latest value and change ("Tap to record a value" when there's none); History lists every value, newest day
 * first, filtered to one measurement or All; Graph shows one measurement, chosen from a dropdown. The pencil opens
 * Measurements and ⋮ reorders. Tapping a measurement opens it to log or edit a value ([BodyMeasurementScreen]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScreen(snap: Snapshot, nav: Nav) {
    // Every enabled measurement, logged or not, as FitNotes lists them.
    val measurements = snap.allMeasurements.filter { it.enabled }
    var ordering by remember { mutableStateOf(false) }
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = "Body Tracker",
            onBack = LocalNavBack.current,
            actions = listOf(TopBarAction(Icons.Filled.Edit, "Measurements") { nav.push(Screen.Measurements) }),
            overflow = listOfNotNull(
                if (measurements.size > 1) MenuAction("Reorder measurements") { ordering = true } else null,
                MenuAction("Measurements") { nav.push(Screen.Measurements) }
            )
        )
        if (measurements.isEmpty()) {
            EmptyState("No measurements yet", "Add the standard measurements and your own, or import a FitNotes backup.") {
                Row {
                    TextButton(onClick = { nav.push(Screen.Measurements) }) { Text("Measurements") }
                    TextButton(onClick = { nav.push(Screen.SettingsPage(SettingsSection.Import)) }) { Text("Import from FitNotes") }
                }
            }
        } else {
            FitTabRow(
                titles = listOf("Track", "History", "Graph"),
                selected = pager.currentPage,
                onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } }
            )
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(measurements, key = { it.name }) { m ->
                            MeasurementListRow(snap, m) { nav.push(Screen.BodyMeasurement(m.name)) }
                            HorizontalDivider(color = Brand.Hairline)
                        }
                    }
                    1 -> BodyHistoryAll(snap, measurements) { r -> nav.push(Screen.BodyMeasurement(r.name)) }
                    else -> {
                        var graphOf by rememberSaveable { mutableStateOf(measurements.first().name) }
                        val names = measurements.map { it.name }
                        val shown = graphOf.takeIf { it in names } ?: names.first()
                        Column {
                            // FitNotes's "GRAPH: Bodyweight" picker over the graph.
                            DropdownPill("Graph", names, names.indexOf(shown), Modifier.padding(horizontal = 4.dp)) { graphOf = names[it] }
                            key(shown) { BodyGraphPane(snap, nav, shown) }
                        }
                    }
                }
            }
        }
    }
    if (ordering) MeasurementOrderSheet(measurements) { ordering = false }
}

/** How long ago a value was logged, as FitNotes says it: "4 hours ago", "5 days ago", "1 month ago", "2 years ago". */
internal fun agoText(date: String, time: String): String {
    val at = runCatching { java.time.LocalDateTime.parse(date.take(10) + "T" + time.take(5).ifBlank { "12:00" }) }.getOrNull()
        ?: return Dates.medium(date)
    val now = java.time.LocalDateTime.now()
    val mins = java.time.Duration.between(at, now).toMinutes().coerceAtLeast(0)
    fun n(v: Long, unit: String) = "$v $unit${if (v == 1L) "" else "s"} ago"
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> n(mins, "minute")
        mins < 60 * 24 -> n(mins / 60, "hour")
        mins < 60 * 24 * 30 -> n(mins / (60 * 24), "day")
        mins < 60 * 24 * 365 -> n(mins / (60 * 24 * 30), "month")
        else -> n(mins / (60 * 24 * 365), "year")
    }
}

/**
 * One measurement on the Track tab, as FitNotes lists it: the name in bold with how long ago it was logged (or "Tap
 * to record a value"), and on the right the latest value with its unit, the change since the value before under it.
 */
@Composable
private fun MeasurementListRow(snap: Snapshot, m: MeasurementDef, onOpen: () -> Unit) {
    val recs = snap.recordsByName[m.name].orEmpty()
    val last = recs.lastOrNull()
    val prev = recs.getOrNull(recs.size - 2)
    val unit = m.unit.ifBlank { last?.unit.orEmpty() }
    val change = if (last != null && prev != null) changeText(prev, last) else null
    val spoken = m.name + ", " + (last?.let { "${fmtNum(it.value)} $unit on ${Dates.medium(it.date)}" } ?: "nothing logged yet") +
        (change?.let { ", $it" } ?: "")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClickLabel = "Open ${m.name}", onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            Text(
                last?.let { agoText(it.date, it.time) } ?: "Tap to record a value",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (last != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = Spacing.md)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmtNum(last.value), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
                    if (unit.isNotBlank()) Text(" $unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (change != null && prev != null) {
                    Text(change, style = MaterialTheme.typography.bodySmall, color = changeColour(m, prev.value, last.value), maxLines = 1)
                }
            }
        }
    }
}

/**
 * The History tab, as FitNotes's (#144): a "History" filter (All, or one measurement), then every value newest first
 * under a band for its day ("Wednesday, September 23"), each with its name and time on the left and its value and
 * change on the right. Tapping a value opens its measurement.
 */
@Composable
private fun BodyHistoryAll(snap: Snapshot, measurements: List<MeasurementDef>, onOpen: (MRecord) -> Unit) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val names = measurements.map { it.name }
    val all = remember(snap, filter) {
        names.filter { filter == null || it == filter }.flatMap { snap.recordsByName[it].orEmpty() }
            .sortedWith(compareByDescending<MRecord> { it.date.take(10) }.thenByDescending { it.time })
    }
    val byDay = remember(all) { all.groupBy { it.date.take(10) } }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "filter") {
            DropdownPill("History", listOf("All") + names, (filter?.let { names.indexOf(it) + 1 } ?: 0), Modifier.padding(horizontal = 4.dp)) { i ->
                filter = if (i == 0) null else names[i - 1]
            }
        }
        if (all.isEmpty()) {
            item(key = "none") {
                Text("Nothing logged yet.", Modifier.padding(Spacing.lg), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        byDay.forEach { (day, recs) ->
            item(key = "d$day") {
                // FitNotes's day band: the date in normal case on a slightly lighter strip.
                Text(
                    Dates.parse(day)?.format(java.time.format.DateTimeFormatter.ofPattern(
                        if (Dates.parse(day)?.year == java.time.LocalDate.now().year) "EEEE, MMMM d" else "EEEE, MMMM d, yyyy",
                        java.util.Locale.getDefault()
                    )) ?: day,
                    Modifier.fillMaxWidth().background(Brand.SurfaceHigh).padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
                )
            }
            items(recs, key = { "r${it.id}" }) { r ->
                val history = snap.recordsByName[r.name].orEmpty()
                val prev = history.getOrNull(history.indexOfFirst { it.id == r.id } - 1)
                val def = measurements.firstOrNull { it.name == r.name }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.row)
                        .clickable(onClickLabel = "Open ${r.name}") { onOpen(r) }
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        if (r.time.isNotBlank()) Text(r.time.take(5), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(fmtNum(r.value), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
                            if (r.unit.isNotBlank()) Text(" ${r.unit}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (prev != null) {
                            Text(changeText(prev, r), style = MaterialTheme.typography.bodySmall, color = changeColour(def, prev.value, r.value), maxLines = 1)
                        }
                    }
                }
                HorizontalDivider(color = Brand.Hairline)
            }
        }
    }
}

/** A measurement's Graph tab: range, trend and from-zero options, the chart with its goal line and photo days, and stats. */
@Composable
internal fun BodyGraphPane(snap: Snapshot, nav: Nav, selectedName: String) {
    val def = snap.allMeasurements.firstOrNull { it.name == selectedName }
    var rangeIdx by rememberSaveable { mutableIntStateOf(4) }
    var selectedPoint by remember(selectedName, rangeIdx) { mutableStateOf<Int?>(null) }
    var showTrend by rememberSaveable { mutableStateOf(false) }
    var fromZero by rememberSaveable { mutableStateOf(false) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    val all = remember(snap, selectedName) { snap.dailySeries(selectedName) }
    val shown = remember(all, rangeIdx) { inRange(all, RANGES[rangeIdx].second) { it.date } }
    // Line, bar, area or step, remembered for each measurement (#137).
    val (kind, setKind) = rememberChartKind("body:$selectedName")
    if (all.isEmpty()) {
        EmptyState("Nothing to graph yet", "Log $selectedName on the Track tab and it is graphed here.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            // Range, trend and from-zero in one compact row (#115).
            GraphOptionChips(
                rangeIdx, { rangeIdx = it },
                showTrend, { showTrend = !showTrend },
                fromZero, { fromZero = !fromZero },
                kind = kind, onKind = setKind,
                trailing = { ExpandGraphButton { fullScreen = true } }
            )
        }
        item {
            val points = rememberChartData(shown) {
                shown.map { ChartPoint(Dates.epochDay(it.date), it.value, it.date) }
            } ?: emptyList()
            val photoDays = remember(snap) { snap.photosByDate.keys.map { Dates.epochDay(it) }.toSet() }
            val unit = def?.unit ?: shown.lastOrNull()?.unit ?: ""
            FitChart(
                listOf(LineSeries(selectedName, points)),
                Modifier.padding(horizontal = 8.dp),
                kind = kind,
                photoDays = photoDays,
                goal = if (def != null && def.goalType != 0 && def.goalValue > 0) def.goalValue else null,
                selected = selectedPoint?.let { ChartSelection(0, it) },
                onSelect = { selectedPoint = it.index; ChartHints.tapped() },
                unit = unit,
                showTrend = showTrend,
                yFromZero = fromZero,
                onExpand = { ChartHints.expanded(); fullScreen = true }
            )
            ChartHint()
            if (fullScreen) {
                FullScreenChart(
                    selectedName,
                    onDismiss = { fullScreen = false },
                    controls = {
                        GraphOptionChips(
                            rangeIdx, { rangeIdx = it },
                            showTrend, { showTrend = !showTrend },
                            fromZero, { fromZero = !fromZero },
                            kind = kind, onKind = setKind
                        )
                    },
                    valueZoom = kind != ChartKind.BAR,
                    footer = {
                        selectedPoint?.let { points.getOrNull(it) }?.let { p ->
                            Text(
                                "${Dates.long(p.date)}: ${fmtNum(p.y, 1)} $unit",
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                ) { vp, h, resetZoom ->
                    FitChart(
                        listOf(LineSeries(selectedName, points)),
                        kind = kind,
                        height = h,
                        photoDays = photoDays,
                        goal = if (def != null && def.goalType != 0 && def.goalValue > 0) def.goalValue else null,
                        selected = selectedPoint?.let { ChartSelection(0, it) },
                        onSelect = { selectedPoint = it.index },
                        unit = unit,
                        showTrend = showTrend,
                        yFromZero = fromZero,
                        viewport = vp,
                        onExpand = resetZoom
                    )
                }
            }
            Text(
                "Tap the graph to see that day. Gold ticks and rings mark days with photos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item {
            val sel = selectedPoint?.let { shown.getOrNull(it) }
            if (sel != null) SelectedPointCard(snap, nav, sel)
        }
        item { StatsBlock(shown, def?.unit ?: shown.lastOrNull()?.unit ?: "") }
    }
}

@Composable
private fun SelectedPointCard(snap: Snapshot, nav: Nav, r: MRecord) {
    val photo = snap.photosByDate[r.date]?.firstOrNull()
    val near = if (photo == null) nearestPhoto(snap, r.date, 7) else null
    Card(Modifier.fillMaxWidth().padding(12.dp).clickable { nav.push(Screen.Day(r.date)) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val shownPhoto = photo ?: near
            if (shownPhoto != null) {
                PhotoThumb(snap, shownPhoto, Modifier.size(width = 90.dp, height = 120.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(Dates.long(r.date), style = MaterialTheme.typography.titleMedium)
                Text("${r.name}: ${fmtNum(r.value)} ${r.unit}", style = MaterialTheme.typography.headlineSmall)
                if (!r.comment.isNullOrBlank()) Text("“${r.comment}”", style = MaterialTheme.typography.bodySmall)
                if (photo == null && near != null) {
                    Text("Photo shown is from ${Dates.medium(near.date ?: "")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (photo == null) {
                    Text("No photo within a week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Open day →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun StatsBlock(list: List<MRecord>, unit: String) {
    if (list.isEmpty()) return
    val first = list.first()
    val last = list.last()
    val min = list.minBy { it.value }
    val max = list.maxBy { it.value }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            LabelValue("Start · ${Dates.short(first.date)}", "${fmtNum(first.value)} $unit", Modifier.weight(1f))
            LabelValue("Latest · ${Dates.short(last.date)}", "${fmtNum(last.value)} $unit", Modifier.weight(1f))
            LabelValue("Change", "${fmtSigned(last.value - first.value)} $unit", Modifier.weight(1f), deltaColour(last.value - first.value, Color.Unspecified))
        }
        Row(Modifier.fillMaxWidth()) {
            LabelValue("Lowest · ${Dates.short(min.date)}", "${fmtNum(min.value)} $unit", Modifier.weight(1f))
            LabelValue("Highest · ${Dates.short(max.date)}", "${fmtNum(max.value)} $unit", Modifier.weight(1f))
            LabelValue("Entries", "${list.size}", Modifier.weight(1f))
        }
        val days = Dates.epochDay(last.date) - Dates.epochDay(first.date)
        if (days >= 14) {
            val perWeek = (last.value - first.value) / days * 7
            Text("Average ${fmtSigned(perWeek, 2)} $unit per week over ${days} days", style = MaterialTheme.typography.bodyMedium, color = deltaColour(perWeek, Color.Unspecified))
        }
    }
}

/**
 * A measurement's History tab, as the exercise History: each day, newest first, under its date, with the change since
 * the value before and a dot for a day with photos. Tapping a value opens it on Track for Update or Delete.
 */
@Composable
internal fun BodyHistoryPane(snap: Snapshot, name: String, onOpen: (MRecord) -> Unit) {
    val records = snap.recordsByName[name].orEmpty()
    val def = snap.allMeasurements.firstOrNull { it.name == name }
    if (records.isEmpty()) {
        EmptyState("No history yet", "Every value you log for $name appears here, newest first.")
        return
    }
    val byDate = remember(records) { records.groupBy { it.date.take(10) }.toSortedMap() }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        byDate.entries.reversed().forEach { (d, l) ->
            item(key = d) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // FitNotes's day heading, as on the exercise screen's History (#144).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(historyDay(d), Modifier.weight(1f))
                        if (snap.photosByDate.containsKey(d)) Dot(LocalChartColors.current.accent)
                    }
                    l.forEachIndexed { i, r ->
                        val prev = records.getOrNull(records.indexOf(r) - 1)
                        SetRowView(
                            index = i + 1,
                            summary = "${fmtNum(r.value)} ${r.unit}",
                            cells = valueCells(r),
                            comment = r.comment,
                            framed = false,
                            showIndex = false,
                            noun = "Value",
                            onClick = { onOpen(r) }
                        )
                        // The change since the value before, coloured by the goal's direction (#27); the arrow and
                        // sign keep the meaning without colour.
                        if (prev != null) {
                            Text(
                                changeText(prev, r),
                                style = MaterialTheme.typography.bodySmall,
                                color = changeColour(def, prev.value, r.value),
                                modifier = Modifier.padding(start = 18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A value and the time it was logged, in the set-row columns the exercise screen uses. */
internal fun valueCells(r: MRecord): List<SetCell> {
    val time = r.time.take(5)
    return listOfNotNull(
        SetCell(fmtNum(r.value), r.unit, "${fmtNum(r.value)} ${r.unit}"),
        time.takeIf { it.isNotBlank() }?.let { SetCell(it, spoken = "at $it", unitSlot = false) }
    )
}
