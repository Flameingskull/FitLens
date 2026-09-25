package com.fitlens.companion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Backups
import com.fitlens.companion.data.CsvExport
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.ImportSummary
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.PickerItem
import com.fitlens.companion.ui.design.RangeChips
import com.fitlens.companion.ui.design.RangePreset
import com.fitlens.companion.ui.design.SearchablePicker
import com.fitlens.companion.ui.design.SegmentedSwitch
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Data tools: CSV export (#31) and deleting workout history (#32). Both work on a date range picked with
 * the shared [RangeChips]; a preset or a custom range resolves to inclusive ISO dates, null meaning open-ended.
 */
@Composable
fun DataToolsPage(snap: Snapshot) {
    CsvExportSection(snap)
    DeleteHistorySection(snap)
}

/** A preset or custom date range as (from, to) ISO dates. */
private class RangeState {
    var preset by mutableStateOf<RangePreset?>(RangePreset.All)
    var custom by mutableStateOf<Pair<String, String>?>(null)

    val from: String?
        get() {
            val p = preset
            return if (p != null) p.startDate() else custom?.first
        }
    val to: String? get() = if (preset == null) custom?.second else null

    val label: String
        get() {
            val p = preset
            val c = custom
            return when {
                p == RangePreset.All -> "all dates"
                p != null -> "the last ${p.label}"
                c != null -> "${Dates.medium(c.first)} to ${Dates.medium(c.second)}"
                else -> "all dates"
            }
        }
}

@Composable
private fun RangePicker(state: RangeState) {
    RangeChips(
        selected = state.preset,
        onPreset = { state.preset = it },
        custom = state.custom,
        onCustom = { from, to ->
            state.custom = from to to
            state.preset = null
        }
    )
}

@Composable
private fun ToolHint(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

// ---------- CSV export (#31) ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CsvExportSection(snap: Snapshot) {
    val ctx = LocalContext.current.applicationContext
    var body by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf(snap.weightUnit) }
    val range = remember { RangeState() }
    val from = range.from
    val to = range.to
    val kind = if (body) "body" else "workouts"

    // Builds the file's text from the newest data, off the main thread.
    suspend fun csv(): String = withContext(Dispatchers.Default) {
        val s = Store.snapshot.value ?: snap
        if (body) CsvExport.body(s, from, to) else CsvExport.workouts(s, from, to, unit)
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CsvExport.MIME)) { uri ->
        if (uri != null) runBusy("Exporting…") {
            val text = csv()
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            } ?: return@runBusy ImportSummary("Couldn't write the CSV file.", false)
            ImportSummary("CSV saved.", true)
        }
    }

    fun share() {
        AppScope.scope.launch {
            UiEvents.busy.value = "Exporting…"
            val file = try {
                val text = csv()
                withContext(Dispatchers.IO) {
                    // One shared CSV at a time in the share cache (res/xml/file_paths.xml).
                    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles()?.filter { it.name.endsWith(".csv") }?.forEach { it.delete() }
                    File(dir, CsvExport.fileName(kind, from, to)).apply { writeText(text, Charsets.UTF_8) }
                }
            } catch (e: Exception) {
                UiEvents.show("Couldn't create the CSV file: ${e.message}", ResultLevel.Failure)
                null
            } finally {
                UiEvents.busy.value = null
            }
            if (file != null) shareFile(ctx, file, CsvExport.MIME)
        }
    }

    val count = remember(snap, body, from, to) {
        if (body) CsvExport.countBody(snap, from, to) to 0 else CsvExport.countWorkouts(snap, from, to)
    }
    val preview = if (body) plural(count.first, "body value", "body values")
    else "${plural(count.first, "set", "sets")} from ${plural(count.second, "workout", "workouts")}"

    SectionTitle("Export as CSV")
    ToolHint("For spreadsheets such as Excel or Google Sheets. FitLens can't restore from a CSV file: use a backup for that.")
    SegmentedSwitch(options = listOf("Workouts", "Body data"), selected = if (body) 1 else 0, onSelect = { body = it == 1 })
    RangePicker(range)
    if (!body) {
        SegmentedSwitch(
            options = listOf("Weights in kg", "Weights in lbs"),
            selected = if (unit == "lbs") 1 else 0,
            onSelect = { unit = if (it == 1) "lbs" else "kg" }
        )
    }
    Text("$preview, ${range.label}.", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { save.launch(CsvExport.fileName(kind, from, to)) }, enabled = count.first > 0) { Text("Save CSV") }
        OutlinedButton(onClick = { share() }, enabled = count.first > 0) { Text("Share CSV") }
    }
    val columns = if (body) {
        CsvExport.BODY_COLUMNS.joinToString(", ") + ". One row per value."
    } else {
        CsvExport.WORKOUT_COLUMNS + ". One row per set, numbered from 1 for each exercise on each day. Time is in seconds."
    }
    ToolHint("Columns: $columns Dates are written year-month-day.")
}

