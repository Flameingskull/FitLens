package com.fitlens.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Records
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.SegmentedSwitch
import com.fitlens.companion.ui.design.StepperField
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

/** Rounds [v] to the nearest multiple of [step] (both in the display unit); a step of 0 leaves it as it is. */
internal fun roundTo(v: Double, step: Double): Double = if (step <= 0) v else round(v / step) * step

private fun parse(t: String): Double? = t.trim().replace(',', '.').toDoubleOrNull()

/** The plates FitLens offers until the user edits the list, in the display unit (#28). */
fun defaultPlates(unit: String): List<Double> =
    if (unit == "lbs") listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5) else listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

/** The bar FitLens assumes until the user sets one, in the display unit. */
fun defaultBar(unit: String): Double = if (unit == "lbs") 45.0 else 20.0

/**
 * The plates for one side of the bar to reach [target] (#28), heaviest first, from [plates] (as many of each as
 * needed). With [countBar] the bar's weight is part of the target. Everything is in the display unit. Returns the
 * plates and the weight they actually make, which is less than [target] when it can't be made exactly.
 */
fun platesPerSide(target: Double, bar: Double, countBar: Boolean, plates: List<Double>): Pair<List<Double>, Double> {
    val base = if (countBar) bar else 0.0
    var side = (target - base) / 2
    val out = ArrayList<Double>()
    if (side > 0) {
        plates.filter { it > 0 }.sortedDescending().forEach { p ->
            val n = floor((side + 1e-6) / p).toInt()
            repeat(n) { out += p }
            side -= n * p
        }
    }
    return out to base + out.sum() * 2
}

/**
 * The set calculator (#28): weights as percentages of a one-rep max, or a warm-up ramp to a target, rounded to the
 * exercise's step. Every row has Use, which fills in the current set's weight (and reps, for warm-ups).
 */
