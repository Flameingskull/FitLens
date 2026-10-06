package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.DropdownPill
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.PickerPill
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SegmentedSwitch
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsFolderRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.SettingsStatusCard
import com.fitlens.companion.ui.design.SettingsSwitchRow
import com.fitlens.companion.ui.design.StatusLine
import android.content.Context
import android.net.Uri
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.AutoBackup
import com.fitlens.companion.data.Backups
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.ImportSummary
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.report.PdfReport
import com.fitlens.companion.report.ReportOptions
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings → Backup: backup files, automatic backups and PDF reports. Everything stays on the device. The page's text
 * is in `res/values/strings.xml` (#94); messages from callbacks read it through `res`.
 */
@Composable
fun BackupsPage(snap: Snapshot) {
    val ctx = LocalContext.current.applicationContext
    val res = LocalContext.current.resources
    // Every value here comes from Settings, so the screen follows each change live, including ones a background
    // backup makes while it's open (#38).
    val device by Settings.device.collectAsState()
    val prefs by Settings.portable.collectAsState()
    val autoFolder = device.autoBackupFolder?.let { Uri.parse(it) }
    val autoDays = device.autoBackupDays
    val keep = device.autoBackupKeep
    val afterChanges = device.backupAfterChanges
    val busy by UiEvents.busy.collectAsState()
    val lastAuto = device.autoBackupLast
    val lastError = Backups.parseError(device.autoBackupError)
    val nextDue = remember(device, busy) { AutoBackup.nextDue() }
    var folderStatus by remember { mutableStateOf<AutoBackup.FolderStatus?>(null) }
    LaunchedEffect(autoFolder, busy) {
        if (busy == null) folderStatus = AutoBackup.folderStatus(ctx)
    }
    // Automatic backups tell the user when the folder can't be reached, so setting them up asks for notifications,
    // with the reason first (#41).
    val notify = rememberNotificationAccess()
    val notifyReason = stringResource(R.string.notify_reason_backup)
    fun ensureNotifyPermission() = notify.ask(notifyReason)
    fun fmtTime(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreInfo by remember { mutableStateOf<Backups.Info?>(null) }
    var showReport by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    // The safety copy (#47) and the last result (#62), re-read whenever a job finishes.
    val undoAt = remember(device, busy) { if (Backups.undoAvailable(ctx)) device.safetyAt else null }
    val undoReason = device.safetyReason
    val lastResult by UiEvents.lastResult.collectAsState()

    fun undo() = runBusy(res.getString(R.string.backup_busy_undo)) { Backups.undoLastRestore(ctx) }
    var reportOpts by remember { mutableStateOf<ReportOptions?>(null) }

    fun inspect(uri: Uri) {
        AppScope.scope.launch {
            UiEvents.busy.value = res.getString(R.string.backup_busy_checking)
            val info = try { Backups.inspect(ctx, uri) } finally { UiEvents.busy.value = null }
            if (info == null) UiEvents.show(res.getString(R.string.backup_not_fitlens))
            else { restoreUri = uri; restoreInfo = info }
        }
    }

    // A backup opened from a file manager or shared to FitLens
    val pending by UiEvents.pendingRestore.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { UiEvents.pendingRestore.value = null; inspect(it) }
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backups.MIME)) { uri ->
        if (uri != null) runBusy(res.getString(R.string.backup_busy_saving)) { Backups.export(ctx, uri) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) inspect(uri)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            Backups.setAutoFolder(ctx, uri)
            AutoBackup.schedule(ctx)
            ensureNotifyPermission()
            runBusy(res.getString(R.string.backup_busy_first_auto)) { Backups.backupToFolder(ctx) }
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val o = reportOpts
        if (uri != null && o != null) runBusy(res.getString(R.string.backup_busy_pdf)) {
            val s = Store.snapshot.value ?: snap
            val pages = ctx.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                PdfReport.create(res, s, o, os) { UiEvents.busy.value = it }
            } ?: return@runBusy ImportSummary(res.getString(R.string.backup_pdf_failed), false)
            ImportSummary(res.getQuantityString(R.plurals.backup_pdf_saved, pages, pages), true)
        }
    }

    // Built from the shared settings rows (#86), like every other Settings page.
    SettingsNote(stringResource(R.string.backup_local_note))

    SettingsGroup(stringResource(R.string.backup_group_file))
    SettingsActionRow(stringResource(R.string.backup_save), stringResource(R.string.backup_save_summary)) {
        saveBackup.launch(Backups.manualFileName())
    }
    SettingsActionRow(
        stringResource(R.string.backup_share),
        stringResource(R.string.backup_share_summary),
        enabled = busy == null
    ) { shareBackup(ctx) }
    SettingsActionRow(stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_summary)) {
        openBackup.launch(arrayOf("*/*"))
    }
    SettingsSwitchRow(stringResource(R.string.backup_timestamp), prefs.backupTimestamp) { on ->
        Settings.updatePortable { it.copy(backupTimestamp = on) }
    }

    SettingsGroup(stringResource(R.string.backup_group_auto))
    val root = stringResource(R.string.settings_folder_root)
    SettingsFolderRow(
        stringResource(R.string.backup_folder),
        autoFolder?.let { folderLabel(it, root) },
        summary = stringResource(R.string.backup_choose_folder_summary),
        lost = folderStatus?.reachable == false,
        lostText = stringResource(R.string.settings_folder_lost)
    ) { pickFolder.launch(null) }
    if (autoFolder != null) {
        SettingsActionRow(stringResource(R.string.backup_now), enabled = busy == null) {
            runBusy(res.getString(R.string.backup_busy_now)) { Backups.backupToFolder(ctx) }
        }
        val often = listOf(
            0 to stringResource(R.string.backup_off),
            1 to stringResource(R.string.backup_daily),
            7 to stringResource(R.string.backup_weekly)
        )
        SettingsChoiceRow(
            stringResource(R.string.backup_how_often),
            often.map { it.second },
            often.indexOfFirst { it.first == autoDays }.coerceAtLeast(0),
            summary = stringResource(R.string.backup_how_often_summary)
        ) { i ->
            Backups.setAutoDays(often[i].first)
            AutoBackup.schedule(ctx)
        }
        val keeps = listOf(3, 5, 10)
        SettingsChoiceRow(
            stringResource(R.string.backup_keep),
            keeps.map { pluralStringResource(R.plurals.backup_keep_count, it, it) },
            keeps.indexOf(keep).coerceAtLeast(0),
            summary = stringResource(R.string.backup_keep_summary)
        ) { i -> Backups.setAutoKeep(keeps[i]) }
        SettingsSwitchRow(
            stringResource(R.string.backup_after), afterChanges,
            summary = stringResource(R.string.backup_after_summary)
        ) {
            AutoBackup.setAfterChanges(it)
            if (it) ensureNotifyPermission()
        }
        NotificationsOffNote(notify, afterChanges || autoDays > 0)

        // One status card (#41): each line with a tick, or a warning icon for a problem.
        SettingsGroup(stringResource(R.string.backup_group_status))
        val status = mutableListOf(
            StatusLine(lastAuto?.let { stringResource(R.string.backup_last_ok, fmtTime(it)) } ?: stringResource(R.string.backup_none_yet)),
            StatusLine(
                nextDue?.let { due ->
                    if (due <= System.currentTimeMillis() + 5 * 60_000L) stringResource(R.string.backup_next_due_now)
                    else stringResource(R.string.backup_next_from, fmtTime(due))
                } ?: stringResource(R.string.backup_schedule_off)
            )
        )
        folderStatus?.let { st ->
            val free = st.freeBytes
            status += when {
                !st.reachable -> StatusLine(stringResource(R.string.backup_folder_unreachable), problem = true)
                free == null -> StatusLine(stringResource(R.string.backup_folder_ok))
                else -> StatusLine(stringResource(R.string.backup_folder_ok_free, Formatter.formatShortFileSize(ctx, free)))
            }
        }
        lastError?.let { (at, msg) -> status += StatusLine(stringResource(R.string.backup_last_failed, fmtTime(at), msg), problem = true) }
        SettingsStatusCard(status)
    }

    if (undoAt != null) {
        SettingsGroup(stringResource(R.string.backup_group_safety))
        SettingsActionRow(
            stringResource(R.string.undo),
            pluralStringResource(R.plurals.backup_undo_summary, Backups.UNDO_DAYS, Backups.UNDO_DAYS),
            value = stringResource(R.string.backup_safety_value, undoReason ?: stringResource(R.string.backup_safety_copy), fmtTime(undoAt))
        ) { confirmUndo = true }
    }

    lastResult?.let { r ->
        SettingsGroup(stringResource(R.string.backup_group_last_result))
        SettingsNote(fmtTime(r.at) + ": " + r.text, error = r.level == ResultLevel.Failure || r.level == ResultLevel.Warning)
        SettingsActionRow(stringResource(R.string.backup_clear)) { UiEvents.clearLastResult() }
    }

    SettingsGroup(stringResource(R.string.backup_group_pdf))
    SettingsActionRow(
        stringResource(R.string.backup_pdf_create),
        stringResource(if (snap.allDates.isEmpty()) R.string.backup_pdf_empty else R.string.backup_pdf_summary),
        enabled = snap.allDates.isNotEmpty()
    ) { showReport = true }

    val info = restoreInfo
    val uri = restoreUri
    if (info != null && uri != null) {
        RestoreDialog(info, onDismiss = { restoreInfo = null; restoreUri = null }) {
            restoreInfo = null
            restoreUri = null
            runBusy(res.getString(R.string.backup_busy_restoring)) {
                val r = Backups.restore(ctx, uri)
                if (r.ok && Backups.undoAvailable(ctx)) {
                    UiEvents.show(r.message, ResultLevel.Success, res.getString(R.string.undo)) { undo() }
                    null
                } else r
            }
        }
    }
    if (confirmUndo) {
        ConfirmDialog(
            title = stringResource(R.string.backup_undo_title),
            text = undoAt?.let { stringResource(R.string.backup_undo_body, fmtTime(it)) } ?: stringResource(R.string.backup_undo_body_unknown),
            confirm = stringResource(R.string.undo),
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
        title = { Text(stringResource(R.string.restore_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val made = info.createdAt?.let { Dates.medium(it) + (if (it.length >= 16) ", " + it.substring(11, 16) else "") }
                val version = info.appVersion
                if (made != null) Text(
                    if (version == null) stringResource(R.string.restore_made, made)
                    else stringResource(R.string.restore_made_with, made, version)
                )
                else if (info.legacy) Text(stringResource(R.string.restore_legacy))
                val workouts = info.workouts
                val records = info.records
                Text(
                    listOfNotNull(
                        pluralStringResource(R.plurals.restore_photos, info.photos, info.photos),
                        if (workouts == null) null else pluralStringResource(R.plurals.restore_workouts, workouts, workouts),
                        if (records == null) null else pluralStringResource(R.plurals.restore_body_records, records, records)
                    ).joinToString(" · ")
                )
                if (info.firstDate != null && info.lastDate != null) {
                    Text(stringResource(R.string.restore_covers, Dates.medium(info.firstDate), Dates.medium(info.lastDate)))
                }
                Text(
                    pluralStringResource(R.plurals.restore_warning, Backups.UNDO_DAYS, Backups.UNDO_DAYS),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.restore_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/**
 * The PDF report's options as a FitLens sheet (#92). It opens with the page style and photos per day saved in
 * Settings → Progress photos and media (#46); the period and sections are chosen per report.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReportDialog(snap: Snapshot, onDismiss: () -> Unit, onCreate: (ReportOptions) -> Unit) {
    val first = snap.allDates.lastOrNull() ?: Dates.today()
    val last = snap.allDates.firstOrNull() ?: Dates.today()
    val saved = remember { Settings.currentPortable() }
    var from by remember { mutableStateOf(first) }
    var to by remember { mutableStateOf(last) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(saved.pdfDark) }
    var measurements by remember { mutableStateOf(true) }
    var training by remember { mutableStateOf(true) }
    var daily by remember { mutableStateOf(true) }
    var perDay by remember { mutableIntStateOf(saved.pdfPhotosPerDay.coerceIn(0, 4)) }
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

    val datesOrder = stringResource(R.string.report_dates_order)
    FitSheet(
        title = stringResource(R.string.report_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.report_create),
        onConfirm = {
            if (from > to) UiEvents.show(datesOrder) else onCreate(opts)
        }
    ) {
        SectionLabel(stringResource(R.string.report_period))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = from == first && to == last, onClick = { range(null) }, label = { Text(stringResource(R.string.report_all)) })
            FilterChip(selected = false, onClick = { range(12) }, label = { Text(stringResource(R.string.report_last_year)) })
            FilterChip(selected = false, onClick = { range(3) }, label = { Text(stringResource(R.string.report_3_months)) })
            FilterChip(selected = false, onClick = { range(1) }, label = { Text(stringResource(R.string.report_1_month)) })
        }
        val fromLabel = stringResource(R.string.report_from)
        val toLabel = stringResource(R.string.report_to_label)
        FlowRow(verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fromLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickerPill(fromLabel, Dates.medium(from)) { pickFrom = true }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.report_to), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickerPill(toLabel, Dates.medium(to)) { pickTo = true }
            }
        }

        SectionLabel(stringResource(R.string.report_include))
        ToggleRow(stringResource(R.string.report_measurements), measurements, inset = 0.dp) { measurements = it }
        ToggleRow(stringResource(R.string.report_training), training, inset = 0.dp) { training = it }
        ToggleRow(stringResource(R.string.report_daily), daily, inset = 0.dp) { daily = it }
        if (daily) {
            ToggleRow(stringResource(R.string.report_photo_days_only), onlyPhotoDays, inset = 0.dp) { onlyPhotoDays = it }
            val perDayLabel = stringResource(R.string.report_photos_per_day)
            val none = stringResource(R.string.report_none)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(perDayLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                DropdownPill(
                    label = perDayLabel,
                    options = (0..4).map { if (it == 0) none else "$it" },
                    selected = perDay
                ) { perDay = it }
            }
        }

        SectionLabel(stringResource(R.string.report_style))
        SegmentedSwitch(
            options = listOf(stringResource(R.string.report_dark), stringResource(R.string.report_light)),
            selected = if (dark) 0 else 1,
            onSelect = { dark = it == 0 }
        )
        ToggleRow(stringResource(R.string.report_hq), hq, inset = 0.dp) { hq = it }

        Text(
            stringResource(
                R.string.report_estimate,
                pluralStringResource(R.plurals.report_days, days, days),
                pluralStringResource(R.plurals.report_photos, photos, photos),
                if (estMb < 1) "<1" else "%.0f".format(estMb)
            ),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (photos > 400) Text(
            stringResource(R.string.report_large),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
        )
        Text(
            stringResource(R.string.report_defaults_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (pickFrom) PickDateDialog(from, onDismiss = { pickFrom = false }) { from = it }
    if (pickTo) PickDateDialog(to, onDismiss = { pickTo = false }) { to = it }
}

/** A chosen folder's name as the Settings folder rows show it: the path inside its storage, or [root] for the top. */
internal fun folderLabel(uri: Uri, root: String): String =
    uri.lastPathSegment?.substringAfter(':')?.ifBlank { root } ?: uri.toString()

/**
 * The problem behind automatic backups, for the main list's Automatic Backup row (#41, section 3): the last one
 * failed, or none has run for a day past its schedule. Null when they're off or fine. The folder itself is checked on
 * the Backup page, which reads it.
 */
internal fun autoBackupProblem(device: com.fitlens.companion.data.DeviceSettings, now: Long = System.currentTimeMillis()): Int? {
    if (device.autoBackupFolder == null) return null
    val error = Backups.parseError(device.autoBackupError)
    val last = device.autoBackupLast
    if (error != null && (last == null || error.first > last)) return R.string.settings_auto_backup_failed
    val days = device.autoBackupDays
    if (days > 0 && last != null && now > last + (days + 1) * 24L * 3600_000L) return R.string.settings_auto_backup_overdue
    return null
}

/** Makes a backup and opens the share sheet with it (#30). The busy overlay shows while the archive is written. */
private fun shareBackup(ctx: Context) {
    AppScope.scope.launch {
        UiEvents.busy.value = ctx.getString(R.string.backup_busy_preparing)
        val (file, result) = try { Backups.exportForShare(ctx) } finally { UiEvents.busy.value = null }
        if (file != null) shareFile(ctx, file, Backups.MIME) else UiEvents.show(result.message, result.level())
    }
}
