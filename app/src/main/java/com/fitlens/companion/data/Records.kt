package com.fitlens.companion.data

import java.time.LocalDate
import kotlin.math.pow

/**
 * Personal records and one-rep-max estimates (#23). Every screen and the PDF report use this one engine, so an
 * estimate or a record reads the same everywhere.
 */
object Records {
    /** The Records tab lists rep maxes from 1RM to this. */
    const val MAX_REPS = 15

    /** Sets above this many reps are too far from a single to estimate from. */
    const val MAX_ESTIMATE_REPS = 20

    /**
     * How many times heavier a one-rep max is than [reps] reps at a given weight, or 0 when it can't be estimated.
     * - 1 rep: the weight itself.
     * - 2 to 10 reps: the mean of Epley (1 + r/30) and Brzycki (36 / (37 - r)). The two agree closely here, and the
     *   mean evens out Epley's slight overestimate at low reps.
     * - 11 to 20 reps: the 10-rep factor grown with Lombardi's curve, (r/10)^0.1. Epley keeps climbing in a straight
     *   line and Brzycki runs away near 37 reps, and both overestimate from high-rep sets. Lombardi's flatter curve
     *   doesn't, and starting from the 10-rep value keeps the estimate continuous and rising with reps.
     * - More than 20 reps: not estimated.
     */
    fun factor(reps: Int): Double = when {
        reps <= 0 || reps > MAX_ESTIMATE_REPS -> 0.0
        reps == 1 -> 1.0
        reps <= 10 -> (1 + reps / 30.0 + 36.0 / (37 - reps)) / 2
        else -> factor(10) * (reps / 10.0).pow(0.1)
    }

    /** Estimated one-rep max in kg for [weightKg] × [reps], or 0 when it can't be estimated. */
    fun oneRepMax(weightKg: Double, reps: Int): Double = if (weightKg <= 0) 0.0 else weightKg * factor(reps)

    fun oneRepMax(s: SetRow): Double = oneRepMax(s.weightKg, s.reps)

    /** The weight in kg a lifter with [oneRepMaxKg] should manage for [reps] reps, or 0 when it can't be estimated. */
    fun weightFor(oneRepMaxKg: Double, reps: Int): Double {
        val f = factor(reps)
        return if (oneRepMaxKg <= 0 || f <= 0) 0.0 else oneRepMaxKg / f
    }

    /**
     * The actual record for [reps] reps: the heaviest set of at least that many reps. A higher-rep set of equal or
     * heavier weight supersedes lower-rep records, so on equal weight the set with more reps wins, and on an exact
     * tie the earliest set (the one that set the record first) keeps it.
     */
    fun repMax(sets: List<SetRow>, reps: Int): SetRow? =
        sets.filter { it.reps >= reps && it.weightKg > 0 }
            .maxWithOrNull(compareBy<SetRow>({ it.weightKg }, { it.reps }).thenByDescending { it.date })

    /**
     * Whether a new set of [weightKg] × [reps] beats the record for that rep count. [bestKg] is the
     * heaviest weight already lifted for at least [reps] reps, or null when there's none.
     */
    fun isNewRecord(weightKg: Double, reps: Int, bestKg: Double?): Boolean =
        weightKg > 0 && reps > 0 && (bestKg == null || weightKg > bestKg)

    /** Record periods for the Records tab. */
    enum class Period(val label: String) { WORKOUT("Workout"), WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All") }

    /**
     * The sets that fall in [period]. Week, month and year count back from today. Workout is the exercise's most
     * recent workout day.
     */
    fun inPeriod(sets: List<SetRow>, period: Period, today: LocalDate = LocalDate.now()): List<SetRow> {
        val from = when (period) {
            Period.ALL -> return sets
            Period.WORKOUT -> return sets.maxOfOrNull { it.date.take(10) }?.let { last -> sets.filter { it.date.take(10) == last } } ?: sets
            Period.WEEK -> today.minusDays(6)
            Period.MONTH -> today.minusMonths(1)
            Period.YEAR -> today.minusYears(1)
        }.format(Dates.ISO)
        return sets.filter { it.date.take(10) >= from }
    }
}
