package com.fitlens.companion.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Routines (#21). In the owner's vocabulary a **routine** is the user's own list of saved workouts (#100) split into
 * days they name ("Push", "Day A", "Monday"): each day uses a saved workout rather than holding loose exercises.
 * Routines live in their own tables (database v8), so they travel in `.fitlens` backups and FitNotes imports never
 * touch them. `workout_origin` remembers which saved workout (and routine day) a logged day was started from, which
 * is how the next day of a routine is suggested.
 */

/** One day of a routine. [workoutId] is the saved workout it uses; 0 means none chosen yet. */
data class RoutineDay(val id: Long, val name: String, val workoutId: Long)

/** A routine: its name, notes and days in order. An [id] of 0 is one not saved yet. */
data class Routine(
    val id: Long,
    val name: String,
    val notes: String? = null,
    val sortOrder: Int = 0,
    val days: List<RoutineDay> = emptyList()
)

/** Which saved workout, and which routine day if any, a logged day was started from. */
data class WorkoutOrigin(val date: String, val workoutId: Long, val routineDayId: Long?)

object Routines {
    const val CREATE_ROUTINE =
        "CREATE TABLE routine(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, notes TEXT, " +
            "sort_order INTEGER NOT NULL DEFAULT 0)"
    const val CREATE_DAY =
        "CREATE TABLE routine_day(id INTEGER PRIMARY KEY AUTOINCREMENT, routine_id INTEGER NOT NULL, " +
            "name TEXT NOT NULL, workout_id INTEGER NOT NULL DEFAULT 0, sort_order INTEGER NOT NULL DEFAULT 0)"
    const val CREATE_ORIGIN =
        "CREATE TABLE workout_origin(date TEXT PRIMARY KEY, workout_id INTEGER NOT NULL, routine_day_id INTEGER)"

    fun load(r: SQLiteDatabase): List<Routine> {
        val days = HashMap<Long, MutableList<RoutineDay>>()
        r.rawQuery("SELECT id, routine_id, name, workout_id FROM routine_day ORDER BY routine_id, sort_order, id", null).use { c ->
            while (c.moveToNext()) days.getOrPut(c.lng(1)) { ArrayList() }.add(RoutineDay(c.lng(0), c.strOr(2), c.lng(3)))
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
     * Creates the routine (id 0) or updates it. Days keep their ids when they're kept (a day with id 0 is new), so a
     * logged day still knows which routine day it was, and days that were removed are deleted. Returns the id.
     */
    suspend fun save(routine: Routine): Long = write { w ->
        val name = routine.name.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) throw WorkoutDataException("Enter a name for the routine.")
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
        w.delete(
            "routine_day",
            "routine_id=?" + if (kept.isEmpty()) "" else " AND id NOT IN (${kept.joinToString(",")})",
            arrayOf(id.toString())
        )
        routine.days.forEachIndexed { i, d ->
            val dv = ContentValues().apply {
                put("routine_id", id); put("name", d.name.trim().ifBlank { "Day ${i + 1}" })
                put("workout_id", d.workoutId); put("sort_order", i)
            }
            if (d.id > 0) w.update("routine_day", dv, "id=?", arrayOf(d.id.toString())) else w.insertOrThrow("routine_day", null, dv)
        }
        id
    }

    suspend fun delete(id: Long): Unit = write { w ->
        w.delete("routine_day", "routine_id=?", arrayOf(id.toString()))
        w.delete("routine", "id=?", arrayOf(id.toString()))
    }

    /** A copy of [routine] with new ids, named [name]. Returns the new id. */
    suspend fun copy(routine: Routine, name: String): Long =
        save(routine.copy(id = 0L, name = name, days = routine.days.map { it.copy(id = 0L) }))

    /** Adds a day using [workoutId] to the end of routine [routineId] (#99). Returns the new day's id. */
    suspend fun addDay(routineId: Long, name: String, workoutId: Long): Long = write { w ->
        val next = w.rawQuery("SELECT IFNULL(MAX(sort_order), -1) + 1 FROM routine_day WHERE routine_id=?", arrayOf(routineId.toString()))
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        w.insertOrThrow("routine_day", null, ContentValues().apply {
            put("routine_id", routineId); put("name", name.trim().ifBlank { "Day ${next + 1}" })
            put("workout_id", workoutId); put("sort_order", next)
        })
    }

    /** Removes one routine day, used to undo [addDay]. */
    suspend fun deleteDay(dayId: Long): Unit = write { w ->
        w.delete("routine_day", "id=?", arrayOf(dayId.toString()))
    }

    /** Points routine day [dayId] at [workoutId] (#99). */
    suspend fun setDayWorkout(dayId: Long, workoutId: Long): Unit = write { w ->
        w.update("routine_day", ContentValues().apply { put("workout_id", workoutId) }, "id=?", arrayOf(dayId.toString()))
    }

    /**
     * The day of [routine] to suggest next: the one after the day logged most recently (wrapping round), or the first
     * day when none has been logged. Only days that still have sets count, so a deleted workout doesn't move it on.
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
