package com.fitlens.companion.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.Closeable
import java.util.concurrent.Callable

/**
 * FitLens's database, `fitlens.db`, opened through Room (#36). Room creates it on a fresh install from the tables in
 * `Schema.kt`, runs [upgrade] and then [reconcile] on anything older, and checks the result against those tables
 * before the app sees it. Everything but the FitNotes import and backups uses the typed queries in `Daos.kt`
 * through [snapshotDao] and its neighbours; those two still write SQL through [writableDatabase].
 */
class Db(context: Context) : Closeable {

    companion object {
        const val NAME = "fitlens.db"
        /** 21: Room takes over the file (#36). */
        const val VERSION = 21

        /** The most ids one query binds, inside the 999-variable limit of older phones' SQLite. */
        const val MAX_IDS = 500

        /**
         * The saved workouts of v7–v12 (#100). Since v13 their contents live in workout days (#106) and these tables
         * stay empty, kept only so an older FitLens can still open the database (#77) and the v13 step can replay.
         */
        private const val CREATE_LEGACY_SAVED_WORKOUT =
            "CREATE TABLE saved_workout(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, notes TEXT, " +
                "sort_order INTEGER NOT NULL DEFAULT 0)"
        private const val CREATE_LEGACY_SAVED_EXERCISE =
            "CREATE TABLE saved_workout_exercise(id INTEGER PRIMARY KEY AUTOINCREMENT, workout_id INTEGER NOT NULL, " +
                "exercise_id INTEGER NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, fill INTEGER NOT NULL DEFAULT 0, " +
                "superset INTEGER NOT NULL DEFAULT 0)"
        private const val CREATE_LEGACY_SAVED_SET =
            "CREATE TABLE saved_workout_set(id INTEGER PRIMARY KEY AUTOINCREMENT, item_id INTEGER NOT NULL, " +
                "sort_order INTEGER NOT NULL DEFAULT 0, weight REAL NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, " +
                "distance REAL NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, set_type INTEGER NOT NULL DEFAULT 0)"

        /**
         * Exercise comments (#107): one note per exercise within a date's workout ("left shoulder tight"). FitLens's
         * own; FitNotes imports never write it.
         */
        const val CREATE_EXERCISE_COMMENT =
            "CREATE TABLE exercise_comment(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, exercise_id INTEGER NOT NULL, " +
                "comment TEXT NOT NULL, source TEXT NOT NULL DEFAULT 'fitlens', UNIQUE(date, exercise_id))"

        /**
         * Prescribed rest on a logged date (#138): one row per exercise, copied from the workout day it was logged
         * from. FitLens's own; FitNotes imports never write it.
         */
        const val CREATE_WORKOUT_REST =
            "CREATE TABLE workout_rest(date TEXT NOT NULL, exercise_id INTEGER NOT NULL, rest_seconds INTEGER, " +
                "rest_after_seconds INTEGER, PRIMARY KEY(date, exercise_id))"

        /**
         * User-defined exercise types (#14). Ids start at [ExerciseTypes.CUSTOM_BASE], so `exercise.type` tells them
         * from the built-in ones. Each flag says whether a set records that value; `metric_name` and `metric_unit`
         * describe the type's own metric, kept in `workout_set.metric`. FitLens's own; FitNotes imports never write it.
         */
        const val CREATE_EXERCISE_TYPE =
            "CREATE TABLE exercise_type(id INTEGER PRIMARY KEY, name TEXT NOT NULL, uses_weight INTEGER NOT NULL DEFAULT 0, " +
                "uses_reps INTEGER NOT NULL DEFAULT 0, uses_distance INTEGER NOT NULL DEFAULT 0, uses_time INTEGER NOT NULL DEFAULT 0, " +
                "metric_name TEXT, metric_unit TEXT)"

        private const val CREATE_COMMENT =
            "CREATE TABLE workout_comment(id INTEGER PRIMARY KEY, date TEXT NOT NULL, comment TEXT NOT NULL, source TEXT NOT NULL DEFAULT 'fitlens')"
        private const val CREATE_TIME =
            "CREATE TABLE workout_time(id INTEGER PRIMARY KEY, date TEXT NOT NULL, start TEXT, finish TEXT, source TEXT NOT NULL DEFAULT 'fitlens')"

        /**
         * Remembers what the user did in FitLens to data that came from FitNotes, so a later import respects it.
         * kind 'category' / 'exercise': key = lower-case FitNotes name, target_id = the FitLens row it now maps to
         *   (after a rename), or NULL when the user deleted it (the import then skips it).
         * kind 'set' / 'comment' / 'time': key = the imported values the user deleted or edited; one matching row in a
         *   backup is skipped per rule.
         */
        /** Exercise goals (#25): one row per goal, ordered per exercise by sort_order. */
        private const val CREATE_GOAL =
            "CREATE TABLE exercise_goal(id INTEGER PRIMARY KEY AUTOINCREMENT, exercise_id INTEGER NOT NULL, kind INTEGER NOT NULL, " +
                "target REAL NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0)"

        /**
         * The order of sets within a day (#70): a new set takes its own id as its position, so it lands last. The
         * trigger covers every insert path (logging, copies, saved workouts, FitNotes imports) without each one
         * having to remember. A position set on insert (an undo putting a set back) is kept.
         */
        const val CREATE_POSITION_TRIGGER =
            "CREATE TRIGGER IF NOT EXISTS set_position AFTER INSERT ON workout_set WHEN NEW.position = 0 " +
                "BEGIN UPDATE workout_set SET position = NEW.id WHERE id = NEW.id; END"

        /**
         * Supersets (#18): a new set joins its exercise's group on that day, so every set of a grouped exercise
         * carries the same group number whichever way it was added.
         */
        const val CREATE_SUPERSET_TRIGGER =
            "CREATE TRIGGER IF NOT EXISTS set_superset AFTER INSERT ON workout_set WHEN NEW.superset = 0 " +
                "BEGIN UPDATE workout_set SET superset = IFNULL((SELECT MAX(superset) FROM workout_set " +
                "WHERE exercise_id = NEW.exercise_id AND substr(date, 1, 10) = substr(NEW.date, 1, 10) AND id <> NEW.id), 0) " +
                "WHERE id = NEW.id; END"

        private const val CREATE_IMPORT_RULE =
            "CREATE TABLE import_rule(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, key TEXT NOT NULL, target_id INTEGER)"

        /**
         * The hand-written upgrades of versions 1 to 20, run by Room before the v21 rebuild. Every step migrates in
         * place and keeps existing rows, and replays safely after a downgrade (#77).
         */
        internal fun upgrade(db: SupportSQLiteDatabase, oldVersion: Int) {
            // Migrations run in place so updates (and restores of older backups) keep all existing data.
            if (oldVersion < 2) {
                addColumn(db, "measurement", "custom", "INTEGER NOT NULL DEFAULT 0")
                addColumn(db, "measurement", "link", "TEXT")
            }
            if (oldVersion < 3) {
                // FitLens-owned workout data (#6). Every workout row that exists before this version came from a FitNotes
                // import, so it is marked 'fitnotes' and keeps its FitNotes id for reference. Nothing is deleted.
                listOf("category", "exercise", "workout_set").forEach { t ->
                    if (addColumn(db, t, "source", "TEXT NOT NULL DEFAULT 'fitlens'")) {
                        addColumn(db, t, "fitnotes_id", "INTEGER")
                        db.execSQL("UPDATE $t SET source='fitnotes', fitnotes_id=id")
                    }
                }
                // Workout comments and times had no id column. They are rebuilt with a stable id, keeping every row.
                if (!hasColumn(db, "workout_comment", "id")) {
                    db.execSQL("ALTER TABLE workout_comment RENAME TO workout_comment_old")
                    db.execSQL(CREATE_COMMENT)
                    db.execSQL("INSERT INTO workout_comment(date, comment, source) SELECT date, comment, 'fitnotes' FROM workout_comment_old ORDER BY rowid")
                    db.execSQL("DROP TABLE workout_comment_old")
                }
                if (!hasColumn(db, "workout_time", "id")) {
                    db.execSQL("ALTER TABLE workout_time RENAME TO workout_time_old")
                    db.execSQL(CREATE_TIME)
                    db.execSQL("INSERT INTO workout_time(date, start, finish, source) SELECT date, start, finish, 'fitnotes' FROM workout_time_old ORDER BY rowid")
                    db.execSQL("DROP TABLE workout_time_old")
                }
                db.execSQL(CREATE_IMPORT_RULE.replace("CREATE TABLE", "CREATE TABLE IF NOT EXISTS"))
                // The FitNotes folder sync is now off by default. People who already chose a sync folder and never
                // switched sync off keep it on, so an update doesn't silently stop their sync.
                db.execSQL(
                    "INSERT OR IGNORE INTO meta(k, v) SELECT 'auto_sync', '1' WHERE EXISTS (SELECT 1 FROM meta WHERE k='backup_folder' AND v IS NOT NULL)"
                )
            }
            if (oldVersion < 4) {
                // ---- 1.0.8, the logging core -------------------------------------------------------------------
                // One consolidated step for the whole 1.0.8 build: anything else this build needs is added *inside*
                // this block rather than as a version 5, so an update and an archive restore both replay one upgrade.
                // Every statement must migrate in place and keep existing rows.
                //
                // #13 exercise library: favourite exercises, listed first in the exercise pickers. Existing
                // exercises (imported or FitLens's own) default to not a favourite and are otherwise untouched.
                addColumn(db, "exercise", "favourite", "INTEGER NOT NULL DEFAULT 0")
                // (add further 1.0.8 statements here)
            }
            if (oldVersion < 5) {
                // ---- 1.0.27: set types (#43) and effort (#44), one step for both --------------------------------
                // Every existing set becomes a working set with no effort recorded. Both columns are added only if
                // missing, so the step replays safely after a downgrade (#77) and on restores of older backups.
                addColumn(db, "workout_set", "set_type", "INTEGER NOT NULL DEFAULT 0")
                addColumn(db, "workout_set", "rpe", "REAL")
            }
            if (oldVersion < 6) {
                // ---- 1.0.28: goals and per-exercise defaults --------------------------------------------------
                // Exercise goals (#25) get their own table. Exercises gain an optional weight step and default graph
                // (#15; -1 means automatic). Measurements gain `edited`, set when the user changes a goal or the order
                // in FitLens, so a FitNotes import stops refreshing them (#27). Every existing row is kept as it is.
                db.execSQL(CREATE_GOAL.replace("CREATE TABLE", "CREATE TABLE IF NOT EXISTS"))
                addColumn(db, "exercise", "weight_step", "REAL")
                addColumn(db, "exercise", "default_graph", "INTEGER NOT NULL DEFAULT -1")
                addColumn(db, "measurement", "edited", "INTEGER NOT NULL DEFAULT 0")
            }
            if (oldVersion < 7) {
                // ---- 1.0.33: saved workouts (#100) -----------------------------------------------------------
                // Three new tables and nothing else: no existing row or column changes. IF NOT EXISTS lets the step
                // replay safely after a downgrade (#77) and on restores of older backups.
                listOf(CREATE_LEGACY_SAVED_WORKOUT, CREATE_LEGACY_SAVED_EXERCISE, CREATE_LEGACY_SAVED_SET).forEach {
                    db.execSQL(it.replace("CREATE TABLE", "CREATE TABLE IF NOT EXISTS"))
                }
            }
            if (oldVersion < 8) {
                // ---- 1.0.34: routines (#21) ----------------------------------------------------------------------
                // Routines, their days, and which saved workout each logged day was started from. New tables only;
                // nothing existing changes, and the step replays safely (#77).
                listOf(Routines.CREATE_ROUTINE, Routines.CREATE_DAY, Routines.CREATE_ORIGIN).forEach {
                    db.execSQL(it.replace("CREATE TABLE", "CREATE TABLE IF NOT EXISTS"))
                }
            }
            if (oldVersion < 9) {
                // ---- 1.0.39: the order of sets and exercises within a workout (#70) ------------------------------
                // Every existing set takes its id as its position, which is exactly the order it was shown in before,
                // so nothing moves. The column is only added when missing, so the step replays safely (#77).
                if (addColumn(db, "workout_set", "position", "INTEGER NOT NULL DEFAULT 0")) {
                    db.execSQL("UPDATE workout_set SET position = id")
                }
                db.execSQL(CREATE_POSITION_TRIGGER)
            }
            if (oldVersion < 10) {
                // ---- 1.0.40: supersets (#18) ------------------------------------------------------------------------
                // A group number on logged sets and on saved workouts' exercises; 0 means not grouped, so everything that
                // exists stays as it is. Columns are only added when missing, so the step replays safely (#77).
                addColumn(db, "workout_set", "superset", "INTEGER NOT NULL DEFAULT 0")
                addColumn(db, "saved_workout_exercise", "superset", "INTEGER NOT NULL DEFAULT 0")
                db.execSQL(CREATE_SUPERSET_TRIGGER)
            }
            if (oldVersion < 11) {
                // ---- 1.0.41: mark sets complete (#19) ----------------------------------------------------------------
                // Whether a set has been ticked off. Every existing set starts unticked; the step replays safely (#77).
                addColumn(db, "workout_set", "done", "INTEGER NOT NULL DEFAULT 0")
            }
            if (oldVersion < 12) {
                // ---- 1.0.44: per-exercise rest time (#15) ------------------------------------------------------------
                // An exercise's own rest length in seconds; NULL uses the global one, so every exercise keeps today's
                // behaviour. The step replays safely (#77).
                addColumn(db, "exercise", "rest_seconds", "INTEGER")
            }
            if (oldVersion < 13) {
                // ---- 1.0.50: workouts and routines as one function (#106) --------------------------------------------
                // Each routine day takes a copy of its saved workout's exercises and sets; saved workouts no day used
                // become one-day workouts; logged dates are re-pointed. See Routines.migrateSavedWorkouts. The step
                // runs inside the upgrade's transaction, so it copies everything or nothing, and it replays safely (#77).
                Routines.migrateSavedWorkouts(db)
            }
            if (oldVersion < 14) {
                // ---- 1.0.52: exercise comments (#107) ------------------------------------------------------------------
                // One new table; nothing existing changes, and the step replays safely (#77).
                db.execSQL(CREATE_EXERCISE_COMMENT.replace("CREATE TABLE", "CREATE TABLE IF NOT EXISTS"))
            }
            if (oldVersion < 15) {
                // ---- 1.0.65: distance units (#7) ---------------------------------------------------------------------
                // An exercise's own distance unit; NULL uses the global one. Distances themselves are untouched, and the
                // step replays safely (#77).
                addColumn(db, "exercise", "distance_unit", "TEXT")
            }
            if (oldVersion < 16) {
                // ---- 1.0.66: per-exercise weight unit and per-measurement units (#7) ---------------------------------
                // NULL uses the global unit. Weights stay in kg and body values in the unit they were logged in; these
                // only choose how they're shown. The step replays safely (#77).
                addColumn(db, "exercise", "weight_unit", "TEXT")
                if (hasTable(db, "measurement")) addColumn(db, "measurement", "display_unit", "TEXT")
            }
            if (oldVersion < 17) {
                // ---- 1.0.73: prescribed rest (#138) --------------------------------------------------------------------
                // A rest per planned set, per planned exercise and after it, the rest copied onto logged sets, and one
                // table for a logged date's exercise rests. All NULL means none, so every workout and set keeps today's
                // behaviour. The workout-day tables exist from v13 on (Routines.migrateSavedWorkouts); the step replays
                // safely (#77).
                addColumn(db, "workout_set", "rest_seconds", "INTEGER")
                if (hasTable(db, "routine_day_exercise")) {
                    addColumn(db, "routine_day_exercise", "rest_seconds", "INTEGER")
                    addColumn(db, "routine_day_exercise", "rest_after_seconds", "INTEGER")
                }
                if (hasTable(db, "routine_day_set")) addColumn(db, "routine_day_set", "rest_seconds", "INTEGER")
                db.execSQL(CREATE_WORKOUT_REST.replace("CREATE TABLE", "CREATE TABLE IF NOT EXISTS"))
            }
            if (oldVersion < 18) {
                // ---- 1.0.93: body fat and height for everyone (#153) ---------------------------------------------------
                // Two rows, added only when no measurement or logged value already has the name (ignoring capitals), so
                // FitNotes's "Body Fat" isn't doubled. Nothing existing changes, and the step replays safely (#77).
                if (hasTable(db, "measurement")) addDefaultMeasurements(db)
            }
            if (oldVersion < 19) {
                // ---- 1.0.98: user-defined exercise types (#14) ---------------------------------------------------------
                // A new table and one nullable column, so every exercise keeps its type and every set its values. The
                // step replays safely (#77).
                db.execSQL(CREATE_EXERCISE_TYPE.replace("CREATE TABLE", "CREATE TABLE IF NOT EXISTS"))
                if (hasTable(db, "workout_set")) addColumn(db, "workout_set", "metric", "REAL")
            }
            if (oldVersion < 20) {
                // ---- 1.0.100: a custom type's metric in predefined sets (#155) ------------------------------------------
                // One nullable column, so every predefined set keeps its values and has no metric. Replays safely (#77).
                if (hasTable(db, "routine_day_set")) addColumn(db, "routine_day_set", "metric", "REAL")
            }
        }

        /**
         * The v21 step (#36): every table is rebuilt exactly as Room creates it, so Room's schema check passes on a
         * database that has grown through twenty versions of hand-written SQL. Room's own SQL is read from an empty
         * in-memory copy, so it can never drift from `Schema.kt`. Each table keeps every row and every value: a column
         * the old table has and the new one doesn't stops the upgrade (Room then leaves the old file untouched)
         * rather than losing data. Missing tables are created, and AUTOINCREMENT counters carry on where they were, so
         * a deleted row's id is never reused. Runs inside the upgrade's transaction and replays safely (#77).
         */
        internal fun reconcile(context: Context, db: SupportSQLiteDatabase) {
            val reference = RoomReference.read(context)
            db.execSQL("DROP TRIGGER IF EXISTS set_position")
            db.execSQL("DROP TRIGGER IF EXISTS set_superset")
            reference.tables.forEach { (table, sql) -> rebuild(db, table, sql) }
            reference.indices.forEach { db.execSQL(it) }
            db.execSQL(CREATE_POSITION_TRIGGER)
            db.execSQL(CREATE_SUPERSET_TRIGGER)
        }

        private class Column(val name: String, val type: String, val notNull: Boolean, val default: String?, val pk: Boolean)

        private fun columns(db: SupportSQLiteDatabase, table: String): List<Column> =
            db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
                val name = c.getColumnIndexOrThrow("name")
                val type = c.getColumnIndexOrThrow("type")
                val notNull = c.getColumnIndexOrThrow("notnull")
                val default = c.getColumnIndexOrThrow("dflt_value")
                val pk = c.getColumnIndexOrThrow("pk")
                ArrayList<Column>().apply {
                    while (c.moveToNext()) {
                        add(Column(c.getString(name), c.strOr(type), c.getInt(notNull) != 0, c.str(default), c.getInt(pk) > 0))
                    }
                }
            }

