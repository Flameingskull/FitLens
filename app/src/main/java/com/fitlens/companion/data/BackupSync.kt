package com.fitlens.companion.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Semi-automatic FitNotes sync: the user points FitLens at the folder where FitNotes saves its
 * backups. Whenever FitLens opens (or "Sync now" is tapped) the newest .fitnotes file in that
 * folder is imported if it's newer than the last one imported.
 */
object BackupSync {

    data class Found(val uri: Uri, val name: String, val modified: Long)

    fun folder(): Uri? = Store.db.getMeta("backup_folder")?.let { Uri.parse(it) }

    fun setFolder(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Some providers don't support persisted permissions; sync will ask again when needed.
        }
        Store.db.setMeta("backup_folder", uri.toString())
    }

    fun autoSyncEnabled(): Boolean = Store.db.getMeta("auto_sync") != "0"
    fun setAutoSync(on: Boolean) = Store.db.setMeta("auto_sync", if (on) "1" else "0")

    suspend fun newestBackup(context: Context): Found? = withContext(Dispatchers.IO) {
        val tree = folder() ?: return@withContext null
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            var best: Found? = null
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (!name.endsWith(".fitnotes", ignoreCase = true)) continue
                    val mod = if (c.isNull(2)) 0L else c.getLong(2)
                    if (best == null || mod > best!!.modified) {
                        best = Found(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)), name, mod)
                    }
                }
            }
            best
        } catch (e: Exception) {
            null
        }
    }

    /** Imports the newest backup if it's newer than the last import. Returns a message or null if nothing to do. */
    suspend fun syncIfNewer(context: Context, force: Boolean = false): ImportSummary? {
        val found = newestBackup(context) ?: return if (force) {
            if (folder() == null) ImportSummary("Choose your FitNotes backup folder first.", false)
            else ImportSummary("No .fitnotes backups found in the chosen folder.", false)
        } else null
        val last = Store.db.getMeta("last_import_modified")?.toLongOrNull() ?: 0L
        val lastName = Store.db.getMeta("last_import_name")
        if (!force && found.modified <= last && found.name == lastName) return null
        if (!force && found.modified <= last) return null
        return FitNotesImporter.importBackup(context, found.uri, found.modified)
    }

    fun launchFitNotes(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(FitNotesImporter.FITNOTES_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}

/** Full FitLens archive (database + photos) so nothing is lost if the phone or app changes. */
object Archive {

    suspend fun export(context: Context, dest: Uri): ImportSummary = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath(Db.NAME)
            Store.db.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
            var n = 0
            context.contentResolver.openOutputStream(dest)?.use { os ->
                ZipOutputStream(os.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry("fitlens.db"))
                    dbFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    Store.photoDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.forEach { f ->
                        zip.putNextEntry(ZipEntry("photos/" + f.name))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        n++
                    }
                }
            } ?: return@withContext ImportSummary("Couldn't write the archive.", false)
            ImportSummary("Archive saved with $n photos.", true)
        } catch (e: Exception) {
            ImportSummary("Archive export failed: ${e.message}", false)
        }
    }

    suspend fun restore(context: Context, src: Uri): ImportSummary = withContext(Dispatchers.IO) {
        try {
            val tmpDb = File(context.cacheDir, "restore.db")
            var photos = 0
            var hadDb = false
            context.contentResolver.openInputStream(src)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        when {
                            e.name == "fitlens.db" -> { tmpDb.outputStream().use { zip.copyTo(it) }; hadDb = true }
                            e.name.startsWith("photos/") && !e.isDirectory -> {
                                val name = File(e.name).name
                                if (name.isNotBlank() && !name.contains("..")) {
                                    File(Store.photoDir, name).outputStream().use { zip.copyTo(it) }
                                    photos++
                                }
                            }
                        }
                    }
                }
            } ?: return@withContext ImportSummary("Couldn't open the archive.", false)
            if (!hadDb) return@withContext ImportSummary("That isn't a FitLens archive.", false)
            Store.db.close()
            val dbFile = context.getDatabasePath(Db.NAME)
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            tmpDb.copyTo(dbFile, overwrite = true)
            tmpDb.delete()
            Store.init(context)
            Store.reload()
            ImportSummary("Restored archive with $photos photos.", true)
        } catch (e: Exception) {
            ImportSummary("Restore failed: ${e.message}", false)
        }
    }
}
