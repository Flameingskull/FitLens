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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned

val RANGES = listOf("1M" to 30L, "3M" to 91L, "6M" to 182L, "1Y" to 365L, "All" to 0L)

/** Points for a date range; range 0 = all. The range is anchored on the latest data point. */
fun <T> inRange(items: List<T>, range: Long, dateOf: (T) -> String): List<T> {
    if (range <= 0 || items.isEmpty()) return items
    val end = Dates.epochDay(dateOf(items.last()))
    return items.filter { Dates.epochDay(dateOf(it)) >= end - range }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScreen(snap: Snapshot, nav: Nav) {
    val measurements = snap.usedMeasurements
    var chosenName by rememberSaveable { mutableStateOf(snap.bodyweightName ?: "") }
    val selectedName = if (measurements.none { it.name == chosenName } && measurements.isNotEmpty()) measurements.first().name else chosenName
    var rangeIdx by rememberSaveable { mutableIntStateOf(4) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedPoint by remember(selectedName, rangeIdx) { mutableStateOf<Int?>(null) }
    var showTrend by rememberSaveable { mutableStateOf(false) }
    var fromZero by rememberSaveable { mutableStateOf(false) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PlainTopBar("Body tracker") {
            IconButton(onClick = { managing = true }) { Icon(Icons.Filled.Edit, contentDescription = "Custom metrics") }
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add measurement") }
        }
        if (measurements.isEmpty()) {
            EmptyState("No body tracker data yet", "Import a FitNotes backup, add a measurement, or create a custom metric.") {
                Row {
                    TextButton(onClick = { nav.tab(Screen.Sync) }) { Text("Go to Sync") }
                    TextButton(onClick = { managing = true }) { Text("Custom metrics") }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                measurements.forEach { m ->
                    FilterChip(selected = m.name == selectedName, onClick = { chosenName = m.name }, label = { Text(m.name) })
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Graph") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
            }
            val def = measurements.firstOrNull { it.name == selectedName }
            val all = remember(snap, selectedName) { snap.dailySeries(selectedName) }
            val shown = remember(all, rangeIdx) { inRange(all, RANGES[rangeIdx].second) { it.date } }
            if (tab == 0) {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RANGES.forEachIndexed { i, r ->
                                FilterChip(selected = rangeIdx == i, onClick = { rangeIdx = i }, label = { Text(r.first) })
                            }
                        }
                    }
                    item {
                        // Graph options (#50): a least-squares trend, and the y axis from zero.
                        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = showTrend, onClick = { showTrend = !showTrend }, label = { Text("Trend") })
                            FilterChip(selected = fromZero, onClick = { fromZero = !fromZero }, label = { Text("From zero") })
                            Spacer(Modifier.weight(1f))
                            ExpandGraphButton { fullScreen = true }
                        }
                    }
                    item {
                        val points = rememberChartData(shown) {
                            shown.map { ChartPoint(Dates.epochDay(it.date), it.value, it.date) }
                        } ?: emptyList()
                        val photoDays = remember(snap) { snap.photosByDate.keys.map { Dates.epochDay(it) }.toSet() }
                        val unit = def?.unit ?: shown.lastOrNull()?.unit ?: ""
                        LineChart(
                            listOf(LineSeries(selectedName, points)),
                            Modifier.padding(horizontal = 8.dp),
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
                                LineChart(
                                    listOf(LineSeries(selectedName, points)),
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "Tap the graph to see that day. Purple ticks and rings mark days with photos.",
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
            } else {
                HistoryTable(snap, nav, snap.recordsByName[selectedName] ?: emptyList())
            }
        }
    }
    if (adding) AddMeasurementDialog(snap, Dates.today()) { adding = false }
    if (managing) CustomMetricsDialog(snap) { managing = false }
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
            LabelValue("Change", "${fmtSigned(last.value - first.value)} $unit", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            LabelValue("Lowest · ${Dates.short(min.date)}", "${fmtNum(min.value)} $unit", Modifier.weight(1f))
            LabelValue("Highest · ${Dates.short(max.date)}", "${fmtNum(max.value)} $unit", Modifier.weight(1f))
            LabelValue("Entries", "${list.size}", Modifier.weight(1f))
        }
        val days = Dates.epochDay(last.date) - Dates.epochDay(first.date)
        if (days >= 14) {
            val perWeek = (last.value - first.value) / days * 7
            Text("Average ${fmtSigned(perWeek, 2)} $unit per week over ${days} days", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HistoryTable(snap: Snapshot, nav: Nav, records: List<MRecord>) {
    val rows = records.reversed()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Date", Modifier.weight(1.4f), style = MaterialTheme.typography.labelLarge)
                Text("Value", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Change", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Photo", Modifier.width(48.dp), style = MaterialTheme.typography.labelLarge)
            }
            HorizontalDivider()
        }
        items(rows, key = { it.id }) { r ->
            val idx = records.indexOf(r)
            val prev = if (idx > 0) records[idx - 1] else null
            val photo = snap.photosByDate[r.date]?.firstOrNull()
            Row(
                Modifier.fillMaxWidth().clickable { nav.push(Screen.Day(r.date)) }.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1.4f)) {
                    Text(Dates.medium(r.date), style = MaterialTheme.typography.bodyMedium)
                    if (!r.comment.isNullOrBlank()) Text(r.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Text("${fmtNum(r.value)} ${r.unit}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(prev?.let { fmtSigned(r.value - it.value) } ?: "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (photo != null) PhotoThumb(snap, photo, Modifier.size(width = 36.dp, height = 48.dp), sizePx = 120)
                else Spacer(Modifier.width(48.dp).height(1.dp))
            }
        }
    }
}
