@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import androidx.compose.foundation.clickable
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
import com.fitlens.companion.data.DistanceUnits
import com.fitlens.companion.data.Records
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.DateRangePickerDialog
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.StatTile
import com.fitlens.companion.ui.design.StepperField
import kotlin.math.max
import kotlin.math.roundToInt
import com.fitlens.companion.ui.design.PeriodDropdown

/** The periods the Stats tab offers, in days back from today; 0 is all time. Custom follows them (#24). */
private val STAT_PERIODS = listOf("All time" to 0L, "Last year" to 365L, "Last 3 months" to 91L, "Last month" to 30L)

/** One Stats tile: its value, a line under it, and the day it happened, which a tap opens (#24). */
private data class StatItem(val label: String, val value: String, val line: String? = null, val date: String? = null)

/**
 * An exercise's Stats tab (#24, #89), with FitNotes's tiles: max weight, estimated 1RM, max reps, max volume (the best
 * single set), workout reps and workout volume (the best day), each with its date, then the totals and first and last
 * logged, for a period or a custom date range. A tile with a date opens that day on the exercise screen. Warm-ups
 * follow the stats setting (#43), since [sets] are stat sets.
 */
@Composable
fun ExerciseStatsTab(snap: Snapshot, nav: Nav, exId: Long, sets: List<SetRow>, timeBased: Boolean) {
    // -1 is the Custom range, customFrom..customTo, kept while the screen is open.
    var period by rememberSaveable { mutableIntStateOf(0) }
    var customFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var customTo by rememberSaveable { mutableStateOf<String?>(null) }
    val from = customFrom
    val to = customTo
    val shown = remember(sets, period, from, to) {
        val days = STAT_PERIODS.getOrNull(period)?.second
        when {
            days == null -> if (from != null && to != null) Records.between(sets, from, to) else sets
            days == 0L -> sets
            else -> {
                val start = Dates.epochDay(Dates.today()) - days
                sets.filter { Dates.epochDay(it.date) >= start }
            }
        }
    }
    val unit = snap.weightUnitOf(exId)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PeriodDropdown(
            label = "Period",
            options = STAT_PERIODS.map { it.first },
            selected = period,
            custom = if (from != null && to != null) from to to else null,
            onSelect = { period = it },
            onCustom = { f, t -> customFrom = f; customTo = t; period = -1 }
        )
        if (shown.isEmpty()) {
            Text("Nothing logged in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val byDay = shown.groupBy { it.date }
        val sessions = byDay.size
        val tiles = ArrayList<StatItem>()
        fun dated(label: String, value: String, date: String) = StatItem(label, value, Dates.medium(date), date)
        if (timeBased) {
            val longest = shown.maxBy { it.durationSec }
            val farthest = byDay.maxBy { e -> e.value.sumOf { it.distance } }
            val longestDay = byDay.maxBy { e -> e.value.sumOf { it.durationSec } }
            tiles += dated("Longest set", fmtDuration(longest.durationSec), longest.date)
            tiles += dated("Longest workout", fmtDuration(longestDay.value.sumOf { it.durationSec }), longestDay.key)
            // Distances are in the exercise's unit (#7); pace needs a set with both a distance and a time (#24).
            val dUnit = snap.distanceUnit(exId)
            val farthestSet = shown.maxBy { it.distance }
            if (farthestSet.distance > 0) {
                tiles += dated("Longest distance", "${fmtNum(farthestSet.distance, 2)} $dUnit", farthestSet.date)
                tiles += dated("Most distance in a workout", "${fmtNum(farthest.value.sumOf { it.distance }, 2)} $dUnit", farthest.key)
            }
            val paced = shown.filter { it.distance > 0 && it.durationSec > 0 }
            if (paced.isNotEmpty()) {
                val fastest = paced.minBy { it.durationSec / it.distance }
                tiles += dated("Best pace", pace(fastest.durationSec.toDouble(), fastest.distance, dUnit), fastest.date)
                tiles += StatItem(
                    "Average pace",
                    pace(paced.sumOf { it.durationSec }.toDouble(), paced.sumOf { it.distance }, dUnit)
                )
            }
            tiles += StatItem("Total time", fmtDuration(shown.sumOf { it.durationSec }))
            if (farthestSet.distance > 0) tiles += StatItem("Total distance", "${fmtNum(shown.sumOf { it.distance }, 2)} $dUnit")
        } else {
            val heaviest = shown.maxBy { it.weightKg }
            val best1rm = shown.maxBy { Records.oneRepMax(it) }
            val mostReps = shown.maxBy { it.reps }
            val bestSet = shown.maxBy { Analysis.volumeKg(it) }
            val bestDay = byDay.maxBy { e -> e.value.sumOf { Analysis.volumeKg(it) } }
            val repsDay = byDay.maxBy { e -> e.value.sumOf { it.reps } }
            tiles += dated("Max weight", "${snap.fmtWeight(heaviest.weightKg, exId)} $unit × ${heaviest.reps}", heaviest.date)
            // An estimate from more than 10 reps is marked approximate (#139).
            val approx = if (Records.approximate(best1rm.reps)) "≈ " else ""
            tiles += dated("Estimated 1RM", "$approx${snap.fmtWeight(Records.oneRepMax(best1rm), exId)} $unit", best1rm.date)
            tiles += dated("Max reps", "${mostReps.reps} × ${snap.fmtWeight(mostReps.weightKg, exId)} $unit", mostReps.date)
            tiles += dated("Max volume", "${snap.fmtWeight(bestSet.weightKg, exId)} $unit × ${bestSet.reps}", bestSet.date)
            tiles += dated("Workout reps", "${repsDay.value.sumOf { it.reps }}", repsDay.key)
            tiles += dated("Workout volume", "${fmtNum(snap.weight(bestDay.value.sumOf { Analysis.volumeKg(it) }, exId), 0)} $unit", bestDay.key)
            tiles += StatItem("Total reps", "${shown.sumOf { it.reps }}")
            tiles += StatItem("Total volume", "${fmtNum(snap.weight(shown.sumOf { Analysis.volumeKg(it) }, exId), 0)} $unit")
        }
        tiles += StatItem("Workouts", "$sessions")
        tiles += StatItem("Sets", "${shown.size}", "${fmtNum(shown.size.toDouble() / max(1, sessions), 1)} per workout")
        val first = shown.minOf { it.date }
        val last = shown.maxOf { it.date }
        tiles += StatItem("First logged", Dates.medium(first), date = first)
        tiles += StatItem("Last logged", Dates.medium(last), date = last)
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { t ->
                    val open = t.date?.let { d ->
                        Modifier.clickable(onClickLabel = "Open ${Dates.long(d)}") { nav.push(Screen.SetEntry(d, exId)) }
                    } ?: Modifier
                    StatTile(label = t.label, value = t.value, modifier = Modifier.weight(1f).then(open), dateLine = t.line)
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
    val unit = snap.weightUnitOf(start?.exerciseId)
    var weight by remember { mutableStateOf(start?.weightKg?.takeIf { it > 0 }?.let { fmtNum(snap.weight(it, start?.exerciseId), 2) } ?: "") }
    var reps by remember { mutableStateOf(start?.reps?.takeIf { it > 0 }?.toString() ?: "5") }
    val kg = snap.toKg(weight.trim().replace(',', '.').toDoubleOrNull() ?: 0.0, start?.exerciseId)
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
            if (oneRm > 0) (if (Records.approximate(r)) "≈ " else "") + "${snap.fmtWeight(oneRm, start?.exerciseId)} $unit" else "—",
            Modifier.semantics {
                contentDescription = if (oneRm > 0) (if (Records.approximate(r)) "About " else "Estimated one rep max ") +
                    "${snap.fmtWeight(oneRm, start?.exerciseId)} $unit" else "Enter a weight and 1 to ${Records.MAX_ESTIMATE_REPS} reps"
            },
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
                        Text("${n}RM  ${snap.fmtWeight(Records.weightFor(oneRm, n), start?.exerciseId)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            GoldHairline()
            Text("PERCENTAGES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (100 downTo 50 step 5).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { pct ->
                        Text("$pct%  ${snap.fmtWeight(oneRm * pct / 100.0, start?.exerciseId)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                    repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
            Text("Weights in $unit. Estimates are most reliable from sets of 10 reps or fewer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Time per distance, as runners read it (#24): "5:12 /km" or "8:30 /mi". Metres are paced per 100 m, as swimmers
 * count them.
 */
internal fun pace(seconds: Double, distance: Double, unit: String): String {
    if (distance <= 0) return "—"
    val per = if (unit == DistanceUnits.M) 100.0 else 1.0
    val secs = (seconds / distance * per).roundToInt()
    return "${fmtDuration(secs)} /${if (unit == DistanceUnits.M) "100 m" else unit}"
}
