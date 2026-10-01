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

    /**
     * Runs [block] in one transaction, then re-reads the [areas] of the snapshot it changed (#60). The default covers
     * everything a workout write can touch; writes that only change the library or the notes say so.
     */
    private suspend fun <T> write(areas: Set<Area> = Area.WORKOUT, block: (SQLiteDatabase) -> T): T =
        withContext(Dispatchers.IO) {
            val w = Store.db.writableDatabase
            w.beginTransaction()
            val result = try {
                block(w).also { w.setTransactionSuccessful() }
            } finally {
                w.endTransaction()
            }
            Store.refresh(*areas.toTypedArray())
            result
        }

    /** Which sets a small set write changed: those of [exercises] and those on [dates] (#60). */
    private class SetScope {
        val exercises = HashSet<Long>()
        val dates = HashSet<String>()

        /** Names the exercises of the sets with these ids. Call it before a delete, while the rows still exist. */
        fun addSets(w: SQLiteDatabase, ids: Collection<Long>) {
            if (ids.isEmpty()) return
            w.rawQuery("SELECT DISTINCT exercise_id FROM workout_set WHERE id IN (${ids.joinToString(",")})", null).use { c ->
                while (c.moveToNext()) exercises += c.getLong(0)
            }
        }
    }

    /**
     * Like [write], for a write that changes a few sets and never replays PRs across the history (#60): only the sets
     * [block] names in its [SetScope] are re-read, so saving one set doesn't reload the whole database.
     */
    private suspend fun <T> writeSets(block: (SQLiteDatabase, SetScope) -> T): T = withContext(Dispatchers.IO) {
        val w = Store.db.writableDatabase
        val scope = SetScope()
        w.beginTransaction()
        val result = try {
            block(w, scope).also { w.setTransactionSuccessful() }
        } finally {
            w.endTransaction()
        }
        Store.refreshSets(scope.exercises, scope.dates)
        result
    }

    private val LIBRARY = setOf(Area.LIBRARY)
    private val NOTES = setOf(Area.NOTES)

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

    suspend fun createCategory(name: String, colour: Int = 0): Long = write(LIBRARY) { w ->
        val n = cleanName(name, "category")
        if (sameName(w, "category", n) != null) throw WorkoutDataException("There's already a category called $n.")
        val order = w.longOrNull("SELECT IFNULL(MAX(sort_order), 0) + 1 FROM category") ?: 1L
        clearDeletedLink(w, RULE_CATEGORY, n)
        w.insertOrThrow("category", null, ContentValues().apply {
            put("name", n); put("colour", colour); put("sort_order", order); put("source", Sources.FITLENS)
        })
    }

    suspend fun updateCategory(id: Long, name: String, colour: Int): Unit = write(LIBRARY) { w ->
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

    /**
     * Logs a workout day's sets on [date] in one transaction (#100, #106): each pair is an exercise and a prescribed set,
     * added in order as FitLens sets, like FitNotes's "Log All". PR marks are replayed, since a prescribed set can be
     * a record. [workoutId] (and [routineDayId]) record which workout and day the date was started from, for the workout's
     * next-day suggestion (#21). Returns the new ids, so the whole workout can be undone.
     */
    suspend fun logPlanned(
        date: String,
        rows: List<Pair<Long, PlannedSet>>,
        workoutId: Long = 0L,
        routineDayId: Long? = null,
        /** The workout's supersets (#18): exercise id to its group within the workout. */
        groups: Map<Long, Int> = emptyMap()
    ): List<Long> = write { w ->
        val d = checkDate(date)
        // The workout's groups become new groups on the day, after any the day already has.
        val offset = (w.longOrNull("SELECT MAX(superset) FROM workout_set WHERE substr(date, 1, 10)=?", d) ?: 0L).toInt()
        if (workoutId > 0L) {
            w.insertWithOnConflict("workout_origin", null, ContentValues().apply {
                put("date", d); put("workout_id", workoutId)
                if (routineDayId == null) putNull("routine_day_id") else put("routine_day_id", routineDayId)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
        val ids = rows.map { (exId, s) ->
            w.insertOrThrow("workout_set", null, ContentValues().apply {
                put("exercise_id", exId); put("date", d); put("weight", s.weightKg); put("reps", s.reps)
                put("distance", s.distance); put("duration", s.durationSec); put("is_pr", 0)
                put("source", Sources.FITLENS); put("set_type", s.setType); putNull("rpe")
                val g = groups[exId] ?: 0
                if (g > 0) put("superset", g + offset)
            })
        }
        replayPrs(w)
        ids
    }

    /**
     * Swaps exercise [from] for [to] on [date] (#100): the day's sets move to the new exercise and become FitLens's own.
     * An imported set that moves leaves a skip rule, so the next import doesn't bring the original back. PR marks are
     * replayed. Returns the moved sets' ids, for [setExerciseOf] to undo it.
     */
    suspend fun swapExercise(date: String, from: Long, to: Long): List<Long> = write { w ->
        val d = checkDate(date)
        if (from == to) return@write emptyList()
        w.longOrNull("SELECT id FROM exercise WHERE id=?", to.toString())
            ?: throw WorkoutDataException("That exercise no longer exists.")
        val ids = ArrayList<Long>()
        w.rawQuery(
            "SELECT id, exercise_id, date, weight, reps, distance, duration, source FROM workout_set " +
                "WHERE substr(date, 1, 10)=? AND exercise_id=?",
            arrayOf(d, from.toString())
        ).use { c ->
            while (c.moveToNext()) {
                ids.add(c.lng(0))
                if (c.strOr(7) == Sources.FITNOTES) {
                    addSkip(w, RULE_SET, setKey(c.lng(1), c.strOr(2), c.dbl(3), c.int(4), c.dbl(5), c.int(6)))
                }
            }
        }
        if (ids.isNotEmpty()) {
            w.execSQL(
                "UPDATE workout_set SET exercise_id=?, source=? WHERE id IN (${ids.joinToString(",")})",
                arrayOf<Any>(to, Sources.FITLENS)
            )
            replayPrs(w)
        }
        ids
    }

    /** Puts the sets with these ids under [exerciseId], used to undo [swapExercise]. */
    suspend fun setExerciseOf(ids: Collection<Long>, exerciseId: Long): Unit = write { w ->
        if (ids.isEmpty()) return@write
        w.update("workout_set", ContentValues().apply { put("exercise_id", exerciseId) }, "id IN (${ids.joinToString(",")})", null)
        replayPrs(w)
    }

    /**
     * Sets or clears one set's comment (#108); null or blank removes it. Nothing else about the set changes. Like any
     * edit, an imported set becomes FitLens's own; its values are unchanged, so no skip rule is needed.
     */
    suspend fun setComment(id: Long, comment: String?): Unit = writeSets { w, scope ->
        scope.addSets(w, listOf(id))
        val changed = w.update("workout_set", ContentValues().apply {
            put("comment", comment?.trim()?.takeIf { it.isNotEmpty() }); put("source", Sources.FITLENS)
        }, "id=?", arrayOf(id.toString()))
        if (changed == 0) throw WorkoutDataException("That set no longer exists.")
    }

    /** Ticks a set off, or clears the tick (#19). Nothing else about the set changes. */
    suspend fun setDone(id: Long, done: Boolean): Unit = writeSets { w, scope ->
        scope.addSets(w, listOf(id))
        w.update("workout_set", ContentValues().apply { put("done", if (done) 1 else 0) }, "id=?", arrayOf(id.toString()))
    }

    /**
     * Puts the exercises [exIds] into one superset on [date] (#18): a new group, or the group one of them is already
     * in. Returns the group number.
     */
    suspend fun groupExercises(date: String, exIds: Collection<Long>): Int = writeSets { w, scope ->
        val d = checkDate(date)
        scope.dates += d
        val inList = exIds.joinToString(",")
        val existing = w.longOrNull(
            "SELECT MAX(superset) FROM workout_set WHERE substr(date, 1, 10)=? AND exercise_id IN ($inList)", d
        )?.toInt() ?: 0
        val group = if (existing > 0) existing else
            ((w.longOrNull("SELECT MAX(superset) FROM workout_set WHERE substr(date, 1, 10)=?", d) ?: 0L) + 1).toInt()
        w.execSQL(
            "UPDATE workout_set SET superset=? WHERE substr(date, 1, 10)=? AND exercise_id IN ($inList)",
            arrayOf<Any>(group, d)
        )
        group
    }

    /**
     * Takes exercise [exId] out of its superset on [date] (#18). A group left with a single exercise is dissolved.
     */
    suspend fun ungroupExercise(date: String, exId: Long): Unit = writeSets { w, scope ->
        val d = checkDate(date)
        scope.dates += d
        val group = (w.longOrNull(
            "SELECT MAX(superset) FROM workout_set WHERE substr(date, 1, 10)=? AND exercise_id=?", d, exId.toString()
        ) ?: 0L).toInt()
        if (group == 0) return@writeSets
        w.execSQL("UPDATE workout_set SET superset=0 WHERE substr(date, 1, 10)=? AND exercise_id=?", arrayOf<Any>(d, exId))
        val left = w.longOrNull(
            "SELECT COUNT(DISTINCT exercise_id) FROM workout_set WHERE substr(date, 1, 10)=? AND superset=?", d, group.toString()
        ) ?: 0L
        if (left < 2) w.execSQL("UPDATE workout_set SET superset=0 WHERE substr(date, 1, 10)=? AND superset=?", arrayOf<Any>(d, group))
    }

    /**
     * Stores the order of a day's sets (#70): [orderedIds] first to last. Exercises follow the order of their first
     * set, so moving an exercise is moving its sets as a block.
     */
    suspend fun reorderDay(orderedIds: List<Long>): Unit = writeSets { w, scope ->
        scope.addSets(w, orderedIds)
        orderedIds.forEachIndexed { i, id ->
            w.update("workout_set", ContentValues().apply { put("position", i + 1) }, "id=?", arrayOf(id.toString()))
        }
    }

    /**
     * Saves the categories' order (#83), first to last. A FitNotes import only ever adds categories, never changes
     * one that exists, so the order chosen here is kept.
     */
    suspend fun reorderCategories(ids: List<Long>): Unit = write(LIBRARY) { w ->
        ids.forEachIndexed { i, id ->
            w.update("category", ContentValues().apply { put("sort_order", i + 1) }, "id=?", arrayOf(id.toString()))
        }
    }

    /** Deletes a category. Its exercises and their history are kept and become uncategorised. */
    suspend fun deleteCategory(id: Long): Unit = write(LIBRARY) { w ->
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

    /** [type] is one of [ExerciseTypes]. */
    suspend fun createExercise(name: String, categoryId: Long, type: Int = 0, notes: String? = null): Long = write(LIBRARY) { w ->
        val n = cleanName(name, "exercise")
        if (sameName(w, "exercise", n) != null) throw WorkoutDataException("There's already an exercise called $n.")
        clearDeletedLink(w, RULE_EXERCISE, n)
        w.insertOrThrow("exercise", null, ContentValues().apply {
            put("name", n); put("category_id", categoryId); put("type", type)
            put("notes", notes?.takeIf { it.isNotBlank() }); put("source", Sources.FITLENS)
        })
    }

    suspend fun updateExercise(id: Long, name: String, categoryId: Long, type: Int, notes: String?): Unit = write(LIBRARY) { w ->
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
     * An exercise's own defaults (#15): its weight step in kg (null uses the global step), the graph it opens on
     * (-1 for the first), its rest length in seconds (null uses the global one), and its distance and weight units
     * (null uses the global one, #7). Kept apart from [updateExercise] so a rename never touches them.
     */
    suspend fun setExerciseDefaults(
        id: Long, weightStepKg: Double?, defaultGraph: Int, restSeconds: Int?, distanceUnit: String?, weightUnit: String?
    ): Unit = write(LIBRARY) { w ->
        w.update("exercise", ContentValues().apply {
            if (weightStepKg == null) putNull("weight_step") else put("weight_step", weightStepKg)
            put("default_graph", defaultGraph)
            if (restSeconds == null) putNull("rest_seconds") else put("rest_seconds", restSeconds)
            val unit = DistanceUnits.of(distanceUnit)
            if (unit == null) putNull("distance_unit") else put("distance_unit", unit)
            val wUnit = WeightUnits.of(weightUnit)
            if (wUnit == null) putNull("weight_unit") else put("weight_unit", wUnit)
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
        Routines.forgetExercise(w, id)
        w.delete("exercise_comment", "exercise_id=?", arrayOf(id.toString()))
        w.delete("exercise", "id=?", arrayOf(id.toString()))
        w.execSQL("UPDATE import_rule SET target_id=NULL WHERE kind=? AND target_id=?", arrayOf<Any>(RULE_EXERCISE, id))
        if (hadImports) setLink(w, RULE_EXERCISE, nameKey(row.first), null)
    }

    /**
     * Merges exercise [fromId] into [intoId] and deletes [fromId] (#57), for duplicates such as "Bench Press" and
     * "Barbell Bench Press". Every set, goal and workout-day entry moves across with its date, place, superset and
     * tick. [intoId] keeps its own name, category, type and defaults, and takes [fromId]'s notes and star only when it
     * has none. Later FitNotes imports follow the merge: [fromId]'s name (and every name already linked to it) maps
     * onto [intoId], and skip rules for its deleted imported sets are re-keyed, so nothing comes back as a duplicate.
     * PR marks are replayed, since the joined history can change them. Returns how many sets moved.
     */
    suspend fun mergeExercises(fromId: Long, intoId: Long): Int = write { w ->
        if (fromId == intoId) throw WorkoutDataException("Choose a different exercise to merge into.")
        data class Row(val name: String, val notes: String?, val favourite: Boolean)
        fun row(id: Long) = w.rawQuery("SELECT name, notes, favourite FROM exercise WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) Row(c.strOr(0), c.str(1), c.int(2) != 0) else null
        } ?: throw WorkoutDataException("That exercise no longer exists.")
        val from = row(fromId)
        val into = row(intoId)
        val fromArg = arrayOf(fromId.toString())
        val moved = w.rawQuery("SELECT COUNT(*) FROM workout_set WHERE exercise_id=?", fromArg).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        val target = ContentValues().apply { put("exercise_id", intoId) }
        w.update("workout_set", target, "exercise_id=?", fromArg)
        w.update("exercise_goal", target, "exercise_id=?", fromArg)
        w.update("routine_day_exercise", target, "exercise_id=?", fromArg)
        mergeExerciseComments(w, fromId, intoId)
        w.update("exercise", ContentValues().apply {
            if (into.notes.isNullOrBlank() && !from.notes.isNullOrBlank()) put("notes", from.notes)
            if (from.favourite) put("favourite", 1)
            put("source", Sources.FITLENS)
        }, "id=?", arrayOf(intoId.toString()))
        w.delete("exercise", "id=?", fromArg)
        // Imports: names that led to the old exercise now lead to the kept one, and its skipped sets stay skipped.
        w.execSQL("UPDATE import_rule SET target_id=? WHERE kind=? AND target_id=?", arrayOf<Any>(intoId, RULE_EXERCISE, fromId))
        setLink(w, RULE_EXERCISE, nameKey(from.name), intoId)
        val prefix = "$fromId|"
        val rekey = mutableListOf<Pair<Long, String>>()
        w.rawQuery("SELECT rowid, key FROM import_rule WHERE kind=? AND key LIKE ?", arrayOf(RULE_SET, "$prefix%")).use { c ->
            while (c.moveToNext()) rekey += c.getLong(0) to "$intoId|" + c.strOr(1).removePrefix(prefix)
        }
        rekey.forEach { (rowid, key) ->
            w.update("import_rule", ContentValues().apply { put("key", key) }, "rowid=?", arrayOf(rowid.toString()))
        }
        replayPrs(w)
        moved
    }

    /** Stars or unstars an exercise. Favourites are listed first when choosing an exercise. */
    suspend fun setFavourite(id: Long, favourite: Boolean): Unit = write(LIBRARY) { w ->
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
    suspend fun seedStarterLibrary(palette: List<Int>): SeedResult = write(LIBRARY) { w ->
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
    ): Long = writeSets { w, scope ->
        val d = checkDate(date)
        w.longOrNull("SELECT id FROM exercise WHERE id=?", exerciseId.toString())
            ?: throw WorkoutDataException("That exercise no longer exists.")
        // Its PR mark is decided here against earlier sets, so no other set changes and only this exercise is re-read.
        scope.exercises += exerciseId
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
    suspend fun updateSet(set: SetRow): Unit = writeSets { w, scope ->
        val d = checkDate(set.date)
        val old = w.rawQuery(
            "SELECT exercise_id, date, weight, reps, distance, duration, source FROM workout_set WHERE id=?",
            arrayOf(set.id.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                scope.exercises += c.lng(0)
                c.strOr(6) to setKey(c.lng(0), c.strOr(1), c.dbl(2), c.int(3), c.dbl(4), c.int(5))
            } else null
        } ?: throw WorkoutDataException("That set no longer exists.")
        scope.exercises += set.exerciseId
        val newKey = setKey(set.exerciseId, d, set.weightKg, set.reps, set.distance, set.durationSec)
        if (old.first == Sources.FITNOTES && old.second != newKey) addSkip(w, RULE_SET, old.second)
        w.update("workout_set", ContentValues().apply {
            put("exercise_id", set.exerciseId); put("date", d); put("weight", set.weightKg); put("reps", set.reps)
            put("distance", set.distance); put("duration", set.durationSec); put("is_pr", if (set.isPr) 1 else 0)
            put("comment", set.comment?.takeIf { it.isNotBlank() }); put("source", Sources.FITLENS)
            put("set_type", set.setType); putRpe(set.rpe)
        }, "id=?", arrayOf(set.id.toString()))
    }

    suspend fun deleteSet(id: Long): Unit = writeSets { w, scope ->
        scope.addSets(w, listOf(id))
        deleteSetsWhere(w, "id=?", arrayOf(id.toString()))
    }

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
        // Exercise comments belong to the exercise in that day's workout, so they go with its sets (#107).
        w.delete("exercise_comment", where.replace("substr(date, 1, 10)", "date"), args.toTypedArray())
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
    suspend fun addSets(rows: List<SetRow>): Int = writeSets { w, scope ->
        scope.exercises += rows.map { it.exerciseId }
        rows.forEach { s ->
            w.insertOrThrow("workout_set", null, ContentValues().apply {
                put("exercise_id", s.exerciseId); put("date", s.date.take(10)); put("weight", s.weightKg)
                put("reps", s.reps); put("distance", s.distance); put("duration", s.durationSec)
                put("is_pr", if (s.isPr) 1 else 0); put("comment", s.comment?.takeIf { it.isNotBlank() })
                put("source", Sources.FITLENS); put("set_type", s.setType); putRpe(s.rpe)
                // Back in its old place (#70), group (#18) and tick (#19); 0 lets the triggers decide.
                put("position", s.position); put("superset", s.superset); put("done", if (s.done) 1 else 0)
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
    suspend fun setWorkoutComment(date: String, comment: String?): Unit = write(NOTES) { w ->
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

    /** Replaces the comment on exercise [exerciseId] in [date]'s workout (#107). A blank comment removes it. */
    suspend fun setExerciseComment(date: String, exerciseId: Long, comment: String?): Unit = write(NOTES) { w ->
        writeExerciseComment(w, checkDate(date), exerciseId, comment)
    }

    /** Puts [date]'s exercise comments back exactly as [comments] (exercise id to text), for Undo (#107). */
    suspend fun setExerciseComments(date: String, comments: Map<Long, String>): Unit = write(NOTES) { w ->
        val d = checkDate(date)
        w.delete("exercise_comment", "date=?", arrayOf(d))
        comments.forEach { (ex, text) -> writeExerciseComment(w, d, ex, text) }
    }

    private fun writeExerciseComment(w: SQLiteDatabase, d: String, exerciseId: Long, comment: String?) {
        w.delete("exercise_comment", "date=? AND exercise_id=?", arrayOf(d, exerciseId.toString()))
        val text = comment?.trim()
        if (!text.isNullOrEmpty()) {
            w.insertOrThrow("exercise_comment", null, ContentValues().apply {
                put("date", d); put("exercise_id", exerciseId); put("comment", text); put("source", Sources.FITLENS)
            })
        }
    }

    /** Moves exercise comments from exercise [fromId] to [intoId]; on a date where both have one, they're joined. */
    private fun mergeExerciseComments(w: SQLiteDatabase, fromId: Long, intoId: Long) {
        val moving = w.rawQuery("SELECT date, comment FROM exercise_comment WHERE exercise_id=?", arrayOf(fromId.toString()))
            .use { c -> ArrayList<Pair<String, String>>().apply { while (c.moveToNext()) add(c.strOr(0) to c.strOr(1)) } }
        w.delete("exercise_comment", "exercise_id=?", arrayOf(fromId.toString()))
        moving.forEach { (d, text) -> joinExerciseComment(w, d, intoId, text) }
    }

    /** Adds [text] to exercise [exerciseId]'s comment on [d], after any comment already there. */
    private fun joinExerciseComment(w: SQLiteDatabase, d: String, exerciseId: Long, text: String) {
        val existing = w.rawQuery("SELECT comment FROM exercise_comment WHERE date=? AND exercise_id=?", arrayOf(d, exerciseId.toString()))
            .use { c -> if (c.moveToFirst()) c.strOr(0) else null }
        val joined = listOfNotNull(existing, text).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n\n")
        writeExerciseComment(w, d, exerciseId, joined)
    }

    /**
     * Replaces the workout start and end for [date]. Times use FitNotes's format (`yyyy-MM-dd HH:mm:ss`).
     * Both null removes them.
     */
    suspend fun setWorkoutTime(date: String, start: String?, finish: String?): Unit = write(NOTES) { w ->
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
    suspend fun setWorkoutTimes(date: String, times: List<WorkoutTime>): Unit = write(NOTES) { w ->
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
        w.delete("exercise_comment", "date=?", arrayOf(d))
        w.delete("workout_time", "date=?", arrayOf(d))
        w.delete("workout_origin", "date=?", arrayOf(d))
    }

    /**
     * Copies sets from the workout on [from] to [to], as new FitLens sets. [setIds] limits it to those sets;
     * null copies the whole workout.
     *
     * The copies are added to whatever is already on [to] — nothing there is replaced. The originals are left
     * untouched, so no skip rule is needed. PR flags aren't copied: a copy isn't the day the record was set
     * (personal records are recalculated by #23). Returns the new sets' ids, so the copy can be undone (#84).
     */
    suspend fun copyWorkout(from: String, to: String, setIds: Collection<Long>? = null): List<Long> = write { w ->
        val f = checkDate(from)
        val t = checkDate(to)
        if (setIds != null && setIds.isEmpty()) return@write emptyList()
        val where = if (setIds == null) "date=?" else "date=? AND id IN (${setIds.joinToString(",")})"
        val copies = ArrayList<ContentValues>()
        // Supersets come across as new groups on the target day, after any it already has (#18).
        val offset = (w.longOrNull("SELECT MAX(superset) FROM workout_set WHERE substr(date, 1, 10)=?", t) ?: 0L).toInt()
        w.rawQuery(
            "SELECT exercise_id, weight, reps, distance, duration, comment, set_type, rpe, superset FROM workout_set WHERE $where ORDER BY position, id",
            arrayOf(f)
        ).use { c ->
            while (c.moveToNext()) {
                copies.add(ContentValues().apply {
                    put("exercise_id", c.lng(0)); put("date", t); put("weight", c.dbl(1)); put("reps", c.int(2))
                    put("distance", c.dbl(3)); put("duration", c.int(4)); put("is_pr", 0)
                    put("comment", c.str(5)); put("source", Sources.FITLENS)
                    put("set_type", c.int(6)); if (c.isNull(7)) putNull("rpe") else put("rpe", c.getDouble(7))
                    if (c.int(8) > 0) put("superset", c.int(8) + offset)
                })
            }
        }
        val ids = copies.map { w.insertOrThrow("workout_set", null, it) }
        // Exercise comments come along for the exercises copied, unless the target day already has its own (#107).
        val copied = copies.map { it.getAsLong("exercise_id") }.distinct()
        if (copied.isNotEmpty()) {
            w.execSQL(
                "INSERT OR IGNORE INTO exercise_comment(date, exercise_id, comment, source) " +
                    "SELECT ?, exercise_id, comment, ? FROM exercise_comment WHERE date=? AND exercise_id IN (${copied.joinToString(",")})",
                arrayOf<Any>(t, Sources.FITLENS, f)
            )
        }
        ids
    }

    /**
     * Deletes the sets with these ids in one transaction, used to undo a copy (#84). Imported sets leave a skip rule
     * like any other delete, and PR marks are replayed since a deleted set may have held one.
     */
    suspend fun deleteSets(ids: Collection<Long>): Int = write { w ->
        if (ids.isEmpty()) return@write 0
        val pairs = w.rawQuery("SELECT DISTINCT substr(date, 1, 10), exercise_id FROM workout_set WHERE id IN (${ids.joinToString(",")})", null)
            .use { c -> ArrayList<Pair<String, Long>>().apply { while (c.moveToNext()) add(c.strOr(0) to c.lng(1)) } }
        deleteSetsWhere(w, "id IN (${ids.joinToString(",")})", emptyArray())
        // Undoing a copy or a logged workout takes the exercise comments it brought along (#107): a comment goes
        // once its exercise has no sets left on that date.
        pairs.forEach { (d, ex) ->
            w.execSQL(
                "DELETE FROM exercise_comment WHERE date=? AND exercise_id=? AND NOT EXISTS " +
                    "(SELECT 1 FROM workout_set WHERE substr(date, 1, 10)=? AND exercise_id=?)",
                arrayOf<Any>(d, ex, d, ex)
            )
        }
        replayPrs(w)
        ids.size
    }

    /** Moves the sets with these ids to [to], keeping them otherwise as they are. Used to undo a move (#84). */
    suspend fun moveSets(ids: Collection<Long>, to: String): Unit = write { w ->
        val t = checkDate(to)
        if (ids.isEmpty()) return@write
        w.update("workout_set", ContentValues().apply { put("date", t) }, "id IN (${ids.joinToString(",")})", null)
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
        // Moved supersets keep their groups, numbered after the target day's own so the two never merge (#18).
        val ssOffset = (w.longOrNull("SELECT MAX(superset) FROM workout_set WHERE substr(date, 1, 10)=?", t) ?: 0L).toInt()
        if (ssOffset > 0) w.execSQL("UPDATE workout_set SET superset = superset + ? WHERE date=? AND superset > 0", arrayOf<Any>(ssOffset, f))
        // Where the workout came from moves with it (#21), replacing the target day's.
        w.execSQL("UPDATE OR REPLACE workout_origin SET date=? WHERE date=?", arrayOf<Any>(t, f))
        val values = ContentValues().apply { put("date", t); put("source", Sources.FITLENS) }
        w.update("workout_set", values, "date=?", arrayOf(f))
        // Exercise comments move too; one landing on an exercise that already has a comment there is joined (#107).
        val movingComments = w.rawQuery("SELECT exercise_id, comment FROM exercise_comment WHERE date=?", arrayOf(f))
            .use { c -> ArrayList<Pair<Long, String>>().apply { while (c.moveToNext()) add(c.lng(0) to c.strOr(1)) } }
        w.delete("exercise_comment", "date=?", arrayOf(f))
        movingComments.forEach { (ex, text) -> joinExerciseComment(w, t, ex, text) }

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
