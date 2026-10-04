package com.fitlens.companion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.data.BackupSync
import com.fitlens.companion.data.FileKind
import com.fitlens.companion.data.FitNotesImporter
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.SettingsSwitchRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings → Import From FitNotes: a backup file, or the FitNotes backup folder with optional auto-sync. It replaced the
 * Sync tab (#35). The screen showing it also needs a [FitNotesImportHost]. Its text is in `res/values/strings.xml` (#94).
 */
@Composable
fun FitNotesImportPage(snap: Snapshot) {
    val ctx = LocalContext.current.applicationContext
    val res = LocalContext.current.resources
    val device by Settings.device.collectAsState()
    val folder = device.backupFolder?.let { android.net.Uri.parse(it) }
    val autoSync = device.autoSync
    val lastName = device.lastImportName
    val lastAt = device.lastImportAt

    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) when (FitNotesImporter.sniff(ctx, uri)) {
            // A FitNotes backup shows what it adds before anything is imported (FitNotesImportHost).
            FileKind.FITNOTES_BACKUP -> FitNotesImports.start(ctx, uri)
            FileKind.BODY_CSV -> runBusy(res.getString(R.string.import_busy)) { FitNotesImporter.importBodyCsv(ctx, uri) }
            else -> UiEvents.show(res.getString(R.string.import_not_fitnotes))
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            BackupSync.setFolder(ctx, uri)
            FitNotesImports.startFromFolder(ctx)
        }
    }

    // Built from the shared settings rows (#86), like every other Settings page.
    SettingsGroup(stringResource(R.string.import_group_data))
    SettingsNote(
        if (lastName == null) stringResource(R.string.import_nothing_yet) else {
            val at = lastAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")) } ?: ""
            val workouts = snap.setsByDate.size
            val sets = snap.sets.size
            val records = snap.records.size
            stringResource(R.string.import_last, lastName, at) + "\n" + listOf(
                pluralStringResource(R.plurals.restore_workouts, workouts, workouts),
                pluralStringResource(R.plurals.import_sets, sets, sets),
                pluralStringResource(R.plurals.restore_body_records, records, records)
            ).joinToString(" · ")
        }
    )
    SettingsActionRow(stringResource(R.string.import_file), stringResource(R.string.import_file_summary)) {
        openBackup.launch(arrayOf("*/*"))
    }
    val notInstalled = stringResource(R.string.import_fitnotes_missing)
    SettingsActionRow(stringResource(R.string.import_open_fitnotes), stringResource(R.string.import_open_fitnotes_summary)) {
        if (!BackupSync.launchFitNotes(ctx)) UiEvents.show(notInstalled)
    }

    SettingsGroup(stringResource(R.string.import_group_folder))
    SettingsNote(stringResource(R.string.import_folder_note))
    val root = stringResource(R.string.settings_folder_root)
    SettingsActionRow(
        stringResource(if (folder == null) R.string.import_choose_folder else R.string.import_folder),
        stringResource(if (folder == null) R.string.import_no_folder else R.string.settings_tap_to_change),
        value = folder?.let { it.lastPathSegment?.substringAfter(':')?.ifBlank { root } ?: it.toString() }
    ) { pickFolder.launch(null) }
    if (folder != null) {
        SettingsActionRow(stringResource(R.string.import_sync_now), stringResource(R.string.import_sync_now_summary)) {
            FitNotesImports.startFromFolder(ctx)
        }
        SettingsSwitchRow(
            stringResource(R.string.import_sync_auto), autoSync,
            summary = stringResource(R.string.import_sync_auto_summary)
        ) { BackupSync.setAutoSync(it) }
    }
    SettingsNote(stringResource(R.string.import_read_only_note))
}
