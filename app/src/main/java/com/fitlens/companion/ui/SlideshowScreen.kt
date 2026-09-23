package com.fitlens.companion.ui

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Poses
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

private data class Format(val label: String, val w: Int, val h: Int)

private val FORMATS = listOf(
    Format("Portrait HD", 720, 1280),
    Format("Portrait Full HD", 1080, 1920),
    Format("Square", 1024, 1024)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideshowScreen(snap: Snapshot, nav: Nav, ids: List<Long>?) {
    val ctx = LocalContext.current
    val source = remember(snap, ids) {
        if (ids == null) snap.datedPhotos else ids.mapNotNull { snap.photosById[it] }.filter { it.date != null }
    }
    val firstDate = source.firstOrNull()?.date ?: Dates.today()
    val lastDate = source.lastOrNull()?.date ?: Dates.today()

    var pose by remember { mutableStateOf("All") }
    var from by remember { mutableStateOf(firstDate) }
    var to by remember { mutableStateOf(lastDate) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var onePerDay by remember { mutableStateOf(true) }
    var seconds by remember { mutableFloatStateOf(1.0f) }
    var fade by remember { mutableStateOf(true) }
    var showDate by remember { mutableStateOf(true) }
    var showDays by remember { mutableStateOf(true) }
    var showPose by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var formatIdx by remember { mutableIntStateOf(0) }
    // Overlays in display order: bodyweight as a chart, body fat and waist as values, when they exist
    val overlays = remember {
        mutableStateListOf<Overlay>().apply {
            snap.bodyweightName?.let { add(Overlay(it, chart = true)) }
            snap.usedMeasurements.map { it.name }
                .filter { (it.equals("Body Fat", true) || it.equals("Waist", true)) && none { o -> o.name == it } }
                .forEach { add(Overlay(it, chart = false)) }
        }
    }
    var playing by remember { mutableStateOf(true) }
    var index by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var lastVideo by remember { mutableStateOf<File?>(null) }

    val fmt = FORMATS[formatIdx]
    val photos = source.filter { p ->
        val d = p.date!!
        d >= from && d <= to && when (pose) {
            "All" -> true
            "Unset" -> p.pose.isBlank()
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
        BackTopBar("Slideshow & video", onBack = { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Box(
                Modifier.fillMaxWidth().height(420.dp).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (slides.isEmpty()) {
                    Text("No photos match these settings", color = Color.White)
                } else {
                    Crossfade(targetState = preview, animationSpec = tween(if (fade) 300 else 0), label = "preview") { img ->
                        if (img != null) Image(
                            bitmap = img, contentDescription = "Slideshow preview",
                            modifier = Modifier.fillMaxSize().aspectRatio(opts.width.toFloat() / opts.height, matchHeightConstraintsFirst = true)
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (slides.isEmpty()) "0 photos" else "${index.coerceIn(0, slides.lastIndex) + 1} / ${slides.size} · " +
                        "${fmtNum(slides.size * seconds.toDouble(), 0)} s video",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { index = (index - 1 + slides.size).coerceAtLeast(0) % maxOf(1, slides.size) }) { Text("‹ Prev") }
                TextButton(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
                TextButton(onClick = { index = (index + 1) % maxOf(1, slides.size) }) { Text("Next ›") }
            }

            SectionTitle("Photos")
            ChipRow(listOf("All") + Poses.all + "Unset", pose) { pose = it }
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { pickFrom = true }) { Text("From ${Dates.medium(from)}") }
                TextButton(onClick = { pickTo = true }) { Text("To ${Dates.medium(to)}") }
            }
            ToggleRow("One photo per day", onePerDay) { onePerDay = it }

            SectionTitle("Timing")
            Text("${fmtNum(seconds.toDouble(), 1)} s per photo", Modifier.padding(horizontal = 16.dp))
            Slider(value = seconds, onValueChange = { seconds = it }, valueRange = 0.3f..3f, modifier = Modifier.padding(horizontal = 16.dp))
            ToggleRow("Cross-fade between photos", fade) { fade = it }

            SectionTitle("Overlay")
            ToggleRow("Date", showDate) { showDate = it }
            ToggleRow("Day / week counter", showDays) { showDays = it }
            ToggleRow("Pose label", showPose) { showPose = it }
            SectionTitle("Data on the video · ${overlays.size} of ${FrameRenderer.MAX_OVERLAYS}")
            Text(
                "Choose up to ${FrameRenderer.MAX_OVERLAYS} metrics, including your custom ones. Show each as a value or as a " +
                    "value with a progress chart. Values come from that date, or the nearest within 7 days (marked ≈).",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
            OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true, label = { Text("Title (optional)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SectionTitle("Video")
            ChipRow(FORMATS.map { it.label }, fmt.label) { l -> formatIdx = FORMATS.indexOfFirst { it.label == l } }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = slides.isNotEmpty(), onClick = {
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
                    OutlinedButton(onClick = { shareFile(ctx, f, "video/mp4") }) { Text("Share video") }
                }
            }
            Text(
                "Videos are made on your phone and saved to Movies/FitLens. Full HD takes longer to create.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (pickFrom) PickDateDialog(from, onDismiss = { pickFrom = false }) { from = it; index = 0 }
    if (pickTo) PickDateDialog(to, onDismiss = { pickTo = false }) { to = it; index = 0 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) }) }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
