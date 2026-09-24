package com.fitlens.companion.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    companion object {
        const val NAME = "fitlens.db"
        const val VERSION = 4

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
                "favourite INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL DEFAULT 'fitlens', fitnotes_id INTEGER)",
            "CREATE TABLE workout_set(id INTEGER PRIMARY KEY, exercise_id INTEGER NOT NULL, date TEXT NOT NULL, weight REAL NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, distance REAL NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, is_pr INTEGER NOT NULL DEFAULT 0, comment TEXT, " +
                "source TEXT NOT NULL DEFAULT 'fitlens', fitnotes_id INTEGER)",
            "CREATE INDEX idx_set_date ON workout_set(date)",
            "CREATE INDEX idx_set_ex ON workout_set(exercise_id)",
            "CREATE TABLE measurement(name TEXT PRIMARY KEY, unit TEXT NOT NULL DEFAULT '', sort_order INTEGER NOT NULL DEFAULT 999, goal_type INTEGER NOT NULL DEFAULT 0, goal_value REAL NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, custom INTEGER NOT NULL DEFAULT 0, link TEXT)",
            "CREATE TABLE mrecord(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, unit TEXT NOT NULL DEFAULT '', date TEXT NOT NULL, time TEXT NOT NULL DEFAULT '', value REAL NOT NULL, comment TEXT, source TEXT NOT NULL)",
            "CREATE INDEX idx_mr_date ON mrecord(date)",
            CREATE_COMMENT,
            CREATE_TIME,
            CREATE_IMPORT_RULE,
            "CREATE TABLE photo(id INTEGER PRIMARY KEY AUTOINCREMENT, file TEXT NOT NULL, date TEXT, taken_at TEXT, date_source TEXT NOT NULL, pose TEXT NOT NULL DEFAULT '', note TEXT, original_name TEXT, hash TEXT UNIQUE, added_at INTEGER NOT NULL DEFAULT 0)",
            "CREATE INDEX idx_photo_date ON photo(date)",
            "CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)"
        )
        stmts.forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrations run in place so updates (and restores of older backups) keep all existing data.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE measurement ADD COLUMN custom INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE measurement ADD COLUMN link TEXT")
        }
        if (oldVersion < 3) {
            // FitLens-owned workout data (#6). Every workout row that exists before this version came from a FitNotes
            // import, so it is marked 'fitnotes' and keeps its FitNotes id for reference. Nothing is deleted.
            listOf("category", "exercise", "workout_set").forEach { t ->
                db.execSQL("ALTER TABLE $t ADD COLUMN source TEXT NOT NULL DEFAULT 'fitlens'")
                db.execSQL("ALTER TABLE $t ADD COLUMN fitnotes_id INTEGER")
                db.execSQL("UPDATE $t SET source='fitnotes', fitnotes_id=id")
            }
            // Workout comments and times had no id column. They are rebuilt with a stable id, keeping every row.
            db.execSQL("ALTER TABLE workout_comment RENAME TO workout_comment_old")
            db.execSQL(CREATE_COMMENT)
            db.execSQL("INSERT INTO workout_comment(date, comment, source) SELECT date, comment, 'fitnotes' FROM workout_comment_old ORDER BY rowid")
            db.execSQL("DROP TABLE workout_comment_old")
            db.execSQL("ALTER TABLE workout_time RENAME TO workout_time_old")
            db.execSQL(CREATE_TIME)
            db.execSQL("INSERT INTO workout_time(date, start, finish, source) SELECT date, start, finish, 'fitnotes' FROM workout_time_old ORDER BY rowid")
            db.execSQL("DROP TABLE workout_time_old")
            db.execSQL(CREATE_IMPORT_RULE)
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
            db.execSQL("ALTER TABLE exercise ADD COLUMN favourite INTEGER NOT NULL DEFAULT 0")
            // (add further 1.0.8 statements here)
        }
    }

    fun getMeta(key: String): String? {
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(key)).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
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
