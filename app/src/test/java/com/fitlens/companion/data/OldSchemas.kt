package com.fitlens.companion.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Test fixtures (#40): database schemas as older FitLens versions created them, taken from the repository's history,
 * and small helpers for writing synthetic rows. Nothing here is real user data.
 */
object OldSchemas {

    /** Version 2 (1.0.4), the oldest schema in this repository. */
    val V2 = listOf(
        "CREATE TABLE category(id INTEGER PRIMARY KEY, name TEXT NOT NULL, colour INTEGER NOT NULL DEFAULT 0, sort_order INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE exercise(id INTEGER PRIMARY KEY, name TEXT NOT NULL, category_id INTEGER NOT NULL DEFAULT 0, type INTEGER NOT NULL DEFAULT 0, notes TEXT)",
        "CREATE TABLE workout_set(id INTEGER PRIMARY KEY, exercise_id INTEGER NOT NULL, date TEXT NOT NULL, weight REAL NOT NULL DEFAULT 0, " +
            "reps INTEGER NOT NULL DEFAULT 0, distance REAL NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, is_pr INTEGER NOT NULL DEFAULT 0, comment TEXT)",
        "CREATE INDEX idx_set_date ON workout_set(date)",
        "CREATE INDEX idx_set_ex ON workout_set(exercise_id)",
        "CREATE TABLE measurement(name TEXT PRIMARY KEY, unit TEXT NOT NULL DEFAULT '', sort_order INTEGER NOT NULL DEFAULT 999, " +
            "goal_type INTEGER NOT NULL DEFAULT 0, goal_value REAL NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, custom INTEGER NOT NULL DEFAULT 0, link TEXT)",
        "CREATE TABLE mrecord(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, unit TEXT NOT NULL DEFAULT '', date TEXT NOT NULL, " +
            "time TEXT NOT NULL DEFAULT '', value REAL NOT NULL, comment TEXT, source TEXT NOT NULL)",
        "CREATE INDEX idx_mr_date ON mrecord(date)",
        "CREATE TABLE workout_comment(date TEXT NOT NULL, comment TEXT NOT NULL)",
        "CREATE TABLE workout_time(date TEXT NOT NULL, start TEXT, finish TEXT)",
        "CREATE TABLE photo(id INTEGER PRIMARY KEY AUTOINCREMENT, file TEXT NOT NULL, date TEXT, taken_at TEXT, date_source TEXT NOT NULL, " +
            "pose TEXT NOT NULL DEFAULT '', note TEXT, original_name TEXT, hash TEXT UNIQUE, added_at INTEGER NOT NULL DEFAULT 0)",
        "CREATE INDEX idx_photo_date ON photo(date)",
        "CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)"
    )

    /** Version 1: version 2 before measurements gained `custom` and `link`. */
    val V1 = V2.map {
        if (it.startsWith("CREATE TABLE measurement")) {
            "CREATE TABLE measurement(name TEXT PRIMARY KEY, unit TEXT NOT NULL DEFAULT '', sort_order INTEGER NOT NULL DEFAULT 999, " +
                "goal_type INTEGER NOT NULL DEFAULT 0, goal_value REAL NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1)"
        } else it
    }

    /** Writes a database file at [file] with [schema] and whatever [fill] inserts, stamped with [version]. */
    fun create(file: File, version: Int, schema: List<String>, fill: (SQLiteDatabase) -> Unit = {}) {
        file.parentFile?.mkdirs()
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.forEach { db.execSQL(it) }
            fill(db)
            db.version = version
        }
    }

    /** A pre-v3 history: one category, one exercise, three sets, a comment, a time, a body value and a sync folder. */
    fun fillV2(db: SQLiteDatabase) {
        db.row("category", "id" to 1L, "name" to "Chest")
        db.row("exercise", "id" to 1L, "name" to "Bench Press", "category_id" to 1L)
        db.row("workout_set", "id" to 1L, "exercise_id" to 1L, "date" to "2025-01-02", "weight" to 80.0, "reps" to 8)
        db.row("workout_set", "id" to 2L, "exercise_id" to 1L, "date" to "2025-01-02", "weight" to 80.0, "reps" to 8)
        db.row("workout_set", "id" to 3L, "exercise_id" to 1L, "date" to "2025-01-05", "weight" to 85.0, "reps" to 5)
        db.row("workout_comment", "date" to "2025-01-02", "comment" to "Felt strong")
        db.row("workout_time", "date" to "2025-01-02", "start" to "2025-01-02 18:00:00", "finish" to "2025-01-02 19:00:00")
        db.row("measurement", "name" to "Bodyweight", "unit" to "kg")
        db.row("mrecord", "name" to "Bodyweight", "unit" to "kg", "date" to "2025-01-02", "value" to 82.5, "source" to "fitnotes")
        db.row("meta", "k" to "backup_folder", "v" to "content://folder")
    }
}

/** Inserts one row from name-value pairs. */
fun SQLiteDatabase.row(table: String, vararg values: Pair<String, Any?>): Long =
    insertOrThrow(table, null, ContentValues().apply {
        values.forEach { (k, v) ->
            when (v) {
                null -> putNull(k)
                is Long -> put(k, v)
                is Int -> put(k, v)
                is Double -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    })

/** The first column of the first row of [sql], as an Int (-1 when there's no row). */
fun SQLiteDatabase.count(sql: String, vararg args: String): Int =
    rawQuery(sql, args).use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }

fun SQLiteDatabase.hasTable(name: String): Boolean =
    count("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", name) == 1

fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean =
    rawQuery("PRAGMA table_info($table)", null).use { c ->
        val i = c.getColumnIndex("name")
        var found = false
        while (c.moveToNext()) if (c.getString(i).equals(column, ignoreCase = true)) found = true
        found
    }