        private fun rebuild(db: SupportSQLiteDatabase, table: String, roomSql: String) {
            val temp = "${table}_v$VERSION"
            val createTemp = Regex("^CREATE TABLE\\s+(IF NOT EXISTS\\s+)?[`\"]?${Regex.escape(table)}[`\"]?", RegexOption.IGNORE_CASE)
                .replaceFirst(roomSql, "CREATE TABLE `$temp`")
            check(createTemp != roomSql) { "Unexpected table SQL for $table" }
            val old = columns(db, table)
            db.execSQL("DROP TABLE IF EXISTS `$temp`")
            db.execSQL(createTemp)
            if (old.isEmpty()) {
                // A table this database never had (an old or partial one): created empty.
                db.execSQL("ALTER TABLE `$temp` RENAME TO `$table`")
                return
            }
            val fresh = columns(db, temp)
            val freshNames = fresh.map { it.name.lowercase() }.toSet()
            val unknown = old.filter { it.name.lowercase() !in freshNames }.map { it.name }
            check(unknown.isEmpty()) { "Upgrade stopped so no data is lost: $table has columns $unknown that Schema.kt lacks" }
            val oldNames = old.map { it.name.lowercase() }.toSet()
            val into = ArrayList<String>()
            val from = ArrayList<String>()
            fresh.forEach { c ->
                val quoted = "`${c.name}`"
                // A required value that's missing or NULL takes the column's default, or an empty value when it has none.
                val text = c.type.contains("TEXT", ignoreCase = true) || c.type.contains("CHAR", ignoreCase = true)
                val fallback = c.default ?: if (text) "''" else "0"
                when {
                    c.name.lowercase() in oldNames && c.notNull && !c.pk -> { into += quoted; from += "IFNULL($quoted, $fallback)" }
                    c.name.lowercase() in oldNames -> { into += quoted; from += quoted }
                    c.notNull && c.default == null -> { into += quoted; from += fallback }
                    else -> Unit // a new column: its default fills it
                }
            }
            val sequence = if (roomSql.contains("AUTOINCREMENT", ignoreCase = true) && hasTable(db, "sqlite_sequence")) {
                db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name=?", arrayOf(table)).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            } else null
            val before = db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            if (into.isNotEmpty()) {
                db.execSQL("INSERT OR IGNORE INTO `$temp`(${into.joinToString()}) SELECT ${from.joinToString()} FROM `$table`")
            }
            val after = db.rawQuery("SELECT COUNT(*) FROM `$temp`", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            if (after != before) Log.w("FitLens", "Rebuilding $table kept $after of $before rows (the others had no key)")
            db.execSQL("DROP TABLE `$table`")
            db.execSQL("ALTER TABLE `$temp` RENAME TO `$table`")
            if (sequence != null) {
                db.execSQL("DELETE FROM sqlite_sequence WHERE name=?", arrayOf(table))
                db.execSQL(
                    "INSERT INTO sqlite_sequence(name, seq) SELECT ?, MAX(?, IFNULL((SELECT MAX(rowid) FROM `$table`), 0))",
                    arrayOf<Any>(table, sequence)
                )
            }
        }

        /**
         * Body fat (%) and Height (cm) are default measurements (#153): body fat is logged or calculated from the tape
         * measurements, and height is one of the formula's inputs. Each is added after the others unless its name is
         * already used by a measurement or a logged value, ignoring capitals.
         */
        internal fun addDefaultMeasurements(db: SupportSQLiteDatabase) {
            val names = HashSet<String>()
            db.rawQuery("SELECT name FROM measurement", null).use { c -> while (c.moveToNext()) names += c.getString(0).trim().lowercase() }
            if (hasTable(db, "mrecord")) {
                db.rawQuery("SELECT DISTINCT name FROM mrecord", null).use { c -> while (c.moveToNext()) names += c.getString(0).trim().lowercase() }
            }
            // A very old table may lack the later columns until its own steps add them: fill in only those it has.
            val has = listOf("sort_order", "enabled", "custom", "edited").filter { hasColumn(db, "measurement", it) }.toSet()
            var order = if ("sort_order" !in has) 0 else
                db.rawQuery("SELECT IFNULL(MAX(sort_order), 0) FROM measurement WHERE sort_order < 900", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            listOf(BodyFat.NAME to BodyFat.UNIT, "Height" to LengthUnits.CM).forEach { (name, unit) ->
                if (name.lowercase() in names) return@forEach
                order++
                db.insertWithOnConflict("measurement", null, ContentValues().apply {
                    put("name", name); put("unit", unit)
                    if ("sort_order" in has) put("sort_order", order)
                    listOf("enabled", "custom", "edited").filter { it in has }.forEach { put(it, 1) }
                }, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }

        private fun hasTable(db: SupportSQLiteDatabase, table: String): Boolean =
            db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }

        /** True when [table] already has [column]. Used so a migration step can be replayed without failing. */
        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                val nameCol = c.getColumnIndex("name")
                while (c.moveToNext()) {
                    if (c.getString(nameCol).equals(column, ignoreCase = true)) return true
                }
            }
            return false
        }

        /** Adds [column] unless it is already there. Returns true when it actually added it. */
        private fun addColumn(db: SupportSQLiteDatabase, table: String, column: String, type: String): Boolean {
            if (hasColumn(db, table, column)) return false
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $type")
            return true
        }
    }

    private val appContext: Context = context.applicationContext ?: context

    private val room: FitLensDatabase = Room.databaseBuilder(appContext, FitLensDatabase::class.java, NAME)
        // A rollback journal, as before: a backup copies the one database file, and a restore replaces it (#35).
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .addMigrations(*(1 until VERSION).map { Upgrade(appContext, it) }.toTypedArray())
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_POSITION_TRIGGER)
                db.execSQL(CREATE_SUPERSET_TRIGGER)
                addDefaultMeasurements(db)
            }
        })
        .build()

    @Volatile
    private var downgradeChecked = false

    /** The open database. The first call opens it, running any upgrade; later calls return the same connection. */
    val writableDatabase: SupportSQLiteDatabase
        get() {
            if (!downgradeChecked) {
                synchronized(this) {
                    if (!downgradeChecked) {
                        allowDowngrade()
                        downgradeChecked = true
                    }
                }
            }
            return room.openHelper.writableDatabase
        }

    val readableDatabase: SupportSQLiteDatabase get() = writableDatabase

    /**
     * Installing an older FitLens over a newer one used to be fatal: opening the newer file threw during
     * `Application.onCreate`, so the app crash-looped and the only way out was uninstalling, taking every workout,
     * photo and measurement with it (#77). Room refuses a newer file in the same way.
     *
     * Every upgrade this app has ever written only *adds* to the schema, and every read names its columns, so an
     * older build runs perfectly well against a newer database; it simply ignores what it doesn't know about. So a
     * newer file is stamped down to [VERSION] and marked as this build's schema, keeping every row, and the newer
     * build's upgrade replays when it's installed again. That's why every upgrade step is written to replay safely.
     */
    private fun allowDowngrade() {
        val file = appContext.getDatabasePath(NAME)
        if (!file.exists()) return
        runCatching {
            val version = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
            if (version <= VERSION) return
            Log.w("FitLens", "Opening a version $version database with version $VERSION code. Data is kept as it is.")
            val hash = RoomReference.read(appContext).identityHash
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
                raw.beginTransaction()
                try {
                    raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    raw.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)", arrayOf(hash))
                    raw.version = VERSION
                    raw.setTransactionSuccessful()
                } finally {
                    raw.endTransaction()
                }
            }
        }.onFailure { Log.w("FitLens", "Couldn't check the database version before opening it", it) }
    }

    // ---- Typed queries (#36). Each opens the database first, so the downgrade check above runs before Room's. ----

    val snapshotDao: SnapshotDao get() = opened().snapshotDao()
    val metaDao: MetaDao get() = opened().metaDao()
    val bodyDao: BodyDao get() = opened().bodyDao()
    val photoDao: PhotoDao get() = opened().photoDao()
    val goalDao: GoalDao get() = opened().goalDao()
    val workoutDao: WorkoutDao get() = opened().workoutDao()
    val routineDao: RoutineDao get() = opened().routineDao()

    private fun opened(): FitLensDatabase {
        writableDatabase
        return room
    }

    /** Runs [block] in one Room transaction: every DAO call inside it lands together, or none does. */
    fun <T> transaction(block: () -> T): T = opened().runInTransaction(Callable<T> { block() })

    // ---- Preferences (`meta`). Off the main thread only, like every query (#36). ---------------------------------

    fun getMeta(key: String): String? = metaDao.get(key)

    /** Deletes the given `meta` rows in one transaction. */
    fun deleteMeta(keys: Collection<String>) {
        if (keys.isEmpty()) return
        metaDao.deleteAll(keys.toList())
    }

    fun setMeta(key: String, value: String?) {
        if (value == null) metaDao.delete(key) else metaDao.put(MetaRow(key, value))
    }

    /** Stores several `meta` values in one transaction; a null value removes its row. */
    fun setMetas(values: Map<String, String?>) = metaDao.putAll(values)

    override fun close() {
        room.close()
    }
}

