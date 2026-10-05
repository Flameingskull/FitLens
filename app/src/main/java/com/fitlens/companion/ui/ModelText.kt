package com.fitlens.companion.ui

import android.content.res.Resources
import com.fitlens.companion.R
import com.fitlens.companion.data.CustomType
import com.fitlens.companion.data.DistanceUnits
import com.fitlens.companion.data.Effort
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.Records
import com.fitlens.companion.data.SetTypes
import com.fitlens.companion.data.fmtNum
import java.util.Locale

/*
 * What the workout model's stored values read as on screen (#156): exercise types, a custom type's values, set types,
 * effort and distance units. The values stay as stored; only their words come from strings.xml.
 */

/** "Weight & reps", "Distance & time"; a custom type is shown by its own name. */
internal fun exerciseTypeText(res: Resources, type: Int): String =
    if (ExerciseTypes.custom.containsKey(type)) ExerciseTypes.label(type)
    else res.getString(
        when (type) {
            ExerciseTypes.DISTANCE_TIME -> R.string.xt_distance_time
            ExerciseTypes.WEIGHT_DISTANCE -> R.string.xt_weight_distance
            ExerciseTypes.TIME -> R.string.xt_time
            ExerciseTypes.WEIGHT_TIME -> R.string.xt_weight_time
            ExerciseTypes.REPS_TIME -> R.string.xt_reps_time
            ExerciseTypes.REPS_DISTANCE -> R.string.xt_reps_distance
            ExerciseTypes.WEIGHT_ONLY -> R.string.xt_weight
            ExerciseTypes.REPS_ONLY -> R.string.xt_reps
            ExerciseTypes.DISTANCE_ONLY -> R.string.xt_distance
            else -> R.string.xt_weight_reps
        }
    )

/** A short example of the exercises a type suits, for the type picker; a custom type lists what it records. */
internal fun exerciseTypeExample(res: Resources, type: Int): String =
    ExerciseTypes.custom[type]?.let { customTypeText(res, it) } ?: res.getString(
        when (type) {
            ExerciseTypes.DISTANCE_TIME -> R.string.xte_distance_time
            ExerciseTypes.WEIGHT_DISTANCE -> R.string.xte_weight_distance
            ExerciseTypes.TIME -> R.string.xte_time
            ExerciseTypes.WEIGHT_TIME -> R.string.xte_weight_time
            ExerciseTypes.REPS_TIME -> R.string.xte_reps_time
            ExerciseTypes.REPS_DISTANCE -> R.string.xte_reps_distance
            ExerciseTypes.WEIGHT_ONLY -> R.string.xte_weight
            ExerciseTypes.REPS_ONLY -> R.string.xte_reps
            ExerciseTypes.DISTANCE_ONLY -> R.string.xte_distance
            else -> R.string.xte_weight_reps
        }
    )

/** "Weight, reps and height (cm)": what a custom type records. */
internal fun customTypeText(res: Resources, t: CustomType): String {
    val parts = listOfNotNull(
        res.getString(R.string.ct_weight).takeIf { t.weight },
        res.getString(R.string.ct_reps).takeIf { t.reps },
        res.getString(R.string.ct_distance).takeIf { t.distance },
        res.getString(R.string.ct_time).takeIf { t.time },
        t.metricName?.let { n ->
            val name = n.lowercase(Locale.getDefault())
            t.metricUnit?.takeIf { it.isNotBlank() }?.let { res.getString(R.string.ct_metric_unit, name, it) } ?: name
        }
    )
    val list = when (parts.size) {
        0 -> ""
        1 -> parts[0]
        else -> res.getString(
            R.string.ct_list_two,
            parts.dropLast(1).reduce { a, b -> res.getString(R.string.ct_list_more, a, b) },
            parts.last()
        )
    }
    return list.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/** "Working", "Warm-up", "Drop set", "To failure" (#43). */
internal fun setTypeText(res: Resources, t: Int): String = res.getString(
    when (t) {
        SetTypes.WARMUP -> R.string.stype_warmup
        SetTypes.DROP -> R.string.stype_drop
        SetTypes.FAILURE -> R.string.stype_failure
        else -> R.string.stype_working
    }
)

/** Effort for a set list (#44): "RPE 8.5" or "2 RIR". */
internal fun effortText(res: Resources, rpe: Double, mode: String): String =
    if (mode == Effort.RIR) {
        val r = Effort.rirFromRpe(rpe)
        if (r >= 5) res.getString(R.string.eff_rir_max) else res.getString(R.string.eff_rir, r)
    } else res.getString(R.string.eff_rpe, fmtNum(rpe, 1))

/** What TalkBack reads for effort: "RPE 8" or "2 reps in reserve". */
internal fun effortSpoken(res: Resources, rpe: Double, mode: String): String =
    if (mode == Effort.RIR) {
        val r = Effort.rirFromRpe(rpe)
        if (r >= 5) res.getString(R.string.eff_rir_max_spoken) else res.getQuantityString(R.plurals.eff_rir_spoken, r, r)
    } else res.getString(R.string.eff_rpe, fmtNum(rpe, 1))

/** A 1RM formula's name (#42): Automatic is worded here; the others are named after their authors. */
internal fun formulaText(res: Resources, f: Records.Formula): String =
    if (f == Records.Formula.AUTO) res.getString(R.string.formula_auto) else f.label

/** "Kilometres", "Miles", "Metres" (#7). */
internal fun distanceUnitText(res: Resources, unit: String): String = res.getString(
    when (unit) {
        DistanceUnits.MI -> R.string.du_mi
        DistanceUnits.M -> R.string.du_m
        else -> R.string.du_km
    }
)

/** A distance unit as TalkBack reads it. */
internal fun distanceUnitSpoken(res: Resources, unit: String): String = res.getString(
    when (unit) {
        DistanceUnits.MI -> R.string.du_mi_spoken
        DistanceUnits.M -> R.string.du_m_spoken
        else -> R.string.du_km_spoken
    }
)
