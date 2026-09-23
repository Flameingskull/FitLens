package com.fitlens.companion.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.fitlens.companion.data.ImportSummary
import com.fitlens.companion.data.PhotoImportResult
import com.fitlens.companion.data.PhotoImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Scope that outlives individual screens so long imports/exports aren't cancelled by navigation. */
object AppScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}

suspend fun runPhotoImport(ctx: Context, uris: List<Uri>, forcedDate: String?): PhotoImportResult {
    UiEvents.busy.value = "Importing ${uris.size} photos…"
    try {
        val r = PhotoImporter.importUris(ctx, uris, forcedDate) { d, t -> UiEvents.busy.value = "Importing photos… $d / $t" }
        UiEvents.show(r.describe())
        return r
    } finally {
        UiEvents.busy.value = null
    }
}

/** Runs a long task with the busy overlay and shows its result message. */
fun runBusy(label: String, block: suspend () -> ImportSummary?) {
    AppScope.scope.launch {
        UiEvents.busy.value = label
        try {
            block()?.let { UiEvents.show(it.message) }
        } catch (e: Exception) {
            UiEvents.show("Something went wrong: ${e.message}")
        } finally {
            UiEvents.busy.value = null
        }
    }
}

/** Multi-select photo picker (keeps full EXIF metadata, unlike the photo picker). */
@Composable
fun rememberPhotoImporter(forcedDate: String? = null, onDone: (PhotoImportResult) -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current.applicationContext
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) AppScope.scope.launch { onDone(runPhotoImport(ctx, uris, forcedDate)) }
    }
    return { launcher.launch(arrayOf("image/*")) }
}

/** Imports every image in a chosen folder (and its sub-folders). */
@Composable
fun rememberFolderPhotoImporter(onDone: (PhotoImportResult) -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current.applicationContext
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) AppScope.scope.launch {
            UiEvents.busy.value = "Scanning folder…"
            val uris = try { PhotoImporter.listFolderImages(ctx, tree) } finally { UiEvents.busy.value = null }
            if (uris.isEmpty()) UiEvents.show("No images found in that folder.")
            else onDone(runPhotoImport(ctx, uris, null))
        }
    }
    return { launcher.launch(null) }
}
