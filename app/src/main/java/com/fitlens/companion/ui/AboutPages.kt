package com.fitlens.companion.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.WriteTimings
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import java.io.IOException

internal const val REPO_URL = "https://github.com/Flameingskull/FitLens"

/** Opens [url] in the phone's browser. FitLens itself never needs the internet (#33). */
internal fun openInBrowser(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        UiEvents.show("There's no browser on this phone to open $url")
    }
}

/** This install's version name ("1.0.97") and version code. */
internal fun appVersion(ctx: Context): Pair<String, Long> = try {
    val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    (info.versionName ?: "?") to info.longVersionCode
} catch (e: Exception) {
    "?" to 0L
}

/** The release notes bundled in the APK at build time (#33), so "What's new" works offline. Read once. */
internal object ReleaseNotes {
    @Volatile private var cached: String? = null

    fun load(ctx: Context): String? = cached ?: try {
        ctx.assets.open("release_notes.md").bufferedReader().use { it.readText() }.also { cached = it }
    } catch (e: IOException) {
        null
    }
}

/** One block of the release notes: a heading, a paragraph, or a bullet at a depth (0 = top level). */
internal sealed class MdBlock {
    data class Heading(val text: String) : MdBlock()
    data class Para(val text: String) : MdBlock()
    data class Bullet(val text: String, val level: Int) : MdBlock()
}

/**
 * The plain Markdown the release notes use (#33): `#` headings, paragraphs, `-` bullets nested by indent, and lines
 * that continue the block above them. Anything else reads as a paragraph. Pure, so it's unit tested.
 */
internal fun parseMarkdown(text: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    var open: MdBlock? = null
    fun close() { open?.let(out::add); open = null }
    for (raw in text.lines()) {
        val line = raw.trimEnd()
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length
        when {
            trimmed.isEmpty() -> close()
            trimmed.startsWith("#") -> { close(); out += MdBlock.Heading(trimmed.trimStart('#').trim()) }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                close()
                open = MdBlock.Bullet(trimmed.drop(2).trim(), (indent / 2).coerceIn(0, 2))
            }
            else -> open = when (val o = open) {
                is MdBlock.Bullet -> o.copy(text = o.text + " " + trimmed)
                is MdBlock.Para -> o.copy(text = o.text + " " + trimmed)
                else -> MdBlock.Para(trimmed.removePrefix(">").trim())
            }
        }
    }
    close()
    return out
}

