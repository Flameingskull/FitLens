package com.fitlens.companion.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Outcome of an import, export or backup. [folderProblem]: the backup folder couldn't be reached. */
data class ImportSummary(val message: String, val ok: Boolean, val folderProblem: Boolean = false)

/** What a FitNotes import adds to FitLens and what it skips as already present. */
class ImportPlan {
    var categoriesAdded = 0
    var exercisesAdded = 0
    var exercisesSkipped = 0
    var workoutsAdded = 0
    var setsAdded = 0
    var setsSkipped = 0
    var commentsAdded = 0
    var commentsSkipped = 0
    var timesAdded = 0
    var timesSkipped = 0
    var measurementsAdded = 0
    var recordsAdded = 0
    var recordsSkipped = 0

    val nothingNew: Boolean
        get() = categoriesAdded + exercisesAdded + setsAdded + commentsAdded + timesAdded + measurementsAdded + recordsAdded == 0

    /** What the import adds, one line each. */
    fun addedLines(): List<String> = buildList {
        if (setsAdded > 0) add("$setsAdded sets" + if (workoutsAdded > 0) " ($workoutsAdded new workout days)" else " on days already in FitLens")
        if (exercisesAdded > 0) add("$exercisesAdded exercises")
        if (categoriesAdded > 0) add("$categoriesAdded categories")
        if (commentsAdded > 0) add("$commentsAdded workout comments")
        if (timesAdded > 0) add("$timesAdded workout times")
        if (recordsAdded > 0) add("$recordsAdded body tracker records")
        if (measurementsAdded > 0) add("$measurementsAdded measurements")
    }

    /** What the import skips because FitLens already has it (or the user removed it in FitLens). */
    fun skippedLines(): List<String> = buildList {
        if (setsSkipped > 0) add("$setsSkipped sets")
        if (commentsSkipped + timesSkipped > 0) add("${commentsSkipped + timesSkipped} workout comments and times")
        if (recordsSkipped > 0) add("$recordsSkipped body tracker records")
        if (exercisesSkipped > 0) add("$exercisesSkipped exercises you deleted in FitLens")
    }

