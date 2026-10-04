package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.CustomType
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.SectionLabel
import kotlinx.coroutines.launch

/**
 * Creates or edits a user-defined exercise type (#14): its name, which of weight, reps, distance and time each set
 * records, and optionally a metric of the user's own with its unit (box jump height in cm, a band's colour level).
 * One to [CustomType.MAX_VALUES] values in all. [onSaved] gets the type's id, so the exercise form can choose it.
 * An existing type can be deleted here when no exercise uses it.
 */
@Composable
fun CustomTypeSheet(existing: CustomType?, onDismiss: () -> Unit, onSaved: (Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var weight by rememberSaveable { mutableStateOf(existing?.weight ?: false) }
    var reps by rememberSaveable { mutableStateOf(existing?.reps ?: false) }
    var distance by rememberSaveable { mutableStateOf(existing?.distance ?: false) }
    var time by rememberSaveable { mutableStateOf(existing?.time ?: false) }
    var ownMetric by rememberSaveable { mutableStateOf(existing?.metricName != null) }
    var metricName by rememberSaveable { mutableStateOf(existing?.metricName ?: "") }
    var metricUnit by rememberSaveable { mutableStateOf(existing?.metricUnit ?: "") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val draft = CustomType(
        existing?.id ?: 0, name, weight, reps, distance, time,
        metricName.trim().takeIf { ownMetric && it.isNotEmpty() }, metricUnit.trim().takeIf { ownMetric && it.isNotEmpty() }
    )
    val count = draft.valueCount
    val res = LocalContext.current.resources
    val ready = name.isNotBlank() && count in 1..CustomType.MAX_VALUES && (!ownMetric || draft.metricName != null)

    fun save() {
        AppScope.scope.launch {
            try {
                val id = Workouts.saveExerciseType(draft)
                UiEvents.show(res.getString(if (existing == null) R.string.type_created else R.string.type_saved, draft.name.trim()))
                onSaved(id)
                onDismiss()
            } catch (e: WorkoutDataException) {
                UiEvents.show(e.message ?: res.getString(R.string.type_save_failed))
            }
        }
    }

    if (confirmDelete && existing != null) {
        FitSheet(
            title = stringResource(R.string.type_delete_title, existing.name),
            onDismiss = { confirmDelete = false },
            confirmLabel = stringResource(R.string.type_delete),
            destructive = true,
            onConfirm = {
                AppScope.scope.launch {
                    try {
                        Workouts.deleteExerciseType(existing.id)
                        UiEvents.show(res.getString(R.string.type_deleted, existing.name))
                        onDismiss()
                    } catch (e: WorkoutDataException) {
                        UiEvents.show(e.message ?: res.getString(R.string.type_delete_failed))
                    }
                }
                confirmDelete = false
            }
        ) {
            Text(stringResource(R.string.type_delete_body))
        }
        return
    }

    FitSheet(
        title = stringResource(if (existing == null) R.string.type_new_title else R.string.type_edit_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.type_save),
        onConfirm = { save() },
        confirmEnabled = ready,
        secondaryLabel = if (existing != null) stringResource(R.string.type_delete) else null,
        onSecondary = if (existing != null) ({ confirmDelete = true }) else null
    ) {
        SectionLabel(stringResource(R.string.type_name))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            placeholder = { Text(stringResource(R.string.type_name_hint)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        SectionLabel(stringResource(R.string.type_records))
        ToggleRow(stringResource(R.string.type_weight), weight, inset = 0.dp) { weight = it }
        ToggleRow(stringResource(R.string.type_reps), reps, inset = 0.dp) { reps = it }
        ToggleRow(stringResource(R.string.type_distance), distance, inset = 0.dp) { distance = it }
        ToggleRow(stringResource(R.string.type_time), time, inset = 0.dp) { time = it }
        ToggleRow(stringResource(R.string.type_own_metric), ownMetric, inset = 0.dp) { ownMetric = it }
        if (ownMetric) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = metricName, onValueChange = { metricName = it },
                    label = { Text(stringResource(R.string.type_metric_name)) },
                    placeholder = { Text(stringResource(R.string.type_metric_name_hint)) },
                    singleLine = true, modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = metricUnit, onValueChange = { metricUnit = it },
                    label = { Text(stringResource(R.string.type_metric_unit)) },
                    placeholder = { Text(stringResource(R.string.type_metric_unit_hint)) },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
            }
        }
        // Says why Save is off, rather than leaving the user to guess.
        val hint = when {
            count == 0 -> stringResource(R.string.type_hint_none)
            count > CustomType.MAX_VALUES ->
                pluralStringResource(R.plurals.type_hint_too_many, CustomType.MAX_VALUES, CustomType.MAX_VALUES, count)
            ownMetric && draft.metricName == null -> stringResource(R.string.type_hint_metric_name)
            else -> stringResource(R.string.type_hint_ready, draft.describe().lowercase())
        }
        Text(
            hint,
            Modifier.padding(top = Spacing.sm),
            style = MaterialTheme.typography.bodySmall,
            color = if (ready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
        )
        if (existing != null) {
            Text(
                stringResource(R.string.type_edit_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
