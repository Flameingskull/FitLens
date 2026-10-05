package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.GoldButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.DropdownPill
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.fitlens.companion.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(snap: Snapshot, nav: Nav) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val dates = remember(snap, filter) {
        when (filter) {
            1 -> snap.allDates.filter { snap.photosByDate.containsKey(it) }
            2 -> snap.allDates.filter { snap.recordsByDate.containsKey(it) }
            3 -> snap.allDates.filter { snap.setsByDate.containsKey(it) }
            4 -> snap.allDates.filter { snap.photosByDate.containsKey(it) && snap.recordsByDate.containsKey(it) }
            else -> snap.allDates
        }
    }
    Column(Modifier.fillMaxSize()) {
        PlainTopBar(stringResource(R.string.tl_title)) {
            IconButton(onClick = { nav.home() }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tl_log_today))
            }
            LibraryAction(nav)
        }
        if (snap.allDates.isEmpty()) {
            EmptyState(
                stringResource(R.string.tl_empty_title),
                stringResource(R.string.tl_empty_body)
            ) {
                GoldButton(onClick = { nav.home() }) { Text(stringResource(R.string.tl_log_today)) }
                TextButton(onClick = { nav.push(Screen.SettingsPage(SettingsSection.Import)) }) { Text(stringResource(R.string.tl_import)) }
                TextButton(onClick = { nav.push(Screen.SettingsPage(SettingsSection.Backups)) }) { Text(stringResource(R.string.tl_restore)) }
            }
        } else {
        DropdownPill(
            label = stringResource(R.string.tl_show),
            options = listOf(
                stringResource(R.string.tl_f_all), stringResource(R.string.tl_f_photos), stringResource(R.string.tl_f_body),
                stringResource(R.string.tl_f_workout), stringResource(R.string.tl_f_photos_body)
            ),
            selected = filter,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) { filter = it }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(dates, key = { it }) { date ->
                DayCard(snap, date) { nav.push(Screen.Day(date)) }
            }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DayCard(snap: Snapshot, date: String, onClick: () -> Unit) {
    val photos = snap.photosByDate[date] ?: emptyList()
    val records = snap.recordsByDate[date] ?: emptyList()
    val sets = snap.setsByDate[date] ?: emptyList()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(Dates.long(date), style = MaterialTheme.typography.titleMedium)
            if (photos.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    photos.take(4).forEach { p ->
                        PhotoThumb(snap, p, Modifier.weight(1f).aspectRatio(0.75f))
                    }
                    repeat(maxOf(0, 4 - photos.size)) { Spacer(Modifier.weight(1f)) }
                }
                if (photos.size > 4) {
                    Text(stringResource(R.string.tl_more, photos.size - 4), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (records.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    records.groupBy { it.name }.forEach { (name, list) ->
                        val r = list.last()
                        SuggestionChip(onClick = onClick, label = { Text(stringResource(R.string.tl_value, name, fmtNum(r.value), r.unit).trim()) })
                    }
                }
            }
            if (sets.isNotEmpty()) {
                val byEx = sets.groupBy { it.exerciseId }
                val cats = byEx.keys.mapNotNull { snap.categoryOf(it) }.distinctBy { it.id }
                Text(
                    stringResource(R.string.tl_ex_sets, pluralStringResource(R.plurals.lib_exercises, byEx.size, byEx.size), pluralStringResource(R.plurals.sets_count, sets.size, sets.size)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    cats.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(Color(c.colour))
                            Spacer(Modifier.width(4.dp))
                            Text(c.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
