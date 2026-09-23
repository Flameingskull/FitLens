package com.fitlens.companion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import kotlinx.coroutines.launch

/** Lists custom metrics with add, edit and delete. */
@Composable
fun CustomMetricsDialog(snap: Snapshot, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<MeasurementDef?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MeasurementDef?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom metrics") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Track anything FitNotes doesn't. Enter values by hand from the + button. If a FitNotes backup has a " +
                        "measurement with the same name, or the one you link, its values fill the metric in automatically.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (snap.customMetrics.isEmpty()) {
                    Text("No custom metrics yet.", Modifier.padding(vertical = 16.dp))
                }
                snap.customMetrics.forEach { m ->
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name + if (m.unit.isNotBlank()) " (${m.unit})" else "", style = MaterialTheme.typography.titleSmall)
                            val count = snap.recordsByName[m.name]?.size ?: 0
                            val from = m.link?.takeIf { it.isNotBlank() }?.let { "Filled from FitNotes “$it”" }
                                ?: "Filled from FitNotes when names match"
                            Text("$count entries · $from", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editing = m }) { Icon(Icons.Filled.Edit, contentDescription = "Edit ${m.name}") }
                        IconButton(onClick = { deleting = m }) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${m.name}") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("Add metric") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )

    if (creating) CustomMetricEditor(snap, null) { creating = false }
    editing?.let { m -> CustomMetricEditor(snap, m) { editing = null } }
    deleting?.let { m ->
        val manual = Store.manualCount(snap, m.name)
        ConfirmDialog(
            title = "Delete ${m.name}?",
            text = (if (manual > 0) "The $manual values you entered by hand will be deleted. " else "") +
                "Values from FitNotes stay under their FitNotes measurement.",
            onDismiss = { deleting = null }
        ) { AppScope.scope.launch { Store.deleteCustomMetric(m.name) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomMetricEditor(snap: Snapshot, existing: MeasurementDef?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "") }
    var link by remember { mutableStateOf(existing?.link ?: "") }
    val fitNotes = snap.fitNotesMeasurementNames

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New custom metric" else "Edit ${existing.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit (e.g. cm, kcal, hrs)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Fill in from FitNotes", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = link.isBlank(), onClick = { link = "" }, label = { Text("Same name") })
                    fitNotes.forEach { n -> FilterChip(selected = link == n, onClick = { link = n }, label = { Text(n) }) }
                }
                Text(
                    if (link.isBlank()) "Uses FitNotes values from a measurement with the same name, ignoring capitals. If there isn't one, you enter values by hand."
                    else "Uses every value of FitNotes “$link”. You can still add values by hand.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                val clash = snap.customMetrics.any { it.name.equals(n, true) && it.name != existing?.name }
                when {
                    n.isBlank() -> UiEvents.show("Enter a name")
                    clash -> UiEvents.show("A custom metric called $n already exists")
                    else -> {
                        val u = unit.trim()
                        val l = link.ifBlank { null }
                        val orig = existing?.name
                        AppScope.scope.launch { Store.saveCustomMetric(orig, n, u, l) }
                        onDismiss()
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
