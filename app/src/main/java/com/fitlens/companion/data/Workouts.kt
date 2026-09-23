package com.fitlens.companion.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** A change to workout data that can't be made, with a message that can be shown to the user as it is. */
class WorkoutDataException(message: String) : IllegalArgumentException(message)

/**
 * Create, update and delete workout data in FitLens: categories, exercises, sets, and per-day workout comments and
 * times. A "workout" is every set, comment and time logged on one date, as in FitNotes.
 *
 * ## Ownership
 * Every category, exercise, set, workout comment and workout time has a `source`:
 * - `fitnotes`: imported from a FitNotes backup and not changed since.
 * - `fitlens`: created in FitLens, or an imported row the user has edited in FitLens (editing makes it FitLens's).
 * Rows have FitLens's own stable `id`. Imported rows also keep their FitNotes id in `fitnotes_id`, for reference only.
 *
 * ## Conflict rules (shared with [FitNotesImporter])
 * 1. A FitNotes import only ever **adds** rows. It never deletes, edits or overwrites anything already in FitLens,
 *    whoever created it. FitLens never writes to FitNotes or its backups.
 * 2. Categories and exercises are matched **by name** (ignoring case and surrounding spaces), so imported and
 *    FitLens-logged history join up. When both exist, the FitLens row is kept as it is (its category, type, notes).
 * 3. A set is "already present" when a set on the same date, for the same exercise, with the same weight, reps,
 *    distance and time exists, whoever created it. Identical sets are counted, so three identical sets in a backup
 *    match three in FitLens. Re-importing the same backup therefore changes nothing.
 * 4. Workout comments and times are present when the same text (or the same start and end) exists on that date.
 * 5. What the user does in FitLens wins over later imports: renaming an exercise or category keeps it linked to the
 *    FitNotes name; deleting imported data, or editing an imported set, comment or time, is remembered in
 *    `import_rule` so the next import doesn't bring the original back. Re-creating a deleted exercise or category
 *    with the same name lets its FitNotes history import again.
 * 6. Body measurements merge the same way: a FitNotes value is skipped when the same measurement, date, time and value
 *    exists, or when a value entered by hand in FitLens has the same measurement, date and value.
 */
object Workouts {

    const val UNCATEGORISED = 0L

    // ---------- Keys shared with the importer ----------

    internal const val RULE_CATEGORY = "category"
    internal const val RULE_EXERCISE = "exercise"
    internal const val RULE_SET = "set"
    internal const val RULE_COMMENT = "comment"
    internal const val RULE_TIME = "time"

