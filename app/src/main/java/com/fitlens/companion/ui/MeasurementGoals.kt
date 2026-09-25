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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.MeasurementGoals
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.ListRowWithMenu
import kotlinx.coroutines.launch

/**
 * The colour of a change between two values of a measurement with a goal (#27): gold when it moved the way the goal
 * wants, purple when it moved away, and plain otherwise. The signed number next to it still carries the meaning.
 */
@Composable
fun changeColour(def: MeasurementDef?, from: Double, to: Double): Color {
    val good = def?.let { MeasurementGoals.isImprovement(it.goalType, it.goalValue, from, to) }
    return when (good) {
        true -> Brand.Gold
        false -> Brand.PurpleLight
        null -> MaterialTheme.colorScheme.onSurface
    }
}

/** A short description of a measurement's goal, for the Body tab. */
fun goalText(def: MeasurementDef?): String = when (def?.goalType) {
    MeasurementGoals.INCREASE -> "Goal: increase" + if (def.goalValue > 0) " to ${fmtNum(def.goalValue)} ${def.unit}" else ""
    MeasurementGoals.DECREASE -> "Goal: decrease" + if (def.goalValue > 0) " to ${fmtNum(def.goalValue)} ${def.unit}" else ""
    MeasurementGoals.TARGET -> "Goal: ${fmtNum(def.goalValue)} ${def.unit}"
    else -> "No goal set"
}

/** Sets a measurement's goal: increase, decrease or a specific value, with an optional target for the first two. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeasurementGoalSheet(def: MeasurementDef, onDismiss: () -> Unit) {
    var type by remember { mutableIntStateOf(def.goalType.takeIf { it in MeasurementGoals.all } ?: MeasurementGoals.NONE) }
    var text by remember { mutableStateOf(if (def.goalValue > 0) fmtNum(def.goalValue) else "") }
    val value = text.trim().replace(',', '.').toDoubleOrNull()
    val needsValue = type == MeasurementGoals.TARGET
    FitSheet(
        title = "Goal for ${def.name}",
        onDismiss = onDismiss,
        confirmLabel = "Save goal",
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
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(MeasurementGoals.label(t)) })
            }
        }
        if (type != MeasurementGoals.NONE) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(if (needsValue) "Target (${def.unit})" else "Target (${def.unit}, optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            "Changes in the history are gold when they move towards the goal and purple when they move away. " +
                "A goal set here isn't replaced by a later FitNotes import.",
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
        title = "Order of measurements",
        onDismiss = onDismiss,
        confirmLabel = "Save order",
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
