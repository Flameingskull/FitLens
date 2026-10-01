package com.fitlens.companion.data

import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.pow

/**
 * Personal records and one-rep-max estimates (#23). Every screen and the PDF report use this one engine, so an
 * estimate or a record reads the same everywhere.
 */
object Records {
    /** The Records tab lists rep maxes from 1RM to this. */
    const val MAX_REPS = 15

    /** Sets above this many reps are too far from a single to estimate from, whatever the formula. */
    const val MAX_ESTIMATE_REPS = 20

    /**
     * The estimated-1RM formulas the user can choose from (#42), each with the most reps it's valid for: sets above
     * that aren't estimated. [key] is what's stored in `meta` (`PortableSettings.e1rmFormula`).
     * - Automatic: FitLens's blend by rep range, see [factor].
     * - Epley (1985): 1 + r/30. Widely used; a little high at low reps.
     * - Brzycki (1993): 36 / (37 − r). Accurate to about 10 reps, then climbs steeply.
     * - Lombardi (1989): r^0.10. A flat curve, conservative at higher reps.
     * - O'Conner et al. (1989): 1 + 0.025 r. The most conservative of the linear formulas.
     * - Wathan (1994): 100 / (48.8 + 53.8 e^(−0.075 r)). Fits well up to about 15 reps.
     */
    enum class Formula(val key: String, val label: String, val maxReps: Int) {
        AUTO("auto", "Automatic (recommended)", MAX_ESTIMATE_REPS),
        EPLEY("epley", "Epley", 12),
        BRZYCKI("brzycki", "Brzycki", 10),
        LOMBARDI("lombardi", "Lombardi", 12),
        OCONNER("oconner", "O'Conner", 12),
        WATHAN("wathan", "Wathan", 15);

        companion object {
            fun of(key: String?): Formula = entries.firstOrNull { it.key == key } ?: AUTO
        }
    }

    /** The formula chosen in Settings → Personal records (#42). */
    fun chosen(): Formula = Formula.of(Settings.currentPortable().e1rmFormula)

    /**
     * How many times heavier a one-rep max is than [reps] reps at a given weight, or 0 when it can't be estimated,
     * using [formula] (the user's choice by default, #42). Automatic works like this:
     * - 1 rep: the weight itself.
     * - 2 to 10 reps: the mean of Epley (1 + r/30) and Brzycki (36 / (37 - r)). The two agree closely here, and the
     *   mean evens out Epley's slight overestimate at low reps.
     * - 11 to 20 reps: the 10-rep factor grown with Lombardi's curve, (r/10)^0.1. Epley keeps climbing in a straight
     *   line and Brzycki runs away near 37 reps, and both overestimate from high-rep sets. Lombardi's flatter curve
     *   doesn't, and starting from the 10-rep value keeps the estimate continuous and rising with reps.
     * - More than 20 reps: not estimated.
     */
    fun factor(reps: Int, formula: Formula = chosen()): Double = when {
        reps <= 0 || reps > formula.maxReps -> 0.0
        reps == 1 -> 1.0
        else -> when (formula) {
            Formula.AUTO -> {
                if (reps <= 10) (1 + reps / 30.0 + 36.0 / (37 - reps)) / 2
                else factor(10, Formula.AUTO) * (reps / 10.0).pow(0.1)
            }
            Formula.EPLEY -> 1 + reps / 30.0
            Formula.BRZYCKI -> 36.0 / (37 - reps)
            Formula.LOMBARDI -> reps.toDouble().pow(0.1)
            Formula.OCONNER -> 1 + 0.025 * reps
            Formula.WATHAN -> 100.0 / (48.8 + 53.8 * exp(-0.075 * reps))
        }
    }

    /** Estimated one-rep max in kg for [weightKg] × [reps], or 0 when it can't be estimated. */
    fun oneRepMax(weightKg: Double, reps: Int, formula: Formula = chosen()): Double =
        if (weightKg <= 0) 0.0 else weightKg * factor(reps, formula)

    fun oneRepMax(s: SetRow): Double = oneRepMax(s.weightKg, s.reps)

    /** The weight in kg a lifter with [oneRepMaxKg] should manage for [reps] reps, or 0 when it can't be estimated. */
    fun weightFor(oneRepMaxKg: Double, reps: Int, formula: Formula = chosen()): Double {
        val f = factor(reps, formula)
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

    /** The heaviest weight in [sets] lifted for exactly [reps] reps, or 0 when there's none ("Max weight for reps", #22). */
    fun maxWeightForReps(sets: List<SetRow>, reps: Int): Double =
        sets.filter { it.reps == reps && it.weightKg > 0 }.maxOfOrNull { it.weightKg } ?: 0.0

    /**
     * The "Personal records" graph (#22): one point for each date, in date order, that has a set marked as a PR,
     * valued at the best estimated 1RM of all the PR sets so far. Built from the PR marks themselves, so it agrees with
     * the trophies, and it never falls.
     */
    fun recordProgress(byDate: Map<String, List<SetRow>>, formula: Formula = chosen()): List<Pair<String, Double>> {
        var best = 0.0
        return byDate.entries.sortedBy { it.key }.mapNotNull { (date, sets) ->
            val prs = sets.filter { it.isPr && it.weightKg > 0 && it.reps > 0 }
            if (prs.isEmpty()) null else {
                best = maxOf(best, prs.maxOf { oneRepMax(it.weightKg, it.reps, formula) })
                date to best
            }
        }
    }

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

    /** The sets dated from [from] to [to] inclusive (ISO dates), for the Records tab's Custom range. */
    fun between(sets: List<SetRow>, from: String, to: String): List<SetRow> =
        sets.filter { it.date.take(10) in from..to }
}
