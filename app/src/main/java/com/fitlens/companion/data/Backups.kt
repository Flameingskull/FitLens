package com.fitlens.companion.data

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.Formatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local backups: one .fitlens file (a zip holding manifest.json, the database and every photo) that restores
 * everything on this or another phone. No accounts and no network. Files go where the user chooses.
 * Older FitLens archives (.zip without a manifest) can still be restored.
 */
object Backups {

    const val FORMAT = 1
    const val EXTENSION = "fitlens"
    /** Generic type so storage providers keep the .fitlens name instead of appending .zip. */
    const val MIME = "application/octet-stream"
    private const val AUTO_PREFIX = "FitLens_auto_"
    private const val PART = ".part"

    /** What a backup file contains, shown before restoring. */
    data class Info(
        val createdAt: String?,
        val appVersion: String?,
        val dbVersion: Int,
        val photos: Int,
        val workouts: Int?,
        val records: Int?,
        val firstDate: String?,
        val lastDate: String?,
        val legacy: Boolean
    )

    private val lock = Mutex()

    fun fileName(prefix: String = "FitLens_backup_"): String =
        prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + ".$EXTENSION"

    // ---------- Writing ----------

    private fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /**
     * Writes a complete backup to [os]. Returns the number of photos written.
     *
     * [includePhotos] is false only for the safety copy taken before a merge import (#47), which cannot touch
     * photos: leaving the photo library out keeps that copy small enough to take before every import. Such an
     * archive is marked `dataOnly` in its manifest so [restoreFrom] puts the database back without emptying the
     * photo folder. Backups the user saves always include everything.
     */
    private fun write(context: Context, os: OutputStream, includePhotos: Boolean = true): Int {
        val dbFile = context.getDatabasePath(Db.NAME)
        Store.db.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        val snap = Store.snapshot.value
        val photos = if (!includePhotos) emptyList()
        else Store.photoDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: emptyList()
        val manifest = JSONObject().apply {
            put("format", FORMAT)
            put("app", "FitLens")
            put("appVersion", appVersion(context))
            put("dbVersion", Db.VERSION)
            put("createdAt", LocalDateTime.now().toString())
            put("photos", photos.size)
            if (!includePhotos) put("dataOnly", true)
            if (snap != null) {
                put("workouts", snap.setsByDate.size)
                put("records", snap.records.size)
                snap.allDates.lastOrNull()?.let { put("firstDate", it) }
                snap.allDates.firstOrNull()?.let { put("lastDate", it) }
            }
        }
        ZipOutputStream(os.buffered()).use { zip ->
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("fitlens.db"))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            // Photos are already compressed (JPEG etc.), so store them without recompressing.
            zip.setLevel(Deflater.NO_COMPRESSION)
            photos.forEach { f ->
                zip.putNextEntry(ZipEntry("photos/" + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return photos.size
    }

    suspend fun export(context: Context, dest: Uri): ImportSummary = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                val n = context.contentResolver.openOutputStream(dest, "wt")?.use { write(context, it) }
                    ?: return@withContext ImportSummary("Couldn't write the backup file.", false)
                ImportSummary("Backup saved with $n photos.", true)
            } catch (e: Exception) {
                ImportSummary("Backup failed: ${e.message}", false)
            }
        }
    }

    // ---------- Inspecting and restoring ----------

