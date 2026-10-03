package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.SearchFieldIcon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.DistanceUnits
import com.fitlens.companion.data.WeightUnits
import com.fitlens.companion.data.Effort
import com.fitlens.companion.data.ImportSummary
import com.fitlens.companion.data.LengthUnits
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Records
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.SettingsSwitchRow
import kotlinx.coroutines.delay

/**
 * The pages Settings still opens (#38, #86), where FitNotes opens a page or dialog too (#147): backups, the FitNotes
 * import, the data tools, Home Screen Settings and the rest timer. Every other setting sits on the main list. Each reads
 * and writes only through [Settings], so no screen knows whether a value lives on this phone or travels in backups.
 */
enum class SettingsSection(val title: String) {
    Backups("Backup and restore"),
    // FitNotes imports lived on the Sync tab until #35 moved them here.
    Import("Import from FitNotes"),
    DataTools("Export and delete history"),
    Home("Home screen settings"),
    // The rest timer's options lived only in its sheet until #86 gave them a page.
    Rest("Rest timer"),
    // Defaults for photos, the slideshow and video, and the PDF report (#46).
    Media("Progress photos and media")
}

/** One searchable setting on a page (#86): what it's called, other words people might search for, and the page. */
private data class SettingEntry(val title: String, val section: SettingsSection, val keywords: String = "")

/** Every setting on a page, for search. A row added to a page belongs here too; main-list rows search themselves. */
private val CATALOGUE = listOf(
    SettingEntry("Save, share or restore a backup", SettingsSection.Backups, "fitlens file export"),
    SettingEntry("Automatic backups", SettingsSection.Backups, "schedule daily weekly folder"),
    SettingEntry("Date and time in backup names", SettingsSection.Backups, "timestamp file name"),
    SettingEntry("Keep the newest automatic backups", SettingsSection.Backups, "how many old delete"),
    SettingEntry("Back up after changes", SettingsSection.Backups, "automatic leave hour"),
    SettingEntry("Safety copy", SettingsSection.Backups, "undo restore"),
    SettingEntry("PDF progress report", SettingsSection.Backups, "report print"),
    SettingEntry("Import a FitNotes backup", SettingsSection.Import, "fitnotes merge"),
    SettingEntry("FitNotes backup folder sync", SettingsSection.Import, "auto sync folder"),
    SettingEntry("Sync the FitNotes folder automatically", SettingsSection.Import, "auto sync open start"),
    SettingEntry("Export to CSV", SettingsSection.DataTools, "spreadsheet excel"),
    SettingEntry("Delete workout history", SettingsSection.DataTools, "erase remove clear"),
    SettingEntry("Show categories", SettingsSection.Home, "colour day log home"),
    SettingEntry("Sets shown per exercise", SettingsSection.Home, "day log home cards"),
    SettingEntry("Rest length", SettingsSection.Rest, "seconds break between sets"),
    SettingEntry("Start the rest timer after saving a set", SettingsSection.Rest, "auto start"),
    SettingEntry("Vibrate when rest is over", SettingsSection.Rest, "vibration haptic"),
    SettingEntry("Rest-over sound and volume", SettingsSection.Rest, "ringtone alarm notification"),
    SettingEntry("Pose for new photos", SettingsSection.Media, "front side back import default"),
    SettingEntry("Group photos by", SettingsSection.Media, "day week month year pose gallery"),
    SettingEntry("Remember slideshow and video options", SettingsSection.Media, "video slideshow overlay format"),
    SettingEntry("Reset slideshow and video options", SettingsSection.Media, "video slideshow defaults"),
    SettingEntry("PDF report pages", SettingsSection.Media, "dark light print style"),
    SettingEntry("PDF photos per day", SettingsSection.Media, "report daily log")
)

/** FitNotes's three headings, in its order (#147). */
private enum class SettingsHeading(val title: String) { SETTINGS("Settings"), DATA("Data"), OTHER("Other") }

