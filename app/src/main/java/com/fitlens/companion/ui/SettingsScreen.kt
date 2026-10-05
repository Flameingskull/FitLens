package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.SearchFieldIcon

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.DistanceUnits
import com.fitlens.companion.data.WeightUnits
import com.fitlens.companion.data.Effort
import com.fitlens.companion.data.ImportSummary
import com.fitlens.companion.data.LengthUnits
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.PreferenceGroup
import com.fitlens.companion.data.Records
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.OverflowMenu
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.SettingsSwitchRow
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The pages Settings still opens (#38, #86), where FitNotes opens a page or dialog too (#147): backups, the FitNotes
 * import, the data tools, Home Screen Settings and the rest timer. Every other setting sits on the main list. Each reads
 * and writes only through [Settings], so no screen knows whether a value lives on this phone or travels in backups.
 * The text of the main list and these pages is in `res/values/strings.xml` (#94).
 */
enum class SettingsSection(@StringRes val title: Int, val group: PreferenceGroup? = null) {
    Backups(R.string.settings_page_backups),
    // FitNotes imports lived on the Sync tab until #35 moved them here.
    Import(R.string.settings_page_import),
    DataTools(R.string.settings_page_data_tools),
    // A page with a group has ⋮ › Reset this section (#41).
    Home(R.string.settings_page_home, PreferenceGroup.HOME),
    // The rest timer's options lived only in its sheet until #86 gave them a page.
    Rest(R.string.settings_page_rest, PreferenceGroup.REST),
    // Defaults for photos, the slideshow and video, and the PDF report (#46).
    Media(R.string.settings_page_media, PreferenceGroup.MEDIA),
    // In-app help and About (#33).
    Help(R.string.settings_page_help),
    About(R.string.settings_page_about)
}

/** One searchable setting on a page (#86): what it's called, other words people might search for, and the page. */
private data class SettingEntry(@StringRes val title: Int, val section: SettingsSection, @StringRes val keywords: Int)

/** Every setting on a page, for search. A row added to a page belongs here too; main-list rows search themselves. */
private val CATALOGUE = listOf(
    SettingEntry(R.string.search_backup_file, SettingsSection.Backups, R.string.search_backup_file_kw),
    SettingEntry(R.string.search_auto_backups, SettingsSection.Backups, R.string.search_auto_backups_kw),
    SettingEntry(R.string.search_backup_names, SettingsSection.Backups, R.string.search_backup_names_kw),
    SettingEntry(R.string.search_backup_keep, SettingsSection.Backups, R.string.search_backup_keep_kw),
    SettingEntry(R.string.search_backup_after, SettingsSection.Backups, R.string.search_backup_after_kw),
    SettingEntry(R.string.search_safety_copy, SettingsSection.Backups, R.string.search_safety_copy_kw),
    SettingEntry(R.string.search_pdf_report, SettingsSection.Backups, R.string.search_pdf_report_kw),
    SettingEntry(R.string.search_import_fitnotes, SettingsSection.Import, R.string.search_import_fitnotes_kw),
    SettingEntry(R.string.search_fitnotes_sync, SettingsSection.Import, R.string.search_fitnotes_sync_kw),
    SettingEntry(R.string.search_fitnotes_auto, SettingsSection.Import, R.string.search_fitnotes_auto_kw),
    SettingEntry(R.string.search_csv, SettingsSection.DataTools, R.string.search_csv_kw),
    SettingEntry(R.string.search_delete_history, SettingsSection.DataTools, R.string.search_delete_history_kw),
    SettingEntry(R.string.reset_row, SettingsSection.DataTools, R.string.search_reset_kw),
    SettingEntry(R.string.home_show_categories, SettingsSection.Home, R.string.search_home_categories_kw),
    SettingEntry(R.string.home_sets_shown, SettingsSection.Home, R.string.search_home_sets_kw),
    SettingEntry(R.string.search_rest_length, SettingsSection.Rest, R.string.search_rest_length_kw),
    SettingEntry(R.string.search_rest_auto, SettingsSection.Rest, R.string.search_rest_auto_kw),
    SettingEntry(R.string.search_rest_vibrate, SettingsSection.Rest, R.string.search_rest_vibrate_kw),
    SettingEntry(R.string.search_rest_sound, SettingsSection.Rest, R.string.search_rest_sound_kw),
    SettingEntry(R.string.media_pose, SettingsSection.Media, R.string.search_media_pose_kw),
    SettingEntry(R.string.media_group, SettingsSection.Media, R.string.search_media_group_kw),
    SettingEntry(R.string.media_remember, SettingsSection.Media, R.string.search_media_remember_kw),
    SettingEntry(R.string.media_reset, SettingsSection.Media, R.string.search_media_reset_kw),
    SettingEntry(R.string.media_pdf_pages, SettingsSection.Media, R.string.search_media_pdf_pages_kw),
    SettingEntry(R.string.media_pdf_photos, SettingsSection.Media, R.string.search_media_pdf_photos_kw),
    SettingEntry(R.string.search_guides, SettingsSection.Help, R.string.search_guides_kw),
    SettingEntry(R.string.search_version, SettingsSection.About, R.string.search_version_kw),
    SettingEntry(R.string.speed_title, SettingsSection.About, R.string.search_speed_kw),
    SettingEntry(R.string.search_licences, SettingsSection.About, R.string.search_licences_kw)
)

/** FitNotes's three headings, in its order (#147). */
private enum class SettingsHeading(@StringRes val title: Int) {
    SETTINGS(R.string.settings_heading_settings), DATA(R.string.settings_heading_data), OTHER(R.string.settings_heading_other)
}

/** One row of the main list: its heading, words to search by, and the row itself. */
private class MainRow(
    val heading: SettingsHeading,
    val title: String,
    val keywords: String = "",
    val content: @Composable () -> Unit
)

/** A main-list row whose title and search words are string resources; [content] gets the title. */
@Composable
private fun mainRow(
    heading: SettingsHeading,
    @StringRes title: Int,
    @StringRes keywords: Int,
    content: @Composable (String) -> Unit
): MainRow {
    val text = stringResource(title)
    return MainRow(heading, text, stringResource(keywords)) { content(text) }
}

/** The days the week can start on (#7), named in the phone's language. Stored as ISO day numbers. */
private val WEEK_STARTS = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private fun dayName(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

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
    val headings = SettingsHeading.entries.associateWith { stringResource(it.title) }
    val pages = SettingsSection.entries.associateWith { stringResource(it.title) }
    // The pages' settings as text for search: the entry, its title, then its search words and page.
    val catalogue = CATALOGUE.map { e ->
        Triple(e, stringResource(e.title), "${stringResource(e.keywords)} ${pages.getValue(e.section)}")
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.settings_title), onBack = { nav.pop() })
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.settings_search)) },
            leadingIcon = { SearchFieldIcon() },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            fun matches(text: String) = words.all { it in text.lowercase() }
            val shown = if (words.isEmpty()) rows else
                rows.filter { matches("${it.title} ${it.keywords} ${headings.getValue(it.heading)}") }
            val onPages = if (words.isEmpty()) emptyList() else catalogue.filter { (_, title, more) -> matches("$title $more") }
            if (words.isNotEmpty() && shown.isEmpty() && onPages.isEmpty()) {
                SettingsNote(stringResource(R.string.settings_search_none, query.trim()))
            }
            shown.groupBy { it.heading }.forEach { (heading, inGroup) ->
                SettingsGroup(headings.getValue(heading))
                inGroup.forEach { it.content() }
            }
            if (onPages.isNotEmpty()) {
                SettingsGroup(stringResource(R.string.settings_on_other_pages))
                onPages.forEach { (e, title, _) ->
                    SettingsActionRow(title, stringResource(R.string.settings_in_page, pages.getValue(e.section))) {
                        nav.push(Screen.SettingsPage(e.section))
                    }
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
    var whatsNew by remember { mutableStateOf(false) }
    var e1rmLimit by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    fun open(section: SettingsSection) = nav.push(Screen.SettingsPage(section))
    fun browse(url: String) = openInBrowser(ctx, url)
    if (confirmRecalc) RecalculateRecordsSheet { confirmRecalc = false }
    if (whatsNew) WhatsNewSheet { whatsNew = false }
    if (e1rmLimit) EstimatedOneRmSettingsSheet { e1rmLimit = false }
    if (confirmReset) ResetSettingsSheet { confirmReset = false }
    val s = SettingsHeading.SETTINGS
    val d = SettingsHeading.DATA
    val o = SettingsHeading.OTHER
    val themeMessage = stringResource(R.string.settings_theme_message)
    return listOf(
        mainRow(s, R.string.settings_theme, R.string.settings_theme_kw) { title ->
            SettingsActionRow(title, value = stringResource(R.string.settings_theme_value)) { UiEvents.show(themeMessage) }
        },
        mainRow(s, R.string.settings_unit_system, R.string.settings_unit_system_kw) { title -> UnitSystemRow(title, prefs) },
        mainRow(s, R.string.settings_weight_unit, R.string.settings_weight_unit_kw) { title ->
            SettingsChoiceRow(
                title,
                listOf(stringResource(R.string.unit_kilograms), stringResource(R.string.unit_pounds)),
                if (prefs.weightUnit == "lbs") 1 else 0,
                summary = stringResource(R.string.settings_weight_unit_summary)
            ) { i ->
                // Choosing by hand is remembered, so a later FitNotes import doesn't switch it back.
                Settings.updatePortable { it.copy(weightUnit = if (i == 1) "lbs" else "kg", weightUnitManual = true) }
            }
        },
        mainRow(s, R.string.settings_distance_unit, R.string.settings_distance_unit_kw) { title ->
            SettingsChoiceRow(
                title,
                DistanceUnits.ALL.map { "${DistanceUnits.label(it)} ($it)" },
                DistanceUnits.ALL.indexOf(prefs.distanceUnit).coerceAtLeast(0),
                summary = stringResource(R.string.settings_distance_unit_summary)
            ) { i -> Settings.updatePortable { it.copy(distanceUnit = DistanceUnits.ALL[i]) } }
        },
        mainRow(s, R.string.settings_length_unit, R.string.settings_length_unit_kw) { title ->
            SettingsChoiceRow(
                title,
                listOf(stringResource(R.string.unit_centimetres), stringResource(R.string.unit_inches)),
                if (prefs.lengthUnit == LengthUnits.IN) 1 else 0,
                summary = stringResource(R.string.settings_length_unit_summary)
            ) { i -> Settings.updatePortable { it.copy(lengthUnit = if (i == 1) LengthUnits.IN else LengthUnits.CM) } }
        },
        mainRow(s, R.string.settings_sex, R.string.settings_sex_kw) { title ->
            SettingsChoiceRow(
                title,
                com.fitlens.companion.data.BodyFat.Sex.entries.map { sexText(LocalContext.current.resources, it) },
                com.fitlens.companion.data.BodyFat.Sex.of(prefs.profileSex)?.ordinal ?: -1,
                summary = stringResource(R.string.settings_sex_summary)
            ) { i -> Settings.updatePortable { it.copy(profileSex = com.fitlens.companion.data.BodyFat.Sex.entries[i].key) } }
        },
        mainRow(s, R.string.settings_week_start, R.string.settings_week_start_kw) { title ->
            SettingsChoiceRow(
                title,
                WEEK_STARTS.map(::dayName),
                WEEK_STARTS.indexOfFirst { it.value == prefs.weekStart }.coerceAtLeast(0)
            ) { i -> Settings.updatePortable { it.copy(weekStart = WEEK_STARTS[i].value) } }
        },
        mainRow(s, R.string.settings_weight_step, R.string.settings_weight_step_kw) { title ->
            WeightStepRow(title, prefs.weightUnit, prefs.weightIncrementKg)
        },
        mainRow(s, R.string.settings_home, R.string.settings_home_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_home_summary)) { open(SettingsSection.Home) }
        },
        mainRow(s, R.string.settings_prs, R.string.settings_prs_kw) { title ->
            SettingsSwitchRow(title, prefs.celebratePrs, summary = stringResource(R.string.settings_prs_summary)) { on ->
                Settings.updatePortable { it.copy(celebratePrs = on) }
            }
        },
        mainRow(s, R.string.settings_mark_complete, R.string.settings_mark_complete_kw) { title ->
            SettingsSwitchRow(title, prefs.markComplete, summary = stringResource(R.string.settings_mark_complete_summary)) { on ->
                Settings.updatePortable { it.copy(markComplete = on) }
            }
        },
        mainRow(s, R.string.settings_auto_next, R.string.settings_auto_next_kw) { title ->
            SettingsSwitchRow(title, prefs.autoSelectNext, summary = stringResource(R.string.settings_auto_next_summary)) { on ->
                Settings.updatePortable { it.copy(autoSelectNext = on) }
            }
        },
        mainRow(s, R.string.settings_screen_on, R.string.settings_screen_on_kw) { title ->
            SettingsSwitchRow(title, prefs.keepScreenOn, summary = stringResource(R.string.settings_screen_on_summary)) { on ->
                Settings.updatePortable { it.copy(keepScreenOn = on) }
            }
        },
        // FitLens's own settings follow FitNotes's.
        mainRow(s, R.string.settings_fill, R.string.settings_fill_kw) { title ->
            SettingsChoiceRow(
                title,
                listOf(stringResource(R.string.settings_fill_last), stringResource(R.string.settings_fill_empty)),
                if (prefs.autofillSource == PortableSettings.AUTOFILL_EMPTY) 1 else 0,
                descriptions = listOf(
                    stringResource(R.string.settings_fill_last_detail),
                    stringResource(R.string.settings_fill_empty_detail)
                )
            ) { i ->
                val source = if (i == 1) PortableSettings.AUTOFILL_EMPTY else PortableSettings.AUTOFILL_LAST
                Settings.updatePortable { it.copy(autofillSource = source) }
            }
        },
        mainRow(s, R.string.settings_effort, R.string.settings_effort_kw) { title ->
            SettingsChoiceRow(
                title,
                listOf(stringResource(R.string.settings_effort_off), "RPE", "RIR"),
                when (prefs.effortMode) { Effort.RPE -> 1; Effort.RIR -> 2; else -> 0 },
                summary = stringResource(R.string.settings_effort_summary),
                descriptions = listOf(
                    stringResource(R.string.settings_effort_off_detail),
                    stringResource(R.string.settings_effort_rpe_detail),
                    stringResource(R.string.settings_effort_rir_detail)
                )
            ) { i ->
                val mode = when (i) { 1 -> Effort.RPE; 2 -> Effort.RIR; else -> Effort.OFF }
                Settings.updatePortable { it.copy(effortMode = mode) }
            }
        },
        mainRow(s, R.string.settings_set_types, R.string.settings_set_types_kw) { title ->
            SettingsSwitchRow(title, prefs.showSetType, summary = stringResource(R.string.settings_set_types_summary)) { on ->
                Settings.updatePortable { it.copy(showSetType = on) }
            }
        },
        mainRow(s, R.string.settings_warmups, R.string.settings_warmups_kw) { title ->
            val busy = stringResource(R.string.settings_prs_updating)
            SettingsSwitchRow(title, prefs.warmupsCount, summary = stringResource(R.string.settings_warmups_summary)) { on ->
                Settings.updatePortable { it.copy(warmupsCount = on) }
                // PR marks follow the setting straight away (#43).
                runBusy(busy) {
                    val n = Workouts.recalculatePrs()
                    if (n == 0) null
                    else ImportSummary(ctx.resources.getQuantityString(R.plurals.settings_prs_updated, n, n), ok = true)
                }
            }
        },
        mainRow(s, R.string.settings_timer_auto, R.string.settings_timer_auto_kw) { title ->
            SettingsSwitchRow(title, prefs.workoutTimerAuto, summary = stringResource(R.string.settings_timer_auto_summary)) { on ->
                Settings.updatePortable { it.copy(workoutTimerAuto = on) }
            }
        },
        mainRow(s, R.string.settings_page_rest, R.string.settings_rest_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_rest_summary), value = fmtDuration(prefs.restSeconds)) {
                open(SettingsSection.Rest)
            }
        },
        mainRow(s, R.string.settings_formula, R.string.settings_formula_kw) { title ->
            FormulaChoice(title, snap, Records.Formula.of(prefs.e1rmFormula))
        },
        mainRow(s, R.string.settings_e1rm, R.string.settings_e1rm_kw) { title ->
            val limit = Records.maxRepsFor(Records.Formula.of(prefs.e1rmFormula))
            SettingsActionRow(
                title,
                stringResource(R.string.settings_e1rm_summary),
                value = pluralStringResource(R.plurals.settings_e1rm_value, limit, limit)
            ) { e1rmLimit = true }
        },
        // FitLens's own: photos, the slideshow and video, and the PDF report (#46).
        mainRow(s, R.string.settings_media, R.string.settings_media_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_media_summary)) { open(SettingsSection.Media) }
        },
        mainRow(d, R.string.settings_backup, R.string.settings_backup_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_backup_summary)) { open(SettingsSection.Backups) }
        },
        mainRow(d, R.string.settings_restore, R.string.settings_restore_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_restore_summary)) { open(SettingsSection.Backups) }
        },
        mainRow(d, R.string.settings_auto_backup, R.string.settings_auto_backup_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_auto_backup_summary)) { open(SettingsSection.Backups) }
        },
        mainRow(d, R.string.settings_csv, R.string.settings_csv_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_csv_summary)) { open(SettingsSection.DataTools) }
        },
        mainRow(d, R.string.settings_recalc, R.string.settings_recalc_kw) { title ->
            SettingsActionRow(
                title,
                stringResource(R.string.settings_recalc_summary),
                enabled = snap.sets.isNotEmpty(),
                disabledReason = stringResource(R.string.settings_recalc_disabled)
            ) { confirmRecalc = true }
        },
        mainRow(d, R.string.settings_delete_history, R.string.settings_delete_history_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_delete_history_summary)) { open(SettingsSection.DataTools) }
        },
        mainRow(d, R.string.settings_reset, R.string.search_reset_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_reset_summary)) { confirmReset = true }
        },
        mainRow(d, R.string.settings_import, R.string.settings_import_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_import_summary)) { open(SettingsSection.Import) }
        },
        mainRow(o, R.string.settings_help, R.string.settings_help_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_help_summary)) { open(SettingsSection.Help) }
        },
        mainRow(o, R.string.settings_feedback, R.string.settings_feedback_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_feedback_summary)) { browse("$REPO_URL/issues/new/choose") }
        },
        mainRow(o, R.string.settings_change_log, R.string.settings_change_log_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_change_log_summary)) { whatsNew = true }
        },
        mainRow(o, R.string.settings_setup, R.string.settings_setup_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_setup_summary)) { nav.push(Screen.Setup) }
        },
        mainRow(o, R.string.settings_privacy, R.string.settings_privacy_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_privacy_summary)) { open(SettingsSection.About) }
        },
        mainRow(o, R.string.settings_about, R.string.settings_about_kw) { title ->
            SettingsActionRow(title, stringResource(R.string.settings_about_summary)) { open(SettingsSection.About) }
        }
    )
}

