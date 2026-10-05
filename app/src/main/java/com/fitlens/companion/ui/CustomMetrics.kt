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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
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

/** Creates or edits a custom measurement (#3), from the Measurements screen (#88). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomMetricEditor(snap: Snapshot, existing: MeasurementDef?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "") }
    var link by remember { mutableStateOf(existing?.link ?: "") }
    val fitNotes = snap.fitNotesMeasurementNames
    val res = LocalContext.current.resources

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) stringResource(R.string.cm_new) else stringResource(R.string.mse_edit, existing.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.rb_sort_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(stringResource(R.string.cm_unit)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.cm_fill_from), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = link.isBlank(), onClick = { link = "" }, label = { Text(stringResource(R.string.cm_same_name)) })
                    fitNotes.forEach { n -> FilterChip(selected = link == n, onClick = { link = n }, label = { Text(n) }) }
                }
                Text(
                    if (link.isBlank()) stringResource(R.string.cm_same_name_note) else stringResource(R.string.cm_link_note, link),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                val clash = snap.customMetrics.any { it.name.equals(n, true) && it.name != existing?.name }
                when {
                    n.isBlank() -> UiEvents.show(res.getString(R.string.cm_enter_name))
                    clash -> UiEvents.show(res.getString(R.string.cm_clash, n))
                    else -> {
                        val u = unit.trim()
                        val l = link.ifBlank { null }
                        val orig = existing?.name
                        AppScope.scope.launch { Store.saveCustomMetric(orig, n, u, l) }
                        onDismiss()
                    }
                }
            }) { Text(stringResource(R.string.type_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
