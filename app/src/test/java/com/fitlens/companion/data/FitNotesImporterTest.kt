package com.fitlens.companion.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The FitNotes import (#40): a small synthetic FitNotes backup, built here, merged into a fresh FlexNotes database with
 * [FitNotesImporter.merge]. Imports only add: they never delete, edit or overwrite FlexNotes data, a second import of
 * the same backup adds nothing, and the renames and deletions made in FlexNotes are respected (conflict rules in
 * `Workouts`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FitNotesImporterTest {

    private lateinit var app: Application
    private lateinit var backup: File
    private lateinit var flexnotes: Db

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase(Db.NAME)
        flexnotes = Db(app)
        backup = File(app.cacheDir, "test.fitnotes")
        OldSchemas.create(backup, 1, fitNotesSchema) { fillBackup(it) }
    }

    @After
    fun tearDown() {
        flexnotes.close()
        app.deleteDatabase(Db.NAME)
        backup.delete()
    }

    /** The FitNotes tables the importer reads. */
    private val fitNotesSchema = listOf(
        "CREATE TABLE Category(_id INTEGER PRIMARY KEY, name TEXT, colour INTEGER, sort_order INTEGER)",
        "CREATE TABLE exercise(_id INTEGER PRIMARY KEY, name TEXT, category_id INTEGER, exercise_type_id INTEGER, notes TEXT)",
        "CREATE TABLE training_log(_id INTEGER PRIMARY KEY, exercise_id INTEGER, date TEXT, metric_weight REAL, reps INTEGER, " +
            "distance REAL, duration_seconds INTEGER, is_personal_record INTEGER)",
        "CREATE TABLE Comment(_id INTEGER PRIMARY KEY, owner_type_id INTEGER, owner_id INTEGER, comment TEXT)",
        "CREATE TABLE WorkoutComment(_id INTEGER PRIMARY KEY, date TEXT, comment TEXT)",
        "CREATE TABLE WorkoutTime(_id INTEGER PRIMARY KEY, workout_date TEXT, start_date_time TEXT, end_date_time TEXT)"
    )

    /** Chest: Bench Press 3 × 5 × 100 kg (one set with a comment); Legs: Squat 1 × 5 × 140 kg; a comment and a time. */
    private fun fillBackup(db: SQLiteDatabase) {
        db.row("Category", "_id" to 1L, "name" to "Chest", "colour" to 0, "sort_order" to 1)
        db.row("Category", "_id" to 2L, "name" to "Legs", "colour" to 0, "sort_order" to 2)
        db.row("exercise", "_id" to 10L, "name" to "Bench Press", "category_id" to 1L, "exercise_type_id" to 0)
        db.row("exercise", "_id" to 11L, "name" to "Squat", "category_id" to 2L, "exercise_type_id" to 0)
        for (i in 1..3) {
            db.row(
                "training_log", "_id" to (100L + i), "exercise_id" to 10L, "date" to "2026-01-10", "metric_weight" to 100.0,
                "reps" to 5, "distance" to 0.0, "duration_seconds" to 0, "is_personal_record" to 0
            )
        }
        db.row(
            "training_log", "_id" to 200L, "exercise_id" to 11L, "date" to "2026-01-10", "metric_weight" to 140.0,
            "reps" to 5, "distance" to 0.0, "duration_seconds" to 0, "is_personal_record" to 1
        )
        db.row("Comment", "_id" to 1L, "owner_type_id" to 1, "owner_id" to 101L, "comment" to "Paused reps")
        db.row("WorkoutComment", "_id" to 1L, "date" to "2026-01-10", "comment" to "Great session")
        db.row("WorkoutTime", "_id" to 1L, "workout_date" to "2026-01-10", "start_date_time" to "2026-01-10 17:00:00", "end_date_time" to "2026-01-10 18:15:00")
    }

    /**
     * Merges the backup in one transaction, as the app does ([apply] = false is the dry run behind the summary).
     * Off the main thread, because Room refuses queries on it and Robolectric runs tests there.
     */
    private fun import(apply: Boolean = true): ImportPlan =
        runBlocking(Dispatchers.IO) { FitNotesImporter.runMerge(backup, apply, flexnotes) }

    private val db: SupportSQLiteDatabase get() = flexnotes.writableDatabase

    @Test
    fun importIntoAnEmptyFlexNotesAddsEverything() {
        val plan = import()
        assertEquals(2, plan.categoriesAdded)
        assertEquals(2, plan.exercisesAdded)
        assertEquals(4, plan.setsAdded)
        assertEquals(1, plan.commentsAdded)
        assertEquals(1, plan.timesAdded)
        assertEquals(4, db.count("SELECT COUNT(*) FROM workout_set WHERE source='fitnotes'"))
        assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set WHERE comment='Paused reps'"))
        assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set WHERE weight=140 AND is_pr=1"))
        assertEquals(1, db.count("SELECT COUNT(*) FROM exercise WHERE name='Squat' AND fitnotes_id=11 AND source='fitnotes'"))
    }

    @Test
    fun importingTheSameBackupTwiceAddsNothing() {
        import()
        val again = import()
        assertTrue(again.nothingNew)
        assertEquals(4, again.setsSkipped)
        assertEquals(4, db.count("SELECT COUNT(*) FROM workout_set"))
        assertEquals(2, db.count("SELECT COUNT(*) FROM exercise"))
        assertEquals(1, db.count("SELECT COUNT(*) FROM workout_comment"))
        assertEquals(1, db.count("SELECT COUNT(*) FROM workout_time"))
    }

    @Test
    fun flexNotesDataIsKeptAndMatchedNotOverwritten() {
        // FlexNotes already has its own Bench Press (different case), one identical set and a comment on that day.
        val bench = db.row("exercise", "name" to "bench press", "category_id" to 0L, "source" to Sources.FLEXNOTES, "notes" to "Mine")
        db.row("workout_set", "exercise_id" to bench, "date" to "2026-01-10", "weight" to 100.0, "reps" to 5, "source" to Sources.FLEXNOTES)
        db.row("workout_comment", "date" to "2026-01-10", "comment" to "Logged in FlexNotes", "source" to Sources.FLEXNOTES)

        val plan = import()
        // Matched by name, so no second Bench Press, and the FlexNotes exercise is left exactly as it was.
        assertEquals(1, plan.exercisesAdded)
        assertEquals(1, db.count("SELECT COUNT(*) FROM exercise WHERE lower(name)='bench press'"))
        assertEquals(1, db.count("SELECT COUNT(*) FROM exercise WHERE id=? AND name='bench press' AND notes='Mine' AND source='fitlens'", bench.toString()))
        // Identical sets are counted: one of the three is already there, so two are added and the FlexNotes one stays.
        assertEquals(3, db.count("SELECT COUNT(*) FROM workout_set WHERE exercise_id=? AND weight=100 AND reps=5", bench.toString()))
        assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set WHERE exercise_id=? AND source='fitlens'", bench.toString()))
        // The FlexNotes comment is still there; the FitNotes one is added beside it, never over it.
        assertEquals(1, db.count("SELECT COUNT(*) FROM workout_comment WHERE comment='Logged in FlexNotes' AND source='fitlens'"))
        assertEquals(2, db.count("SELECT COUNT(*) FROM workout_comment WHERE date='2026-01-10'"))
    }

    @Test
    fun renamesAndDeletionsMadeInFlexNotesAreRespected() {
        // The user renamed Bench Press to "Barbell Bench" and deleted Squat, both in FlexNotes.
        val barbell = db.row("exercise", "name" to "Barbell Bench", "category_id" to 0L, "source" to Sources.FLEXNOTES)
        db.row("import_rule", "kind" to Workouts.RULE_EXERCISE, "key" to Workouts.nameKey("Bench Press"), "target_id" to barbell)
        db.row("import_rule", "kind" to Workouts.RULE_EXERCISE, "key" to Workouts.nameKey("Squat"), "target_id" to null)

        val plan = import()
        assertEquals(0, plan.exercisesAdded)
        assertEquals(1, plan.exercisesSkipped)
        assertEquals(3, db.count("SELECT COUNT(*) FROM workout_set WHERE exercise_id=?", barbell.toString()))
        assertEquals(0, db.count("SELECT COUNT(*) FROM exercise WHERE name IN ('Bench Press', 'Squat')"))
        assertEquals(0, db.count("SELECT COUNT(*) FROM workout_set WHERE weight=140"))
    }

    @Test
    fun anImportNeverDeletesFlexNotesRows() {
        val mine = db.row("exercise", "name" to "Face Pull", "category_id" to 0L, "source" to Sources.FLEXNOTES)
        db.row("workout_set", "exercise_id" to mine, "date" to "2026-01-11", "weight" to 20.0, "reps" to 15, "source" to Sources.FLEXNOTES)
        db.row("mrecord", "name" to "Bodyweight", "unit" to "kg", "date" to "2026-01-11", "value" to 81.0, "source" to Sources.FLEXNOTES)
        import()
        import()
        assertEquals(1, db.count("SELECT COUNT(*) FROM exercise WHERE name='Face Pull'"))
        assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set WHERE exercise_id=? AND source='fitlens'", mine.toString()))
        assertEquals(1, db.count("SELECT COUNT(*) FROM mrecord WHERE value=81 AND source='fitlens'"))
    }

    @Test
    fun theDryRunWorksOutThePlanButKeepsNothing() {
        val plan = import(apply = false)
        assertEquals(4, plan.setsAdded)
        assertEquals(2, plan.exercisesAdded)
        assertEquals(0, db.count("SELECT COUNT(*) FROM workout_set"))
        assertEquals(0, db.count("SELECT COUNT(*) FROM exercise"))
        assertEquals(0, db.count("SELECT COUNT(*) FROM workout_comment"))
        // The real import afterwards finds the same plan.
        assertEquals(4, import().setsAdded)
    }

    /** The FitNotes body tables: Bodyweight in kg (two values) and Waist in cm (one). */
    private fun withMeasurements() {
        backup.delete()
        val schema = fitNotesSchema + listOf(
            "CREATE TABLE MeasurementUnit(_id INTEGER PRIMARY KEY, short_name TEXT)",
            "CREATE TABLE Measurement(_id INTEGER PRIMARY KEY, name TEXT, unit_id INTEGER, sort_order INTEGER, " +
                "goal_type INTEGER, goal_value REAL, enabled INTEGER)",
            "CREATE TABLE MeasurementRecord(_id INTEGER PRIMARY KEY, measurement_id INTEGER, date TEXT, time TEXT, " +
                "value REAL, comment TEXT)"
        )
        OldSchemas.create(backup, 1, schema) { b ->
            fillBackup(b)
            b.row("MeasurementUnit", "_id" to 1L, "short_name" to "kg")
            b.row("MeasurementUnit", "_id" to 2L, "short_name" to "cm")
            b.row("Measurement", "_id" to 1L, "name" to "Bodyweight", "unit_id" to 1L, "sort_order" to 1, "goal_type" to 0, "goal_value" to 0.0, "enabled" to 1)
            b.row("Measurement", "_id" to 2L, "name" to "Waist", "unit_id" to 2L, "sort_order" to 2, "goal_type" to 2, "goal_value" to 80.0, "enabled" to 1)
            b.row("MeasurementRecord", "measurement_id" to 1L, "date" to "2026-01-10", "time" to "07:00:00", "value" to 82.0)
            b.row("MeasurementRecord", "measurement_id" to 1L, "date" to "2026-01-11", "time" to "07:00:00", "value" to 81.5)
            b.row("MeasurementRecord", "measurement_id" to 2L, "date" to "2026-01-10", "time" to "07:00:00", "value" to 84.0)
        }
    }

    @Test
    fun bodyValuesAreAddedOnceAndUserEditsToADefinitionWin() {
        withMeasurements()
        // The user reordered Waist and set its goal in FlexNotes, and logged the same Bodyweight value by hand.
        db.execSQL("DELETE FROM measurement WHERE name='Waist'")
        db.row("measurement", "name" to "Waist", "unit" to "in", "sort_order" to 7, "goal_type" to 1, "goal_value" to 30.0, "edited" to 1)
        db.row("mrecord", "name" to "Bodyweight", "unit" to "kg", "date" to "2026-01-11", "time" to "12:00:00", "value" to 81.5, "source" to "manual")

        val plan = import()
        assertEquals(2, plan.recordsAdded)
        assertEquals(1, plan.recordsSkipped)
        assertEquals(2, db.count("SELECT COUNT(*) FROM mrecord WHERE name='Bodyweight'"))
        // The unit follows FitNotes; the order and goal set in FlexNotes are kept.
        assertEquals(1, db.count("SELECT COUNT(*) FROM measurement WHERE name='Waist' AND unit='cm' AND sort_order=7 AND goal_type=1 AND goal_value=30"))

        val again = import()
        assertEquals(0, again.recordsAdded)
        assertEquals(3, db.count("SELECT COUNT(*) FROM mrecord"))
    }

    @Test
    fun valuesForACustomMetricGoIntoIt() {
        withMeasurements()
        db.row("measurement", "name" to "Weight", "unit" to "", "custom" to 1, "link" to "bodyweight", "edited" to 1)
        import()
        assertEquals(2, db.count("SELECT COUNT(*) FROM mrecord WHERE name='Weight' AND unit='kg'"))
        assertEquals(1, db.count("SELECT COUNT(*) FROM measurement WHERE name='Weight' AND unit='kg' AND custom=1"))
    }

    @Test
    fun aBodyTrackerCsvAddsEachValueOnce() {
        val lines = listOf(
            "Date,Time,Measurement,Value,Unit,Comment",
            "2026-01-10,07:00:00,Bodyweight,82.0,kg,",
            "2026-01-11,07:00:00,Bodyweight,81.5,kg,\"Light, after cardio\"",
            "2026-01-11,07:00:00,Chest,not a number,cm,",
            ""
        )
        val first = runBlocking(Dispatchers.IO) { FitNotesImporter.mergeBodyCsv(lines, flexnotes) }
        assertEquals(2 to 0, first)
        assertEquals(1, db.count("SELECT COUNT(*) FROM mrecord WHERE comment='Light, after cardio' AND source='csv'"))
        assertEquals(1, db.count("SELECT COUNT(*) FROM measurement WHERE name='Bodyweight'"))
        val again = runBlocking(Dispatchers.IO) { FitNotesImporter.mergeBodyCsv(lines, flexnotes) }
        assertEquals(0 to 2, again)
        assertEquals(2, db.count("SELECT COUNT(*) FROM mrecord WHERE name='Bodyweight'"))
    }
}
