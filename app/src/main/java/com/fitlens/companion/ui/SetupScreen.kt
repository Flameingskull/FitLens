package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fitlens.companion.data.AutoBackup
import com.fitlens.companion.data.Backups
import com.fitlens.companion.data.FileKind
import com.fitlens.companion.data.FitNotesImporter
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.StarterLibrary
import kotlinx.coroutines.launch
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.StandardMeasurements
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.SegmentedSwitch
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.fitlens.companion.R

/**
 * The guided setup (#29): shown once on a fresh install, skippable at every step, and run again from Settings.
 * Each step only calls what the matching Settings page or tab already uses, so setup never has its own copy of a
 * feature: units ([Settings]), automatic backups ([Backups], [AutoBackup]), the FitNotes merge import
 * ([FitNotesImports]), photo import and the starter library.
 *
 * Choosing to restore a FitLens backup ends setup and hands the file to Settings → Backup, which checks it and asks
 * before replacing anything, because the backup brings its own preferences with it.
 */
private enum class SetupStep(@StringRes val title: Int) {
    Welcome(R.string.su_step_welcome),
    Units(R.string.su_step_units),
    Backups(R.string.su_step_backups),
    FitNotes(R.string.su_step_fitnotes),
    Photos(R.string.su_step_photos),
    Exercises(R.string.su_step_exercises)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(snap: Snapshot, nav: Nav) {
    var stepIdx by rememberSaveable { mutableIntStateOf(0) }
    val step = SetupStep.entries[stepIdx]
    val last = stepIdx == SetupStep.entries.lastIndex

    fun finish() {
        Settings.updateDevice { it.copy(setupDone = true) }
        nav.home()
    }

    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            Settings.updateDevice { it.copy(setupDone = true) }
            // Settings → Backup inspects the file and asks before restoring (BackupsPage).
            nav.home()
            nav.push(Screen.SettingsHome)
            nav.push(Screen.SettingsPage(SettingsSection.Backups))
            UiEvents.pendingRestore.value = uri
        }
    }

    BackHandler { if (stepIdx > 0) stepIdx-- else finish() }
    FitNotesImportHost()

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = stringResource(R.string.su_title),
            subtitle = stringResource(R.string.su_step_of, stepIdx + 1, SetupStep.entries.size, stringResource(R.string.step.title)),
            centered = false,
            trailing = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.su_skip)) } }
        )
        GoldHairline()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (step) {
                SetupStep.Welcome -> WelcomeStep(onRestore = { restore.launch(arrayOf("*/*")) })
                SetupStep.Units -> UnitsStep()
                SetupStep.Backups -> BackupsStep()
                SetupStep.FitNotes -> FitNotesStep(snap)
                SetupStep.Photos -> PhotosStep(snap)
                SetupStep.Exercises -> ExercisesStep(snap)
            }
        }
        GoldHairline()
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (stepIdx > 0) TextButton(onClick = { stepIdx-- }) { Text(stringResource(R.string.su_back)) }
            Spacer(Modifier.weight(1f))
            GoldButton(onClick = { if (last) finish() else stepIdx++ }) {
                Text(if (last) stringResource(R.string.su_finish) else if (step == SetupStep.Welcome) stringResource(R.string.su_title) else stringResource(R.string.su_next))
            }
        }
    }
}

@Composable
private fun StepHeading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun StepText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StepStatus(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun WelcomeStep(onRestore: () -> Unit) {
    StepHeading(stringResource(R.string.su_welcome_head))
    StepText(stringResource(R.string.su_welcome_1))
    StepText(stringResource(R.string.su_welcome_2))
    SectionTitle(stringResource(R.string.su_moving))
    StepText(stringResource(R.string.su_moving_body))
    GlassOutlinedButton(onClick = onRestore) { Text(stringResource(R.string.su_restore)) }
}

@Composable
private fun UnitsStep() {
    val prefs by Settings.portable.collectAsState()
    StepHeading(stringResource(R.string.su_units_head))
    SegmentedSwitch(
        options = listOf(stringResource(R.string.su_kg), stringResource(R.string.su_lbs)),
        selected = if (prefs.weightUnit == "lbs") 1 else 0,
        onSelect = { i ->
            val unit = if (i == 1) "lbs" else "kg"
            Settings.updatePortable { it.copy(weightUnit = unit, weightUnitManual = true) }
        }
    )
    StepText(stringResource(R.string.su_units_body))
    DistanceAndLengthSetting(prefs.distanceUnit, prefs.lengthUnit)
    WeekStartSetting(prefs.weekStart)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupsStep() {
    val ctx = LocalContext.current.applicationContext
    val device by Settings.device.collectAsState()
    val folder = device.autoBackupFolder?.let { Uri.parse(it) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            Backups.setAutoFolder(ctx, uri)
            AutoBackup.schedule(ctx)
            // So FitLens can say if the backup folder ever becomes unreachable, as in Settings → Backup.
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            runBusy(ctx.getString(R.string.su_saving_first)) { Backups.backupToFolder(ctx) }
        }
    }
    StepHeading(stringResource(R.string.su_backups_head))
    StepText(stringResource(R.string.su_backups_body))
    StepStatus(
        folder?.let { f ->
            stringResource(R.string.su_folder, f.lastPathSegment?.substringAfter(':')?.ifBlank { null } ?: stringResource(R.string.su_root))
        } ?: stringResource(R.string.su_no_folder)
    )
    GlassOutlinedButton(onClick = { pickFolder.launch(null) }) { Text(if (folder == null) stringResource(R.string.su_choose_folder) else stringResource(R.string.su_change_folder)) }
    if (folder != null) {
        StepText(stringResource(R.string.su_how_often))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to stringResource(R.string.su_daily), 7 to stringResource(R.string.su_weekly)).forEach { (d, label) ->
                FilterChip(
                    selected = device.autoBackupDays == d,
                    onClick = {
                        Backups.setAutoDays(d)
                        AutoBackup.schedule(ctx)
                    },
                    label = { Text(label) }
                )
            }
        }
        StepText(stringResource(R.string.su_backups_more))
    }
}

