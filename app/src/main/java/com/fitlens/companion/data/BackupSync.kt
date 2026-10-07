package com.fitlens.companion.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.fitlens.companion.R

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional FitNotes folder sync, for people moving over from FitNotes gradually: the user points FlexNotes at the
 * folder where FitNotes saves its backups. "Sync now" imports the newest .fitnotes file there (after showing what it
 * adds), and when "sync automatically" is on, FlexNotes imports it quietly on opening if it's newer than the last one.
 * Imports merge into FlexNotes and never delete or overwrite FlexNotes data (see [Workouts]).
 *
 * Automatic sync is off by default. Installs that had chosen a sync folder before FlexNotes became the main logger
 * keep it on (set by the database upgrade to version 3), unless they had switched it off.
 */
object BackupSync {

    data class Found(val uri: Uri, val name: String, val modified: Long)

    fun folder(): Uri? = Settings.current().backupFolder?.let { Uri.parse(it) }

    fun setFolder(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Some providers don't support persisted permissions; sync will ask again when needed.
        }
        Settings.updateDevice { it.copy(backupFolder = uri.toString()) }
    }

    /**
     * Whether the chosen folder can still be listed (#41): false once the phone has withdrawn FlexNotes's access or the
     * storage holding it is gone, null when no folder is chosen. Settings shows a warning on the folder row for false.
     */
    suspend fun folderReachable(context: Context): Boolean? = withContext(Dispatchers.IO) {
        val tree = folder() ?: return@withContext null
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun autoSyncEnabled(): Boolean = Settings.current().autoSync
    fun setAutoSync(on: Boolean) = Settings.updateDevice { it.copy(autoSync = on) }

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
            if (folder() == null) ImportSummary(context.getString(R.string.imp_choose_folder), false)
            else ImportSummary(context.getString(R.string.imp_no_backups), false)

        } else null
        val settings = Settings.current()
        val last = settings.lastImportModified ?: 0L
        val lastName = settings.lastImportName
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
