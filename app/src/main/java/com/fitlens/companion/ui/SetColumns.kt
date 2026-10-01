package com.fitlens.companion.ui

import com.fitlens.companion.data.DistanceUnits
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.SetCell

/** One value a set can record, shown as its own labelled column (#101). */
enum class SetField(val label: String) { WEIGHT("Weight"), REPS("Reps"), DISTANCE("Distance"), TIME("Time") }

private fun SetField.recorded(s: SetRow): Boolean = when (this) {
    SetField.WEIGHT -> s.weightKg != 0.0
    SetField.REPS -> s.reps > 0
    SetField.DISTANCE -> s.distance > 0
    SetField.TIME -> s.durationSec > 0
}

/**
 * The columns an exercise's sets are shown in (#101). The exercise type decides them, so a bodyweight set still has
 * its Weight column. A value the type doesn't use but a set recorded anyway (imported FitNotes data) gets a column
 * too, so nothing logged is hidden.
 */
fun setFields(type: Int, sets: List<SetRow>): List<SetField> = SetField.entries.filter { f ->
    val byType = when (f) {
        SetField.WEIGHT -> ExerciseTypes.usesWeight(type)
        SetField.REPS -> ExerciseTypes.usesReps(type)
        SetField.DISTANCE -> ExerciseTypes.usesDistance(type)
        SetField.TIME -> ExerciseTypes.usesDuration(type)
    }
    byType || sets.any { f.recorded(it) }
}

/** [setFields] for exercise [exerciseId], from its library type. */
fun setFields(snap: Snapshot, exerciseId: Long, sets: List<SetRow>): List<SetField> =
    setFields(snap.exercises[exerciseId]?.type ?: ExerciseTypes.WEIGHT_REPS, sets)

/** A duration as TalkBack should say it: "1 minute 23 seconds". */
fun spokenDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    val parts = ArrayList<String>()
    if (h > 0) parts += "$h hour${if (h == 1) "" else "s"}"
    if (m > 0) parts += "$m minute${if (m == 1) "" else "s"}"
    if (s > 0 || parts.isEmpty()) parts += "$s second${if (s == 1) "" else "s"}"
    return parts.joinToString(" ")
}

/**
 * A set's values in the order of [fields], ready for `SetRow(cells = …)`, each with its unit after it as FitNotes shows
 * them (#112): "85 kg", "6 reps", "5 km", "1:30". Distance is in the exercise's unit (#7). An empty value shows as
 * a dash.
 */
fun setCells(snap: Snapshot, fields: List<SetField>, s: SetRow): List<SetCell> = fields.map { f ->
    when (f) {
        SetField.WEIGHT -> {
            // A bodyweight set: "BW" says what happened, where "0 kg" read as though the weight had been lost (#74).
            if (s.weightKg == 0.0) {
                SetCell("BW", spoken = "bodyweight")
            } else {
                val unitName = if (snap.weightUnit == "kg") "kilograms" else "pounds"
                val w = snap.fmtWeight(s.weightKg)
                SetCell(w, snap.weightUnit, "$w $unitName")
            }
        }
        SetField.REPS ->
            if (s.reps > 0) SetCell("${s.reps}", if (s.reps == 1) "rep" else "reps", "${s.reps} rep${if (s.reps == 1) "" else "s"}")
            else SetCell("—", spoken = "no reps")
        SetField.DISTANCE ->
            if (s.distance > 0) {
                val unit = snap.distanceUnit(s.exerciseId)
                fmtNum(s.distance).let { SetCell(it, unit, "$it ${DistanceUnits.spoken(unit)}") }
            }
            else SetCell("—", spoken = "no distance")
        SetField.TIME ->
            if (s.durationSec > 0) SetCell(fmtDuration(s.durationSec), spoken = spokenDuration(s.durationSec), unitSlot = false)
            else SetCell("—", spoken = "no time", unitSlot = false)
    }
}
