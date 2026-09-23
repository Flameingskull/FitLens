package com.fitlens.companion.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ImportSummary(val message: String, val ok: Boolean)

enum class FileKind { FITNOTES_BACKUP, BODY_CSV, WORKOUT_CSV, IMAGE, ARCHIVE, UNKNOWN }

object FitNotesImporter {

    const val FITNOTES_PACKAGE = "com.github.jamesgay.fitnotes"

    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: uri.lastPathSegment

    /** Work out what a shared/opened file is by looking at its first bytes. */
    fun sniff(context: Context, uri: Uri): FileKind {
        val mime = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        if (mime != null && mime.startsWith("image/")) return FileKind.IMAGE
        val head = ByteArray(64)
        val n = try {
            context.contentResolver.openInputStream(uri)?.use { it.read(head) } ?: -1
        } catch (e: Exception) { -1 }
        if (n <= 0) return FileKind.UNKNOWN
        val text = String(head, 0, n, Charsets.ISO_8859_1)
        return when {
            text.startsWith("SQLite format 3") -> FileKind.FITNOTES_BACKUP
            text.startsWith("PK") -> FileKind.ARCHIVE
            text.startsWith("Date,Time,Measurement") -> FileKind.BODY_CSV
            text.startsWith("Date,Exercise") -> FileKind.WORKOUT_CSV
            (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xFF) == 0xD8 -> FileKind.IMAGE
            text.startsWith("\u0089PNG") -> FileKind.IMAGE
            text.length > 12 && text.substring(4, 8) == "ftyp" -> FileKind.IMAGE // HEIC
            else -> FileKind.UNKNOWN
        }
    }