/** FitNotes's Unit System (#147): metric or imperial in one go. A mix chosen below shows as Custom. */
@Composable
private fun UnitSystemRow(title: String, prefs: PortableSettings) {
    val metric = prefs.weightUnit == "kg" && prefs.distanceUnit == DistanceUnits.KM && prefs.lengthUnit == LengthUnits.CM
    val imperial = prefs.weightUnit == "lbs" && prefs.distanceUnit == DistanceUnits.MI && prefs.lengthUnit == LengthUnits.IN
    SettingsChoiceRow(
        title,
        listOf(stringResource(R.string.settings_metric), stringResource(R.string.settings_imperial)),
        when { metric -> 0; imperial -> 1; else -> -1 },
        summary = if (metric || imperial) null else
            stringResource(R.string.settings_units_custom, prefs.weightUnit, prefs.distanceUnit, prefs.lengthUnit),
        descriptions = listOf(stringResource(R.string.settings_metric_detail), stringResource(R.string.settings_imperial_detail))
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
    val ctx = LocalContext.current
    val busy = stringResource(R.string.recalc_busy)
    val unchanged = stringResource(R.string.recalc_unchanged)
    ConfirmSheet(
        title = stringResource(R.string.recalc_title),
        message = stringResource(R.string.recalc_body),
        confirmLabel = stringResource(R.string.recalc_confirm),
        onDismiss = onDismiss,
        onConfirm = {
            runBusy(busy) {
                val n = Workouts.recalculatePrs()
                ImportSummary(
                    if (n == 0) unchanged else ctx.resources.getQuantityString(R.plurals.recalc_done, n, n),
                    ok = true
                )
            }
        },
        destructive = false
    )
}

/** One Settings sub-screen. Back returns to Settings, then to wherever Settings was opened from. */
@Composable
fun SettingsPageScreen(snap: Snapshot, nav: Nav, section: SettingsSection) {
    val group = section.group
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(section.title), onBack = { nav.pop() }) {
            if (group != null) {
                val portable by Settings.portable.collectAsState()
                val device by Settings.device.collectAsState()
                OverflowMenu(
                    listOf(
                        MenuAction(
                            stringResource(R.string.reset_section),
                            enabled = Settings.isGroupChanged(group, portable, device)
                        ) { confirmReset = true }
                    ),
                    description = stringResource(R.string.reset_section_menu)
                )
            }
        }
        if (group != null && confirmReset) ResetSectionSheet(section, group) { confirmReset = false }
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
                SettingsSection.Help -> HelpPage()
                SettingsSection.About -> AboutPage()
            }
        }
    }
}

