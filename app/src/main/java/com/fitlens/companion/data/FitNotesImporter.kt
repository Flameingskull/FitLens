package com.fitlens.companion.data

import android.content.Context
import android.content.res.Resources
import com.fitlens.companion.R
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.database.sqlite.SQLiteDatabase
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
    fun addedLines(res: Resources): List<String> = buildList {
        fun count(id: Int, n: Int) { if (n > 0) add(res.getQuantityString(id, n, n)) }
        if (setsAdded > 0) {
            val sets = res.getQuantityString(R.plurals.imp_sets, setsAdded, setsAdded)
            add(
                if (workoutsAdded > 0) res.getString(
                    R.string.imp_sets_with_days, sets, res.getQuantityString(R.plurals.imp_new_days, workoutsAdded, workoutsAdded)
                )
                else res.getString(R.string.imp_sets_existing_days, sets)
            )
        }
        count(R.plurals.imp_exercises, exercisesAdded)
        count(R.plurals.imp_categories, categoriesAdded)
        count(R.plurals.imp_comments, commentsAdded)
        count(R.plurals.imp_times, timesAdded)
        count(R.plurals.imp_records, recordsAdded)
        count(R.plurals.imp_measurements, measurementsAdded)
    }

    /** What the import skips because FitLens already has it (or the user removed it in FitLens). */
    fun skippedLines(res: Resources): List<String> = buildList {
        fun count(id: Int, n: Int) { if (n > 0) add(res.getQuantityString(id, n, n)) }
        count(R.plurals.imp_sets, setsSkipped)
        count(R.plurals.imp_comments_times, commentsSkipped + timesSkipped)
        count(R.plurals.imp_records, recordsSkipped)
        count(R.plurals.imp_exercises_deleted, exercisesSkipped)
    }

    fun describe(res: Resources): String {
        if (nothingNew) return res.getString(R.string.imp_nothing_new)
        val skipped = setsSkipped + commentsSkipped + timesSkipped + recordsSkipped
        val added = res.getString(R.string.imp_added, addedLines(res).joinToString(", "))
        return if (skipped > 0) added + " " + res.getQuantityString(R.plurals.imp_items_skipped, skipped, skipped) else added
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
        /** Application context, so [importStaged] can take the safety copy (#47) before merging and word its result. */
        val context: Context
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
            if (copied == null) return@withContext Prepared(null, context.getString(R.string.imp_cant_open))
            val header = ByteArray(16)
            tmp.inputStream().use { it.read(header) }
            if (!String(header, Charsets.ISO_8859_1).startsWith("SQLite format 3")) {
                deleteStage(tmp)
                return@withContext Prepared(null, context.getString(R.string.imp_not_fitnotes))
            }
            val plan = lock.withLock { runMerge(tmp, apply = false) }
            Prepared(
                StagedImport(
                    tmp, displayName(context, uri) ?: context.getString(R.string.imp_backup_name), sourceModified, plan,
                    context.applicationContext
                ),
                null
            )
        } catch (e: Exception) {
            deleteStage(tmp)
            Prepared(null, context.getString(R.string.imp_cant_read, e.message ?: e.javaClass.simpleName))
        }
    }

    /** Imports a staged backup (merging it into FitLens) and deletes the staged copy. */
    suspend fun importStaged(staged: StagedImport): ImportSummary = withContext(Dispatchers.IO) {
        val ctx = staged.context
        try {
            // The way back (#47): a data-only safety copy before the merge. Without one the import doesn't run.
            val safety = Backups.safetyCopy(ctx, ctx.getString(R.string.imp_safety_reason, staged.name))
            if (!safety.ok) return@withContext ImportSummary(ctx.getString(R.string.imp_safety_failed, safety.message), false)
            val plan = lock.withLock { runMerge(staged.file, apply = true) }
            Settings.updateDeviceNow {
                it.copy(
                    lastImportName = staged.name,
                    lastImportAt = System.currentTimeMillis(),
                    lastImportModified = if (staged.modified > 0) staged.modified else it.lastImportModified
                )
            }
            Store.reload()
            ImportSummary(ctx.getString(R.string.imp_done, staged.name, plan.describe(ctx.resources)), true)
        } catch (e: Exception) {
            ImportSummary(ctx.getString(R.string.imp_failed, e.message ?: e.javaClass.simpleName), false)
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
        val staged = p.staged ?: return ImportSummary(p.error ?: context.getString(R.string.imp_failed_short), false)
        return importStaged(staged)
    }

    /** Rolls back a dry run's transaction, carrying the plan it worked out. */
    private class DryRun(val plan: ImportPlan) : RuntimeException()

    /**
     * Opens the staged backup and merges it into [db] in one transaction. With [apply] = false every change is rolled
     * back (a dry run), so the plan is exactly what a real import would do.
     */
    internal fun runMerge(file: File, apply: Boolean, db: Db = Store.db): ImportPlan {
        val src = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            return try {
                db.transaction {
                    val plan = merge(src, db)
                    if (!apply) throw DryRun(plan)
                    plan
                }
            } catch (dry: DryRun) {
                dry.plan
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

    private fun owned(rows: List<LibraryOrigin>): MutableList<Owned> =
        rows.mapTo(ArrayList()) { Owned(it.id, Workouts.nameKey(it.name), it.source, it.fitnotes_id) }

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
     * The caller runs it inside one transaction on [db], off the main thread.
     */
    internal fun merge(src: SQLiteDatabase, db: Db): ImportPlan {
        val plan = ImportPlan()
        val w = db.importDao
        val workouts = db.workoutDao
        val all = db.snapshotDao

        // What the user changed in FitLens (see Workouts, conflict rule 5).
        val links = HashMap<String, Long?>()
        val skips = HashMap<String, Int>()
        for (rule in w.rules()) {
            val k = rule.kind + "|" + rule.key
            if (rule.kind == Workouts.RULE_CATEGORY || rule.kind == Workouts.RULE_EXERCISE) {
                links[k] = rule.target_id
            } else {
                skips[k] = (skips[k] ?: 0) + 1
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
        val categories = owned(w.categories())
        val catMap = HashMap<Long, Long>()
        var catOrder = workouts.lastCategoryOrder()
        src.each("SELECT _id, name, colour, sort_order FROM Category ORDER BY sort_order, _id") { c ->
            val fnId = c.lng(0)
            val name = c.strOr(1).trim()
            val key = Workouts.nameKey(name)
            if (key.isEmpty()) return@each
            val found = resolve(Workouts.RULE_CATEGORY, fnId, key, categories, links)
            if (found == null) {
                catOrder++
                val id = w.addCategory(name, c.int(2), catOrder, Sources.FITNOTES, fnId)
                categories.add(Owned(id, key, Sources.FITNOTES, fnId))
                catMap[fnId] = id
                plan.categoriesAdded++
            } else {
                catMap[fnId] = found
            }
        }

        // ---------- Exercises ----------
        val exercises = owned(w.exercises())
        val exMap = HashMap<Long, Long>()
        src.each("SELECT _id, name, category_id, exercise_type_id, notes FROM exercise ORDER BY _id") { c ->
            val fnId = c.lng(0)
            val name = c.strOr(1).trim()
            val key = Workouts.nameKey(name)
            if (key.isEmpty()) return@each
            val found = resolve(Workouts.RULE_EXERCISE, fnId, key, exercises, links)
            if (found == null) {
                val cat = catMap[c.lng(2)]?.takeIf { it != SKIP } ?: Workouts.UNCATEGORISED
                val id = w.addExercise(name, cat, c.int(3), c.str(4), Sources.FITNOTES, fnId)
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
        for (s in w.setValues()) {
            val k = Workouts.setKey(s.exercise_id, s.date, s.weight, s.reps, s.distance, s.duration.toInt())
            present[k] = (present[k] ?: 0) + 1
            workoutDates.add(s.date.take(10))
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
            w.addSet(exId, date, c.dbl(3), c.int(4), c.dbl(5), c.int(6), c.int(7), c.str(8), Sources.FITNOTES, c.lng(0))
            plan.setsAdded++
            if (workoutDates.add(date)) plan.workoutsAdded++
        }
        // On FitNotes versions without these columns the first query fails before reading any row.
        if (!src.each(setSqlWithComments, mergeSet)) src.each(setSqlPlain, mergeSet)

        // ---------- Workout comments and times ----------
        val comments = HashSet<String>()
        for (row in all.workoutComments()) comments.add(Workouts.commentKey(row.date, row.comment))
        src.each("SELECT date, comment FROM WorkoutComment") { c ->
            val date = c.strOr(0).take(10)
            val text = c.strOr(1).trim()
            if (text.isEmpty()) return@each
            val k = Workouts.commentKey(date, text)
            if (k in comments || consumeSkip(Workouts.RULE_COMMENT, k)) {
                plan.commentsSkipped++
                return@each
            }
            workouts.addComment(date, text, Sources.FITNOTES)
            comments.add(k)
            plan.commentsAdded++
        }
        val times = HashSet<String>()
        for (row in all.workoutTimes()) times.add(Workouts.timeKey(row.date, row.start, row.finish))
        src.each("SELECT workout_date, start_date_time, end_date_time FROM WorkoutTime") { c ->
            val date = c.strOr(0).take(10)
            val k = Workouts.timeKey(date, c.str(1), c.str(2))
            if (k in times || consumeSkip(Workouts.RULE_TIME, k)) {
                plan.timesSkipped++
                return@each
            }
            workouts.addTime(date, c.str(1), c.str(2), Sources.FITNOTES)
            times.add(k)
            plan.timesAdded++
        }

        // ---------- Measurements ----------
        // Values for a measurement matching a custom metric go into that metric.
        val aliases = customAliases(w)
        val body = db.bodyDao
        fun target(name: String) = aliases[name.trim().lowercase()] ?: name
        val recordKeys = HashSet<String>()
        val manualKeys = HashSet<String>()
        fun rk(name: String, date: String, time: String, value: Double) = name + "|" + date + "|" + time + "|" + fmtNum(value, 3)
        fun mk(name: String, date: String, value: Double) = name + "|" + date + "|" + fmtNum(value, 3)
        for (r in all.records()) {
            recordKeys.add(rk(r.name, r.date, r.time, r.value))
            if (r.source == "manual") manualKeys.add(mk(r.name, r.date, r.value))
        }
        fun addRecord(name: String, unit: String, date: String, time: String, value: Double, comment: String?) {
            if (rk(name, date, time, value) in recordKeys || mk(name, date, value) in manualKeys) {
                plan.recordsSkipped++
                return
            }
            w.addRecord(name, unit, date, time, value, comment, "fitnotes")
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
                w.setUnitIfNone(custom, unit)
                return@each
            }
            // Measurement definitions imported from FitNotes (not custom metrics) follow FitNotes: their unit, order
            // and goal are refreshed. Custom metrics made in FitLens are never changed.
            val existing = body.definition(name)
            if (existing == null) {
                w.addMeasurement(name, unit, c.int(3), c.int(4), c.dbl(5), c.int(6))
                plan.measurementsAdded++
            } else if (existing.custom == 0) {
                // A goal or order the user set in FitLens wins over FitNotes's (#27); the unit still follows FitNotes.
                // So does switching it on or off on the Measurements screen.
                if (existing.edited != 0) w.setMeasurementUnit(name, unit)
                else w.refreshMeasurement(name, unit, c.int(3), c.int(4), c.dbl(5), c.int(6))
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
        if (!db.metaDao.has("weight_unit_manual")) {
            var metric = 1
            src.each("SELECT metric FROM settings LIMIT 1") { c -> metric = c.int(0) }
            db.metaDao.put(MetaRow("weight_unit", if (metric == 0) "lbs" else "kg"))
        }
        return plan
    }

    /** Imports a FitNotes Body Tracker CSV export. Rows already present are skipped. */
    suspend fun importBodyCsv(context: Context, uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        try {
            val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }
                ?: return@withContext ImportSummary(context.getString(R.string.imp_cant_open), false)
            if (lines.isEmpty() || !lines[0].startsWith("Date,Time,Measurement")) {
                return@withContext ImportSummary(context.getString(R.string.imp_csv_not_body), false)
            }
            val (added, skipped) = mergeBodyCsv(lines, Store.db)
            Store.reload()
            val res = context.resources
            ImportSummary(
                res.getString(
                    R.string.imp_csv_done,
                    res.getQuantityString(R.plurals.imp_csv_added, added, added),
                    res.getQuantityString(R.plurals.imp_csv_present, skipped, skipped)
                ),
                true
            )
        } catch (e: Exception) {
            ImportSummary(context.getString(R.string.imp_csv_failed, e.message ?: e.javaClass.simpleName), false)

        }
    }

    /**
     * Adds the values of a Body Tracker CSV ([lines], header first) to [db] in one transaction, skipping those already
     * there. Returns how many were added and how many skipped. Off the main thread.
     */
    internal fun mergeBodyCsv(lines: List<String>, db: Db): Pair<Int, Int> = db.transaction {
        val w = db.importDao
        var added = 0
        var skipped = 0
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
            if (w.hasRecord(name, date, time, value)) { skipped++; continue }
            db.bodyDao.ensure(name, unit, 999)
            w.addRecord(name, unit, date, time, value, comment, "csv")
            added++
        }
        added to skipped
    }

    /** Lower-case FitNotes measurement name → custom metric that takes its values. */
    private fun customAliases(w: ImportDao): Map<String, String> {
        val out = HashMap<String, String>()
        for (m in w.customMetrics()) {
            val key = (m.link?.takeIf { it.isNotBlank() } ?: m.name).trim().lowercase()
            out[key] = m.name
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
