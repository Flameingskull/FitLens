package com.fitlens.companion.data

import android.content.ContentValues
import android.content.res.Resources
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitlens.companion.R
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
    val setType: Int = SetTypes.WORKING,
    /** The rest after this set (#138); null uses the exercise's prescribed rest, then its own, then the global one. */
    val restSeconds: Int? = null,
    /** The value of its exercise's custom metric (#14, #155), or null when none is set. 0 is a real value. */
    val metric: Double? = null
) {
    val isEmpty: Boolean get() = weightKg == 0.0 && reps == 0 && distance == 0.0 && durationSec == 0 && metric == null
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
    val superset: Int = 0,
    /** The rest between its sets when a set has none of its own (#138); null for none prescribed. */
    val restSeconds: Int? = null,
    /** The rest after its last set, before the next exercise (#138); null for none prescribed. */
    val restAfterSeconds: Int? = null
) {
    /** The rest prescribed for this exercise on the day it's logged (#138). */
    val rest: WorkoutRest get() = WorkoutRest(restSeconds, restAfterSeconds)
}

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

    /**
     * "Copy previous rest" (#151), stored in the rest columns in place of a length: the rest between sets, before the
     * next exercise and after each set comes from the exercise's most recent workout, as "Copy previous sets" does
     * for its sets. No schema change: a real rest is always at least a second.
     */
    const val REST_PREVIOUS = -1

    /** Whether [p] copies its rest from the previous workout (#151). */
    fun copiesRest(p: PlannedExercise): Boolean = p.restSeconds == REST_PREVIOUS || p.restAfterSeconds == REST_PREVIOUS

    /** The last date before [date] that exercise [exId] was logged, or null. */
    private fun previousDay(snap: Snapshot, exId: Long, date: String): String? =
        snap.setsByExercise[exId].orEmpty().filter { it.date < date }.maxOfOrNull { it.date }

    /**
     * The rest [p] prescribes when logged on [date] (#138). "Copy previous rest" (#151) takes each part from the
     * exercise's most recent workout before [date]; a part that workout didn't prescribe stays unset, so the
     * exercise's usual rest applies.
     */
    fun resolveRest(snap: Snapshot, p: PlannedExercise, date: String): WorkoutRest {
        if (!copiesRest(p)) return p.rest
        val prev = previousDay(snap, p.exerciseId, date)?.let { snap.workoutRests[it.take(10)]?.get(p.exerciseId) }
        return WorkoutRest(
            if (p.restSeconds == REST_PREVIOUS) prev?.restSeconds else p.restSeconds,
            if (p.restAfterSeconds == REST_PREVIOUS) prev?.restAfterSeconds else p.restAfterSeconds
        )
    }

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
            "superset INTEGER NOT NULL DEFAULT 0, rest_seconds INTEGER, rest_after_seconds INTEGER)"
    const val CREATE_SET =
        "CREATE TABLE routine_day_set(id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL, " +
            "sort_order INTEGER NOT NULL DEFAULT 0, weight REAL NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, " +
            "distance REAL NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, set_type INTEGER NOT NULL DEFAULT 0, " +
            "rest_seconds INTEGER, metric REAL)"

    /** Every workout with its days, their exercises and planned sets, in the user's order (#36: typed queries). */
    fun load(r: SnapshotDao): List<Routine> {
        val sets = HashMap<Long, MutableList<PlannedSet>>()
        r.routineDaySets().forEach { sets.getOrPut(it.item_id) { ArrayList() }.add(it.toModel()) }
        val items = HashMap<Long, MutableList<PlannedExercise>>()
        r.routineDayExercises().forEach {
            items.getOrPut(it.day_id) { ArrayList() }.add(
                PlannedExercise(it.exercise_id, it.fill, sets[it.id].orEmpty(), it.superset, it.rest_seconds, it.rest_after_seconds)
            )
        }
        val days = HashMap<Long, MutableList<RoutineDay>>()
        r.routineDays().forEach { days.getOrPut(it.routine_id) { ArrayList() }.add(RoutineDay(it.id, it.name, items[it.id].orEmpty())) }
        return r.routines().map { Routine(it.id, it.name, it.notes, it.sort_order, days[it.id].orEmpty()) }
    }

    fun loadOrigins(r: SnapshotDao): Map<String, WorkoutOrigin> =
        HashMap<String, WorkoutOrigin>().apply {
            r.workoutOrigins().forEach { put(it.date, WorkoutOrigin(it.date, it.workout_id, it.routine_day_id)) }
        }

    /**
     * Creates the workout (id 0) or updates it. Days keep their ids when they're kept (a day with id 0 is new), so a
     * logged date still knows which day it was; removed days are deleted. Each day's exercises are small, so they are
     * rewritten rather than diffed, and exercises that no longer exist are dropped. Returns the workout's id.
     */
    suspend fun save(routine: Routine): Long = write { r ->
        val name = routine.name.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) throw WorkoutDataException(R.string.wde_name_workout)

        val notes = routine.notes?.trim()?.ifBlank { null }
        val id = if (routine.id == 0L) {
            r.addRoutine(RoutineRow(0L, name, notes, r.nextRoutineOrder()))
        } else {
            r.updateRoutine(routine.id, name, notes)
            routine.id
        }
        val kept = routine.days.map { it.id }.filter { it > 0 }
        r.dayIds(id).filter { it !in kept }.forEach { deleteDayRows(r, it) }
        val known = r.exerciseIds().toHashSet()
        routine.days.forEachIndexed { i, d ->
            val dayId = if (d.id > 0) {
                r.updateDay(d.id, id, dayName(d.name, i), i)
                d.id
            } else r.addDay(id, dayName(d.name, i), i)
            writeExercises(r, dayId, d.exercises, known)
        }
        id
    }

    suspend fun delete(id: Long): Unit = write { r ->
        r.dayIds(id).forEach { deleteDayRows(r, it) }
        r.deleteRoutine(id)
    }

    /** A copy of [routine] with new ids, named [name]. Returns the new id. */
    suspend fun copy(routine: Routine, name: String): Long =
        save(routine.copy(id = 0L, name = name, days = routine.days.map { it.copy(id = 0L) }))

    /** Adds a day holding [exercises] to the end of workout [routineId]. Returns the new day's id. */
    suspend fun addDay(routineId: Long, name: String, exercises: List<PlannedExercise>): Long = write { r ->
        val next = r.nextDayOrder(routineId)
        val dayId = r.addDay(routineId, dayName(name, next), next)
        writeExercises(r, dayId, exercises, r.exerciseIds().toHashSet())
        dayId
    }

    /** Removes one day, used to undo [addDay]. */
    suspend fun deleteDay(dayId: Long): Unit = write { r -> deleteDayRows(r, dayId) }

    /** Replaces day [dayId]'s exercises with [exercises] ("Save as a workout day" into an existing day). */
    suspend fun setDayExercises(dayId: Long, exercises: List<PlannedExercise>): Unit = write { r ->
        writeExercises(r, dayId, exercises, r.exerciseIds().toHashSet())
    }

    private fun dayName(name: String, index: Int) = name.trim().replace(Regex("\\s+"), " ").ifBlank { "Day ${index + 1}" }

    private fun clearExercises(r: RoutineDao, dayId: Long) {
        r.deletePlannedSetsOfDay(dayId)
        r.deleteItemsOfDay(dayId)
    }

    private fun deleteDayRows(r: RoutineDao, dayId: Long) {
        clearExercises(r, dayId)
        r.deleteDay(dayId)
    }

    private fun writeExercises(r: RoutineDao, dayId: Long, exercises: List<PlannedExercise>, known: Set<Long>) {
        clearExercises(r, dayId)
        exercises.filter { it.exerciseId in known }.forEachIndexed { i, p ->
            val itemId = r.addItem(
                RoutineDayExerciseRow(0L, dayId, p.exerciseId, i, p.fill, p.superset, p.restSeconds, p.restAfterSeconds)
            )
            p.sets.forEachIndexed { j, s ->
                r.addPlannedSet(
                    RoutineDaySetRow(
                        0L, itemId, j, s.weightKg, s.reps, s.distance, s.durationSec.toLong(), s.setType, s.restSeconds, s.metric
                    )
                )
            }
        }
    }

    /** Removes an exercise from every workout day, when the exercise itself is deleted (inside that write). */
    internal fun forgetExercise(r: RoutineDao, exerciseId: Long) {
        r.deletePlannedSetsOfExercise(exerciseId)
        r.deleteItemsOfExercise(exerciseId)
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
     * The sets [p] adds on [date]. Predefined sets are used as they are, except that a blank weight, reps or custom
     * metric (#155) copies that value from the same set the previous time (as in FitNotes). "Copy previous sets"
     * repeats every set from the last day before [date] the exercise was logged. Empty for "Don't populate any sets",
     * and when there's nothing to go on (a new exercise set to copy the previous time).
     */
    fun resolve(snap: Snapshot, p: PlannedExercise, date: String): List<PlannedSet> {
        if (p.fill == FILL_NONE) return emptyList()
        val history = snap.setsByExercise[p.exerciseId].orEmpty()
        val lastDay = previousDay(snap, p.exerciseId, date)
        val previous = if (lastDay == null) emptyList() else history.filter { it.date == lastDay }.map { it.toPlanned() }
        // "Copy previous rest" (#151) keeps each set's rest from last time; otherwise copied sets take the day's
        // prescribed rest, not the rest they were logged with (#138).
        val copyRest = copiesRest(p)
        if (p.fill == FILL_LAST) return previous.map { if (copyRest) it else it.copy(restSeconds = null) }
        return p.sets.mapIndexed { i, s ->
            val before = previous.getOrNull(i) ?: previous.lastOrNull()
            val rest = if (copyRest || s.restSeconds == REST_PREVIOUS) previous.getOrNull(i)?.restSeconds else s.restSeconds
            if (before == null) s.copy(restSeconds = rest) else s.copy(
                weightKg = if (s.weightKg == 0.0) before.weightKg else s.weightKg,
                reps = if (s.reps == 0) before.reps else s.reps,
                metric = s.metric ?: before.metric,
                restSeconds = rest
            )
        }.filter { !it.isEmpty }
    }

    /** A logged date as a workout day's exercises: in the order first logged, each with that date's sets. */
    fun fromDate(snap: Snapshot, date: String, fill: Int, only: Set<Long>? = null): List<PlannedExercise> =
        snap.setsByDate[date].orEmpty()
            // Create Workout's checklist (#148) can leave sets out.
            .filter { only == null || it.id in only }
            .groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.position } }
            .map { (exId, sets) ->
                // The logged workout's prescribed rest (#138) comes along when a date is saved as a workout day.
                val rest = snap.workoutRests[date]?.get(exId)
                PlannedExercise(exId, fill, sets.map { it.toPlanned() }, sets.maxOf { it.superset }, rest?.restSeconds, rest?.restAfterSeconds)
            }

    private fun SetRow.toPlanned() = PlannedSet(weightKg, reps, distance, durationSec, setType, restSeconds, metric)

    /** How the sets read in lists, for example "3 sets · 100 kg · 5 reps", in the words of `strings.xml` (#94). */
    fun describe(res: Resources, snap: Snapshot, sets: List<PlannedSet>, exerciseId: Long? = null): String {
        if (sets.isEmpty()) return res.getString(R.string.plan_no_sets)
        val first = sets.first()
        val parts = ArrayList<String>()
        if (first.weightKg != 0.0) parts.add("${snap.fmtWeight(first.weightKg, exerciseId)} ${snap.weightUnitOf(exerciseId)}")
        if (first.reps > 0) parts.add(res.getQuantityString(R.plurals.plan_reps, first.reps, first.reps))
        if (first.distance > 0) parts.add("${fmtNum(first.distance)} ${exerciseId?.let { snap.distanceUnit(it) } ?: snap.globalDistanceUnit}")
        if (first.durationSec > 0) parts.add(fmtDuration(first.durationSec))
        first.metric?.let { m ->
            val unit = exerciseId?.let { snap.exercises[it] }?.let { ExerciseTypes.metricOf(it.type) }?.metricUnit
            parts.add(fmtNum(m) + unit?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty())
        }
        val same = sets.all { it == first }
        val head = res.getQuantityString(R.plurals.plan_sets, sets.size, sets.size)
        return if (parts.isEmpty()) head else "$head · ${parts.joinToString(" · ")}${if (same) "" else " …"}"
    }

    /**
     * v13 (#106): folds the saved workouts (#100) into workout days. Each routine day takes a copy of the exercises
     * and sets of the saved workout it pointed at (a saved workout shared by two days becomes two copies), and each
     * saved workout no day used becomes a one-day workout with its name and notes. `workout_origin` rows are
     * re-pointed at the workout and day (one whose saved workout is gone keeps its row, pointing nowhere). Nothing is lost: the saved-workout rows are only emptied once copied, and the
     * empty tables stay so an older FitLens can still open the database (#77). Replays safely: a day already copied
     * has `workout_id` 0, and a saved workout already copied is gone.
     */
    internal fun migrateSavedWorkouts(db: SupportSQLiteDatabase) {
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

    private suspend fun <T> write(block: (RoutineDao) -> T): T = withContext(Dispatchers.IO) {
        val db = Store.db
        val result = db.transaction { block(db.routineDao) }
        // Workouts live in the library area; logged sets aren't touched (#60).
        Store.refresh(Area.LIBRARY)
        result
    }
}
