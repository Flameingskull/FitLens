package com.fitlens.companion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.BackupSync
import com.fitlens.companion.data.FileKind
import com.fitlens.companion.data.FitNotesImporter
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Settings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Sync tab: FitNotes imports and progress photos. Backups, personal records and the other settings live in
 * Settings (#38) since 1.0.21; #35 later folds this tab into Settings too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncScreen(snap: Snapshot, nav: Nav) {
    val importPhotos = rememberPhotoImporter()
    val importFolder = rememberFolderPhotoImporter()

    FitNotesImportHost()
    Column(Modifier.fillMaxSize()) {
        PlainTopBar("Sync & import")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FitNotesCards(snap)

            // ---------- Photos ----------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Progress photos", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${snap.photos.size} photos on ${snap.photosByDate.size} days" +
                            if (snap.reviewPhotos.isNotEmpty()) " · ${snap.reviewPhotos.size} need a date check" else "",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = importPhotos) { Text("Choose photos") }
                        OutlinedButton(onClick = importFolder) { Text("Import a folder") }
                        if (snap.reviewPhotos.isNotEmpty()) OutlinedButton(onClick = { nav.push(Screen.Review) }) { Text("Check dates") }
                    }
                    Text(
                        "Each photo is dated from its camera metadata (EXIF), then the media library, then its file name. " +
                            "Re-importing the same photo is detected and skipped. You can also share photos from your gallery to FitLens.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- Backups moved to Settings ----------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backups", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Backup files, automatic backups, the safety copy and PDF reports are in Settings, " +
                            "under the gear at the top of every tab.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { nav.push(Screen.SettingsPage(SettingsSection.Backups)) }) { Text("Open Backups") }
                }
            }
            HorizontalDivider()
            Text(
                "FitLens never changes your FitNotes data. It only reads FitNotes backups.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Importing from FitNotes: a backup file, or the FitNotes backup folder with optional auto-sync. Shown on the Sync
 * tab and in Settings → Import & sync. The screen showing it also needs a [FitNotesImportHost].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FitNotesCards(snap: Snapshot) {
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

    // ---------- FitNotes ----------
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("FitNotes data", style = MaterialTheme.typography.titleMedium)
            val status = if (lastName == null) "Nothing imported yet." else {
                val at = lastAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")) } ?: ""
                "Last import: $lastName ($at)\n${snap.setsByDate.size} workouts · ${snap.sets.size} sets · ${snap.records.size} body records"
            }
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { openBackup.launch(arrayOf("*/*")) }) { Text("Import backup file") }
                OutlinedButton(onClick = {
                    if (!BackupSync.launchFitNotes(ctx)) UiEvents.show("FitNotes isn't installed on this phone.")
                }) { Text("Open FitNotes") }
            }
            Text(
                "Accepts FitNotes backups (.fitnotes, which include everything) and Body Tracker CSV exports. " +
                    "You can also share a backup from FitNotes straight to FitLens. Imports merge: you'll see what " +
                    "will be added first, anything already in FitLens is skipped, and nothing you logged or edited " +
                    "in FitLens is deleted or changed.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // ---------- Auto sync ----------
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("FitNotes backup folder", style = MaterialTheme.typography.titleMedium)
            Text(
                "For moving over from FitNotes gradually. Choose the folder where FitNotes saves its backups, then " +
                    "tap Sync now to import the newest one. With automatic sync on, FitLens also imports it quietly " +
                    "each time it opens, if it has changed.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                folder?.let { "Folder: " + (it.lastPathSegment?.substringAfter(':')?.ifBlank { "(root)" } ?: it.toString()) } ?: "No folder chosen",
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickFolder.launch(null) }) { Text(if (folder == null) "Choose folder" else "Change folder") }
                if (folder != null) Button(onClick = { FitNotesImports.startFromFolder(ctx) }) { Text("Sync now") }
            }
            if (folder != null) ToggleRow("Sync automatically when FitLens opens (off by default)", autoSync) {
                BackupSync.setAutoSync(it)
            }
        }
    }
}
