@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.fitlens.companion.data.Analysis
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Records
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.StatTile
import com.fitlens.companion.ui.design.StepperField
import kotlin.math.max

/** The periods the Stats tab offers, in days back from today; 0 is all time. */
private val STAT_PERIODS = listOf("All" to 0L, "1Y" to 365L, "3M" to 91L, "1M" to 30L)

/**
 * An exercise's Stats tab (#24, #89): best set, best estimated 1RM, heaviest weight, best workout volume, totals and
 * first and last logged, for a chosen period. Warm-ups follow the stats setting (#43), since [sets] are stat sets.
 */
@Composable
fun ExerciseStatsTab(snap: Snapshot, sets: List<SetRow>, timeBased: Boolean) {
    var period by rememberSaveable { mutableIntStateOf(0) }
    val days = STAT_PERIODS[period].second
    val shown = remember(sets, days) {
        if (days == 0L) sets else {
            val from = Dates.epochDay(Dates.today()) - days
            sets.filter { Dates.epochDay(it.date) >= from }
        }
    }
    val unit = snap.weightUnit
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            STAT_PERIODS.forEachIndexed { i, (label, _) ->
                FilterChip(selected = period == i, onClick = { period = i }, label = { Text(label) })
            }
        }
        if (shown.isEmpty()) {
            Text("Nothing logged in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val byDay = shown.groupBy { it.date }
        val sessions = byDay.size
        val tiles = ArrayList<Triple<String, String, String?>>()
        if (timeBased) {
            val longest = shown.maxBy { it.durationSec }
            val farthest = byDay.maxBy { e -> e.value.sumOf { it.distance } }
            tiles += Triple("Longest set", fmtDuration(longest.durationSec), Dates.medium(longest.date))
            tiles += Triple("Most distance in a workout", fmtNum(farthest.value.sumOf { it.distance }, 2), Dates.medium(farthest.key))
            tiles += Triple("Total time", fmtDuration(shown.sumOf { it.durationSec }), null)
            tiles += Triple("Total distance", fmtNum(shown.sumOf { it.distance }, 2), null)
        } else {
            val heaviest = shown.maxBy { it.weightKg }
            val best1rm = shown.maxBy { Records.oneRepMax(it) }
            val bestSet = shown.maxBy { Analysis.volumeKg(it) }
            val bestDay = byDay.maxBy { e -> e.value.sumOf { Analysis.volumeKg(it) } }
            tiles += Triple("Heaviest weight", "${snap.fmtWeight(heaviest.weightKg)} $unit × ${heaviest.reps}", Dates.medium(heaviest.date))
            tiles += Triple("Best est. 1RM", "${snap.fmtWeight(Records.oneRepMax(best1rm))} $unit", Dates.medium(best1rm.date))
            tiles += Triple("Best set (volume)", "${snap.fmtWeight(bestSet.weightKg)} $unit × ${bestSet.reps}", Dates.medium(bestSet.date))
            tiles += Triple(
                "Best workout volume",
                "${fmtNum(snap.weight(bestDay.value.sumOf { Analysis.volumeKg(it) }), 0)} $unit",
                Dates.medium(bestDay.key)
            )
            tiles += Triple("Total reps", "${shown.sumOf { it.reps }}", null)
            tiles += Triple("Total volume", "${fmtNum(snap.weight(shown.sumOf { Analysis.volumeKg(it) }), 0)} $unit", null)
        }
        tiles += Triple("Workouts", "$sessions", null)
        tiles += Triple("Sets", "${shown.size}", "${fmtNum(shown.size.toDouble() / max(1, sessions), 1)} per workout")
        tiles += Triple("First logged", Dates.medium(shown.minOf { it.date }), null)
        tiles += Triple("Last logged", Dates.medium(shown.maxOf { it.date }), null)
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { (label, value, line) ->
                    StatTile(label = label, value = value, modifier = Modifier.weight(1f), dateLine = line)
                }
                if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The 1RM calculator (#28): a weight and reps give an estimated one-rep max with FitLens's formula (#23), and a table
 * of what that means for 1 to 12 reps and for percentages of it, in the user's unit.
 */
@Composable
fun OneRepMaxSheet(snap: Snapshot, start: SetRow?, onDismiss: () -> Unit) {
    val unit = snap.weightUnit
    var weight by remember { mutableStateOf(start?.weightKg?.takeIf { it > 0 }?.let { fmtNum(snap.weight(it), 2) } ?: "") }
    var reps by remember { mutableStateOf(start?.reps?.takeIf { it > 0 }?.toString() ?: "5") }
    val kg = snap.toKg(weight.trim().replace(',', '.').toDoubleOrNull() ?: 0.0)
    val r = reps.trim().toIntOrNull() ?: 0
    val oneRm = Records.oneRepMax(kg, r)
    FitSheet(title = "1RM calculator", onDismiss = onDismiss, dismissLabel = "Close") {
        StepperField(
            label = "Weight ($unit)",
            value = weight,
            onValue = { weight = it },
            onStep = { d -> weight = fmtNum(max(0.0, (weight.trim().replace(',', '.').toDoubleOrNull() ?: 0.0) + d * 2.5), 2) }
        )
        StepperField(
            label = "Reps",
            value = reps,
            onValue = { reps = it },
            onStep = { d -> reps = max(1, (reps.trim().toIntOrNull() ?: 0) + d).toString() },
            keyboard = KeyboardType.Number
        )
        Text(
            if (oneRm > 0) "${snap.fmtWeight(oneRm)} $unit" else "—",
            Modifier.semantics { contentDescription = if (oneRm > 0) "Estimated one rep max ${snap.fmtWeight(oneRm)} $unit" else "Enter a weight and 1 to 20 reps" },
            style = MaterialTheme.typography.displaySmall,
            color = Brand.Gold
        )
        Text("ESTIMATED ONE-REP MAX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (oneRm > 0) {
            GoldHairline()
            Text("REP MAXES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (1..12).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { n ->
                        Text("${n}RM  ${snap.fmtWeight(Records.weightFor(oneRm, n))}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            GoldHairline()
            Text("PERCENTAGES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (100 downTo 50 step 5).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { pct ->
                        Text("$pct%  ${snap.fmtWeight(oneRm * pct / 100.0)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                    repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
            Text("Weights in $unit. Estimates are most reliable from sets of 10 reps or fewer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