/**
 * Confirms a page's Reset this section (#41), then resets only that page's preferences ([Settings.resetGroup]) and
 * offers Undo. The rest of Settings and all data stay as they are.
 */
@Composable
private fun ResetSectionSheet(section: SettingsSection, group: PreferenceGroup, onDismiss: () -> Unit) {
    val page = stringResource(section.title)
    val done = stringResource(R.string.reset_section_done, page)
    val undo = stringResource(R.string.undo)
    ConfirmSheet(
        title = stringResource(R.string.reset_section_title, page),
        message = stringResource(
            when (group) {
                PreferenceGroup.HOME -> R.string.reset_section_body_home
                PreferenceGroup.REST -> R.string.reset_section_body_rest
                PreferenceGroup.MEDIA -> R.string.reset_section_body_media
            }
        ),
        confirmLabel = stringResource(R.string.reset_section_confirm),
        dismissLabel = stringResource(R.string.reset_section_keep),
        onDismiss = onDismiss,
        onConfirm = {
            val before = Settings.resetGroup(group)
            UiEvents.show(done, undo) { Settings.restoreGroup(group, before) }
        },
        destructive = false
    )
}

/** Home Screen Settings (#147): how the day log shows each exercise (#8). */
@Composable
private fun HomePage() {
    val prefs by Settings.portable.collectAsState()
    SettingsGroup(stringResource(R.string.home_group))
    SettingsSwitchRow(
        stringResource(R.string.home_show_categories), prefs.homeShowCategories,
        summary = stringResource(R.string.home_show_categories_summary)
    ) { on -> Settings.updatePortable { it.copy(homeShowCategories = on) } }
    val counts = listOf(0) + (1..10)
    val all = stringResource(R.string.home_sets_all)
    SettingsChoiceRow(
        stringResource(R.string.home_sets_shown),
        counts.map { if (it == 0) all else "$it" },
        counts.indexOf(prefs.homeSetsShown).coerceAtLeast(0),
        summary = stringResource(R.string.home_sets_shown_summary)
    ) { i -> Settings.updatePortable { it.copy(homeSetsShown = counts[i]) } }
    SettingsNote(stringResource(R.string.settings_travel_note))
}

