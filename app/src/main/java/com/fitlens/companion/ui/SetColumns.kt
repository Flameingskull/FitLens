package com.fitlens.companion.ui

import com.fitlens.companion.data.DistanceUnits
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.SetCell
import android.content.res.Resources
import com.fitlens.companion.R

/** One value a set can record, shown as its own labelled column (#101). */
enum class SetField(val label: String) {
    WEIGHT("Weight"), REPS("Reps"), DISTANCE("Distance"), TIME("Time"),
    /** A user-defined type's own metric (#14), named by the type. */
    METRIC("Metric")
}

private fun SetField.recorded(s: SetRow): Boolean = when (this) {
    SetField.WEIGHT -> s.weightKg != 0.0
    SetField.REPS -> s.reps > 0
    SetField.DISTANCE -> s.distance > 0
    SetField.TIME -> s.durationSec > 0
    SetField.METRIC -> s.metric != null
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
        SetField.METRIC -> ExerciseTypes.metricOf(type) != null
    }
    byType || sets.any { f.recorded(it) }
}

/** [setFields] for exercise [exerciseId], from its library type. */
fun setFields(snap: Snapshot, exerciseId: Long, sets: List<SetRow>): List<SetField> =
    setFields(snap.exercises[exerciseId]?.type ?: ExerciseTypes.WEIGHT_REPS, sets)

/** A duration as TalkBack should say it: "1 minute 23 seconds". */
fun spokenDuration(res: Resources, sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    val parts = ArrayList<String>()
    if (h > 0) parts += res.getQuantityString(R.plurals.sd_hours, h, h)
    if (m > 0) parts += res.getQuantityString(R.plurals.sd_minutes, m, m)
    if (s > 0 || parts.isEmpty()) parts += res.getQuantityString(R.plurals.sd_seconds, s, s)
    return parts.joinToString(" ")
}

/**
 * A set's values in the order of [fields], ready for `SetRow(cells = …)`, each with its unit after it as FitNotes shows
 * them (#112): "85 kg", "6 reps", "5 km", "1:30". Distance is in the exercise's unit (#7). An empty value shows as
 * a dash.
 */
fun setCells(res: Resources, snap: Snapshot, fields: List<SetField>, s: SetRow): List<SetCell> = fields.map { f ->
    when (f) {
        SetField.WEIGHT -> {
            // A bodyweight set: "BW" says what happened, where "0 kg" read as though the weight had been lost (#74).
            if (s.weightKg == 0.0) {
                SetCell(res.getString(R.string.sc_bw), spoken = res.getString(R.string.sc_bodyweight))
            } else {
                val unitName = res.getString(if (snap.weightUnitOf(s.exerciseId) == "kg") R.string.sc_kilograms else R.string.sc_pounds)
                val w = snap.fmtWeight(s.weightKg, s.exerciseId)
                SetCell(w, snap.weightUnitOf(s.exerciseId), res.getString(R.string.sc_spoken, w, unitName))
            }
        }
        SetField.REPS ->
            if (s.reps > 0) SetCell("${s.reps}", res.getString(if (s.reps == 1) R.string.sc_rep else R.string.sc_reps), res.getQuantityString(R.plurals.reps_count, s.reps, s.reps))
            else SetCell("—", spoken = res.getString(R.string.sc_no_reps))
        SetField.DISTANCE ->
            if (s.distance > 0) {
                val unit = snap.distanceUnit(s.exerciseId)
                fmtNum(s.distance).let { SetCell(it, unit, "$it ${distanceUnitSpoken(res, unit)}") }
            }
            else SetCell("—", spoken = res.getString(R.string.sc_no_distance))
        SetField.TIME ->
            if (s.durationSec > 0) SetCell(fmtDuration(s.durationSec), spoken = spokenDuration(res, s.durationSec), unitSlot = false)
            else SetCell("—", spoken = res.getString(R.string.sc_no_time), unitSlot = false)
        SetField.METRIC -> {
            // The type's own metric (#14), with its unit; it keeps its column even if the type later drops it.
            val type = snap.exercises[s.exerciseId]?.type ?: ExerciseTypes.WEIGHT_REPS
            val metric = ExerciseTypes.metricOf(type)
            val name = metric?.metricName ?: res.getString(R.string.sc_value)
            val unit = metric?.metricUnit.orEmpty()
            s.metric?.let { v -> fmtNum(v, 2).let { SetCell(it, unit, "$it $unit $name".replace("  ", " ")) } }
                ?: SetCell("—", spoken = res.getString(R.string.sc_no_metric, name))
        }
    }
}
