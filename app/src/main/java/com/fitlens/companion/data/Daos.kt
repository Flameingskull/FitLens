package com.fitlens.companion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * Typed, compile-checked queries on `fitlens.db` (#36). Room checks every query here against `Schema.kt` when the
 * app is built, so a misspelt column or table fails the build instead of a screen. Reach them through [Db] (its
 * properties open the database first, so the downgrade check of #77 always runs before Room's own).
 *
 * Every DAO call runs off the main thread: Room refuses a query on the main thread, and every caller already runs on
 * `Dispatchers.IO`. Nothing in the app writes SQL to `fitlens.db` any other way; only the upgrades in `Db` do.
 */

/** Everything the in-memory snapshot is built from (`Store.build`), in the order each area expects. */
@Dao
interface SnapshotDao {
    @Query("SELECT * FROM category")
    fun categories(): List<CategoryRow>

    @Query("SELECT * FROM exercise")
    fun exercises(): List<ExerciseRow>

    @Query("SELECT * FROM exercise_type")
    fun exerciseTypes(): List<ExerciseTypeRow>

    @Query("SELECT * FROM exercise_goal ORDER BY exercise_id, sort_order, id")
    fun goals(): List<ExerciseGoalRow>

    @Query("SELECT * FROM routine ORDER BY sort_order, id")
    fun routines(): List<RoutineRow>

    @Query("SELECT * FROM routine_day ORDER BY routine_id, sort_order, id")
    fun routineDays(): List<RoutineDayRow>

    @Query("SELECT * FROM routine_day_exercise ORDER BY day_id, sort_order, id")
    fun routineDayExercises(): List<RoutineDayExerciseRow>

    @Query("SELECT * FROM routine_day_set ORDER BY item_id, sort_order, id")
    fun routineDaySets(): List<RoutineDaySetRow>

    @Query("SELECT * FROM workout_origin")
    fun workoutOrigins(): List<WorkoutOriginRow>

    /** Every logged set, in the order a day shows them. */
    @Query("SELECT * FROM workout_set ORDER BY date, position, id")
    fun sets(): List<WorkoutSetRow>

    /** The sets of [exerciseIds] and the sets on [days] (`yyyy-MM-dd`), for a small set write (#60). */
    @Query(
        "SELECT * FROM workout_set WHERE exercise_id IN (:exerciseIds) OR substr(date, 1, 10) IN (:days) " +
            "ORDER BY date, position, id"
    )
    fun setsFor(exerciseIds: List<Long>, days: List<String>): List<WorkoutSetRow>

    @Query("SELECT * FROM workout_comment ORDER BY id")
    fun workoutComments(): List<WorkoutCommentRow>

    @Query("SELECT * FROM workout_time ORDER BY id")
    fun workoutTimes(): List<WorkoutTimeRow>

    @Query("SELECT * FROM exercise_comment")
    fun exerciseComments(): List<ExerciseCommentRow>

    @Query("SELECT * FROM workout_rest")
    fun workoutRests(): List<WorkoutRestRow>

    @Query("SELECT * FROM measurement ORDER BY sort_order, name")
    fun measurements(): List<MeasurementRow>

    @Query("SELECT * FROM mrecord ORDER BY date, time")
    fun records(): List<MeasurementRecordRow>

    @Query("SELECT * FROM photo ORDER BY date, taken_at, id")
    fun photos(): List<PhotoRow>
}

/** The user's preferences (`meta`), which `Settings` keeps in memory and writes through. */
@Dao
abstract class MetaDao {
    @Query("SELECT v FROM meta WHERE k = :k")
    abstract fun get(k: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun put(row: MetaRow)

    @Query("SELECT EXISTS(SELECT 1 FROM meta WHERE k = :k)")
    abstract fun has(k: String): Boolean

    @Query("DELETE FROM meta WHERE k = :k")
    abstract fun delete(k: String)

    @Query("DELETE FROM meta WHERE k IN (:keys)")
    abstract fun deleteAll(keys: List<String>)

    /** Stores every value in one transaction; a null value removes its row. */
    @Transaction
    open fun putAll(values: Map<String, String?>) {
        values.forEach { (k, v) -> if (v == null) delete(k) else put(MetaRow(k, v)) }
    }
}

/** Measurement definitions and body values (`Store`'s body edits). */
@Dao
interface BodyDao {
    @Query("SELECT * FROM measurement WHERE name = :name")
    fun definition(name: String): MeasurementRow?

    /** A measurement seen only in its values gets a row, so it can hold a goal, an order or a unit. */
    @Query("INSERT OR IGNORE INTO measurement(name, unit, sort_order) VALUES(:name, :unit, :sortOrder)")
    fun ensure(name: String, unit: String, sortOrder: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun replaceDefinition(row: MeasurementRow)

    @Query("DELETE FROM measurement WHERE name = :name")
    fun deleteDefinition(name: String)

    @Query("DELETE FROM measurement WHERE name = :name AND custom = 1")
    fun deleteCustomDefinition(name: String)

    @Query("UPDATE measurement SET goal_type = :type, goal_value = :value, edited = 1 WHERE name = :name")
    fun setGoal(name: String, type: Int, value: Double)

    @Query("UPDATE measurement SET sort_order = :sortOrder, edited = 1 WHERE name = :name")
    fun setOrder(name: String, sortOrder: Int)

    @Query("UPDATE measurement SET enabled = :enabled, edited = 1 WHERE name = :name")
    fun setEnabled(name: String, enabled: Int)

    @Query("UPDATE measurement SET display_unit = :unit WHERE name = :name")
    fun setDisplayUnit(name: String, unit: String?)

    @Query("SELECT name FROM measurement")
    fun definitionNames(): List<String>

    @Query("SELECT DISTINCT name FROM mrecord")
    fun recordNames(): List<String>

    /** The last place in the user's own order (the 900s hold measurements placed at the end). */
    @Query("SELECT IFNULL(MAX(sort_order), 0) FROM measurement WHERE sort_order < 900")
    fun lastOrder(): Int

    @Query(
        "INSERT OR IGNORE INTO measurement(name, unit, sort_order, enabled, custom, edited) " +
            "VALUES(:name, :unit, :sortOrder, 1, 1, 1)"
    )
    fun addOwn(name: String, unit: String, sortOrder: Int)

    /** The FitNotes measurement a custom metric's [match] (lower case, trimmed) matches, if any. */
    @Query("SELECT name FROM measurement WHERE custom = 0 AND lower(trim(name)) = :match LIMIT 1")
    fun importedNamed(match: String): String?

    @Query("SELECT unit FROM mrecord WHERE name = :name AND unit <> '' LIMIT 1")
    fun recordUnit(name: String): String?

    @Query("SELECT unit FROM mrecord WHERE id = :id")
    fun unitOfRecord(id: Long): String?

    @Query("SELECT m.display_unit FROM measurement m JOIN mrecord r ON r.name = m.name WHERE r.id = :id")
    fun displayUnitOfRecord(id: Long): String?

    @Query(
        "INSERT INTO mrecord(name, unit, date, time, value, comment, source) " +
            "VALUES(:name, :unit, :date, :time, :value, :comment, 'manual')"
    )
    fun addManualRecord(name: String, unit: String, date: String, time: String, value: Double, comment: String?)

    @Query("UPDATE mrecord SET date = :date, time = :time, value = :value, comment = :comment WHERE id = :id AND source = 'manual'")
    fun updateManualRecord(id: Long, date: String, time: String, value: Double, comment: String?)

    @Query("DELETE FROM mrecord WHERE id = :id AND source = 'manual'")
    fun deleteManualRecord(id: Long)

    @Query("DELETE FROM mrecord WHERE name = :name AND source = 'manual'")
    fun deleteManualRecords(name: String)

    @Query("UPDATE mrecord SET name = :newName WHERE name = :oldName AND source = 'manual'")
    fun renameManualRecords(oldName: String, newName: String)

    @Query("UPDATE mrecord SET name = :newName WHERE name = :oldName AND source IN ('fitnotes', 'csv')")
    fun renameImportedRecords(oldName: String, newName: String)

    /** Puts imported values whose name matches [match] (lower case, trimmed) under the measurement [name]. */
    @Query("UPDATE mrecord SET name = :name WHERE source IN ('fitnotes', 'csv') AND lower(trim(name)) = :match")
    fun claimImportedRecords(name: String, match: String)

    /** A measurement with no unit takes the unit its values use. */
    @Query(
        "UPDATE measurement SET unit = IFNULL((SELECT unit FROM mrecord WHERE name = :name AND unit <> '' LIMIT 1), '') " +
            "WHERE name = :name"
    )
    fun takeUnitFromRecords(name: String)

    /** Values entered by hand follow their measurement's unit. */
    @Query("UPDATE mrecord SET unit = (SELECT unit FROM measurement WHERE name = :name) WHERE name = :name AND source = 'manual'")
    fun matchManualUnits(name: String)
}

/** Progress photos: the import (`PhotoImporter`) and edits (`Store`). Callers pass at most [Db.MAX_IDS] ids at a time. */
@Dao
interface PhotoDao {
    @Query("UPDATE photo SET date = :date, date_source = :dateSource WHERE id IN (:ids)")
    fun setDate(ids: List<Long>, date: String?, dateSource: String)

    /** Accepts the detected date of each photo that has one. */
    @Query("UPDATE photo SET date_source = :dateSource WHERE id IN (:ids) AND date IS NOT NULL")
    fun confirmDates(ids: List<Long>, dateSource: String)

    /** Re-dates one photo (#163), or puts its old date back on Undo. A date set by hand in the meantime stays. */
    @Query("UPDATE photo SET date = :date, taken_at = :takenAt, date_source = :dateSource WHERE id = :id AND date_source != 'manual'")
    fun redate(id: Long, date: String?, takenAt: String?, dateSource: String)

    @Query("UPDATE photo SET pose = :pose WHERE id IN (:ids)")
    fun setPose(ids: List<Long>, pose: String)

    @Query("UPDATE photo SET note = :note WHERE id = :id")
    fun setNote(id: Long, note: String)

    @Query("SELECT file FROM photo WHERE id IN (:ids)")
    fun files(ids: List<Long>): List<String>

    @Query("DELETE FROM photo WHERE id IN (:ids)")
    fun delete(ids: List<Long>)

    @Query("SELECT EXISTS(SELECT 1 FROM photo WHERE hash = :hash)")
    fun hasHash(hash: String): Boolean

    /** Adds an imported photo; returns -1 when one with the same hash is already there. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun add(row: PhotoRow): Long
}

/** Exercise goals (#25). */
@Dao
interface GoalDao {
    @Query("SELECT IFNULL(MAX(sort_order), -1) + 1 FROM exercise_goal WHERE exercise_id = :exerciseId")
    fun nextOrder(exerciseId: Long): Int

    @Query("INSERT INTO exercise_goal(exercise_id, kind, target, sort_order) VALUES(:exerciseId, :kind, :target, :sortOrder)")
    fun add(exerciseId: Long, kind: Int, target: Double, sortOrder: Int)

    @Query("UPDATE exercise_goal SET exercise_id = :exerciseId, kind = :kind, target = :target WHERE id = :id")
    fun update(id: Long, exerciseId: Long, kind: Int, target: Double)

    @Query("UPDATE exercise_goal SET sort_order = :sortOrder WHERE id = :id")
    fun setOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM exercise_goal WHERE id = :id")
    fun delete(id: Long)
}

/** A row's id and name, for the library's by-name checks (ignoring case and spacing, see `Workouts.nameKey`). */
data class IdName(val id: Long, val name: String)

/** A category's or exercise's name, and its FitNotes id when it was imported. */
data class NameAndOrigin(val name: String, val fitnotes_id: Long?)

/** What `Workouts.mergeExercises` needs of each exercise. */
data class MergeExercise(val name: String, val notes: String?, val favourite: Int)

/** One weight-and-reps set, as the PR replay reads it (#23). */
data class PrCandidate(val id: Long, val exercise_id: Long, val weight: Double, val reps: Int, val is_pr: Int, val set_type: Int)

/** An exercise logged on a day (`yyyy-MM-dd`). */
data class DayExercise(val day: String, val exercise_id: Long)

/**
 * Workout data (`Workouts`): the library, logged sets, each day's notes, times and rests, and the import rules that
 * stop FitNotes imports undoing what the user did (#6). Every write runs inside `Workouts`' own transaction. Lists of
 * ids are passed at most [Db.MAX_IDS] at a time.
 */
@Dao
interface WorkoutDao {
    // ---- Import rules ----

    @Query("DELETE FROM import_rule WHERE kind = :kind AND key = :ruleKey")
    fun deleteRule(kind: String, ruleKey: String)

    @Query("INSERT INTO import_rule(kind, key, target_id) VALUES(:kind, :ruleKey, :targetId)")
    fun addRule(kind: String, ruleKey: String, targetId: Long?)

    /** A name the user re-creates stops being skipped by imports. */
    @Query("DELETE FROM import_rule WHERE kind = :kind AND key = :ruleKey AND target_id IS NULL")
    fun deleteSkips(kind: String, ruleKey: String)

    /** Drops one skip rule with this key, when the row it stood for comes back (#76). */
    @Query(
        "DELETE FROM import_rule WHERE id = (SELECT id FROM import_rule " +
            "WHERE kind = :kind AND key = :ruleKey AND target_id IS NULL LIMIT 1)"
    )
    fun deleteOneSkip(kind: String, ruleKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM import_rule WHERE kind = :kind AND target_id = :targetId)")
    fun hasLinkTo(kind: String, targetId: Long): Boolean

    @Query("UPDATE import_rule SET target_id = :intoId WHERE kind = :kind AND target_id = :fromId")
    fun relink(kind: String, fromId: Long, intoId: Long?)

    @Query("SELECT * FROM import_rule WHERE kind = :kind AND key LIKE :pattern")
    fun rulesLike(kind: String, pattern: String): List<ImportRuleRow>

    @Query("UPDATE import_rule SET key = :ruleKey WHERE id = :id")
    fun setRuleKey(id: Long, ruleKey: String)

    // ---- Categories ----

    @Query("SELECT id, name FROM category")
    fun categoryNames(): List<IdName>

    @Query("SELECT name, fitnotes_id FROM category WHERE id = :id")
    fun category(id: Long): NameAndOrigin?

    @Query("SELECT IFNULL(MAX(sort_order), 0) FROM category")
    fun lastCategoryOrder(): Int

    @Query("INSERT INTO category(name, colour, sort_order, source) VALUES(:name, :colour, :sortOrder, :source)")
    fun addCategory(name: String, colour: Int, sortOrder: Int, source: String): Long

    @Query("UPDATE category SET name = :name, colour = :colour, source = :source WHERE id = :id")
    fun updateCategory(id: Long, name: String, colour: Int, source: String)

    @Query("UPDATE category SET sort_order = :sortOrder WHERE id = :id")
    fun setCategoryOrder(id: Long, sortOrder: Int)

    @Query("UPDATE exercise SET category_id = :intoId WHERE category_id = :fromId")
    fun moveExercisesToCategory(fromId: Long, intoId: Long)

    @Query("DELETE FROM category WHERE id = :id")
    fun deleteCategory(id: Long)

    // ---- Exercises ----

    @Query("SELECT id, name FROM exercise")
    fun exerciseNames(): List<IdName>

    @Query("SELECT EXISTS(SELECT 1 FROM exercise WHERE id = :id)")
    fun exerciseExists(id: Long): Boolean

    @Query("SELECT name, fitnotes_id FROM exercise WHERE id = :id")
    fun exercise(id: Long): NameAndOrigin?

    @Query("SELECT name, notes, favourite FROM exercise WHERE id = :id")
    fun exerciseToMerge(id: Long): MergeExercise?

    @Query("INSERT INTO exercise(name, category_id, type, notes, source) VALUES(:name, :categoryId, :type, :notes, :source)")
    fun addExercise(name: String, categoryId: Long, type: Int, notes: String?, source: String): Long

    @Query(
        "UPDATE exercise SET name = :name, category_id = :categoryId, type = :type, notes = :notes, source = :source " +
            "WHERE id = :id"
    )
    fun updateExercise(id: Long, name: String, categoryId: Long, type: Int, notes: String?, source: String)

    @Query(
        "UPDATE exercise SET weight_step = :weightStep, default_graph = :defaultGraph, rest_seconds = :restSeconds, " +
            "distance_unit = :distanceUnit, weight_unit = :weightUnit WHERE id = :id"
    )
    fun setExerciseDefaults(
        id: Long, weightStep: Double?, defaultGraph: Int, restSeconds: Int?, distanceUnit: String?, weightUnit: String?
    )

    @Query("UPDATE exercise SET notes = :notes, favourite = :favourite, source = :source WHERE id = :id")
    fun setMergedExercise(id: Long, notes: String?, favourite: Int, source: String)

    @Query("UPDATE exercise SET favourite = :favourite WHERE id = :id")
    fun setFavourite(id: Long, favourite: Int)

    @Query("DELETE FROM exercise WHERE id = :id")
    fun deleteExercise(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM workout_set WHERE exercise_id = :exerciseId AND fitnotes_id IS NOT NULL)")
    fun hasImportedSets(exerciseId: Long): Boolean

    @Query("SELECT COUNT(*) FROM workout_set WHERE exercise_id = :exerciseId")
    fun countSetsOf(exerciseId: Long): Int

    @Query("DELETE FROM workout_set WHERE exercise_id = :exerciseId")
    fun deleteSetsOf(exerciseId: Long)

    @Query("DELETE FROM exercise_goal WHERE exercise_id = :exerciseId")
    fun deleteGoalsOf(exerciseId: Long)

    @Query("DELETE FROM exercise_comment WHERE exercise_id = :exerciseId")
    fun deleteExerciseCommentsOf(exerciseId: Long)

    @Query("DELETE FROM workout_rest WHERE exercise_id = :exerciseId")
    fun deleteRestsOf(exerciseId: Long)

    @Query("UPDATE workout_set SET exercise_id = :intoId WHERE exercise_id = :fromId")
    fun moveSetsOf(fromId: Long, intoId: Long)

    @Query("UPDATE exercise_goal SET exercise_id = :intoId WHERE exercise_id = :fromId")
    fun moveGoalsOf(fromId: Long, intoId: Long)

    @Query("UPDATE routine_day_exercise SET exercise_id = :intoId WHERE exercise_id = :fromId")
    fun movePlansOf(fromId: Long, intoId: Long)

    /** Moves prescribed rests (#138); on a date where both exercises have one, the kept exercise's stays. */
    @Query("UPDATE OR IGNORE workout_rest SET exercise_id = :intoId WHERE exercise_id = :fromId")
    fun moveRestsOf(fromId: Long, intoId: Long)

    @Query("SELECT * FROM exercise_comment WHERE exercise_id = :exerciseId")
    fun exerciseCommentsOf(exerciseId: Long): List<ExerciseCommentRow>

    // ---- Exercise types (#14) ----

    @Query("SELECT name FROM exercise_type WHERE id <> :id")
    fun typeNamesExcept(id: Long): List<String>

    @Query("SELECT MAX(id) FROM exercise_type")
    fun lastTypeId(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putType(row: ExerciseTypeRow)

    @Query("SELECT COUNT(*) FROM exercise WHERE type = :type")
    fun countExercisesOfType(type: Int): Int

    @Query("DELETE FROM exercise_type WHERE id = :id")
    fun deleteType(id: Long)

    // ---- Logged sets ----

    /** A new set. A [position] or [superset] of 0 lets the database's triggers place it (#70, #18). */
    @Query(
        "INSERT INTO workout_set(exercise_id, date, weight, reps, distance, duration, is_pr, comment, source, set_type, " +
            "rpe, metric, position, superset, done, rest_seconds) VALUES(:exerciseId, :date, :weight, :reps, :distance, " +
            ":duration, :isPr, :comment, :source, :setType, :rpe, :metric, :position, :superset, :done, :restSeconds)"
    )
    fun addSet(
        exerciseId: Long, date: String, weight: Double, reps: Int, distance: Double, duration: Int, isPr: Int,
        comment: String?, source: String, setType: Int, rpe: Double?, metric: Double?, position: Long, superset: Int,
        done: Int, restSeconds: Int?
    ): Long

    @Query(
        "UPDATE workout_set SET exercise_id = :exerciseId, date = :date, weight = :weight, reps = :reps, " +
            "distance = :distance, duration = :duration, is_pr = :isPr, comment = :comment, source = :source, " +
            "set_type = :setType, rpe = :rpe, metric = :metric WHERE id = :id"
    )
    fun updateSet(
        id: Long, exerciseId: Long, date: String, weight: Double, reps: Int, distance: Double, duration: Int, isPr: Int,
        comment: String?, source: String, setType: Int, rpe: Double?, metric: Double?
    )

    @Query("SELECT * FROM workout_set WHERE id = :id")
    fun setById(id: Long): WorkoutSetRow?

    @Query("SELECT * FROM workout_set WHERE id IN (:ids) AND source = :source")
    fun setsWithSource(ids: List<Long>, source: String): List<WorkoutSetRow>

    @Query("SELECT DISTINCT exercise_id FROM workout_set WHERE id IN (:ids)")
    fun exercisesOfSets(ids: List<Long>): List<Long>

    @Query("SELECT DISTINCT substr(date, 1, 10) AS day, exercise_id FROM workout_set WHERE id IN (:ids)")
    fun daysOfSets(ids: List<Long>): List<DayExercise>

    @Query("DELETE FROM workout_set WHERE id IN (:ids)")
    fun deleteSets(ids: List<Long>)

    @Query("SELECT * FROM workout_set WHERE substr(date, 1, 10) = :day AND exercise_id = :exerciseId")
    fun setsOfExerciseOn(day: String, exerciseId: Long): List<WorkoutSetRow>

    @Query("UPDATE workout_set SET exercise_id = :exerciseId WHERE id IN (:ids)")
    fun setExercise(ids: List<Long>, exerciseId: Long)

    @Query("UPDATE workout_set SET exercise_id = :exerciseId, source = :source WHERE id IN (:ids)")
    fun moveSetsToExercise(ids: List<Long>, exerciseId: Long, source: String)

    @Query("UPDATE workout_set SET comment = :comment, source = :source WHERE id = :id")
    fun setComment(id: Long, comment: String?, source: String): Int

    @Query("UPDATE workout_set SET done = :done WHERE id = :id")
    fun setDone(id: Long, done: Int)

    @Query("UPDATE workout_set SET position = :position WHERE id = :id")
    fun setPosition(id: Long, position: Long)

    @Query("UPDATE workout_set SET is_pr = :isPr WHERE id = :id")
    fun setPr(id: Long, isPr: Int)

    @Query("UPDATE workout_set SET date = :date WHERE id IN (:ids)")
    fun moveSetsTo(ids: List<Long>, date: String)

    /** The heaviest set of at least [reps] reps on or before [day]; warm-ups count only when [countWarmups] (#43). */
    @Query(
        "SELECT MAX(weight) FROM workout_set WHERE exercise_id = :exerciseId AND reps >= :reps AND weight > 0 " +
            "AND substr(date, 1, 10) <= :day AND (:countWarmups OR set_type <> :warmup)"
    )
    fun bestBefore(exerciseId: Long, reps: Int, day: String, countWarmups: Boolean, warmup: Int): Double?

    /** Every weight-and-reps set, per exercise in date and log order, for the PR replay (#23). */
    @Query(
        "SELECT id, exercise_id, weight, reps, is_pr, set_type FROM workout_set WHERE weight > 0 AND reps > 0 " +
            "ORDER BY exercise_id, substr(date, 1, 10), id"
    )
    fun prCandidates(): List<PrCandidate>

    /** [prCandidates] for these exercises only, so a write replays the PRs it can change and no others (#60). */
    @Query(
        "SELECT id, exercise_id, weight, reps, is_pr, set_type FROM workout_set WHERE weight > 0 AND reps > 0 " +
            "AND exercise_id IN (:exerciseIds) ORDER BY exercise_id, substr(date, 1, 10), id"
    )
    fun prCandidatesOf(exerciseIds: List<Long>): List<PrCandidate>

    @Query("SELECT DISTINCT exercise_id FROM workout_set WHERE date = :date")
    fun exercisesOn(date: String): List<Long>

    // ---- Supersets (#18) ----

    @Query("SELECT MAX(superset) FROM workout_set WHERE substr(date, 1, 10) = :day")
    fun lastGroupOn(day: String): Int?

    @Query("SELECT MAX(superset) FROM workout_set WHERE substr(date, 1, 10) = :day AND exercise_id IN (:exerciseIds)")
    fun groupOf(day: String, exerciseIds: List<Long>): Int?

    @Query("UPDATE workout_set SET superset = :groupNo WHERE substr(date, 1, 10) = :day AND exercise_id IN (:exerciseIds)")
    fun setGroup(day: String, exerciseIds: List<Long>, groupNo: Int)

    @Query("SELECT COUNT(DISTINCT exercise_id) FROM workout_set WHERE substr(date, 1, 10) = :day AND superset = :groupNo")
    fun exercisesInGroup(day: String, groupNo: Int): Int

    @Query("UPDATE workout_set SET superset = 0 WHERE substr(date, 1, 10) = :day AND superset = :groupNo")
    fun dissolveGroup(day: String, groupNo: Int)

    // ---- Deleting history (#32): [from] and [to] are inclusive dates, null for open-ended ----

    @Query(
        "SELECT * FROM workout_set WHERE (:fromDate IS NULL OR substr(date, 1, 10) >= :fromDate) " +
            "AND (:toDate IS NULL OR substr(date, 1, 10) <= :toDate) AND (:everyExercise OR exercise_id IN (:exerciseIds)) " +
            "AND source = :source"
    )
    fun setsInRangeWithSource(
        fromDate: String?, toDate: String?, everyExercise: Boolean, exerciseIds: List<Long>, source: String
    ): List<WorkoutSetRow>

    @Query(
        "DELETE FROM workout_set WHERE (:fromDate IS NULL OR substr(date, 1, 10) >= :fromDate) " +
            "AND (:toDate IS NULL OR substr(date, 1, 10) <= :toDate) AND (:everyExercise OR exercise_id IN (:exerciseIds))"
    )
    fun deleteSetsInRange(fromDate: String?, toDate: String?, everyExercise: Boolean, exerciseIds: List<Long>): Int

    @Query(
        "DELETE FROM exercise_comment WHERE (:fromDate IS NULL OR date >= :fromDate) AND (:toDate IS NULL OR date <= :toDate) " +
            "AND (:everyExercise OR exercise_id IN (:exerciseIds))"
    )
    fun deleteExerciseCommentsInRange(fromDate: String?, toDate: String?, everyExercise: Boolean, exerciseIds: List<Long>)

    @Query(
        "DELETE FROM workout_rest WHERE (:fromDate IS NULL OR date >= :fromDate) AND (:toDate IS NULL OR date <= :toDate) " +
            "AND (:everyExercise OR exercise_id IN (:exerciseIds))"
    )
    fun deleteRestsInRange(fromDate: String?, toDate: String?, everyExercise: Boolean, exerciseIds: List<Long>)

    // ---- A whole day's workout ----

    @Query("SELECT * FROM workout_set WHERE date = :date ORDER BY position, id")
    fun setsOn(date: String): List<WorkoutSetRow>

    @Query("SELECT * FROM workout_set WHERE date = :date AND source = :source")
    fun setsOnWithSource(date: String, source: String): List<WorkoutSetRow>

    @Query("SELECT COUNT(*) FROM workout_set WHERE date = :date")
    fun countSetsOn(date: String): Int

    @Query("DELETE FROM workout_set WHERE date = :date")
    fun deleteSetsOn(date: String)

    @Query("UPDATE workout_set SET superset = superset + :shift WHERE date = :date AND superset > 0")
    fun shiftGroupsOn(date: String, shift: Int)

    @Query("UPDATE workout_set SET date = :toDate, source = :source WHERE date = :fromDate")
    fun moveSetsOn(fromDate: String, toDate: String, source: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putOrigin(row: WorkoutOriginRow)

    /** Where the workout came from moves with it (#21), replacing the target day's. */
    @Query("UPDATE OR REPLACE workout_origin SET date = :toDate WHERE date = :fromDate")
    fun moveOrigin(fromDate: String, toDate: String)

    @Query("DELETE FROM workout_origin WHERE date = :date")
    fun deleteOrigin(date: String)

    // ---- Workout comments and times ----

    @Query("SELECT * FROM workout_comment WHERE date = :date ORDER BY id")
    fun commentsOn(date: String): List<WorkoutCommentRow>

    @Query("INSERT INTO workout_comment(date, comment, source) VALUES(:date, :comment, :source)")
    fun addComment(date: String, comment: String, source: String)

    @Query("UPDATE workout_comment SET date = :toDate, source = :source WHERE date = :fromDate")
    fun moveComments(fromDate: String, toDate: String, source: String)

    @Query("DELETE FROM workout_comment WHERE date = :date")
    fun deleteCommentsOn(date: String)

    @Query("SELECT * FROM workout_time WHERE date = :date ORDER BY id")
    fun timesOn(date: String): List<WorkoutTimeRow>

    @Query("INSERT INTO workout_time(date, start, finish, source) VALUES(:date, :start, :finish, :source)")
    fun addTime(date: String, start: String?, finish: String?, source: String)

    /** Start and finish begin with the date, so their day part moves with the workout and the duration is kept. */
    @Query(
        "UPDATE workout_time SET date = :toDate, source = :source, " +
            "start = CASE WHEN start IS NULL THEN NULL ELSE :toDate || substr(start, 11) END, " +
            "finish = CASE WHEN finish IS NULL THEN NULL ELSE :toDate || substr(finish, 11) END WHERE date = :fromDate"
    )
    fun moveTimes(fromDate: String, toDate: String, source: String)

    @Query("DELETE FROM workout_time WHERE date = :date")
    fun deleteTimesOn(date: String)

    // ---- Exercise comments (#107) ----

    @Query("SELECT * FROM exercise_comment WHERE date = :date")
    fun exerciseCommentsOn(date: String): List<ExerciseCommentRow>

    @Query("SELECT comment FROM exercise_comment WHERE date = :date AND exercise_id = :exerciseId")
    fun exerciseComment(date: String, exerciseId: Long): String?

    @Query("INSERT INTO exercise_comment(date, exercise_id, comment, source) VALUES(:date, :exerciseId, :comment, :source)")
    fun addExerciseComment(date: String, exerciseId: Long, comment: String, source: String)

    /** Adds the comment unless the exercise already has one that day. */
    @Query(
        "INSERT OR IGNORE INTO exercise_comment(date, exercise_id, comment, source) " +
            "VALUES(:date, :exerciseId, :comment, :source)"
    )
    fun addExerciseCommentIfNone(date: String, exerciseId: Long, comment: String, source: String)

    @Query("DELETE FROM exercise_comment WHERE date = :date AND exercise_id = :exerciseId")
    fun deleteExerciseComment(date: String, exerciseId: Long)

    @Query("DELETE FROM exercise_comment WHERE date = :date")
    fun deleteExerciseCommentsOn(date: String)

    /** Drops [exerciseId]'s comment on [day] once it has no sets left there (#107). */
    @Query(
        "DELETE FROM exercise_comment WHERE date = :day AND exercise_id = :exerciseId AND NOT EXISTS " +
            "(SELECT 1 FROM workout_set WHERE substr(date, 1, 10) = :day AND exercise_id = :exerciseId)"
    )
    fun deleteExerciseCommentIfNoSets(day: String, exerciseId: Long)

    // ---- Prescribed rests (#138) ----

    @Query("SELECT * FROM workout_rest WHERE date = :date")
    fun restsOn(date: String): List<WorkoutRestRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putRest(row: WorkoutRestRow)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addRestIfNone(row: WorkoutRestRow)

    @Query("DELETE FROM workout_rest WHERE date = :date AND exercise_id = :exerciseId")
    fun deleteRest(date: String, exerciseId: Long)

    @Query("DELETE FROM workout_rest WHERE date = :date")
    fun deleteRestsOn(date: String)

    /** The rest belongs to the exercise's place in the workout, so it follows a swap. */
    @Query("UPDATE OR REPLACE workout_rest SET exercise_id = :toId WHERE date = :date AND exercise_id = :fromId")
    fun swapRest(date: String, fromId: Long, toId: Long)

    /** Rests move with their sets; the target day's own win where both have one. */
    @Query("UPDATE OR IGNORE workout_rest SET date = :toDate WHERE date = :fromDate")
    fun moveRests(fromDate: String, toDate: String)
}

/** A category or exercise already in FlexNotes, as a FitNotes import matches against it. */
data class LibraryOrigin(val id: Long, val name: String, val source: String, val fitnotes_id: Long?)

/** The values that tell one logged set from another (`Workouts.setKey`). */
data class SetValues(val exercise_id: Long, val date: String, val weight: Double, val reps: Int, val distance: Double, val duration: Long)

/** A custom metric and the FitNotes measurement name it takes values from. */
data class NameLink(val name: String, val link: String?)

/**
 * The FitNotes import (`FitNotesImporter`): what it matches against and the rows it adds. It only adds, apart from
 * refreshing the unit, order and goal of measurements that came from FitNotes. Every call runs inside the import's
 * own transaction.
 */
@Dao
interface ImportDao {
    @Query("SELECT * FROM import_rule ORDER BY id")
    fun rules(): List<ImportRuleRow>

    @Query("SELECT id, name, source, fitnotes_id FROM category ORDER BY id")
    fun categories(): List<LibraryOrigin>

    @Query("SELECT id, name, source, fitnotes_id FROM exercise ORDER BY id")
    fun exercises(): List<LibraryOrigin>

    @Query(
        "INSERT INTO category(name, colour, sort_order, source, fitnotes_id) " +
            "VALUES(:name, :colour, :sortOrder, :source, :fitnotesId)"
    )
    fun addCategory(name: String, colour: Int, sortOrder: Int, source: String, fitnotesId: Long): Long

    @Query(
        "INSERT INTO exercise(name, category_id, type, notes, source, fitnotes_id) " +
            "VALUES(:name, :categoryId, :type, :notes, :source, :fitnotesId)"
    )
    fun addExercise(name: String, categoryId: Long, type: Int, notes: String?, source: String, fitnotesId: Long): Long

    @Query("SELECT exercise_id, date, weight, reps, distance, duration FROM workout_set")
    fun setValues(): List<SetValues>

    /** An imported set. Its position and superset are 0, so the database's triggers place it (#70, #18). */
    @Query(
        "INSERT INTO workout_set(exercise_id, date, weight, reps, distance, duration, is_pr, comment, source, fitnotes_id) " +
            "VALUES(:exerciseId, :date, :weight, :reps, :distance, :duration, :isPr, :comment, :source, :fitnotesId)"
    )
    fun addSet(
        exerciseId: Long, date: String, weight: Double, reps: Int, distance: Double, duration: Int, isPr: Int,
        comment: String?, source: String, fitnotesId: Long
    ): Long

    @Query("SELECT name, link FROM measurement WHERE custom = 1")
    fun customMetrics(): List<NameLink>

    /** A custom metric with no unit takes the unit of the FitNotes measurement it matches. */
    @Query("UPDATE measurement SET unit = :unit WHERE name = :name AND unit = ''")
    fun setUnitIfNone(name: String, unit: String)

    @Query(
        "INSERT INTO measurement(name, unit, sort_order, goal_type, goal_value, enabled) " +
            "VALUES(:name, :unit, :sortOrder, :goalType, :goalValue, :enabled)"
    )
    fun addMeasurement(name: String, unit: String, sortOrder: Int, goalType: Int, goalValue: Double, enabled: Int)

    @Query(
        "UPDATE measurement SET unit = :unit, sort_order = :sortOrder, goal_type = :goalType, goal_value = :goalValue, " +
            "enabled = :enabled WHERE name = :name"
    )
    fun refreshMeasurement(name: String, unit: String, sortOrder: Int, goalType: Int, goalValue: Double, enabled: Int)

    @Query("UPDATE measurement SET unit = :unit WHERE name = :name")
    fun setMeasurementUnit(name: String, unit: String)

    @Query(
        "INSERT INTO mrecord(name, unit, date, time, value, comment, source) " +
            "VALUES(:name, :unit, :date, :time, :value, :comment, :source)"
    )
    fun addRecord(name: String, unit: String, date: String, time: String, value: Double, comment: String?, source: String): Long

    /** A value already there: the same measurement, day and value, at the same time or entered by hand. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM mrecord WHERE name = :name AND date = :date AND abs(value - :value) < 0.001 " +
            "AND (time = :time OR source = 'manual'))"
    )
    fun hasRecord(name: String, date: String, time: String, value: Double): Boolean
}

/** Upkeep on the database file itself (`Backups`). */
@Dao
interface MaintenanceDao {
    /** Runs a PRAGMA that returns rows, such as `wal_checkpoint`, and gives its first column. */
    @RawQuery
    fun pragma(query: SupportSQLiteQuery): Int
}

/** User-made workouts and their days (`Routines`, #21, #106). */
@Dao
interface RoutineDao {
    @Query("SELECT IFNULL(MAX(sort_order), -1) + 1 FROM routine")
    fun nextRoutineOrder(): Int

    @Insert
    fun addRoutine(row: RoutineRow): Long

    @Query("UPDATE routine SET name = :name, notes = :notes WHERE id = :id")
    fun updateRoutine(id: Long, name: String, notes: String?)

    @Query("DELETE FROM routine WHERE id = :id")
    fun deleteRoutine(id: Long)

    @Query("SELECT id FROM routine_day WHERE routine_id = :routineId")
    fun dayIds(routineId: Long): List<Long>

    @Query("SELECT IFNULL(MAX(sort_order), -1) + 1 FROM routine_day WHERE routine_id = :routineId")
    fun nextDayOrder(routineId: Long): Int

    @Query("INSERT INTO routine_day(routine_id, name, sort_order) VALUES(:routineId, :name, :sortOrder)")
    fun addDay(routineId: Long, name: String, sortOrder: Int): Long

    @Query("UPDATE routine_day SET routine_id = :routineId, name = :name, sort_order = :sortOrder WHERE id = :id")
    fun updateDay(id: Long, routineId: Long, name: String, sortOrder: Int)

    @Query("DELETE FROM routine_day WHERE id = :id")
    fun deleteDay(id: Long)

    @Query("SELECT id FROM exercise")
    fun exerciseIds(): List<Long>

    @Insert
    fun addItem(row: RoutineDayExerciseRow): Long

    @Insert
    fun addPlannedSet(row: RoutineDaySetRow): Long

    @Query("DELETE FROM routine_day_set WHERE item_id IN (SELECT id FROM routine_day_exercise WHERE day_id = :dayId)")
    fun deletePlannedSetsOfDay(dayId: Long)

    @Query("DELETE FROM routine_day_exercise WHERE day_id = :dayId")
    fun deleteItemsOfDay(dayId: Long)

    @Query("DELETE FROM routine_day_set WHERE item_id IN (SELECT id FROM routine_day_exercise WHERE exercise_id = :exerciseId)")
    fun deletePlannedSetsOfExercise(exerciseId: Long)

    @Query("DELETE FROM routine_day_exercise WHERE exercise_id = :exerciseId")
    fun deleteItemsOfExercise(exerciseId: Long)
}

// ---- Rows to the app's models -----------------------------------------------------------------------------------

internal fun CategoryRow.toModel() = Category(id, name, colour.toInt(), sort_order, source)

internal fun ExerciseRow.toModel() = Exercise(
    id, name, category_id, type, notes, source, favourite != 0, weight_step, default_graph, rest_seconds,
    DistanceUnits.of(distance_unit), WeightUnits.of(weight_unit)
)

internal fun ExerciseTypeRow.toModel() = CustomType(
    id.toInt(), name, uses_weight != 0, uses_reps != 0, uses_distance != 0, uses_time != 0,
    metric_name?.takeIf { it.isNotBlank() }, metric_unit?.takeIf { it.isNotBlank() }
)

internal fun ExerciseGoalRow.toModel() = ExerciseGoal(id, exercise_id, kind, target, sort_order)

internal fun WorkoutSetRow.toModel() = SetRow(
    id, exercise_id, date, weight, reps, distance, duration.toInt(), is_pr != 0, comment, source, set_type, rpe,
    position, superset, done != 0, rest_seconds, metric
)

internal fun RoutineDaySetRow.toModel() =
    PlannedSet(weight, reps, distance, duration.toInt(), set_type, rest_seconds, metric)

internal fun MeasurementRow.toModel() =
    MeasurementDef(name, unit, sort_order, goal_type, goal_value, enabled != 0, custom != 0, link, display_unit)

internal fun MeasurementRecordRow.toModel() = MRecord(id, name, unit, date, time, value, comment, source)

internal fun PhotoRow.toModel() = Photo(id, file, date, taken_at, date_source, pose, note, original_name)
