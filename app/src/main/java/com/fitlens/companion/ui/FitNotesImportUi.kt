package com.fitlens.companion.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.BackupSync
import com.fitlens.companion.data.Backups
import com.fitlens.companion.data.FitNotesImporter
import com.fitlens.companion.data.ImportSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * FitNotes imports that go through the pre-import summary (#6): the backup is read and checked first, the user sees
 * what it adds and skips, can save a FitLens backup first, and then confirms.
 */
object FitNotesImports {
    /** A FitNotes backup opened or shared from outside FitLens, waiting for Settings → FitNotes import to show its summary. */
    val pending = MutableStateFlow<Uri?>(null)
    /** A checked backup waiting for the user to confirm. */
    val staged = MutableStateFlow<FitNotesImporter.StagedImport?>(null)

    fun start(ctx: Context, uri: Uri, modified: Long = 0L) {
        AppScope.scope.launch {
            UiEvents.busy.value = "Reading the FitNotes backup…"
            val p = try {
                FitNotesImporter.prepare(ctx, uri, modified)
            } finally {
                UiEvents.busy.value = null
            }
            val s = p.staged
            if (s == null) {
                UiEvents.show(p.error ?: "Couldn't read that backup.")
            } else {
                staged.value?.let { FitNotesImporter.discard(it) }
                staged.value = s
            }
        }
    }

    /** Finds the newest backup in the FitNotes backup folder and shows its summary. */
    fun startFromFolder(ctx: Context) {
        AppScope.scope.launch {
            UiEvents.busy.value = "Looking for the newest FitNotes backup…"
            val found = try {
                BackupSync.newestBackup(ctx)
            } finally {
                UiEvents.busy.value = null
            }
            when {
                found != null -> start(ctx, found.uri, found.modified)
                BackupSync.folder() == null -> UiEvents.show("Choose your FitNotes backup folder first.")
                else -> UiEvents.show("No .fitnotes backups found in the chosen folder.")
            }
        }
    }
}

/** Shows the summary for a staged FitNotes import and runs it when confirmed. Placed on Settings → FitNotes import. */
@Composable
fun FitNotesImportHost() {
    val ctx = LocalContext.current.applicationContext
    val pending by FitNotesImports.pending.collectAsState()
    LaunchedEffect(pending) {
        pending?.let {
            FitNotesImports.pending.value = null
            FitNotesImports.start(ctx, it)
        }
    }
    val backupFirst = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backups.MIME)) { uri ->
        val s = FitNotesImports.staged.value
        if (uri != null && s != null) {
            FitNotesImports.staged.value = null
            runBusy("Saving a FitLens backup…") {
                val b = Backups.export(ctx, uri)
                if (!b.ok) {
                    FitNotesImports.staged.value = s // back to the summary so the user can decide
                    b
                } else {
                    UiEvents.busy.value = "Importing the FitNotes backup…"
                    val r = FitNotesImporter.importStaged(s)
                    ImportSummary("FitLens backup saved. " + r.message, r.ok)
                }
            }
        }
    }
    val staged by FitNotesImports.staged.collectAsState()
    val s = staged ?: return
    val plan = s.plan
    val close = {
        FitNotesImports.staged.value = null
        FitNotesImporter.discard(s)
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("Import FitNotes backup") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (plan.nothingNew) {
                    Text("Nothing new: everything in this backup is already in FitLens.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    SummaryHeading("Will be added")
                    plan.addedLines().forEach { Text("·  $it", style = MaterialTheme.typography.bodyMedium) }
                }
                val skipped = plan.skippedLines()
                if (skipped.isNotEmpty()) {
                    SummaryHeading("Skipped, already in FitLens")
                    skipped.forEach {
                        Text("·  $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Imports only add to FitLens. Nothing you logged or edited in FitLens is deleted or changed, " +
                        "and FitNotes' own data isn't touched.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (!plan.nothingNew) {
                    OutlinedButton(onClick = { backupFirst.launch(Backups.manualFileName()) }, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Save a FitLens backup first")
                    }
                }
            }
        },
        confirmButton = {
            if (plan.nothingNew) {
                TextButton(onClick = close) { Text("Close") }
            } else {
                TextButton(onClick = {
                    FitNotesImports.staged.value = null
                    runBusy("Importing the FitNotes backup…") { FitNotesImporter.importStaged(s) }
                }) { Text("Import") }
            }
        },
        dismissButton = {
            if (!plan.nothingNew) TextButton(onClick = close) { Text("Cancel") }
        }
    )
}

@Composable
private fun SummaryHeading(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp)
    )
}
