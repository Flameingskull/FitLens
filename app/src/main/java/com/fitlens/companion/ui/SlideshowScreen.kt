package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.DropdownPill
import com.fitlens.companion.ui.design.FitIcons
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import com.fitlens.companion.ui.design.PickerPill
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.TopBarAction
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Poses
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.SlideshowPrefs
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.video.FrameRenderer
import com.fitlens.companion.video.Overlay
import com.fitlens.companion.video.SlideOptions
import com.fitlens.companion.video.VideoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.video.SlideWords

private data class Format(@StringRes val label: Int, val w: Int, val h: Int) {
    val key get() = "${w}x$h"
}

private val FORMATS = listOf(
    Format(R.string.ss_fmt_hd, 720, 1280),
    Format(R.string.ss_fmt_fhd, 1080, 1920),
    Format(R.string.ss_fmt_square, 1024, 1024)
)

/** The pose filter's choices, as stored in [SlideshowPrefs.pose], and how each reads. */
private val POSES = listOf(SlideshowPrefs.ALL) + Poses.all + SlideshowPrefs.UNSET

private fun poseLabel(res: Resources, p: String) = when (p) {
    SlideshowPrefs.ALL -> res.getString(R.string.ph_all_poses)
    SlideshowPrefs.UNSET -> res.getString(R.string.ph_pose_not_set)
    else -> poseText(res, p)
}

