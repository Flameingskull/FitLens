package com.fitlens.companion.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class Db(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    companion object {
        const val NAME = "fitlens.db"
        const val VERSION = 10

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
    }

    override fun onCreate(db: SQLiteDatabase) {
        val stmts = listOf(
            // Workout data. `source` says who owns a row: 'fitnotes' (imported) or 'fitlens' (created or edited in
            // FitLens). `id` is FitLens's own stable id; `fitnotes_id` keeps the FitNotes id of imported rows for
            // reference only, so the two id spaces can never collide.
            "CREATE TABLE category(id INTEGER PRIMARY KEY, name TEXT NOT NULL, colour INTEGER NOT NULL DEFAULT 0, sort_order INTEGER NOT NULL DEFAULT 0, " +
                "source TEXT NOT NULL DEFAULT 'fitlens', fitnotes_id INTEGER)",
            "CREATE TABLE exercise(id INTEGER PRIMARY KEY, name TEXT NOT NULL, category_id INTEGER NOT NULL DEFAULT 0, type INTEGER NOT NULL DEFAULT 0, notes TEXT, " +
                "favourite INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL DEFAULT 'fitlens', fitnotes_id INTEGER, " +
                "weight_step REAL, default_graph INTEGER NOT NULL DEFAULT -1)",
            "CREATE TABLE workout_set(id INTEGER PRIMARY KEY, exercise_id INTEGER NOT NULL, date TEXT NOT NULL, weight REAL NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, distance REAL NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, is_pr INTEGER NOT NULL DEFAULT 0, comment TEXT, " +
                "source TEXT NOT NULL DEFAULT 'fitlens', fitnotes_id INTEGER, set_type INTEGER NOT NULL DEFAULT 0, rpe REAL, " +
                "position INTEGER NOT NULL DEFAULT 0, superset INTEGER NOT NULL DEFAULT 0)",
            CREATE_POSITION_TRIGGER,
            CREATE_SUPERSET_TRIGGER,
            "CREATE INDEX idx_set_date ON workout_set(date)",
            "CREATE INDEX idx_set_ex ON workout_set(exercise_id)",
            "CREATE TABLE measurement(name TEXT PRIMARY KEY, unit TEXT NOT NULL DEFAULT '', sort_order INTEGER NOT NULL DEFAULT 999, goal_type INTEGER NOT NULL DEFAULT 0, goal_value REAL NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, custom INTEGER NOT NULL DEFAULT 0, link TEXT, " +
                "edited INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE mrecord(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, unit TEXT NOT NULL DEFAULT '', date TEXT NOT NULL, time TEXT NOT NULL DEFAULT '', value REAL NOT NULL, comment TEXT, source TEXT NOT NULL)",
            "CREATE INDEX idx_mr_date ON mrecord(date)",
            CREATE_COMMENT,
            CREATE_TIME,
            CREATE_IMPORT_RULE,
            CREATE_GOAL,
            SavedWorkouts.CREATE_WORKOUT,
            SavedWorkouts.CREATE_EXERCISE,
            SavedWorkouts.CREATE_SET,
            Routines.CREATE_ROUTINE,
            Routines.CREATE_DAY,
            Routines.CREATE_ORIGIN,
            "CREATE TABLE photo(id INTEGER PRIMARY KEY AUTOINCREMENT, file TEXT NOT NULL, date TEXT, taken_at TEXT, date_source TEXT NOT NULL, pose TEXT NOT NULL DEFAULT '', note TEXT, original_name TEXT, hash TEXT UNIQUE, added_at INTEGER NOT NULL DEFAULT 0)",
            "CREATE INDEX idx_photo_date ON photo(date)",
            "CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)"
        )
        stmts.forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
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
            listOf(SavedWorkouts.CREATE_WORKOUT, SavedWorkouts.CREATE_EXERCISE, SavedWorkouts.CREATE_SET).forEach {
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
    }

    /**
     * Installing an older FitLens over a newer one used to be fatal: the default implementation throws
     * `SQLiteDowngradeFailedException`, which lands in `Store.init` during `Application.onCreate`, so the app
     * crash-looped and the only way out was uninstalling — taking every workout, photo and measurement with it (#77).
     *
     * Every migration this app has ever written only *adds* to the schema, and every read names its columns, so an
     * older build runs perfectly well against a newer database; it simply ignores what it doesn't know about. Keeping
     * the data and carrying on is therefore both safe and the only option that doesn't lose the user's history.
     *
     * The framework stamps the version down to [newVersion] once this returns, so the newer columns would be offered
     * to [onUpgrade] a second time on the way back up. That is why every step above is written to replay safely.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w("FitLens", "Opening a version $oldVersion database with version $newVersion code. Data is kept as it is.")
    }

    /** True when [table] already has [column]. Used so a migration step can be replayed without failing. */
    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameCol = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (c.getString(nameCol).equals(column, ignoreCase = true)) return true
            }
        }
        return false
    }

    /** Adds [column] unless it is already there. Returns true when it actually added it. */
    private fun addColumn(db: SQLiteDatabase, table: String, column: String, type: String): Boolean {
        if (hasColumn(db, table, column)) return false
        db.execSQL("ALTER TABLE $table ADD COLUMN $column $type")
        return true
    }

    fun getMeta(key: String): String? {
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(key)).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    /** Deletes the given `meta` rows in one transaction. */
    fun deleteMeta(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            keys.forEach { db.delete("meta", "k=?", arrayOf(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setMeta(key: String, value: String?) {
        val db = writableDatabase
        if (value == null) {
            db.delete("meta", "k=?", arrayOf(key))
        } else {
            val cv = ContentValues().apply { put("k", key); put("v", value) }
            db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
}

fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
fun Cursor.strOr(i: Int, def: String = ""): String = if (isNull(i)) def else (getString(i) ?: def)
fun Cursor.dbl(i: Int): Double = if (isNull(i)) 0.0 else getDouble(i)
fun Cursor.int(i: Int): Int = if (isNull(i)) 0 else getInt(i)
fun Cursor.lng(i: Int): Long = if (isNull(i)) 0L else getLong(i)
