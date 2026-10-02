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
import androidx.compose.ui.Modifier
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
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.SettingsSwitchRow
import kotlinx.coroutines.delay

/**
 * The Settings sub-screens (#38, styled per #86). Each reads and writes only through [Settings], so no screen knows
 * whether a value lives on this phone or travels in backups. Rows appear only for features that exist today; later
 * builds add theirs.
 */
enum class SettingsSection(val title: String, val summary: String, val group: String) {
    Backups("Backups", "Backup files, automatic backups, safety copy and PDF reports", "Data, backup & import"),
    // FitNotes imports lived on the Sync tab until #35 moved them here.
    Import("FitNotes import", "Import a FitNotes backup any time, or sync its backup folder", "Data, backup & import"),
    DataTools("Data tools", "Export to CSV, delete workout history", "Data, backup & import"),
    Units("Units & display", "Weight, distance and length units, weight step, week start and the day log", "Training"),
    Logging("Workout & logging", "Screen on, filling in new sets, effort, set types and the workout timer", "Training"),
    // The rest timer's options lived only in its sheet until #86 gave them a page.
    Rest("Rest timer", "Rest length, starting after a set, vibration and sound", "Training"),
    Records("Personal records", "PR marks, celebrations and the estimated 1RM formula", "Training")
}

/** One searchable setting (#86): what it's called, other words people might search for, and where it lives. */
private data class SettingEntry(val title: String, val section: SettingsSection, val keywords: String = "")

/** Every setting, for search. A row added to a page belongs here too. */
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
    SettingEntry("Weight unit", SettingsSection.Units, "kg kilograms lbs pounds"),
    SettingEntry("Weight step", SettingsSection.Units, "increment plus minus stepper"),
    SettingEntry("Distance unit", SettingsSection.Units, "km kilometres miles metres cardio running"),
    SettingEntry("Length unit", SettingsSection.Units, "cm centimetres inches body measurements waist"),
    SettingEntry("Week starts on", SettingsSection.Units, "monday sunday saturday calendar"),
    SettingEntry("Show categories on the day log", SettingsSection.Units, "colour home"),
    SettingEntry("Sets shown per exercise", SettingsSection.Units, "day log home cards"),
    SettingEntry("Keep the screen on", SettingsSection.Logging, "sleep awake display"),
    SettingEntry("Fill new sets from", SettingsSection.Logging, "autofill last workout empty"),
    SettingEntry("Select the next set after updating one", SettingsSection.Logging, "auto select"),
    SettingEntry("Effort per set", SettingsSection.Logging, "rpe rir reps in reserve"),
    SettingEntry("Show set type on each set", SettingsSection.Logging, "warm-up drop failure badge"),
    SettingEntry("Count warm-up sets in records and stats", SettingsSection.Logging, "warmup"),
    SettingEntry("Show sets done", SettingsSection.Logging, "mark complete done checkbox tick progress"),
    SettingEntry("Start the workout timer with the first set", SettingsSection.Logging, "duration clock"),
    SettingEntry("Rest length", SettingsSection.Rest, "seconds break between sets"),
    SettingEntry("Start the rest timer after saving a set", SettingsSection.Rest, "auto start"),
    SettingEntry("Vibrate when rest is over", SettingsSection.Rest, "vibration haptic"),
    SettingEntry("Rest-over sound and volume", SettingsSection.Rest, "ringtone alarm notification"),
    SettingEntry("Celebrate new personal records", SettingsSection.Records, "pr vibration"),
    SettingEntry("Estimated 1RM formula", SettingsSection.Records, "epley brzycki one rep max"),
    SettingEntry("Recalculate personal records", SettingsSection.Records, "pr marks rebuild")
)

/**
 * The main Settings screen: a search field, then one row per sub-screen, grouped under gold-ruled headings (#86).
 * Searching lists every matching setting with the page it's on; tapping one opens that page.
 */
