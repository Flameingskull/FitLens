package com.fitlens.companion.ui

import androidx.compose.runtime.LaunchedEffect
import com.fitlens.companion.ui.design.GoldButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.ExerciseGoal
import com.fitlens.companion.data.GoalKinds
import com.fitlens.companion.data.GoalProgress
import com.fitlens.companion.data.Goals
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.MenuAction
import kotlinx.coroutines.launch

/** A goal's value in the display unit: weights in kg or lbs, times in minutes, counts as they are. */
internal fun goalShown(snap: Snapshot, kind: Int, v: Double, exerciseId: Long? = null): Double = when {
    GoalKinds.isWeight(kind) -> snap.weight(v, exerciseId)
    GoalKinds.isTime(kind) -> v / 60.0
    else -> v
}

internal fun goalUnit(res: Resources, snap: Snapshot, kind: Int, exerciseId: Long? = null): String = when {
    GoalKinds.isWeight(kind) -> snap.weightUnitOf(exerciseId)
    GoalKinds.isTime(kind) -> res.getString(R.string.an_unit_min)
    kind == GoalKinds.MAX_REPS -> res.getString(R.string.an_word_reps)
    else -> ""
}

/** The goal kind whose line belongs on the exercise graph called [graphLabel], or null (#25). */
internal fun goalKindForGraph(graphLabel: String): Int? = when (graphLabel) {
    GRAPH_E1RM -> GoalKinds.E1RM
    GRAPH_MAX_WEIGHT -> GoalKinds.MAX_WEIGHT
    GRAPH_WORKOUT_VOLUME -> GoalKinds.WORKOUT_VOLUME
    GRAPH_MAX_REPS -> GoalKinds.MAX_REPS
    GRAPH_MAX_VOLUME -> GoalKinds.SET_VOLUME
    GRAPH_LONGEST -> GoalKinds.LONGEST_SET
    GRAPH_DISTANCE -> GoalKinds.WORKOUT_DISTANCE
    else -> null
}

/**
 * An exercise's Goals tab (#25): each goal with a progress bar, the best so far and the date the target was reached.
 * Goals can be added, edited, deleted and moved up or down. Progress counts the same sets as records, so warm-ups
 * follow the setting (#43).
 */
@Composable
fun GoalsTab(
    snap: Snapshot,
    exId: Long,
    timeBased: Boolean,
    /** Set by the screen's + (#142): opens a new goal, then [onAddHandled] clears it. */
    addRequested: Boolean = false,
    onAddHandled: () -> Unit = {},
    /** The "Add a goal" button, for places without a + in their top bar (the overview sheet). */
    showAddButton: Boolean = true
) {
    val res = LocalContext.current.resources
    val goals = snap.goalsByExercise[exId].orEmpty()
    val sets = snap.statSetsByExercise[exId].orEmpty()
    var editing by remember { mutableStateOf<ExerciseGoal?>(null) }
    var deleting by remember { mutableStateOf<ExerciseGoal?>(null) }

    fun newGoal() = ExerciseGoal(0, exId, if (timeBased) GoalKinds.LONGEST_SET else GoalKinds.MAX_WEIGHT, 0.0, 0)
    LaunchedEffect(addRequested) {
        if (addRequested) {
            editing = newGoal()
            onAddHandled()
        }
    }

    fun move(i: Int, by: Int) {
        val list = goals.toMutableList()
        val j = i + by
        if (j !in list.indices) return
        list.add(j, list.removeAt(i))
        AppScope.scope.launch { Goals.reorder(list) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        if (goals.isEmpty()) {
            // FitNotes's words (#142).
            EmptyState(
                stringResource(R.string.goals_empty_title),
                stringResource(if (showAddButton) R.string.goals_empty_body else R.string.goals_empty_body_plus)
            )
        }
        goals.forEachIndexed { i, g ->
            val p = remember(sets, g) { GoalKinds.progress(g.kind, g.target, sets) }
            val (title, status) = goalText(res, snap, g, p)
            ListRowWithMenu(
                title = title,
                subtitle = status,
                onClick = { editing = g },
                menu = listOf(
                    MenuAction(stringResource(R.string.lib_edit)) { editing = g },
                    MenuAction(stringResource(R.string.lib_delete)) { deleting = g }
                ),
                onMoveUp = if (i > 0) { { move(i, -1) } } else null,
                onMoveDown = if (i < goals.lastIndex) { { move(i, 1) } } else null
            )
            val percentText = stringResource(R.string.goals_percent, (p.fraction(g.target) * 100).toInt())
            LinearProgressIndicator(
                progress = { p.fraction(g.target) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(6.dp)
                    .semantics { contentDescription = percentText },
                color = Brand.Gold,
                trackColor = Brand.Hairline
            )
        }
        if (showAddButton) {
            GoldButton(onClick = { editing = newGoal() }, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.ex_add_goal)) }
        }
        if (goals.isNotEmpty()) AnalysisNote(stringResource(R.string.goals_line_hint))
    }

    editing?.let { g -> GoalEditor(snap, g, timeBased) { editing = null } }
    deleting?.let { g ->
        ConfirmSheet(
            title = stringResource(R.string.goals_delete_title),
            message = stringResource(R.string.goals_delete_body, goalKindText(res, g.kind)),
            confirmLabel = stringResource(R.string.goals_delete_confirm),
            onDismiss = { deleting = null },
            onConfirm = { AppScope.scope.launch { Goals.delete(g.id) } }
        )
    }
}

