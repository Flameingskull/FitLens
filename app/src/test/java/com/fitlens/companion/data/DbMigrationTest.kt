package com.fitlens.companion.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Database migrations (#40, first slice): build a database as an older FitLens left it, open it with today's [Db],
 * and check that every row survives the upgrade. Runs on the JVM with Robolectric's real SQLite, in CI before every
 * release build, so a migration that loses data stops the release. The fixtures are synthetic.
 *
 * A plain [Application] stands in for FitLens's own, so the test doesn't start the store, settings or backups.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DbMigrationTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase(Db.NAME)
    }

    @After
    fun tearDown() {
        app.deleteDatabase(Db.NAME)
    }

    /** The v12 tables the v13 and v14 steps read, exactly as 1.0.44–1.0.49 created them. */
    private val v12Schema = listOf(
        "CREATE TABLE exercise(id INTEGER PRIMARY KEY, name TEXT NOT NULL, category_id INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE workout_set(id INTEGER PRIMARY KEY, exercise_id INTEGER NOT NULL, date TEXT NOT NULL, " +
            "weight REAL NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE saved_workout(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, notes TEXT, " +
            "sort_order INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE saved_workout_exercise(id INTEGER PRIMARY KEY AUTOINCREMENT, workout_id INTEGER NOT NULL, " +
            "exercise_id INTEGER NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, fill INTEGER NOT NULL DEFAULT 0, " +
            "superset INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE saved_workout_set(id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL, " +
            "sort_order INTEGER NOT NULL DEFAULT 0, weight REAL NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, " +
            "distance REAL NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, set_type INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE routine(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, notes TEXT, " +
            "sort_order INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE routine_day(id INTEGER PRIMARY KEY AUTOINCREMENT, routine_id INTEGER NOT NULL, " +
            "name TEXT NOT NULL, workout_id INTEGER NOT NULL DEFAULT 0, sort_order INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE workout_origin(date TEXT PRIMARY KEY, workout_id INTEGER NOT NULL, routine_day_id INTEGER)"
    )

    /** Writes a database at [version] with [schema] and whatever [fill] inserts, the way an older build left it. */
    private fun oldDatabase(version: Int, schema: List<String>, fill: (SQLiteDatabase) -> Unit) =
        OldSchemas.create(app.getDatabasePath(Db.NAME), version, schema, fill)

    /** A v12 database: a routine whose two days share one saved workout, plus a saved workout no day uses. */
    private fun v12WithSavedWorkouts() = oldDatabase(12, v12Schema) { db ->
        db.row("exercise", "id" to 1L, "name" to "Bench Press")
        db.row("exercise", "id" to 2L, "name" to "Squat")
        db.row("exercise", "id" to 3L, "name" to "Deadlift")
        val push = db.row("saved_workout", "name" to "Push", "sort_order" to 0)
        val pushBench = db.row("saved_workout_exercise", "workout_id" to push, "exercise_id" to 1L, "sort_order" to 0, "fill" to 1)
        db.row("saved_workout_set", "item_id" to pushBench, "sort_order" to 0, "weight" to 80.0, "reps" to 8)
        db.row("saved_workout_set", "item_id" to pushBench, "sort_order" to 1, "weight" to 85.0, "reps" to 6)
        db.row("saved_workout_exercise", "workout_id" to push, "exercise_id" to 2L, "sort_order" to 1, "fill" to 0, "superset" to 1)
        val legs = db.row("saved_workout", "name" to "Legs", "notes" to "Slow eccentrics", "sort_order" to 1)
        db.row("saved_workout_exercise", "workout_id" to legs, "exercise_id" to 3L, "sort_order" to 0)
        val ppl = db.row("routine", "name" to "PPL", "sort_order" to 0)
        val dayA = db.row("routine_day", "routine_id" to ppl, "name" to "Monday", "workout_id" to push, "sort_order" to 0)
        val dayB = db.row("routine_day", "routine_id" to ppl, "name" to "Thursday", "workout_id" to push, "sort_order" to 1)
        db.row("routine_day", "routine_id" to ppl, "name" to "Rest", "workout_id" to 0L, "sort_order" to 2)
        db.row("workout_origin", "date" to "2026-09-01", "workout_id" to push, "routine_day_id" to dayA)
        db.row("workout_origin", "date" to "2026-09-04", "workout_id" to push, "routine_day_id" to dayB)
        db.row("workout_origin", "date" to "2026-09-06", "workout_id" to legs, "routine_day_id" to null)
        db.row("workout_set", "exercise_id" to 1L, "date" to "2026-09-01", "weight" to 80.0, "reps" to 8)
    }

    /** The oldest histories: every row survives, and the v3 ownership rules (#6) are applied on the way up. */
    private fun assertUpgradedFromBeforeV3() {
        Db(app).writableDatabase.use { db ->
            assertEquals(Db.VERSION, db.version)
            assertEquals(3, db.count("SELECT COUNT(*) FROM workout_set"))
            // Rows that existed before v3 came from FitNotes: marked so, keeping their FitNotes id.
            assertEquals(3, db.count("SELECT COUNT(*) FROM workout_set WHERE source='fitnotes' AND fitnotes_id=id"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM exercise WHERE source='fitnotes' AND fitnotes_id=id AND name='Bench Press'"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM category WHERE source='fitnotes'"))
            // Comments and times were rebuilt with an id, keeping their text.
            assertTrue(db.hasColumn("workout_comment", "id"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM workout_comment WHERE comment='Felt strong' AND source='fitnotes'"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM workout_time WHERE start='2025-01-02 18:00:00' AND source='fitnotes'"))
            // A chosen sync folder keeps sync on; body data is untouched.
            assertEquals(1, db.count("SELECT COUNT(*) FROM meta WHERE k='auto_sync' AND v='1'"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM mrecord WHERE name='Bodyweight' AND value=82.5"))
            // Later columns arrived with safe defaults.
            assertEquals(3, db.count("SELECT COUNT(*) FROM workout_set WHERE set_type=0 AND done=0 AND superset=0 AND position=id"))
            listOf("custom", "link", "edited").forEach { assertTrue("measurement.$it", db.hasColumn("measurement", it)) }
            listOf("favourite", "weight_step", "default_graph", "rest_seconds").forEach { assertTrue("exercise.$it", db.hasColumn("exercise", it)) }
            listOf("import_rule", "exercise_goal", "routine", "routine_day_exercise", "exercise_comment").forEach {
                assertTrue("missing table $it", db.hasTable(it))
            }
        }
    }

    @Test
    fun v2HistoryUpgradesToCurrent() {
        oldDatabase(2, OldSchemas.V2) { OldSchemas.fillV2(it) }
        assertUpgradedFromBeforeV3()
    }

    @Test
    fun v1HistoryUpgradesToCurrent() {
        oldDatabase(1, OldSchemas.V1) { OldSchemas.fillV2(it) }
        assertUpgradedFromBeforeV3()
    }

    @Test
    fun freshInstallCreatesEveryTable() {
        Db(app).writableDatabase.use { db ->
            assertEquals(Db.VERSION, db.version)
            listOf(
                "workout_set", "exercise", "routine", "routine_day", "routine_day_exercise", "routine_day_set",
                "workout_origin", "exercise_comment", "saved_workout", "workout_rest"
            ).forEach { assertTrue("missing table $it", db.hasTable(it)) }
        }
    }

    @Test
    fun v12SavedWorkoutsBecomeWorkoutDays() {
        v12WithSavedWorkouts()
        Db(app).writableDatabase.use { db ->
            assertEquals(Db.VERSION, db.version)
            // Logged sets are untouched.
            assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set"))
            // The shared saved workout became two copies, one per day, each with its exercises and sets.
            assertEquals(2, db.count("SELECT COUNT(*) FROM routine_day_exercise e JOIN routine_day d ON d.id = e.day_id WHERE d.name='Monday'"))
            assertEquals(2, db.count("SELECT COUNT(*) FROM routine_day_exercise e JOIN routine_day d ON d.id = e.day_id WHERE d.name='Thursday'"))
            assertEquals(4, db.count("SELECT COUNT(*) FROM routine_day_set"))
            assertEquals(
                2,
                db.count(
                    "SELECT COUNT(*) FROM routine_day_set s JOIN routine_day_exercise e ON e.id = s.item_id " +
                        "JOIN routine_day d ON d.id = e.day_id WHERE d.name='Monday' AND e.exercise_id=1"
                )
            )
            // Fill modes and supersets come across.
            assertEquals(1, db.count("SELECT COUNT(*) FROM routine_day_exercise WHERE exercise_id=1 AND fill=1 AND day_id=(SELECT id FROM routine_day WHERE name='Monday')"))
            assertEquals(2, db.count("SELECT COUNT(*) FROM routine_day_exercise WHERE exercise_id=2 AND superset=1"))
            // The day with no saved workout stays, empty.
            assertEquals(0, db.count("SELECT COUNT(*) FROM routine_day_exercise WHERE day_id=(SELECT id FROM routine_day WHERE name='Rest')"))
            // The unused saved workout became a one-day workout with its name and notes.
            assertEquals(1, db.count("SELECT COUNT(*) FROM routine WHERE name='Legs' AND notes='Slow eccentrics'"))
            assertEquals(
                1,
                db.count(
                    "SELECT COUNT(*) FROM routine_day_exercise e JOIN routine_day d ON d.id = e.day_id " +
                        "JOIN routine r ON r.id = d.routine_id WHERE r.name='Legs' AND e.exercise_id=3"
                )
            )
            // Logged dates point at their workout and day.
            assertEquals(
                1,
                db.count(
                    "SELECT COUNT(*) FROM workout_origin o JOIN routine_day d ON d.id = o.routine_day_id " +
                        "JOIN routine r ON r.id = o.workout_id WHERE o.date='2026-09-04' AND d.name='Thursday' AND r.name='PPL'"
                )
            )
            assertEquals(
                1,
                db.count(
                    "SELECT COUNT(*) FROM workout_origin o JOIN routine r ON r.id = o.workout_id " +
                        "WHERE o.date='2026-09-06' AND r.name='Legs' AND o.routine_day_id IS NOT NULL"
                )
            )
            // The old tables are kept for older builds (#77), but emptied, and days no longer point at them.
            assertTrue(db.hasTable("saved_workout"))
            assertEquals(0, db.count("SELECT COUNT(*) FROM saved_workout"))
            assertEquals(0, db.count("SELECT COUNT(*) FROM routine_day WHERE workout_id <> 0"))
            // v14: exercise comments.
            assertTrue(db.hasTable("exercise_comment"))
        }
    }

    @Test
    fun v13StepReplaysWithoutDuplicating() {
        v12WithSavedWorkouts()
        Db(app).writableDatabase.use { db ->
            val routines = db.count("SELECT COUNT(*) FROM routine")
            val items = db.count("SELECT COUNT(*) FROM routine_day_exercise")
            val sets = db.count("SELECT COUNT(*) FROM routine_day_set")
            // A downgrade stamps the version back and the step runs again (#77): nothing may double.
            Routines.migrateSavedWorkouts(db)
            assertEquals(routines, db.count("SELECT COUNT(*) FROM routine"))
            assertEquals(items, db.count("SELECT COUNT(*) FROM routine_day_exercise"))
            assertEquals(sets, db.count("SELECT COUNT(*) FROM routine_day_set"))
        }
    }

    @Test
    fun v13DatabaseGainsExerciseCommentsAndKeepsWorkouts() {
        oldDatabase(
            13,
            v12Schema + listOf(Routines.CREATE_EXERCISE, Routines.CREATE_SET)
        ) { db ->
            val r = db.row("routine", "name" to "Upper", "sort_order" to 0)
            val d = db.row("routine_day", "routine_id" to r, "name" to "Day 1", "sort_order" to 0)
            db.row("routine_day_exercise", "day_id" to d, "exercise_id" to 1L, "sort_order" to 0)
        }
        Db(app).writableDatabase.use { db ->
            assertEquals(Db.VERSION, db.version)
            assertEquals(1, db.count("SELECT COUNT(*) FROM routine_day_exercise"))
            db.row("exercise_comment", "date" to "2026-09-29", "exercise_id" to 1L, "comment" to "Left shoulder tight")
            assertEquals(1, db.count("SELECT COUNT(*) FROM exercise_comment WHERE source='fitlens'"))
        }
    }

    @Test
    fun v14ExercisesGainADistanceUnitAndKeepTheirRows() {
        oldDatabase(14, v12Schema + listOf(Routines.CREATE_EXERCISE, Routines.CREATE_SET, Db.CREATE_EXERCISE_COMMENT)) { db ->
            db.row("exercise", "id" to 7L, "name" to "Outdoor Run")
            db.row("workout_set", "exercise_id" to 7L, "date" to "2026-09-30", "weight" to 0.0, "reps" to 0)
        }
        Db(app).writableDatabase.use { db ->
            assertEquals(Db.VERSION, db.version)
            assertEquals(1, db.count("SELECT COUNT(*) FROM exercise WHERE id=7 AND distance_unit IS NULL"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set WHERE exercise_id=7"))
        }
    }

    @Test
    fun v15GainsWeightAndMeasurementUnitsAndKeepsRows() {
        oldDatabase(
            15,
            v12Schema + listOf(
                Routines.CREATE_EXERCISE, Routines.CREATE_SET, Db.CREATE_EXERCISE_COMMENT,
                "ALTER TABLE exercise ADD COLUMN distance_unit TEXT",
                "CREATE TABLE measurement(name TEXT PRIMARY KEY, unit TEXT NOT NULL DEFAULT '')"
            )
        ) { db ->
            db.row("exercise", "id" to 3L, "name" to "Bench Press", "distance_unit" to "km")
            db.row("workout_set", "exercise_id" to 3L, "date" to "2026-10-01", "weight" to 100.0, "reps" to 5)
            db.row("measurement", "name" to "Waist", "unit" to "cm")
        }
        Db(app).writableDatabase.use { db ->
            assertEquals(Db.VERSION, db.version)
            assertEquals(1, db.count("SELECT COUNT(*) FROM exercise WHERE id=3 AND distance_unit='km' AND weight_unit IS NULL"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set WHERE exercise_id=3 AND weight=100.0"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM measurement WHERE name='Waist' AND unit='cm' AND display_unit IS NULL"))
        }
    }

    @Test
    fun v16GainsPrescribedRestAndKeepsWorkouts() {
        // The workout-day tables exactly as v13 to v16 created them, before the rest columns (#138).
        oldDatabase(
            16,
            v12Schema + listOf(
                "CREATE TABLE routine_day_exercise(id INTEGER PRIMARY KEY AUTOINCREMENT, day_id INTEGER NOT NULL, " +
                    "exercise_id INTEGER NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, fill INTEGER NOT NULL DEFAULT 0, " +
                    "superset INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE routine_day_set(id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL, " +
                    "sort_order INTEGER NOT NULL DEFAULT 0, weight REAL NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, " +
                    "distance REAL NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, set_type INTEGER NOT NULL DEFAULT 0)",
                Db.CREATE_EXERCISE_COMMENT,
                "ALTER TABLE exercise ADD COLUMN distance_unit TEXT",
                "ALTER TABLE exercise ADD COLUMN weight_unit TEXT"
            )
        ) { db ->
            db.row("exercise", "id" to 1L, "name" to "Bench Press")
            val r = db.row("routine", "name" to "Upper", "sort_order" to 0)
            val d = db.row("routine_day", "routine_id" to r, "name" to "Push Day", "sort_order" to 0)
            val item = db.row("routine_day_exercise", "day_id" to d, "exercise_id" to 1L, "sort_order" to 0, "fill" to 1)
            db.row("routine_day_set", "item_id" to item, "sort_order" to 0, "weight" to 80.0, "reps" to 8)
            db.row("workout_set", "exercise_id" to 1L, "date" to "2026-10-02", "weight" to 80.0, "reps" to 8)
        }
        Db(app).writableDatabase.use { db ->
            assertEquals(Db.VERSION, db.version)
            assertEquals(1, db.count("SELECT COUNT(*) FROM routine_day_exercise WHERE rest_seconds IS NULL AND rest_after_seconds IS NULL"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM routine_day_set WHERE weight=80.0 AND rest_seconds IS NULL"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set WHERE weight=80.0 AND rest_seconds IS NULL"))
            assertTrue(db.hasTable("workout_rest"))
            db.row("workout_rest", "date" to "2026-10-02", "exercise_id" to 1L, "rest_seconds" to 90, "rest_after_seconds" to 120)
            assertEquals(1, db.count("SELECT COUNT(*) FROM workout_rest WHERE rest_after_seconds=120"))
        }
    }

    @Test
    fun v17GainsBodyFatAndHeightWithoutDoublingFitNotesBodyFat() {
        // FitNotes's "Body Fat" exists only as logged values; Waist is a measurement (#153).
        oldDatabase(
            17,
            v12Schema + listOf(
                "CREATE TABLE measurement(name TEXT PRIMARY KEY, unit TEXT NOT NULL DEFAULT '', sort_order INTEGER NOT NULL DEFAULT 999, " +
                    "goal_type INTEGER NOT NULL DEFAULT 0, goal_value REAL NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, " +
                    "custom INTEGER NOT NULL DEFAULT 0, link TEXT, edited INTEGER NOT NULL DEFAULT 0, display_unit TEXT)",
                "CREATE TABLE mrecord(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, unit TEXT NOT NULL DEFAULT '', " +
                    "date TEXT NOT NULL, time TEXT NOT NULL DEFAULT '', value REAL NOT NULL, comment TEXT, source TEXT NOT NULL)"
            )
        ) { db ->
            db.row("measurement", "name" to "Waist", "unit" to "cm", "sort_order" to 3)
            db.row("mrecord", "name" to "Body Fat", "unit" to "%", "date" to "2026-09-30", "value" to 18.5, "source" to "fitnotes")
        }
        Db(app).writableDatabase.use { db ->
            assertEquals(Db.VERSION, db.version)
            assertEquals(1, db.count("SELECT COUNT(*) FROM measurement WHERE name='Waist' AND sort_order=3"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM mrecord WHERE name='Body Fat' AND value=18.5"))
            assertEquals(0, db.count("SELECT COUNT(*) FROM measurement WHERE lower(name)='body fat'"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM measurement WHERE name='Height' AND unit='cm' AND enabled=1 AND custom=1 AND sort_order=4"))
        }
    }
}