    /** Reads a backup's summary without changing anything. Returns null if it isn't a FitLens backup. */
    suspend fun inspect(context: Context, src: Uri): Info? = withContext(Dispatchers.IO) {
        try {
            var manifest: JSONObject? = null
            var hasDb = false
            var photos = 0
            context.contentResolver.openInputStream(src)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        when {
                            e.name == "manifest.json" -> manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                            e.name == "fitlens.db" -> hasDb = true
                            e.name.startsWith("photos/") && !e.isDirectory -> photos++
                        }
                    }
                }
            } ?: return@withContext null
            if (!hasDb) return@withContext null
            val m = manifest
            Info(
                createdAt = m?.optString("createdAt")?.takeIf { it.isNotBlank() },
                appVersion = m?.optString("appVersion")?.takeIf { it.isNotBlank() },
                dbVersion = m?.optInt("dbVersion", 1) ?: 1,
                photos = photos,
                workouts = m?.takeIf { it.has("workouts") }?.optInt("workouts"),
                records = m?.takeIf { it.has("records") }?.optInt("records"),
                firstDate = m?.optString("firstDate")?.takeIf { it.isNotBlank() },
                lastDate = m?.optString("lastDate")?.takeIf { it.isNotBlank() },
                legacy = m == null
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Replaces all FitLens data with the backup. Everything is unpacked and checked in a staging folder first,
     * so a damaged or incompatible file is rejected before anything on the phone is touched.
     *
     * Once the staged database is copied over the live one the change can't be undone from the backup file, so
     * a safety copy of the current data is written first (#47) and failures are reported differently on each side
     * of that point: before it the current data really is untouched, after it the backup has already taken the
     * place of the old data and the message has to say so, and point at Undo.
     */
    suspend fun restore(context: Context, src: Uri): ImportSummary =
        restoreFrom(context, "Before restoring a backup") { context.contentResolver.openInputStream(src) }

    /**
     * The restore itself. [safetyReason] names what the copy is being taken before; null means the caller has
     * already dealt with the way back, which is the case for [undoLastRestore] replaying the copy itself.
     */
    private suspend fun restoreFrom(
        context: Context,
        safetyReason: String?,
        open: () -> InputStream?
    ): ImportSummary = withContext(Dispatchers.IO) {
        lock.withLock {
            val stage = File(context.cacheDir, "restore_stage").apply { deleteRecursively(); mkdirs() }
            val stagePhotos = File(stage, "photos").apply { mkdirs() }
            val stageDb = File(stage, "fitlens.db")
            // Set the moment the live database is about to be closed and overwritten: from here on the old data is gone.
            var replacing = false
            try {
                var photos = 0
                // A safety copy taken before a merge import holds no photos and must not empty the photo folder.
                var dataOnly = false
                open()?.use { input ->
                    ZipInputStream(input.buffered()).use { zip ->
                        while (true) {
                            val e = zip.nextEntry ?: break
                            when {
                                e.name == "manifest.json" ->
                                    dataOnly = runCatching {
                                        JSONObject(zip.readBytes().toString(Charsets.UTF_8)).optBoolean("dataOnly", false)
                                    }.getOrDefault(false)
                                e.name == "fitlens.db" -> stageDb.outputStream().use { zip.copyTo(it) }
                                e.name.startsWith("photos/") && !e.isDirectory -> {
                                    val name = File(e.name).name
                                    if (name.isNotBlank() && name != "." && name != "..") {
                                        File(stagePhotos, name).outputStream().use { zip.copyTo(it) }
                                        photos++
                                    }
                                }
                            }
                        }
                    }
                } ?: return@withContext ImportSummary("Couldn't open the backup file.", false)
                if (!stageDb.exists()) return@withContext ImportSummary("That isn't a FitLens backup.", false)

                // Check the database before touching anything.
                val version = SQLiteDatabase.openDatabase(stageDb.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                        buildSet { while (c.moveToNext()) add(c.getString(0)) }
                    }
                    if (!tables.containsAll(listOf("photo", "mrecord", "measurement", "workout_set"))) {
                        return@withContext ImportSummary("That backup is damaged or isn't from FitLens.", false)
                    }
                    db.version
                }
                if (version > Db.VERSION) {
                    return@withContext ImportSummary("That backup was made by a newer FitLens. Update FitLens first, then restore.", false)
                }

                // The way back (#47), written while the current data is still all there. If it can't be made, the
                // restore doesn't start: replacing everything with no way back is exactly what this prevents.
                if (safetyReason != null) {
                    val problem = writeSafetyCopy(context, safetyReason, includePhotos = true)
                    if (problem != null) return@withContext ImportSummary(problem, false)
                }

                // Settings that belong to this phone (folders, schedules, the safety copy, the last result) live
                // in DataStore, outside the database, so the restore can't replace them (#38). Phone-only rows an
                // older backup still carries in `meta` are ignored.

                replacing = true
                Store.db.close()
                val dbFile = context.getDatabasePath(Db.NAME)
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
                File(dbFile.path + "-journal").delete()
                stageDb.copyTo(dbFile, overwrite = true)

                if (!dataOnly) {
                    val photoDir = Store.photoDir
                    val old = File(photoDir.parentFile, "photos_old").apply { deleteRecursively() }
                    photoDir.renameTo(old)
                    if (!stagePhotos.renameTo(photoDir)) {
                        photoDir.mkdirs()
                        stagePhotos.listFiles()?.forEach { it.copyTo(File(photoDir, it.name), overwrite = true) }
                    }
                    old.deleteRecursively()
                }

                Store.init(context)
                // An older backup's phone-only rows are never used; clear them rather than carry them on (#98).
                runCatching { Settings.dropLegacyDeviceRows() }
                Store.reload()
                if (dataOnly) ImportSummary("Your previous workouts, measurements and notes are back.", true)
                else ImportSummary("Backup restored with $photos photos.", true)
            } catch (e: Exception) {
                if (replacing) {
                    // The database was already swapped. Reopen it so the app is never left holding a closed
                    // database, then tell the user honestly that their previous data has gone.
                    try {
                        Store.init(context)
                        Store.reload()
                    } catch (reopen: Exception) {
                        // Nothing further can be done here; the message below tells the user what to do next.
                    }
                    val wayBack = if (runCatching { undoAvailable(context) }.getOrDefault(false)) {
                        "The safety copy taken just before this restore is still here: use Undo in Settings → Backups " +
                            "to put your previous data back."
                    } else {
                        "Check what's there before adding anything new; restoring again is safe."
                    }
                    ImportSummary(
                        "Restore failed part-way: ${e.message}. Your previous data has already been replaced by " +
                            "this backup. $wayBack",
                        false
                    )
                } else {
                    ImportSummary("Restore failed: ${e.message}. Your current data wasn't changed.", false)
                }
            } finally {
                stage.deleteRecursively()
            }
        }
    }

    // ---------- The safety copy: the way back from a restore or an import (#47) ----------

    /**
     * Before anything replaces or merges into the user's data, FitLens writes a `.fitlens` archive of what is
     * there now to `filesDir/safety/`, and [undoLastRestore] puts it back through the normal restore path, so
     * database migrations still run and this phone's own settings survive.
     *
     * The folder sits in app-private storage, outside `photos/`, so a `.fitlens` backup never picks it up, and
     * `res/xml/data_extraction_rules.xml` keeps it out of Android's cloud backup and device transfer.
     */
    const val UNDO_DAYS = 7
    private const val SAFETY_DIR = "safety"
    private const val SAFETY_FILE = "safety_copy.$EXTENSION"

    private fun safetyDir(context: Context): File = File(context.filesDir, SAFETY_DIR).apply { mkdirs() }

    private fun safetyFile(context: Context): File = File(safetyDir(context), SAFETY_FILE)

    /** When the safety copy was taken, or null when there isn't one to go back to. */
    fun undoAt(): Long? = Settings.current().safetyAt

    /** What the safety copy was taken before, e.g. "Before restoring a backup". */
    fun undoReason(): String? = Settings.current().safetyReason

    /** The record and the file have to agree: a record without a file would offer an Undo that can't happen. */
    fun undoAvailable(context: Context): Boolean = undoAt() != null && safetyFile(context).exists()

    fun undoExpiresAt(): Long? = undoAt()?.let { it + UNDO_DAYS * 24L * 3600_000L }

    /** Roughly how much room a safety copy needs. Photos are stored without recompressing, so they count in full. */
    suspend fun safetyCopySize(context: Context, includePhotos: Boolean = true): Long = withContext(Dispatchers.IO) {
        estimateSafetySize(context, includePhotos)
    }

    private fun estimateSafetySize(context: Context, includePhotos: Boolean): Long {
        val db = context.getDatabasePath(Db.NAME).let { it.length() + File(it.path + "-wal").length() }
        val photos = if (!includePhotos) 0L
        else Store.photoDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
        return db + photos
    }

    /**
     * Writes a safety copy of the current data for any bulk change that isn't already inside [restore].
     * A failed summary means there is no way back, so the caller must not go ahead.
     */
    suspend fun safetyCopy(context: Context, reason: String, includePhotos: Boolean = false): ImportSummary =
        withContext(Dispatchers.IO) {
            val problem = lock.withLock { writeSafetyCopy(context, reason, includePhotos) }
            if (problem == null) ImportSummary("Safety copy saved.", true) else ImportSummary(problem, false)
        }

    /**
     * Writes the copy and records it. Returns null when it worked, or a message explaining why it couldn't be
     * made. The caller must already hold [lock]; this does not take it, so it can be used from inside a restore.
     */
    private fun writeSafetyCopy(context: Context, reason: String, includePhotos: Boolean): String? {
        val dir = safetyDir(context)
        val need = estimateSafetySize(context, includePhotos)
        val room = need + need / 10 + 2L * 1024 * 1024
        val free = runCatching { dir.usableSpace }.getOrDefault(0L)
        if (free > 0 && free < room) {
            return "There isn't room on this phone for a safety copy of your current data: about " +
                Formatter.formatShortFileSize(context, room) + " is needed and " +
                Formatter.formatShortFileSize(context, free) + " is free. Free some space and try again."
        }
        val part = File(dir, SAFETY_FILE + PART)
        val dest = safetyFile(context)
        val previous = File(dir, "$SAFETY_FILE.old")
        return try {
            part.delete()
            part.outputStream().use { write(context, it, includePhotos) }
            // The copy already there is only let go once the new one is complete and in place.
            previous.delete()
            if (dest.exists()) dest.renameTo(previous)
            if (!part.renameTo(dest)) {
                if (previous.exists()) previous.renameTo(dest)
                part.delete()
                "Couldn't finish the safety copy of your current data."
            } else {
                previous.delete()
                Settings.updateDevice { it.copy(safetyAt = System.currentTimeMillis(), safetyReason = reason) }
                null
            }
        } catch (e: Exception) {
            part.delete()
            "Couldn't save a safety copy of your current data: ${e.message}"
        }
    }

    private fun clearUndo() = Settings.updateDevice { it.copy(safetyAt = null, safetyReason = null) }

    /**
     * Keeps exactly one safety copy, and only for [UNDO_DAYS]. Called when the app starts, so an archive of a
     * large photo library never sits in app storage indefinitely.
     */
    suspend fun pruneSafety(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, SAFETY_DIR)
                val at = undoAt()
                val expired = at == null || System.currentTimeMillis() - at > UNDO_DAYS * 24L * 3600_000L
                dir.listFiles()?.forEach { if (expired || it.name != SAFETY_FILE) it.delete() }
                // A record with no file, or a file with no record, would both be lies. Clear either.
                if (expired || !safetyFile(context).exists()) clearUndo()
            }
        }
    }

    /**
     * Puts the safety copy back. It goes through the normal restore path, so `onUpgrade` runs if the copy was made
     * against an older database version, and this phone's folder settings are kept.
     */
    suspend fun undoLastRestore(context: Context): ImportSummary = withContext(Dispatchers.IO) {
        val file = safetyFile(context)
        val at = undoAt()
        if (at == null || !file.exists()) {
            ImportSummary("There's no safety copy to go back to.", false)
        } else {
            // Moved out of the safety folder before the restore runs, so the file being read is never the one the
            // restore is rewriting around.
            val working = File(context.cacheDir, "undo_$SAFETY_FILE")
            working.delete()
            val moved = file.renameTo(working) ||
                runCatching { file.copyTo(working, overwrite = true); true }.getOrDefault(false)
            if (!moved) {
                ImportSummary("Couldn't read the safety copy.", false)
            } else {
                val reason = undoReason()
                clearUndo()
                val r = restoreFrom(context, safetyReason = null) { working.inputStream() }
                if (r.ok) {
                    working.delete()
                    r
                } else {
                    // Put the copy back, with its original date, so the user can try again.
                    runCatching {
                        if (working.renameTo(safetyFile(context))) {
                            Settings.updateDevice { it.copy(safetyAt = at, safetyReason = reason) }
                        }
                    }
                    r
                }
            }
        }
    }

    // ---------- Automatic backups to a folder the user chooses ----------

    fun autoFolder(): Uri? = Settings.current().autoBackupFolder?.let { Uri.parse(it) }
    /** 0 = off, 1 = daily, 7 = weekly. */
    fun autoDays(): Int = Settings.current().autoBackupDays
    fun autoKeep(): Int = Settings.current().autoBackupKeep
    fun lastAutoBackup(): Long? = Settings.current().autoBackupLast
    fun setAutoDays(days: Int) = Settings.updateDevice { it.copy(autoBackupDays = days) }
    fun setAutoKeep(n: Int) = Settings.updateDevice { it.copy(autoBackupKeep = n) }

    /** The last automatic backup that failed since the last one that worked: time and message. */
    fun lastError(): Pair<Long, String>? = parseError(Settings.current().autoBackupError)

    /** Reads a stored "time|message" failure. Public so screens can show it from [Settings.device] live. */
    fun parseError(v: String?): Pair<Long, String>? {
        if (v == null) return null
        val t = v.substringBefore('|').toLongOrNull() ?: return null
        return t to v.substringAfter('|')
    }

    private suspend fun failed(message: String, folderProblem: Boolean): ImportSummary {
        Settings.updateDeviceNow { it.copy(autoBackupError = "${System.currentTimeMillis()}|$message") }
        return ImportSummary(message, false, folderProblem)
    }

    fun setAutoFolder(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Some providers don't support persisted permissions; the next backup will report it.
        }
        Settings.updateDevice { it.copy(autoBackupFolder = uri.toString(), autoBackupDays = it.autoBackupDays.takeIf { d -> d > 0 } ?: 1) }
    }

    /** Runs an automatic backup when one is due. Returns a message, or null when nothing was due. */
    suspend fun autoBackupIfDue(context: Context): ImportSummary? {
        val days = autoDays()
        if (days <= 0 || autoFolder() == null) return null
        val snap = Store.snapshot.value ?: return null
        if (snap.photos.isEmpty() && snap.records.isEmpty() && snap.sets.isEmpty()) return null
        val last = lastAutoBackup() ?: 0L
        val dueAfter = days * 24L * 3600_000L - 3600_000L // an hour's grace so daily backups don't drift
        if (System.currentTimeMillis() - last < dueAfter) return null
        return backupToFolder(context)
    }

    /** Writes a backup into the automatic-backup folder and keeps only the newest [autoKeep] files. */
    suspend fun backupToFolder(context: Context): ImportSummary = withContext(Dispatchers.IO) {
        val tree = autoFolder() ?: return@withContext ImportSummary("Choose a backup folder first.", false)
        lock.withLock {
            try {
                val resolver = context.contentResolver
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val name = fileName(AUTO_PREFIX)
                // Written under a temporary name, then renamed, so an interrupted backup is never mistaken for a good one.
                val doc = DocumentsContract.createDocument(resolver, parent, MIME, name + PART)
                    ?: return@withContext failed(FOLDER_MISSING, true)
                val photos = resolver.openOutputStream(doc, "wt")?.use { write(context, it) }
                    ?: return@withContext failed("Couldn't write to the backup folder.", true)
                DocumentsContract.renameDocument(resolver, doc, name)
                Settings.updateDeviceNow { it.copy(autoBackupLast = System.currentTimeMillis(), autoBackupError = null) }
                AutoBackup.markBackedUp()
                prune(context, tree)
                ImportSummary("Automatic backup saved ($photos photos).", true)
            } catch (e: SecurityException) {
                failed("FitLens lost access to the backup folder. Choose it again in Settings → Backups.", true)
            } catch (e: java.io.FileNotFoundException) {
                failed(FOLDER_MISSING, true)
            } catch (e: IllegalArgumentException) {
                failed(FOLDER_MISSING, true)
            } catch (e: Exception) {
                failed("Automatic backup failed: ${e.message}", false)
            }
        }
    }

    private const val FOLDER_MISSING =
        "The backup folder isn't available. If it's on an SD card, check the card is in; otherwise choose the folder again in Settings → Backups."

    private fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = ArrayList<Pair<String, Uri>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(AUTO_PREFIX)) found.add(name to DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)))
            }
        }
        // Leftovers from interrupted backups
        found.filter { it.first.endsWith(PART) }.forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.second) } }
        found.filter { it.first.endsWith(".$EXTENSION") }
            .sortedByDescending { it.first } // names contain the timestamp
            .drop(autoKeep())
            .forEach { runCatching { DocumentsContract.deleteDocument(resolver, it.second) } }
    }
}
