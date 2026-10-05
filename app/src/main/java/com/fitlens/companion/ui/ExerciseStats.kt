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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.data.Settings
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.Alignment
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
private val STAT_PERIODS = listOf(R.string.an_all_time to 0L, R.string.st_last_year to 365L, R.string.st_last_3_months to 91L, R.string.st_last_month to 30L)

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
    val res = LocalContext.current.resources
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
            label = stringResource(R.string.ex_period),
            options = STAT_PERIODS.map { stringResource(it.first) },
            selected = period,
            custom = if (from != null && to != null) from to to else null,
            onSelect = { period = it },
            onCustom = { f, t -> customFrom = f; customTo = t; period = -1 }
        )
        if (shown.isEmpty()) {
            Text(stringResource(R.string.st_nothing), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val byDay = shown.groupBy { it.date }
        val sessions = byDay.size
        val tiles = ArrayList<StatItem>()
        fun dated(label: Int, value: String, date: String) = StatItem(res.getString(label), value, Dates.medium(date), date)
        fun item(label: Int, value: String, line: String? = null) = StatItem(res.getString(label), value, line)
        val perWorkout = res.getString(R.string.st_per_workout, fmtNum(shown.size.toDouble() / max(1, sessions), 1))
        if (timeBased) {
            val longest = shown.maxBy { it.durationSec }
            val farthest = byDay.maxBy { e -> e.value.sumOf { it.distance } }
            val longestDay = byDay.maxBy { e -> e.value.sumOf { it.durationSec } }
            tiles += dated(R.string.goal_longest_set, fmtDuration(longest.durationSec), longest.date)
            tiles += dated(R.string.st_longest_workout, fmtDuration(longestDay.value.sumOf { it.durationSec }), longestDay.key)
            // Distances are in the exercise's unit (#7); pace needs a set with both a distance and a time (#24).
            val dUnit = snap.distanceUnit(exId)
            val farthestSet = shown.maxBy { it.distance }
            if (farthestSet.distance > 0) {
                tiles += dated(R.string.st_longest_distance, "${fmtNum(farthestSet.distance, 2)} $dUnit", farthestSet.date)
                tiles += dated(R.string.st_most_distance, "${fmtNum(farthest.value.sumOf { it.distance }, 2)} $dUnit", farthest.key)
            }
            val paced = shown.filter { it.distance > 0 && it.durationSec > 0 }
            if (paced.isNotEmpty()) {
                val fastest = paced.minBy { it.durationSec / it.distance }
                tiles += dated(R.string.st_best_pace, pace(fastest.durationSec.toDouble(), fastest.distance, dUnit), fastest.date)
                tiles += item(
                    R.string.st_average_pace,
                    pace(paced.sumOf { it.durationSec }.toDouble(), paced.sumOf { it.distance }, dUnit)
                )
            }
            tiles += item(R.string.st_total_time, fmtDuration(shown.sumOf { it.durationSec }))
            if (farthestSet.distance > 0) tiles += item(R.string.st_total_distance, "${fmtNum(shown.sumOf { it.distance }, 2)} $dUnit")
        } else {
            val heaviest = shown.maxBy { it.weightKg }
            val best1rm = shown.maxBy { Records.oneRepMax(it) }
            val mostReps = shown.maxBy { it.reps }
            val bestSet = shown.maxBy { Analysis.volumeKg(it) }
            val bestDay = byDay.maxBy { e -> e.value.sumOf { Analysis.volumeKg(it) } }
            val repsDay = byDay.maxBy { e -> e.value.sumOf { it.reps } }
            // FitNotes's tiles in FitNotes's order (#142): the totals, then each best with its date.
            tiles += item(R.string.st_total_workouts, "$sessions")
            tiles += item(R.string.st_total_sets, "${shown.size}", perWorkout)
            tiles += item(R.string.st_total_reps, "${shown.sumOf { it.reps }}")
            tiles += item(R.string.st_total_volume, "${fmtNum(snap.weight(shown.sumOf { Analysis.volumeKg(it) }, exId), 0)} $unit")
            tiles += dated(R.string.goal_max_weight, "${snap.fmtWeight(heaviest.weightKg, exId)} $unit", heaviest.date)
            // An estimate from more than 10 reps is marked approximate (#139).
            val approx = if (Records.approximate(best1rm.reps)) "≈ " else ""
            tiles += dated(R.string.goal_e1rm, "$approx${snap.fmtWeight(Records.oneRepMax(best1rm), exId)} $unit", best1rm.date)
            tiles += dated(R.string.st_max_reps, "${mostReps.reps}", mostReps.date)
            tiles += dated(R.string.st_workout_reps, "${repsDay.value.sumOf { it.reps }}", repsDay.key)
            tiles += dated(R.string.st_max_volume, "${fmtNum(snap.weight(Analysis.volumeKg(bestSet), exId), 0)} $unit", bestSet.date)
            tiles += dated(R.string.st_workout_volume, "${fmtNum(snap.weight(bestDay.value.sumOf { Analysis.volumeKg(it) }, exId), 0)} $unit", bestDay.key)
        }
        if (timeBased) {
            tiles += item(R.string.an_metric_workouts, "$sessions")
            tiles += item(R.string.an_metric_sets, "${shown.size}", perWorkout)
        }
        val first = shown.minOf { it.date }
        val last = shown.maxOf { it.date }
        tiles += StatItem(res.getString(R.string.st_first_logged), Dates.medium(first), date = first)
        tiles += StatItem(res.getString(R.string.st_last_logged), Dates.medium(last), date = last)
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { t ->
                    val open = t.date?.let { d ->
                        Modifier.clickable(onClickLabel = res.getString(R.string.st_open_day, Dates.long(d))) { nav.push(Screen.SetEntry(d, exId)) }
                    } ?: Modifier
                    StatTile(label = t.label, value = t.value, modifier = Modifier.weight(1f).then(open), dateLine = t.line)
                }
                if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The Estimated 1RM Calculator, as FitNotes's (#28, #148): WEIGHT and REPS steppers, then 1RM to 8RM, each with its
 * weight and its percentage of the 1RM under it. FitLens adds 9RM to 12RM (marked approximate past 10 reps) and a
 * percentage table. The estimate uses the formula and rep limit chosen in Settings (#42, #148).
 */
@Composable
fun OneRepMaxSheet(snap: Snapshot, start: SetRow?, onDismiss: () -> Unit) {
    val exId = start?.exerciseId
    val unit = snap.weightUnitOf(exId)
    val ex = exId?.let { snap.exercises[it] }
    // The exercise's own step, then the global one when the units agree, as on the Track tab.
    val globalStepKg = Settings.currentPortable().weightIncrementKg?.takeIf { snap.weightUnitOf(exId) == snap.weightUnit }
    val step = (ex?.weightStepKg ?: globalStepKg)?.let { snap.weight(it, exId) } ?: 2.5
    var weight by remember { mutableStateOf(start?.weightKg?.takeIf { it > 0 }?.let { fmtNum(snap.weight(it, exId), 2) } ?: "") }
    var reps by remember { mutableStateOf(start?.reps?.takeIf { it > 0 }?.toString() ?: "5") }
    val kg = snap.toKg(weight.trim().replace(',', '.').toDoubleOrNull() ?: 0.0, exId)
    val r = reps.trim().toIntOrNull() ?: 0
    val oneRm = Records.oneRepMax(kg, r)
    FitSheet(title = stringResource(R.string.st_calc_title), onDismiss = onDismiss, dismissLabel = stringResource(R.string.st_ok)) {
        StepperField(
            label = stringResource(R.string.st_weight_unit, unit),
            value = weight,
            onValue = { weight = it },
            onStep = { d -> weight = fmtNum(max(0.0, (weight.trim().replace(',', '.').toDoubleOrNull() ?: 0.0) + d * step), 2) }
        )
        StepperField(
            label = stringResource(R.string.ex_reps),
            value = reps,
            onValue = { reps = it },
            onStep = { d -> reps = max(1, (reps.trim().toIntOrNull() ?: 0) + d).toString() },
            keyboard = KeyboardType.Number
        )
        GoldHairline()
        if (oneRm <= 0) {
            Text(
                stringResource(R.string.st_calc_enter, Records.maxRepsFor()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            (1..12).forEach { n ->
                val w = Records.weightFor(oneRm, n)
                if (w > 0) RepMaxLine(n, snap.fmtWeight(w, exId), unit, w / oneRm * 100)
            }
            SectionLabel(stringResource(R.string.st_calc_percentages))
            (100 downTo 50 step 5).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { pct ->
                        Text("$pct%  ${snap.fmtWeight(oneRm * pct / 100.0, exId)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                    repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
            Text(
                stringResource(R.string.st_calc_note, unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One calculator line, as FitNotes lays it out: "3RM" on the left, the weight with the % of 1RM under it on the right. */
@Composable
private fun RepMaxLine(reps: Int, weight: String, unit: String, percent: Double) {
    val approx = if (Records.approximate(reps)) "≈ " else ""
    val spoken = stringResource(
        if (approx.isEmpty()) R.string.st_rm_spoken else R.string.st_rm_spoken_about, reps, "$weight $unit", fmtNum(percent, 1)
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.ex_record_rm, reps), Modifier.weight(1f).padding(start = Spacing.lg), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$approx$weight", style = MaterialTheme.typography.titleLarge, color = Brand.Gold)
                Text(" $unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${String.format(java.util.Locale.US, "%.1f", percent)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * FitNotes's Estimated 1RM Settings (#148): the most reps a set can have to be estimated from. Cancel, Reset (back to
 * the formula's own limit) and OK. Records, graphs and the calculator follow at once; logged sets never change.
 */
@Composable
fun EstimatedOneRmSettingsSheet(onDismiss: () -> Unit) {
    val formula = Records.chosen()
    val current = Settings.currentPortable().e1rmMaxReps
    var reps by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    val typed = reps.trim().toIntOrNull()
    val valid = reps.isBlank() || (typed != null && typed in 1..formula.maxReps)
    fun save(value: Int) {
        Settings.updatePortable { it.copy(e1rmMaxReps = value) }
        onDismiss()
    }
    FitSheet(
        title = stringResource(R.string.st_e1rm_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.st_ok),
        confirmEnabled = valid,
        onConfirm = { save(typed ?: 0) },
        secondaryLabel = stringResource(R.string.st_reset),
        onSecondary = { save(0) }
    ) {
        Text(
            stringResource(R.string.st_e1rm_body),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            stringResource(R.string.st_e1rm_hint, formula.label, formula.maxReps),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StepperField(
            label = stringResource(R.string.ex_reps),
            value = reps,
            onValue = { reps = it.filter { c -> c.isDigit() }.take(2) },
            onStep = { d ->
                val base = typed ?: formula.maxReps
                reps = (base + d).coerceIn(1, formula.maxReps).toString()
            },
            keyboard = KeyboardType.Number
        )
        if (!valid) {
            Text(
                stringResource(R.string.st_e1rm_invalid, formula.maxReps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
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
