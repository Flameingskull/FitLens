package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.PickerItem
import com.fitlens.companion.ui.design.SearchablePicker

/**
 * The calendar's filter (#9), as in FitNotes: days where one set meets every condition, for example "Bench Press,
 * at least 80 kg for at least 5 reps". Weights are kept in kg, distance in the unit it was logged in, time in seconds.
 * Null means no condition. It's remembered on this phone as [encode]d text (`DeviceSettings.calendarFilter`).
 */
data class CalendarFilter(
    val exerciseId: Long? = null,
    val categoryId: Long? = null,
    val minWeightKg: Double? = null,
    val maxWeightKg: Double? = null,
    val minReps: Int? = null,
    val maxReps: Int? = null,
    val minDistance: Double? = null,
    val minDurationSec: Int? = null
) {
    val active: Boolean get() = this != CalendarFilter()

    fun matches(snap: Snapshot, s: SetRow): Boolean =
        (exerciseId == null || s.exerciseId == exerciseId) &&
            (categoryId == null || snap.exercises[s.exerciseId]?.categoryId == categoryId) &&
            (minWeightKg == null || s.weightKg >= minWeightKg - 0.001) &&
            (maxWeightKg == null || s.weightKg <= maxWeightKg + 0.001) &&
            (minReps == null || s.reps >= minReps) &&
            (maxReps == null || s.reps <= maxReps) &&
            (minDistance == null || s.distance >= minDistance) &&
            (minDurationSec == null || s.durationSec >= minDurationSec)

    /** Every date with at least one matching set. */
    fun days(snap: Snapshot): Set<String> =
        if (!active) emptySet() else snap.setsByDate.filterValues { sets -> sets.any { matches(snap, it) } }.keys

    /** The conditions in words, for the line above the grid: "Bench Press · ≥ 80 kg · ≥ 5 reps". */
    fun describe(snap: Snapshot): String = listOfNotNull(
        exerciseId?.let { snap.exercises[it]?.name ?: "A deleted exercise" },
        categoryId?.let { snap.categories[it]?.name ?: "Uncategorised" },
        minWeightKg?.let { "≥ ${snap.fmtWeight(it)} ${snap.weightUnit}" },
        maxWeightKg?.let { "≤ ${snap.fmtWeight(it)} ${snap.weightUnit}" },
        minReps?.let { "≥ $it reps" },
        maxReps?.let { "≤ $it reps" },
        minDistance?.let { "≥ ${fmtNum(it)} distance" },
        minDurationSec?.let { "≥ ${fmtDuration(it)}" }
    ).joinToString("  ·  ")

    fun encode(): String? = if (!active) null else listOfNotNull(
        exerciseId?.let { "e=$it" }, categoryId?.let { "c=$it" },
        minWeightKg?.let { "wmin=$it" }, maxWeightKg?.let { "wmax=$it" },
        minReps?.let { "rmin=$it" }, maxReps?.let { "rmax=$it" },
        minDistance?.let { "dmin=$it" }, minDurationSec?.let { "tmin=$it" }
    ).joinToString(";")

    companion object {
        /** Reads what [encode] wrote; anything unreadable is simply left out. */
        fun decode(text: String?): CalendarFilter {
            if (text.isNullOrBlank()) return CalendarFilter()
            val m = text.split(";").mapNotNull { part -> part.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return CalendarFilter(
                exerciseId = m["e"]?.toLongOrNull(),
                categoryId = m["c"]?.toLongOrNull(),
                minWeightKg = m["wmin"]?.toDoubleOrNull(),
                maxWeightKg = m["wmax"]?.toDoubleOrNull(),
                minReps = m["rmin"]?.toIntOrNull(),
                maxReps = m["rmax"]?.toIntOrNull(),
                minDistance = m["dmin"]?.toDoubleOrNull(),
                minDurationSec = m["tmin"]?.toIntOrNull()
            )
        }
    }
}

/**
 * Builds the calendar filter (#9): an exercise or a category, then weight, reps, distance and time limits. Weights
 * are entered in the display unit and time in minutes. [onApply] gets the new filter; Clear removes every condition.
 */
@Composable
fun CalendarFilterSheet(snap: Snapshot, initial: CalendarFilter, onApply: (CalendarFilter) -> Unit, onDismiss: () -> Unit) {
    var exerciseId by remember { mutableStateOf(initial.exerciseId) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    fun weightText(kg: Double?) = kg?.let { fmtNum(snap.weight(it), 2) } ?: ""
    var minW by remember { mutableStateOf(weightText(initial.minWeightKg)) }
    var maxW by remember { mutableStateOf(weightText(initial.maxWeightKg)) }
    var minR by remember { mutableStateOf(initial.minReps?.toString() ?: "") }
    var maxR by remember { mutableStateOf(initial.maxReps?.toString() ?: "") }
    var minD by remember { mutableStateOf(initial.minDistance?.let { fmtNum(it, 2) } ?: "") }
    var minT by remember { mutableStateOf(initial.minDurationSec?.let { fmtNum(it / 60.0, 2) } ?: "") }
    var picking by remember { mutableStateOf<String?>(null) }

    fun num(t: String) = t.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    fun built() = CalendarFilter(
        exerciseId = exerciseId,
        categoryId = categoryId,
        minWeightKg = num(minW)?.let { snap.toKg(it) },
        maxWeightKg = num(maxW)?.let { snap.toKg(it) },
        minReps = num(minR)?.toInt(),
        maxReps = num(maxR)?.toInt(),
        minDistance = num(minD),
        minDurationSec = num(minT)?.let { (it * 60).toInt() }
    )

    FitSheet(
        title = "Filter days",
        onDismiss = onDismiss,
        confirmLabel = "Apply",
        onConfirm = { onApply(built()); onDismiss() },
        secondaryLabel = "Clear",
        onSecondary = { onApply(CalendarFilter()); onDismiss() }
    ) {
        Text(
            "Highlights the days where one set meets every condition. Leave a field empty to ignore it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ListRowWithMenu(
            title = "Exercise",
            subtitle = exerciseId?.let { snap.exercises[it]?.name } ?: "Any exercise",
            onClick = { picking = "exercise" }
        )
        ListRowWithMenu(
            title = "Category",
            subtitle = categoryId?.let { snap.categories[it]?.name } ?: "Any category",
            onClick = { picking = "category" }
        )
        NumberPair("Weight at least (${snap.weightUnit})", minW, { minW = it }, "at most", maxW, { maxW = it })
        NumberPair("Reps at least", minR, { minR = it }, "at most", maxR, { maxR = it }, decimal = false)
        NumberPair("Distance at least", minD, { minD = it }, "Time at least (min)", minT, { minT = it })
    }

    when (picking) {
        "exercise" -> SearchablePicker(
            title = "Filter by exercise",
            items = listOf(PickerItem(-1L, "Any exercise")) + exercisePickerItems(snap),
            onDismiss = { picking = null },
            onPick = { ids ->
                val id = ids.firstOrNull()
                exerciseId = id?.takeIf { it >= 0 }
                // One exercise already names its category, so the two don't combine.
                if (exerciseId != null) categoryId = null
                picking = null
            },
            searchLabel = "Search exercises"
        )
        "category" -> SearchablePicker(
            title = "Filter by category",
            items = listOf(PickerItem(-1L, "Any category")) + snap.categoriesSorted.map { c ->
                PickerItem(c.id, c.name, color = categoryColour(c.colour))
            },
            onDismiss = { picking = null },
            onPick = { ids ->
                categoryId = ids.firstOrNull()?.takeIf { it >= 0 }
                if (categoryId != null) exerciseId = null
                picking = null
            },
            searchLabel = "Search categories"
        )
    }
}

@Composable
private fun NumberPair(
    firstLabel: String, first: String, onFirst: (String) -> Unit,
    secondLabel: String, second: String, onSecond: (String) -> Unit,
    decimal: Boolean = true
) {
    val keyboard = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(first, onFirst, Modifier.weight(1f), label = { Text(firstLabel) }, singleLine = true, keyboardOptions = keyboard)
        OutlinedTextField(second, onSecond, Modifier.weight(1f), label = { Text(secondLabel) }, singleLine = true, keyboardOptions = keyboard)
    }
}
