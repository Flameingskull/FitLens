package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.ImportSummary
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.SegmentedSwitch

/**
 * The Settings sub-screens (#38, styled per #86). Each reads and writes only through [Settings], so no screen knows
 * whether a value lives on this phone or travels in backups. Rows appear only for features that exist today; later
 * builds add theirs (#7 units, #20 rest timer, #35 data tools and so on).
 */
enum class SettingsSection(val title: String, val summary: String, val group: String) {
    Backups("Backups", "Backup files, automatic backups, safety copy and PDF reports", "Data, backup & import"),
    // FitNotes imports lived on the Sync tab until #35 moved them here.
    Import("FitNotes import", "Import a FitNotes backup any time, or sync its backup folder", "Data, backup & import"),
    DataTools("Data tools", "Export to CSV, delete workout history", "Data, backup & import"),
    Units("Units & display", "Kilograms or pounds", "Training"),
    Logging("Workout & logging", "Screen on, filling in new sets, selecting the next set", "Training"),
    Records("Personal records", "PR marks and celebrations", "Training")
}

/** The main Settings screen: one row per sub-screen, grouped under gold-ruled headings. */
@Composable
fun SettingsScreen(nav: Nav) {
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Settings", onBack = { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSection.entries.groupBy { it.group }.forEach { (group, sections) ->
                SectionTitle(group)
                GoldHairline()
                sections.forEach { s ->
                    ListRowWithMenu(
                        title = s.title,
                        subtitle = s.summary,
                        onClick = { nav.push(Screen.SettingsPage(s)) }
                    )
                }
            }
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
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (section) {
                SettingsSection.Backups -> BackupsCard(snap)
                SettingsSection.Import -> FitNotesCards(snap)
                SettingsSection.DataTools -> DataToolsPage(snap)
                SettingsSection.Units -> UnitsPage()
                SettingsSection.Logging -> LoggingPage()
                SettingsSection.Records -> RecordsPage(snap)
            }
        }
    }
}

@Composable
private fun PageHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun UnitsPage() {
    val prefs by Settings.portable.collectAsState()
    SectionTitle("Weight")
    SegmentedSwitch(
        options = listOf("Kilograms (kg)", "Pounds (lbs)"),
        selected = if (prefs.weightUnit == "lbs") 1 else 0,
        onSelect = { i ->
            val unit = if (i == 1) "lbs" else "kg"
            // Choosing by hand is remembered, so a later FitNotes import doesn't switch it back.
            Settings.updatePortable { it.copy(weightUnit = unit, weightUnitManual = true) }
        }
    )
    PageHint(
        "Every weight is stored exactly, so switching only changes how weights are shown and entered. " +
            "This preference travels with your .fitlens backups."
    )
}

@Composable
private fun LoggingPage() {
    val prefs by Settings.portable.collectAsState()
    SectionTitle("While logging")
    ToggleRow("Keep the screen on", prefs.keepScreenOn) { on ->
        Settings.updatePortable { it.copy(keepScreenOn = on) }
    }
    PageHint("Stops the phone going to sleep on the set entry screen, so it's ready between sets.")
    SectionTitle("Fill new sets from")
    // Routine plans join these choices once routines exist (#21).
    SegmentedSwitch(
        options = listOf("Last workout", "Leave empty"),
        selected = if (prefs.autofillSource == PortableSettings.AUTOFILL_EMPTY) 1 else 0,
        onSelect = { i ->
            val source = if (i == 1) PortableSettings.AUTOFILL_EMPTY else PortableSettings.AUTOFILL_LAST
            Settings.updatePortable { it.copy(autofillSource = source) }
        }
    )
    PageHint(
        "Last workout fills the fields from your latest set today, or from the first set of the last time you did " +
            "the exercise. Leave empty starts every new set blank."
    )
    SectionTitle("After updating a set")
    ToggleRow("Select the next set", prefs.autoSelectNext) { on ->
        Settings.updatePortable { it.copy(autoSelectNext = on) }
    }
    PageHint(
        "Handy for a copied workout: update each set in turn without tapping the next one. " +
            "These preferences travel with your .fitlens backups."
    )
}

@Composable
private fun RecordsPage(snap: Snapshot) {
    var confirmRecalc by remember { mutableStateOf(false) }
    val prefs by Settings.portable.collectAsState()
    SectionTitle("Celebrations")
    ToggleRow("Celebrate new personal records", prefs.celebratePrs) { on ->
        Settings.updatePortable { it.copy(celebratePrs = on) }
    }
    PageHint("When a set you save is a new PR, FitLens gives a short vibration and names the record.")
    SectionTitle("PR marks")
    PageHint(
        "New sets are marked as PRs when you save them. Recalculate rebuilds the PR marks on every " +
            "weight-and-reps set, imported ones included, for example after editing old sets."
    )
    OutlinedButton(onClick = { confirmRecalc = true }, enabled = snap.sets.isNotEmpty()) { Text("Recalculate personal records") }
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
