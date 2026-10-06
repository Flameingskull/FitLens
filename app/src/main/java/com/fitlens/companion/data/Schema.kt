package com.fitlens.companion.data

import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

/**
 * The database as Room sees it (#36). Room opens `fitlens.db`, runs the upgrades in [Db] and, after every upgrade,
 * checks that each table below matches what it finds: the same columns, types, NOT NULLs, defaults, keys and
 * indices. Room also creates these tables on a fresh install, so the defaults here are the ones a new row gets.
 *
 * Column names are the property names, exactly as the SQL in the rest of the app uses them. Every column the app has
 * ever had is here: the v21 rebuild ([Db.reconcile]) refuses to drop a column it doesn't know, so a column left out
 * of this file stops the upgrade instead of losing data. Adding a column means a new [Db.VERSION] and an upgrade
 * step, the same as before.
 */
@Database(
    version = Db.VERSION,
    exportSchema = false,
    entities = [
        CategoryRow::class, ExerciseRow::class, WorkoutSetRow::class, MeasurementRow::class, MeasurementRecordRow::class,
        WorkoutCommentRow::class, ExerciseCommentRow::class, WorkoutRestRow::class, WorkoutTimeRow::class,
        ImportRuleRow::class, ExerciseGoalRow::class, ExerciseTypeRow::class,
        SavedWorkoutRow::class, SavedWorkoutExerciseRow::class, SavedWorkoutSetRow::class,
        RoutineRow::class, RoutineDayRow::class, WorkoutOriginRow::class, RoutineDayExerciseRow::class, RoutineDaySetRow::class,
        PhotoRow::class, MetaRow::class
    ]
)
abstract class FitLensDatabase : RoomDatabase()

// ---- Workout data. `source` is 'fitnotes' (imported) or 'fitlens' (made or edited in FitLens) -------------------

@Entity(tableName = "category")
data class CategoryRow(
    @PrimaryKey val id: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0") val colour: Long,
    @ColumnInfo(defaultValue = "0") val sort_order: Int,
    @ColumnInfo(defaultValue = "'fitlens'") val source: String,
    val fitnotes_id: Long?
)

@Entity(tableName = "exercise")
data class ExerciseRow(
    @PrimaryKey val id: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0") val category_id: Long,
    @ColumnInfo(defaultValue = "0") val type: Int,
    val notes: String?,
    @ColumnInfo(defaultValue = "0") val favourite: Int,
    @ColumnInfo(defaultValue = "'fitlens'") val source: String,
    val fitnotes_id: Long?,
    val weight_step: Double?,
    @ColumnInfo(defaultValue = "-1") val default_graph: Int,
    val rest_seconds: Int?,
    val distance_unit: String?,
    val weight_unit: String?
)

@Entity(
    tableName = "workout_set",
    indices = [Index(value = ["date"], name = "idx_set_date"), Index(value = ["exercise_id"], name = "idx_set_ex")]
)
data class WorkoutSetRow(
    @PrimaryKey val id: Long,
    val exercise_id: Long,
    val date: String,
    @ColumnInfo(defaultValue = "0") val weight: Double,
    @ColumnInfo(defaultValue = "0") val reps: Int,
    @ColumnInfo(defaultValue = "0") val distance: Double,
    @ColumnInfo(defaultValue = "0") val duration: Long,
    @ColumnInfo(defaultValue = "0") val is_pr: Int,
    val comment: String?,
    @ColumnInfo(defaultValue = "'fitlens'") val source: String,
    val fitnotes_id: Long?,
    @ColumnInfo(defaultValue = "0") val set_type: Int,
    val rpe: Double?,
    @ColumnInfo(defaultValue = "0") val position: Long,
    @ColumnInfo(defaultValue = "0") val superset: Int,
    @ColumnInfo(defaultValue = "0") val done: Int,
    val rest_seconds: Int?,
    val metric: Double?
)

@Entity(tableName = "workout_comment")
data class WorkoutCommentRow(
    @PrimaryKey val id: Long,
    val date: String,
    val comment: String,
    @ColumnInfo(defaultValue = "'fitlens'") val source: String
)

/** One note per exercise within a date's workout (#107). */
@Entity(
    tableName = "exercise_comment",
    indices = [Index(value = ["date", "exercise_id"], name = "idx_exercise_comment_day", unique = true)]
)
data class ExerciseCommentRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val date: String,
    val exercise_id: Long,
    val comment: String,
    @ColumnInfo(defaultValue = "'fitlens'") val source: String
)

/** Prescribed rest on a logged date (#138). */
@Entity(tableName = "workout_rest", primaryKeys = ["date", "exercise_id"])
data class WorkoutRestRow(
    val date: String,
    val exercise_id: Long,
    val rest_seconds: Int?,
    val rest_after_seconds: Int?
)

@Entity(tableName = "workout_time")
data class WorkoutTimeRow(
    @PrimaryKey val id: Long,
    val date: String,
    val start: String?,
    val finish: String?,
    @ColumnInfo(defaultValue = "'fitlens'") val source: String
)

/** What the user did in FitLens to imported data, so a later FitNotes import respects it (#6). */
@Entity(tableName = "import_rule")
data class ImportRuleRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val kind: String,
    val key: String,
    val target_id: Long?
)