/**
 * Settings → Progress photos and media (#46): the defaults FitLens's own photo features start from. All of them
 * travel in backups, and each is validated when read, so a newer build's backup can't break these screens.
 */
@Composable
private fun MediaPage() {
    val prefs by Settings.portable.collectAsState()
    val res = LocalContext.current.resources
    SettingsGroup(stringResource(R.string.media_group_photos))
    // Ask each time, then None, then the poses: null, Poses.NONE and the pose names as stored.
    val poses: List<String?> = listOf(null, com.fitlens.companion.data.Poses.NONE) + com.fitlens.companion.data.Poses.all
    val none = stringResource(R.string.media_none)
    SettingsChoiceRow(
        stringResource(R.string.media_pose),
        listOf(stringResource(R.string.media_pose_ask), none) + com.fitlens.companion.data.Poses.all.map { poseText(res, it) },
        poses.indexOf(prefs.photoDefaultPose).coerceAtLeast(0),
        summary = stringResource(R.string.media_pose_summary)
    ) { i -> Settings.updatePortable { it.copy(photoDefaultPose = poses[i]) } }
    val groups = com.fitlens.companion.data.MediaPrefs.GROUPS
    SettingsChoiceRow(
        stringResource(R.string.media_group),
        groups.map { groupText(res, it) },
        groups.indexOf(prefs.photoGroupBy).coerceAtLeast(0),
        summary = stringResource(R.string.media_group_summary)
    ) { i -> Settings.updatePortable { it.copy(photoGroupBy = groups[i]) } }

    SettingsGroup(stringResource(R.string.media_group_video))
    SettingsSwitchRow(
        stringResource(R.string.media_remember), prefs.rememberVideoOpts,
        summary = stringResource(R.string.media_remember_summary)
    ) { on -> Settings.updatePortable { it.copy(rememberVideoOpts = on) } }
    val resetDone = stringResource(R.string.media_reset_done)
    SettingsActionRow(
        stringResource(R.string.media_reset),
        stringResource(R.string.media_reset_summary),
        enabled = prefs.videoOpts != null,
        disabledReason = stringResource(R.string.media_reset_disabled)
    ) {
        Settings.updatePortable { it.copy(videoOpts = null) }
        UiEvents.show(resetDone)
    }

    SettingsGroup(stringResource(R.string.media_group_pdf))
    SettingsChoiceRow(
        stringResource(R.string.media_pdf_pages),
        listOf(stringResource(R.string.media_pdf_dark), stringResource(R.string.media_pdf_light)),
        if (prefs.pdfDark) 0 else 1
    ) { i -> Settings.updatePortable { it.copy(pdfDark = i == 0) } }
    SettingsChoiceRow(
        stringResource(R.string.media_pdf_photos),
        (0..4).map { if (it == 0) none else "$it" },
        prefs.pdfPhotosPerDay.coerceIn(0, 4),
        summary = stringResource(R.string.media_pdf_photos_summary)
    ) { i -> Settings.updatePortable { it.copy(pdfPhotosPerDay = i) } }
    SettingsNote(stringResource(R.string.settings_travel_note))
}

