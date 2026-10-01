package com.fitlens.companion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
 * Settings → FitNotes import: a backup file, or the FitNotes backup folder with optional auto-sync. It replaced the
 * Sync tab (#35). The screen showing it also needs a [FitNotesImportHost].
 */
@Composable
fun FitNotesImportPage(snap: Snapshot) {
    val ctx = LocalContext.current.applicationContext
    val device by Settings.device.collectAsState()
    val folder = device.backupFolder?.let { android.net.Uri.parse(it) }
    val autoSync = device.autoSync
    val lastName = device.lastImportName
    val lastAt = device.lastImportAt

    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) when (FitNotesImporter.sniff(ctx, uri)) {
            // A FitNotes backup shows what it adds before anything is imported (FitNotesImportHost).
            FileKind.FITNOTES_BACKUP -> FitNotesImports.start(ctx, uri)
            FileKind.BODY_CSV -> runBusy("Importing…") { FitNotesImporter.importBodyCsv(ctx, uri) }
            else -> UiEvents.show("That isn't a FitNotes backup (.fitnotes) or Body Tracker CSV.")
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            BackupSync.setFolder(ctx, uri)
            FitNotesImports.startFromFolder(ctx)
        }
    }

    // Built from the shared settings rows (#86), like every other Settings page.
    SettingsGroup("FitNotes data")
    SettingsNote(
        if (lastName == null) "Nothing imported yet." else {
            val at = lastAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")) } ?: ""
            "Last import: $lastName ($at)
${snap.setsByDate.size} workouts · ${snap.sets.size} sets · ${snap.records.size} body records"
        }
    )
    SettingsActionRow(
        "Import a backup file",
        "FitNotes backups (.fitnotes, which include everything) and Body Tracker CSV exports. Imports merge: you'll " +
            "see what will be added first, anything already in FitLens is skipped, and nothing you logged or edited " +
            "in FitLens is deleted or changed."
    ) { openBackup.launch(arrayOf("*/*")) }
    SettingsActionRow(
        "Open FitNotes",
        "Make a fresh backup there, then share it straight to FitLens."
    ) { if (!BackupSync.launchFitNotes(ctx)) UiEvents.show("FitNotes isn't installed on this phone.") }

    SettingsGroup("FitNotes backup folder")
    SettingsNote(
        "For moving over from FitNotes gradually. Choose the folder where FitNotes saves its backups, then tap Sync " +
            "now to import the newest one."
    )
    SettingsActionRow(
        if (folder == null) "Choose the FitNotes folder" else "FitNotes folder",
        if (folder == null) "No folder chosen yet." else "Tap to change it.",
        value = folder?.let { it.lastPathSegment?.substringAfter(':')?.ifBlank { "(root)" } ?: it.toString() }
    ) { pickFolder.launch(null) }
    if (folder != null) {
        SettingsActionRow("Sync now", "Imports the newest backup in the folder, if it has changed.") {
            FitNotesImports.startFromFolder(ctx)
        }
        SettingsSwitchRow(
            "Sync automatically", autoSync,
            summary = "Off by default. When on, FitLens quietly imports the newest backup each time it opens, if it has changed."
        ) { BackupSync.setAutoSync(it) }
    }
    SettingsNote("FitLens never changes your FitNotes data. It only reads FitNotes backups.")
}
