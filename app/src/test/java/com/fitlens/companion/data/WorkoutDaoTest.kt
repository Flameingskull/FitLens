package com.fitlens.companion.data

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The typed workout and routine queries (#36), run on a real database: the triggers that place a new set, the
 * optional date range and exercise list of a history delete, the import rules, and the queries that move a day's
 * notes. Room has already checked at build time that each query names real tables and columns; these tests check
 * that each one does what `Workouts` and `Routines` rely on. Synthetic data only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutDaoTest {

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

    /** Room refuses queries on the main thread, which is the thread Robolectric runs tests on. */
    private fun withDao(block: (Db, WorkoutDao) -> Unit) = Db(app).use { h -> runBlocking(Dispatchers.IO) { block(h, h.workoutDao) } }

    private fun WorkoutDao.set(exerciseId: Long, date: String, weight: Double, reps: Int, setType: Int = SetTypes.WORKING, superset: Int = 0) =
        addSet(
            exerciseId, date, weight, reps, 0.0, 0, isPr = 0, comment = null, source = Sources.FITLENS, setType = setType,
            rpe = null, metric = null, position = 0L, superset = superset, done = 0, restSeconds = null
        )

    @Test
    fun aNewSetIsPlacedAndGroupedByTheTriggers() = withDao { _, w ->
        val chest = w.addCategory("Chest", 0, w.lastCategoryOrder() + 1, Sources.FITLENS)
        val bench = w.addExercise("Bench Press", chest, 0, null, Sources.FITLENS)
        val fly = w.addExercise("Cable Fly", chest, 0, null, Sources.FITLENS)
        val first = w.set(bench, "2026-10-06", 100.0, 5, superset = 2)
        val second = w.set(bench, "2026-10-06", 100.0, 5)
        w.set(fly, "2026-10-06", 20.0, 12)
        // The position trigger puts each set at the end of its day; the superset trigger keeps an exercise in its group.
        assertEquals(first, w.setById(first)!!.position)
        assertEquals(second, w.setById(second)!!.position)
        assertEquals(2, w.setById(second)!!.superset)
        assertEquals(2, w.lastGroupOn("2026-10-06"))
        assertEquals(2, w.groupOf("2026-10-06", listOf(bench, fly)))
        assertNull(w.groupOf("2026-10-06", emptyList()))
        assertEquals(listOf(IdName(bench, "Bench Press"), IdName(fly, "Cable Fly")), w.exerciseNames().sortedBy { it.id })
        assertTrue(w.exerciseExists(bench))
        assertFalse(w.exerciseExists(999L))
    }

    @Test
    fun warmUpsSetTheBarOnlyWhenTheyCount() = withDao { _, w ->
        val squat = w.addExercise("Squat", Workouts.UNCATEGORISED, 0, null, Sources.FITLENS)
        w.set(squat, "2026-10-01", 140.0, 5, setType = SetTypes.WARMUP)
        w.set(squat, "2026-10-02", 120.0, 5)
        assertEquals(120.0, w.bestBefore(squat, 5, "2026-10-06", countWarmups = false, warmup = SetTypes.WARMUP)!!, 0.0)
        assertEquals(140.0, w.bestBefore(squat, 5, "2026-10-06", countWarmups = true, warmup = SetTypes.WARMUP)!!, 0.0)
        assertNull(w.bestBefore(squat, 6, "2026-10-06", countWarmups = true, warmup = SetTypes.WARMUP))
        assertEquals(2, w.prCandidates().size)
    }

    @Test
    fun aHistoryDeleteHonoursItsRangeAndExercises() = withDao { _, w ->
        val a = w.addExercise("Row", Workouts.UNCATEGORISED, 0, null, Sources.FITLENS)
        val b = w.addExercise("Curl", Workouts.UNCATEGORISED, 0, null, Sources.FITLENS)
        listOf("2026-09-01", "2026-09-15", "2026-10-01").forEach { d -> w.set(a, d, 60.0, 8); w.set(b, d, 15.0, 10) }
        w.addExerciseComment("2026-09-15", a, "Strap", Sources.FITLENS)

        // One exercise, open-ended start.
        assertEquals(2, w.deleteSetsInRange(null, "2026-09-15", everyExercise = false, exerciseIds = listOf(a)))
        w.deleteExerciseCommentsInRange(null, "2026-09-15", everyExercise = false, exerciseIds = listOf(a))
        assertNull(w.exerciseComment("2026-09-15", a))
        // Every exercise: the empty list is ignored.
        assertEquals(3, w.deleteSetsInRange("2026-09-02", null, everyExercise = true, exerciseIds = emptyList()))
        assertEquals(1, w.countSetsOf(b))
        // No exercises chosen and not every exercise: nothing goes.
        assertEquals(0, w.deleteSetsInRange(null, null, everyExercise = false, exerciseIds = emptyList()))
    }

    @Test
    fun importRulesAreAddedRelinkedAndDroppedOneAtATime() = withDao { _, w ->
        w.addRule(Workouts.RULE_SET, "1|2026-10-01|100.000|5|0.000|0", null)
        w.addRule(Workouts.RULE_SET, "1|2026-10-01|100.000|5|0.000|0", null)
        w.addRule(Workouts.RULE_EXERCISE, "bench", 1L)
        w.deleteOneSkip(Workouts.RULE_SET, "1|2026-10-01|100.000|5|0.000|0")
        assertEquals(1, w.rulesLike(Workouts.RULE_SET, "1|%").size)

        assertTrue(w.hasLinkTo(Workouts.RULE_EXERCISE, 1L))
        w.relink(Workouts.RULE_EXERCISE, 1L, 2L)
        assertTrue(w.hasLinkTo(Workouts.RULE_EXERCISE, 2L))
        w.relink(Workouts.RULE_EXERCISE, 2L, null)
        assertFalse(w.hasLinkTo(Workouts.RULE_EXERCISE, 2L))

        val rule = w.rulesLike(Workouts.RULE_SET, "1|%").single()
        w.setRuleKey(rule.id, "2|" + rule.key.removePrefix("1|"))
        assertEquals(1, w.rulesLike(Workouts.RULE_SET, "2|%").size)
    }

    @Test
    fun aDaysNotesMoveWithItsWorkout() = withDao { _, w ->
        w.addTime("2026-10-01", "2026-10-01 18:00:00", null, Sources.FITNOTES)
        w.addComment("2026-10-01", "Good day", Sources.FITNOTES)
        w.addExerciseComment("2026-10-01", 1L, "Elbows in", Sources.FITLENS)
        w.putRest(WorkoutRestRow("2026-10-01", 1L, 120, null))
        w.putOrigin(WorkoutOriginRow("2026-10-01", 1L, 3L))

        w.moveTimes("2026-10-01", "2026-10-03", Sources.FITLENS)
        w.moveComments("2026-10-01", "2026-10-03", Sources.FITLENS)
        w.moveRests("2026-10-01", "2026-10-03")
        w.moveOrigin("2026-10-01", "2026-10-03")
        val time = w.timesOn("2026-10-03").single()
        assertEquals("2026-10-03 18:00:00", time.start)
        assertNull(time.finish)
        assertEquals(Sources.FITLENS, time.source)
        assertEquals("Good day", w.commentsOn("2026-10-03").single().comment)
        assertEquals(120, w.restsOn("2026-10-03").single().rest_seconds)

        // A copied comment or rest never replaces the day's own.
        w.addExerciseComment("2026-10-04", 1L, "Own note", Sources.FITLENS)
        w.addExerciseCommentIfNone("2026-10-04", 1L, "Elbows in", Sources.FITLENS)
        assertEquals("Own note", w.exerciseComment("2026-10-04", 1L))
        w.addRestIfNone(WorkoutRestRow("2026-10-03", 1L, 60, null))
        assertEquals(120, w.restsOn("2026-10-03").single().rest_seconds)
    }

    @Test
    fun aDeletedExerciseLeavesEveryWorkoutDay() = withDao { h, w ->
        val r = h.routineDao
        val ex = w.addExercise("Dip", Workouts.UNCATEGORISED, 0, null, Sources.FITLENS)
        val routine = r.addRoutine(RoutineRow(0L, "Upper", null, r.nextRoutineOrder()))
        val day = r.addDay(routine, "Push Day", r.nextDayOrder(routine))
        val item = r.addItem(RoutineDayExerciseRow(0L, day, ex, 0, Routines.FILL_LAST, 0, 90, null))
        r.addPlannedSet(RoutineDaySetRow(0L, item, 0, 0.0, 10, 0.0, 0L, SetTypes.WORKING, null, null))
        assertEquals(listOf(day), r.dayIds(routine))
        assertEquals(1, r.nextDayOrder(routine))

        Routines.forgetExercise(r, ex)
        val snap = h.snapshotDao
        assertTrue(snap.routineDayExercises().isEmpty())
        assertTrue(snap.routineDaySets().isEmpty())
        assertEquals(1, snap.routineDays().size)
    }
}

/**
 * Room's own migration check (#36): [MigrationTestHelper] upgrades a database through the app's migrations and
 * compares every table with the exported schema committed in `app/schemas/`, the record a future version's
 * migration is reviewed against.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExportedSchemaTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FitLensDatabase::class.java)

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

    private val migrations get() = (1 until Db.VERSION).map { Upgrade(app, it) }.toTypedArray()

    @Test
    fun aV20DatabaseUpgradesToTheExportedSchema() {
        OldSchemas.create(app.getDatabasePath(Db.NAME), 20, OldSchemas.V20) { db ->
            db.row("category", "id" to 1L, "name" to "Chest", "source" to "fitnotes", "fitnotes_id" to 1L)
            db.row("exercise", "id" to 1L, "name" to "Bench Press", "category_id" to 1L)
            db.row("workout_set", "id" to 1L, "exercise_id" to 1L, "date" to "2026-10-01", "weight" to 100.0, "reps" to 5)
            db.row("import_rule", "kind" to "exercise", "key" to "bench", "target_id" to 1L)
            db.row("meta", "k" to "weight_unit", "v" to "kg")
        }
        val db = helper.runMigrationsAndValidate(Db.NAME, Db.VERSION, true, *migrations)
        assertEquals(1, db.count("SELECT COUNT(*) FROM workout_set WHERE weight = 100.0 AND reps = 5"))
        assertEquals(1, db.count("SELECT COUNT(*) FROM import_rule WHERE target_id = 1"))
        assertEquals(1, db.count("SELECT COUNT(*) FROM meta WHERE k = 'weight_unit'"))
    }

    @Test
    fun theExportedSchemaIsWhatTheAppOpens() {
        // A database made from the committed JSON opens in the app: same tables, same identity hash.
        helper.createDatabase(Db.NAME, Db.VERSION).close()
        Db(app).use { h ->
            runBlocking(Dispatchers.IO) {
                assertTrue(h.snapshotDao.sets().isEmpty())
                assertNull(h.getMeta("weight_unit"))
            }
        }
    }
}