/**
 * Slideshow and video (#92): the preview fills the top of the screen with its play controls under it, and every option
 * is in one options sheet. With "Remember slideshow and video options" on in Settings (#46), the screen opens with
 * the options used last; the date range always starts at all dates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideshowScreen(snap: Snapshot, nav: Nav, ids: List<Long>?) {
    val ctx = LocalContext.current
    val res = ctx.resources
    val source = remember(snap.photosKey, ids) {
        if (ids == null) snap.datedPhotos else ids.mapNotNull { snap.photosById[it] }.filter { it.date != null }
    }
    val firstDate = source.firstOrNull()?.date ?: Dates.today()
    val lastDate = source.lastOrNull()?.date ?: Dates.today()

    // The options the screen opens with: the remembered ones, or the defaults (#46).
    val remembering = remember { Settings.currentPortable().rememberVideoOpts }
    val start = remember {
        val saved = if (remembering) SlideshowPrefs.decode(Settings.currentPortable().videoOpts) else SlideshowPrefs()
        // A slideshow of chosen photos shows them all, whatever pose was remembered.
        if (ids != null) saved.copy(pose = SlideshowPrefs.ALL) else saved
    }

    var pose by remember { mutableStateOf(start.pose) }
    var from by remember { mutableStateOf(firstDate) }
    var to by remember { mutableStateOf(lastDate) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var onePerDay by remember { mutableStateOf(start.onePerDay) }
    var seconds by remember { mutableFloatStateOf(start.seconds) }
    var fade by remember { mutableStateOf(start.fade) }
    var showDate by remember { mutableStateOf(start.showDate) }
    var showDays by remember { mutableStateOf(start.showDays) }
    var showPose by remember { mutableStateOf(start.showPose) }
    var title by remember { mutableStateOf(start.title) }
    var formatIdx by remember { mutableIntStateOf(FORMATS.indexOfFirst { it.key == start.format }.coerceAtLeast(0)) }
    // Overlays in display order. Remembered ones are kept while their measurement still exists; otherwise bodyweight
    // as a chart, then body fat and waist as values, when they exist.
    val overlays = remember {
        mutableStateListOf<Overlay>().apply {
            val known = snap.usedMeasurements.map { it.name }.toSet()
            val saved = start.overlays
            if (saved != null) {
                saved.filter { it.first in known }.take(FrameRenderer.MAX_OVERLAYS).forEach { (n, chart) -> add(Overlay(n, chart)) }
            } else {
                snap.bodyweightName?.let { add(Overlay(it, chart = true)) }
                snap.usedMeasurements.map { it.name }
                    .filter { (it.equals("Body Fat", true) || it.equals("Waist", true)) && none { o -> o.name == it } }
                    .forEach { add(Overlay(it, chart = false)) }
            }
        }
    }
    var playing by remember { mutableStateOf(true) }
    var index by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var lastVideo by remember { mutableStateOf<File?>(null) }
    var showOptions by remember { mutableStateOf(false) }

    val fmt = FORMATS[formatIdx]
    val photos = source.filter { p ->
        val d = p.date!!
        d >= from && d <= to && when (pose) {
            SlideshowPrefs.ALL -> true
            SlideshowPrefs.UNSET -> p.pose.isBlank()
            else -> p.pose == pose
        }
    }
    val words = remember(res) {
        SlideWords(
            day = res.getString(R.string.vid_day), week = res.getString(R.string.vid_week),
            nearBefore = res.getString(R.string.vid_near_before), nearAfter = res.getString(R.string.vid_near_after),
            poses = Poses.all.associateWith { poseText(res, it) }
        )
    }
    val opts = SlideOptions(
        words = words,
        width = fmt.w, height = fmt.h, secondsPerPhoto = seconds, fade = fade,
        showDate = showDate, showDayCount = showDays, showPose = showPose,
        overlays = overlays.toList(), onePerDay = onePerDay, title = title
    )
    val slides = remember(snap, photos, opts) { FrameRenderer.buildSlides(snap, photos, opts) }
    val charts = remember(snap, slides, opts) { FrameRenderer.chartsFor(snap, slides, opts) }

    // Remember the options a moment after the last change, and on leaving (#46). Nothing is saved until something
    // changed, so opening the screen doesn't turn the defaults into saved options.
    val initialText = remember { start.encode() }
    val currentText = SlideshowPrefs(
        pose, onePerDay, seconds, fade, showDate, showDays, showPose, title, fmt.key,
        overlays.map { it.name to it.chart }
    ).encode()
    fun save(text: String) {
        if (remembering && text != initialText && Settings.currentPortable().videoOpts != text) {
            Settings.updatePortable { it.copy(videoOpts = text) }
        }
    }
    LaunchedEffect(currentText) {
        delay(800)
        save(currentText)
    }
    val latestText by rememberUpdatedState(currentText)
    DisposableEffect(Unit) { onDispose { save(latestText) } }

    // Render the preview frame (smaller than export size, same layout)
    LaunchedEffect(slides, index, opts) {
        if (slides.isEmpty()) { preview = null; return@LaunchedEffect }
        val i = index.coerceIn(0, slides.lastIndex)
        val pw = 540
        val ph = (540f * opts.height / opts.width).toInt()
        preview = withContext(Dispatchers.Default) {
            val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
            val photo = FrameRenderer.loadBitmap(snap.photoFile(slides[i].photo), pw, ph)
            FrameRenderer.drawSlide(Canvas(bmp), photo, slides[i], opts, charts)
            photo?.recycle()
            bmp.asImageBitmap()
        }
    }
    LaunchedEffect(playing, slides.size, seconds) {
        while (playing && slides.size > 1) {
            delay((seconds * 1000).toLong())
            index = (index + 1) % slides.size
        }
    }

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = stringResource(R.string.ss_title),
            onBack = { nav.pop() },
            actions = listOf(TopBarAction(FitIcons.Tune, stringResource(R.string.ss_options_cd)) { showOptions = true })
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            // The preview, edge to edge on black.
            Box(
                Modifier.fillMaxWidth().height(420.dp).background(Brand.Black),
                contentAlignment = Alignment.Center
            ) {
                if (slides.isEmpty()) {
                    EmptyState(stringResource(R.string.ss_no_match), stringResource(R.string.ss_no_match_body))
                } else {
                    Crossfade(targetState = preview, animationSpec = tween(if (fade) 300 else 0), label = "preview") { img ->
                        if (img != null) Image(
                            bitmap = img, contentDescription = stringResource(R.string.ss_preview),
                            modifier = Modifier.fillMaxSize().aspectRatio(opts.width.toFloat() / opts.height, matchHeightConstraintsFirst = true)
                        )
                    }
                }
            }
            GoldHairline()
            // Play controls, centred under the preview.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(enabled = slides.size > 1, onClick = { index = (index - 1 + slides.size).coerceAtLeast(0) % maxOf(1, slides.size) }) {
                    Icon(FitIcons.SkipPrevious, contentDescription = stringResource(R.string.ss_prev))
                }
                IconButton(enabled = slides.size > 1, onClick = { playing = !playing }) {
                    if (playing) Icon(FitIcons.Pause, contentDescription = stringResource(R.string.ss_pause))
                    else Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.ss_play))
                }
                IconButton(enabled = slides.size > 1, onClick = { index = (index + 1) % maxOf(1, slides.size) }) {
                    Icon(FitIcons.SkipNext, contentDescription = stringResource(R.string.ss_next))
                }
            }
            Text(
                if (slides.isEmpty()) stringResource(R.string.ss_zero) else stringResource(R.string.ss_position,
                    index.coerceIn(0, slides.lastIndex) + 1, slides.size, fmtNum(slides.size * seconds.toDouble(), 0)),
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium
            )

            SectionTitle(stringResource(R.string.ss_options))
            // What the video will use, in one line each; the options sheet changes them.
            val overlayText = if (overlays.isEmpty()) stringResource(R.string.ss_no_data) else overlays.joinToString { it.name }
            OptionSummary(stringResource(R.string.ss_photos), stringResource(R.string.ss_photos_summary, poseLabel(res, pose), Dates.medium(from), Dates.medium(to)) + if (onePerDay) stringResource(R.string.ss_one_per_day_suffix) else "")
            OptionSummary(stringResource(R.string.ss_timing), stringResource(R.string.ss_per_photo, fmtNum(seconds.toDouble(), 1)) + if (fade) stringResource(R.string.ss_crossfade_suffix) else "")
            OptionSummary(stringResource(R.string.ss_on_video), overlayText)
            OptionSummary(stringResource(R.string.ss_video), stringResource(fmt.label) + if (title.isNotBlank()) stringResource(R.string.ss_title_suffix, title) else "")
            GlassOutlinedButton(
                onClick = { showOptions = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) { Text(stringResource(R.string.ss_change_options)) }

            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldButton(enabled = slides.isNotEmpty(), onClick = {
                    val app = ctx.applicationContext
                    val snapSlides = slides
                    val snapOpts = opts
                    AppScope.scope.launch {
                        UiEvents.busy.value = res.getString(R.string.ss_creating, 0)
                        try {
                            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                            val out = File(File(app.cacheDir, "exports"), "FitLens_progress_$stamp.mp4")
                            VideoExporter.export(snap, snapSlides, snapOpts, out) { p ->
                                UiEvents.busy.value = res.getString(R.string.ss_creating, (p * 100).toInt())
                            }
                            withContext(Dispatchers.IO) { VideoExporter.saveToGallery(app, out) }
                            lastVideo = out
                            UiEvents.show(res.getString(R.string.ss_saved))
                        } catch (e: Exception) {
                            UiEvents.show(res.getString(R.string.ss_failed, e.message ?: ""))
                        } finally {
                            UiEvents.busy.value = null
                        }
                    }
                }) { Text(stringResource(R.string.ss_create)) }
                lastVideo?.let { f ->
                    GlassOutlinedButton(onClick = { shareFile(ctx, f, "video/mp4") }) { Text(stringResource(R.string.ss_share)) }
                }
            }
            Text(
                stringResource(R.string.ss_note) +
                    if (remembering) stringResource(R.string.ss_note_remember) else "",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showOptions) FitSheet(title = stringResource(R.string.ss_options_cd), onDismiss = { showOptions = false }, dismissLabel = stringResource(R.string.ss_done)) {
        SectionLabel(stringResource(R.string.ss_photos))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ss_pose), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            DropdownPill(
                label = stringResource(R.string.ss_pose),
                options = POSES.map { poseLabel(res, it) },
                selected = POSES.indexOf(pose).coerceAtLeast(0)
            ) { pose = POSES[it]; index = 0 }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ss_dates), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            PickerPill(stringResource(R.string.ss_from), Dates.medium(from)) { pickFrom = true }
            Text(stringResource(R.string.ss_to_word), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PickerPill(stringResource(R.string.ss_to), Dates.medium(to)) { pickTo = true }
        }
        ToggleRow(stringResource(R.string.ss_one_per_day), onePerDay, inset = 0.dp) { onePerDay = it }

        SectionLabel(stringResource(R.string.ss_timing))
        Text(stringResource(R.string.ss_per_photo, fmtNum(seconds.toDouble(), 1)), style = MaterialTheme.typography.bodyLarge)
        Slider(value = seconds, onValueChange = { seconds = it }, valueRange = SlideshowPrefs.SECONDS)
        ToggleRow(stringResource(R.string.ss_crossfade), fade, inset = 0.dp) { fade = it }

        SectionLabel(stringResource(R.string.ss_overlay))
        ToggleRow(stringResource(R.string.ss_date), showDate, inset = 0.dp) { showDate = it }
        ToggleRow(stringResource(R.string.ss_counter), showDays, inset = 0.dp) { showDays = it }
        ToggleRow(stringResource(R.string.ss_pose_label), showPose, inset = 0.dp) { showPose = it }

        SectionLabel(stringResource(R.string.ss_data_on_video, overlays.size, FrameRenderer.MAX_OVERLAYS))
        Text(
            stringResource(R.string.ss_data_help, FrameRenderer.MAX_OVERLAYS),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            snap.usedMeasurements.forEach { m ->
                val on = overlays.any { it.name == m.name }
                FilterChip(
                    selected = on,
                    enabled = on || overlays.size < FrameRenderer.MAX_OVERLAYS,
                    onClick = { if (on) overlays.removeAll { it.name == m.name } else overlays.add(Overlay(m.name, chart = false)) },
                    label = { Text(m.name) }
                )
            }
        }
        overlays.forEachIndexed { i, o ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}. ${o.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                FilterChip(selected = !o.chart, onClick = { overlays[i] = o.copy(chart = false) }, label = { Text(stringResource(R.string.ss_value)) })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = o.chart, onClick = { overlays[i] = o.copy(chart = true) }, label = { Text(stringResource(R.string.ss_chart)) })
                IconButton(enabled = i > 0, onClick = { overlays.add(i - 1, overlays.removeAt(i)) }) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.ss_move_up, o.name))
                }
                IconButton(onClick = { overlays.removeAt(i) }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ss_remove, o.name)) }
            }
        }

        SectionLabel(stringResource(R.string.ss_video))
        OutlinedTextField(
            value = title, onValueChange = { title = it.take(SlideshowPrefs.MAX_TITLE) }, singleLine = true,
            label = { Text(stringResource(R.string.ss_title_field)) }, modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ss_size), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            DropdownPill(label = stringResource(R.string.ss_video_size), options = FORMATS.map { res.getString(it.label) }, selected = formatIdx) { formatIdx = it }
        }
        TextButton(onClick = {
            val d = SlideshowPrefs()
            pose = d.pose; onePerDay = d.onePerDay; seconds = d.seconds; fade = d.fade
            showDate = d.showDate; showDays = d.showDays; showPose = d.showPose; title = d.title; formatIdx = 0
            from = firstDate; to = lastDate; index = 0
        }) { Text(stringResource(R.string.ss_reset)) }
    }
    if (pickFrom) PickDateDialog(from, onDismiss = { pickFrom = false }) { from = it; index = 0 }
    if (pickTo) PickDateDialog(to, onDismiss = { pickTo = false }) { to = it; index = 0 }
}

/** One line of the options summary: a small label and what's chosen. */
@Composable
private fun OptionSummary(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
        Text(
            label, Modifier.width(104.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * An on/off option with FitNotes's checkbox on the right, as on the Settings rows (#147); the whole row is the touch
 * target. [inset] is the row's side padding: 0 inside a sheet, which pads its content already.
 */
@Composable
fun ToggleRow(label: String, checked: Boolean, inset: Dp = 16.dp, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = inset),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
