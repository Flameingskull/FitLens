package com.fitlens.companion.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PhotoImportResult(
    val added: Int,
    val duplicates: Int,
    val failed: Int,
    val bySource: Map<String, Int>,
    val newIds: List<Long>
) {
    /** Worded for the screen by `importResultText` in `ui/PhotoText.kt` (#156). */
    val needsReview: Int get() = (bySource[DateSources.FILE] ?: 0) + (bySource[DateSources.NONE] ?: 0)
}


object PhotoImporter {

    private val exifFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    private val nameDate = Regex("""(?<!\d)(20\d{2})[-_.]?(0[1-9]|1[0-2])[-_.]?(0[1-9]|[12]\d|3[01])(?:[-_ T.]?([01]\d|2[0-3])[-_.:]?([0-5]\d)[-_.:]?([0-5]\d))?""")

    /** Lists image files in a folder picked with the system folder picker (recursively). */
    suspend fun listFolderImages(context: Context, treeUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
        val out = ArrayList<Uri>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 2000) {
            val docId = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            try {
                context.contentResolver.query(
                    children,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
                    null, null, null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        val mime = c.getString(1) ?: ""
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> queue.add(id)
                            mime.startsWith("image/") -> out.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
                        }
                    }
                }
            } catch (e: Exception) {
                // unreadable sub-folder: skip
            }
        }
        out
    }

    suspend fun importUris(
        context: Context,
        uris: List<Uri>,
        forcedDate: String? = null,
        /** Pose given to every newly added photo. Photos that were already imported keep their pose. */
        pose: String = Poses.NONE,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): PhotoImportResult = withContext(Dispatchers.IO) {
        var added = 0
        var dup = 0
        var failed = 0
        val bySource = HashMap<String, Int>()
        val newIds = ArrayList<Long>()
        val dir = Store.photoDir
        val photos = Store.db.photoDao
        uris.forEachIndexed { index, uri ->
            onProgress(index, uris.size)
            try {
                val name = FitNotesImporter.displayName(context, uri) ?: "photo.jpg"
                val tmp = File(dir, "incoming.tmp")
                val md = MessageDigest.getInstance("SHA-1")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            md.update(buf, 0, n)
                            out.write(buf, 0, n)
                        }
                    }
                } ?: throw IllegalStateException("cannot open")
                val hash = md.digest().joinToString("") { "%02x".format(it) }
                if (photos.hasHash(hash)) {
                    tmp.delete()
                    dup++
                    return@forEachIndexed
                }
                val ext = name.substringAfterLast('.', "jpg").lowercase().take(5).ifBlank { "jpg" }
                val fileName = "$hash.$ext"
                val dest = File(dir, fileName)
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }

                var takenAt: LocalDateTime? = null
                var source = DateSources.NONE
                if (forcedDate != null) {
                    takenAt = exifDate(dest)
                    source = DateSources.MANUAL
                } else {
                    exifDate(dest)?.let { takenAt = it; source = DateSources.EXIF }
                    if (takenAt == null) mediaStoreDate(context, uri)?.let { takenAt = it; source = DateSources.MEDIA }
                    if (takenAt == null) fileNameDate(name)?.let { takenAt = it; source = DateSources.FILENAME }
                    if (takenAt == null) lastModified(context, uri)?.let { takenAt = it; source = DateSources.FILE }
                }
                val date = forcedDate ?: takenAt?.toLocalDate()?.format(Dates.ISO)
                val id = photos.add(
                    PhotoRow(
                        id = 0L, file = fileName, date = date, taken_at = takenAt?.toString(),
                        date_source = if (date == null) DateSources.NONE else source, pose = pose, note = null,
                        original_name = name, hash = hash, added_at = System.currentTimeMillis()
                    )
                )
                if (id > 0) {
                    newIds.add(id)
                    added++
                    val key = if (date == null) DateSources.NONE else source
                    bySource[key] = (bySource[key] ?: 0) + 1
                } else {
                    dup++
                }
            } catch (e: Exception) {
                failed++
            }
        }
        onProgress(uris.size, uris.size)
        Store.refresh(Area.PHOTOS)
        PhotoImportResult(added, dup, failed, bySource, newIds)
    }

    private fun plausible(d: LocalDateTime?): LocalDateTime? {
        if (d == null) return null
        val now = LocalDateTime.now().plusDays(1)
        return if (d.year >= 2000 && d.isBefore(now)) d else null
    }

    fun exifDate(file: File): LocalDateTime? = try {
        val exif = ExifInterface(file.path)
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        if (raw == null || raw.length < 19 || raw.startsWith("0000")) null
        else plausible(LocalDateTime.parse(raw.substring(0, 19), exifFormat))
    } catch (e: Exception) {
        null
    }

    private fun mediaStoreDate(context: Context, uri: Uri): LocalDateTime? = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) {
                val ms = c.getLong(0)
                if (ms > 0) plausible(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())) else null
            } else null
        }
    } catch (e: Exception) {
        null
    }

    fun fileNameDate(name: String): LocalDateTime? {
        val m = nameDate.find(name) ?: return null
        return try {
            val (y, mo, d) = Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            val h = m.groupValues[4].toIntOrNull() ?: 12
            val mi = m.groupValues[5].toIntOrNull() ?: 0
            val s = m.groupValues[6].toIntOrNull() ?: 0
            plausible(LocalDate.of(y, mo, d).atTime(h, mi, s))
        } catch (e: Exception) {
            null
        }
    }

    private fun lastModified(context: Context, uri: Uri): LocalDateTime? = try {
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) {
                val ms = c.getLong(0)
                if (ms > 0) plausible(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())) else null
            } else null
        }
    } catch (e: Exception) {
        null
    }
}