/** The + and − step for weights (#7). Stored in kg; the choices follow the display unit. */
@Composable
private fun WeightStepRow(title: String, unit: String, currentKg: Double?) {
    val lbs = unit == "lbs"
    val choices = if (lbs) listOf(1.0, 2.5, 5.0, 10.0) else listOf(0.5, 1.0, 1.25, 2.5, 5.0)
    fun toKg(v: Double) = if (lbs) v * WeightUnits.KG_PER_LB else v
    val selected = if (currentKg == null) 0 else
        choices.indexOfFirst { kotlin.math.abs(toKg(it) - currentKg) < 0.001 }.let { if (it < 0) 0 else it + 1 }
    SettingsChoiceRow(
        title,
        listOf(stringResource(R.string.settings_weight_step_default)) + choices.map { "${fmtNum(it, 2)} $unit" },
        selected,
        summary = stringResource(R.string.settings_weight_step_summary)
    ) { i ->
        val kg = if (i == 0) null else toKg(choices[i - 1])
        Settings.updatePortable { it.copy(weightIncrementKg = kg) }
    }
}

/** The distance and length units (#7), as first-run setup asks them. */
@Composable
internal fun DistanceAndLengthSetting(distanceUnit: String, lengthUnit: String) {
    SectionTitle(stringResource(R.string.setup_distances))
    com.fitlens.companion.ui.design.SegmentedSwitch(
        options = DistanceUnits.ALL.map { DistanceUnits.label(it) },
        selected = DistanceUnits.ALL.indexOf(distanceUnit).coerceAtLeast(0),
        onSelect = { i -> Settings.updatePortable { it.copy(distanceUnit = DistanceUnits.ALL[i]) } }
    )
    SectionTitle(stringResource(R.string.setup_body_measurements))
    com.fitlens.companion.ui.design.SegmentedSwitch(
        options = listOf(stringResource(R.string.setup_centimetres), stringResource(R.string.setup_inches)),
        selected = if (lengthUnit == LengthUnits.IN) 1 else 0,
        onSelect = { i -> Settings.updatePortable { it.copy(lengthUnit = if (i == 1) LengthUnits.IN else LengthUnits.CM) } }
    )
}

