package com.fitlens.companion.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saved workouts (#100). In the owner's vocabulary a **workout** is a group of exercises with prescribed sets; a
 * saved workout is a named, reusable one. Adding it to a day logs all of its sets at once, like FitNotes's "Log All",
 * and the user then updates each set as they train. Saved workouts live in their own tables (database v7), so they
 * travel in `.fitlens` backups, and a FitNotes import never touches them.
 */

/** A prescribed set. Weights are in kg and times in seconds, like logged sets. */
data class PlannedSet(
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val distance: Double = 0.0,
    val durationSec: Int = 0,
    val setType: Int = SetTypes.WORKING
) {
    val isEmpty: Boolean get() = weightKg == 0.0 && reps == 0 && distance == 0.0 && durationSec == 0
}

/**
 * One exercise in a saved workout. [fill] decides its sets when the workout is added to a day:
 * [SavedWorkouts.FILL_LAST] repeats what was done the last time, [SavedWorkouts.FILL_PLANNED] uses [sets].
 */
data class PlannedExercise(
    val exerciseId: Long,
    val fill: Int = SavedWorkouts.FILL_LAST,
    val sets: List<PlannedSet> = emptyList(),
    /** The superset (#18) it belongs to within the workout; 0 when none. */
    val superset: Int = 0
)

/** A saved workout: its name, notes and exercises in order. An [id] of 0 is one not saved yet. */
data class SavedWorkout(
    val id: Long,
    val name: String,
    val notes: String? = null,
    val sortOrder: Int = 0,
    val exercises: List<PlannedExercise> = emptyList()
)

object SavedWorkouts {
    const val FILL_LAST = 0
    const val FILL_PLANNED = 1

    const val CREATE_WORKOUT =
        "CREATE TABLE saved_workout(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, notes TEXT, " +
            "sort_order INTEGER NOT NULL DEFAULT 0)"
    const val CREATE_EXERCISE =
        "CREATE TABLE saved_workout_exercise(id INTEGER PRIMARY KEY AUTOINCREMENT, workout_id INTEGER NOT NULL, " +
            "exercise_id INTEGER NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, fill INTEGER NOT NULL DEFAULT 0, " +
            "superset INTEGER NOT NULL DEFAULT 0)"
    const val CREATE_SET =
        "CREATE TABLE saved_workout_set(id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL, " +
            "sort_order INTEGER NOT NULL DEFAULT 0, weight REAL NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, " +
            "distance REAL NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, set_type INTEGER NOT NULL DEFAULT 0)"

    /** Every saved workout in the user's order, for the snapshot. */
    fun load(r: SQLiteDatabase): List<SavedWorkout> {
        val sets = HashMap<Long, MutableList<PlannedSet>>()
        r.rawQuery(
            "SELECT item_id, weight, reps, distance, duration, set_type FROM saved_workout_set ORDER BY item_id, sort_order, id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                sets.getOrPut(c.lng(0)) { ArrayList() }.add(PlannedSet(c.dbl(1), c.int(2), c.dbl(3), c.int(4), c.int(5)))
            }
        }
        val items = HashMap<Long, MutableList<PlannedExercise>>()
        r.rawQuery(
            "SELECT id, workout_id, exercise_id, fill, superset FROM saved_workout_exercise ORDER BY workout_id, sort_order, id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                items.getOrPut(c.lng(1)) { ArrayList() }
                    .add(PlannedExercise(c.lng(2), c.int(3), sets[c.lng(0)].orEmpty(), c.int(4)))
            }
        }
        val out = ArrayList<SavedWorkout>()
        r.rawQuery("SELECT id, name, notes, sort_order FROM saved_workout ORDER BY sort_order, id", null).use { c ->
            while (c.moveToNext()) {
                val id = c.lng(0)
                out.add(SavedWorkout(id, c.strOr(1), c.str(2), c.int(3), items[id].orEmpty()))
            }
        }
        return out
    }

    /**
     * Creates the workout (id 0) or replaces it whole: a saved workout is small, so its exercises and sets are
     * rewritten rather than diffed. Exercises that no longer exist are dropped. Returns the workout's id.
     */
    suspend fun save(workout: SavedWorkout): Long = write { w ->
        val name = workout.name.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) throw WorkoutDataException("Enter a name for the workout.")
        val cv = ContentValues().apply { put("name", name); put("notes", workout.notes?.trim()?.ifBlank { null }) }
        val id = if (workout.id == 0L) {
            val next = w.rawQuery("SELECT IFNULL(MAX(sort_order), -1) + 1 FROM saved_workout", null)
                .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            cv.put("sort_order", next)
            w.insertOrThrow("saved_workout", null, cv)
        } else {
            w.update("saved_workout", cv, "id=?", arrayOf(workout.id.toString()))
            clearItems(w, workout.id)
            workout.id
        }
        val known = w.rawQuery("SELECT id FROM exercise", null).use { c ->
            HashSet<Long>().apply { while (c.moveToNext()) add(c.getLong(0)) }
        }
        workout.exercises.filter { it.exerciseId in known }.forEachIndexed { i, p ->
            val itemId = w.insertOrThrow("saved_workout_exercise", null, ContentValues().apply {
                put("workout_id", id); put("exercise_id", p.exerciseId); put("sort_order", i); put("fill", p.fill)
                put("superset", p.superset)
            })
            p.sets.forEachIndexed { j, s ->
                w.insertOrThrow("saved_workout_set", null, ContentValues().apply {
                    put("item_id", itemId); put("sort_order", j); put("weight", s.weightKg); put("reps", s.reps)
                    put("distance", s.distance); put("duration", s.durationSec); put("set_type", s.setType)
                })
            }
        }
        id
    }

    suspend fun delete(id: Long): Unit = write { w ->
        clearItems(w, id)
        w.delete("saved_workout", "id=?", arrayOf(id.toString()))
    }

    /** Stores [ids] as the order of the saved workouts, first to last. */
    suspend fun reorder(ids: List<Long>): Unit = write { w ->
        ids.forEachIndexed { i, id ->
            w.update("saved_workout", ContentValues().apply { put("sort_order", i) }, "id=?", arrayOf(id.toString()))
        }
    }

    private fun clearItems(w: SQLiteDatabase, workoutId: Long) {
        w.execSQL(
            "DELETE FROM saved_workout_set WHERE item_id IN (SELECT id FROM saved_workout_exercise WHERE workout_id=?)",
            arrayOf<Any>(workoutId)
        )
        w.delete("saved_workout_exercise", "workout_id=?", arrayOf(workoutId.toString()))
    }

    /** Removes an exercise from every saved workout, when the exercise itself is deleted. */
    internal fun forgetExercise(w: SQLiteDatabase, exerciseId: Long) {
        w.execSQL(
            "DELETE FROM saved_workout_set WHERE item_id IN (SELECT id FROM saved_workout_exercise WHERE exercise_id=?)",
            arrayOf<Any>(exerciseId)
        )
        w.delete("saved_workout_exercise", "exercise_id=?", arrayOf(exerciseId.toString()))
    }

    /**
     * The sets [p] adds on [date]: its prescribed sets, or every set from the last day before [date] that the
     * exercise was logged. Empty when there's nothing to go on (a new exercise set to repeat last time).
     */
    fun resolve(snap: Snapshot, p: PlannedExercise, date: String): List<PlannedSet> {
        if (p.fill == FILL_PLANNED) return p.sets.filter { !it.isEmpty }
        val history = snap.setsByExercise[p.exerciseId].orEmpty()
        val lastDay = history.filter { it.date < date }.maxOfOrNull { it.date } ?: return emptyList()
        return history.filter { it.date == lastDay }.map { it.toPlanned() }
    }

    /** A logged day as a saved workout's exercises: in the order first logged, each with that day's sets. */
    fun fromDay(snap: Snapshot, date: String, fill: Int): List<PlannedExercise> =
        snap.setsByDate[date].orEmpty()
            .groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.position } }
            .map { (exId, sets) -> PlannedExercise(exId, fill, sets.map { it.toPlanned() }, sets.maxOf { it.superset }) }

    private fun SetRow.toPlanned() = PlannedSet(weightKg, reps, distance, durationSec, setType)

    /** How the sets read in lists, for example "3 sets · 100 kg × 5". */
    fun describe(snap: Snapshot, sets: List<PlannedSet>): String {
        if (sets.isEmpty()) return "No sets"
        val first = sets.first()
        val parts = ArrayList<String>()
        if (first.weightKg != 0.0) parts.add("${snap.fmtWeight(first.weightKg)} ${snap.weightUnit}")
        if (first.reps > 0) parts.add("${first.reps}")
        if (first.distance > 0) parts.add("${fmtNum(first.distance)} dist")
        if (first.durationSec > 0) parts.add(fmtDuration(first.durationSec))
        val same = sets.all { it == first }
        val head = "${sets.size} set${if (sets.size == 1) "" else "s"}"
        return if (parts.isEmpty()) head else "$head · ${parts.joinToString(" × ")}${if (same) "" else " …"}"
    }

    private suspend fun <T> write(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        val w = Store.db.writableDatabase
        w.beginTransaction()
        val result = try {
            block(w).also { w.setTransactionSuccessful() }
        } finally {
            w.endTransaction()
        }
        Store.reload()
        result
    }
}
