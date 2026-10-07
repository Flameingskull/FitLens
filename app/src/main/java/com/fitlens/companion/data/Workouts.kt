package com.fitlens.companion.data

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.fitlens.companion.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale


/**
 * Why a workout write was refused. The data layer has no `Resources`, so it carries the string and its arguments and
 * the screen words it with [text] (#156).
 */
class WorkoutDataException private constructor(private val word: (Resources) -> String) : IllegalArgumentException() {
    constructor(@StringRes id: Int, vararg args: Any) : this({ res -> res.getString(id, *args) })

    fun text(res: Resources): String = word(res)

    companion object {
        /** A refusal whose wording depends on a count. */
        fun counted(@PluralsRes id: Int, n: Int): WorkoutDataException = WorkoutDataException { it.getQuantityString(id, n, n) }
    }
}

/** What to tell the user about a failed write: a [WorkoutDataException]'s own wording, else the exception's message. */
fun Throwable.userText(res: Resources): String =
    (this as? WorkoutDataException)?.text(res) ?: message ?: javaClass.simpleName


/**
 * Create, update and delete workout data in FlexNotes: categories, exercises, sets, and per-day workout comments and
 * times. A "workout" is every set, comment and time logged on one date, as in FitNotes.
 *
 * ## Ownership
 * Every category, exercise, set, workout comment and workout time has a `source`:
 * - `fitnotes`: imported from a FitNotes backup and not changed since.
 * - `fitlens`: created in FlexNotes, or an imported row the user has edited in FlexNotes (editing makes it FlexNotes's).
 * Rows have FlexNotes's own stable `id`. Imported rows also keep their FitNotes id in `fitnotes_id`, for reference only.
 *
 * ## Conflict rules (shared with [FitNotesImporter])
 * 1. A FitNotes import only ever **adds** rows. It never deletes, edits or overwrites anything already in FlexNotes,
 *    whoever created it. FlexNotes never writes to FitNotes or its backups.
 * 2. Categories and exercises are matched **by name** (ignoring case and surrounding spaces), so imported and
 *    FlexNotes-logged history join up. When both exist, the FlexNotes row is kept as it is (its category, type, notes).
 * 3. A set is "already present" when a set on the same date, for the same exercise, with the same weight, reps,
 *    distance and time exists, whoever created it. Identical sets are counted, so three identical sets in a backup
 *    match three in FlexNotes. Re-importing the same backup therefore changes nothing.
 * 4. Workout comments and times are present when the same text (or the same start and end) exists on that date.
 * 5. What the user does in FlexNotes wins over later imports: renaming an exercise or category keeps it linked to the
 *    FitNotes name; deleting imported data, or editing an imported set, comment or time, is remembered in
 *    `import_rule` so the next import doesn't bring the original back. Re-creating a deleted exercise or category
 *    with the same name lets its FitNotes history import again.
 * 6. Body measurements merge the same way: a FitNotes value is skipped when the same measurement, date, time and value
 *    exists, or when a value entered by hand in FlexNotes has the same measurement, date and value.
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
    private fun WorkoutDao.setLink(kind: String, key: String, targetId: Long?) {
        deleteRule(kind, key)
        addRule(kind, key, targetId)
    }

    /** One imported row with this key is skipped by later imports. */
    private fun WorkoutDao.addSkip(kind: String, key: String) = addRule(kind, key, null)

    /** A name the user re-creates stops being skipped by imports (see conflict rule 5). */
    private fun WorkoutDao.clearDeletedLink(kind: String, name: String) = deleteSkips(kind, nameKey(name))

    private fun WorkoutSetRow.key(): String = setKey(exercise_id, date, weight, reps, distance, duration.toInt())

    /** Imported sets leave a skip rule when they're deleted, moved or changed, so the next import doesn't restore them. */
    private fun WorkoutDao.skipSets(rows: List<WorkoutSetRow>) = rows.forEach { addSkip(RULE_SET, it.key()) }

    private fun WorkoutDao.skipImportedComments(d: String) =
        commentsOn(d).filter { it.source == Sources.FITNOTES }.forEach { addSkip(RULE_COMMENT, commentKey(d, it.comment)) }

    private fun WorkoutDao.skipImportedTimes(d: String) =
        timesOn(d).filter { it.source == Sources.FITNOTES }.forEach { addSkip(RULE_TIME, timeKey(d, it.start, it.finish)) }

    /** Runs [block] once per [Db.MAX_IDS] ids, the most one query takes. */
    private fun Collection<Long>.inChunks(block: (List<Long>) -> Unit) = toList().chunked(Db.MAX_IDS).forEach(block)

    private fun flag(on: Boolean) = if (on) 1 else 0

    /** [missing] is what the user is told when the name is blank. */
    private fun cleanName(name: String, @StringRes missing: Int): String {
        val n = name.trim().replace(Regex("\\s+"), " ")
        if (n.isEmpty()) throw WorkoutDataException(missing)
        return n
    }

    private fun checkDate(date: String): String {
        val d = date.take(10)
        if (Dates.parse(d) == null) throw WorkoutDataException(R.string.wde_bad_date)
        return d
    }

    /**
     * Runs [block] in one Room transaction, then re-reads the [areas] of the snapshot it changed (#60). The default
     * covers everything a workout write can touch; writes that only change the library or the notes say so.
     */
    private suspend fun <T> write(areas: Set<Area> = Area.WORKOUT, block: (WorkoutDao) -> T): T =
        withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            val db = Store.db
            val result = db.transaction { block(db.workoutDao) }
            val written = System.nanoTime()
            Store.refresh(*areas.toTypedArray())
            WriteTimings.record(WriteTimings.OTHER, start, written, System.nanoTime(), Store.snapshot.value?.sets?.size ?: 0)
            result
        }

    /**
     * Which sets a write changed: those of [exercises] and those on [dates] (#60), plus any other [areas] of the
     * snapshot it touched (the notes, the library).
     */
    private class SetScope {
        val exercises = HashSet<Long>()
        val dates = HashSet<String>()
        val areas = HashSet<Area>()

        /** Names the exercises of the sets with these ids. Call it before a delete, while the rows still exist. */
        fun addSets(w: WorkoutDao, ids: Collection<Long>) {
            ids.toList().chunked(Db.MAX_IDS).forEach { exercises += w.exercisesOfSets(it) }
        }
    }

    /**
     * Like [write], for a write that changes the sets of a few exercises or dates (#60): only the sets [block] names
     * in its [SetScope] are re-read, with the other areas it names, so saving one set or logging a workout day doesn't
     * reload the whole database. PRs are replayed for the scope's exercises only ([replayPrs]). [kind] is how
     * [WriteTimings] files it: a single set save, or a larger write.
     */
    private suspend fun <T> writeSets(kind: Int = WriteTimings.SET, block: (WorkoutDao, SetScope) -> T): T =
        withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            val db = Store.db
            val scope = SetScope()
            val result = db.transaction { block(db.workoutDao, scope) }
            val written = System.nanoTime()
            Store.refreshSets(scope.exercises, scope.dates, scope.areas)
            // How long a set save takes on this phone, shown in Settings › About (#60).
            WriteTimings.record(kind, start, written, System.nanoTime(), Store.snapshot.value?.sets?.size ?: 0)
            result
        }

    private val LIBRARY = setOf(Area.LIBRARY)
    private val NOTES = setOf(Area.NOTES)

    /** Id of another row in [rows] with this name (ignoring case), if any. */
    private fun sameName(rows: List<IdName>, name: String, exceptId: Long = -1L): Long? {
        val key = nameKey(name)
        return rows.firstOrNull { it.id != exceptId && nameKey(it.name) == key }?.id
    }

    // ---------- Categories ----------

    suspend fun createCategory(name: String, colour: Int = 0): Long = write(LIBRARY) { w ->
        val n = cleanName(name, R.string.wde_name_category)
        if (sameName(w.categoryNames(), n) != null) throw WorkoutDataException(R.string.wde_category_exists, n)
        val order = w.lastCategoryOrder() + 1
        w.clearDeletedLink(RULE_CATEGORY, n)
        w.addCategory(n, colour, order, Sources.FLEXNOTES)
    }

    suspend fun updateCategory(id: Long, name: String, colour: Int): Unit = write(LIBRARY) { w ->
        val n = cleanName(name, R.string.wde_name_category)
        val old = w.category(id)?.name ?: throw WorkoutDataException(R.string.wde_category_gone)
        if (sameName(w.categoryNames(), n, exceptId = id) != null) throw WorkoutDataException(R.string.wde_category_exists, n)
        if (nameKey(old) != nameKey(n)) {
            w.setLink(RULE_CATEGORY, nameKey(old), id)
            w.clearDeletedLink(RULE_CATEGORY, n)
        }
        w.updateCategory(id, n, colour, Sources.FLEXNOTES)
    }

    /**
     * Logs a workout day's sets on [date] in one transaction (#100, #106): each pair is an exercise and a prescribed set,
     * added in order as FlexNotes sets, like FitNotes's "Log All". PR marks are replayed, since a prescribed set can be
     * a record. [workoutId] (and [routineDayId]) record which workout and day the date was started from, for the workout's
     * next-day suggestion (#21). Returns the new ids, so the whole workout can be undone.
     */
    suspend fun logPlanned(
        date: String,
        rows: List<Pair<Long, PlannedSet>>,
        workoutId: Long = 0L,
        routineDayId: Long? = null,
        /** The workout's supersets (#18): exercise id to its group within the workout. */
        groups: Map<Long, Int> = emptyMap(),
        /** The rest the workout day prescribes per exercise (#138), kept on the date so later edits don't change it. */
        rests: Map<Long, WorkoutRest> = emptyMap()
    ): List<Long> = writeSets(WriteTimings.OTHER) { w, scope ->
        val d = checkDate(date)
        scope.dates += d
        scope.exercises += rows.map { it.first }
        scope.areas += Area.LIBRARY
        scope.areas += Area.NOTES
        // The workout's groups become new groups on the day, after any the day already has.
        val offset = w.lastGroupOn(d) ?: 0
        if (workoutId > 0L) w.putOrigin(WorkoutOriginRow(d, workoutId, routineDayId))
        val ids = rows.map { (exId, s) ->
            val g = groups[exId] ?: 0
            w.addSet(
                exId, d, s.weightKg, s.reps, s.distance, s.durationSec, isPr = 0, comment = null, source = Sources.FLEXNOTES,
                setType = s.setType, rpe = null, metric = s.metric, position = 0L, superset = if (g > 0) g + offset else 0,
                done = 0, restSeconds = s.restSeconds
            )
        }
        rests.filterValues { !it.isEmpty }.forEach { (exId, r) -> putRest(w, d, exId, r) }
        replayPrs(w, scope.exercises)
        ids
    }

    /**
     * Swaps exercise [from] for [to] on [date] (#100): the day's sets move to the new exercise and become FlexNotes's own.
     * An imported set that moves leaves a skip rule, so the next import doesn't bring the original back. PR marks are
     * replayed. Returns the moved sets' ids, for [setExerciseOf] to undo it.
     */
    suspend fun swapExercise(date: String, from: Long, to: Long): List<Long> = writeSets(WriteTimings.OTHER) { w, scope ->
        val d = checkDate(date)
        scope.areas += Area.NOTES
        // The prescribed rest (#138) belongs to the exercise's place in the workout, so it follows the swap.
        w.swapRest(d, from, to)
        if (from == to) return@writeSets emptyList()
        if (!w.exerciseExists(to)) throw WorkoutDataException(R.string.wde_exercise_gone)
        val sets = w.setsOfExerciseOn(d, from)
        w.skipSets(sets.filter { it.source == Sources.FITNOTES })
        val ids = sets.map { it.id }
        if (ids.isNotEmpty()) {
            scope.exercises += from
            scope.exercises += to
            scope.dates += d
            ids.inChunks { w.moveSetsToExercise(it, to, Sources.FLEXNOTES) }
            replayPrs(w, scope.exercises)
        }
        ids
    }

    /** Puts the sets with these ids under [exerciseId], used to undo [swapExercise]. */
    suspend fun setExerciseOf(ids: Collection<Long>, exerciseId: Long): Unit = writeSets(WriteTimings.OTHER) { w, scope ->
        if (ids.isEmpty()) return@writeSets
        scope.addSets(w, ids)
        scope.exercises += exerciseId
        ids.inChunks { w.setExercise(it, exerciseId) }
        replayPrs(w, scope.exercises)
    }

    /**
     * Sets or clears one set's comment (#108); null or blank removes it. Nothing else about the set changes. Like any
     * edit, an imported set becomes FlexNotes's own; its values are unchanged, so no skip rule is needed.
     */
    suspend fun setComment(id: Long, comment: String?): Unit = writeSets { w, scope ->
        scope.addSets(w, listOf(id))
        val changed = w.setComment(id, comment?.trim()?.takeIf { it.isNotEmpty() }, Sources.FLEXNOTES)
        if (changed == 0) throw WorkoutDataException(R.string.wde_set_gone)
    }

    /** Ticks a set off, or clears the tick (#19). Nothing else about the set changes. */
    suspend fun setDone(id: Long, done: Boolean): Unit = writeSets { w, scope ->
        scope.addSets(w, listOf(id))
        w.setDone(id, flag(done))
    }

    /**
     * Puts the exercises [exIds] into one superset on [date] (#18): a new group, or the group one of them is already
     * in. Returns the group number.
     */
    suspend fun groupExercises(date: String, exIds: Collection<Long>): Int = writeSets { w, scope ->
        val d = checkDate(date)
        scope.dates += d
        val list = exIds.toList()
        val existing = w.groupOf(d, list) ?: 0
        val group = if (existing > 0) existing else (w.lastGroupOn(d) ?: 0) + 1
        w.setGroup(d, list, group)
        group
    }

    /**
     * Takes exercise [exId] out of its superset on [date] (#18). A group left with a single exercise is dissolved.
     */
    suspend fun ungroupExercise(date: String, exId: Long): Unit = writeSets { w, scope ->
        val d = checkDate(date)
        scope.dates += d
        val group = w.groupOf(d, listOf(exId)) ?: 0
        if (group == 0) return@writeSets
        w.setGroup(d, listOf(exId), 0)
        if (w.exercisesInGroup(d, group) < 2) w.dissolveGroup(d, group)
    }

    /**
     * Stores the order of a day's sets (#70): [orderedIds] first to last. Exercises follow the order of their first
     * set, so moving an exercise is moving its sets as a block.
     */
    suspend fun reorderDay(orderedIds: List<Long>): Unit = writeSets { w, scope ->
        scope.addSets(w, orderedIds)
        orderedIds.forEachIndexed { i, id -> w.setPosition(id, i + 1L) }
    }

    /**
     * Saves the categories' order (#83), first to last. A FitNotes import only ever adds categories, never changes
     * one that exists, so the order chosen here is kept.
     */
    suspend fun reorderCategories(ids: List<Long>): Unit = write(LIBRARY) { w ->
        ids.forEachIndexed { i, id -> w.setCategoryOrder(id, i + 1) }
    }

    /** Deletes a category. Its exercises and their history are kept and become uncategorised. */
    suspend fun deleteCategory(id: Long): Unit = write(LIBRARY) { w ->
        val row = w.category(id) ?: return@write
        val linked = w.hasLinkTo(RULE_CATEGORY, id)
        w.moveExercisesToCategory(id, UNCATEGORISED)
        w.deleteCategory(id)
        w.relink(RULE_CATEGORY, id, null)
        if (row.fitnotes_id != null || linked) w.setLink(RULE_CATEGORY, nameKey(row.name), null)
    }

    // ---------- Exercises ----------

    /** [type] is one of [ExerciseTypes]. */
    suspend fun createExercise(name: String, categoryId: Long, type: Int = 0, notes: String? = null): Long = write(LIBRARY) { w ->
        val n = cleanName(name, R.string.wde_name_exercise)
        if (sameName(w.exerciseNames(), n) != null) throw WorkoutDataException(R.string.wde_exercise_exists, n)
        w.clearDeletedLink(RULE_EXERCISE, n)
        w.addExercise(n, categoryId, type, notes?.takeIf { it.isNotBlank() }, Sources.FLEXNOTES)
    }

    suspend fun updateExercise(id: Long, name: String, categoryId: Long, type: Int, notes: String?): Unit = write(LIBRARY) { w ->
        val n = cleanName(name, R.string.wde_name_exercise)
        val old = w.exercise(id)?.name ?: throw WorkoutDataException(R.string.wde_exercise_gone)
        if (sameName(w.exerciseNames(), n, exceptId = id) != null) throw WorkoutDataException(R.string.wde_exercise_exists, n)
        if (nameKey(old) != nameKey(n)) {
            w.setLink(RULE_EXERCISE, nameKey(old), id)
            w.clearDeletedLink(RULE_EXERCISE, n)
        }
        w.updateExercise(id, n, categoryId, type, notes?.takeIf { it.isNotBlank() }, Sources.FLEXNOTES)
    }

    /**
     * An exercise's own defaults (#15): its weight step in kg (null uses the global step), the graph it opens on
     * (-1 for the first), its rest length in seconds (null uses the global one), and its distance and weight units
     * (null uses the global one, #7). Kept apart from [updateExercise] so a rename never touches them.
     */
    suspend fun setExerciseDefaults(
        id: Long, weightStepKg: Double?, defaultGraph: Int, restSeconds: Int?, distanceUnit: String?, weightUnit: String?
    ): Unit = write(LIBRARY) { w ->
        w.setExerciseDefaults(
            id, weightStepKg, defaultGraph, restSeconds, DistanceUnits.of(distanceUnit), WeightUnits.of(weightUnit)
        )
    }

    // ---------- Exercise types (#14) ----------

    /**
     * Saves a user-defined exercise type: a new one when [CustomType.id] is 0, given the next free id from
     * [ExerciseTypes.CUSTOM_BASE] up. It must record 1 to [CustomType.MAX_VALUES] values and have a name no other
     * type has. Returns its id. Exercises of an edited type follow the change; their sets keep every value.
     */
    suspend fun saveExerciseType(t: CustomType): Int = write(LIBRARY) { w ->
        val n = cleanName(t.name, R.string.wde_name_type)
        val metric = t.metricName?.trim()?.takeIf { it.isNotEmpty() }
        val count = t.copy(metricName = metric).valueCount
        if (count == 0) throw WorkoutDataException(R.string.wde_type_no_values, n)
        if (count > CustomType.MAX_VALUES) throw WorkoutDataException(R.string.wde_type_too_many, CustomType.MAX_VALUES)
        // Its graphs are named after it ("Best Height"), so it can't share a name with a value FlexNotes already records.
        if (metric != null && nameKey(metric) in setOf("weight", "reps", "distance", "time")) {
            throw WorkoutDataException(R.string.wde_type_metric_builtin, metric)
        }
        val clash = (ExerciseTypes.all.map { ExerciseTypes.label(it) } + w.typeNamesExcept(t.id.toLong()))
            .any { nameKey(it) == nameKey(n) }
        if (clash) throw WorkoutDataException(R.string.wde_type_exists, n)
        val id = if (t.id >= ExerciseTypes.CUSTOM_BASE) t.id else
            ((w.lastTypeId()?.toInt() ?: 0) + 1).coerceAtLeast(ExerciseTypes.CUSTOM_BASE)
        val unit = t.metricUnit?.trim()?.takeIf { it.isNotEmpty() && metric != null }
        w.putType(
            ExerciseTypeRow(
                id.toLong(), n, flag(t.weight), flag(t.reps), flag(t.distance), flag(t.time), metric, unit
            )
        )
        id
    }

    /** Deletes a user-defined type. One still used by an exercise can't go, so no exercise is left without a type. */
    suspend fun deleteExerciseType(id: Int): Unit = write(LIBRARY) { w ->
        val users = w.countExercisesOfType(id)
        if (users > 0) {
            throw WorkoutDataException.counted(R.plurals.wde_type_in_use, users)
        }
        w.deleteType(id.toLong())
    }

    /** Deletes an exercise and every set logged for it. */
    suspend fun deleteExercise(id: Long): Unit = writeSets(WriteTimings.OTHER) { w, scope ->
        val row = w.exercise(id) ?: return@writeSets
        // Only its own sets go, so no other exercise's PRs change.
        scope.exercises += id
        scope.areas += Area.LIBRARY
        scope.areas += Area.NOTES
        val hadImports = row.fitnotes_id != null || w.hasImportedSets(id) || w.hasLinkTo(RULE_EXERCISE, id)
        w.deleteSetsOf(id)
        w.deleteGoalsOf(id)
        Routines.forgetExercise(Store.db.routineDao, id)
        w.deleteExerciseCommentsOf(id)
        w.deleteRestsOf(id)
        w.deleteExercise(id)
        w.relink(RULE_EXERCISE, id, null)
        if (hadImports) w.setLink(RULE_EXERCISE, nameKey(row.name), null)
    }

    /**
     * Merges exercise [fromId] into [intoId] and deletes [fromId] (#57), for duplicates such as "Bench Press" and
     * "Barbell Bench Press". Every set, goal and workout-day entry moves across with its date, place, superset and
     * tick. [intoId] keeps its own name, category, type and defaults, and takes [fromId]'s notes and star only when it
     * has none. Later FitNotes imports follow the merge: [fromId]'s name (and every name already linked to it) maps
     * onto [intoId], and skip rules for its deleted imported sets are re-keyed, so nothing comes back as a duplicate.
     * PR marks are replayed, since the joined history can change them. Returns how many sets moved.
     */
    suspend fun mergeExercises(fromId: Long, intoId: Long): Int = writeSets(WriteTimings.OTHER) { w, scope ->
        if (fromId == intoId) throw WorkoutDataException(R.string.wde_merge_same)
        scope.exercises += fromId
        scope.exercises += intoId
        scope.areas += Area.LIBRARY
        scope.areas += Area.NOTES
        val from = w.exerciseToMerge(fromId) ?: throw WorkoutDataException(R.string.wde_exercise_gone)
        val into = w.exerciseToMerge(intoId) ?: throw WorkoutDataException(R.string.wde_exercise_gone)
        val moved = w.countSetsOf(fromId)
        w.moveSetsOf(fromId, intoId)
        w.moveGoalsOf(fromId, intoId)
        w.movePlansOf(fromId, intoId)
        mergeExerciseComments(w, fromId, intoId)
        // Prescribed rests (#138) move too; on a date where both had one, the kept exercise's stays.
        w.moveRestsOf(fromId, intoId)
        w.deleteRestsOf(fromId)
        w.setMergedExercise(
            intoId,
            notes = if (into.notes.isNullOrBlank() && !from.notes.isNullOrBlank()) from.notes else into.notes,
            favourite = if (from.favourite != 0) 1 else into.favourite,
            source = Sources.FLEXNOTES
        )
        w.deleteExercise(fromId)
        // Imports: names that led to the old exercise now lead to the kept one, and its skipped sets stay skipped.
        w.relink(RULE_EXERCISE, fromId, intoId)
        w.setLink(RULE_EXERCISE, nameKey(from.name), intoId)
        val prefix = "$fromId|"
        w.rulesLike(RULE_SET, "$prefix%").forEach { w.setRuleKey(it.id, "$intoId|" + it.key.removePrefix(prefix)) }
        replayPrs(w, listOf(intoId))
        moved
    }

    /** Stars or unstars an exercise. Favourites are listed first when choosing an exercise. */
    suspend fun setFavourite(id: Long, favourite: Boolean): Unit = write(LIBRARY) { w ->
        w.setFavourite(id, flag(favourite))
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
        var order = w.lastCategoryOrder()
        val categories = w.categoryNames().toMutableList()
        val exercises = w.exerciseNames().toMutableList()
        StarterLibrary.categories.forEachIndexed { i, sc ->
            val categoryId = sameName(categories, sc.name) ?: run {
                order += 1
                categoriesAdded += 1
                w.clearDeletedLink(RULE_CATEGORY, sc.name)
                val colour = if (palette.isEmpty()) 0 else palette[i % palette.size]
                w.addCategory(sc.name, colour, order, Sources.FLEXNOTES).also { categories += IdName(it, sc.name) }
            }
            sc.exercises.forEach { se ->
                if (sameName(exercises, se.name) != null) {
                    skipped += 1
                } else {
                    w.clearDeletedLink(RULE_EXERCISE, se.name)
                    val id = w.addExercise(se.name, categoryId, se.type, null, Sources.FLEXNOTES)
                    exercises += IdName(id, se.name)
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
        rpe: Double? = null,
        /** The exercise type's own metric (#14), or null when it has none. */
        metric: Double? = null
    ): Long = writeSets { w, scope ->
        val d = checkDate(date)
        if (!w.exerciseExists(exerciseId)) throw WorkoutDataException(R.string.wde_exercise_gone)
        // Its PR mark is decided here against earlier sets, so no other set changes and only this exercise is re-read.
        scope.exercises += exerciseId
        val countWarmups = Settings.currentPortable().warmupsCount
        // A warm-up is never a record unless warm-ups count, and uncounted warm-ups never set the bar (#43).
        val pr = if (setType == SetTypes.WARMUP && !countWarmups) false else isPr ?: Records.isNewRecord(
            weightKg, reps, w.bestBefore(exerciseId, reps, d, countWarmups, SetTypes.WARMUP)
        )
        w.addSet(
            exerciseId, d, weightKg, reps, distance, durationSec, flag(pr), comment?.takeIf { it.isNotBlank() },
            Sources.FLEXNOTES, setType, rpe, metric, position = 0L, superset = 0, done = 0, restSeconds = null
        )
    }

    /** Saves changes to a set (matched by [SetRow.id]). An edited imported set becomes FlexNotes's own. */
    suspend fun updateSet(set: SetRow): Unit = writeSets { w, scope ->
        val d = checkDate(set.date)
        val old = w.setById(set.id) ?: throw WorkoutDataException(R.string.wde_set_gone)
        scope.exercises += old.exercise_id
        scope.exercises += set.exerciseId
        val newKey = setKey(set.exerciseId, d, set.weightKg, set.reps, set.distance, set.durationSec)
        if (old.source == Sources.FITNOTES && old.key() != newKey) w.addSkip(RULE_SET, old.key())
        w.updateSet(
            set.id, set.exerciseId, d, set.weightKg, set.reps, set.distance, set.durationSec, flag(set.isPr),
            set.comment?.takeIf { it.isNotBlank() }, Sources.FLEXNOTES, set.setType, set.rpe, set.metric
        )
        // A new weight, rep count or date can make or end a record, for this set and those logged after it.
        replayPrs(w, scope.exercises)
    }

    suspend fun deleteSet(id: Long): Unit = writeSets { w, scope ->
        scope.addSets(w, listOf(id))
        deleteSetsById(w, listOf(id))
        // A deleted record hands its mark to the next set that now beats everything before it.
        replayPrs(w, scope.exercises)
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
    suspend fun deleteHistory(from: String?, to: String?, exerciseIds: Set<Long>): Int =
        // Every exercise re-reads the whole history; named exercises re-read and replay only their own (#60).
        if (exerciseIds.isEmpty()) {
            write { w -> deleteRange(w, from, to, null) }
        } else {
            writeSets(WriteTimings.OTHER) { w, scope ->
                scope.exercises += exerciseIds
                scope.areas += Area.NOTES
                deleteRange(w, from, to, exerciseIds)
            }
        }

    /** [deleteHistory]'s transaction, for [exerciseIds] or (when null) every exercise. */
    private fun deleteRange(w: WorkoutDao, from: String?, to: String?, exerciseIds: Set<Long>?): Int {
        val every = exerciseIds == null
        val batches = if (exerciseIds == null) listOf(emptyList()) else exerciseIds.toList().chunked(Db.MAX_IDS)
        var count = 0
        batches.forEach { ids ->
            w.skipSets(w.setsInRangeWithSource(from, to, every, ids, Sources.FITNOTES))
            count += w.deleteSetsInRange(from, to, every, ids)
            // Exercise comments belong to the exercise in that day's workout, so they go with its sets (#107).
            w.deleteExerciseCommentsInRange(from, to, every, ids)
            w.deleteRestsInRange(from, to, every, ids)
        }
        if (count > 0) replayPrs(w, exerciseIds)
        return count
    }

    /**
     * Replays the PR marks of [exercises], or of every exercise when it's null (#23). A write replays only the
     * exercises whose sets it changed: no other exercise's records can move, so saving a set stays quick (#60).
     * Returns how many marks changed. Internal for `WorkoutDaoTest`.
     */
    internal fun replayPrs(
        w: WorkoutDao,
        exercises: Collection<Long>? = null,
        countWarmups: Boolean = Settings.currentPortable().warmupsCount
    ): Int {
        val candidates = when {
            exercises == null -> w.prCandidates()
            exercises.isEmpty() -> return 0
            else -> exercises.distinct().chunked(Db.MAX_IDS).flatMap { w.prCandidatesOf(it) }
        }
        val changes = mutableListOf<Pair<Long, Boolean>>()
        var exercise = -1L
        // best[r] = heaviest weight so far for at least r reps, for the exercise being replayed.
        var best = DoubleArray(0)
        candidates.forEach { s ->
            if (s.exercise_id != exercise) { exercise = s.exercise_id; best = DoubleArray(0) }
            // An uncounted warm-up loses any PR mark and doesn't set the bar for later sets (#43).
            if (!countWarmups && s.set_type == SetTypes.WARMUP) {
                if (s.is_pr != 0) changes += s.id to false
                return@forEach
            }
            if (best.size <= s.reps) best = best.copyOf(s.reps + 1)
            val pr = Records.isNewRecord(s.weight, s.reps, best[s.reps].takeIf { it > 0 })
            for (r in 1..s.reps) if (s.weight > best[r]) best[r] = s.weight
            if (pr != (s.is_pr != 0)) changes += s.id to pr
        }
        changes.forEach { (id, pr) -> w.setPr(id, flag(pr)) }
        return changes.size
    }

    /**
     * Puts whole sets back in one transaction, used to undo a delete. They return as FlexNotes's own rows on the
     * date they carry; their old ids are not reused. Returns how many were added.
     */
    suspend fun addSets(rows: List<SetRow>): Int = writeSets { w, scope ->
        scope.exercises += rows.map { it.exerciseId }
        rows.forEach { s ->
            // Back in its old place (#70), group (#18) and tick (#19); 0 lets the triggers decide.
            w.addSet(
                s.exerciseId, s.date.take(10), s.weightKg, s.reps, s.distance, s.durationSec, flag(s.isPr),
                s.comment?.takeIf { it.isNotBlank() }, Sources.FLEXNOTES, s.setType, s.rpe, s.metric, s.position,
                s.superset, flag(s.done), s.restSeconds
            )
            // Deleting an imported set left one skip rule; the set is back, so drop one matching rule too (#76).
            if (s.imported) {
                w.deleteOneSkip(RULE_SET, setKey(s.exerciseId, s.date, s.weightKg, s.reps, s.distance, s.durationSec))
            }
        }
        replayPrs(w, scope.exercises)
        rows.size
    }

    /** Deletes the sets with these ids; imported ones leave a skip rule. */
    private fun deleteSetsById(w: WorkoutDao, ids: Collection<Long>) = ids.inChunks { chunk ->
        w.skipSets(w.setsWithSource(chunk, Sources.FITNOTES))
        w.deleteSets(chunk)
    }

    // ---------- Workouts (everything on one date) ----------

    /** Replaces the workout comment for [date]. A blank comment removes it. */
    suspend fun setWorkoutComment(date: String, comment: String?): Unit = write(NOTES) { w ->
        val d = checkDate(date)
        w.skipImportedComments(d)
        w.deleteCommentsOn(d)
        val text = comment?.trim()
        if (!text.isNullOrEmpty()) w.addComment(d, text, Sources.FLEXNOTES)
    }

    /** Replaces the comment on exercise [exerciseId] in [date]'s workout (#107). A blank comment removes it. */
    suspend fun setExerciseComment(date: String, exerciseId: Long, comment: String?): Unit = write(NOTES) { w ->
        writeExerciseComment(w, checkDate(date), exerciseId, comment)
    }

    /** Puts [date]'s exercise comments back exactly as [comments] (exercise id to text), for Undo (#107). */
    suspend fun setExerciseComments(date: String, comments: Map<Long, String>): Unit = write(NOTES) { w ->
        val d = checkDate(date)
        w.deleteExerciseCommentsOn(d)
        comments.forEach { (ex, text) -> writeExerciseComment(w, d, ex, text) }
    }

    private fun writeExerciseComment(w: WorkoutDao, d: String, exerciseId: Long, comment: String?) {
        w.deleteExerciseComment(d, exerciseId)
        val text = comment?.trim()
        if (!text.isNullOrEmpty()) w.addExerciseComment(d, exerciseId, text, Sources.FLEXNOTES)
    }

    /** Moves exercise comments from exercise [fromId] to [intoId]; on a date where both have one, they're joined. */
    private fun mergeExerciseComments(w: WorkoutDao, fromId: Long, intoId: Long) {
        val moving = w.exerciseCommentsOf(fromId)
        w.deleteExerciseCommentsOf(fromId)
        moving.forEach { joinExerciseComment(w, it.date, intoId, it.comment) }
    }

    /** Stores the rest [r] prescribed for [exerciseId] on [d] (#138), or removes it when [r] is empty. */
    private fun putRest(w: WorkoutDao, d: String, exerciseId: Long, r: WorkoutRest) {
        if (r.isEmpty) w.deleteRest(d, exerciseId)
        else w.putRest(WorkoutRestRow(d, exerciseId, r.restSeconds, r.restAfterSeconds))
    }

    /** Adds [text] to exercise [exerciseId]'s comment on [d], after any comment already there. */
    private fun joinExerciseComment(w: WorkoutDao, d: String, exerciseId: Long, text: String) {
        val existing = w.exerciseComment(d, exerciseId)
        val joined = listOfNotNull(existing, text).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n\n")
        writeExerciseComment(w, d, exerciseId, joined)
    }

    /**
     * Replaces the workout start and end for [date]. Times use FitNotes's format (`yyyy-MM-dd HH:mm:ss`).
     * Both null removes them.
     */
    suspend fun setWorkoutTime(date: String, start: String?, finish: String?): Unit = write(NOTES) { w ->
        val d = checkDate(date)
        w.skipImportedTimes(d)
        w.deleteTimesOn(d)
        if (start != null || finish != null) w.addTime(d, start, finish, Sources.FLEXNOTES)
    }

    /**
     * Restores every time row for [date] at once. [setWorkoutTime] keeps only one pair, which is right when the
     * user is editing a single start/finish, but loses rows when undoing a delete on a day that carried several
     * (an imported day can) (#69). Passing an empty list clears the day's times.
     */
    suspend fun setWorkoutTimes(date: String, times: List<WorkoutTime>): Unit = write(NOTES) { w ->
        val d = checkDate(date)
        w.skipImportedTimes(d)
        w.deleteTimesOn(d)
        times.forEach { t -> w.addTime(d, t.start.ifBlank { null }, t.end.ifBlank { null }, Sources.FLEXNOTES) }
    }

    /**
     * Deletes the whole workout on [date]: its sets, comment and times. Measurements and photos are kept. The PR
     * marks of the exercises it held are replayed, since a record may have gone with it.
     */
    suspend fun deleteWorkout(date: String): Unit = writeSets(WriteTimings.OTHER) { w, scope ->
        val d = checkDate(date)
        scope.dates += d
        scope.exercises += w.exercisesOn(d)
        scope.areas += Area.LIBRARY
        scope.areas += Area.NOTES
        w.skipSets(w.setsOnWithSource(d, Sources.FITNOTES))
        w.deleteSetsOn(d)
        w.skipImportedComments(d)
        w.skipImportedTimes(d)
        w.deleteCommentsOn(d)
        w.deleteExerciseCommentsOn(d)
        w.deleteRestsOn(d)
        w.deleteTimesOn(d)
        w.deleteOrigin(d)
        replayPrs(w, scope.exercises)
    }

    /**
     * Copies sets from the workout on [from] to [to], as new FlexNotes sets. [setIds] limits it to those sets;
     * null copies the whole workout.
     *
     * The copies are added to whatever is already on [to] — nothing there is replaced. The originals are left
     * untouched, so no skip rule is needed. PR flags aren't copied but replayed for the exercises copied: a copy on
     * a later day can beat the best so far, and one on an earlier day can take a record from a later set (#23).
     * Returns the new sets' ids, so the copy can be undone (#84).
     */
    suspend fun copyWorkout(from: String, to: String, setIds: Collection<Long>? = null): List<Long> =
        writeSets(WriteTimings.OTHER) { w, scope ->
            val f = checkDate(from)
            val t = checkDate(to)
            if (setIds != null && setIds.isEmpty()) return@writeSets emptyList()
            val wanted = setIds?.toHashSet()
            val copies = w.setsOn(f).filter { wanted == null || it.id in wanted }
            scope.dates += t
            scope.exercises += copies.map { it.exercise_id }
            scope.areas += Area.NOTES
            // Supersets come across as new groups on the target day, after any it already has (#18).
            val offset = w.lastGroupOn(t) ?: 0
            val ids = copies.map { s ->
                w.addSet(
                    s.exercise_id, t, s.weight, s.reps, s.distance, s.duration.toInt(), isPr = 0, comment = s.comment,
                    source = Sources.FLEXNOTES, setType = s.set_type, rpe = s.rpe, metric = s.metric, position = 0L,
                    superset = if (s.superset > 0) s.superset + offset else 0, done = 0, restSeconds = s.rest_seconds
                )
            }
            // Exercise comments and prescribed rests (#107, #138) come along for the exercises copied, unless the
            // target day already has its own.
            val copied = copies.map { it.exercise_id }.toHashSet()
            if (copied.isNotEmpty()) {
                w.exerciseCommentsOn(f).filter { it.exercise_id in copied }
                    .forEach { w.addExerciseCommentIfNone(t, it.exercise_id, it.comment, Sources.FLEXNOTES) }
                w.restsOn(f).filter { it.exercise_id in copied }.forEach { w.addRestIfNone(it.copy(date = t)) }
            }
            replayPrs(w, scope.exercises)
            ids
        }

    /**
     * Deletes the sets with these ids in one transaction, used to undo a copy (#84). Imported sets leave a skip rule
     * like any other delete, and PR marks are replayed since a deleted set may have held one.
     */
    suspend fun deleteSets(ids: Collection<Long>): Int = writeSets(WriteTimings.OTHER) { w, scope ->
        if (ids.isEmpty()) return@writeSets 0
        val pairs = ArrayList<DayExercise>()
        ids.inChunks { pairs += w.daysOfSets(it) }
        scope.exercises += pairs.map { it.exercise_id }
        scope.dates += pairs.map { it.day }
        scope.areas += Area.NOTES
        deleteSetsById(w, ids)
        // Undoing a copy or a logged workout takes the exercise comments it brought along (#107): a comment goes
        // once its exercise has no sets left on that date.
        pairs.distinct().forEach { w.deleteExerciseCommentIfNoSets(it.day, it.exercise_id) }
        replayPrs(w, scope.exercises)
        ids.size
    }

    /**
     * Moves the sets with these ids to [to], keeping them otherwise as they are. Used to undo a move (#84). PR marks
     * are replayed for their exercises, since the order of their history changed.
     */
    suspend fun moveSets(ids: Collection<Long>, to: String): Unit = writeSets(WriteTimings.OTHER) { w, scope ->
        val t = checkDate(to)
        if (ids.isEmpty()) return@writeSets
        scope.addSets(w, ids)
        scope.dates += t
        ids.inChunks { w.moveSetsTo(it, t) }
        replayPrs(w, scope.exercises)
    }

    /**
     * Moves a whole workout (its sets, comment and times) from [from] to [to], merging into anything already
     * there rather than replacing it. Moved rows become FlexNotes's own, and any FitNotes row that moves leaves a
     * skip rule behind for its old date so a later import doesn't put the original back. PR marks are replayed for
     * the exercises moved, since a record can change hands when a workout moves past another. Returns sets moved.
     */
    suspend fun moveWorkout(from: String, to: String): Int = writeSets(WriteTimings.OTHER) { w, scope ->
        val f = checkDate(from)
        val t = checkDate(to)
        if (f == t) return@writeSets 0
        scope.exercises += w.exercisesOn(f)
        scope.dates += f
        scope.dates += t
        scope.areas += Area.LIBRARY
        scope.areas += Area.NOTES
        w.skipSets(w.setsOnWithSource(f, Sources.FITNOTES))
        w.skipImportedComments(f)
        w.skipImportedTimes(f)
        val moved = w.countSetsOn(f)
        // Moved supersets keep their groups, numbered after the target day's own so the two never merge (#18).
        val ssOffset = w.lastGroupOn(t) ?: 0
        if (ssOffset > 0) w.shiftGroupsOn(f, ssOffset)
        // Where the workout came from moves with it (#21), replacing the target day's.
        w.moveOrigin(f, t)
        w.moveSetsOn(f, t, Sources.FLEXNOTES)
        // Prescribed rests (#138) move with their sets; the target day's own win where both have one.
        w.moveRests(f, t)
        w.deleteRestsOn(f)
        // Exercise comments move too; one landing on an exercise that already has a comment there is joined (#107).
        val movingComments = w.exerciseCommentsOn(f)
        w.deleteExerciseCommentsOn(f)
        movingComments.forEach { joinExerciseComment(w, t, it.exercise_id, it.comment) }

        // Comments: if both days have one, merge into a single FlexNotes row (destination first) (#76).
        val movedComments = w.commentsOn(f)
        val destComments = w.commentsOn(t)
        if (movedComments.isNotEmpty() && destComments.isNotEmpty()) {
            destComments.filter { it.source == Sources.FITNOTES }.forEach { w.addSkip(RULE_COMMENT, commentKey(t, it.comment)) }
            val merged = (destComments + movedComments).map { it.comment.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
            w.deleteCommentsOn(f)
            w.deleteCommentsOn(t)
            if (merged.isNotEmpty()) w.addComment(t, merged, Sources.FLEXNOTES)
        } else {
            w.moveComments(f, t, Sources.FLEXNOTES)
        }

        // Times: if both days have them, keep one row from the earliest start to the latest finish (#76).
        // Timestamps are `yyyy-MM-dd HH:mm:ss`, so they compare correctly as text.
        val movedTimes = w.timesOn(f).map { it.copy(start = it.start?.let { s -> t + s.drop(10) }, finish = it.finish?.let { e -> t + e.drop(10) }) }
        val destTimes = w.timesOn(t)
        if (movedTimes.isNotEmpty() && destTimes.isNotEmpty()) {
            destTimes.filter { it.source == Sources.FITNOTES }.forEach { w.addSkip(RULE_TIME, timeKey(t, it.start, it.finish)) }
            val all = destTimes + movedTimes
            val start = all.mapNotNull { it.start }.minOrNull()
            val finish = all.mapNotNull { it.finish }.maxOrNull()
            w.deleteTimesOn(f)
            w.deleteTimesOn(t)
            if (start != null || finish != null) w.addTime(t, start, finish, Sources.FLEXNOTES)
        } else {
            // Start and finish are full timestamps that begin with the date, so their day part moves with the workout
            // and the recorded duration stays the same.
            w.moveTimes(f, t, Sources.FLEXNOTES)
        }
        replayPrs(w, scope.exercises)
        moved
    }
}
