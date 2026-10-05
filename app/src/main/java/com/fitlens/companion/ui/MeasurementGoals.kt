package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.MeasurementGoals
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.ListRowWithMenu
import kotlinx.coroutines.launch

/**
 * The colour of a change between two values of a measurement with a goal (#27): green when it moved the way the goal
 * wants and red when it moved away (1.0.71). Without a goal it is green for a rise and red for a fall, plain when
 * unchanged. The signed number next to it still carries the meaning.
 */
@Composable
fun changeColour(def: MeasurementDef?, from: Double, to: Double): Color {
    val good = def?.let { MeasurementGoals.isImprovement(it.goalType, it.goalValue, from, to) }
    return when (good) {
        true -> Brand.Rise
        false -> Brand.Fall
        null -> deltaColour(to - from, MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * A change between two values of a measurement, in full figures (#120) and short enough to sit on one line (#128):
 * the direction, the amount in its unit, since when, and the value it moved from, "▼ 0.7 kg since 21 Aug · was
 * 113.25 kg". An unchanged value reads "No change since 18 Sept". Never a bare number or a percentage.
 */
fun changeText(res: Resources, prev: MRecord, now: MRecord): String {
    val d = now.value - prev.value
    val unit = now.unit.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
    val since = Dates.short(prev.date)
    if (d == 0.0) return res.getString(R.string.mg_no_change, since)
    val arrow = if (d > 0) "▲" else "▼"
    return res.getString(R.string.mg_change, arrow, "${fmtNum(kotlin.math.abs(d))}$unit", since, "${fmtNum(prev.value)}$unit")
}

/** A short description of a measurement's goal, for the Body tab. */
fun goalText(res: Resources, def: MeasurementDef?): String {
    val target = def?.let { "${fmtNum(it.goalValue)} ${it.unit}".trim() }.orEmpty()
    return when (def?.goalType) {
        MeasurementGoals.INCREASE -> if (def.goalValue > 0) res.getString(R.string.mg_increase_to, target) else res.getString(R.string.mg_increase)
        MeasurementGoals.DECREASE -> if (def.goalValue > 0) res.getString(R.string.mg_decrease_to, target) else res.getString(R.string.mg_decrease)
        MeasurementGoals.TARGET -> res.getString(R.string.mg_target, target)
        else -> res.getString(R.string.mg_none)
    }
}

/** A goal type's name on its chip: "Increase", "Specific value". */
private fun goalTypeText(res: Resources, t: Int): String = res.getString(
    when (t) {
        MeasurementGoals.INCREASE -> R.string.mg_type_increase
        MeasurementGoals.DECREASE -> R.string.mg_type_decrease
        MeasurementGoals.TARGET -> R.string.mg_type_target
        else -> R.string.mg_type_none
    }
)

/** Sets a measurement's goal: increase, decrease or a specific value, with an optional target for the first two. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeasurementGoalSheet(def: MeasurementDef, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    var type by remember { mutableIntStateOf(def.goalType.takeIf { it in MeasurementGoals.all } ?: MeasurementGoals.NONE) }
    var text by remember { mutableStateOf(if (def.goalValue > 0) fmtNum(def.goalValue) else "") }
    val value = text.trim().replace(',', '.').toDoubleOrNull()
    val needsValue = type == MeasurementGoals.TARGET
    FitSheet(
        title = stringResource(R.string.mg_title, def.name),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.goals_save),
        confirmEnabled = !needsValue || (value != null && value > 0),
        onConfirm = {
            val t = type
            val v = if (t == MeasurementGoals.NONE) 0.0 else value ?: 0.0
            AppScope.scope.launch { Store.setMeasurementGoal(def.name, def.unit, t, v) }
            onDismiss()
        }
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MeasurementGoals.all.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(goalTypeText(res, t)) })
            }
        }
        if (type != MeasurementGoals.NONE) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(if (needsValue) R.string.goals_target_unit else R.string.mg_target_optional, def.unit)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            stringResource(R.string.mg_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Moves measurements up and down. The order is used by the Body tab, the day view, videos and the PDF report. */
@Composable
fun MeasurementOrderSheet(measurements: List<MeasurementDef>, onDismiss: () -> Unit) {
    var order by remember { mutableStateOf(measurements.map { it.name }) }
    fun move(i: Int, by: Int) {
        val j = i + by
        if (j !in order.indices) return
        order = order.toMutableList().also { it.add(j, it.removeAt(i)) }
    }
    FitSheet(
        title = stringResource(R.string.mg_order_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.mg_order_save),
        onConfirm = {
            val names = order
            val units = measurements.associate { it.name to it.unit }
            AppScope.scope.launch { Store.reorderMeasurements(names, units) }
            onDismiss()
        }
    ) {
        order.forEachIndexed { i, name ->
            ListRowWithMenu(
                title = name,
                onMoveUp = if (i > 0) { { move(i, -1) } } else null,
                onMoveDown = if (i < order.lastIndex) { { move(i, 1) } } else null
            )
        }
    }
}
