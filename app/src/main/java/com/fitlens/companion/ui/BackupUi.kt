package com.fitlens.companion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fitlens.companion.data.AutoBackup
import com.fitlens.companion.data.Backups
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.ImportSummary
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.report.PdfReport
import com.fitlens.companion.report.ReportOptions
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun SubHeading(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Backups section of the Sync tab: backup files, automatic backups and PDF reports. Everything stays on the device. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BackupsCard(snap: Snapshot) {
    val ctx = LocalContext.current.applicationContext
    var autoFolder by remember { mutableStateOf(Backups.autoFolder()) }
    var autoDays by remember { mutableIntStateOf(Backups.autoDays()) }
    var keep by remember { mutableIntStateOf(Backups.autoKeep()) }
    var afterChanges by remember { mutableStateOf(AutoBackup.afterChangesEnabled()) }
    val busy by UiEvents.busy.collectAsState()
    // Re-read after each backup (the busy overlay closes) and after data changes.
    val lastAuto = remember(snap, busy, autoFolder) { Backups.lastAutoBackup() }
    val lastError = remember(snap, busy, autoFolder) { Backups.lastError() }
    val nextDue = remember(snap, busy, autoFolder, autoDays) { AutoBackup.nextDue() }
    var folderStatus by remember { mutableStateOf<AutoBackup.FolderStatus?>(null) }
    LaunchedEffect(autoFolder, busy) {
        if (busy == null) folderStatus = AutoBackup.folderStatus(ctx)
    }
    // Android 13+ asks for the notification permission the first time automatic backups are set up, so FitLens can
    // say when the backup folder can't be reached.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotifyPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun fmtTime(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreInfo by remember { mutableStateOf<Backups.Info?>(null) }
    var showReport by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    // The safety copy (#47) and the last result (#62), re-read whenever a job finishes.
    val undoAt = remember(snap, busy) { if (Backups.undoAvailable(ctx)) Backups.undoAt() else null }
    val undoReason = remember(snap, busy) { Backups.undoReason() }
    val lastResult by UiEvents.lastResult.collectAsState()

    fun undo() = runBusy("Putting your previous data back…") { Backups.undoLastRestore(ctx) }
    var reportOpts by remember { mutableStateOf<ReportOptions?>(null) }

    fun inspect(uri: Uri) {
        AppScope.scope.launch {
            UiEvents.busy.value = "Checking backup…"
            val info = try { Backups.inspect(ctx, uri) } finally { UiEvents.busy.value = null }
            if (info == null) UiEvents.show("That isn't a FitLens backup file.")
            else { restoreUri = uri; restoreInfo = info }
        }
    }

    // A backup opened from a file manager or shared to FitLens
    val pending by UiEvents.pendingRestore.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { UiEvents.pendingRestore.value = null; inspect(it) }
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backups.MIME)) { uri ->
        if (uri != null) runBusy("Saving backup…") { Backups.export(ctx, uri) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) inspect(uri)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            Backups.setAutoFolder(ctx, uri)
            autoFolder = uri
            autoDays = Backups.autoDays()
            AutoBackup.schedule(ctx)
            ensureNotifyPermission()
            runBusy("Saving the first automatic backup…") { Backups.backupToFolder(ctx) }
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val o = reportOpts
        if (uri != null && o != null) runBusy("Creating PDF…") {
            val s = Store.snapshot.value ?: snap
            val pages = ctx.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                PdfReport.create(s, o, os) { UiEvents.busy.value = it }
            } ?: return@runBusy ImportSummary("Couldn't write the PDF.", false)
            ImportSummary("PDF report saved ($pages pages).", true)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backups", style = MaterialTheme.typography.titleMedium)
            Hint("Everything stays on your devices. No account or internet connection is needed.")

            SubHeading("Backup file")
            Hint(
                "One .fitlens file with all your data and photos. Keep a copy off your phone (computer, USB drive or SD card) " +
                    "to restore after reinstalling FitLens or on a new phone."
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { saveBackup.launch(Backups.fileName()) }) { Text("Save backup") }
                OutlinedButton(onClick = { openBackup.launch(arrayOf("*/*")) }) { Text("Restore backup") }
            }

            SubHeading("Automatic backups")
            Text(
                autoFolder?.let { "Folder: " + (it.lastPathSegment?.substringAfter(':')?.ifBlank { "(root)" } ?: it.toString()) }
                    ?: "Choose a folder outside FitLens, such as Documents or an SD card, so backups survive uninstalling.",
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickFolder.launch(null) }) { Text(if (autoFolder == null) "Choose folder" else "Change folder") }
                if (autoFolder != null) Button(onClick = { runBusy("Backing up…") { Backups.backupToFolder(ctx) } }) { Text("Back up now") }
            }
            if (autoFolder != null) {
                Hint("How often. Backups run in the background, even when FitLens is closed, while the battery isn't low.")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Off", 1 to "Daily", 7 to "Weekly").forEach { (d, label) ->
                        FilterChip(selected = autoDays == d, onClick = {
                            autoDays = d
                            Backups.setAutoDays(d)
                            AutoBackup.schedule(ctx)
                        }, label = { Text(label) })
                    }
                }
                Hint("Keep the newest")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 10).forEach { n ->
                        FilterChip(selected = keep == n, onClick = { keep = n; Backups.setAutoKeep(n) }, label = { Text("$n backups") })
                    }
                }
                ToggleRow("Back up after changes", afterChanges) {
                    afterChanges = it
                    AutoBackup.setAfterChanges(it)
                    if (it) ensureNotifyPermission()
                }
                Hint("When you leave FitLens after changing something, a backup is saved in the background, at most once an hour.")

                SubHeading("Status")
                Text(
                    lastAuto?.let { "Last successful backup: " + fmtTime(it) } ?: "No automatic backup yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
                nextDue?.let { due ->
                    Hint(
                        if (due <= System.currentTimeMillis() + 5 * 60_000L) "Next scheduled backup: due now. It runs shortly, once the battery isn't low."
                        else "Next scheduled backup: from " + fmtTime(due)
                    )
                } ?: Hint("Scheduled backups are off.")
                folderStatus?.let { st ->
                    if (st.reachable) {
                        Hint("Backup folder: available" + (st.freeBytes?.let { " · " + Formatter.formatShortFileSize(ctx, it) + " free" } ?: ""))
                    } else {
                        Text(
                            "FitLens can't reach the backup folder. If it's on an SD card, check the card is in; otherwise choose the folder again.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                lastError?.let { (at, msg) ->
                    Text(
                        "Last attempt failed (${fmtTime(at)}): $msg",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (undoAt != null) {
                SubHeading("Safety copy")
                Text(
                    (undoReason ?: "Safety copy") + ", " + fmtTime(undoAt) + ".",
                    style = MaterialTheme.typography.bodyMedium
                )
                Hint(
                    "FitLens keeps a copy of your data from just before the last restore or import, for " +
                        "${Backups.UNDO_DAYS} days. Undo puts that data back and replaces what is there now."
                )
                OutlinedButton(onClick = { confirmUndo = true }) { Text("Undo") }
            }

            lastResult?.let { r ->
                SubHeading("Last result")
                Text(
                    fmtTime(r.at) + ": " + r.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (r.level == ResultLevel.Failure || r.level == ResultLevel.Warning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { UiEvents.clearLastResult() }) { Text("Clear") }
            }

            SubHeading("PDF report")
            Hint("A readable report of your photos, measurements, charts and workouts in the FitLens style. Good for printing or sharing.")
            Button(onClick = { showReport = true }, enabled = snap.allDates.isNotEmpty()) { Text("Create PDF report") }
        }
    }

    val info = restoreInfo
    val uri = restoreUri
    if (info != null && uri != null) {
        RestoreDialog(info, onDismiss = { restoreInfo = null; restoreUri = null }) {
            restoreInfo = null
            restoreUri = null
            runBusy("Restoring backup…") {
                val r = Backups.restore(ctx, uri)
                if (r.ok && Backups.undoAvailable(ctx)) {
                    UiEvents.show(r.message, ResultLevel.Success, "Undo") { undo() }
                    null
                } else r
            }
        }
    }
    if (confirmUndo) {
        ConfirmDialog(
            title = "Put your previous data back?",
            text = "This replaces everything in FitLens now with the safety copy from " +
                (undoAt?.let { fmtTime(it) } ?: "before the last restore or import") +
                ". Anything added since then will be lost.",
            confirm = "Undo",
            onDismiss = { confirmUndo = false }
        ) {
            confirmUndo = false
            undo()
        }
    }
    if (showReport) {
        ReportDialog(snap, onDismiss = { showReport = false }) { o ->
            showReport = false
            reportOpts = o
            savePdf.launch("FitLens_Report_${o.from}_to_${o.to}.pdf")
        }
    }
}

@Composable
private fun RestoreDialog(info: Backups.Info, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore this backup?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val made = info.createdAt?.let { Dates.medium(it) + (if (it.length >= 16) ", " + it.substring(11, 16) else "") }
                if (made != null) Text("Made $made" + (info.appVersion?.let { " with FitLens $it" } ?: ""))
                else if (info.legacy) Text("An archive from an earlier version of FitLens.")
                Text("${info.photos} photos" + (info.workouts?.let { " · $it workouts" } ?: "") + (info.records?.let { " · $it body records" } ?: ""))
                if (info.firstDate != null && info.lastDate != null) Text("Covers ${Dates.medium(info.firstDate)} – ${Dates.medium(info.lastDate)}")
                Text(
                    "This replaces everything currently in FitLens on this phone. FitLens keeps a safety copy of the " +
                        "current data for ${Backups.UNDO_DAYS} days so you can undo, but a saved backup off the phone is safer still.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReportDialog(snap: Snapshot, onDismiss: () -> Unit, onCreate: (ReportOptions) -> Unit) {
    val first = snap.allDates.lastOrNull() ?: Dates.today()
    val last = snap.allDates.firstOrNull() ?: Dates.today()
    var from by remember { mutableStateOf(first) }
    var to by remember { mutableStateOf(last) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(true) }
    var measurements by remember { mutableStateOf(true) }
    var training by remember { mutableStateOf(true) }
    var daily by remember { mutableStateOf(true) }
    var perDay by remember { mutableIntStateOf(2) }
    var onlyPhotoDays by remember { mutableStateOf(false) }
    var hq by remember { mutableStateOf(false) }

    val opts = ReportOptions(from, to, dark, measurements, training, daily, perDay, onlyPhotoDays, hq)
    val photos = remember(opts) { PdfReport.photoCount(snap, opts) }
    val days = remember(opts) { PdfReport.daysIn(snap, opts).size }
    val estMb = (photos * (if (hq) 0.45 else 0.18) + days * 0.01).coerceAtLeast(0.1)

    fun range(monthsBack: Long?) {
        to = last
        from = if (monthsBack == null) first else maxOf(first, LocalDate.parse(last).minusMonths(monthsBack).toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF report") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Period", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = from == first && to == last, onClick = { range(null) }, label = { Text("All") })
                    FilterChip(selected = false, onClick = { range(12) }, label = { Text("Last year") })
                    FilterChip(selected = false, onClick = { range(3) }, label = { Text("3 months") })
                    FilterChip(selected = false, onClick = { range(1) }, label = { Text("1 month") })
                }
                FlowRow {
                    TextButton(onClick = { pickFrom = true }) { Text("From ${Dates.medium(from)}") }
                    TextButton(onClick = { pickTo = true }) { Text("To ${Dates.medium(to)}") }
                }

                Text("Include", style = MaterialTheme.typography.labelLarge)
                ToggleRow("Measurement charts and stats", measurements) { measurements = it }
                ToggleRow("Training summary", training) { training = it }
                ToggleRow("Daily log", daily) { daily = it }
                if (daily) {
                    ToggleRow("Only days with photos", onlyPhotoDays) { onlyPhotoDays = it }
                    Text("Photos per day", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..4).forEach { n -> FilterChip(selected = perDay == n, onClick = { perDay = n }, label = { Text("$n") }) }
                    }
                }

                Text("Style", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = dark, onClick = { dark = true }, label = { Text("Dark (as in the app)") })
                    FilterChip(selected = !dark, onClick = { dark = false }, label = { Text("Light (for printing)") })
                }
                ToggleRow("High-quality photos", hq) { hq = it }

                Text(
                    "$days days · about $photos photos · roughly ${if (estMb < 1) "<1" else "%.0f".format(estMb)} MB",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (photos > 400) Text(
                    "This is a large report and may take a few minutes. Fewer photos per day or a shorter period makes it faster and smaller.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (from > to) UiEvents.show("The start date is after the end date") else onCreate(opts)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (pickFrom) PickDateDialog(from, onDismiss = { pickFrom = false }) { from = it }
    if (pickTo) PickDateDialog(to, onDismiss = { pickTo = false }) { to = it }
}
