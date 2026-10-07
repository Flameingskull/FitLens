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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.fitlens.companion.R

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
    val res = LocalContext.current.resources

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
                contentDescription = res.getString(R.string.pv_photo_cd, p.date?.let { Dates.long(it) } ?: "", if (p.pose.isBlank()) "" else poseText(res, p.pose)).trim(),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = if (chrome) stringResource(R.string.pv_hide_controls) else stringResource(R.string.pv_show_controls)
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
                    title = current.date?.let { Dates.long(it) } ?: stringResource(R.string.ph_no_date),
                    subtitle = stringResource(R.string.pv_n_of, pager.currentPage + 1, photos.size),
                    onBack = { nav.pop() },
                    actions = listOf(
                        TopBarAction(FitIcons.Compare, stringResource(R.string.pv_compare_other), enabled = snap.datedPhotos.size > 1) { compare() },
                        TopBarAction(Icons.Filled.Delete, stringResource(R.string.pv_delete)) { confirmDelete = true }
                    ),
                    overflow = buildList {
                        add(MenuAction(stringResource(R.string.ph_change_date_menu)) { pickDate = true })
                        current.date?.let { d -> add(MenuAction(res.getString(R.string.pv_open_day)) { nav.push(Screen.Day(d)) }) }
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
                        Text(stringResource(R.string.pv_pose_caps), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val labels = Poses.all.map { poseText(res, it) } + res.getString(R.string.pose_not_set)
                        val values = Poses.all + Poses.NONE
                        DropdownPill(
                            label = stringResource(R.string.ph_pose),
                            options = labels,
                            selected = values.indexOf(current.pose).let { if (it < 0) labels.lastIndex else it }
                        ) { i ->
                            if (current.pose != values[i]) {
                                val id = current.id
                                AppScope.scope.launch { Store.setPhotoPose(listOf(id), values[i]) }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.pv_change_date)) }
                    }
                    Text(
                        "${dateSourceText(res, current.dateSource)}${current.takenAt?.let { formatTime(it) }?.let { " · $it" } ?: ""}" +
                            (current.originalName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    val d = current.date
                    if (d != null) {
                        val near = snap.usedMeasurements.take(8).mapNotNull { m -> snap.valueNear(m.name, d, 7)?.let { m to it } }
                        if (near.isNotEmpty()) {
                            SectionLabel(stringResource(R.string.pv_body_values), Modifier.padding(top = 8.dp, bottom = 2.dp))
                            near.forEach { (m, hit) ->
                                val (r, exact) = hit
                                val note = if (exact) "" else {
                                    val diff = Dates.epochDay(r.date) - Dates.epochDay(d)
                                    res.getString(if (diff < 0) R.string.pv_near_before else R.string.pv_near_after, abs(diff).toInt())
                                }
                                InfoRow(m.name, "${fmtNum(r.value)} ${r.unit}$note")
                            }
                        }
                        GlassOutlinedButton(onClick = { nav.push(Screen.Day(d)) }, modifier = Modifier.padding(top = 6.dp)) { Text(stringResource(R.string.pv_open_day)) }
                    }
                }
            }
        }
    }
    if (pickDate) PickDateDialog(current.date, onDismiss = { pickDate = false }) { nd ->
        AppScope.scope.launch { Store.setPhotoDate(listOf(current.id), nd) }
    }
    if (confirmDelete) ConfirmSheet(
        title = stringResource(R.string.pv_delete_title),
        message = stringResource(R.string.pv_delete_body),
        confirmLabel = stringResource(R.string.pv_delete),
        onDismiss = { confirmDelete = false },
        onConfirm = { AppScope.scope.launch { Store.deletePhotos(listOf(current.id)) } }
    )
}