@Composable
private fun FitNotesStep(snap: Snapshot) {
    val ctx = LocalContext.current.applicationContext
    val device by Settings.device.collectAsState()
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) when (FitNotesImporter.sniff(ctx, uri)) {
            // The import host shows what the backup adds before anything is imported.
            FileKind.FITNOTES_BACKUP -> FitNotesImports.start(ctx, uri)
            FileKind.BODY_CSV -> runBusy(ctx.getString(R.string.su_importing)) { FitNotesImporter.importBodyCsv(ctx, uri) }
            else -> UiEvents.show(ctx.getString(R.string.su_not_fitnotes))
        }
    }
    StepHeading(stringResource(R.string.su_fitnotes_head))
    StepText(stringResource(R.string.su_fitnotes_body))
    val last = device.lastImportName
    if (last != null) StepStatus(
        stringResource(R.string.su_imported, last, pluralStringResource(R.plurals.su_workouts, snap.setsByDate.size, snap.setsByDate.size), pluralStringResource(R.plurals.sets_count, snap.sets.size, snap.sets.size))
    )
    GoldButton(onClick = { openBackup.launch(arrayOf("*/*")) }) { Text(if (last == null) stringResource(R.string.su_import_backup) else stringResource(R.string.su_import_another)) }
    StepText(stringResource(R.string.su_fitnotes_later))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotosStep(snap: Snapshot) {
    val importPhotos = rememberPhotoImporter()
    val importFolder = rememberFolderPhotoImporter()
    StepHeading(stringResource(R.string.su_photos_head))
    StepText(stringResource(R.string.su_photos_body))
    if (snap.photos.isNotEmpty()) StepStatus(
        stringResource(R.string.su_photos_status, pluralStringResource(R.plurals.su_photos_n, snap.photos.size, snap.photos.size), pluralStringResource(R.plurals.su_days_n, snap.photosByDate.size, snap.photosByDate.size))
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GoldButton(onClick = importPhotos) { Text(stringResource(R.string.su_choose_photos)) }
        GlassOutlinedButton(onClick = importFolder) { Text(stringResource(R.string.su_import_folder)) }
    }
    StepText(stringResource(R.string.su_photos_more))
}

@Composable
private fun ExercisesStep(snap: Snapshot) {
    var starter by remember { mutableStateOf(false) }
    StepHeading(stringResource(R.string.su_ex_head))
    StepText(
        stringResource(R.string.su_ex_body, pluralStringResource(R.plurals.lib_exercises, StarterLibrary.exerciseCount, StarterLibrary.exerciseCount),
            pluralStringResource(R.plurals.su_categories_n, StarterLibrary.categories.size, StarterLibrary.categories.size))
    )
    if (snap.exercises.isNotEmpty()) StepStatus(stringResource(R.string.su_ex_status, pluralStringResource(R.plurals.lib_exercises, snap.exercises.size, snap.exercises.size)))
    GoldButton(onClick = { starter = true }) { Text(stringResource(R.string.su_add_starter)) }
    StepText(stringResource(R.string.su_ex_more))
    if (starter) StarterLibraryDialog(onDismiss = { starter = false })
    // The standard body measurements (#27), for anyone not bringing them from FitNotes.
    val missing = StandardMeasurements.missing(snap.measurementDefs.map { it.name } + snap.recordsByName.keys)
    StepHeading(stringResource(R.string.su_body_head))
    StepText(stringResource(R.string.su_body_body, StandardMeasurements.all.size - 3))
    if (missing.isEmpty()) StepStatus(stringResource(R.string.su_body_ready))
    else GoldButton(onClick = { AppScope.scope.launch { Store.addStandardMeasurements() } }) { Text(stringResource(R.string.su_body_add)) }
}