/** Exercise goals (#25). */
@Entity(tableName = "exercise_goal")
data class ExerciseGoalRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val exercise_id: Long,
    val kind: Int,
    val target: Double,
    @ColumnInfo(defaultValue = "0") val sort_order: Int
)

/** User-defined exercise types (#14), with ids from [ExerciseTypes.CUSTOM_BASE]. */
@Entity(tableName = "exercise_type")
data class ExerciseTypeRow(
    @PrimaryKey val id: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0") val uses_weight: Int,
    @ColumnInfo(defaultValue = "0") val uses_reps: Int,
    @ColumnInfo(defaultValue = "0") val uses_distance: Int,
    @ColumnInfo(defaultValue = "0") val uses_time: Int,
    val metric_name: String?,
    val metric_unit: String?
)

// ---- Saved workouts of v7–v12 (#100): empty since v13, kept so an older FitLens can still open the file (#77) ------

@Entity(tableName = "saved_workout")
data class SavedWorkoutRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val notes: String?,
    @ColumnInfo(defaultValue = "0") val sort_order: Int
)

@Entity(tableName = "saved_workout_exercise")
data class SavedWorkoutExerciseRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val workout_id: Long,
    val exercise_id: Long,
    @ColumnInfo(defaultValue = "0") val sort_order: Int,
    @ColumnInfo(defaultValue = "0") val fill: Int,
    @ColumnInfo(defaultValue = "0") val superset: Int
)

@Entity(tableName = "saved_workout_set")
data class SavedWorkoutSetRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val item_id: Long,
    @ColumnInfo(defaultValue = "0") val sort_order: Int,
    @ColumnInfo(defaultValue = "0") val weight: Double,
    @ColumnInfo(defaultValue = "0") val reps: Int,
    @ColumnInfo(defaultValue = "0") val distance: Double,
    @ColumnInfo(defaultValue = "0") val duration: Long,
    @ColumnInfo(defaultValue = "0") val set_type: Int
)

// ---- Workouts and routines (#21, #106) ------------------------------------------------------------------------------

@Entity(tableName = "routine")
data class RoutineRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val notes: String?,
    @ColumnInfo(defaultValue = "0") val sort_order: Int
)

@Entity(tableName = "routine_day")
data class RoutineDayRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val routine_id: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0") val workout_id: Long,
    @ColumnInfo(defaultValue = "0") val sort_order: Int
)

@Entity(tableName = "workout_origin")
data class WorkoutOriginRow(
    @PrimaryKey val date: String,
    val workout_id: Long,
    val routine_day_id: Long?
)

@Entity(tableName = "routine_day_exercise")
data class RoutineDayExerciseRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val day_id: Long,
    val exercise_id: Long,
    @ColumnInfo(defaultValue = "0") val sort_order: Int,
    @ColumnInfo(defaultValue = "0") val fill: Int,
    @ColumnInfo(defaultValue = "0") val superset: Int,
    val rest_seconds: Int?,
    val rest_after_seconds: Int?
)

@Entity(tableName = "routine_day_set")
data class RoutineDaySetRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val item_id: Long,
    @ColumnInfo(defaultValue = "0") val sort_order: Int,
    @ColumnInfo(defaultValue = "0") val weight: Double,
    @ColumnInfo(defaultValue = "0") val reps: Int,
    @ColumnInfo(defaultValue = "0") val distance: Double,
    @ColumnInfo(defaultValue = "0") val duration: Long,
    @ColumnInfo(defaultValue = "0") val set_type: Int,
    val rest_seconds: Int?,
    val metric: Double?
)

// ---- Body ---------------------------------------------------------------------------------------------------------

@Entity(tableName = "measurement")
data class MeasurementRow(
    @PrimaryKey val name: String,
    @ColumnInfo(defaultValue = "''") val unit: String,
    @ColumnInfo(defaultValue = "999") val sort_order: Int,
    @ColumnInfo(defaultValue = "0") val goal_type: Int,
    @ColumnInfo(defaultValue = "0") val goal_value: Double,
    @ColumnInfo(defaultValue = "1") val enabled: Int,
    @ColumnInfo(defaultValue = "0") val custom: Int,
    val link: String?,
    @ColumnInfo(defaultValue = "0") val edited: Int,
    val display_unit: String?
)

@Entity(tableName = "mrecord", indices = [Index(value = ["date"], name = "idx_mr_date")])
data class MeasurementRecordRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    @ColumnInfo(defaultValue = "''") val unit: String,
    val date: String,
    @ColumnInfo(defaultValue = "''") val time: String,
    val value: Double,
    val comment: String?,
    val source: String
)

// ---- Photos and settings --------------------------------------------------------------------------------------------

@Entity(
    tableName = "photo",
    indices = [Index(value = ["date"], name = "idx_photo_date"), Index(value = ["hash"], name = "idx_photo_hash", unique = true)]
)
data class PhotoRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val file: String,
    val date: String?,
    val taken_at: String?,
    val date_source: String,
    @ColumnInfo(defaultValue = "''") val pose: String,
    val note: String?,
    val original_name: String?,
    val hash: String?,
    @ColumnInfo(defaultValue = "0") val added_at: Long
)

@Entity(tableName = "meta")
data class MetaRow(
    @PrimaryKey val k: String,
    val v: String?
)
