package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.ExerciseGoal
import com.fitlens.companion.data.GoalKinds
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.SearchablePicker

/** The exercise Analysis → Exercises showed last, kept between visits while the app is open (#90). */
private object AnalysisChoice {
    var exerciseId: Long? = null
}

/**
 * Analysis → Exercises (#90): choose an exercise, then any of its graphs (#22), with the same range, trend, from-zero,
 * goal and full-screen options as the exercise screen's Graph tab, because it is that pane.
 */
@Composable
fun AnalysisExercisesTab(snap: Snapshot, nav: Nav) {
    var exId by rememberSaveable { mutableStateOf(AnalysisChoice.exerciseId) }
    var picking by remember { mutableStateOf(false) }
    val chosen = exId?.takeIf { snap.exercises.containsKey(it) && snap.setsByExercise[it].orEmpty().isNotEmpty() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            FilterChip(
                selected = chosen != null,
                onClick = { picking = true },
                label = { Text(chosen?.let { snap.exercises[it]?.name } ?: "Choose an exercise…") }
            )
        }
        if (chosen == null) {
            EmptyState(
                "No exercise chosen",
                "Choose an exercise to see its graphs: estimated 1RM, max weight, volume, personal records and more."
            )
        } else {
            // A fresh pane per exercise, so it opens on that exercise's own default graph.
            Box(Modifier.weight(1f)) { key(chosen) { ExerciseGraphPane(snap, nav, chosen) } }
        }
    }
    if (picking) {
        val items = exercisePickerItems(snap).filter { snap.setsByExercise[it.id].orEmpty().isNotEmpty() }
        SearchablePicker(
            title = "Exercise",
            items = items,
            onDismiss = { picking = false },
            onPick = { ids ->
                ids.firstOrNull()?.let { exId = it; AnalysisChoice.exerciseId = it }
                picking = false
            },
            searchLabel = "Search exercises"
        )
    }
}

/**
 * Analysis → Goals (#90): every exercise goal with its progress, grouped by exercise. Tapping one opens that exercise's
 * Goals tab, where it can be changed. [adding] is the top bar's +: choose an exercise, then the goal editor.
 */
@Composable
fun AnalysisGoalsTab(snap: Snapshot, nav: Nav, adding: Boolean, onAddingDone: () -> Unit) {
    var editing by remember { mutableStateOf<ExerciseGoal?>(null) }
    val byExercise = remember(snap) {
        snap.goals.groupBy { it.exerciseId }.entries
            .filter { snap.exercises.containsKey(it.key) }
            .sortedBy { snap.exercises[it.key]?.name?.lowercase() }
    }
    if (byExercise.isEmpty()) {
        EmptyState("No training goals yet", "Tap + to set a target for any exercise, such as a heavier max or a bigger workout.")
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            byExercise.forEach { (exId, goals) ->
                item(key = "exercise-$exId") {
                    SectionTitle(snap.exercises[exId]?.name ?: "Exercise", Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp))
                }
                items(goals, key = { it.id }) { g ->
                    val sets = snap.statSetsByExercise[exId].orEmpty()
                    val p = remember(sets, g) { GoalKinds.progress(g.kind, g.target, sets) }
                    val (title, status) = goalText(snap, g, p)
                    ListRowWithMenu(
                        title = title,
                        subtitle = status,
                        onClick = { nav.push(Screen.ExerciseDetail(exId, tab = 2)) }
                    )
                    val fraction = p.fraction(g.target)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(6.dp)
                            .semantics { contentDescription = "${(fraction * 100).toInt()} percent of the goal" },
                        color = Brand.Gold,
                        trackColor = Brand.Hairline
                    )
                }
            }
        }
    }
    if (adding) {
        val items = exercisePickerItems(snap)
        SearchablePicker(
            title = "Add a goal for",
            items = items,
            onDismiss = onAddingDone,
            onPick = { ids ->
                ids.firstOrNull()?.let { id ->
                    val timed = isTimeBased(snap, id, snap.setsByExercise[id].orEmpty())
                    editing = ExerciseGoal(0, id, if (timed) GoalKinds.LONGEST_SET else GoalKinds.MAX_WEIGHT, 0.0, 0)
                }
                onAddingDone()
            },
            searchLabel = "Search exercises"
        )
    }
    editing?.let { g ->
        GoalEditor(snap, g, isTimeBased(snap, g.exerciseId, snap.setsByExercise[g.exerciseId].orEmpty())) { editing = null }
    }
}
