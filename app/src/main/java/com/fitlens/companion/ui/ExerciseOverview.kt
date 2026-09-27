package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.ui.design.FitTabRow

/**
 * The exercise overview (#26): one sheet with an exercise's History, Graph, Records, Stats and Goals, opened from the
 * calendar's selected day and the day log without leaving them. Open full screen goes to the matching screen: the
 * exercise screen's History or Graph tab for [date], or the exercise's details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseOverviewSheet(snap: Snapshot, nav: Nav, exId: Long, date: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sets = snap.setsByExercise[exId].orEmpty()
    val timeBased = isTimeBased(snap, exId, sets)
    // Records and stats leave out warm-ups unless Settings counts them (#43).
    val statSets = snap.statSetsByExercise[exId].orEmpty()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = FitShapes.sheet,
        containerColor = Brand.Onyx,
        contentColor = Brand.Ivory
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snap.exercises[exId]?.name ?: "Exercise",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = {
                    onDismiss()
                    nav.push(
                        when (tab) {
                            0 -> Screen.SetEntry(date, exId, page = 1)
                            1 -> Screen.SetEntry(date, exId, page = 2)
                            else -> Screen.ExerciseDetail(exId)
                        }
                    )
                }) { Text("Open full screen") }
            }
            FitTabRow(titles = listOf("History", "Graph", "Records", "Stats", "Goals"), selected = tab, onSelect = { tab = it })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> ExerciseHistoryPane(snap, nav, exId)
                    1 -> ExerciseGraphPane(snap, nav, exId)
                    2 -> RecordsTab(snap, statSets, timeBased)
                    3 -> ExerciseStatsTab(snap, statSets, timeBased)
                    else -> GoalsTab(snap, exId, timeBased)
                }
            }
        }
    }
}