@Composable
fun SetCalculatorSheet(
    snap: Snapshot,
    bestOneRmKg: Double,
    targetText: String,
    stepShown: Double,
    onUse: (weightText: String, reps: Int?) -> Unit,
    onDismiss: () -> Unit,
    /** The exercise it was opened from, whose own weight unit applies (#7). */
    exerciseId: Long? = null
) {
    val unit = snap.weightUnitOf(exerciseId)
    var mode by remember { mutableStateOf(if (bestOneRmKg > 0) 0 else 1) }
    var base by remember(mode) {
        mutableStateOf(
            if (mode == 0) bestOneRmKg.takeIf { it > 0 }?.let { fmtNum(roundTo(snap.weight(it, exerciseId), stepShown), 2) } ?: ""
            else targetText
        )
    }
    val baseShown = parse(base) ?: 0.0
    FitSheet(title = "Set calculator", onDismiss = onDismiss, dismissLabel = "Close") {
        SegmentedSwitch(options = listOf("% of 1RM", "Warm-up to a target"), selected = mode, onSelect = { mode = it })
        StepperField(
            label = if (mode == 0) "One-rep max ($unit)" else "Target weight ($unit)",
            value = base,
            onValue = { base = it },
            onStep = { d -> base = fmtNum(max(0.0, baseShown + d * stepShown), 2) }
        )
        if (baseShown <= 0) {
            Text("Enter a weight to see the sets.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (mode == 0) {
            val oneRmKg = snap.toKg(baseShown, exerciseId)
            (100 downTo 50 step 5).forEach { pct ->
                val w = roundTo(baseShown * pct / 100.0, stepShown)
                // The most reps that weight should allow, by the chosen formula (#42).
                val reps = (1..Records.MAX_REPS).lastOrNull { Records.weightFor(oneRmKg, it) >= snap.toKg(w, exerciseId) - 0.01 }
                CalcRow("$pct%", "${fmtNum(w, 2)} $unit", reps?.let { "about $it reps" }) { onUse(fmtNum(w, 2), null) }
            }
            Text(
                "Weights are rounded to ${fmtNum(stepShown, 2)} $unit. Reps are estimated with your 1RM formula.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // A common ramp: lighter sets for more reps, building to the working weight.
            listOf(40 to 8, 60 to 5, 75 to 3, 85 to 2, 100 to 0).forEach { (pct, reps) ->
                val w = roundTo(baseShown * pct / 100.0, stepShown)
                val label = if (pct == 100) "Work set" else "$pct%"
                CalcRow(label, "${fmtNum(w, 2)} $unit", if (reps > 0) "× $reps" else null) { onUse(fmtNum(w, 2), reps.takeIf { it > 0 }) }
            }
            Text(
                "A warm-up ramp to your working weight. Weights are rounded to ${fmtNum(stepShown, 2)} $unit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalcRow(label: String, value: String, note: String?, onUse: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.touch).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.width(88.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onUse, modifier = Modifier.semantics { contentDescription = "Use $value" }) { Text("Use") }
    }
}

/**
 * The plate calculator (#28): a target weight, the bar (counted or not) and the plates on hand give the plates for
 * each side, drawn as a bar end. The bar, the count-the-bar choice and the plate list are remembered and travel with
 * backups. Use fills in the current set with the weight actually loaded.
 */
@Composable
fun PlateCalculatorSheet(
    snap: Snapshot,
    targetText: String,
    onUse: (weightText: String) -> Unit,
    onDismiss: () -> Unit,
    exerciseId: Long? = null
) {
    // An exercise with its own weight unit (#7) works in that unit. Your plate list is in the global unit, so such an
    // exercise uses the standard plates for its unit and leaves your list alone; the bar is kept in kg and converts.
    val unit = snap.weightUnitOf(exerciseId)
    val ownPlates = unit == snap.weightUnit
    val prefs by Settings.portable.collectAsState()
    val savedBar = prefs.barKg?.let { snap.weight(it, exerciseId) } ?: defaultBar(unit)
    val savedPlates = prefs.plates?.takeIf { ownPlates }?.let { parsePlates(it) }?.takeIf { it.isNotEmpty() } ?: defaultPlates(unit)
    var target by remember { mutableStateOf(targetText) }
    var barText by remember(savedBar) { mutableStateOf(fmtNum(savedBar, 2)) }
    var platesText by remember(savedPlates) { mutableStateOf(savedPlates.joinToString(", ") { fmtNum(it, 2) }) }
    val bar = parse(barText) ?: 0.0
    val plates = parsePlates(platesText)
    val t = parse(target) ?: 0.0
    val (side, loaded) = platesPerSide(t, bar, prefs.countBar, plates)

    fun saveSetup() {
        val b = parse(barText)
        val list = parsePlates(platesText)
        Settings.updatePortable {
            it.copy(
                barKg = b?.let { v -> snap.toKg(v, exerciseId) },
                plates = if (!ownPlates) it.plates
                    else list.takeIf { l -> l.isNotEmpty() && l != defaultPlates(unit) }?.joinToString(",") { v -> fmtNum(v, 3) }
            )
        }
    }

    FitSheet(
        title = "Plate calculator",
        onDismiss = { saveSetup(); onDismiss() },
        dismissLabel = "Close",
        confirmLabel = if (loaded > 0) "Use ${fmtNum(loaded, 2)} $unit" else null,
        onConfirm = if (loaded > 0) ({ saveSetup(); onUse(fmtNum(loaded, 2)); onDismiss() }) else null
    ) {
        StepperField(
            label = "Target weight ($unit)",
            value = target,
            onValue = { target = it },
            onStep = { d -> target = fmtNum(max(0.0, t + d * (plates.minOrNull() ?: 1.0) * 2), 2) }
        )
        if (t > 0) {
            val perSide = side.groupBy { it }.entries.joinToString(" + ") { (p, l) -> if (l.size == 1) fmtNum(p, 2) else "${l.size} × ${fmtNum(p, 2)}" }
            Text(
                if (side.isEmpty()) "No plates: the bar alone" else "Each side: $perSide",
                style = MaterialTheme.typography.titleMedium,
                color = Brand.Gold
            )
            BarEnd(side)
            val gap = t - loaded
            Text(
                if (gap > 0.01) "That's ${fmtNum(loaded, 2)} $unit. ${fmtNum(gap, 2)} $unit can't be made with these plates."
                else "Loaded: ${fmtNum(loaded, 2)} $unit" + if (prefs.countBar) ", bar included." else ", not counting the bar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GoldHairline()
        Text("YOUR SETUP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            barText, { barText = it },
            Modifier.fillMaxWidth(),
            label = { Text("Bar weight ($unit)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        ToggleRow("Count the bar in the target", prefs.countBar) { on -> Settings.updatePortable { it.copy(countBar = on) } }
        OutlinedTextField(
            platesText, { platesText = it },
            Modifier.fillMaxWidth(),
            label = { Text("Plates on hand ($unit), separated by commas") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        TextButton(onClick = { platesText = defaultPlates(unit).joinToString(", ") { fmtNum(it, 2) }; barText = fmtNum(defaultBar(unit), 2) }) {
            Text("Reset to standard plates")
        }
    }
}

/** A comma- or space-separated plate list, in the display unit; anything unreadable is left out. */
fun parsePlates(text: String): List<Double> =
    text.split(',', ';', ' ').mapNotNull { parse(it)?.takeIf { v -> v > 0 } }

/** One end of the bar with its plates, heaviest nearest the middle, each plate's height following its weight. */
@Composable
private fun BarEnd(side: List<Double>) {
    val heaviest = side.maxOrNull() ?: 1.0
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = Spacing.sm)
            .semantics { contentDescription = "Plates on each side: " + side.joinToString(", ") { fmtNum(it, 2) } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(Modifier.width(56.dp).height(10.dp).background(Brand.Muted))
        side.forEachIndexed { i, p ->
            Box(
                Modifier
                    .width(30.dp)
                    .height((40 + 80 * (p / heaviest)).dp)
                    // Dark plates with gold rims and gold figures, never black text (#104).
                    .background(if (i % 2 == 0) Brand.GoldDusk else Brand.Graphite, FitShapes.row)
                    .border(1.dp, if (i % 2 == 0) Brand.Gold else Brand.GoldDeep, FitShapes.row),
                contentAlignment = Alignment.Center
            ) {
                Text(fmtNum(p, 2), style = MaterialTheme.typography.labelSmall, color = Brand.GoldLight, maxLines = 1)
            }
        }
        Box(Modifier.width(24.dp).height(10.dp).background(Brand.Muted))
    }
}