/** The first day of the week for the calendar and weekly analysis (#7), as first-run setup asks it. */
@Composable
internal fun WeekStartSetting(weekStart: Int) {
    SectionTitle(stringResource(R.string.setup_week_starts))
    com.fitlens.companion.ui.design.SegmentedSwitch(
        options = WEEK_STARTS.map(::dayName),
        selected = WEEK_STARTS.indexOfFirst { it.value == weekStart }.coerceAtLeast(0),
        onSelect = { i -> Settings.updatePortable { it.copy(weekStart = WEEK_STARTS[i].value) } }
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
    SettingsGroup(stringResource(R.string.rest_group_length))
    Column(Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        RestLengthStepper(seconds = chosen, onChange = { chosen = it })
    }
    SettingsNote(stringResource(R.string.rest_length_note, fmtDuration(chosen)))
    SettingsGroup(stringResource(R.string.rest_group_over))
    Column {
        RestAlertOptions()
    }
    SettingsNote(stringResource(R.string.rest_keeps_running))
}

/**
 * Settings → Estimated 1RM Formula (#42): each formula with a worked example from the user's best recent
 * set, so the difference shows before choosing. Stored sets never change; estimates follow at once.
 */
@Composable
private fun FormulaChoice(title: String, snap: Snapshot, chosen: Records.Formula) {
    // The best recent set to show the formulas on: the last 90 days, 2 to 10 reps, highest automatic estimate.
    val example = remember(snap) {
        val from = java.time.LocalDate.now().minusDays(90).format(Dates.ISO)
        val recent = snap.statSets.filter { it.weightKg > 0 && it.reps in 2..10 }
        (recent.filter { it.date.take(10) >= from }.ifEmpty { recent })
            .maxByOrNull { Records.oneRepMax(it.weightKg, it.reps, Records.Formula.AUTO) }
    }
    val formulas = Records.Formula.entries
    val notEstimated = stringResource(R.string.formula_not_estimated)
    SettingsChoiceRow(
        title,
        formulas.map { it.label },
        formulas.indexOf(chosen).coerceAtLeast(0),
        summary = stringResource(R.string.formula_summary),
        descriptions = formulas.map { f ->
            val upTo = pluralStringResource(R.plurals.formula_up_to, f.maxReps, f.maxReps)
            if (example == null) pluralStringResource(R.plurals.settings_e1rm_value, f.maxReps, f.maxReps) else {
                val est = Records.oneRepMax(example.weightKg, example.reps, f)
                val result = if (est > 0) "${snap.fmtWeight(est)} ${snap.weightUnit}" else notEstimated
                stringResource(
                    R.string.formula_example,
                    "${snap.fmtWeight(example.weightKg)} ${snap.weightUnit}", example.reps, result, upTo
                )
            }
        }
    ) { i -> Settings.updatePortable { it.copy(e1rmFormula = formulas[i].key) } }
}
