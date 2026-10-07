package com.fitlens.companion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An exercise goal (#25): reach [target] in one of the [GoalKinds] for one exercise. Weights and volumes are in kg,
 * times in seconds. Goals live in `exercise_goal` (database v6), so they travel in `.flexnotes` backups.
 */
data class ExerciseGoal(val id: Long, val exerciseId: Long, val kind: Int, val target: Double, val sortOrder: Int)

/** Progress towards a goal: the best so far, when it was set, and when the target was first reached. */
data class GoalProgress(val best: Double, val bestDate: String?, val achievedDate: String?) {
    fun fraction(target: Double): Float = if (target <= 0) 0f else (best / target).toFloat().coerceIn(0f, 1f)
}

object GoalKinds {
    const val MAX_WEIGHT = 0
    const val E1RM = 1
    const val MAX_REPS = 2
    const val SET_VOLUME = 3
    const val WORKOUT_VOLUME = 4
    const val LONGEST_SET = 5
    const val WORKOUT_DISTANCE = 6

    /** The kinds offered for weight-and-reps exercises and for time or distance ones. */
    val strength = listOf(MAX_WEIGHT, E1RM, MAX_REPS, SET_VOLUME, WORKOUT_VOLUME)
    val timed = listOf(LONGEST_SET, WORKOUT_DISTANCE)

    fun isWeight(k: Int) = k == MAX_WEIGHT || k == E1RM || k == SET_VOLUME || k == WORKOUT_VOLUME
    fun isTime(k: Int) = k == LONGEST_SET

    /**
     * The best value so far and its date, and the first date the target was reached, from [sets] in date order.
     * Per-set kinds look at each set; per-workout kinds add up each day first.
     */
    fun progress(kind: Int, target: Double, sets: List<SetRow>): GoalProgress {
        val perDay = kind == WORKOUT_VOLUME || kind == WORKOUT_DISTANCE
        val values: List<Pair<String, Double>> = if (perDay) {
            sets.groupBy { it.date.take(10) }.toSortedMap().map { (d, l) ->
                d to if (kind == WORKOUT_VOLUME) l.sumOf { Analysis.volumeKg(it) } else l.sumOf { it.distance }
            }
        } else {
            sets.sortedBy { it.date }.map { s ->
                s.date.take(10) to when (kind) {
                    MAX_WEIGHT -> s.weightKg
                    E1RM -> Records.oneRepMax(s)
                    MAX_REPS -> s.reps.toDouble()
                    SET_VOLUME -> Analysis.volumeKg(s)
                    LONGEST_SET -> s.durationSec.toDouble()
                    else -> 0.0
                }
            }
        }
        var best = 0.0
        var bestDate: String? = null
        var achieved: String? = null
        values.forEach { (d, v) ->
            if (v > best) { best = v; bestDate = d }
            if (achieved == null && target > 0 && v >= target) achieved = d
        }
        return GoalProgress(best, bestDate, achieved)
    }
}

/** Writes for exercise goals, through [GoalDao] (#36). Each refreshes the library area of the snapshot (#60). */
object Goals {
    suspend fun save(goal: ExerciseGoal): Unit = withContext(Dispatchers.IO) {
        val goals = Store.db.goalDao
        if (goal.id == 0L) {
            Store.db.transaction { goals.add(goal.exerciseId, goal.kind, goal.target, goals.nextOrder(goal.exerciseId)) }
        } else {
            goals.update(goal.id, goal.exerciseId, goal.kind, goal.target)
        }
        Store.refresh(Area.LIBRARY)
    }

    suspend fun delete(id: Long): Unit = withContext(Dispatchers.IO) {
        Store.db.goalDao.delete(id)
        Store.refresh(Area.LIBRARY)
    }

    /** Stores [ordered] as the exercise's goal order, top first. */
    suspend fun reorder(ordered: List<ExerciseGoal>): Unit = withContext(Dispatchers.IO) {
        val goals = Store.db.goalDao
        Store.db.transaction { ordered.forEachIndexed { i, g -> goals.setOrder(g.id, i) } }
        Store.refresh(Area.LIBRARY)
    }
}