/** One row of the main list: its heading, words to search by, and the row itself. */
private class MainRow(
    val heading: SettingsHeading,
    val title: String,
    val keywords: String = "",
    val content: @Composable () -> Unit
)

private const val REPO = "https://github.com/Flameingskull/FitLens"

/**
 * The main Settings screen (#147): FitNotes's single list, under its SETTINGS, DATA and OTHER headings, in its order
 * and wording, with on/off settings as checkboxes. FitLens's own settings follow FitNotes's in the matching group.
 * Searching (#86) filters the list to the matching rows, which work in place, and lists matching settings on the
 * pages it opens.
 */
@Composable
fun SettingsScreen(snap: Snapshot, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    val rows = mainRows(snap, nav)
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Settings", onBack = { nav.pop() })
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search settings") },
            leadingIcon = { SearchFieldIcon() },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            fun matches(text: String) = words.all { it in text.lowercase() }
            val shown = if (words.isEmpty()) rows else rows.filter { matches("${it.title} ${it.keywords} ${it.heading.title}") }
            val onPages = if (words.isEmpty()) emptyList() else
                CATALOGUE.filter { matches("${it.title} ${it.keywords} ${it.section.title}") }
            if (words.isNotEmpty() && shown.isEmpty() && onPages.isEmpty()) {
                SettingsNote("No setting matches \"${query.trim()}\".")
            }
            shown.groupBy { it.heading }.forEach { (heading, inGroup) ->
                SettingsGroup(heading.title)
                inGroup.forEach { it.content() }
            }
            if (onPages.isNotEmpty()) {
                SettingsGroup("On other pages")
                onPages.forEach { e ->
                    SettingsActionRow(e.title, "In ${e.section.title}") { nav.push(Screen.SettingsPage(e.section)) }
                }
            }
        }
    }
}