    /**
     * Imports a FitNotes backup (.fitnotes SQLite file). FitNotes data in FitLens is replaced
     * with the backup contents; photos and manual entries made in FitLens are kept.
     */
    suspend fun importBackup(context: Context, uri: Uri, sourceModified: Long = 0L): ImportSummary =
        withContext(Dispatchers.IO) {
            val tmp = File(context.cacheDir, "import.fitnotes")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                } ?: return@withContext ImportSummary("Couldn't open the file.", false)
                val header = ByteArray(16)
                tmp.inputStream().use { it.read(header) }
                if (!String(header, Charsets.ISO_8859_1).startsWith("SQLite format 3")) {
                    return@withContext ImportSummary("That file isn't a FitNotes backup (.fitnotes).", false)
                }
                val src = SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY)
                val summary = try {
                    copyFrom(src)
                } finally {
                    src.close()
                }
                val name = displayName(context, uri) ?: "backup"
                Store.db.setMeta("last_import_name", name)
                Store.db.setMeta("last_import_at", System.currentTimeMillis().toString())
                if (sourceModified > 0) Store.db.setMeta("last_import_modified", sourceModified.toString())
                Store.reload()
                ImportSummary("Imported $name: $summary", true)
            } catch (e: Exception) {
                ImportSummary("Import failed: ${e.message}", false)
            } finally {
                tmp.delete()
                File(context.cacheDir, "import.fitnotes-journal").delete()
            }
        }

    private inline fun SQLiteDatabase.each(sql: String, block: (Cursor) -> Unit): Boolean = try {
        rawQuery(sql, null).use { c -> while (c.moveToNext()) block(c) }
        true
    } catch (e: Exception) {
        false // table missing in older FitNotes versions
    }

    private fun copyFrom(src: SQLiteDatabase): String {
        val w = Store.db.writableDatabase
        var nSets = 0
        var nRecords = 0
        var nDays = 0
        w.beginTransaction()
        try {
            listOf("category", "exercise", "workout_set", "workout_comment", "workout_time").forEach { w.delete(it, null, null) }
            w.delete("mrecord", "source IN ('fitnotes','csv')", null)

            src.each("SELECT _id, name, colour, sort_order FROM Category") { c ->
                w.insert("category", null, ContentValues().apply {
                    put("id", c.lng(0)); put("name", c.strOr(1)); put("colour", c.int(2)); put("sort_order", c.int(3))
                })
            }
            src.each("SELECT _id, name, category_id, exercise_type_id, notes FROM exercise") { c ->
                w.insert("exercise", null, ContentValues().apply {
                    put("id", c.lng(0)); put("name", c.strOr(1)); put("category_id", c.lng(2))
                    put("type", c.int(3)); put("notes", c.str(4))
                })
            }
            val setSqlWithComments = """
                SELECT t._id, t.exercise_id, t.date, t.metric_weight, t.reps, t.distance, t.duration_seconds, t.is_personal_record,
                  (SELECT group_concat(cm.comment, ' / ') FROM Comment cm WHERE cm.owner_type_id = 1 AND cm.owner_id = t._id)
                FROM training_log t
            """.trimIndent()
            val setSqlPlain = "SELECT _id, exercise_id, date, metric_weight, reps, 0, 0, 0, NULL FROM training_log"
            val insertSet: (Cursor) -> Unit = { c ->
                w.insert("workout_set", null, ContentValues().apply {
                    put("id", c.lng(0)); put("exercise_id", c.lng(1)); put("date", c.strOr(2).take(10))
                    put("weight", c.dbl(3)); put("reps", c.int(4)); put("distance", c.dbl(5))
                    put("duration", c.int(6)); put("is_pr", c.int(7)); put("comment", c.str(8))
                })
                nSets++
            }
            if (!src.each(setSqlWithComments, insertSet)) {
                nSets = 0
                src.each(setSqlPlain, insertSet)
            }

            // Measurements and their records. Values for a measurement matching a custom metric go into that metric.
            val aliases = customAliases(w)
            fun target(name: String) = aliases[name.trim().lowercase()] ?: name
            val defs = HashMap<Long, Pair<String, String>>()
            src.each(
                "SELECT m._id, m.name, IFNULL(u.short_name, ''), m.sort_order, m.goal_type, m.goal_value, m.enabled " +
                    "FROM Measurement m LEFT JOIN MeasurementUnit u ON u._id = m.unit_id"
            ) { c ->
                val name = c.strOr(1)
                val unit = c.strOr(2)
                val custom = aliases[name.trim().lowercase()]
                defs[c.lng(0)] = (custom ?: name) to unit
                if (custom != null) {
                    w.execSQL("UPDATE measurement SET unit=? WHERE name=? AND unit=''", arrayOf(unit, custom))
                    return@each
                }
                w.insertWithOnConflict("measurement", null, ContentValues().apply {
                    put("name", name); put("unit", unit); put("sort_order", c.int(3))
                    put("goal_type", c.int(4)); put("goal_value", c.dbl(5)); put("enabled", c.int(6))
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            src.each("SELECT measurement_id, date, time, value, comment FROM MeasurementRecord") { c ->
                val def = defs[c.lng(0)] ?: return@each
                w.insert("mrecord", null, ContentValues().apply {
                    put("name", def.first); put("unit", def.second); put("date", c.strOr(1).take(10))
                    put("time", c.strOr(2)); put("value", c.dbl(3)); put("comment", c.str(4)); put("source", "fitnotes")
                })
                nRecords++
            }
            // Very old FitNotes versions kept body weight in a separate table.
            src.each("SELECT date, body_weight_metric, body_fat, comments FROM BodyWeight") { c ->
                val date = c.strOr(0).take(10)
                val time = c.strOr(0).drop(11).take(8)
                if (c.dbl(1) > 0) {
                    w.insert("mrecord", null, ContentValues().apply {
                        put("name", target("Bodyweight")); put("unit", "kgs"); put("date", date); put("time", time)
                        put("value", c.dbl(1)); put("comment", c.str(3)); put("source", "fitnotes")
                    })
                    nRecords++
                }
                if (c.dbl(2) > 0) {
                    w.insert("mrecord", null, ContentValues().apply {
                        put("name", target("Body Fat")); put("unit", "%"); put("date", date); put("time", time)
                        put("value", c.dbl(2)); put("source", "fitnotes")
                    })
                    nRecords++
                }
            }

            src.each("SELECT date, comment FROM WorkoutComment") { c ->
                w.insert("workout_comment", null, ContentValues().apply { put("date", c.strOr(0).take(10)); put("comment", c.strOr(1)) })
            }
            src.each("SELECT workout_date, start_date_time, end_date_time FROM WorkoutTime") { c ->
                w.insert("workout_time", null, ContentValues().apply {
                    put("date", c.strOr(0).take(10)); put("start", c.str(1)); put("finish", c.str(2))
                })
            }
            var metric = 1
            src.each("SELECT metric FROM settings LIMIT 1") { c -> metric = c.int(0) }
            w.insertWithOnConflict("meta", null, ContentValues().apply {
                put("k", "weight_unit"); put("v", if (metric == 0) "lbs" else "kg")
            }, SQLiteDatabase.CONFLICT_REPLACE)

            // Manual FitLens entries that now also exist in FitNotes are removed as duplicates.
            w.execSQL(
                "DELETE FROM mrecord WHERE source='manual' AND EXISTS (SELECT 1 FROM mrecord f WHERE f.source='fitnotes' " +
                    "AND f.name = mrecord.name AND f.date = mrecord.date AND abs(f.value - mrecord.value) < 0.001)"
            )
            w.rawQuery("SELECT COUNT(DISTINCT date) FROM workout_set", null).use { c -> c.moveToFirst(); nDays = c.getInt(0) }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        return "$nDays workouts ($nSets sets), $nRecords body tracker records."
    }

    /** Imports a FitNotes Body Tracker CSV export. Rows already present are skipped. */
    suspend fun importBodyCsv(context: Context, uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        try {
            val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }
                ?: return@withContext ImportSummary("Couldn't open the file.", false)
            if (lines.isEmpty() || !lines[0].startsWith("Date,Time,Measurement")) {
                return@withContext ImportSummary("That isn't a FitNotes Body Tracker CSV export.", false)
            }
            val w = Store.db.writableDatabase
            var added = 0
            var skipped = 0
            w.beginTransaction()
            try {
                val aliases = customAliases(w)
                for (line in lines.drop(1)) {
                    if (line.isBlank()) continue
                    val f = parseCsvLine(line)
                    if (f.size < 4) continue
                    val date = f[0].take(10)
                    val time = f[1]
                    val name = aliases[f[2].trim().lowercase()] ?: f[2]
                    val value = f[3].toDoubleOrNull() ?: continue
                    val unit = f.getOrElse(4) { "" }
                    val comment = f.getOrElse(5) { "" }
                    val exists = w.rawQuery(
                        "SELECT 1 FROM mrecord WHERE name=? AND date=? AND abs(value-?) < 0.001 AND (time=? OR source='manual') LIMIT 1",
                        arrayOf(name, date, value.toString(), time)
                    ).use { it.moveToFirst() }
                    if (exists) { skipped++; continue }
                    w.insertWithOnConflict("measurement", null, ContentValues().apply {
                        put("name", name); put("unit", unit); put("sort_order", 999)
                    }, SQLiteDatabase.CONFLICT_IGNORE)
                    w.insert("mrecord", null, ContentValues().apply {
                        put("name", name); put("unit", unit); put("date", date); put("time", time)
                        put("value", value); put("comment", comment); put("source", "csv")
                    })
                    added++
                }
                w.setTransactionSuccessful()
            } finally {
                w.endTransaction()
            }
            Store.reload()
            ImportSummary("CSV: $added new records added, $skipped already present.", true)
        } catch (e: Exception) {
            ImportSummary("CSV import failed: ${e.message}", false)
        }
    }

    /** Lower-case FitNotes measurement name → custom metric that takes its values. */
    private fun customAliases(w: SQLiteDatabase): Map<String, String> {
        val out = HashMap<String, String>()
        w.rawQuery("SELECT name, link FROM measurement WHERE custom=1", null).use { c ->
            while (c.moveToNext()) {
                val name = c.strOr(0)
                val key = (c.str(1)?.takeIf { it.isNotBlank() } ?: name).trim().lowercase()
                out[key] = name
            }
        }
        return out
    }

    fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ } else inQuotes = false
                } else sb.append(ch)
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> { out.add(sb.toString()); sb.setLength(0) }
                    else -> sb.append(ch)
                }
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
