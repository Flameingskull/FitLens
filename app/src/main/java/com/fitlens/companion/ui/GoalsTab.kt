package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.ExerciseGoal
import com.fitlens.companion.data.GoalKinds
import com.fitlens.companion.data.Goals
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.MenuAction
import kotlinx.coroutines.launch

/** A goal's value in the display unit: weights in kg or lbs, times in minutes, counts as they are. */
internal fun goalShown(snap: Snapshot, kind: Int, v: Double): Double = when {
    GoalKinds.isWeight(kind) -> snap.weight(v)
    GoalKinds.isTime(kind) -> v / 60.0
    else -> v
}

internal fun goalUnit(snap: Snapshot, kind: Int): String = when {
    GoalKinds.isWeight(kind) -> snap.weightUnit
    GoalKinds.isTime(kind) -> "min"
    kind == GoalKinds.MAX_REPS -> "reps"
    else -> ""
}

/** The goal kind whose line belongs on the exercise graph called [graphLabel], or null (#25). */
internal fun goalKindForGraph(graphLabel: String): Int? = when (graphLabel) {
    "Est. 1RM" -> GoalKinds.E1RM
    "Max weight" -> GoalKinds.MAX_WEIGHT
    "Volume" -> GoalKinds.WORKOUT_VOLUME
    "Max reps" -> GoalKinds.MAX_REPS
    "Longest set" -> GoalKinds.LONGEST_SET
    "Distance" -> GoalKinds.WORKOUT_DISTANCE
    else -> null
}

/**
 * An exercise's Goals tab (#25): each goal with a progress bar, the best so far and the date the target was reached.
 * Goals can be added, edited, deleted and moved up or down. Progress counts the same sets as records, so warm-ups
 * follow the setting (#43).
 */
@Composable
fun GoalsTab(snap: Snapshot, exId: Long, timeBased: Boolean) {
    val goals = snap.goalsByExercise[exId].orEmpty()
    val sets = snap.statSetsByExercise[exId].orEmpty()
    var editing by remember { mutableStateOf<ExerciseGoal?>(null) }
    var deleting by remember { mutableStateOf<ExerciseGoal?>(null) }

    fun move(i: Int, by: Int) {
        val list = goals.toMutableList()
        val j = i + by
        if (j !in list.indices) return
        list.add(j, list.removeAt(i))
        AppScope.scope.launch { Goals.reorder(list) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        if (goals.isEmpty()) {
            EmptyState(
                "No goals yet",
                "Set a target, such as a heavier max or a bigger workout, and follow your progress towards it."
            )
        }
        goals.forEachIndexed { i, g ->
            val p = remember(sets, g) { GoalKinds.progress(g.kind, g.target, sets) }
            val unit = goalUnit(snap, g.kind)
            val target = "${fmtNum(goalShown(snap, g.kind, g.target), 1)} $unit".trim()
            val best = "${fmtNum(goalShown(snap, g.kind, p.best), 1)} $unit".trim()
            val status = when {
                p.achievedDate != null -> "Reached on ${Dates.medium(p.achievedDate)}"
                p.bestDate != null -> "Best so far $best on ${Dates.medium(p.bestDate)}"
                else -> "Nothing logged towards it yet"
            }
            ListRowWithMenu(
                title = "${GoalKinds.label(g.kind)}: $target",
                subtitle = status,
                onClick = { editing = g },
                menu = listOf(
                    MenuAction("Edit") { editing = g },
                    MenuAction("Delete") { deleting = g }
                ),
                onMoveUp = if (i > 0) { { move(i, -1) } } else null,
                onMoveDown = if (i < goals.lastIndex) { { move(i, 1) } } else null
            )
            val pct = (p.fraction(g.target) * 100).toInt()
            LinearProgressIndicator(
                progress = { p.fraction(g.target) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(6.dp)
                    .semantics { contentDescription = "$pct percent of the goal" },
                color = Brand.Gold,
                trackColor = Brand.Hairline
            )
        }
        Button(
            onClick = { editing = ExerciseGoal(0, exId, if (timeBased) GoalKinds.LONGEST_SET else GoalKinds.MAX_WEIGHT, 0.0, 0) },
            modifier = Modifier.padding(16.dp)
        ) { Text("Add a goal") }
        if (goals.isNotEmpty()) AnalysisNote("Turn on Goal under a matching graph to see the target as a line.")
    }

    editing?.let { g -> GoalEditor(snap, g, timeBased) { editing = null } }
    deleting?.let { g ->
        ConfirmSheet(
            title = "Delete this goal?",
            message = "${GoalKinds.label(g.kind)}. Your sets and records aren't affected.",
            confirmLabel = "Delete goal",
            onDismiss = { deleting = null },
            onConfirm = { AppScope.scope.launch { Goals.delete(g.id) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalEditor(snap: Snapshot, goal: ExerciseGoal, timeBased: Boolean, onDismiss: () -> Unit) {
    val kinds = if (timeBased) GoalKinds.timed else GoalKinds.strength
    var kind by remember { mutableIntStateOf(goal.kind) }
    var text by remember {
        mutableStateOf(if (goal.target > 0) fmtNum(goalShown(snap, goal.kind, goal.target), 2) else "")
    }
    val value = text.trim().replace(',', '.').toDoubleOrNull()

    fun save() {
        val v = value ?: return
        val target = when {
            GoalKinds.isWeight(kind) -> snap.toKg(v)
            GoalKinds.isTime(kind) -> v * 60.0
            else -> v
        }
        val g = goal.copy(kind = kind, target = target)
        AppScope.scope.launch { Goals.save(g) }
        onDismiss()
    }

    FitSheet(
        title = if (goal.id == 0L) "New goal" else "Edit goal",
        onDismiss = onDismiss,
        confirmLabel = "Save goal",
        onConfirm = { save() },
        confirmEnabled = value != null && value > 0
    ) {
        Text("Goal", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            kinds.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(GoalKinds.label(k)) }) }
        }
        val unit = goalUnit(snap, kind)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(if (unit.isBlank()) "Target" else "Target ($unit)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