/** One upgrade from [from] straight to [Db.VERSION]: the hand-written steps, then the v21 rebuild for Room. */
internal class Upgrade(private val context: Context, from: Int) : Migration(from, Db.VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Db.upgrade(db, startVersion)
        Db.reconcile(context, db)
    }
}

/**
 * What Room itself creates for `Schema.kt`, read from an empty in-memory database: the SQL of each table and index,
 * and the schema's identity hash. Used only while upgrading or opening a newer file, never on a normal start.
 */
internal class RoomReference(val tables: List<Pair<String, String>>, val indices: List<String>, val identityHash: String) {
    companion object {
        fun read(context: Context): RoomReference {
            val memory = Room.inMemoryDatabaseBuilder(context, FitLensDatabase::class.java).build()
            try {
                val db = memory.openHelper.writableDatabase
                val tables = ArrayList<Pair<String, String>>()
                val indices = ArrayList<String>()
                db.query(
                    "SELECT type, name, sql FROM sqlite_master WHERE type IN ('table', 'index') AND sql IS NOT NULL " +
                        "AND name NOT IN ('room_master_table', 'android_metadata', 'sqlite_sequence') ORDER BY rowid"
                ).use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(0) == "table") tables += c.getString(1) to c.getString(2) else indices += c.getString(2)
                    }
                }
                val hash = db.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { c ->
                    check(c.moveToFirst()) { "Room wrote no identity hash" }
                    c.getString(0)
                }
                return RoomReference(tables, indices, hash)
            } finally {
                memory.close()
            }
        }
    }
}

