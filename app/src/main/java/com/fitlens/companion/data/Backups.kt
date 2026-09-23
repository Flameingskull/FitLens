package com.fitlens.companion.data

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
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

    /** Writes a complete backup to [os]. Returns the number of photos written. */
    private fun write(context: Context, os: OutputStream): Int {
        val dbFile = context.getDatabasePath(Db.NAME)
        Store.db.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        val snap = Store.snapshot.value
        val photos = Store.photoDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: emptyList()
        val manifest = JSONObject().apply {
            put("format", FORMAT)
            put("app", "FitLens")
            put("appVersion", appVersion(context))
            put("dbVersion", Db.VERSION)
            put("createdAt", LocalDateTime.now().toString())
            put("photos", photos.size)
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
     * so a damaged or incompatible file leaves the current data untouched.
     */
    suspend fun restore(context: Context, src: Uri): ImportSummary = withContext(Dispatchers.IO) {
        lock.withLock {
            val stage = File(context.cacheDir, "restore_stage").apply { deleteRecursively(); mkdirs() }
            val stagePhotos = File(stage, "photos").apply { mkdirs() }
            val stageDb = File(stage, "fitlens.db")
            try {
                var photos = 0
                context.contentResolver.openInputStream(src)?.use { input ->
                    ZipInputStream(input.buffered()).use { zip ->
                        while (true) {
                            val e = zip.nextEntry ?: break
                            when {
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

                // Settings that belong to this phone (folder permissions) are kept, not taken from the backup.
                val keepMeta = listOf(
                    "backup_folder", "auto_sync", AUTO_FOLDER, AUTO_DAYS, AUTO_KEEP, AUTO_LAST, AUTO_ERROR,
                    AutoBackup.AFTER_CHANGES, AutoBackup.DIRTY
                ).associateWith { Store.db.getMeta(it) }

                Store.db.close()
                val dbFile = context.getDatabasePath(Db.NAME)
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
                File(dbFile.path + "-journal").delete()
                stageDb.copyTo(dbFile, overwrite = true)

                val photoDir = Store.photoDir
                val old = File(photoDir.parentFile, "photos_old").apply { deleteRecursively() }
                photoDir.renameTo(old)
                if (!stagePhotos.renameTo(photoDir)) {
                    photoDir.mkdirs()
                    stagePhotos.listFiles()?.forEach { it.copyTo(File(photoDir, it.name), overwrite = true) }
                }
                old.deleteRecursively()

                Store.init(context)
                keepMeta.forEach { (k, v) -> Store.db.setMeta(k, v) }
                Store.reload()
                ImportSummary("Backup restored with $photos photos.", true)
            } catch (e: Exception) {
                ImportSummary("Restore failed: ${e.message}. Your current data wasn't changed.", false)
            } finally {
                stage.deleteRecursively()
            }
        }
    }

    // ---------- Automatic backups to a folder the user chooses ----------

    private const val AUTO_FOLDER = "auto_backup_folder"
    private const val AUTO_DAYS = "auto_backup_days"
    private const val AUTO_KEEP = "auto_backup_keep"
    private const val AUTO_LAST = "auto_backup_last"
    private const val AUTO_ERROR = "auto_backup_error"

    fun autoFolder(): Uri? = Store.db.getMeta(AUTO_FOLDER)?.let { Uri.parse(it) }
    /** 0 = off, 1 = daily, 7 = weekly. */
    fun autoDays(): Int = Store.db.getMeta(AUTO_DAYS)?.toIntOrNull() ?: 0
    fun autoKeep(): Int = Store.db.getMeta(AUTO_KEEP)?.toIntOrNull() ?: 5
    fun lastAutoBackup(): Long? = Store.db.getMeta(AUTO_LAST)?.toLongOrNull()
    fun setAutoDays(days: Int) = Store.db.setMeta(AUTO_DAYS, days.toString())
    fun setAutoKeep(n: Int) = Store.db.setMeta(AUTO_KEEP, n.toString())

    /** The last automatic backup that failed since the last one that worked: time and message. */
    fun lastError(): Pair<Long, String>? = Store.db.getMeta(AUTO_ERROR)?.let { v ->
        val t = v.substringBefore('|').toLongOrNull() ?: return@let null
        t to v.substringAfter('|')
    }

    private fun failed(message: String, folderProblem: Boolean): ImportSummary {
        Store.db.setMeta(AUTO_ERROR, "${System.currentTimeMillis()}|$message")
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
        Store.db.setMeta(AUTO_FOLDER, uri.toString())
        if (autoDays() == 0) setAutoDays(1)
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
                Store.db.setMeta(AUTO_LAST, System.currentTimeMillis().toString())
                Store.db.setMeta(AUTO_ERROR, null)
                AutoBackup.markBackedUp()
                prune(context, tree)
                ImportSummary("Automatic backup saved ($photos photos).", true)
            } catch (e: SecurityException) {
                failed("FitLens lost access to the backup folder. Choose it again in Sync → Backups.", true)
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
        "The backup folder isn't available. If it's on an SD card, check the card is in; otherwise choose the folder again in Sync → Backups."

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
