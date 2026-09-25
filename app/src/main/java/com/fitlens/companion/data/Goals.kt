package com.fitlens.companion.data

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An exercise goal (#25): reach [target] in one of the [GoalKinds] for one exercise. Weights and volumes are in kg,
 * times in seconds. Goals live in `exercise_goal` (database v6), so they travel in `.fitlens` backups.
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

    fun label(k: Int): String = when (k) {
        MAX_WEIGHT -> "Max weight"
        E1RM -> "Est. 1RM"
        MAX_REPS -> "Max reps in a set"
        SET_VOLUME -> "Volume in one set"
        WORKOUT_VOLUME -> "Volume in one workout"
        LONGEST_SET -> "Longest set"
        WORKOUT_DISTANCE -> "Distance in one workout"
        else -> "Goal"
    }

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

/** Writes for exercise goals. Each reloads the snapshot, like every other write. */
object Goals {
    suspend fun save(goal: ExerciseGoal): Unit = withContext(Dispatchers.IO) {
        val w = Store.db.writableDatabase
        val cv = ContentValues().apply {
            put("exercise_id", goal.exerciseId); put("kind", goal.kind); put("target", goal.target)
        }
        if (goal.id == 0L) {
            val next = w.rawQuery("SELECT IFNULL(MAX(sort_order), -1) + 1 FROM exercise_goal WHERE exercise_id=?",
                arrayOf(goal.exerciseId.toString())).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            cv.put("sort_order", next)
            w.insertOrThrow("exercise_goal", null, cv)
        } else {
            w.update("exercise_goal", cv, "id=?", arrayOf(goal.id.toString()))
        }
        Store.reload()
    }

    suspend fun delete(id: Long): Unit = withContext(Dispatchers.IO) {
        Store.db.writableDatabase.delete("exercise_goal", "id=?", arrayOf(id.toString()))
        Store.reload()
    }

    /** Stores [ordered] as the exercise's goal order, top first. */
    suspend fun reorder(ordered: List<ExerciseGoal>): Unit = withContext(Dispatchers.IO) {
        val w = Store.db.writableDatabase
        w.beginTransaction()
        try {
            ordered.forEachIndexed { i, g ->
                w.update("exercise_goal", ContentValues().apply { put("sort_order", i) }, "id=?", arrayOf(g.id.toString()))
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        Store.reload()
    }
}