/** Two photos side by side on black (#92), with the body values on each date and the change between them. */
@OptIn(ExperimentalMaterial3Api::class)
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
            title = stringResource(R.string.pv_compare),
            onBack = { nav.pop() },
            actions = listOf(
                TopBarAction(FitIcons.SwapHoriz, stringResource(R.string.pv_swap)) { val t = aId; aId = bId; bId = t },
                TopBarAction(Icons.Filled.Share, stringResource(R.string.pv_share_image), enabled = pa != null && pb != null) {
                    if (pa != null && pb != null) shareCompare(ctx, snap, pa, pb, save = false)
                }
            ),
            overflow = if (pa != null && pb != null) listOf(
                MenuAction(stringResource(R.string.pv_save_gallery_menu)) { shareCompare(ctx, snap, pa, pb, save = true) }
            ) else emptyList()
        )
        if (pa == null || pb == null) {
            EmptyState(stringResource(R.string.pv_missing), stringResource(R.string.pv_missing_body)) {
                GlassOutlinedButton(onClick = { nav.pop() }) { Text(stringResource(R.string.pv_back)) }
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
                                    .clickable(onClickLabel = if (side == 1) stringResource(R.string.pv_change_left) else stringResource(R.string.pv_change_right)) { picking = side },
                                sizePx = 1000, contentScale = ContentScale.Fit
                            )
                            Text(p.date?.let { Dates.medium(it) } ?: stringResource(R.string.ph_no_date), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.pv_tap_change) + if (p.pose.isNotBlank()) " · ${poseText(LocalContext.current.resources, p.pose)}" else "",
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
                        stringResource(R.string.pv_days_apart, abs(days).toInt(), fmtNum(abs(days) / 7.0, 1)),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall
                    )
                    val rows = compareRows(snap, da, db)
                    if (rows.isNotEmpty()) {
                        SectionLabel(stringResource(R.string.pv_body_values), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
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
                            stringResource(R.string.pv_nearest_note),
                            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldButton(onClick = { shareCompare(ctx, snap, pa, pb, save = false) }) { Text(stringResource(R.string.pv_share_image)) }
                    GlassOutlinedButton(onClick = { shareCompare(ctx, snap, pa, pb, save = true) }) { Text(stringResource(R.string.pv_save_gallery)) }
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

/** Picks one dated photo, newest first, on the FlexNotes glass (#92). */
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
                Text(stringResource(R.string.pv_choose_photo), Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                GoldHairline()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(snap.datedPhotos.reversed(), key = { it.id }) { p ->
                        Box(Modifier.aspectRatio(0.75f).clip(FitShapes.row).clickable(onClickLabel = stringResource(R.string.pv_choose)) { onPick(p.id) }) {
                            PhotoThumb(snap, p, Modifier.fillMaxSize(), sizePx = 240)
                            Text(
                                Dates.short(p.date ?: ""), color = Brand.Ivory, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.BottomStart).background(Brand.Black.copy(alpha = 0.6f)).padding(2.dp)
                            )
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text(stringResource(R.string.pv_cancel)) }
            }
        }
    }
}

private fun shareCompare(ctx: Context, snap: Snapshot, pa: Photo, pb: Photo, save: Boolean) {
    val app = ctx.applicationContext
    val res = app.resources
    AppScope.scope.launch {
        UiEvents.busy.value = res.getString(R.string.pv_creating_compare)
        try {
            val file = withContext(Dispatchers.Default) {
                val ba = FrameRenderer.loadBitmap(snap.photoFile(pa), 1000, 1000)
                val bb = FrameRenderer.loadBitmap(snap.photoFile(pb), 1000, 1000)
                val da = pa.date ?: ""
                val db = pb.date ?: ""
                val rows = compareRows(snap, da, db).filter { it.left != "—" || it.right != "—" }.take(6)
                    .map { Triple(it.label, it.left, "${it.right}  ${it.change}".trim()) }
                val days = abs(Dates.epochDay(db) - Dates.epochDay(da))
                val out = FrameRenderer.renderCompare(ba, bb, da, db, rows, res.getString(R.string.pv_compare_footer, days.toInt()))
                ba?.recycle(); bb?.recycle()
                if (save) {
                    VideoExporter.saveImageToGallery(app, out, "FlexNotes_compare_${da}_$db.jpg")
                    out.recycle()
                    null
                } else {
                    val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                    val f = File(dir, "FlexNotes_compare_${da}_$db.jpg")
                    f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    out.recycle()
                    f
                }
            }
            if (file == null) UiEvents.show(res.getString(R.string.pv_saved_pictures))
            else shareFile(ctx, file, "image/jpeg")
        } catch (e: Exception) {
            UiEvents.show(res.getString(R.string.pv_image_failed, e.message ?: ""))
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
    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.pv_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** How far from a graph point a progress photo may be and still be shown with it (#56). */
const val GRAPH_PHOTO_DAYS = 14

/**
 * The progress photo nearest [date], within [GRAPH_PHOTO_DAYS], as a thumbnail with its date (#56): a graph's tapped
 * point shows what the user looked like then. Tapping it opens the viewer on that photo. Shows nothing without one.
 */
@Composable
fun NearestPhotoThumb(snap: Snapshot, nav: Nav, date: String, modifier: Modifier = Modifier) {
    val photo = remember(snap.photosKey, date) { nearestPhoto(snap, date, GRAPH_PHOTO_DAYS) } ?: return
    val taken = photo.date ?: return
    val days = Dates.epochDay(taken) - Dates.epochDay(date)
    val gap = when {
        days == 0L -> stringResource(R.string.pv_same_day)
        days < 0 -> pluralStringResource(R.plurals.pv_days_before, (-days).toInt(), (-days).toInt())
        else -> pluralStringResource(R.plurals.pv_days_after, days.toInt(), days.toInt())
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(FitShapes.row)
            .clickable(onClickLabel = stringResource(R.string.pv_open_photo)) {
                val ids = snap.datedPhotos.reversed().map { it.id }
                nav.push(Screen.PhotoViewer(ids, ids.indexOf(photo.id).coerceAtLeast(0)))
            }
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhotoThumb(snap, photo, Modifier.width(48.dp).aspectRatio(0.75f), sizePx = 160)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(stringResource(R.string.pv_progress_photo), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "${Dates.medium(taken)} · $gap" + if (photo.pose.isNotBlank()) " · ${poseText(LocalContext.current.resources, photo.pose)}" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
