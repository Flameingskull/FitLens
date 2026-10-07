package com.fitlens.companion.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room takes over `fitlens.db` (#36). Opening a database through [Db] makes Room check every table against
 * `Schema.kt`, so each test here fails if the schema Room expects and the schema the app really has ever disagree:
 * on a fresh install, after the v21 rebuild of a full v20 database, when that rebuild replays, and when a newer
 * FlexNotes's file is opened by this one (#77). Synthetic data only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoomSchemaTest {

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

    private val file get() = app.getDatabasePath(Db.NAME)

    /** Room refuses queries on the main thread, which is the thread Robolectric runs tests on. */
    private fun <T> offMain(block: () -> T): T = runBlocking(Dispatchers.IO) { block() }

    /** A v20 database with a row in every table, as 1.0.112 left it. */
    private fun v20Database() = OldSchemas.create(file, 20, OldSchemas.V20) { db ->
        db.row("category", "id" to 1L, "name" to "Chest", "source" to "fitnotes", "fitnotes_id" to 1L)
        db.row("exercise", "id" to 1L, "name" to "Bench Press", "category_id" to 1L, "weight_step" to 2.5, "weight_unit" to "lb")
        db.row("workout_set", "id" to 1L, "exercise_id" to 1L, "date" to "2026-10-01", "weight" to 100.0, "reps" to 5, "rpe" to 8.5, "comment" to "Paused")
        db.row("workout_set", "id" to 2L, "exercise_id" to 1L, "date" to "2026-10-01", "weight" to 105.0, "reps" to 3)
        db.row("measurement", "name" to "Waist", "unit" to "cm", "sort_order" to 2, "goal_value" to 80.0)
        for (i in 1..5) db.row("mrecord", "name" to "Waist", "unit" to "cm", "date" to "2026-10-0$i", "value" to 90.0 - i, "source" to "manual")
        db.execSQL("DELETE FROM mrecord WHERE id = 5") // the counter stays at 5, so id 5 must never come back
        db.row("workout_comment", "date" to "2026-10-01", "comment" to "Good day")
        db.row("exercise_comment", "date" to "2026-10-01", "exercise_id" to 1L, "comment" to "Elbows in")
        db.row("workout_rest", "date" to "2026-10-01", "exercise_id" to 1L, "rest_seconds" to 120)
        db.row("workout_time", "date" to "2026-10-01", "start" to "2026-10-01 18:00:00")
        db.row("import_rule", "kind" to "exercise", "key" to "bench", "target_id" to 1L)
        db.row("exercise_goal", "exercise_id" to 1L, "kind" to 1, "target" to 120.0)
        db.row("exercise_type", "id" to 100L, "name" to "Jumps", "uses_reps" to 1)
        db.row("routine", "name" to "Upper")
        db.row("routine_day", "routine_id" to 1L, "name" to "Push Day")
        db.row("workout_origin", "date" to "2026-10-01", "workout_id" to 1L, "routine_day_id" to 1L)
        db.row("routine_day_exercise", "day_id" to 1L, "exercise_id" to 1L, "rest_seconds" to 90)
        db.row("routine_day_set", "item_id" to 1L, "weight" to 100.0, "reps" to 5, "metric" to 1.5)
        db.row("photo", "file" to "a.jpg", "date" to "2026-10-01", "date_source" to "exif", "hash" to "abc", "added_at" to 7L)
        db.row("meta", "k" to "weight_unit", "v" to "kg")
    }

    private fun columnsOf(db: SQLiteDatabase): Map<String, Set<String>> {
        val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite%' AND name <> 'android_metadata'", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return tables.associateWith { t ->
            db.rawQuery("PRAGMA table_info($t)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
        }
    }

    @Test
    fun freshInstallHasItsDefaultsTriggersAndBodyMeasurements() {
        Db(app).use { h ->
            val db = h.writableDatabase
            assertEquals(Db.VERSION, db.version)
            val ex = db.row("exercise", "name" to "Squat")
            val set = db.row("workout_set", "exercise_id" to ex, "date" to "2026-10-06", "weight" to 140.0, "reps" to 5)
            // Defaults and the position trigger work on Room's own tables.
            assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set WHERE id=$set AND position=$set AND source='fitlens' AND set_type=0"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM measurement WHERE name='Height' AND enabled=1"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='set_superset'"))
            offMain { h.setMeta("weight_unit", "lb") }
            assertEquals("lb", offMain { h.getMeta("weight_unit") })
            offMain { h.setMetas(mapOf("weight_unit" to null, "week_start" to "7")) }
            assertEquals(null, offMain { h.getMeta("weight_unit") })
            assertEquals("7", offMain { h.getMeta("week_start") })
        }
    }

    @Test
    fun aV20DatabaseKeepsEveryRowAndColumn() {
        v20Database()
        val before = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { columnsOf(it) }
        Db(app).use { h ->
            val db = h.writableDatabase
            assertEquals(Db.VERSION, db.version)
            before.forEach { (table, cols) -> cols.forEach { assertTrue("$table.$it kept", db.hasColumn(table, it)) } }
            assertEquals(2, db.count("SELECT COUNT(*) FROM workout_set WHERE exercise_id=1"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set WHERE id=1 AND rpe=8.5 AND comment='Paused' AND position=1"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM exercise WHERE weight_step=2.5 AND weight_unit='lb' AND default_graph=-1"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM category WHERE source='fitnotes' AND fitnotes_id=1"))
            assertEquals(4, db.count("SELECT COUNT(*) FROM mrecord"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM measurement WHERE name='Waist' AND goal_value=80.0"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM routine_day_set WHERE metric=1.5"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM workout_origin WHERE routine_day_id=1"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM photo WHERE hash='abc' AND added_at=7"))
            assertEquals("kg", offMain { h.getMeta("weight_unit") })
            listOf("workout_comment", "exercise_comment", "workout_rest", "workout_time", "import_rule", "exercise_goal", "exercise_type")
                .forEach { assertEquals("$it rows", 1, db.count("SELECT COUNT(*) FROM $it")) }
            // A deleted body value's id isn't reused.
            assertEquals(6L, db.row("mrecord", "name" to "Waist", "unit" to "cm", "date" to "2026-10-06", "value" to 84.0, "source" to "manual"))
            // The triggers came back, and the unique rules still hold as indices.
            val set = db.row("workout_set", "exercise_id" to 1L, "date" to "2026-10-01", "weight" to 110.0, "reps" to 1)
            assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set WHERE id=$set AND position=$set"))
            assertEquals(-1L, db.insert("photo", null, android.content.ContentValues().apply {
                put("file", "b.jpg"); put("date_source", "exif"); put("hash", "abc")
            }))
            assertEquals(-1L, db.insertWithOnConflict("exercise_comment", null, android.content.ContentValues().apply {
                put("date", "2026-10-01"); put("exercise_id", 1L); put("comment", "Again")
            }, SQLiteDatabase.CONFLICT_IGNORE))
        }
    }

    @Test
    fun theRebuildReplaysAfterAnOlderBuildStampsTheVersionBack() {
        v20Database()
        Db(app).use { it.writableDatabase }
        // An older FlexNotes opened the file and stamped it back to 20 (#77), then this build is installed again.
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 20 }
        Db(app).use { h ->
            val db = h.writableDatabase
            assertEquals(Db.VERSION, db.version)
            assertEquals(2, db.count("SELECT COUNT(*) FROM workout_set"))
            assertEquals(4, db.count("SELECT COUNT(*) FROM mrecord"))
            assertEquals(1, db.count("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='set_position'"))
        }
    }

    @Test
    fun aNewerFlexNotesFileOpensAndKeepsItsData() {
        Db(app).use { h -> h.writableDatabase.row("exercise", "id" to 1L, "name" to "Deadlift") }
        // A later FlexNotes added a column and a version, then this build was installed over it (#77).
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.execSQL("ALTER TABLE exercise ADD COLUMN future TEXT")
            raw.execSQL("UPDATE room_master_table SET identity_hash='from-a-later-build'")
            raw.version = Db.VERSION + 1
        }
        Db(app).use { h ->
            val db = h.writableDatabase
            assertEquals(Db.VERSION, db.version)
            assertEquals(1, db.count("SELECT COUNT(*) FROM exercise WHERE name='Deadlift'"))
            db.row("exercise", "name" to "Squat")
            assertEquals(2, db.count("SELECT COUNT(*) FROM exercise"))
        }
    }

    @Test
    fun theTypedQueriesReadEveryAreaOfAnUpgradedDatabase() {
        v20Database()
        Db(app).use { h ->
            offMain {
                val r = h.snapshotDao
                val sets = r.sets().map { it.toModel() }
                assertEquals(listOf(1L, 2L), sets.map { it.id })
                assertEquals(8.5, sets[0].rpe!!, 0.0)
                assertEquals("Paused", sets[0].comment)
                assertEquals(2, r.setsFor(listOf(1L), emptyList()).size)
                assertEquals(2, r.setsFor(emptyList(), listOf("2026-10-01")).size)
                assertEquals(0, r.setsFor(emptyList(), emptyList()).size)
                assertEquals("lbs", r.exercises().single().toModel().weightUnit)
                assertEquals("Chest", r.categories().single().toModel().name)
                assertEquals(120.0, r.goals().single().toModel().target, 0.0)
                assertEquals("Jumps", r.exerciseTypes().single().toModel().name)
                val routine = Routines.load(r).single()
                assertEquals("Push Day", routine.days.single().name)
                val planned = routine.days.single().exercises.single()
                assertEquals(90, planned.restSeconds)
                assertEquals(1.5, planned.sets.single().metric!!, 0.0)
                assertEquals(1L, Routines.loadOrigins(r)["2026-10-01"]?.routineDayId)
                assertEquals("Good day", r.workoutComments().single().comment)
                assertEquals(120, r.workoutRests().single().rest_seconds)
                assertEquals(4, r.records().size)
                assertEquals(80.0, r.measurements().first { it.name == "Waist" }.toModel().goalValue, 0.0)
                assertEquals("abc", r.photos().single().hash)

                // A body write through the typed queries keeps the AUTOINCREMENT counter (id 5 was deleted).
                val body = h.bodyDao
                h.transaction {
                    body.ensure("Waist", "cm", 999)
                    body.addManualRecord("Waist", "cm", "2026-10-06", "", 84.0, null)
                }
                assertEquals(listOf(1L, 2L, 3L, 4L, 6L), r.records().map { it.id })
                assertEquals(80.0, body.definition("Waist")!!.goal_value, 0.0)
            }
        }
    }

    @Test
    fun aQueryOnTheMainThreadIsRefused() {
        Db(app).use { h ->
            h.writableDatabase // opening is allowed; querying isn't
            assertThrows(IllegalStateException::class.java) { h.getMeta("weight_unit") }
        }
    }

    @Test
    fun everyUpgradeIsRegistered() {
        // Room needs a path from every version FlexNotes has shipped; a database from any of them opens.
        (1 until Db.VERSION).forEach { v ->
            app.deleteDatabase(Db.NAME)
            OldSchemas.create(file, v, OldSchemas.V20)
            Db(app).use { h -> assertEquals("from v$v", Db.VERSION, h.writableDatabase.version) }
        }
    }
}