@Composable
fun SettingsScreen(nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
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
            if (q.isNotEmpty()) {
                val words = q.split(Regex("\\s+"))
                val found = CATALOGUE.filter { e ->
                    val text = "${e.title} ${e.keywords} ${e.section.title}".lowercase()
                    words.all { it in text }
                }
                if (found.isEmpty()) {
                    SettingsNote("No setting matches \"${query.trim()}\".")
                } else {
                    SettingsGroup("${found.size} ${if (found.size == 1) "setting" else "settings"}")
                    found.forEach { e ->
                        SettingsActionRow(e.title, "In ${e.section.title}") { nav.push(Screen.SettingsPage(e.section)) }
                    }
                }
                return@Column
            }
            SettingsSection.entries.groupBy { it.group }.forEach { (group, sections) ->
                SettingsGroup(group)
                sections.forEach { s ->
                    SettingsActionRow(s.title, s.summary) { nav.push(Screen.SettingsPage(s)) }
                }
            }
            SettingsGroup("Setup")
            SettingsActionRow(
                "Run setup again",
                "Units, automatic backups, FitNotes import, photos and the starter library"
            ) { nav.push(Screen.Setup) }
        }
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
                SettingsSection.Units -> UnitsPage()
                SettingsSection.Logging -> LoggingPage()
                SettingsSection.Rest -> RestPage()
                SettingsSection.Records -> RecordsPage(snap)
            }
        }
    }
}

