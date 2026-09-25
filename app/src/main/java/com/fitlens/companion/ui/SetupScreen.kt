package com.fitlens.companion.ui

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
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.SegmentedSwitch

/**
 * The guided setup (#29): shown once on a fresh install, skippable at every step, and run again from Settings.
 * Each step only calls what the matching Settings page or tab already uses, so setup never has its own copy of a
 * feature: units ([Settings]), automatic backups ([Backups], [AutoBackup]), the FitNotes merge import
 * ([FitNotesImports]), photo import and the starter library.
 *
 * Choosing to restore a FitLens backup ends setup and hands the file to Settings → Backups, which checks it and asks
 * before replacing anything, because the backup brings its own preferences with it.
 */
private enum class SetupStep(val title: String) {
    Welcome("Welcome"),
    Units("Units"),
    Backups("Automatic backups"),
    FitNotes("FitNotes"),
    Photos("Progress photos"),
    Exercises("Exercises")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(snap: Snapshot, nav: Nav) {
    var stepIdx by rememberSaveable { mutableIntStateOf(0) }
    val step = SetupStep.entries[stepIdx]
    val last = stepIdx == SetupStep.entries.lastIndex

    fun finish() {
        Settings.updateDevice { it.copy(setupDone = true) }
        nav.tab(Screen.Timeline)
    }

    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            Settings.updateDevice { it.copy(setupDone = true) }
            // Settings → Backups inspects the file and asks before restoring (BackupsCard).
            nav.tab(Screen.Timeline)
            nav.push(Screen.SettingsHome)
            nav.push(Screen.SettingsPage(SettingsSection.Backups))
            UiEvents.pendingRestore.value = uri
        }
    }

    BackHandler { if (stepIdx > 0) stepIdx-- else finish() }
    FitNotesImportHost()

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = "Set up FitLens",
            subtitle = "Step ${stepIdx + 1} of ${SetupStep.entries.size} · ${step.title}",
            centered = false,
            trailing = { TextButton(onClick = { finish() }) { Text("Skip setup") } }
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
            if (stepIdx > 0) TextButton(onClick = { stepIdx-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (last) finish() else stepIdx++ }) {
                Text(if (last) "Finish" else if (step == SetupStep.Welcome) "Set up FitLens" else "Next")
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
    StepHeading("Your training, photos and measurements, together")
    StepText(
        "FitLens logs your workouts and keeps them next to your progress photos and body measurements. " +
            "Everything stays on this phone: there's no account, and FitLens never goes online."
    )
    StepText(
        "A few quick steps set up your units, automatic backups and imports. Every step can be skipped, and you can " +
            "run setup again any time from Settings."
    )
    SectionTitle("Moving from another phone?")
    StepText("Restore a FitLens backup (.fitlens) to bring everything back. This replaces setup, so it ends here.")
    OutlinedButton(onClick = onRestore) { Text("Restore a FitLens backup") }
}

@Composable
private fun UnitsStep() {
    val prefs by Settings.portable.collectAsState()
    StepHeading("How do you weigh your lifts?")
    SegmentedSwitch(
        options = listOf("Kilograms (kg)", "Pounds (lbs)"),
        selected = if (prefs.weightUnit == "lbs") 1 else 0,
        onSelect = { i ->
            val unit = if (i == 1) "lbs" else "kg"
            Settings.updatePortable { it.copy(weightUnit = unit, weightUnitManual = true) }
        }
    )
    StepText("Weights are stored exactly, so you can switch at any time in Settings → Units & display.")
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
            // So FitLens can say if the backup folder ever becomes unreachable, as in Settings → Backups.
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            runBusy("Saving the first automatic backup…") { Backups.backupToFolder(ctx) }
        }
    }
    StepHeading("Keep a copy of everything, automatically")
    StepText(
        "Strongly recommended. Choose a folder outside FitLens, such as Documents or an SD card, and FitLens saves a " +
            "full backup there in the background. If the phone is lost or FitLens is uninstalled, that backup brings " +
            "everything back."
    )
    StepStatus(
        folder?.let { "Folder: " + (it.lastPathSegment?.substringAfter(':')?.ifBlank { "(root)" } ?: it.toString()) }
            ?: "No folder chosen yet."
    )
    OutlinedButton(onClick = { pickFolder.launch(null) }) { Text(if (folder == null) "Choose backup folder" else "Change folder") }
    if (folder != null) {
        StepText("How often")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "Daily", 7 to "Weekly").forEach { (d, label) ->
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
        StepText("How many backups to keep, and backing up after changes, are in Settings → Backups.")
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
            FileKind.BODY_CSV -> runBusy("Importing…") { FitNotesImporter.importBodyCsv(ctx, uri) }
            else -> UiEvents.show("That isn't a FitNotes backup (.fitnotes) or Body Tracker CSV.")
        }
    }
    StepHeading("Coming from FitNotes?")
    StepText(
        "Import a FitNotes backup to bring over your workouts, exercises and body measurements. Make one with " +
            "FitNotes' own backup option, or share it from FitNotes straight to FitLens. You'll see what will be added first, and importing never changes " +
            "FitNotes itself."
    )
    val last = device.lastImportName
    if (last != null) StepStatus("Imported: $last · ${snap.setsByDate.size} workouts, ${snap.sets.size} sets.")
    Button(onClick = { openBackup.launch(arrayOf("*/*")) }) { Text(if (last == null) "Import a FitNotes backup" else "Import another backup") }
    StepText(
        "Later imports merge in the same way, from Settings → FitNotes import. Syncing a FitNotes backup folder " +
            "automatically is also there, and is off unless you turn it on."
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotosStep(snap: Snapshot) {
    val importPhotos = rememberPhotoImporter()
    val importFolder = rememberFolderPhotoImporter()
    StepHeading("Add your progress photos")
    StepText(
        "FitLens dates each photo from its camera details and places it on that day, next to the workout and " +
            "measurements. You'll be asked which pose the photos show."
    )
    if (snap.photos.isNotEmpty()) StepStatus("${snap.photos.size} photos on ${snap.photosByDate.size} days so far.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = importPhotos) { Text("Choose photos") }
        OutlinedButton(onClick = importFolder) { Text("Import a folder") }
    }
    StepText("You can add more at any time with the + on the Photos tab.")
}

@Composable
private fun ExercisesStep(snap: Snapshot) {
    var starter by remember { mutableStateOf(false) }
    StepHeading("Your exercise library")
    StepText(
        "Start with ${StarterLibrary.exerciseCount} common exercises in ${StarterLibrary.categories.size} categories, " +
            "or build your own as you go. Anything already in your library is kept as it is."
    )
    if (snap.exercises.isNotEmpty()) StepStatus("${snap.exercises.size} exercises in your library.")
    Button(onClick = { starter = true }) { Text("Add the starter library") }
    StepText("You can add, rename or delete exercises any time from the library on the Log tab.")
    if (starter) StarterLibraryDialog(onDismiss = { starter = false })
}
