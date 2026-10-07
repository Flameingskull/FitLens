package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.GlassOutlinedButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.fitlens.companion.data.BodyFat
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitSheet
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val HHMMSS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Logs a body measurement, or edits one logged by hand (#27, #88): the measurement, value, date, time and an optional
 * comment, as a sheet. [existing] opens a value to change or delete; a value imported from FitNotes is shown but not
 * edited, because the next import would bring the original back beside it. [onOpenDay] adds an Open day button.
 */
@Composable
fun MeasurementEntrySheet(
    snap: Snapshot,
    date: String,
    initialName: String? = null,
    existing: MRecord? = null,
    onOpenDay: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val res = LocalContext.current.resources
    val names = remember(snap.bodyKey) {
        (snap.usedMeasurements.map { it.name } + snap.measurementDefs.filter { it.enabled }.map { it.name }).distinct()
    }
    var name by remember { mutableStateOf(existing?.name ?: initialName ?: names.firstOrNull() ?: "Bodyweight") }
    var value by remember { mutableStateOf(existing?.value?.let { fmtNum(it) } ?: "") }
    var comment by remember { mutableStateOf(existing?.comment ?: "") }
    var theDate by remember { mutableStateOf(existing?.date ?: date) }
    var timeText by remember {
        mutableStateOf(existing?.time?.take(5)?.takeIf { it.isNotBlank() } ?: LocalTime.now().format(HHMM))
    }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var calculating by remember { mutableStateOf(false) }
    val editable = existing == null || existing.source == "manual"
    val unit = existing?.unit ?: snap.measurementDefs.firstOrNull { it.name == name }?.unit
        ?: snap.recordsByName[name]?.lastOrNull()?.unit ?: ""

    fun save() {
        val v = value.trim().replace(',', '.').toDoubleOrNull()
        val n = name.trim()
        if (v == null || n.isBlank()) {
            UiEvents.show(res.getString(R.string.bm_enter_number))
            return
        }
        // The time typed in, or now when it can't be read.
        val time = runCatching { LocalTime.parse(timeText.trim()) }.getOrNull()?.format(HHMMSS) ?: LocalTime.now().format(HHMMSS)
        val c = comment.trim().ifBlank { null }
        val d = theDate
        AppScope.scope.launch {
            if (existing == null) Store.addManualRecord(n, unit, d, time, v, c)
            else Store.updateRecord(existing.id, d, time, v, c)
        }
        onDismiss()
    }

    FitSheet(
        title = if (existing == null) stringResource(R.string.mse_log) else if (editable) stringResource(R.string.mse_edit, existing.name) else existing.name,
        onDismiss = onDismiss,
        dismissLabel = stringResource(if (editable) R.string.cancel else R.string.mse_close),
        confirmLabel = if (editable) stringResource(R.string.type_save) else null,
        onConfirm = if (editable) ({ save() }) else null,
        secondaryLabel = if (existing != null && editable) stringResource(R.string.lib_delete) else null,
        onSecondary = if (existing != null && editable) ({ confirmDelete = true }) else null
    ) {
        if (existing == null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                names.forEach { n -> FilterChip(selected = n == name, onClick = { name = n }, label = { Text(n) }) }
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.mse_measurement)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
        if (!editable && existing != null) {
            Text(
                stringResource(R.string.body_value_on, "${fmtNum(existing.value)} ${existing.unit}".trim(), Dates.long(existing.date)),
                style = MaterialTheme.typography.headlineSmall
            )
            if (!existing.comment.isNullOrBlank()) Text("“${existing.comment}”", style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.mse_from_fitnotes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            OutlinedTextField(
                value = value, onValueChange = { value = it },
                label = { Text(if (unit.isNotBlank()) stringResource(R.string.mse_value_unit, unit) else stringResource(R.string.body_value)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            // Body fat can be worked out from the tape measurements (#153).
            if (existing == null && BodyFat.isBodyFat(name)) {
                TextButton(onClick = { calculating = true }) { Text(stringResource(R.string.bm_calculate)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GlassOutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) {
                    Text(Dates.medium(theDate))
                }
                OutlinedTextField(
                    value = timeText, onValueChange = { timeText = it },
                    label = { Text(stringResource(R.string.bm_time)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = comment, onValueChange = { comment = it },
                label = { Text(stringResource(R.string.mse_comment_optional)) }, modifier = Modifier.fillMaxWidth()
            )
        }
        if (existing != null && onOpenDay != null) {
            TextButton(onClick = { onDismiss(); onOpenDay(existing.date) }) { Text(stringResource(R.string.st_open_day, Dates.medium(existing.date))) }
        }
    }
    if (calculating) {
        BodyFatCalculatorSheet(snap, theDate, onDismiss = { calculating = false }) { p, c ->
            value = fmtNum(p, 1)
            comment = c
        }
    }
    if (pickDate) PickDateDialog(theDate, onDismiss = { pickDate = false }) { theDate = it }
    if (confirmDelete && existing != null) {
        ConfirmSheet(
            title = stringResource(R.string.bm_delete_title),
            message = stringResource(R.string.bm_delete_body, existing.name, "${fmtNum(existing.value)} ${existing.unit}".trim(), Dates.long(existing.date)) + ".",
            confirmLabel = stringResource(R.string.mse_delete_value),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                AppScope.scope.launch { Store.deleteRecord(existing.id) }
                onDismiss()
            }
        )
    }
}