// ---------- Delete workout history (#32) ----------

@Composable
private fun DeleteHistorySection(snap: Snapshot) {
    val ctx = LocalContext.current.applicationContext
    val range = remember { RangeState() }
    var exerciseIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var picking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val from = range.from
    val to = range.to

    val matching = remember(snap, from, to, exerciseIds) {
        snap.sets.filter { s ->
            val d = s.date.take(10)
            (from == null || d >= from) && (to == null || d <= to) &&
                (exerciseIds.isEmpty() || s.exerciseId in exerciseIds)
        }
    }
    val days = remember(matching) { matching.map { it.date.take(10) }.distinct().size }
    val imported = remember(matching) { matching.count { it.imported } }
    val exerciseLabel = when (exerciseIds.size) {
        0 -> "every exercise"
        1 -> snap.exercises[exerciseIds.first()]?.name ?: "1 exercise"
        else -> "${exerciseIds.size} exercises"
    }
    val summary = "${plural(matching.size, "set", "sets")} from ${plural(days, "workout", "workouts")}"

    SectionTitle("Delete workout history")
    ToolHint(
        "Removes logged sets by date range, exercise or both. Your exercises, categories, workout comments and " +
            "times, photos and body data are kept."
    )
    RangePicker(range)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("For $exerciseLabel", Modifier.padding(horizontal = 4.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (exerciseIds.isNotEmpty()) TextButton(onClick = { exerciseIds = emptySet() }) { Text("All") }
        TextButton(onClick = { picking = true }) { Text("Choose exercises") }
    }
    Text(
        if (matching.isEmpty()) "Nothing to delete for ${range.label}." else "$summary, ${range.label}.",
        Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodyMedium
    )
    Button(
        onClick = { confirming = true },
        enabled = matching.isNotEmpty(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    ) {
        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Delete workout history")
    }

    if (picking) {
        val items = remember(snap) {
            snap.exercisesSorted
                .filter { snap.setsByExercise[it.id].orEmpty().isNotEmpty() }
                .map { e -> PickerItem(e.id, e.name, section = snap.categories[e.categoryId]?.name) }
        }
        SearchablePicker(
            title = "Exercises to delete from",
            items = items,
            onDismiss = { picking = false },
            onPick = { ids ->
                exerciseIds = ids.toSet()
                picking = false
            },
            multiSelect = true,
            searchLabel = "Search exercises"
        )
    }
    if (confirming) {
        val fitNotesNote = if (imported > 0) {
            " ${plural(imported, "set", "sets")} came from FitNotes, and will stay deleted when you next import a " +
                "FitNotes backup."
        } else ""
        ConfirmSheet(
            title = "Delete ${plural(matching.size, "set", "sets")}?",
            message = "This deletes $summary for $exerciseLabel, ${range.label}.$fitNotesNote Personal records are " +
                "worked out again afterwards. A safety copy is taken first, so you can undo this from " +
                "Settings → Backups for ${Backups.UNDO_DAYS} days.",
            confirmLabel = "Delete ${plural(matching.size, "set", "sets")}",
            onDismiss = { confirming = false },
            onConfirm = {
                val ids = exerciseIds
                runBusy("Deleting workout history…") {
                    // The way back (#47). If it can't be made, nothing is deleted.
                    val safety = Backups.safetyCopy(ctx, "Before deleting workout history")
                    if (!safety.ok) return@runBusy safety
                    val n = Workouts.deleteHistory(from, to, ids)
                    ImportSummary("Deleted ${plural(n, "set", "sets")}. Undo is in Settings → Backups.", ok = true)
                }
            }
        )
    }
}
