package com.fitlens.companion.ui

import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.fitlens.companion.data.DateSources
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.Poses
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import com.fitlens.companion.video.FrameRenderer
import com.fitlens.companion.video.VideoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitIcons
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.TopBarAction
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import com.fitlens.companion.ui.design.DropdownPill

/**
 * One photo at a time, edge to edge on black (#92). Swipe for the next; tap the photo to hide or show the controls,
 * which fade, so the photo can fill the screen. The details panel has the pose, the date and the body values near it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(snap: Snapshot, nav: Nav, ids: List<Long>, index: Int) {
    val photos = ids.mapNotNull { snap.photosById[it] }
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val pager = rememberPagerState(initialPage = index.coerceIn(0, photos.lastIndex)) { photos.size }
    val current = photos[pager.currentPage.coerceIn(0, photos.lastIndex)]
    val state = viewModel<PhotoViewerState>()
    var pickDate by state.saved("pickDate", false)
    var confirmDelete by state.saved("confirmDelete", false)
    var chrome by state.saved("chrome", true)

    fun compare() {
        val other = snap.datedPhotos.firstOrNull { it.pose == current.pose && it.id != current.id }
            ?: snap.datedPhotos.firstOrNull { it.id != current.id }
        if (other != null) {
            val pair = listOf(other, current).sortedBy { it.date ?: "" }
            nav.push(Screen.Compare(pair[0].id, pair[1].id))
        }
    }

    Box(Modifier.fillMaxSize().background(Brand.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val p = photos[page]
            coil.compose.AsyncImage(
                model = snap.photoFile(p),
                contentDescription = "Progress photo ${p.date?.let { Dates.long(it) } ?: ""} ${p.pose}".trim(),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = if (chrome) "Hide controls" else "Show controls"
                    ) { chrome = !chrome }
            )
        }
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(Motion.STANDARD)),
            exit = fadeOut(tween(Motion.STANDARD)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(Modifier.fillMaxWidth().background(Brand.Black.copy(alpha = 0.6f))) {
                FitTopBar(
                    title = current.date?.let { Dates.long(it) } ?: "No date",
                    subtitle = "${pager.currentPage + 1} of ${photos.size}",
                    onBack = { nav.pop() },
                    actions = listOf(
                        TopBarAction(FitIcons.Compare, "Compare with another photo", enabled = snap.datedPhotos.size > 1) { compare() },
                        TopBarAction(Icons.Filled.Delete, "Delete photo") { confirmDelete = true }
                    ),
                    overflow = buildList {
                        add(MenuAction("Change date…") { pickDate = true })
                        current.date?.let { d -> add(MenuAction("Open this day") { nav.push(Screen.Day(d)) }) }
                    }
                )
            }
        }
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(Motion.STANDARD)),
            exit = fadeOut(tween(Motion.STANDARD)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(Modifier.fillMaxWidth().background(Brand.Black.copy(alpha = 0.75f))) {
                GoldHairline()
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // The pose as one compact dropdown (#115), so the photo keeps the screen.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("POSE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val labels = Poses.all + "Not set"
                        val values = Poses.all + Poses.NONE
                        DropdownPill(
                            label = "Pose",
                            options = labels,
                            selected = values.indexOf(current.pose).let { if (it < 0) labels.lastIndex else it }
                        ) { i ->
                            if (current.pose != values[i]) {
                                val id = current.id
                                AppScope.scope.launch { Store.setPhotoPose(listOf(id), values[i]) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { pickDate = true }) { Text("Change date") }
                    }
                    Text(
                        "${DateSources.label(current.dateSource)}${current.takenAt?.let { formatTime(it) }?.let { " · $it" } ?: ""}" +
                            (current.originalName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    val d = current.date
                    if (d != null) {
                        val near = snap.usedMeasurements.take(8).mapNotNull { m -> snap.valueNear(m.name, d, 7)?.let { m to it } }
                        if (near.isNotEmpty()) {
                            SectionLabel("Body values", Modifier.padding(top = 8.dp, bottom = 2.dp))
                            near.forEach { (m, hit) ->
                                val (r, exact) = hit
                                val note = if (exact) "" else {
                                    val diff = Dates.epochDay(r.date) - Dates.epochDay(d)
                                    "  (≈ ${abs(diff)}d ${if (diff < 0) "before" else "after"})"
                                }
                                InfoRow(m.name, "${fmtNum(r.value)} ${r.unit}$note")
                            }
                        }
                        GlassOutlinedButton(onClick = { nav.push(Screen.Day(d)) }, modifier = Modifier.padding(top = 6.dp)) { Text("Open this day") }
                    }
                }
            }
        }
    }
    if (pickDate) PickDateDialog(current.date, onDismiss = { pickDate = false }) { nd ->
        AppScope.scope.launch { Store.setPhotoDate(listOf(current.id), nd) }
    }
    if (confirmDelete) ConfirmSheet(
        title = "Delete this photo?",
        message = "It's removed from FitLens only — the original on your phone isn't touched.",
        confirmLabel = "Delete photo",
        onDismiss = { confirmDelete = false }
    ) {
        AppScope.scope.launch { Store.deletePhotos(listOf(current.id)) }
    }
}

/** Two photos side by side on black (#92), with the body values on each date and the change between them. */
@Composable
fun CompareScreen(snap: Snapshot, nav: Nav, a: Long, b: Long) {
    var aId by rememberSaveable { mutableLongStateOf(a) }
    var bId by rememberSaveable { mutableLongStateOf(b) }
    var picking by rememberSaveable { mutableIntStateOf(0) } // 0 none, 1 left, 2 right
    val pa = snap.photosById[aId]
    val pb = snap.photosById[bId]
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = "Compare",
            onBack = { nav.pop() },
            actions = listOf(
                TopBarAction(FitIcons.SwapHoriz, "Swap sides") { val t = aId; aId = bId; bId = t },
                TopBarAction(Icons.Filled.Share, "Share image", enabled = pa != null && pb != null) {
                    if (pa != null && pb != null) shareCompare(ctx, snap, pa, pb, save = false)
                }
            ),
            overflow = if (pa != null && pb != null) listOf(
                MenuAction("Save image to gallery") { shareCompare(ctx, snap, pa, pb, save = true) }
            ) else emptyList()
        )
        if (pa == null || pb == null) {
            EmptyState("Photo missing", "One of these photos was deleted.") {
                GlassOutlinedButton(onClick = { nav.pop() }) { Text("Back") }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().background(Brand.Black).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(pa to 1, pb to 2).forEach { (p, side) ->
                        Column(Modifier.weight(1f)) {
                            PhotoThumb(
                                snap, p,
                                Modifier.fillMaxWidth().aspectRatio(0.75f)
                                    .clickable(onClickLabel = "Change the ${if (side == 1) "left" else "right"} photo") { picking = side },
                                sizePx = 1000, contentScale = ContentScale.Fit
                            )
                            Text(p.date?.let { Dates.medium(it) } ?: "No date", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap photo to change" + if (p.pose.isNotBlank()) " · ${p.pose}" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                GoldHairline()
                val da = pa.date
                val db = pb.date
                if (da != null && db != null) {
                    val days = Dates.epochDay(db) - Dates.epochDay(da)
                    Text(
                        "${abs(days)} days apart (${fmtNum(abs(days) / 7.0, 1)} weeks)",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall
                    )
                    val rows = compareRows(snap, da, db)
                    if (rows.isNotEmpty()) {
                        SectionLabel("Body values", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        rows.forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                                    .semantics(mergeDescendants = true) {}
                            ) {
                                Text(row.label, Modifier.weight(1.3f))
                                Text(row.left, Modifier.weight(1f))
                                Text(row.right, Modifier.weight(1f))
                                Text(row.change, Modifier.weight(0.9f), color = deltaColour(row.delta, MaterialTheme.colorScheme.primary))
                            }
                        }
                        Text(
                            "≈ means the nearest measurement within 7 days was used.",
                            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldButton(onClick = { shareCompare(ctx, snap, pa, pb, save = false) }) { Text("Share image") }
                    GlassOutlinedButton(onClick = { shareCompare(ctx, snap, pa, pb, save = true) }) { Text("Save to gallery") }
                }
            }
        }
    }
    if (picking != 0) {
        PhotoPickerDialog(snap, onDismiss = { picking = 0 }) { id ->
            if (picking == 1) aId = id else bId = id
            picking = 0
        }
    }
}

data class CompareRow(val label: String, val left: String, val right: String, val change: String, val delta: Double = 0.0)

fun compareRows(snap: Snapshot, da: String, db: String): List<CompareRow> =
    snap.usedMeasurements.mapNotNull { m ->
        val ha = snap.valueNear(m.name, da, 7)
        val hb = snap.valueNear(m.name, db, 7)
        if (ha == null && hb == null) return@mapNotNull null
        val l = ha?.let { (if (it.second) "" else "≈") + fmtNum(it.first.value) } ?: "—"
        val r = hb?.let { (if (it.second) "" else "≈") + fmtNum(it.first.value) } ?: "—"
        val ch = if (ha != null && hb != null) fmtSigned(hb.first.value - ha.first.value) else ""
        val unit = (hb ?: ha)?.first?.unit ?: ""
        val delta = if (ha != null && hb != null) hb.first.value - ha.first.value else 0.0
        CompareRow("${m.name}${if (unit.isNotBlank()) " ($unit)" else ""}", l, r, ch, delta)
    }

/** Picks one dated photo, newest first, on the FitLens glass (#92). */
@Composable
fun PhotoPickerDialog(snap: Snapshot, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FitShapes.card,
            color = Brand.Onyx,
            contentColor = Brand.Ivory,
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column {
                Text("Choose a photo", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                GoldHairline()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(snap.datedPhotos.reversed(), key = { it.id }) { p ->
                        Box(Modifier.aspectRatio(0.75f).clip(FitShapes.row).clickable(onClickLabel = "Choose") { onPick(p.id) }) {
                            PhotoThumb(snap, p, Modifier.fillMaxSize(), sizePx = 240)
                            Text(
                                Dates.short(p.date ?: ""), color = Brand.Ivory, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.BottomStart).background(Brand.Black.copy(alpha = 0.6f)).padding(2.dp)
                            )
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text("Cancel") }
            }
        }
    }
}

private fun shareCompare(ctx: Context, snap: Snapshot, pa: Photo, pb: Photo, save: Boolean) {
    val app = ctx.applicationContext
    AppScope.scope.launch {
        UiEvents.busy.value = "Creating comparison image…"
        try {
            val file = withContext(Dispatchers.Default) {
                val ba = FrameRenderer.loadBitmap(snap.photoFile(pa), 1000, 1000)
                val bb = FrameRenderer.loadBitmap(snap.photoFile(pb), 1000, 1000)
                val da = pa.date ?: ""
                val db = pb.date ?: ""
                val rows = compareRows(snap, da, db).filter { it.left != "—" || it.right != "—" }.take(6)
                    .map { Triple(it.label, it.left, "${it.right}  ${it.change}".trim()) }
                val days = abs(Dates.epochDay(db) - Dates.epochDay(da))
                val out = FrameRenderer.renderCompare(ba, bb, da, db, rows, "$days days · FitLens")
                ba?.recycle(); bb?.recycle()
                if (save) {
                    VideoExporter.saveImageToGallery(app, out, "FitLens_compare_${da}_$db.jpg")
                    out.recycle()
                    null
                } else {
                    val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                    val f = File(dir, "FitLens_compare_${da}_$db.jpg")
                    f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    out.recycle()
                    f
                }
            }
            if (file == null) UiEvents.show("Saved to Pictures/FitLens")
            else shareFile(ctx, file, "image/jpeg")
        } catch (e: Exception) {
            UiEvents.show("Couldn't create the image: ${e.message}")
        } finally {
            UiEvents.busy.value = null
        }
    }
}

fun shareFile(ctx: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
