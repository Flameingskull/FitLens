package com.fitlens.companion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Typed, compile-checked queries on `fitlens.db` (#36). Room checks every query here against `Schema.kt` when the
 * app is built, so a misspelt column or table fails the build instead of a screen. Reach them through [Db] (its
 * properties open the database first, so the downgrade check of #77 always runs before Room's own).
 *
 * Every DAO call runs off the main thread: Room refuses a query on the main thread, and every caller already runs on
 * `Dispatchers.IO`. The rest of the app still writes SQL through `Db.writableDatabase`, and moves here area by area.
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

/** Progress photo edits (`Store`). Callers pass at most [Db.MAX_IDS] ids at a time. */
@Dao
interface PhotoDao {
    @Query("UPDATE photo SET date = :date, date_source = :dateSource WHERE id IN (:ids)")
    fun setDate(ids: List<Long>, date: String?, dateSource: String)

    /** Accepts the detected date of each photo that has one. */
    @Query("UPDATE photo SET date_source = :dateSource WHERE id IN (:ids) AND date IS NOT NULL")
    fun confirmDates(ids: List<Long>, dateSource: String)

    @Query("UPDATE photo SET pose = :pose WHERE id IN (:ids)")
    fun setPose(ids: List<Long>, pose: String)

    @Query("UPDATE photo SET note = :note WHERE id = :id")
    fun setNote(id: Long, note: String)

    @Query("SELECT file FROM photo WHERE id IN (:ids)")
    fun files(ids: List<Long>): List<String>

    @Query("DELETE FROM photo WHERE id IN (:ids)")
    fun delete(ids: List<Long>)
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
