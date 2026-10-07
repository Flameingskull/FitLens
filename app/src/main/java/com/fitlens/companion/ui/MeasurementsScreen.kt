package com.fitlens.companion.ui

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Checkbox
import com.fitlens.companion.ui.design.GlassOutlinedButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.fitlens.companion.data.MeasureUnits
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.WeightUnits
import com.fitlens.companion.ui.design.DropdownPill
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.StandardMeasurements
import com.fitlens.companion.data.Store
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.OverflowMenu
import kotlinx.coroutines.launch

/**
 * Every body measurement in one place (#88, #27): turn each on or off (off hides it from the body tracker, the day log
 * and the pickers, and keeps its values), create, edit or delete custom measurements, and add the standard set for
 * people who don't import from FitNotes. Choices made here survive FitNotes imports.
 */
@Composable
fun MeasurementsScreen(snap: Snapshot, nav: Nav) {
    val res = LocalContext.current.resources
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MeasurementDef?>(null) }
    var deleting by remember { mutableStateOf<MeasurementDef?>(null) }
    var goalFor by remember { mutableStateOf<MeasurementDef?>(null) }
    val all = remember(snap.bodyKey) { snap.allMeasurements }
    val missing = remember(snap.bodyKey) { StandardMeasurements.missing(snap.measurementDefs.map { it.name }) }

    Column(Modifier.fillMaxSize()) {
        PlainTopBar(stringResource(R.string.body_measurements)) {
            IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ms_new)) }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            item {
                Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    // FitNotes's words at the top of Measurements (#144).
                    Text(
                        stringResource(R.string.ms_intro),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (missing.isNotEmpty()) {
                        GlassOutlinedButton(
                            onClick = { AppScope.scope.launch { Store.addStandardMeasurements() } },
                            modifier = Modifier.padding(top = Spacing.sm).heightIn(min = Spacing.touch)
                        ) { Text(stringResource(R.string.ms_add_standard, missing.size)) }
                    }
                }
                HorizontalDivider(color = Brand.Hairline)
            }
            if (all.isEmpty()) {
                item { EmptyState(stringResource(R.string.body_empty_title), stringResource(R.string.ms_empty_body)) }
            }
            items(all, key = { it.name }) { m ->
                // As FitNotes lists them (#144): the name in bold, its unit in full, its goal, then the tracking checkbox.
                // Tapping the row edits its goal (and, for one of your own, the measurement itself from its ⋮).
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .clickable(onClickLabel = stringResource(R.string.ms_set_goal_for, m.name)) { goalFor = m }
                        .padding(start = Spacing.lg, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                        Text(m.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        if (m.unit.isNotBlank()) {
                            Text(unitLongName(res, m.unit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(goalText(res, m), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Its own unit (#7): a weight in kg or lbs, a length in cm or in, whatever Settings says for the
                        // rest. Values are stored as logged and converted for display.
                        val choices = MeasureUnits.choices(m.unit)
                        if (choices.isNotEmpty()) {
                            val global = if (WeightUnits.of(m.unit) != null) snap.weightUnit else snap.lengthUnit
                            DropdownPill(
                                stringResource(R.string.ms_unit_for, m.name),
                                listOf(stringResource(R.string.ms_unit_as_settings, global)) + choices.map { stringResource(R.string.ms_always_in, it) },
                                m.displayUnit?.let { u -> choices.indexOf(u) + 1 } ?: 0
                            ) { i ->
                                val unit = if (i == 0) null else choices[i - 1]
                                AppScope.scope.launch { Store.setMeasurementDisplayUnit(m.name, unit) }
                            }
                        }
                    }
                    Checkbox(
                        checked = m.enabled,
                        onCheckedChange = { on -> AppScope.scope.launch { Store.setMeasurementEnabled(m.name, m.unit, on) } },
                        modifier = Modifier.semantics {
                            contentDescription = res.getString(R.string.ms_show, m.name)
                            stateDescription = res.getString(if (m.enabled) R.string.ms_on else R.string.ms_off)
                        }
                    )
                    if (m.custom) {
                        OverflowMenu(
                            listOf(
                                MenuAction(stringResource(R.string.lib_edit)) { editing = m },
                                MenuAction(stringResource(R.string.lib_delete)) { deleting = m }
                            ),
                            description = stringResource(R.string.ms_options_for, m.name)
                        )
                    }
                }
                GoldHairline()
            }
        }
    }

    goalFor?.let { m -> MeasurementGoalSheet(m) { goalFor = null } }
    if (creating) CustomMetricEditor(snap, null) { creating = false }
    editing?.let { m -> CustomMetricEditor(snap, m) { editing = null } }
    deleting?.let { m ->
        val manual = Store.manualCount(snap, m.name)
        ConfirmDialog(
            title = stringResource(R.string.ms_delete_title, m.name),
            text = (if (manual > 0) pluralStringResource(R.plurals.ms_delete_manual, manual, manual) + " " else "") +
                stringResource(R.string.ms_delete_fitnotes),
            onDismiss = { deleting = null }
        ) { AppScope.scope.launch { Store.deleteCustomMetric(m.name) } }
    }
}

/** A unit as FitNotes names it on Measurements (#144): "Kilograms (kgs)", "Milligrams (mg)", "Percent (%)". */
internal fun unitLongName(res: Resources, unit: String): String = when (unit.trim().lowercase()) {
    "kg", "kgs" -> res.getString(R.string.ms_unit_kg)
    "lb", "lbs" -> res.getString(R.string.ms_unit_lb)
    "mg" -> res.getString(R.string.ms_unit_mg)
    "mcg", "µg", "ug" -> res.getString(R.string.ms_unit_mcg)
    "g" -> res.getString(R.string.ms_unit_g)
    "iu" -> res.getString(R.string.ms_unit_iu)
    "%" -> res.getString(R.string.ms_unit_percent)
    "cm" -> res.getString(R.string.unit_centimetres)
    "in" -> res.getString(R.string.unit_inches)
    "mm" -> res.getString(R.string.ms_unit_mm)
    "ml" -> res.getString(R.string.ms_unit_ml)
    "kcal" -> res.getString(R.string.ms_unit_kcal)
    else -> unit
}
