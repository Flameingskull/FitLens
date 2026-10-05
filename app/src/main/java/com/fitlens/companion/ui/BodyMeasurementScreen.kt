@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.BodyFat
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.DayNavigator
import com.fitlens.companion.ui.design.FitTabRow
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.StepperField
import com.fitlens.companion.ui.design.TopBarAction
import com.fitlens.companion.ui.design.SetRow as SetRowView
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val HHMMSS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/** The value stepper's step, as FitNotes's body tracker steps. */
private const val VALUE_STEP = 0.1

private fun shiftDay(date: String, days: Long): String =
    (Dates.parse(date) ?: LocalDate.now()).plusDays(days).format(Dates.ISO)

/**
 * One body measurement, as in FitNotes's body tracker: TRACK, HISTORY and GRAPH tabs laid out like the exercise screen.
 * Track has the day, a +/- value stepper, the time and a comment, Save / Clear (Update / Delete for a selected value),
 * and the values logged that day. History lists every day newest first; tapping a value opens it on Track.
 */
@Composable
fun BodyMeasurementScreen(snap: Snapshot, nav: Nav, name: String, page: Int = 0) {
    val res = LocalContext.current.resources
    val def = snap.allMeasurements.firstOrNull { it.name == name }
    val records = snap.recordsByName[name].orEmpty()
    val unit = def?.unit?.takeIf { it.isNotBlank() } ?: records.lastOrNull()?.unit ?: ""
    val label = if (unit.isNotBlank()) "$name ($unit)" else name

    var date by rememberSaveable(name) { mutableStateOf(Dates.today()) }
    var selected by rememberSaveable(name) { mutableStateOf<Long?>(null) }
    var value by remember(name) { mutableStateOf("") }
    var timeText by remember(name) { mutableStateOf("") }
    var comment by remember(name) { mutableStateOf("") }
    var deleting by remember { mutableStateOf<MRecord?>(null) }
    var editingGoal by remember { mutableStateOf(false) }
    // Body fat can be worked out from the tape measurements (#153).
    val isBodyFat = BodyFat.isBodyFat(name)
    var calculating by remember { mutableStateOf(false) }

    val dayValues = remember(records, date) { records.filter { it.date.take(10) == date } }
    val chosen = selected?.let { id -> records.firstOrNull { it.id == id } }
    // A new value starts from the latest one on or before the day, as FitNotes fills in last time's value.
    val template = remember(records, date) { records.lastOrNull { it.date.take(10) <= date } ?: records.lastOrNull() }

    LaunchedEffect(selected, date, records) {
        if (selected != null && chosen == null) {
            selected = null
        } else {
            val source = chosen ?: template
            value = source?.value?.let { fmtNum(it) } ?: ""
            timeText = chosen?.time?.take(5)?.takeIf { it.isNotBlank() } ?: LocalTime.now().format(HHMM)
            comment = chosen?.comment.orEmpty()
        }
    }

    fun save() {
        val v = value.trim().replace(',', '.').toDoubleOrNull()
        if (v == null) {
            UiEvents.show(res.getString(R.string.bm_enter_number))
            return
        }
        // The time typed in, or now when it can't be read.
        val time = runCatching { LocalTime.parse(timeText.trim()) }.getOrNull()?.format(HHMMSS) ?: LocalTime.now().format(HHMMSS)
        val c = comment.trim().ifBlank { null }
        val d = date
        val editing = chosen
        AppScope.scope.launch {
            if (editing == null) {
                Store.addManualRecord(name, unit, d, time, v, c)
            } else {
                Store.updateRecord(editing.id, d, time, v, c)
                UiEvents.show(res.getString(R.string.bm_updated))
            }
        }
        selected = null
    }

    fun clear() {
        selected = null
        value = ""
        comment = ""
        timeText = LocalTime.now().format(HHMM)
    }

    val pager = rememberPagerState(initialPage = page.coerceIn(0, 2), pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = name,
            subtitle = unit.takeIf { it.isNotBlank() },
            onBack = { nav.pop() },
            actions = listOf(TopBarAction(Icons.Filled.Edit, stringResource(R.string.bm_manage)) { nav.push(Screen.Measurements) }),
            overflow = listOf(MenuAction(stringResource(R.string.goal_generic), enabled = def != null) { editingGoal = true })
        )
        FitTabRow(
            titles = bodyTabs(),
            selected = pager.currentPage,
            onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } }
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { tab ->
            when (tab) {
                1 -> BodyHistoryPane(snap, name) { r ->
                    date = r.date.take(10)
                    selected = r.id
                    scope.launch { pager.animateScrollToPage(0) }
                }
                2 -> BodyGraphPane(snap, nav, name)
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    item {
                        DayNavigator(
                            date = date,
                            onPrevious = { date = shiftDay(date, -1); selected = null },
                            onNext = { date = shiftDay(date, 1); selected = null },
                            onPickDate = { date = it; selected = null },
                            onToday = { date = Dates.today(); selected = null }
                        )
                    }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StepperField(
                                label = label,
                                value = value,
                                onValue = { value = it },
                                onStep = { dir ->
                                    val now = value.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
                                    value = fmtNum(max(0.0, now + dir * VALUE_STEP), 2)
                                }
                            )
                            if (isBodyFat && chosen == null) {
                                GlassOutlinedButton(onClick = { calculating = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                    Text(stringResource(R.string.bm_calculate))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = timeText, onValueChange = { timeText = it },
                                    label = { Text(stringResource(R.string.bm_time)) }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = comment, onValueChange = { comment = it },
                                    label = { Text(stringResource(R.string.bm_comment)) }, singleLine = true,
                                    modifier = Modifier.weight(2f)
                                )
                            }
                            if (def != null && def.goalType != 0) {
                                Text(goalText(res, def), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // As in FitNotes: Save and Clear for a new value, Update and Delete for the selected one.
                            if (chosen == null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GoldButton(onClick = { save() }, modifier = Modifier.weight(1f).height(52.dp)) {
                                        Text(stringResource(R.string.type_save), style = MaterialTheme.typography.labelLarge)
                                    }
                                    GlassOutlinedButton(onClick = { clear() }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.cal_clear)) }
                                }
                            } else {
                                // A value imported from FitNotes isn't edited: the next import would bring it back.
                                val editable = chosen.source == "manual"
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GoldButton(onClick = { save() }, enabled = editable, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.bm_update)) }
                                    GlassOutlinedButton(
                                        onClick = { deleting = chosen },
                                        enabled = editable,
                                        modifier = Modifier.weight(1f).height(52.dp)
                                    ) { Text(stringResource(R.string.lib_delete)) }
                                }
                                if (!editable) {
                                    Text(
                                        stringResource(R.string.bm_from_fitnotes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { selected = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bm_new_instead)) }
                            }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(top = 14.dp)) }
                    if (dayValues.isEmpty()) {
                        item {
                            Text(
                                if (template == null) stringResource(R.string.bm_nothing_for, name) else stringResource(R.string.bm_nothing_day),
                                Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    dayValues.forEachIndexed { i, r ->
                        item(key = "m${r.id}") {
                            SetRowView(
                                index = i + 1,
                                summary = "${fmtNum(r.value)} ${r.unit}",
                                cells = valueCells(res, r),
                                comment = r.comment,
                                selected = selected == r.id,
                                noun = stringResource(R.string.body_value),
                                onClick = { selected = if (selected == r.id) null else r.id }
                            )
                        }
                    }
                }
            }
        }
    }

    deleting?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.bm_delete_title),
            text = stringResource(R.string.bm_delete_body, name, "${fmtNum(r.value)} ${r.unit}".trim(), Dates.long(r.date)),
            onDismiss = { deleting = null }
        ) {
            selected = null
            AppScope.scope.launch {
                Store.deleteRecord(r.id)
                UiEvents.show(res.getString(R.string.bm_deleted), res.getString(R.string.bm_undo)) {
                    AppScope.scope.launch { Store.addManualRecord(r.name, r.unit, r.date, r.time, r.value, r.comment) }
                }
            }
        }
    }
    if (calculating) {
        BodyFatCalculatorSheet(snap, date, onDismiss = { calculating = false }) { p, c ->
            value = fmtNum(p, 1)
            comment = c
            UiEvents.show(res.getString(R.string.bm_calculated))
        }
    }
    if (editingGoal && def != null) MeasurementGoalSheet(def) { editingGoal = false }
}