@Composable
private fun UnitsPage() {
    val prefs by Settings.portable.collectAsState()
    SettingsGroup("Weight")
    SettingsChoiceRow(
        "Weight unit",
        listOf("Kilograms (kg)", "Pounds (lbs)"),
        if (prefs.weightUnit == "lbs") 1 else 0,
        summary = "Weights are stored exactly, so switching only changes how they're shown and entered."
    ) { i ->
        val unit = if (i == 1) "lbs" else "kg"
        // Choosing by hand is remembered, so a later FitNotes import doesn't switch it back.
        Settings.updatePortable { it.copy(weightUnit = unit, weightUnitManual = true) }
    }
    WeightStepRow(prefs.weightUnit, prefs.weightIncrementKg)
    // Distance and length units (#7).
    SettingsGroup("Distance and length")
    SettingsChoiceRow(
        "Distance unit",
        DistanceUnits.ALL.map { "${DistanceUnits.label(it)} ($it)" },
        DistanceUnits.ALL.indexOf(prefs.distanceUnit).coerceAtLeast(0),
        summary = "The unit distances are logged in. An exercise can have its own. Logged distances aren't converted."
    ) { i -> Settings.updatePortable { it.copy(distanceUnit = DistanceUnits.ALL[i]) } }
    SettingsChoiceRow(
        "Length unit",
        listOf("Centimetres (cm)", "Inches (in)"),
        if (prefs.lengthUnit == LengthUnits.IN) 1 else 0,
        summary = "How body measurements such as your waist are shown and entered. Your logged values aren't changed."
    ) { i -> Settings.updatePortable { it.copy(lengthUnit = if (i == 1) LengthUnits.IN else LengthUnits.CM) } }
    SettingsGroup("Calendar")
    val days = listOf(1 to "Monday", 6 to "Saturday", 7 to "Sunday")
    SettingsChoiceRow(
        "Week starts on",
        days.map { it.second },
        days.indexOfFirst { it.first == prefs.weekStart }.coerceAtLeast(0),
        summary = "Used by the calendar and by weekly totals and breakdowns."
    ) { i -> Settings.updatePortable { it.copy(weekStart = days[i].first) } }
    // How the day log (home) shows each exercise (#8).
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

/** The + and − step for weights (#7). Stored in kg; the choices follow the display unit. */
@Composable
private fun WeightStepRow(unit: String, currentKg: Double?) {
    val lbs = unit == "lbs"
    val choices = if (lbs) listOf(1.0, 2.5, 5.0, 10.0) else listOf(0.5, 1.0, 1.25, 2.5, 5.0)
    fun toKg(v: Double) = if (lbs) v * WeightUnits.KG_PER_LB else v
    val selected = if (currentKg == null) 0 else
        choices.indexOfFirst { kotlin.math.abs(toKg(it) - currentKg) < 0.001 }.let { if (it < 0) 0 else it + 1 }
    SettingsChoiceRow(
        "Weight step",
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

@Composable
private fun LoggingPage() {
    val prefs by Settings.portable.collectAsState()
    SettingsGroup("While logging")
    SettingsSwitchRow(
        "Keep the screen on", prefs.keepScreenOn,
        summary = "Stops the phone going to sleep on the exercise screen, so it's ready between sets."
    ) { on -> Settings.updatePortable { it.copy(keepScreenOn = on) } }
    SettingsChoiceRow(
        "Fill new sets from",
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
    SettingsSwitchRow(
        "Select the next set after updating one", prefs.autoSelectNext,
        summary = "Handy for a copied workout: update each set in turn without tapping the next one."
    ) { on -> Settings.updatePortable { it.copy(autoSelectNext = on) } }
    SettingsChoiceRow(
        "Effort per set",
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
    SettingsGroup("Set types")
    SettingsSwitchRow(
        "Show set type on each set", prefs.showSetType,
        summary = "Warm-up, drop and failure sets carry a small W, D or F. Choose a set's type as you log it."
    ) { on -> Settings.updatePortable { it.copy(showSetType = on) } }
    SettingsSwitchRow(
        "Count warm-up sets in records and stats", prefs.warmupsCount,
        summary = "Off by default: warm-ups are left out of records, estimated maxes, volume, graphs, analysis and the " +
            "PDF report. They always show in your history."
    ) { on ->
        Settings.updatePortable { it.copy(warmupsCount = on) }
        // PR marks follow the setting straight away (#43).
        runBusy("Updating personal records…") {
            val n = Workouts.recalculatePrs()
            if (n == 0) null else ImportSummary("Personal records updated: $n ${if (n == 1) "set" else "sets"} changed.", ok = true)
        }
    }
    SettingsGroup("Mark sets complete")
    SettingsSwitchRow(
        "Show sets done", prefs.markComplete,
        summary = "Every set on the exercise screen has a tick box. This also shows how many are done for each exercise and the workout."
    ) { on -> Settings.updatePortable { it.copy(markComplete = on) } }
    SettingsGroup("Workout timer")
    SettingsSwitchRow(
        "Start the timer with the first set", prefs.workoutTimerAuto,
        summary = "Starts timing today's workout when you save its first set. Stop it from the day log's menu."
    ) { on -> Settings.updatePortable { it.copy(workoutTimerAuto = on) } }
    SettingsNote("These preferences travel with your .fitlens backups.")
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

@Composable
private fun RecordsPage(snap: Snapshot) {
    var confirmRecalc by remember { mutableStateOf(false) }
    val prefs by Settings.portable.collectAsState()
    SettingsGroup("Celebrations")
    SettingsSwitchRow(
        "Celebrate new personal records", prefs.celebratePrs,
        summary = "When a set you save is a new PR, FitLens gives a short vibration and names the record."
    ) { on -> Settings.updatePortable { it.copy(celebratePrs = on) } }
    FormulaChoice(snap, Records.Formula.of(prefs.e1rmFormula))
    SettingsGroup("PR marks")
    if (snap.sets.isEmpty()) {
        SettingsNote("New sets are marked as PRs when you save them. Once you've logged some, you can recalculate them here.")
    } else {
        SettingsActionRow(
            "Recalculate personal records",
            "Rebuilds the PR mark on every weight-and-reps set, imported ones included, for example after editing old sets."
        ) { confirmRecalc = true }
    }
    if (confirmRecalc) {
        ConfirmSheet(
            title = "Recalculate personal records?",
            message = "Every weight-and-reps set gets a PR mark only if it beat all earlier sets of at least as " +
                "many reps. PR marks that came from FitNotes are replaced. Timed and cardio sets keep theirs.",
            confirmLabel = "Recalculate",
            onDismiss = { confirmRecalc = false },
            onConfirm = {
                runBusy("Recalculating records…") {
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
}

/**
 * Settings → Personal records → Estimated 1RM (#42): each formula with a worked example from the user's best recent
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
    SettingsGroup("Estimated 1RM")
    SettingsChoiceRow(
        "Formula",
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