/** The main list's rows, FitNotes's first in each group, then FitLens's own (#147). */
@Composable
private fun mainRows(snap: Snapshot, nav: Nav): List<MainRow> {
    val prefs by Settings.portable.collectAsState()
    val ctx = LocalContext.current
    var confirmRecalc by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var e1rmLimit by remember { mutableStateOf(false) }
    fun open(section: SettingsSection) = nav.push(Screen.SettingsPage(section))
    fun browse(url: String) {
        try {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (e: android.content.ActivityNotFoundException) {
            UiEvents.show("There's no browser on this phone to open $url")
        }
    }
    if (confirmRecalc) RecalculateRecordsSheet { confirmRecalc = false }
    if (about) AboutSheet { about = false }
    if (e1rmLimit) EstimatedOneRmSettingsSheet { e1rmLimit = false }
    val s = SettingsHeading.SETTINGS
    val d = SettingsHeading.DATA
    val o = SettingsHeading.OTHER
    return listOf(
        MainRow(s, "Theme", "dark colour colours look black gold") {
            SettingsActionRow("Theme", value = "Black and gold") {
                UiEvents.show("FitLens has one look: luxury black and gold.")
            }
        },
        MainRow(s, "Unit System", "metric imperial kg lbs km miles cm inches") { UnitSystemRow(prefs) },
        MainRow(s, "Weight Unit", "kg kilograms lbs pounds") {
            SettingsChoiceRow(
                "Weight Unit",
                listOf("Kilograms (kg)", "Pounds (lbs)"),
                if (prefs.weightUnit == "lbs") 1 else 0,
                summary = "Weights are stored exactly, so switching only changes how they're shown and entered."
            ) { i ->
                // Choosing by hand is remembered, so a later FitNotes import doesn't switch it back.
                Settings.updatePortable { it.copy(weightUnit = if (i == 1) "lbs" else "kg", weightUnitManual = true) }
            }
        },
        MainRow(s, "Distance Unit", "km kilometres miles metres cardio running") {
            SettingsChoiceRow(
                "Distance Unit",
                DistanceUnits.ALL.map { "${DistanceUnits.label(it)} ($it)" },
                DistanceUnits.ALL.indexOf(prefs.distanceUnit).coerceAtLeast(0),
                summary = "An exercise can have its own. Logged distances aren't converted."
            ) { i -> Settings.updatePortable { it.copy(distanceUnit = DistanceUnits.ALL[i]) } }
        },
        MainRow(s, "Length Unit", "cm centimetres inches body measurements waist") {
            SettingsChoiceRow(
                "Length Unit",
                listOf("Centimetres (cm)", "Inches (in)"),
                if (prefs.lengthUnit == LengthUnits.IN) 1 else 0,
                summary = "How body measurements are shown and entered. Your logged values aren't changed."
            ) { i -> Settings.updatePortable { it.copy(lengthUnit = if (i == 1) LengthUnits.IN else LengthUnits.CM) } }
        },
        MainRow(s, "Sex (Body Fat)", "profile sex male female body fat navy formula calculate") {
            SettingsChoiceRow(
                "Sex (Body Fat)",
                com.fitlens.companion.data.BodyFat.Sex.entries.map { it.label },
                com.fitlens.companion.data.BodyFat.Sex.of(prefs.profileSex)?.ordinal ?: -1,
                summary = "Used only to calculate body fat from your measurements (US Navy formula)."
            ) { i -> Settings.updatePortable { it.copy(profileSex = com.fitlens.companion.data.BodyFat.Sex.entries[i].key) } }
        },
        MainRow(s, "Calendar Week Start", "monday sunday saturday week starts") {
            val days = listOf(1 to "Monday", 6 to "Saturday", 7 to "Sunday")
            SettingsChoiceRow(
                "Calendar Week Start",
                days.map { it.second },
                days.indexOfFirst { it.first == prefs.weekStart }.coerceAtLeast(0)
            ) { i -> Settings.updatePortable { it.copy(weekStart = days[i].first) } }
        },
        MainRow(s, "Default Weight Increment", "weight step plus minus stepper") {
            WeightStepRow(prefs.weightUnit, prefs.weightIncrementKg)
        },
        MainRow(s, "Home Screen Settings", "day log categories sets shown cards") {
            SettingsActionRow("Home Screen Settings", "Configure your preferred behaviour and appearance of the home screen") {
                open(SettingsSection.Home)
            }
        },
        MainRow(s, "Track Personal Records", "pr celebrate vibration notify") {
            SettingsSwitchRow(
                "Track Personal Records", prefs.celebratePrs,
                summary = "Notify new PRs as you save them. Record sets are always marked in your training history."
            ) { on -> Settings.updatePortable { it.copy(celebratePrs = on) } }
        },
        MainRow(s, "Mark Sets Complete", "done checkbox tick progress") {
            SettingsSwitchRow(
                "Mark Sets Complete", prefs.markComplete,
                summary = "Display a checkbox next to each set to indicate that it is complete"
            ) { on -> Settings.updatePortable { it.copy(markComplete = on) } }
        },
        MainRow(s, "Auto-Select Next Set", "auto select update") {
            SettingsSwitchRow(
                "Auto-Select Next Set", prefs.autoSelectNext,
                summary = "Automatically highlight the next set to be updated when using a workout or copying a workout"
            ) { on -> Settings.updatePortable { it.copy(autoSelectNext = on) } }
        },
        MainRow(s, "Keep Screen On", "sleep awake display") {
            SettingsSwitchRow(
                "Keep Screen On", prefs.keepScreenOn,
                summary = "Prevent the device from sleeping while the training screen is open"
            ) { on -> Settings.updatePortable { it.copy(keepScreenOn = on) } }
        },
        // FitLens's own settings follow FitNotes's.
        MainRow(s, "Fill New Sets From", "autofill last workout empty") {
            SettingsChoiceRow(
                "Fill New Sets From",
                listOf("Last workout", "Leave empty"),
                if (prefs.autofillSource == PortableSettings.AUTOFILL_EMPTY) 1 else 0,
                descriptions = listOf(
                    "Your latest set today, or the first set of the last time you did the exercise.",
                    "Every new set starts blank."
                )
            ) { i ->
                val source = if (i == 1) PortableSettings.AUTOFILL_EMPTY else PortableSettings.AUTOFILL_LAST
                Settings.updatePortable { it.copy(autofillSource = source) }
            }
        },
        MainRow(s, "Effort Per Set", "rpe rir reps in reserve") {
            SettingsChoiceRow(
                "Effort Per Set",
                listOf("Off", "RPE", "RIR"),
                when (prefs.effortMode) { Effort.RPE -> 1; Effort.RIR -> 2; else -> 0 },
                summary = "Effort is stored once, so switching between RPE and RIR, or turning it off, never changes it.",
                descriptions = listOf(
                    "No effort field.",
                    "Rate of perceived exertion: 10 means no reps left.",
                    "Reps in reserve: how many more reps you had left."
                )
            ) { i ->
                val mode = when (i) { 1 -> Effort.RPE; 2 -> Effort.RIR; else -> Effort.OFF }
                Settings.updatePortable { it.copy(effortMode = mode) }
            }
        },
        MainRow(s, "Show Set Types", "warm-up drop failure badge") {
            SettingsSwitchRow(
                "Show Set Types", prefs.showSetType,
                summary = "Warm-up, drop and failure sets carry a small W, D or F. Choose a set's type as you log it."
            ) { on -> Settings.updatePortable { it.copy(showSetType = on) } }
        },
        MainRow(s, "Count Warm-up Sets", "warmup records stats") {
            SettingsSwitchRow(
                "Count Warm-up Sets", prefs.warmupsCount,
                summary = "Include warm-ups in records, estimated maxes, volume, graphs, analysis and the PDF report. " +
                    "They always show in your history."
            ) { on ->
                Settings.updatePortable { it.copy(warmupsCount = on) }
                // PR marks follow the setting straight away (#43).
                runBusy("Updating personal records…") {
                    val n = Workouts.recalculatePrs()
                    if (n == 0) null else ImportSummary("Personal records updated: $n ${if (n == 1) "set" else "sets"} changed.", ok = true)
                }
            }
        },
        MainRow(s, "Start Workout Timer Automatically", "duration clock first set") {
            SettingsSwitchRow(
                "Start Workout Timer Automatically", prefs.workoutTimerAuto,
                summary = "Start timing today's workout when you save its first set"
            ) { on -> Settings.updatePortable { it.copy(workoutTimerAuto = on) } }
        },
        MainRow(s, "Rest Timer", "rest length vibrate sound auto start") {
            SettingsActionRow(
                "Rest Timer",
                "Rest length, starting after a set, vibration and sound",
                value = fmtDuration(prefs.restSeconds)
            ) { open(SettingsSection.Rest) }
        },
        MainRow(s, "Estimated 1RM Formula", "epley brzycki mayhew wathan one rep max") {
            FormulaChoice(snap, Records.Formula.of(prefs.e1rmFormula))
        },
        MainRow(s, "Estimated 1RM Settings", "max reps limit one rep max accuracy") {
            val limit = Records.maxRepsFor(Records.Formula.of(prefs.e1rmFormula))
            SettingsActionRow(
                "Estimated 1RM Settings",
                "The most reps a set can have to be included in the 1-rep-max calculation",
                value = "Up to $limit reps"
            ) { e1rmLimit = true }
        },
        // FitLens's own: photos, the slideshow and video, and the PDF report (#46).
        MainRow(s, "Progress Photos & Media", "photos pose slideshow video pdf report group") {
            SettingsActionRow(
                "Progress Photos & Media",
                "The pose for new photos, how the gallery is grouped, and the slideshow, video and PDF defaults"
            ) { open(SettingsSection.Media) }
        },
        MainRow(d, "Backup", "save share fitlens file") {
            SettingsActionRow("Backup", "Back up your data to a file, then keep it off your phone or share it with an app you choose") {
                open(SettingsSection.Backups)
            }
        },
        MainRow(d, "Restore", "restore backup file") {
            SettingsActionRow("Restore", "Restore your data from a previously created backup") { open(SettingsSection.Backups) }
        },
        MainRow(d, "Automatic Backup", "schedule daily weekly folder") {
            SettingsActionRow("Automatic Backup", "Automatically back up your data to a folder you choose") {
                open(SettingsSection.Backups)
            }
        },
        MainRow(d, "Spreadsheet Export", "csv excel export") {
            SettingsActionRow(
                "Spreadsheet Export",
                "Export your training logs as a .csv file to view in your preferred spreadsheet application"
            ) { open(SettingsSection.DataTools) }
        },
        MainRow(d, "Calculate Personal Records", "recalculate pr marks rebuild") {
            SettingsActionRow(
                "Calculate Personal Records",
                "Re-calculate your personal records if you think they might be incorrect",
                enabled = snap.sets.isNotEmpty()
            ) { confirmRecalc = true }
        },
        MainRow(d, "Delete Workout History", "erase remove clear") {
            SettingsActionRow("Delete Workout History", "Delete selected workouts while keeping the rest of your data intact") {
                open(SettingsSection.DataTools)
            }
        },
        MainRow(d, "Import From FitNotes", "fitnotes backup merge sync folder") {
            SettingsActionRow("Import From FitNotes", "Merge a FitNotes backup into FitLens, or sync its backup folder") {
                open(SettingsSection.Import)
            }
        },
        MainRow(o, "Help", "guide instructions readme") {
            SettingsActionRow("Help", "View the FitLens guide on GitHub, with instructions for each feature") { browse("$REPO#readme") }
        },
        MainRow(o, "Feedback", "bug report feature request issue") {
            SettingsActionRow("Feedback", "Report a problem or suggest a feature on GitHub") { browse("$REPO/issues/new/choose") }
        },
        MainRow(o, "Change Log", "releases updates what's new") {
            SettingsActionRow("Change Log", "View a list of the changes included in previous updates") { browse("$REPO/releases") }
        },
        MainRow(o, "Show Setup Again", "tutorials first run welcome") {
            SettingsActionRow("Show Setup Again", "Units, automatic backups, FitNotes import, photos and the starter library") {
                nav.push(Screen.Setup)
            }
        },
        MainRow(o, "Privacy Policy", "privacy data local offline") {
            SettingsActionRow("Privacy Policy", "FitLens keeps everything on this phone: no account, cloud or internet") { about = true }
        },
        MainRow(o, "About", "version privacy local") {
            SettingsActionRow("About", "Version and privacy information") { about = true }
        }
    )
}

/** FitNotes's Unit System (#147): metric or imperial in one go. A mix chosen below shows as Custom. */
@Composable
private fun UnitSystemRow(prefs: PortableSettings) {
    val metric = prefs.weightUnit == "kg" && prefs.distanceUnit == DistanceUnits.KM && prefs.lengthUnit == LengthUnits.CM
    val imperial = prefs.weightUnit == "lbs" && prefs.distanceUnit == DistanceUnits.MI && prefs.lengthUnit == LengthUnits.IN
    SettingsChoiceRow(
        "Unit System",
        listOf("Metric", "Imperial"),
        when { metric -> 0; imperial -> 1; else -> -1 },
        summary = if (metric || imperial) null else
            "Custom: ${prefs.weightUnit}, ${prefs.distanceUnit} and ${prefs.lengthUnit}, set below",
        descriptions = listOf("Kilograms, kilometres and centimetres", "Pounds, miles and inches")
    ) { i ->
        Settings.updatePortable {
            if (i == 1) it.copy(weightUnit = "lbs", weightUnitManual = true, distanceUnit = DistanceUnits.MI, lengthUnit = LengthUnits.IN)
            else it.copy(weightUnit = "kg", weightUnitManual = true, distanceUnit = DistanceUnits.KM, lengthUnit = LengthUnits.CM)
        }
    }
}

/** Data → Calculate Personal Records: confirms, then rebuilds every PR mark. */
@Composable
private fun RecalculateRecordsSheet(onDismiss: () -> Unit) {
    ConfirmSheet(
        title = "Calculate personal records?",
        message = "Every weight-and-reps set gets a PR mark only if it beat all earlier sets of at least as " +
            "many reps. PR marks that came from FitNotes are replaced. Timed and cardio sets keep theirs.",
        confirmLabel = "Calculate",
        onDismiss = onDismiss,
        onConfirm = {
            runBusy("Calculating records…") {
                val n = Workouts.recalculatePrs()
                ImportSummary(
                    if (n == 0) "Personal records checked. Nothing needed changing."
                    else "Personal records recalculated. $n ${if (n == 1) "set" else "sets"} updated.",
                    ok = true
                )
            }
        },
        destructive = false
    )
}

/** Other → About: the version and how FitLens treats your data. */
@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { null } ?: "?"
    }
    FitSheet(title = "About FitLens", onDismiss = onDismiss, dismissLabel = "Close") {
        Text("Version $version", style = MaterialTheme.typography.titleMedium, color = Brand.Gold)
        Text(
            "FitLens is a workout log that pairs your training with progress photos. It works entirely on this phone: " +
                "no account, no cloud service and no internet permission. Your data leaves the phone only in backups " +
                "you save or share yourself.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Workout logging follows FitNotes, and FitNotes backups can be imported at any time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One Settings sub-screen. Back returns to Settings, then to wherever Settings was opened from. */
@Composable
fun SettingsPageScreen(snap: Snapshot, nav: Nav, section: SettingsSection) {
    Column(Modifier.fillMaxSize()) {
        BackTopBar(section.title, onBack = { nav.pop() })
        when (section) {
            SettingsSection.Import -> FitNotesImportHost()
            else -> {}
        }
        // Every page is built from the settings rows and runs edge to edge, like the main list (#86).
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            when (section) {
                SettingsSection.Backups -> BackupsPage(snap)
                SettingsSection.Import -> FitNotesImportPage(snap)
                SettingsSection.DataTools -> DataToolsPage(snap)
                SettingsSection.Home -> HomePage()
                SettingsSection.Rest -> RestPage()
                SettingsSection.Media -> MediaPage()
            }
        }
    }
}

/** Home Screen Settings (#147): how the day log shows each exercise (#8). */
@Composable
private fun HomePage() {
    val prefs by Settings.portable.collectAsState()
    SettingsGroup("Day log")
    SettingsSwitchRow("Show categories", prefs.homeShowCategories, summary = "Each exercise card shows its category colour.") { on ->
        Settings.updatePortable { it.copy(homeShowCategories = on) }
    }
    val counts = listOf(0) + (1..10)
    SettingsChoiceRow(
        "Sets shown per exercise",
        counts.map { if (it == 0) "All" else "$it" },
        counts.indexOf(prefs.homeSetsShown).coerceAtLeast(0),
        summary = "A card with more sets ends with \"+N more sets\"; tap it to see them all."
    ) { i -> Settings.updatePortable { it.copy(homeSetsShown = counts[i]) } }
    SettingsNote("These preferences travel with your .fitlens backups.")
}

/**
 * Settings → Progress photos and media (#46): the defaults FitLens's own photo features start from. All of them
 * travel in backups, and each is validated when read, so a newer build's backup can't break these screens.
 */
@Composable
private fun MediaPage() {
    val prefs by Settings.portable.collectAsState()
    SettingsGroup("Photos")
    // Ask each time, then None, then the poses: null, Poses.NONE and the pose names as stored.
    val poses: List<String?> = listOf(null, com.fitlens.companion.data.Poses.NONE) + com.fitlens.companion.data.Poses.all
    SettingsChoiceRow(
        "Pose for new photos",
        listOf("Ask each time", "None") + com.fitlens.companion.data.Poses.all,
        poses.indexOf(prefs.photoDefaultPose).coerceAtLeast(0),
        summary = "Used for every photo you import, one or many. Ask each time shows the pose question before an import."
    ) { i -> Settings.updatePortable { it.copy(photoDefaultPose = poses[i]) } }
    val groups = com.fitlens.companion.data.MediaPrefs.GROUPS
    SettingsChoiceRow(
        "Group photos by",
        groups.map { com.fitlens.companion.data.MediaPrefs.groupLabel(it) },
        groups.indexOf(prefs.photoGroupBy).coerceAtLeast(0),
        summary = "How the Photos screen sections your photos. Changing it on the Photos screen changes it here too."
    ) { i -> Settings.updatePortable { it.copy(photoGroupBy = groups[i]) } }

    SettingsGroup("Slideshow and video")
    SettingsSwitchRow(
        "Remember slideshow and video options", prefs.rememberVideoOpts,
        summary = "Open the slideshow with the pose, timing, overlays, title and video size you used last. The dates always start at all photos."
    ) { on -> Settings.updatePortable { it.copy(rememberVideoOpts = on) } }
    SettingsActionRow(
        "Reset slideshow and video options",
        if (prefs.videoOpts == null) "Already at the defaults" else "Go back to the default options next time",
        enabled = prefs.videoOpts != null
    ) {
        Settings.updatePortable { it.copy(videoOpts = null) }
        UiEvents.show("Slideshow and video options reset")
    }

    SettingsGroup("PDF report")
    SettingsChoiceRow(
        "PDF report pages",
        listOf("Dark (matches the app)", "Light (better for printing)"),
        if (prefs.pdfDark) 0 else 1
    ) { i -> Settings.updatePortable { it.copy(pdfDark = i == 0) } }
    SettingsChoiceRow(
        "PDF photos per day",
        (0..4).map { if (it == 0) "None" else "$it" },
        prefs.pdfPhotosPerDay.coerceIn(0, 4),
        summary = "How many photos each day of the report's daily log shows. You can still change it for one report."
    ) { i -> Settings.updatePortable { it.copy(pdfPhotosPerDay = i) } }
    SettingsNote("These preferences travel with your .fitlens backups.")
}

/** The + and − step for weights (#7). Stored in kg; the choices follow the display unit. */
@Composable
private fun WeightStepRow(unit: String, currentKg: Double?) {
    val lbs = unit == "lbs"
    val choices = if (lbs) listOf(1.0, 2.5, 5.0, 10.0) else listOf(0.5, 1.0, 1.25, 2.5, 5.0)
    fun toKg(v: Double) = if (lbs) v * WeightUnits.KG_PER_LB else v
    val selected = if (currentKg == null) 0 else
        choices.indexOfFirst { kotlin.math.abs(toKg(it) - currentKg) < 0.001 }.let { if (it < 0) 0 else it + 1 }
    SettingsChoiceRow(
        "Default Weight Increment",
        listOf("Default (2.5)") + choices.map { "${fmtNum(it, 2)} $unit" },
        selected,
        summary = "How much the + and − buttons change the weight when you log a set. An exercise can have its own."
    ) { i ->
        val kg = if (i == 0) null else toKg(choices[i - 1])
        Settings.updatePortable { it.copy(weightIncrementKg = kg) }
    }
}

/** The distance and length units (#7), as first-run setup asks them. */
@Composable
internal fun DistanceAndLengthSetting(distanceUnit: String, lengthUnit: String) {
    SectionTitle("Distances")
    com.fitlens.companion.ui.design.SegmentedSwitch(
        options = DistanceUnits.ALL.map { DistanceUnits.label(it) },
        selected = DistanceUnits.ALL.indexOf(distanceUnit).coerceAtLeast(0),
        onSelect = { i -> Settings.updatePortable { it.copy(distanceUnit = DistanceUnits.ALL[i]) } }
    )
    SectionTitle("Body measurements")
    com.fitlens.companion.ui.design.SegmentedSwitch(
        options = listOf("Centimetres", "Inches"),
        selected = if (lengthUnit == LengthUnits.IN) 1 else 0,
        onSelect = { i -> Settings.updatePortable { it.copy(lengthUnit = if (i == 1) LengthUnits.IN else LengthUnits.CM) } }
    )
}

/** The first day of the week for the calendar and weekly analysis (#7), as first-run setup asks it. */
@Composable
internal fun WeekStartSetting(weekStart: Int) {
    val days = listOf(1 to "Monday", 6 to "Saturday", 7 to "Sunday")
    SectionTitle("Week starts on")
    com.fitlens.companion.ui.design.SegmentedSwitch(
        options = days.map { it.second },
        selected = days.indexOfFirst { it.first == weekStart }.coerceAtLeast(0),
        onSelect = { i -> Settings.updatePortable { it.copy(weekStart = days[i].first) } }
    )
}

/**
 * Settings → Rest timer (#86): the default rest length and what happens when a rest ends. The same options as the
 * rest timer sheet, which shares [RestAlertOptions] with this page.
 */
@Composable
private fun RestPage() {
    val prefs by Settings.portable.collectAsState()
    // Saved a moment after the last change, as in the sheet (#105), so holding + doesn't write every step.
    var chosen by remember(prefs.restSeconds) { mutableIntStateOf(prefs.restSeconds) }
    val saved by rememberUpdatedState(prefs.restSeconds)
    val latest by rememberUpdatedState(chosen)
    LaunchedEffect(chosen) {
        if (chosen == prefs.restSeconds) return@LaunchedEffect
        delay(600)
        Settings.updatePortable { it.copy(restSeconds = chosen) }
    }
    DisposableEffect(Unit) {
        onDispose { if (latest != saved) Settings.updatePortable { it.copy(restSeconds = latest) } }
    }
    SettingsGroup("Rest length")
    Column(Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        RestLengthStepper(seconds = chosen, onChange = { chosen = it })
    }
    SettingsNote(
        "Every exercise rests ${fmtDuration(chosen)} unless it has its own length, set from its rest timer or its " +
            "details in the library."
    )
    SettingsGroup("When rest is over")
    Column {
        RestAlertOptions()
    }
    SettingsNote("The timer keeps running as you move between exercises, and with the screen off.")
}

/**
 * Settings → Estimated 1RM Formula (#42): each formula with a worked example from the user's best recent
 * set, so the difference shows before choosing. Stored sets never change; estimates follow at once.
 */
@Composable
private fun FormulaChoice(snap: Snapshot, chosen: Records.Formula) {
    // The best recent set to show the formulas on: the last 90 days, 2 to 10 reps, highest automatic estimate.
    val example = remember(snap) {
        val from = java.time.LocalDate.now().minusDays(90).format(Dates.ISO)
        val recent = snap.statSets.filter { it.weightKg > 0 && it.reps in 2..10 }
        (recent.filter { it.date.take(10) >= from }.ifEmpty { recent })
            .maxByOrNull { Records.oneRepMax(it.weightKg, it.reps, Records.Formula.AUTO) }
    }
    val formulas = Records.Formula.entries
    SettingsChoiceRow(
        "Estimated 1RM Formula",
        formulas.map { it.label },
        formulas.indexOf(chosen).coerceAtLeast(0),
        summary = "Used for estimated 1RM, rep maxes, graphs, records, goals, the calculators and the PDF report. " +
            "Automatic picks the most accurate formula for each rep range. Your logged sets never change.",
        descriptions = formulas.map { f ->
            if (example == null) "Up to ${f.maxReps} reps" else {
                val est = Records.oneRepMax(example.weightKg, example.reps, f)
                "${snap.fmtWeight(example.weightKg)} ${snap.weightUnit} × ${example.reps} → " +
                    (if (est > 0) "${snap.fmtWeight(est)} ${snap.weightUnit}" else "not estimated") + "  ·  up to ${f.maxReps} reps"
            }
        }
    ) { i -> Settings.updatePortable { it.copy(e1rmFormula = formulas[i].key) } }
}
