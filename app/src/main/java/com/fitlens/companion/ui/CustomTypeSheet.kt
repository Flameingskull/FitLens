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
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.CustomType
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.Spacing
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
    val ready = name.isNotBlank() && count in 1..CustomType.MAX_VALUES && (!ownMetric || draft.metricName != null)

    fun save() {
        AppScope.scope.launch {
            try {
                val id = Workouts.saveExerciseType(draft)
                UiEvents.show(if (existing == null) "Type ${draft.name.trim()} created" else "Type ${draft.name.trim()} saved")
                onSaved(id)
                onDismiss()
            } catch (e: WorkoutDataException) {
                UiEvents.show(e.message ?: "That type couldn't be saved.")
            }
        }
    }

    if (confirmDelete && existing != null) {
        FitSheet(
            title = "Delete ${existing.name}?",
            onDismiss = { confirmDelete = false },
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                AppScope.scope.launch {
                    try {
                        Workouts.deleteExerciseType(existing.id)
                        UiEvents.show("Type ${existing.name} deleted")
                        onDismiss()
                    } catch (e: WorkoutDataException) {
                        UiEvents.show(e.message ?: "That type couldn't be deleted.")
                    }
                }
                confirmDelete = false
            }
        ) {
            Text("Exercises can't use it any more. A type that an exercise still uses can't be deleted.")
        }
        return
    }

    FitSheet(
        title = if (existing == null) "New exercise type" else "Edit exercise type",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        onConfirm = { save() },
        confirmEnabled = ready,
        secondaryLabel = if (existing != null) "Delete" else null,
        onSecondary = if (existing != null) ({ confirmDelete = true }) else null
    ) {
        SectionLabel("Name")
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            placeholder = { Text("Jumps, Bands, Climbing") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        SectionLabel("Each set records")
        ToggleRow("Weight", weight, inset = 0.dp) { weight = it }
        ToggleRow("Reps", reps, inset = 0.dp) { reps = it }
        ToggleRow("Distance", distance, inset = 0.dp) { distance = it }
        ToggleRow("Time", time, inset = 0.dp) { time = it }
        ToggleRow("A metric of your own", ownMetric, inset = 0.dp) { ownMetric = it }
        if (ownMetric) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = metricName, onValueChange = { metricName = it },
                    label = { Text("Metric name") }, placeholder = { Text("Height") },
                    singleLine = true, modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = metricUnit, onValueChange = { metricUnit = it },
                    label = { Text("Unit") }, placeholder = { Text("cm") },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
            }
        }
        // Says why Save is off, rather than leaving the user to guess.
        val hint = when {
            count == 0 -> "Choose at least one value."
            count > CustomType.MAX_VALUES -> "Choose at most ${CustomType.MAX_VALUES} values ($count chosen)."
            ownMetric && draft.metricName == null -> "Name your metric, or untick it."
            else -> "Sets will record: ${draft.describe().lowercase()}."
        }
        Text(
            hint,
            Modifier.padding(top = Spacing.sm),
            style = MaterialTheme.typography.bodySmall,
            color = if (ready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
        )
        if (existing != null) {
            Text(
                "Exercises of this type follow the change. Sets already logged keep every value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