/** A goal's title ("Max weight: 120 kg") and where it stands, for the goal lists (#25, #90). */
internal fun goalText(res: Resources, snap: Snapshot, g: ExerciseGoal, p: GoalProgress): Pair<String, String> {
    val unit = goalUnit(res, snap, g.kind, g.exerciseId)
    val target = "${fmtNum(goalShown(snap, g.kind, g.target, g.exerciseId), 1)} $unit".trim()
    val best = "${fmtNum(goalShown(snap, g.kind, p.best, g.exerciseId), 1)} $unit".trim()
    val status = when {
        p.achievedDate != null -> res.getString(R.string.goals_reached, Dates.medium(p.achievedDate))
        p.bestDate != null -> {
            val left = goalShown(snap, g.kind, g.target, g.exerciseId) - goalShown(snap, g.kind, p.best, g.exerciseId)
            if (left > 0) res.getString(R.string.goals_best_to_go, best, Dates.medium(p.bestDate), "${fmtNum(left, 1)} $unit".trim())
            else res.getString(R.string.goals_best, best, Dates.medium(p.bestDate))
        }
        else -> res.getString(R.string.goals_nothing_yet)
    }
    return res.getString(R.string.an_label_value, goalKindText(res, g.kind), target) to status
}

/** The goal editor (#25): its kind and target. Also opened from Analysis → Goals (#90). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalEditor(snap: Snapshot, goal: ExerciseGoal, timeBased: Boolean, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val kinds = if (timeBased) GoalKinds.timed else GoalKinds.strength
    var kind by remember { mutableIntStateOf(goal.kind) }
    var text by remember {
        mutableStateOf(if (goal.target > 0) fmtNum(goalShown(snap, goal.kind, goal.target, goal.exerciseId), 2) else "")
    }
    val value = text.trim().replace(',', '.').toDoubleOrNull()

    fun save() {
        val v = value ?: return
        val target = when {
            GoalKinds.isWeight(kind) -> snap.toKg(v, goal.exerciseId)
            GoalKinds.isTime(kind) -> v * 60.0
            else -> v
        }
        val g = goal.copy(kind = kind, target = target)
        AppScope.scope.launch { Goals.save(g) }
        onDismiss()
    }

    FitSheet(
        title = stringResource(if (goal.id == 0L) R.string.goals_new else R.string.goals_edit),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.goals_save),
        onConfirm = { save() },
        confirmEnabled = value != null && value > 0
    ) {
        Text(stringResource(R.string.goal_generic), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            kinds.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(goalKindText(res, k)) }) }
        }
        val unit = goalUnit(res, snap, kind, goal.exerciseId)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(if (unit.isBlank()) stringResource(R.string.goals_target) else stringResource(R.string.goals_target_unit, unit)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
