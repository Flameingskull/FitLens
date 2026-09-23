package com.fitlens.companion.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    companion object {
        const val NAME = "fitlens.db"
        const val VERSION = 2
    }

    override fun onCreate(db: SQLiteDatabase) {
        val stmts = listOf(
            "CREATE TABLE category(id INTEGER PRIMARY KEY, name TEXT NOT NULL, colour INTEGER NOT NULL DEFAULT 0, sort_order INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE exercise(id INTEGER PRIMARY KEY, name TEXT NOT NULL, category_id INTEGER NOT NULL DEFAULT 0, type INTEGER NOT NULL DEFAULT 0, notes TEXT)",
            "CREATE TABLE workout_set(id INTEGER PRIMARY KEY, exercise_id INTEGER NOT NULL, date TEXT NOT NULL, weight REAL NOT NULL DEFAULT 0, reps INTEGER NOT NULL DEFAULT 0, distance REAL NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, is_pr INTEGER NOT NULL DEFAULT 0, comment TEXT)",
            "CREATE INDEX idx_set_date ON workout_set(date)",
            "CREATE INDEX idx_set_ex ON workout_set(exercise_id)",
            "CREATE TABLE measurement(name TEXT PRIMARY KEY, unit TEXT NOT NULL DEFAULT '', sort_order INTEGER NOT NULL DEFAULT 999, goal_type INTEGER NOT NULL DEFAULT 0, goal_value REAL NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, custom INTEGER NOT NULL DEFAULT 0, link TEXT)",
            "CREATE TABLE mrecord(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, unit TEXT NOT NULL DEFAULT '', date TEXT NOT NULL, time TEXT NOT NULL DEFAULT '', value REAL NOT NULL, comment TEXT, source TEXT NOT NULL)",
            "CREATE INDEX idx_mr_date ON mrecord(date)",
            "CREATE TABLE workout_comment(date TEXT NOT NULL, comment TEXT NOT NULL)",
            "CREATE TABLE workout_time(date TEXT NOT NULL, start TEXT, finish TEXT)",
            "CREATE TABLE photo(id INTEGER PRIMARY KEY AUTOINCREMENT, file TEXT NOT NULL, date TEXT, taken_at TEXT, date_source TEXT NOT NULL, pose TEXT NOT NULL DEFAULT '', note TEXT, original_name TEXT, hash TEXT UNIQUE, added_at INTEGER NOT NULL DEFAULT 0)",
            "CREATE INDEX idx_photo_date ON photo(date)",
            "CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)"
        )
        stmts.forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrations run in place so updates keep all existing data.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE measurement ADD COLUMN custom INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE measurement ADD COLUMN link TEXT")
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