/** `**bold**` in [accent], `` `code` `` and [links](url) as their plain text. */
internal fun inlineMarkdown(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    val plain = text.replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1").replace("`", "")
    val parts = plain.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1 && i < parts.lastIndex) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = accent)) { append(part) }
        else append(if (i % 2 == 1) "**$part" else part)
    }
}

/** Release notes drawn in the FitLens type: section labels, body text and indented bullets. */
@Composable
internal fun MarkdownBlocks(text: String) {
    val blocks = remember(text) { parseMarkdown(text) }
    val accent = MaterialTheme.colorScheme.primary
    blocks.forEach { b ->
        when (b) {
            is MdBlock.Heading -> SectionLabel(b.text, Modifier.padding(top = 8.dp))
            is MdBlock.Para -> Text(inlineMarkdown(b.text, accent), style = MaterialTheme.typography.bodyMedium)
            is MdBlock.Bullet -> Row(Modifier.padding(start = (b.level * 16).dp)) {
                Text(if (b.level == 0) "•" else "◦", Modifier.width(16.dp), color = accent, style = MaterialTheme.typography.bodyMedium)
                Text(inlineMarkdown(b.text, accent), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * "What's new" (#33): this version's release notes, from the APK. Shown once after each update, and any time from
 * Settings → Change Log or About.
 */
@Composable
fun WhatsNewSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { appVersion(ctx).first }
    val notes = remember { ReleaseNotes.load(ctx) }
    FitSheet(
        title = "What's new in $version",
        onDismiss = onDismiss,
        dismissLabel = "Close",
        secondaryLabel = "All releases",
        onSecondary = { openInBrowser(ctx, "$REPO_URL/releases") }
    ) {
        if (notes == null) {
            Text(
                "This build doesn't include its release notes. Every release's notes are on GitHub.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            MarkdownBlocks(notes)
        }
    }
}

/** A short in-app guide (#33): its title, a one-line summary for the list, and its paragraphs. */
private class Guide(val title: String, val summary: String, val paragraphs: List<String>)

private val GUIDES = listOf(
    Guide(
        "Logging a workout", "The day log, sets, the rest timer and the workout timer",
        listOf(
            "The day log is home. Use ‹ and › or swipe to move between days, and Calendar to jump to any date.",
            "Tap + to open the exercise library and pick an exercise. Its Track tab logs sets: enter the weight and reps " +
                "(or distance and time), then **Save**. Tap a saved set to change or delete it. Each set has its own " +
                "comment button and a done checkbox.",
            "The alarm clock in the top bar starts the rest timer; while it runs, its countdown replaces the icon. " +
                "The workout drawer lists the day's exercises, so you can move between them mid-workout.",
            "The History and Graph tabs show every earlier session of the exercise."
        )
    ),
    Guide(
        "Workouts and routines", "Groups of exercises by day, added in one go",
        listOf(
            "A workout is a set of exercises grouped into days you name, such as \"Push Day\" or \"Monday\". From the " +
                "day log's ⋮ menu, **Add workout** adds a whole day's exercises and its planned sets at once.",
            "**Create workout from this day** turns what you logged into a workout to repeat. **Copy workout** copies a " +
                "day to another date."
        )
    ),
    Guide(
        "Comments", "Notes on the day, on an exercise or on one set",
        listOf(
            "A workout comment is about the whole day. An exercise comment is a longer note on that exercise in the " +
                "workout: the last one is shown the next time you log the exercise, and every one is in its History.",
            "Each set also has its own comment, from the speech-bubble button on its row."
        )
    ),
    Guide(
        "Body tracker", "Bodyweight, body fat and your own measurements",
        listOf(
            "Open it from the day log's ⋮ menu. Track lists each measurement with its latest value and change; tap one " +
                "to log a value. History and Graph show them over time, and the pencil edits the list of measurements.",
            "On an exercise graph, the ⋮ menu can draw a body measurement over the graph on its own scale, and the " +
                "Relative Strength graph divides your estimated 1RM by your bodyweight."
        )
    ),
    Guide(
        "Progress photos", "Importing, poses, comparing and the slideshow",
        listOf(
            "Open Photos from the day log's ⋮ menu. **+** imports photos; the ⋮ menu imports a whole folder and checks " +
                "photo dates. Each photo can have a pose (front, side, back or other).",
            "Open a photo to view it full screen, or compare two photos side by side. Tapping a point on a " +
                "training graph shows the progress photo nearest that date.",
            "The ▶ button plays a slideshow, which can be saved as a video in Movies/FitLens. Settings → Progress Photos " +
                "& Media sets the defaults."
        )
    ),
    Guide(
        "PDF report", "A printable summary of your training and photos",
        listOf(
            "Settings → Backup and restore → **Create PDF report** makes a report with dark or light pages and up to " +
                "four photos per day. It's made on your phone."
        )
    ),
    Guide(
        "Backups and restore", "Keeping your data safe, on your phone only",
        listOf(
            "Settings → Backup and restore saves or shares a .fitlens backup with everything: workouts, body values, " +
                "photos and settings. Restore replaces what's on the phone, after a safety copy.",
            "Automatic backups go to a folder you choose, daily or weekly. Nothing is ever uploaded: FitLens has no " +
                "account, no cloud service and no internet permission."
        )
    ),
    Guide(
        "Importing from FitNotes", "Bring your FitNotes history across",
        listOf(
            "Settings → Import from FitNotes merges a FitNotes backup into FitLens. Imports only add what's missing: " +
                "they never wipe or overwrite anything you've logged in FitLens.",
            "You can import again at any time, or let FitLens sync a FitNotes backup folder."
        )
    ),
    Guide(
        "Analysis and graphs", "Totals, breakdowns, records and pinned graphs",
        listOf(
            "Open Analysis from the day log's ⋮ menu: Workouts totals, a Breakdown by category or exercise, every " +
                "exercise's graphs, goals and a records board.",
            "Every graph can be drawn as a line, bar, area or step chart, opened full screen to zoom, given a trend, " +
                "compared with other exercises and pinned to the Analysis overview with the star."
        )
    )
)

/** Settings → Help (#33): short guides that work offline, then links to the full guide and the issue forms. */
@Composable
fun HelpPage() {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf<Guide?>(null) }
    SettingsGroup("Guides")
    GUIDES.forEach { g -> SettingsActionRow(g.title, g.summary) { open = g } }
    SettingsGroup("More help")
    SettingsActionRow("The FitLens guide", "Every feature in detail, on GitHub") { openInBrowser(ctx, "$REPO_URL#readme") }
    SettingsActionRow("Report a problem or suggest a feature", "The bug report and feature request forms") {
        openInBrowser(ctx, "$REPO_URL/issues/new/choose")
    }
    SettingsActionRow("All releases", "What changed in every update") { openInBrowser(ctx, "$REPO_URL/releases") }
    SettingsNote("These links open in your browser. FitLens itself has no internet access.")
    open?.let { g ->
        val accent = MaterialTheme.colorScheme.primary
        FitSheet(title = g.title, onDismiss = { open = null }, dismissLabel = "Close") {
            g.paragraphs.forEach { Text(inlineMarkdown(it, accent), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/**
 * Settings → About (#33): the version (a long-press copies it with the phone's details, for bug reports), What's new,
 * privacy, open-source licences and the project's links.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutPage() {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val (version, code) = remember { appVersion(ctx) }
    var whatsNew by remember { mutableStateOf(false) }
    var licences by remember { mutableStateOf(false) }
    SettingsGroup("FitLens")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .combinedClickable(
                onClickLabel = "Show how to copy",
                onLongClickLabel = "Copy version details",
                onClick = { UiEvents.show("Long-press to copy the version details for a bug report") },
                onLongClick = {
                    clipboard.setText(
                        AnnotatedString(
                            "FitLens $version (build $code) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                                "${Build.MANUFACTURER} ${Build.MODEL}"
                        )
                    )
                    UiEvents.show("Version details copied")
                }
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Version", style = MaterialTheme.typography.bodyLarge)
            Text("$version (build $code)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "Long-press to copy it with your phone's details, for a bug report",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    SettingsActionRow("What's new", "This update's release notes") { whatsNew = true }
    SettingsActionRow("Open-source licences", "FitLens, the Manrope font and the libraries it's built with") { licences = true }
    SaveSpeed()
    SettingsGroup("Privacy")
    SettingsNote(
        "FitLens is a workout log that pairs your training with progress photos. It works entirely on this phone: " +
            "no account, no cloud service and no internet permission. Your data leaves the phone only in backups, " +
            "reports, videos and images you save or share yourself."
    )
    SettingsGroup("Links")
    SettingsActionRow("Source code", "FitLens is free and open source") { openInBrowser(ctx, REPO_URL) }
    SettingsActionRow("Report a problem", "The bug report form") { openInBrowser(ctx, "$REPO_URL/issues/new/choose") }
    SettingsActionRow("All releases", "Every update and its notes") { openInBrowser(ctx, "$REPO_URL/releases") }
    if (whatsNew) WhatsNewSheet { whatsNew = false }
    if (licences) LicencesSheet { licences = false }
}

/**
 * How long saving takes on this phone (#60), from [WriteTimings]: the last set save split into the database write and
 * updating the screens, the median of the recent set saves, and the history size it was measured against. Comparing it
 * as history grows is the check that saving a set stays fast. Only this session's saves; nothing is stored.
 */
@Composable
private fun SaveSpeed() {
    val timings by WriteTimings.recent.collectAsState()
    val snap by Store.snapshot.collectAsState()
    SettingsGroup(stringResource(R.string.speed_title))
    val sets = timings.filter { it.kind == WriteTimings.SET }
    val last = sets.lastOrNull()
    if (last == null) {
        SettingsNote(stringResource(R.string.speed_none))
    } else {
        val median = WriteTimings.median(sets.map { it.totalMs }) ?: last.totalMs
        val setCount = snap?.sets?.size ?: last.sets
        val photoCount = snap?.photos?.size ?: 0
        SettingsNote(
            listOf(
                stringResource(R.string.speed_last, last.totalMs, last.dbMs, last.refreshMs),
                pluralStringResource(R.plurals.speed_median, sets.size, median, sets.size),
                pluralStringResource(R.plurals.speed_history_sets, setCount, setCount) + " · " +
                    pluralStringResource(R.plurals.speed_history_photos, photoCount, photoCount)
            ).joinToString("\n")
        )
    }
}

/** The licences FitLens ships under and with (#33). The Manrope licence is the full text bundled in the APK. */
@Composable
private fun LicencesSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val ofl = remember {
        try { ctx.resources.openRawResource(R.raw.manrope_ofl).bufferedReader().use { it.readText() } } catch (e: Exception) { null }
    }
    FitSheet(title = "Open-source licences", onDismiss = onDismiss, dismissLabel = "Close") {
        SectionLabel("FitLens")
        Text("MIT License. Copyright (c) 2026 Flameingskull.", style = MaterialTheme.typography.bodyMedium)
        SectionLabel("Libraries", Modifier.padding(top = 8.dp))
        Text(
            "Apache License 2.0: AndroidX (Core, Activity, Lifecycle, Navigation, Compose, Material 3, WorkManager, " +
                "DataStore, ExifInterface), Kotlin, kotlinx.coroutines, kotlinx.serialization and Coil.",
            style = MaterialTheme.typography.bodyMedium
        )
        SectionLabel("Manrope font", Modifier.padding(top = 8.dp))
        Text(
            ofl ?: "SIL Open Font License 1.1.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
