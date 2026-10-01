package com.fitlens.companion.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Workouts, FitNotes's routines (#106). In the owner's vocabulary a user-made **workout** is one function: a name,
 * optional notes and ordered **days** the user names freely ("Monday", "Push Day"), each holding its own exercises
 * with how their sets are filled. It replaced the separate saved workouts (#100) and routines (#21) in database v13.
 * The code keeps the `Routine` names and tables (`routine`, `routine_day`, `routine_day_exercise`, `routine_day_set`).
 * They travel in `.fitlens` backups and FitNotes imports never touch them. `workout_origin` remembers which workout
 * and day a logged date was started from, which is how the next day is suggested.
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
 * One exercise in a workout day. [fill] decides its sets when the day is logged: [Routines.FILL_LAST] copies the
 * previous time's sets, [Routines.FILL_PLANNED] uses [sets], [Routines.FILL_NONE] adds the exercise with no sets.
 */
data class PlannedExercise(
    val exerciseId: Long,
    val fill: Int = Routines.FILL_LAST,
    val sets: List<PlannedSet> = emptyList(),
    /** The superset (#18) it belongs to within the day; 0 when none. */
    val superset: Int = 0
)

/** One day of a workout, named by the user. An [id] of 0 is one not saved yet. */
data class RoutineDay(val id: Long, val name: String, val exercises: List<PlannedExercise> = emptyList())

/** A workout: its name, notes and days in order. An [id] of 0 is one not saved yet. */
data class Routine(
    val id: Long,
    val name: String,
    val notes: String? = null,
    val sortOrder: Int = 0,
    val days: List<RoutineDay> = emptyList()
)

/** Which workout ([workoutId], a `routine` id) and which of its days a logged date was started from. */
data class WorkoutOrigin(val date: String, val workoutId: Long, val routineDayId: Long?)

object Routines {
    const val FILL_LAST = 0
    const val FILL_PLANNED = 1
    const val FILL_NONE = 2

    const val CREATE_ROUTINE =
        "CREATE TABLE routine(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, notes TEXT, " +
            "sort_order INTEGER NOT NULL DEFAULT 0)"

    /** `workout_id` is left from v8–v12, when a day pointed at a saved workout; since v13 it is always 0. */
    const val CREATE_DAY =
        "CREATE TABLE routine_day(id INTEGER PRIMARY KEY AUTOINCREMENT, routine_id INTEGER NOT NULL, " +
            "name TEXT NOT NULL, workout_id INTEGER NOT NULL DEFAULT 0, sort_order INTEGER NOT NULL DEFAULT 0)"
    const val CREATE_ORIGIN =
        "CREATE TABLE workout_origin(date TEXT PRIMARY KEY, workout_id INTEGER NOT NULL, routine_day_id INTEGER)"
    const val CREATE_EXERCISE =
        "CREATE TABLE routine_day_exercise(id INTEGER PRIMARY KEY AUTOINCREMENT, day_id INTEGER NOT NULL, " +
            "exercise_id INTEGER NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, fill INTEGER NOT NULL DEFAULT 0, " +
            "superset INTEGER NOT NULL DEFAULT 0)"
    const val CREATE_SET =
        "CREATE TABLE routine_day_set(id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL, " +
            "sort_order INTEGER NOT NULL DEFAULT 0, weight REAL NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, " +
            "distance REAL NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, set_type INTEGER NOT NULL DEFAULT 0)"

    fun load(r: SQLiteDatabase): List<Routine> {
        val sets = HashMap<Long, MutableList<PlannedSet>>()
        r.rawQuery(
            "SELECT item_id, weight, reps, distance, duration, set_type FROM routine_day_set ORDER BY item_id, sort_order, id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                sets.getOrPut(c.lng(0)) { ArrayList() }.add(PlannedSet(c.dbl(1), c.int(2), c.dbl(3), c.int(4), c.int(5)))
            }
        }
        val items = HashMap<Long, MutableList<PlannedExercise>>()
        r.rawQuery(
            "SELECT id, day_id, exercise_id, fill, superset FROM routine_day_exercise ORDER BY day_id, sort_order, id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                items.getOrPut(c.lng(1)) { ArrayList() }
                    .add(PlannedExercise(c.lng(2), c.int(3), sets[c.lng(0)].orEmpty(), c.int(4)))
            }
        }
        val days = HashMap<Long, MutableList<RoutineDay>>()
        r.rawQuery("SELECT id, routine_id, name FROM routine_day ORDER BY routine_id, sort_order, id", null).use { c ->
            while (c.moveToNext()) {
                val id = c.lng(0)
                days.getOrPut(c.lng(1)) { ArrayList() }.add(RoutineDay(id, c.strOr(2), items[id].orEmpty()))
            }
        }
        val out = ArrayList<Routine>()
        r.rawQuery("SELECT id, name, notes, sort_order FROM routine ORDER BY sort_order, id", null).use { c ->
            while (c.moveToNext()) out.add(Routine(c.lng(0), c.strOr(1), c.str(2), c.int(3), days[c.lng(0)].orEmpty()))
        }
        return out
    }

    fun loadOrigins(r: SQLiteDatabase): Map<String, WorkoutOrigin> {
        val out = HashMap<String, WorkoutOrigin>()
        r.rawQuery("SELECT date, workout_id, routine_day_id FROM workout_origin", null).use { c ->
            while (c.moveToNext()) {
                val d = c.strOr(0)
                out[d] = WorkoutOrigin(d, c.lng(1), if (c.isNull(2)) null else c.getLong(2))
            }
        }
        return out
    }

    /**
     * Creates the workout (id 0) or updates it. Days keep their ids when they're kept (a day with id 0 is new), so a
     * logged date still knows which day it was; removed days are deleted. Each day's exercises are small, so they are
     * rewritten rather than diffed, and exercises that no longer exist are dropped. Returns the workout's id.
     */
    suspend fun save(routine: Routine): Long = write { w ->
        val name = routine.name.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) throw WorkoutDataException("Enter a name for the workout.")
        val cv = ContentValues().apply { put("name", name); put("notes", routine.notes?.trim()?.ifBlank { null }) }
        val id = if (routine.id == 0L) {
            val next = w.rawQuery("SELECT IFNULL(MAX(sort_order), -1) + 1 FROM routine", null)
                .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            cv.put("sort_order", next)
            w.insertOrThrow("routine", null, cv)
        } else {
            w.update("routine", cv, "id=?", arrayOf(routine.id.toString()))
            routine.id
        }
        val kept = routine.days.map { it.id }.filter { it > 0 }
        val gone = w.rawQuery("SELECT id FROM routine_day WHERE routine_id=?", arrayOf(id.toString())).use { c ->
            ArrayList<Long>().apply { while (c.moveToNext()) c.getLong(0).takeIf { it !in kept }?.let { add(it) } }
        }
        gone.forEach { deleteDayRows(w, it) }
        val known = knownExercises(w)
        routine.days.forEachIndexed { i, d ->
            val dv = ContentValues().apply {
                put("routine_id", id); put("name", dayName(d.name, i)); put("sort_order", i)
            }
            val dayId = if (d.id > 0) {
                w.update("routine_day", dv, "id=?", arrayOf(d.id.toString()))
                d.id
            } else w.insertOrThrow("routine_day", null, dv)
            writeExercises(w, dayId, d.exercises, known)
        }
        id
    }

    suspend fun delete(id: Long): Unit = write { w ->
        val days = w.rawQuery("SELECT id FROM routine_day WHERE routine_id=?", arrayOf(id.toString())).use { c ->
            ArrayList<Long>().apply { while (c.moveToNext()) add(c.getLong(0)) }
        }
        days.forEach { deleteDayRows(w, it) }
        w.delete("routine", "id=?", arrayOf(id.toString()))
    }

    /** A copy of [routine] with new ids, named [name]. Returns the new id. */
    suspend fun copy(routine: Routine, name: String): Long =
        save(routine.copy(id = 0L, name = name, days = routine.days.map { it.copy(id = 0L) }))

    /** Adds a day holding [exercises] to the end of workout [routineId]. Returns the new day's id. */
    suspend fun addDay(routineId: Long, name: String, exercises: List<PlannedExercise>): Long = write { w ->
        val next = w.rawQuery("SELECT IFNULL(MAX(sort_order), -1) + 1 FROM routine_day WHERE routine_id=?", arrayOf(routineId.toString()))
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        val dayId = w.insertOrThrow("routine_day", null, ContentValues().apply {
            put("routine_id", routineId); put("name", dayName(name, next)); put("sort_order", next)
        })
        writeExercises(w, dayId, exercises, knownExercises(w))
        dayId
    }

    /** Removes one day, used to undo [addDay]. */
    suspend fun deleteDay(dayId: Long): Unit = write { w -> deleteDayRows(w, dayId) }

    /** Replaces day [dayId]'s exercises with [exercises] ("Save as a workout day" into an existing day). */
    suspend fun setDayExercises(dayId: Long, exercises: List<PlannedExercise>): Unit = write { w ->
        writeExercises(w, dayId, exercises, knownExercises(w))
    }

    private fun dayName(name: String, index: Int) = name.trim().replace(Regex("\\s+"), " ").ifBlank { "Day ${index + 1}" }

    private fun knownExercises(w: SQLiteDatabase): Set<Long> = w.rawQuery("SELECT id FROM exercise", null).use { c ->
        HashSet<Long>().apply { while (c.moveToNext()) add(c.getLong(0)) }
    }

    private fun clearExercises(w: SQLiteDatabase, dayId: Long) {
        w.execSQL(
            "DELETE FROM routine_day_set WHERE item_id IN (SELECT id FROM routine_day_exercise WHERE day_id=?)",
            arrayOf<Any>(dayId)
        )
        w.delete("routine_day_exercise", "day_id=?", arrayOf(dayId.toString()))
    }

    private fun deleteDayRows(w: SQLiteDatabase, dayId: Long) {
        clearExercises(w, dayId)
        w.delete("routine_day", "id=?", arrayOf(dayId.toString()))
    }

    private fun writeExercises(w: SQLiteDatabase, dayId: Long, exercises: List<PlannedExercise>, known: Set<Long>) {
        clearExercises(w, dayId)
        exercises.filter { it.exerciseId in known }.forEachIndexed { i, p ->
            val itemId = w.insertOrThrow("routine_day_exercise", null, ContentValues().apply {
                put("day_id", dayId); put("exercise_id", p.exerciseId); put("sort_order", i); put("fill", p.fill)
                put("superset", p.superset)
            })
            p.sets.forEachIndexed { j, s ->
                w.insertOrThrow("routine_day_set", null, ContentValues().apply {
                    put("item_id", itemId); put("sort_order", j); put("weight", s.weightKg); put("reps", s.reps)
                    put("distance", s.distance); put("duration", s.durationSec); put("set_type", s.setType)
                })
            }
        }
    }

    /** Removes an exercise from every workout day, when the exercise itself is deleted. */
    internal fun forgetExercise(w: SQLiteDatabase, exerciseId: Long) {
        w.execSQL(
            "DELETE FROM routine_day_set WHERE item_id IN (SELECT id FROM routine_day_exercise WHERE exercise_id=?)",
            arrayOf<Any>(exerciseId)
        )
        w.delete("routine_day_exercise", "exercise_id=?", arrayOf(exerciseId.toString()))
    }

    /**
     * The day of [routine] to suggest next: the one after the day logged most recently (wrapping round), or the first
     * day when none has been logged. Only dates that still have sets count, so a deleted workout doesn't move it on.
     */
    fun nextDay(snap: Snapshot, routine: Routine): RoutineDay? {
        if (routine.days.isEmpty()) return null
        val ids = routine.days.map { it.id }
        val last = snap.workoutOrigins.values
            .filter { o -> o.routineDayId?.let { it in ids } == true && snap.setsByDate.containsKey(o.date) }
            .maxByOrNull { it.date } ?: return routine.days.first()
        val at = ids.indexOf(last.routineDayId ?: -1L)
        return routine.days[(at + 1) % routine.days.size]
    }

    /** The workout and day with id [dayId], if it still exists. */
    fun dayById(snap: Snapshot, dayId: Long): Pair<Routine, RoutineDay>? {
        snap.routines.forEach { r -> r.days.firstOrNull { it.id == dayId }?.let { return r to it } }
        return null
    }

    /**
     * The sets [p] adds on [date]. Predefined sets are used as they are, except that a blank weight or reps copies
     * that value from the same set the previous time (as in FitNotes). "Copy previous sets" repeats every set from the
     * last day before [date] the exercise was logged. Empty for "Don't populate any sets", and when there's nothing to
     * go on (a new exercise set to copy the previous time).
     */
    fun resolve(snap: Snapshot, p: PlannedExercise, date: String): List<PlannedSet> {
        if (p.fill == FILL_NONE) return emptyList()
        val history = snap.setsByExercise[p.exerciseId].orEmpty()
        val lastDay = history.filter { it.date < date }.maxOfOrNull { it.date }
        val previous = if (lastDay == null) emptyList() else history.filter { it.date == lastDay }.map { it.toPlanned() }
        if (p.fill == FILL_LAST) return previous
        return p.sets.mapIndexed { i, s ->
            val before = previous.getOrNull(i) ?: previous.lastOrNull()
            if (before == null) s else s.copy(
                weightKg = if (s.weightKg == 0.0) before.weightKg else s.weightKg,
                reps = if (s.reps == 0) before.reps else s.reps
            )
        }.filter { !it.isEmpty }
    }

    /** A logged date as a workout day's exercises: in the order first logged, each with that date's sets. */
    fun fromDate(snap: Snapshot, date: String, fill: Int): List<PlannedExercise> =
        snap.setsByDate[date].orEmpty()
            .groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.position } }
            .map { (exId, sets) -> PlannedExercise(exId, fill, sets.map { it.toPlanned() }, sets.maxOf { it.superset }) }

    private fun SetRow.toPlanned() = PlannedSet(weightKg, reps, distance, durationSec, setType)

    /** How the sets read in lists, for example "3 sets · 100 kg · 5 reps". */
    fun describe(snap: Snapshot, sets: List<PlannedSet>): String {
        if (sets.isEmpty()) return "No sets"
        val first = sets.first()
        val parts = ArrayList<String>()
        if (first.weightKg != 0.0) parts.add("${snap.fmtWeight(first.weightKg)} ${snap.weightUnit}")
        if (first.reps > 0) parts.add("${first.reps} reps")
        if (first.distance > 0) parts.add("${fmtNum(first.distance)} distance")
        if (first.durationSec > 0) parts.add(fmtDuration(first.durationSec))
        val same = sets.all { it == first }
        val head = "${sets.size} set${if (sets.size == 1) "" else "s"}"
        return if (parts.isEmpty()) head else "$head · ${parts.joinToString(" · ")}${if (same) "" else " …"}"
    }

    /** How an exercise's sets are filled, in the words the editor uses. */
    fun fillLabel(fill: Int): String = when (fill) {
        FILL_PLANNED -> "Predefined sets"
        FILL_NONE -> "No sets"
        else -> "Copy previous sets"
    }

    /**
     * v13 (#106): folds the saved workouts (#100) into workout days. Each routine day takes a copy of the exercises
     * and sets of the saved workout it pointed at (a saved workout shared by two days becomes two copies), and each
     * saved workout no day used becomes a one-day workout with its name and notes. `workout_origin` rows are
     * re-pointed at the workout and day (one whose saved workout is gone keeps its row, pointing nowhere). Nothing is lost: the saved-workout rows are only emptied once copied, and the
     * empty tables stay so an older FitLens can still open the database (#77). Replays safely: a day already copied
     * has `workout_id` 0, and a saved workout already copied is gone.
     */
    internal fun migrateSavedWorkouts(db: SQLiteDatabase) {
        listOf(CREATE_EXERCISE, CREATE_SET).forEach { db.execSQL(it.replace("CREATE TABLE", "CREATE TABLE IF NOT EXISTS")) }
        data class Saved(val id: Long, val name: String, val notes: String?)
        val saved = db.rawQuery("SELECT id, name, notes FROM saved_workout ORDER BY sort_order, id", null).use { c ->
            ArrayList<Saved>().apply { while (c.moveToNext()) add(Saved(c.lng(0), c.strOr(1), c.str(2))) }
        }
        fun copyItems(savedId: Long, dayId: Long) {
            val items = db.rawQuery(
                "SELECT id, exercise_id, sort_order, fill, superset FROM saved_workout_exercise WHERE workout_id=? ORDER BY sort_order, id",
                arrayOf(savedId.toString())
            ).use { c -> ArrayList<LongArray>().apply { while (c.moveToNext()) add(longArrayOf(c.lng(0), c.lng(1), c.lng(2), c.lng(3), c.lng(4))) } }
            items.forEach { (oldId, exId, order, fill, superset) ->
                val newId = db.insertOrThrow("routine_day_exercise", null, ContentValues().apply {
                    put("day_id", dayId); put("exercise_id", exId); put("sort_order", order); put("fill", fill); put("superset", superset)
                })
                db.execSQL(
                    "INSERT INTO routine_day_set(item_id, sort_order, weight, reps, distance, duration, set_type) " +
                        "SELECT ?, sort_order, weight, reps, distance, duration, set_type FROM saved_workout_set WHERE item_id=? ORDER BY sort_order, id",
                    arrayOf<Any>(newId, oldId)
                )
            }
        }
        // 1. Each routine day copies the saved workout it used, and remembers the first day that used each one.
        val firstDayOf = HashMap<Long, Pair<Long, Long>>()
        val days = db.rawQuery("SELECT id, routine_id, workout_id FROM routine_day WHERE workout_id > 0 ORDER BY routine_id, sort_order, id", null)
            .use { c -> ArrayList<LongArray>().apply { while (c.moveToNext()) add(longArrayOf(c.lng(0), c.lng(1), c.lng(2))) } }
        val savedIds = saved.map { it.id }.toSet()
        days.forEach { (dayId, routineId, savedId) ->
            if (savedId in savedIds) {
                copyItems(savedId, dayId)
                firstDayOf.getOrPut(savedId) { routineId to dayId }
            }
        }
        // 2. Each saved workout no day used becomes a one-day workout, after the existing ones.
        var order = db.rawQuery("SELECT IFNULL(MAX(sort_order), -1) + 1 FROM routine", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        saved.filter { it.id !in firstDayOf }.forEach { s ->
            val routineId = db.insertOrThrow("routine", null, ContentValues().apply {
                put("name", s.name.ifBlank { "Workout" }); put("notes", s.notes); put("sort_order", order++)
            })
            val dayId = db.insertOrThrow("routine_day", null, ContentValues().apply {
                put("routine_id", routineId); put("name", "Day 1"); put("sort_order", 0)
            })
            copyItems(s.id, dayId)
            firstDayOf[s.id] = routineId to dayId
        }
        // 3. Logged dates point at the workout and day: through their routine day when it still exists, otherwise
        // through the saved workout they were started from.
        db.execSQL(
            "UPDATE workout_origin SET routine_day_id = NULL WHERE routine_day_id IS NOT NULL AND NOT EXISTS " +
                "(SELECT 1 FROM routine_day WHERE routine_day.id = workout_origin.routine_day_id)"
        )
        db.execSQL(
            "UPDATE workout_origin SET workout_id = (SELECT routine_id FROM routine_day WHERE routine_day.id = workout_origin.routine_day_id) " +
                "WHERE routine_day_id IS NOT NULL"
        )
        firstDayOf.forEach { (savedId, target) ->
            db.execSQL(
                "UPDATE workout_origin SET workout_id=?, routine_day_id=? WHERE routine_day_id IS NULL AND workout_id=?",
                arrayOf<Any>(target.first, target.second, savedId)
            )
        }
        // 4. Everything is copied: empty the saved workouts and clear the old pointers.
        db.execSQL("UPDATE routine_day SET workout_id = 0")
        db.execSQL("DELETE FROM saved_workout_set")
        db.execSQL("DELETE FROM saved_workout_exercise")
        db.execSQL("DELETE FROM saved_workout")
    }

    private suspend fun <T> write(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        val w = Store.db.writableDatabase
        w.beginTransaction()
        val result = try {
            block(w).also { w.setTransactionSuccessful() }
        } finally {
            w.endTransaction()
        }
        // Workouts live in the library area; logged sets aren't touched (#60).
        Store.refresh(Area.LIBRARY)
        result
    }
}
