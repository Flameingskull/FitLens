package com.fitlens.companion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.Backups
import com.fitlens.companion.data.CsvExport
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.ImportSummary
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.PickerItem
import com.fitlens.companion.ui.design.DateRangePickerDialog
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.RangePreset
import com.fitlens.companion.ui.design.SearchablePicker
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Data tools: CSV export (#31), deleting workout history (#32) and resetting the settings (#41). The first
 * two work on a date range picked with a [RangeRow]; a preset or a custom range resolves to inclusive ISO dates, null
 * meaning open-ended. The page's text is in `res/values/strings.xml` (#94).
 */
@Composable
fun DataToolsPage(snap: Snapshot) {
    CsvExportSection(snap)
    DeleteHistorySection(snap)
    ResetSettingsSection()
}

// ---------- Reset settings to defaults (#41) ----------

@Composable
private fun ResetSettingsSection() {
    var confirming by remember { mutableStateOf(false) }
    SettingsGroup(stringResource(R.string.reset_group))
    SettingsActionRow(stringResource(R.string.reset_row), stringResource(R.string.reset_row_summary)) { confirming = true }
    if (confirming) ResetSettingsSheet { confirming = false }
}

/**
 * Confirms Reset settings to defaults (#41), then resets the preferences only ([Settings.resetPreferences]) and
 * offers Undo, which puts the values from before back. Also opened from the main Settings list.
 */
@Composable
internal fun ResetSettingsSheet(onDismiss: () -> Unit) {
    val done = stringResource(R.string.reset_done)
    val undo = stringResource(R.string.undo)
    ConfirmSheet(
        title = stringResource(R.string.reset_title),
        message = stringResource(R.string.reset_body),
        confirmLabel = stringResource(R.string.reset_confirm),
        dismissLabel = stringResource(R.string.reset_keep),
        onDismiss = onDismiss,
        onConfirm = {
            val before = Settings.resetPreferences()
            UiEvents.show(done, undo) { Settings.restorePreferences(before) }
        },
        destructive = false
    )
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
}

/** The range as it reads in a sentence: "all dates", "last 30 days" or "1 Jan 2026 to 31 Mar 2026". */
@Composable
private fun RangeState.label(): String {
    val p = preset
    val c = custom
    return when {
        p != null && p != RangePreset.All -> p.label.lowercase()
        p == null && c != null -> stringResource(R.string.data_range_to, Dates.medium(c.first), Dates.medium(c.second))
        else -> stringResource(R.string.data_all_dates)
    }
}

/** The date range as a settings choice row (#86): the presets, then "Custom dates…", which opens a date-range picker. */
@Composable
private fun RangeRow(state: RangeState) {
    var picking by remember { mutableStateOf(false) }
    val presets = RangePreset.entries
    val custom = state.custom
    val customLabel = if (state.preset == null && custom != null) {
        stringResource(R.string.data_range_between, Dates.medium(custom.first), Dates.medium(custom.second))
    } else stringResource(R.string.data_range_custom)
    SettingsChoiceRow(
        stringResource(R.string.data_range),
        presets.map { it.label } + customLabel,
        state.preset?.ordinal ?: presets.size
    ) { i -> if (i < presets.size) state.preset = presets[i] else picking = true }
    if (picking) {
        DateRangePickerDialog(
            initialFrom = custom?.first,
            initialTo = custom?.second,
            onDismiss = { picking = false },
            onPicked = { from, to ->
                state.custom = from to to
                state.preset = null
            }
        )
    }
}

// ---------- CSV export (#31) ----------

@Composable
private fun CsvExportSection(snap: Snapshot) {
    val ctx = LocalContext.current.applicationContext
    var body by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf(snap.weightUnit) }
    val range = remember { RangeState() }
    val from = range.from
    val to = range.to
    val kind = if (body) "body" else "workouts"
    val exporting = stringResource(R.string.data_exporting)
    val writeFailed = stringResource(R.string.data_csv_write_failed)
    val saved = stringResource(R.string.data_csv_saved)

    // Builds the file's text from the newest data, off the main thread.
    suspend fun csv(): String = withContext(Dispatchers.Default) {
        val s = Store.snapshot.value ?: snap
        if (body) CsvExport.body(s, from, to) else CsvExport.workouts(s, from, to, unit)
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CsvExport.MIME)) { uri ->
        if (uri != null) runBusy(exporting) {
            val text = csv()
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            } ?: return@runBusy ImportSummary(writeFailed, false)
            ImportSummary(saved, true)
        }
    }

    fun share() {
        AppScope.scope.launch {
            UiEvents.busy.value = exporting
            val file = try {
                val text = csv()
                withContext(Dispatchers.IO) {
                    // One shared CSV at a time in the share cache (res/xml/file_paths.xml).
                    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles()?.filter { it.name.endsWith(".csv") }?.forEach { it.delete() }
                    File(dir, CsvExport.fileName(kind, from, to)).apply { writeText(text, Charsets.UTF_8) }
                }
            } catch (e: Exception) {
                UiEvents.show(ctx.getString(R.string.data_csv_create_failed, e.message.orEmpty()), ResultLevel.Failure)
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
    val preview = if (body) pluralStringResource(R.plurals.data_body_values, count.first, count.first)
    else stringResource(
        R.string.data_sets_from,
        pluralStringResource(R.plurals.data_sets, count.first, count.first),
        pluralStringResource(R.plurals.data_workouts, count.second, count.second)
    )
    val rangeLabel = range.label()

    SettingsGroup(stringResource(R.string.data_csv_group))
    SettingsNote(stringResource(R.string.data_csv_note))
    SettingsChoiceRow(
        stringResource(R.string.data_kind),
        listOf(stringResource(R.string.data_kind_workouts), stringResource(R.string.data_kind_body)),
        if (body) 1 else 0
    ) { body = it == 1 }
    RangeRow(range)
    if (!body) {
        SettingsChoiceRow(
            stringResource(R.string.data_weights_in),
            listOf(stringResource(R.string.unit_kilograms), stringResource(R.string.unit_pounds)),
            if (unit == "lbs") 1 else 0
        ) { unit = if (it == 1) "lbs" else "kg" }
    }
    val ready = count.first > 0
    val nothing = stringResource(R.string.data_csv_nothing, rangeLabel)
    SettingsActionRow(
        stringResource(R.string.data_save_csv),
        stringResource(R.string.data_count_range, preview, rangeLabel),
        enabled = ready,
        disabledReason = nothing
    ) { save.launch(CsvExport.fileName(kind, from, to)) }
    SettingsActionRow(
        stringResource(R.string.data_share_csv),
        stringResource(R.string.data_share_csv_summary),
        enabled = ready,
        disabledReason = nothing
    ) { share() }
    val columns = if (body) {
        stringResource(R.string.data_columns_body, CsvExport.BODY_COLUMNS.joinToString(", "))
    } else {
        stringResource(R.string.data_columns_workouts, CsvExport.WORKOUT_COLUMNS)
    }
    SettingsNote(stringResource(R.string.data_columns, columns))
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
        0 -> stringResource(R.string.data_every_exercise)
        1 -> snap.exercises[exerciseIds.first()]?.name ?: pluralStringResource(R.plurals.data_exercise_count, 1, 1)
        else -> pluralStringResource(R.plurals.data_exercise_count, exerciseIds.size, exerciseIds.size)
    }
    val setsText = pluralStringResource(R.plurals.data_sets, matching.size, matching.size)
    val summary = stringResource(
        R.string.data_sets_from, setsText, pluralStringResource(R.plurals.data_workouts, days, days)
    )
    val rangeLabel = range.label()

    SettingsGroup(stringResource(R.string.data_delete_group))
    SettingsNote(stringResource(R.string.data_delete_note))
    RangeRow(range)
    SettingsActionRow(
        stringResource(R.string.data_exercises),
        stringResource(R.string.data_exercises_summary),
        value = exerciseLabel.replaceFirstChar { it.uppercase() }
    ) { picking = true }
    if (exerciseIds.isNotEmpty()) SettingsActionRow(stringResource(R.string.data_use_every)) { exerciseIds = emptySet() }
    SettingsNote(
        if (matching.isEmpty()) stringResource(R.string.data_delete_nothing, rangeLabel)
        else stringResource(R.string.data_count_range, summary, rangeLabel)
    )
    Button(
        onClick = { confirming = true },
        enabled = matching.isNotEmpty(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.data_delete_group))
    }

    if (picking) {
        val items = remember(snap) {
            snap.exercisesSorted
                .filter { snap.setsByExercise[it.id].orEmpty().isNotEmpty() }
                .map { e -> PickerItem(e.id, e.name, section = snap.categories[e.categoryId]?.name) }
        }
        SearchablePicker(
            title = stringResource(R.string.data_pick_title),
            items = items,
            onDismiss = { picking = false },
            onPick = { ids ->
                exerciseIds = ids.toSet()
                picking = false
            },
            multiSelect = true,
            searchLabel = stringResource(R.string.data_pick_search)
        )
    }
    if (confirming) {
        val fitNotesNote = if (imported > 0) " " + pluralStringResource(R.plurals.data_delete_fitnotes, imported, imported) else ""
        val busy = stringResource(R.string.data_deleting)
        val reason = stringResource(R.string.data_safety_reason)
        ConfirmSheet(
            title = pluralStringResource(R.plurals.data_delete_title, matching.size, matching.size),
            message = stringResource(R.string.data_delete_body, summary, exerciseLabel, rangeLabel, fitNotesNote, Backups.UNDO_DAYS),
            confirmLabel = pluralStringResource(R.plurals.data_delete_confirm, matching.size, matching.size),
            onDismiss = { confirming = false },
            onConfirm = {
                val ids = exerciseIds
                runBusy(busy) {
                    // The way back (#47). If it can't be made, nothing is deleted.
                    val safety = Backups.safetyCopy(ctx, reason)
                    if (!safety.ok) return@runBusy safety
                    val n = Workouts.deleteHistory(from, to, ids)
                    ImportSummary(ctx.resources.getQuantityString(R.plurals.data_deleted, n, n), ok = true)
                }
            }
        )
    }
}