// ---- The framework SQLiteDatabase calls the app's SQL is written with, on Room's connection (#36) ----------------

fun SupportSQLiteDatabase.rawQuery(sql: String, args: Array<out String?>?): Cursor =
    if (args.isNullOrEmpty()) query(sql) else query(sql, args)

fun SupportSQLiteDatabase.insertOrThrow(table: String, nullColumnHack: String?, values: ContentValues): Long =
    insert(table, SQLiteDatabase.CONFLICT_NONE, values)

/** As the framework's `insert`: -1 instead of an exception when the row can't be added. */
fun SupportSQLiteDatabase.insert(table: String, nullColumnHack: String?, values: ContentValues): Long =
    try {
        insert(table, SQLiteDatabase.CONFLICT_NONE, values)
    } catch (e: SQLException) {
        Log.e("FitLens", "Couldn't add a row to $table", e)
        -1L
    }

fun SupportSQLiteDatabase.insertWithOnConflict(table: String, nullColumnHack: String?, values: ContentValues, conflictAlgorithm: Int): Long =
    insert(table, conflictAlgorithm, values)

fun SupportSQLiteDatabase.update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<out String?>?): Int =
    update(table, SQLiteDatabase.CONFLICT_NONE, values, whereClause, whereArgs)

fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
fun Cursor.strOr(i: Int, def: String = ""): String = if (isNull(i)) def else (getString(i) ?: def)
fun Cursor.dbl(i: Int): Double = if (isNull(i)) 0.0 else getDouble(i)
fun Cursor.int(i: Int): Int = if (isNull(i)) 0 else getInt(i)
fun Cursor.lng(i: Int): Long = if (isNull(i)) 0L else getLong(i)