    fun describe(): String {
        if (nothingNew) return "Everything in it was already in FitLens, so nothing changed."
        val skipped = setsSkipped + commentsSkipped + timesSkipped + recordsSkipped
        return "Added " + addedLines().joinToString(", ") + "." +
            (if (skipped > 0) " $skipped items already in FitLens were skipped." else "")
    }
}

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

    // ---------- FitNotes backups: merged into FitLens, never replacing it ----------

    /** A FitNotes backup copied into FitLens's cache and checked, waiting for the user to confirm the import. */
    class StagedImport(
        val file: File,
        val name: String,
        val modified: Long,
        val plan: ImportPlan,
        /** Application context, so [importStaged] can take the safety copy (#47) before merging. */
        val context: Context? = null
    )

    /** Result of [prepare]: a staged import, or a message saying why the file can't be imported. */
    class Prepared(val staged: StagedImport?, val error: String?)

    private val lock = Mutex()

    /**
     * Copies a FitNotes backup into the cache and works out what importing it would add and skip, without changing
     * anything. Call [importStaged] to import it or [discard] to drop it.
     */
    suspend fun prepare(context: Context, uri: Uri, sourceModified: Long = 0L): Prepared = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "import_${System.nanoTime()}.fitnotes")
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (copied == null) return@withContext Prepared(null, "Couldn't open the file.")
            val header = ByteArray(16)
            tmp.inputStream().use { it.read(header) }
            if (!String(header, Charsets.ISO_8859_1).startsWith("SQLite format 3")) {
                deleteStage(tmp)
                return@withContext Prepared(null, "That file isn't a FitNotes backup (.fitnotes).")
            }
            val plan = lock.withLock { runMerge(tmp, apply = false) }
            Prepared(
                StagedImport(tmp, displayName(context, uri) ?: "backup", sourceModified, plan, context.applicationContext),
                null
            )
        } catch (e: Exception) {
            deleteStage(tmp)
            Prepared(null, "Couldn't read that backup: ${e.message}")
        }
    }

    /** Imports a staged backup (merging it into FitLens) and deletes the staged copy. */
    suspend fun importStaged(staged: StagedImport): ImportSummary = withContext(Dispatchers.IO) {
        try {
            // The way back (#47): a data-only safety copy before the merge. Without one the import doesn't run.
            staged.context?.let { ctx ->
                val safety = Backups.safetyCopy(ctx, "Before importing ${staged.name}")
                if (!safety.ok) return@withContext ImportSummary(
                    safety.message + " The import didn't run; nothing in FitLens was changed.", false
                )
            }
            val plan = lock.withLock { runMerge(staged.file, apply = true) }
            Store.db.setMeta("last_import_name", staged.name)
            Store.db.setMeta("last_import_at", System.currentTimeMillis().toString())
            if (staged.modified > 0) Store.db.setMeta("last_import_modified", staged.modified.toString())
            Store.reload()
            ImportSummary("Imported ${staged.name}. " + plan.describe(), true)
        } catch (e: Exception) {
            ImportSummary("Import failed: ${e.message}. Nothing in FitLens was changed.", false)
        } finally {
            deleteStage(staged.file)
        }
    }

    fun discard(staged: StagedImport) = deleteStage(staged.file)

    private fun deleteStage(f: File) {
        f.delete()
        File(f.path + "-journal").delete()
    }

    /**
     * Imports a FitNotes backup straight away, without the summary step (used by the automatic folder sync).
     * The backup is merged into FitLens as described in [Workouts]: nothing in FitLens is deleted or overwritten.
     */
    suspend fun importBackup(context: Context, uri: Uri, sourceModified: Long = 0L): ImportSummary {
        val p = prepare(context, uri, sourceModified)
        val staged = p.staged ?: return ImportSummary(p.error ?: "Import failed.", false)
        return importStaged(staged)
    }

    /** Opens the staged backup and merges it. With [apply] = false every change is rolled back (a dry run). */
    private fun runMerge(file: File, apply: Boolean): ImportPlan {
        val src = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val w = Store.db.writableDatabase
            w.beginTransaction()
            try {
                val plan = merge(src, w)
                if (apply) w.setTransactionSuccessful()
                return plan
            } finally {
                w.endTransaction()
            }
        } finally {
            src.close()
        }
    }

    private inline fun SQLiteDatabase.each(sql: String, block: (Cursor) -> Unit): Boolean = try {
        rawQuery(sql, null).use { c -> while (c.moveToNext()) block(c) }
        true
    } catch (e: Exception) {
        false // table missing in older FitNotes versions
    }

    /** A category or exercise already in FitLens, for matching. */
    private class Owned(val id: Long, val key: String, val source: String, val fitnotesId: Long?)

    private const val SKIP = -1L

    private fun loadOwned(w: SQLiteDatabase, table: String): MutableList<Owned> {
        val out = ArrayList<Owned>()
        w.rawQuery("SELECT id, name, source, fitnotes_id FROM $table ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                out.add(Owned(c.lng(0), Workouts.nameKey(c.strOr(1)), c.strOr(2, Sources.FITLENS), if (c.isNull(3)) null else c.getLong(3)))
            }
        }
        return out
    }

    /**
     * FitLens id for a FitNotes category or exercise: a rename or delete the user made in FitLens first ([SKIP] when
     * deleted), then the untouched imported row with the same FitNotes id and name, then any row with the same name.
     * Null when nothing matches and a new row is needed.
     */
    private fun resolve(kind: String, fnId: Long, key: String, rows: List<Owned>, links: Map<String, Long?>): Long? {
        val lk = "$kind|$key"
        if (links.containsKey(lk)) {
            val target = links[lk] ?: return SKIP
            if (rows.any { it.id == target }) return target
        }
        rows.firstOrNull { it.source == Sources.FITNOTES && it.fitnotesId == fnId && it.key == key }?.let { return it.id }
        return rows.firstOrNull { it.key == key }?.id
    }

    /**
     * Merges a FitNotes backup into FitLens following the conflict rules in [Workouts]: only adds rows, matches
     * categories and exercises by name, and skips sets, comments, times and body records that are already present.
     */
    private fun merge(src: SQLiteDatabase, w: SQLiteDatabase): ImportPlan {
        val plan = ImportPlan()

        // What the user changed in FitLens (see Workouts, conflict rule 5).
        val links = HashMap<String, Long?>()
        val skips = HashMap<String, Int>()
        w.rawQuery("SELECT kind, key, target_id FROM import_rule ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                val kind = c.strOr(0)
                val k = kind + "|" + c.strOr(1)
                if (kind == Workouts.RULE_CATEGORY || kind == Workouts.RULE_EXERCISE) {
                    links[k] = if (c.isNull(2)) null else c.getLong(2)
                } else {
                    skips[k] = (skips[k] ?: 0) + 1
                }
            }
        }
        fun consumeSkip(kind: String, key: String): Boolean {
            val k = "$kind|$key"
            val n = skips[k] ?: 0
            if (n <= 0) return false
            skips[k] = n - 1
            return true
        }

        // ---------- Categories ----------
        val categories = loadOwned(w, "category")
        val catMap = HashMap<Long, Long>()
        var catOrder = w.rawQuery("SELECT IFNULL(MAX(sort_order), 0) FROM category", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        src.each("SELECT _id, name, colour, sort_order FROM Category ORDER BY sort_order, _id") { c ->
            val fnId = c.lng(0)
            val name = c.strOr(1).trim()
            val key = Workouts.nameKey(name)
            if (key.isEmpty()) return@each
            val found = resolve(Workouts.RULE_CATEGORY, fnId, key, categories, links)
            if (found == null) {
                catOrder++
                val id = w.insertOrThrow("category", null, ContentValues().apply {
                    put("name", name); put("colour", c.int(2)); put("sort_order", catOrder)
                    put("source", Sources.FITNOTES); put("fitnotes_id", fnId)
                })
                categories.add(Owned(id, key, Sources.FITNOTES, fnId))
                catMap[fnId] = id
                plan.categoriesAdded++
            } else {
                catMap[fnId] = found
            }
        }

        // ---------- Exercises ----------
        val exercises = loadOwned(w, "exercise")
        val exMap = HashMap<Long, Long>()
        src.each("SELECT _id, name, category_id, exercise_type_id, notes FROM exercise ORDER BY _id") { c ->
            val fnId = c.lng(0)
            val name = c.strOr(1).trim()
            val key = Workouts.nameKey(name)
            if (key.isEmpty()) return@each
            val found = resolve(Workouts.RULE_EXERCISE, fnId, key, exercises, links)
            if (found == null) {
                val cat = catMap[c.lng(2)]?.takeIf { it != SKIP } ?: Workouts.UNCATEGORISED
                val id = w.insertOrThrow("exercise", null, ContentValues().apply {
                    put("name", name); put("category_id", cat); put("type", c.int(3)); put("notes", c.str(4))
                    put("source", Sources.FITNOTES); put("fitnotes_id", fnId)
                })
                exercises.add(Owned(id, key, Sources.FITNOTES, fnId))
                exMap[fnId] = id
                plan.exercisesAdded++
            } else {
                exMap[fnId] = found
                if (found == SKIP) plan.exercisesSkipped++
            }
        }

        // ---------- Sets ----------
        // Sets already in FitLens, counted by date, exercise and values (identical sets are common, e.g. 3 x 5 x 100 kg).
        val present = HashMap<String, Int>()
        val workoutDates = HashSet<String>()
        w.rawQuery("SELECT exercise_id, date, weight, reps, distance, duration FROM workout_set", null).use { c ->
            while (c.moveToNext()) {
                val k = Workouts.setKey(c.lng(0), c.strOr(1), c.dbl(2), c.int(3), c.dbl(4), c.int(5))
                present[k] = (present[k] ?: 0) + 1
                workoutDates.add(c.strOr(1).take(10))
            }
        }
        val setSqlWithComments = """
            SELECT t._id, t.exercise_id, t.date, t.metric_weight, t.reps, t.distance, t.duration_seconds, t.is_personal_record,
              (SELECT group_concat(cm.comment, ' / ') FROM Comment cm WHERE cm.owner_type_id = 1 AND cm.owner_id = t._id)
            FROM training_log t ORDER BY t.date, t._id
        """.trimIndent()
        val setSqlPlain = "SELECT _id, exercise_id, date, metric_weight, reps, 0, 0, 0, NULL FROM training_log ORDER BY date, _id"
        val mergeSet: (Cursor) -> Unit = mergeSet@{ c ->
            val exId = exMap[c.lng(1)]
            val date = c.strOr(2).take(10)
            if (exId == null || exId == SKIP || Dates.parse(date) == null) {
                plan.setsSkipped++
                return@mergeSet
            }
            val k = Workouts.setKey(exId, date, c.dbl(3), c.int(4), c.dbl(5), c.int(6))
            val n = present[k] ?: 0
            if (n > 0) {
                present[k] = n - 1
                plan.setsSkipped++
                return@mergeSet
            }
            if (consumeSkip(Workouts.RULE_SET, k)) {
                plan.setsSkipped++
                return@mergeSet
            }
            w.insertOrThrow("workout_set", null, ContentValues().apply {
                put("exercise_id", exId); put("date", date)
                put("weight", c.dbl(3)); put("reps", c.int(4)); put("distance", c.dbl(5))
                put("duration", c.int(6)); put("is_pr", c.int(7)); put("comment", c.str(8))
                put("source", Sources.FITNOTES); put("fitnotes_id", c.lng(0))
            })
            plan.setsAdded++
            if (workoutDates.add(date)) plan.workoutsAdded++
        }
        // On FitNotes versions without these columns the first query fails before reading any row.
        if (!src.each(setSqlWithComments, mergeSet)) src.each(setSqlPlain, mergeSet)

        // ---------- Workout comments and times ----------
        val comments = HashSet<String>()
        w.rawQuery("SELECT date, comment FROM workout_comment", null).use { c ->
            while (c.moveToNext()) comments.add(Workouts.commentKey(c.strOr(0), c.strOr(1)))
        }
        src.each("SELECT date, comment FROM WorkoutComment") { c ->
            val date = c.strOr(0).take(10)
            val text = c.strOr(1).trim()
            if (text.isEmpty()) return@each
            val k = Workouts.commentKey(date, text)
            if (k in comments || consumeSkip(Workouts.RULE_COMMENT, k)) {
                plan.commentsSkipped++
                return@each
            }
            w.insertOrThrow("workout_comment", null, ContentValues().apply {
                put("date", date); put("comment", text); put("source", Sources.FITNOTES)
            })
            comments.add(k)
            plan.commentsAdded++
        }
        val times = HashSet<String>()
        w.rawQuery("SELECT date, start, finish FROM workout_time", null).use { c ->
            while (c.moveToNext()) times.add(Workouts.timeKey(c.strOr(0), c.str(1), c.str(2)))
        }
        src.each("SELECT workout_date, start_date_time, end_date_time FROM WorkoutTime") { c ->
            val date = c.strOr(0).take(10)
            val k = Workouts.timeKey(date, c.str(1), c.str(2))
            if (k in times || consumeSkip(Workouts.RULE_TIME, k)) {
                plan.timesSkipped++
                return@each
            }
            w.insertOrThrow("workout_time", null, ContentValues().apply {
                put("date", date); put("start", c.str(1)); put("finish", c.str(2)); put("source", Sources.FITNOTES)
            })
            times.add(k)
            plan.timesAdded++
        }

        // ---------- Measurements ----------
        // Values for a measurement matching a custom metric go into that metric.
        val aliases = customAliases(w)
        fun target(name: String) = aliases[name.trim().lowercase()] ?: name
        val recordKeys = HashSet<String>()
        val manualKeys = HashSet<String>()
        fun rk(name: String, date: String, time: String, value: Double) = name + "|" + date + "|" + time + "|" + fmtNum(value, 3)
        fun mk(name: String, date: String, value: Double) = name + "|" + date + "|" + fmtNum(value, 3)
        w.rawQuery("SELECT name, date, time, value, source FROM mrecord", null).use { c ->
            while (c.moveToNext()) {
                recordKeys.add(rk(c.strOr(0), c.strOr(1), c.strOr(2), c.dbl(3)))
                if (c.strOr(4) == "manual") manualKeys.add(mk(c.strOr(0), c.strOr(1), c.dbl(3)))
            }
        }
        fun addRecord(name: String, unit: String, date: String, time: String, value: Double, comment: String?) {
            if (rk(name, date, time, value) in recordKeys || mk(name, date, value) in manualKeys) {
                plan.recordsSkipped++
                return
            }
            w.insertOrThrow("mrecord", null, ContentValues().apply {
                put("name", name); put("unit", unit); put("date", date); put("time", time)
                put("value", value); put("comment", comment); put("source", "fitnotes")
            })
            recordKeys.add(rk(name, date, time, value))
            plan.recordsAdded++
        }
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
            // Measurement definitions imported from FitNotes (not custom metrics) follow FitNotes: their unit, order
            // and goal are refreshed. Custom metrics made in FitLens are never changed.
            val isCustom = w.rawQuery("SELECT custom FROM measurement WHERE name=?", arrayOf(name)).use { q ->
                if (q.moveToFirst()) q.getInt(0) else null
            }
            val cv = ContentValues().apply {
                put("unit", unit); put("sort_order", c.int(3)); put("goal_type", c.int(4))
                put("goal_value", c.dbl(5)); put("enabled", c.int(6))
            }
            if (isCustom == null) {
                cv.put("name", name)
                w.insertOrThrow("measurement", null, cv)
                plan.measurementsAdded++
            } else if (isCustom == 0) {
                w.update("measurement", cv, "name=?", arrayOf(name))
            }
        }
        src.each("SELECT measurement_id, date, time, value, comment FROM MeasurementRecord") { c ->
            val def = defs[c.lng(0)] ?: return@each
            addRecord(def.first, def.second, c.strOr(1).take(10), c.strOr(2), c.dbl(3), c.str(4))
        }
        // Very old FitNotes versions kept body weight in a separate table.
        src.each("SELECT date, body_weight_metric, body_fat, comments FROM BodyWeight") { c ->
            val date = c.strOr(0).take(10)
            val time = c.strOr(0).drop(11).take(8)
            if (c.dbl(1) > 0) addRecord(target("Bodyweight"), "kgs", date, time, c.dbl(1), c.str(3))
            if (c.dbl(2) > 0) addRecord(target("Body Fat"), "%", date, time, c.dbl(2), null)
        }

        // FitNotes's weight unit is used for display unless the user has picked one in FitLens.
        val unitChosen = w.rawQuery("SELECT 1 FROM meta WHERE k='weight_unit_manual'", null).use { it.moveToFirst() }
        if (!unitChosen) {
            var metric = 1
            src.each("SELECT metric FROM settings LIMIT 1") { c -> metric = c.int(0) }
            w.insertWithOnConflict("meta", null, ContentValues().apply {
                put("k", "weight_unit"); put("v", if (metric == 0) "lbs" else "kg")
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
        return plan
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
