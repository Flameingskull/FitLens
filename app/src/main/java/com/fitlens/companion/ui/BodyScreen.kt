package com.fitlens.companion.ui

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
 * The body tracker, as in FitNotes: every measurement in the user's order, with its latest value. Tapping one opens
 * its own Track, History and Graph tabs ([BodyMeasurementScreen]), laid out like the exercise screen.
 */
@Composable
fun BodyScreen(snap: Snapshot, nav: Nav) {
    // Every enabled measurement, logged or not, as FitNotes lists them.
    val measurements = snap.allMeasurements.filter { it.enabled }
    var ordering by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PlainTopBar("Body tracker") {
            IconButton(onClick = { nav.push(Screen.Measurements) }) { Icon(Icons.Filled.Edit, contentDescription = "Manage measurements") }
            if (measurements.size > 1) IconButton(onClick = { ordering = true }) { Icon(Icons.Filled.Menu, contentDescription = "Reorder measurements") }
        }
        if (measurements.isEmpty()) {
            EmptyState("No measurements yet", "Add the standard measurements and your own, or import a FitNotes backup.") {
                Row {
                    TextButton(onClick = { nav.push(Screen.Measurements) }) { Text("Measurements") }
                    TextButton(onClick = { nav.push(Screen.SettingsPage(SettingsSection.Import)) }) { Text("Import from FitNotes") }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(measurements, key = { it.name }) { m ->
                    MeasurementListRow(snap, m) { nav.push(Screen.BodyMeasurement(m.name)) }
                    GoldHairline()
                }
            }
        }
    }
    if (ordering) MeasurementOrderSheet(measurements) { ordering = false }
}

/** One measurement in the body tracker list: its name, and its latest value and date. */
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
            .heightIn(min = Spacing.row)
            .clickable(onClickLabel = "Open ${m.name}", onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.name, style = MaterialTheme.typography.titleMedium)
            if (last != null) {
                Text(Dates.medium(last.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (change != null && prev != null && last != null) {
                Text(change, style = MaterialTheme.typography.bodySmall, color = changeColour(m, prev.value, last.value))
            }
        }
        Text(
            last?.let { "${fmtNum(it.value)} $unit".trim() } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = Spacing.md)
        )
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
            if (showTrend) trendOf(points)?.let { tr ->
                Text(
                    "Trend: ${if (tr.perMonth >= 0) "+" else ""}${fmtNum(tr.perMonth, 1)} $unit per month",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
                    color = deltaColour(tr.perMonth, MaterialTheme.colorScheme.onSurfaceVariant)
                )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Dates.long(d).uppercase(), style = MaterialTheme.typography.titleSmall, color = Brand.Gold, modifier = Modifier.weight(1f))
                        if (snap.photosByDate.containsKey(d)) Dot(LocalChartColors.current.accent)
                    }
                    HorizontalDivider(Modifier.padding(top = 2.dp, bottom = 4.dp), color = Brand.Gold)
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
                HorizontalDivider()
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
