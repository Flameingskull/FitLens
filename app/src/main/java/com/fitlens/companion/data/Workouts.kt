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

    /**
     * An exercise's own defaults (#15): its weight step in kg (null uses the global step) and the graph it opens on
     * (-1 for the first). Kept apart from [updateExercise] so a rename never touches them.
     */
    suspend fun setExerciseDefaults(id: Long, weightStepKg: Double?, defaultGraph: Int): Unit = write { w ->
        w.update("exercise", ContentValues().apply {
            if (weightStepKg == null) putNull("weight_step") else put("weight_step", weightStepKg)
            put("default_graph", defaultGraph)
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
        w.delete("exercise_goal", "exercise_id=?", arrayOf(id.toString()))
        w.delete("exercise", "id=?", arrayOf(id.toString()))
        w.execSQL("UPDATE import_rule SET target_id=NULL WHERE kind=? AND target_id=?", arrayOf<Any>(RULE_EXERCISE, id))
        if (hadImports) setLink(w, RULE_EXERCISE, nameKey(row.first), null)
    }

    /** Stars or unstars an exercise. Favourites are listed first when choosing an exercise. */
    suspend fun setFavourite(id: Long, favourite: Boolean): Unit = write { w ->
        w.update("exercise", ContentValues().apply { put("favourite", if (favourite) 1 else 0) }, "id=?", arrayOf(id.toString()))
    }

    /**
     * Adds the [StarterLibrary] categories and exercises that aren't in the library yet, for someone starting
     * without a FitNotes backup. Only ever called when the user asks for it.
     *
     * A name that already exists (ignoring case, whoever created it) is left exactly as it is: nothing is renamed,
     * re-filed, overwritten or deleted, so running it on a library full of imported FitNotes exercises is safe.
     * [palette] gives the colours to hand out to the categories it creates, in order.
     */
    suspend fun seedStarterLibrary(palette: List<Int>): SeedResult = write { w ->
        var categoriesAdded = 0
        var exercisesAdded = 0
        var skipped = 0
        var order = w.longOrNull("SELECT IFNULL(MAX(sort_order), 0) FROM category") ?: 0L
        StarterLibrary.categories.forEachIndexed { i, sc ->
            val categoryId = sameName(w, "category", sc.name) ?: run {
                order += 1
                categoriesAdded += 1
                clearDeletedLink(w, RULE_CATEGORY, sc.name)
                w.insertOrThrow("category", null, ContentValues().apply {
                    put("name", sc.name)
                    put("colour", if (palette.isEmpty()) 0 else palette[i % palette.size])
                    put("sort_order", order)
                    put("source", Sources.FITLENS)
                })
            }
            sc.exercises.forEach { se ->
                if (sameName(w, "exercise", se.name) != null) {
                    skipped += 1
                } else {
                    clearDeletedLink(w, RULE_EXERCISE, se.name)
                    w.insertOrThrow("exercise", null, ContentValues().apply {
                        put("name", se.name); put("category_id", categoryId); put("type", se.type)
                        put("source", Sources.FITLENS)
                    })
                    exercisesAdded += 1
                }
            }
        }
        SeedResult(categoriesAdded, exercisesAdded, skipped)
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
        /**
         * Null works it out: the set is a PR when it's heavier than every set of at least as many reps logged on or
         * before its date, the same rule [recalculatePrs] replays (#23).
         */
        isPr: Boolean? = null,
        setType: Int = SetTypes.WORKING,
        rpe: Double? = null
    ): Long = write { w ->
        val d = checkDate(date)
        w.longOrNull("SELECT id FROM exercise WHERE id=?", exerciseId.toString())
            ?: throw WorkoutDataException("That exercise no longer exists.")
        val countWarmups = Settings.currentPortable().warmupsCount
        // A warm-up is never a record unless warm-ups count, and uncounted warm-ups never set the bar (#43).
        val pr = if (setType == SetTypes.WARMUP && !countWarmups) false else isPr ?: Records.isNewRecord(
            weightKg, reps,
            w.rawQuery(
                "SELECT MAX(weight) FROM workout_set WHERE exercise_id=? AND reps>=? AND weight>0 AND substr(date, 1, 10)<=?" +
                    if (countWarmups) "" else " AND set_type<>${SetTypes.WARMUP}",
                arrayOf(exerciseId.toString(), reps.toString(), d)
            ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0) else null }
        )
        w.insertOrThrow("workout_set", null, ContentValues().apply {
            put("exercise_id", exerciseId); put("date", d); put("weight", weightKg); put("reps", reps)
            put("distance", distance); put("duration", durationSec); put("is_pr", if (pr) 1 else 0)
            put("comment", comment?.takeIf { it.isNotBlank() }); put("source", Sources.FITLENS)
            put("set_type", setType); putRpe(rpe)
        })
    }

    private fun ContentValues.putRpe(rpe: Double?) {
        if (rpe == null) putNull("rpe") else put("rpe", rpe)
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
            put("set_type", set.setType); putRpe(set.rpe)
        }, "id=?", arrayOf(set.id.toString()))
    }

    suspend fun deleteSet(id: Long): Unit = write { w -> deleteSetsWhere(w, "id=?", arrayOf(id.toString())) }

    /**
     * Rebuilds the PR mark on every weight-and-reps set, imported ones included (#23). Each exercise is replayed in
     * date order (and log order within a day), and a set is a PR when [Records.isNewRecord] says it beats every
     * earlier set of at least as many reps. Sets without weight and reps (cardio, timed) keep the mark they have.
     * Only the mark changes: the set keeps its source, so an imported set stays imported. Returns how many changed.
     */
    suspend fun recalculatePrs(): Int = write { w -> replayPrs(w) }

    /**
     * Deletes the sets between [from] and [to] (inclusive ISO dates, null for open-ended) for [exerciseIds], or for
     * every exercise when it's empty (#32). Exercises, categories, workout comments and times, photos and body data
     * are kept. Imported sets leave a skip rule, like a single delete, so the next FitNotes import doesn't bring them
     * back. PR marks are replayed in the same transaction, since the deleted sets may have held records.
     * Returns how many sets were deleted.
     */
    suspend fun deleteHistory(from: String?, to: String?, exerciseIds: Set<Long>): Int = write { w ->
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (from != null) { clauses += "substr(date, 1, 10) >= ?"; args += from }
        if (to != null) { clauses += "substr(date, 1, 10) <= ?"; args += to }
        if (exerciseIds.isNotEmpty()) clauses += "exercise_id IN (${exerciseIds.joinToString(",")})"
        val where = clauses.ifEmpty { listOf("1=1") }.joinToString(" AND ")
        val count = w.rawQuery("SELECT COUNT(*) FROM workout_set WHERE $where", args.toTypedArray()).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        if (count > 0) {
            deleteSetsWhere(w, where, args.toTypedArray())
            replayPrs(w)
        }
        count
    }

    private fun replayPrs(w: SQLiteDatabase): Int {
        val changes = mutableListOf<Pair<Long, Boolean>>()
        var exercise = -1L
        // best[r] = heaviest weight so far for at least r reps, for the exercise being replayed.
        var best = DoubleArray(0)
        val countWarmups = Settings.currentPortable().warmupsCount
        w.rawQuery(
            "SELECT id, exercise_id, weight, reps, is_pr, set_type FROM workout_set WHERE weight>0 AND reps>0 " +
                "ORDER BY exercise_id, substr(date, 1, 10), id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val exId = c.lng(1)
                if (exId != exercise) { exercise = exId; best = DoubleArray(0) }
                // An uncounted warm-up loses any PR mark and doesn't set the bar for later sets (#43).
                if (!countWarmups && c.int(5) == SetTypes.WARMUP) {
                    if (c.int(4) != 0) changes += c.lng(0) to false
                    continue
                }
                val weight = c.dbl(2)
                val reps = c.int(3)
                if (best.size <= reps) best = best.copyOf(reps + 1)
                val pr = Records.isNewRecord(weight, reps, best[reps].takeIf { it > 0 })
                for (r in 1..reps) if (weight > best[r]) best[r] = weight
                if (pr != (c.int(4) != 0)) changes += c.lng(0) to pr
            }
        }
        changes.forEach { (id, pr) ->
            w.update("workout_set", ContentValues().apply { put("is_pr", if (pr) 1 else 0) }, "id=?", arrayOf(id.toString()))
        }
        return changes.size
    }

    /**
     * Puts whole sets back in one transaction, used to undo a delete. They return as FitLens's own rows on the
     * date they carry; their old ids are not reused. Returns how many were added.
     */
    suspend fun addSets(rows: List<SetRow>): Int = write { w ->
        rows.forEach { s ->
            w.insertOrThrow("workout_set", null, ContentValues().apply {
                put("exercise_id", s.exerciseId); put("date", s.date.take(10)); put("weight", s.weightKg)
                put("reps", s.reps); put("distance", s.distance); put("duration", s.durationSec)
                put("is_pr", if (s.isPr) 1 else 0); put("comment", s.comment?.takeIf { it.isNotBlank() })
                put("source", Sources.FITLENS); put("set_type", s.setType); putRpe(s.rpe)
            })
            // Deleting an imported set left one skip rule; the set is back, so drop one matching rule too (#76).
            if (s.imported) {
                w.execSQL(
                    "DELETE FROM import_rule WHERE rowid = (SELECT rowid FROM import_rule " +
                        "WHERE kind=? AND key=? AND target_id IS NULL LIMIT 1)",
                    arrayOf<Any>(RULE_SET, setKey(s.exerciseId, s.date, s.weightKg, s.reps, s.distance, s.durationSec))
                )
            }
        }
        rows.size
    }

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

    /**
     * Restores every time row for [date] at once. [setWorkoutTime] keeps only one pair, which is right when the
     * user is editing a single start/finish, but loses rows when undoing a delete on a day that carried several
     * (an imported day can) (#69). Passing an empty list clears the day's times.
     */
    suspend fun setWorkoutTimes(date: String, times: List<WorkoutTime>): Unit = write { w ->
        val d = checkDate(date)
        w.rawQuery("SELECT start, finish FROM workout_time WHERE date=? AND source=?", arrayOf(d, Sources.FITNOTES)).use { c ->
            while (c.moveToNext()) addSkip(w, RULE_TIME, timeKey(d, c.str(0), c.str(1)))
        }
        w.delete("workout_time", "date=?", arrayOf(d))
        times.forEach { t ->
            w.insert("workout_time", null, ContentValues().apply {
                put("date", d); put("start", t.start.ifBlank { null }); put("finish", t.end.ifBlank { null })
                put("source", Sources.FITLENS)
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

    /**
     * Copies sets from the workout on [from] to [to], as new FitLens sets. [setIds] limits it to those sets;
     * null copies the whole workout.
     *
     * The copies are added to whatever is already on [to] — nothing there is replaced. The originals are left
     * untouched, so no skip rule is needed. PR flags aren't copied: a copy isn't the day the record was set
     * (personal records are recalculated by #23). Returns how many sets were copied.
     */
    suspend fun copyWorkout(from: String, to: String, setIds: Collection<Long>? = null): Int = write { w ->
        val f = checkDate(from)
        val t = checkDate(to)
        if (setIds != null && setIds.isEmpty()) return@write 0
        val where = if (setIds == null) "date=?" else "date=? AND id IN (${setIds.joinToString(",")})"
        val copies = ArrayList<ContentValues>()
        w.rawQuery(
            "SELECT exercise_id, weight, reps, distance, duration, comment, set_type, rpe FROM workout_set WHERE $where ORDER BY id",
            arrayOf(f)
        ).use { c ->
            while (c.moveToNext()) {
                copies.add(ContentValues().apply {
                    put("exercise_id", c.lng(0)); put("date", t); put("weight", c.dbl(1)); put("reps", c.int(2))
                    put("distance", c.dbl(3)); put("duration", c.int(4)); put("is_pr", 0)
                    put("comment", c.str(5)); put("source", Sources.FITLENS)
                    put("set_type", c.int(6)); if (c.isNull(7)) putNull("rpe") else put("rpe", c.getDouble(7))
                })
            }
        }
        copies.forEach { w.insertOrThrow("workout_set", null, it) }
        copies.size
    }

    /**
     * Moves a whole workout (its sets, comment and times) from [from] to [to], merging into anything already
     * there rather than replacing it. Moved rows become FitLens's own, and any FitNotes row that moves leaves a
     * skip rule behind for its old date so a later import doesn't put the original back. Returns sets moved.
     */
    suspend fun moveWorkout(from: String, to: String): Int = write { w ->
        val f = checkDate(from)
        val t = checkDate(to)
        if (f == t) return@write 0
        w.rawQuery(
            "SELECT exercise_id, date, weight, reps, distance, duration FROM workout_set WHERE date=? AND source=?",
            arrayOf(f, Sources.FITNOTES)
        ).use { c ->
            while (c.moveToNext()) addSkip(w, RULE_SET, setKey(c.lng(0), c.strOr(1), c.dbl(2), c.int(3), c.dbl(4), c.int(5)))
        }
        w.rawQuery("SELECT comment FROM workout_comment WHERE date=? AND source=?", arrayOf(f, Sources.FITNOTES)).use { c ->
            while (c.moveToNext()) addSkip(w, RULE_COMMENT, commentKey(f, c.strOr(0)))
        }
        w.rawQuery("SELECT start, finish FROM workout_time WHERE date=? AND source=?", arrayOf(f, Sources.FITNOTES)).use { c ->
            while (c.moveToNext()) addSkip(w, RULE_TIME, timeKey(f, c.str(0), c.str(1)))
        }
        val moved = (w.longOrNull("SELECT COUNT(*) FROM workout_set WHERE date=?", f) ?: 0L).toInt()
        val values = ContentValues().apply { put("date", t); put("source", Sources.FITLENS) }
        w.update("workout_set", values, "date=?", arrayOf(f))

        // Comments: if both days have one, merge into a single FitLens row (destination first) (#76).
        val movedComments = readComments(w, f)
        val destComments = readComments(w, t)
        if (movedComments.isNotEmpty() && destComments.isNotEmpty()) {
            destComments.filter { it.second == Sources.FITNOTES }.forEach { addSkip(w, RULE_COMMENT, commentKey(t, it.first)) }
            val merged = (destComments + movedComments).map { it.first.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
            w.delete("workout_comment", "date=? OR date=?", arrayOf(f, t))
            if (merged.isNotEmpty()) {
                w.insert("workout_comment", null, ContentValues().apply { put("date", t); put("comment", merged); put("source", Sources.FITLENS) })
            }
        } else {
            w.update("workout_comment", values, "date=?", arrayOf(f))
        }

        // Times: if both days have them, keep one row from the earliest start to the latest finish (#76).
        // Timestamps are `yyyy-MM-dd HH:mm:ss`, so they compare correctly as text.
        val movedTimes = readTimes(w, f).map { (s, e, src) -> Triple(s?.let { t + it.drop(10) }, e?.let { t + it.drop(10) }, src) }
        val destTimes = readTimes(w, t)
        if (movedTimes.isNotEmpty() && destTimes.isNotEmpty()) {
            destTimes.filter { it.third == Sources.FITNOTES }.forEach { addSkip(w, RULE_TIME, timeKey(t, it.first, it.second)) }
            val all = destTimes + movedTimes
            val start = all.mapNotNull { it.first }.minOrNull()
            val finish = all.mapNotNull { it.second }.maxOrNull()
            w.delete("workout_time", "date=? OR date=?", arrayOf(f, t))
            if (start != null || finish != null) {
                w.insert("workout_time", null, ContentValues().apply {
                    put("date", t); put("start", start); put("finish", finish); put("source", Sources.FITLENS)
                })
            }
            return@write moved
        }
        // Start and finish are full timestamps that begin with the date, so their day part moves with the workout
        // and the recorded duration stays the same.
        w.execSQL(
            "UPDATE workout_time SET date=?, source=?, " +
                "start = CASE WHEN start IS NULL THEN NULL ELSE ? || substr(start, 11) END, " +
                "finish = CASE WHEN finish IS NULL THEN NULL ELSE ? || substr(finish, 11) END " +
                "WHERE date=?",
            arrayOf<Any>(t, Sources.FITLENS, t, t, f)
        )
        moved
    }

    /** The comment rows on [date] as (text, source). */
    private fun readComments(w: SQLiteDatabase, date: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        w.rawQuery("SELECT comment, source FROM workout_comment WHERE date=? ORDER BY rowid", arrayOf(date)).use { c ->
            while (c.moveToNext()) out.add(c.strOr(0) to c.strOr(1))
        }
        return out
    }

    /** The time rows on [date] as (start, finish, source). */
    private fun readTimes(w: SQLiteDatabase, date: String): List<Triple<String?, String?, String>> {
        val out = ArrayList<Triple<String?, String?, String>>()
        w.rawQuery("SELECT start, finish, source FROM workout_time WHERE date=? ORDER BY rowid", arrayOf(date)).use { c ->
            while (c.moveToNext()) out.add(Triple(c.str(0), c.str(1), c.strOr(2)))
        }
        return out
    }
}