    internal fun nameKey(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    internal fun setKey(exerciseId: Long, date: String, weight: Double, reps: Int, distance: Double, duration: Int): String =
        String.format(Locale.US, "%d|%s|%.3f|%d|%.3f|%d", exerciseId, date.take(10), weight, reps, distance, duration)

    internal fun commentKey(date: String, comment: String): String = date.take(10) + "|" + comment.trim()

    internal fun timeKey(date: String, start: String?, finish: String?): String =
        date.take(10) + "|" + (start ?: "") + "|" + (finish ?: "")

    /** Remembers that a FitNotes name now maps to [targetId], or is skipped on import when [targetId] is null. */
    internal fun setLink(w: SQLiteDatabase, kind: String, key: String, targetId: Long?) {
        w.delete("import_rule", "kind=? AND key=?", arrayOf(kind, key))
        w.insert("import_rule", null, ContentValues().apply {
            put("kind", kind); put("key", key)
            if (targetId == null) putNull("target_id") else put("target_id", targetId)
        })
    }

    /** One imported row with this key is skipped by later imports. */
    internal fun addSkip(w: SQLiteDatabase, kind: String, key: String) {
        w.insert("import_rule", null, ContentValues().apply { put("kind", kind); put("key", key); putNull("target_id") })
    }

    private fun cleanName(name: String, what: String): String {
        val n = name.trim().replace(Regex("\\s+"), " ")
        if (n.isEmpty()) throw WorkoutDataException("Enter a name for the $what.")
        return n
    }

    private fun checkDate(date: String): String {
        val d = date.take(10)
        if (Dates.parse(d) == null) throw WorkoutDataException("That date isn't valid.")
        return d
    }

    /** Runs [block] in one transaction, then refreshes the in-memory snapshot. */
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

    private fun SQLiteDatabase.longOrNull(sql: String, vararg args: String): Long? =
        rawQuery(sql, args).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

    /** Id of another row in [table] with this name (ignoring case), if any. */
    private fun sameName(w: SQLiteDatabase, table: String, name: String, exceptId: Long = -1L): Long? {
        val key = nameKey(name)
        w.rawQuery("SELECT id, name FROM $table", null).use { c ->
            while (c.moveToNext()) {
                if (c.getLong(0) != exceptId && nameKey(c.strOr(1)) == key) return c.getLong(0)
            }
        }
        return null
    }

    /** A name the user re-creates stops being skipped by imports (see conflict rule 5). */
    private fun clearDeletedLink(w: SQLiteDatabase, kind: String, name: String) {
        w.delete("import_rule", "kind=? AND key=? AND target_id IS NULL", arrayOf(kind, nameKey(name)))
    }

    // ---------- Categories ----------

    suspend fun createCategory(name: String, colour: Int = 0): Long = write { w ->
        val n = cleanName(name, "category")
        if (sameName(w, "category", n) != null) throw WorkoutDataException("There's already a category called $n.")
        val order = w.longOrNull("SELECT IFNULL(MAX(sort_order), 0) + 1 FROM category") ?: 1L
        clearDeletedLink(w, RULE_CATEGORY, n)
        w.insertOrThrow("category", null, ContentValues().apply {
            put("name", n); put("colour", colour); put("sort_order", order); put("source", Sources.FITLENS)
        })
    }

    suspend fun updateCategory(id: Long, name: String, colour: Int): Unit = write { w ->
        val n = cleanName(name, "category")
        val old = w.rawQuery("SELECT name FROM category WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.strOr(0) else null
        } ?: throw WorkoutDataException("That category no longer exists.")
        if (sameName(w, "category", n, exceptId = id) != null) throw WorkoutDataException("There's already a category called $n.")
        if (nameKey(old) != nameKey(n)) {
            setLink(w, RULE_CATEGORY, nameKey(old), id)
            clearDeletedLink(w, RULE_CATEGORY, n)
        }
        w.update("category", ContentValues().apply {
            put("name", n); put("colour", colour); put("source", Sources.FITLENS)
        }, "id=?", arrayOf(id.toString()))
    }

    /** Deletes a category. Its exercises and their history are kept and become uncategorised. */
    suspend fun deleteCategory(id: Long): Unit = write { w ->
        val row = w.rawQuery("SELECT name, fitnotes_id FROM category WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.strOr(0) to !c.isNull(1) else null
        } ?: return@write
        val linked = w.longOrNull("SELECT 1 FROM import_rule WHERE kind=? AND target_id=? LIMIT 1", RULE_CATEGORY, id.toString()) != null
        w.execSQL("UPDATE exercise SET category_id=? WHERE category_id=?", arrayOf<Any>(UNCATEGORISED, id))
        w.delete("category", "id=?", arrayOf(id.toString()))
        w.execSQL("UPDATE import_rule SET target_id=NULL WHERE kind=? AND target_id=?", arrayOf<Any>(RULE_CATEGORY, id))
        if (row.second || linked) setLink(w, RULE_CATEGORY, nameKey(row.first), null)
    }

    // ---------- Exercises ----------

    /** [type] uses the FitNotes exercise types (0 = weight and reps, 1 = distance and time, 3 = time). */
    suspend fun createExercise(name: String, categoryId: Long, type: Int = 0, notes: String? = null): Long = write { w ->
        val n = cleanName(name, "exercise")
        if (sameName(w, "exercise", n) != null) throw WorkoutDataException("There's already an exercise called $n.")
        clearDeletedLink(w, RULE_EXERCISE, n)
        w.insertOrThrow("exercise", null, ContentValues().apply {
            put("name", n); put("category_id", categoryId); put("type", type)
            put("notes", notes?.takeIf { it.isNotBlank() }); put("source", Sources.FITLENS)
        })
    }

    suspend fun updateExercise(id: Long, name: String, categoryId: Long, type: Int, notes: String?): Unit = write { w ->
        val n = cleanName(name, "exercise")
        val old = w.rawQuery("SELECT name FROM exercise WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.strOr(0) else null
        } ?: throw WorkoutDataException("That exercise no longer exists.")
        if (sameName(w, "exercise", n, exceptId = id) != null) throw WorkoutDataException("There's already an exercise called $n.")
        if (nameKey(old) != nameKey(n)) {
            setLink(w, RULE_EXERCISE, nameKey(old), id)
            clearDeletedLink(w, RULE_EXERCISE, n)
        }
        w.update("exercise", ContentValues().apply {
            put("name", n); put("category_id", categoryId); put("type", type)
            put("notes", notes?.takeIf { it.isNotBlank() }); put("source", Sources.FITLENS)
        }, "id=?", arrayOf(id.toString()))
    }

    /** Deletes an exercise and every set logged for it. */
    suspend fun deleteExercise(id: Long): Unit = write { w ->
        val row = w.rawQuery("SELECT name, fitnotes_id FROM exercise WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.strOr(0) to !c.isNull(1) else null
        } ?: return@write
        val hadImports = row.second ||
            w.longOrNull("SELECT 1 FROM workout_set WHERE exercise_id=? AND fitnotes_id IS NOT NULL LIMIT 1", id.toString()) != null ||
            w.longOrNull("SELECT 1 FROM import_rule WHERE kind=? AND target_id=? LIMIT 1", RULE_EXERCISE, id.toString()) != null
        w.delete("workout_set", "exercise_id=?", arrayOf(id.toString()))
        w.delete("exercise", "id=?", arrayOf(id.toString()))
        w.execSQL("UPDATE import_rule SET target_id=NULL WHERE kind=? AND target_id=?", arrayOf<Any>(RULE_EXERCISE, id))
        if (hadImports) setLink(w, RULE_EXERCISE, nameKey(row.first), null)
    }

    // ---------- Sets ----------

    suspend fun addSet(
        exerciseId: Long,
        date: String,
        weightKg: Double,
        reps: Int,
        distance: Double = 0.0,
        durationSec: Int = 0,
        comment: String? = null,
        isPr: Boolean = false
    ): Long = write { w ->
        val d = checkDate(date)
        w.longOrNull("SELECT id FROM exercise WHERE id=?", exerciseId.toString())
            ?: throw WorkoutDataException("That exercise no longer exists.")
        w.insertOrThrow("workout_set", null, ContentValues().apply {
            put("exercise_id", exerciseId); put("date", d); put("weight", weightKg); put("reps", reps)
            put("distance", distance); put("duration", durationSec); put("is_pr", if (isPr) 1 else 0)
            put("comment", comment?.takeIf { it.isNotBlank() }); put("source", Sources.FITLENS)
        })
    }

    /** Saves changes to a set (matched by [SetRow.id]). An edited imported set becomes FitLens's own. */
    suspend fun updateSet(set: SetRow): Unit = write { w ->
        val d = checkDate(set.date)
        val old = w.rawQuery(
            "SELECT exercise_id, date, weight, reps, distance, duration, source FROM workout_set WHERE id=?",
            arrayOf(set.id.toString())
        ).use { c ->
            if (c.moveToFirst()) (c.strOr(6) to setKey(c.lng(0), c.strOr(1), c.dbl(2), c.int(3), c.dbl(4), c.int(5))) else null
        } ?: throw WorkoutDataException("That set no longer exists.")
        val newKey = setKey(set.exerciseId, d, set.weightKg, set.reps, set.distance, set.durationSec)
        if (old.first == Sources.FITNOTES && old.second != newKey) addSkip(w, RULE_SET, old.second)
        w.update("workout_set", ContentValues().apply {
            put("exercise_id", set.exerciseId); put("date", d); put("weight", set.weightKg); put("reps", set.reps)
            put("distance", set.distance); put("duration", set.durationSec); put("is_pr", if (set.isPr) 1 else 0)
            put("comment", set.comment?.takeIf { it.isNotBlank() }); put("source", Sources.FITLENS)
        }, "id=?", arrayOf(set.id.toString()))
    }

    suspend fun deleteSet(id: Long): Unit = write { w -> deleteSetsWhere(w, "id=?", arrayOf(id.toString())) }

    private fun deleteSetsWhere(w: SQLiteDatabase, where: String, args: Array<String>) {
        w.rawQuery("SELECT exercise_id, date, weight, reps, distance, duration FROM workout_set WHERE ($where) AND source=?",
            args + Sources.FITNOTES).use { c ->
            while (c.moveToNext()) addSkip(w, RULE_SET, setKey(c.lng(0), c.strOr(1), c.dbl(2), c.int(3), c.dbl(4), c.int(5)))
        }
        w.delete("workout_set", where, args)
    }

    // ---------- Workouts (everything on one date) ----------

    /** Replaces the workout comment for [date]. A blank comment removes it. */
    suspend fun setWorkoutComment(date: String, comment: String?): Unit = write { w ->
        val d = checkDate(date)
        w.rawQuery("SELECT comment FROM workout_comment WHERE date=? AND source=?", arrayOf(d, Sources.FITNOTES)).use { c ->
            while (c.moveToNext()) addSkip(w, RULE_COMMENT, commentKey(d, c.strOr(0)))
        }
        w.delete("workout_comment", "date=?", arrayOf(d))
        val text = comment?.trim()
        if (!text.isNullOrEmpty()) {
            w.insert("workout_comment", null, ContentValues().apply { put("date", d); put("comment", text); put("source", Sources.FITLENS) })
        }
    }

    /**
     * Replaces the workout start and end for [date]. Times use FitNotes's format (`yyyy-MM-dd HH:mm:ss`).
     * Both null removes them.
     */
    suspend fun setWorkoutTime(date: String, start: String?, finish: String?): Unit = write { w ->
        val d = checkDate(date)
        w.rawQuery("SELECT start, finish FROM workout_time WHERE date=? AND source=?", arrayOf(d, Sources.FITNOTES)).use { c ->
            while (c.moveToNext()) addSkip(w, RULE_TIME, timeKey(d, c.str(0), c.str(1)))
        }
        w.delete("workout_time", "date=?", arrayOf(d))
        if (start != null || finish != null) {
            w.insert("workout_time", null, ContentValues().apply {
                put("date", d); put("start", start); put("finish", finish); put("source", Sources.FITLENS)
            })
        }
    }

    /** Deletes the whole workout on [date]: its sets, comment and times. Measurements and photos are kept. */
    suspend fun deleteWorkout(date: String): Unit = write { w ->
        val d = checkDate(date)
        deleteSetsWhere(w, "date=?", arrayOf(d))
        w.rawQuery("SELECT comment FROM workout_comment WHERE date=? AND source=?", arrayOf(d, Sources.FITNOTES)).use { c ->
            while (c.moveToNext()) addSkip(w, RULE_COMMENT, commentKey(d, c.strOr(0)))
        }
        w.rawQuery("SELECT start, finish FROM workout_time WHERE date=? AND source=?", arrayOf(d, Sources.FITNOTES)).use { c ->
            while (c.moveToNext()) addSkip(w, RULE_TIME, timeKey(d, c.str(0), c.str(1)))
        }
        w.delete("workout_comment", "date=?", arrayOf(d))
        w.delete("workout_time", "date=?", arrayOf(d))
    }
}
