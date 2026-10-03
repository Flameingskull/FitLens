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

private data class Format(val label: String, val w: Int, val h: Int) {
    val key get() = "${w}x$h"
}

private val FORMATS = listOf(
    Format("Portrait HD", 720, 1280),
    Format("Portrait Full HD", 1080, 1920),
    Format("Square", 1024, 1024)
)

/** The pose filter's choices, as stored in [SlideshowPrefs.pose], and how each reads. */
private val POSES = listOf(SlideshowPrefs.ALL) + Poses.all + SlideshowPrefs.UNSET

private fun poseLabel(p: String) = when (p) {
    SlideshowPrefs.ALL -> "All poses"
    SlideshowPrefs.UNSET -> "Pose not set"
    else -> p
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
    val source = remember(snap, ids) {
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
    val opts = SlideOptions(
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
            title = "Slideshow & video",
            onBack = { nav.pop() },
            actions = listOf(TopBarAction(FitIcons.Tune, "Slideshow and video options") { showOptions = true })
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            // The preview, edge to edge on black.
            Box(
                Modifier.fillMaxWidth().height(420.dp).background(Brand.Black),
                contentAlignment = Alignment.Center
            ) {
                if (slides.isEmpty()) {
                    Text("No photos match these options", color = Brand.Ivory, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Crossfade(targetState = preview, animationSpec = tween(if (fade) 300 else 0), label = "preview") { img ->
                        if (img != null) Image(
                            bitmap = img, contentDescription = "Slideshow preview",
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
                    Icon(FitIcons.SkipPrevious, contentDescription = "Previous photo")
                }
                IconButton(enabled = slides.size > 1, onClick = { playing = !playing }) {
                    if (playing) Icon(FitIcons.Pause, contentDescription = "Pause")
                    else Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                }
                IconButton(enabled = slides.size > 1, onClick = { index = (index + 1) % maxOf(1, slides.size) }) {
                    Icon(FitIcons.SkipNext, contentDescription = "Next photo")
                }
            }
            Text(
                if (slides.isEmpty()) "0 photos" else "${index.coerceIn(0, slides.lastIndex) + 1} of ${slides.size} · " +
                    "${fmtNum(slides.size * seconds.toDouble(), 0)} s video",
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium
            )

            SectionTitle("Options")
            // What the video will use, in one line each; the options sheet changes them.
            val overlayText = if (overlays.isEmpty()) "No data" else overlays.joinToString { it.name }
            OptionSummary("Photos", "${poseLabel(pose)} · ${Dates.medium(from)} to ${Dates.medium(to)}" + if (onePerDay) " · one per day" else "")
            OptionSummary("Timing", "${fmtNum(seconds.toDouble(), 1)} s per photo" + if (fade) " · cross-fade" else "")
            OptionSummary("On the video", overlayText)
            OptionSummary("Video", fmt.label + if (title.isNotBlank()) " · \"$title\"" else "")
            GlassOutlinedButton(
                onClick = { showOptions = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) { Text("Change options") }

            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldButton(enabled = slides.isNotEmpty(), onClick = {
                    val app = ctx.applicationContext
                    val snapSlides = slides
                    val snapOpts = opts
                    AppScope.scope.launch {
                        UiEvents.busy.value = "Creating video… 0%"
                        try {
                            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                            val out = File(File(app.cacheDir, "exports"), "FitLens_progress_$stamp.mp4")
                            VideoExporter.export(snap, snapSlides, snapOpts, out) { p ->
                                UiEvents.busy.value = "Creating video… ${(p * 100).toInt()}%"
                            }
                            withContext(Dispatchers.IO) { VideoExporter.saveToGallery(app, out) }
                            lastVideo = out
                            UiEvents.show("Video saved to Movies/FitLens")
                        } catch (e: Exception) {
                            UiEvents.show("Video export failed: ${e.message}")
                        } finally {
                            UiEvents.busy.value = null
                        }
                    }
                }) { Text("Create video") }
                lastVideo?.let { f ->
                    GlassOutlinedButton(onClick = { shareFile(ctx, f, "video/mp4") }) { Text("Share video") }
                }
            }
            Text(
                "Videos are made on your phone and saved to Movies/FitLens. Full HD takes longer to create." +
                    if (remembering) " These options are remembered for next time." else "",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showOptions) FitSheet(title = "Slideshow and video options", onDismiss = { showOptions = false }, dismissLabel = "Done") {
        SectionLabel("Photos")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pose", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            DropdownPill(
                label = "Pose",
                options = POSES.map { poseLabel(it) },
                selected = POSES.indexOf(pose).coerceAtLeast(0)
            ) { pose = POSES[it]; index = 0 }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dates", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            PickerPill("From", Dates.medium(from)) { pickFrom = true }
            Text("to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PickerPill("To", Dates.medium(to)) { pickTo = true }
        }
        ToggleRow("One photo per day", onePerDay, inset = 0.dp) { onePerDay = it }

        SectionLabel("Timing")
        Text("${fmtNum(seconds.toDouble(), 1)} s per photo", style = MaterialTheme.typography.bodyLarge)
        Slider(value = seconds, onValueChange = { seconds = it }, valueRange = SlideshowPrefs.SECONDS)
        ToggleRow("Cross-fade between photos", fade, inset = 0.dp) { fade = it }

        SectionLabel("Overlay")
        ToggleRow("Date", showDate, inset = 0.dp) { showDate = it }
        ToggleRow("Day / week counter", showDays, inset = 0.dp) { showDays = it }
        ToggleRow("Pose label", showPose, inset = 0.dp) { showPose = it }

        SectionLabel("Data on the video · ${overlays.size} of ${FrameRenderer.MAX_OVERLAYS}")
        Text(
            "Choose up to ${FrameRenderer.MAX_OVERLAYS} metrics, including your custom ones. Show each as a value or as a " +
                "value with a progress chart. Values come from that date, or the nearest within 7 days (marked ≈).",
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
                FilterChip(selected = !o.chart, onClick = { overlays[i] = o.copy(chart = false) }, label = { Text("Value") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = o.chart, onClick = { overlays[i] = o.copy(chart = true) }, label = { Text("Chart") })
                IconButton(enabled = i > 0, onClick = { overlays.add(i - 1, overlays.removeAt(i)) }) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${o.name} up")
                }
                IconButton(onClick = { overlays.removeAt(i) }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${o.name}") }
            }
        }

        SectionLabel("Video")
        OutlinedTextField(
            value = title, onValueChange = { title = it.take(SlideshowPrefs.MAX_TITLE) }, singleLine = true,
            label = { Text("Title (optional)") }, modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            DropdownPill(label = "Video size", options = FORMATS.map { it.label }, selected = formatIdx) { formatIdx = it }
        }
        TextButton(onClick = {
            val d = SlideshowPrefs()
            pose = d.pose; onePerDay = d.onePerDay; seconds = d.seconds; fade = d.fade
            showDate = d.showDate; showDays = d.showDays; showPose = d.showPose; title = d.title; formatIdx = 0
            from = firstDate; to = lastDate; index = 0
        }) { Text("Reset options") }
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
