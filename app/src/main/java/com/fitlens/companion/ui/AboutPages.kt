package com.fitlens.companion.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
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
        UiEvents.show(ctx.getString(R.string.about_no_browser, url))
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
        title = stringResource(R.string.whats_new_title, version),
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.close),
        secondaryLabel = stringResource(R.string.about_all_releases),
        onSecondary = { openInBrowser(ctx, "$REPO_URL/releases") }
    ) {
        if (notes == null) {
            Text(
                stringResource(R.string.whats_new_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            MarkdownBlocks(notes)
        }
    }
}

/** A short in-app guide (#33): its title, a one-line summary for the list, and its paragraphs, as string resources (#94). */
private class Guide(@StringRes val title: Int, @StringRes val summary: Int, val paragraphs: List<Int>)

private val GUIDES = listOf(
    Guide(R.string.guide_logging, R.string.guide_logging_summary, listOf(R.string.guide_logging_1, R.string.guide_logging_2, R.string.guide_logging_3, R.string.guide_logging_4)),
    Guide(R.string.guide_routines, R.string.guide_routines_summary, listOf(R.string.guide_routines_1, R.string.guide_routines_2)),
    Guide(R.string.guide_comments, R.string.guide_comments_summary, listOf(R.string.guide_comments_1, R.string.guide_comments_2)),
    Guide(R.string.guide_body, R.string.guide_body_summary, listOf(R.string.guide_body_1, R.string.guide_body_2)),
    Guide(R.string.guide_photos, R.string.guide_photos_summary, listOf(R.string.guide_photos_1, R.string.guide_photos_2, R.string.guide_photos_3)),
    Guide(R.string.guide_pdf, R.string.guide_pdf_summary, listOf(R.string.guide_pdf_1)),
    Guide(R.string.guide_backups, R.string.guide_backups_summary, listOf(R.string.guide_backups_1, R.string.guide_backups_2)),
    Guide(R.string.guide_import, R.string.guide_import_summary, listOf(R.string.guide_import_1, R.string.guide_import_2)),
    Guide(R.string.guide_analysis, R.string.guide_analysis_summary, listOf(R.string.guide_analysis_1, R.string.guide_analysis_2))
)

/** Settings → Help (#33): short guides that work offline, then links to the full guide and the issue forms. */
@Composable
fun HelpPage() {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf<Guide?>(null) }
    SettingsGroup(stringResource(R.string.help_group_guides))
    GUIDES.forEach { g -> SettingsActionRow(stringResource(g.title), stringResource(g.summary)) { open = g } }
    SettingsGroup(stringResource(R.string.help_group_more))
    SettingsActionRow(stringResource(R.string.help_guide), stringResource(R.string.help_guide_summary)) {
        openInBrowser(ctx, "$REPO_URL#readme")
    }
    SettingsActionRow(stringResource(R.string.help_report), stringResource(R.string.help_report_summary)) {
        openInBrowser(ctx, "$REPO_URL/issues/new/choose")
    }
    SettingsActionRow(stringResource(R.string.about_all_releases), stringResource(R.string.help_releases_summary)) {
        openInBrowser(ctx, "$REPO_URL/releases")
    }
    SettingsNote(stringResource(R.string.help_links_note))
    open?.let { g ->
        val accent = MaterialTheme.colorScheme.primary
        FitSheet(title = stringResource(g.title), onDismiss = { open = null }, dismissLabel = stringResource(R.string.close)) {
            g.paragraphs.forEach { Text(inlineMarkdown(stringResource(it), accent), style = MaterialTheme.typography.bodyMedium) }
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
    val tapHint = stringResource(R.string.about_copy_hint_toast)
    val copied = stringResource(R.string.about_copied)
    SettingsGroup(stringResource(R.string.about_group_app))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .combinedClickable(
                onClickLabel = stringResource(R.string.about_click_label),
                onLongClickLabel = stringResource(R.string.about_long_click_label),
                onClick = { UiEvents.show(tapHint) },
                onLongClick = {
                    clipboard.setText(
                        AnnotatedString(
                            "FitLens $version (build $code) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                                "${Build.MANUFACTURER} ${Build.MODEL}"
                        )
                    )
                    UiEvents.show(copied)
                }
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.about_version), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.about_version_value, version, code), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.about_copy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    SettingsActionRow(stringResource(R.string.about_whats_new), stringResource(R.string.about_whats_new_summary), sheetOpen = whatsNew) {
        whatsNew = true
    }
    SettingsActionRow(stringResource(R.string.licences_title), stringResource(R.string.about_licences_summary), sheetOpen = licences) {
        licences = true
    }
    SaveSpeed()
    SettingsGroup(stringResource(R.string.about_group_privacy))
    SettingsNote(stringResource(R.string.about_privacy))
    SettingsGroup(stringResource(R.string.about_group_links))
    SettingsActionRow(stringResource(R.string.about_source), stringResource(R.string.about_source_summary)) { openInBrowser(ctx, REPO_URL) }
    SettingsActionRow(stringResource(R.string.about_report), stringResource(R.string.about_report_summary)) {
        openInBrowser(ctx, "$REPO_URL/issues/new/choose")
    }
    SettingsActionRow(stringResource(R.string.about_all_releases), stringResource(R.string.about_releases_summary)) {
        openInBrowser(ctx, "$REPO_URL/releases")
    }
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
    FitSheet(title = stringResource(R.string.licences_title), onDismiss = onDismiss, dismissLabel = stringResource(R.string.close)) {
        SectionLabel(stringResource(R.string.about_group_app))
        Text(stringResource(R.string.licences_fitlens), style = MaterialTheme.typography.bodyMedium)
        SectionLabel(stringResource(R.string.licences_libraries), Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.licences_libraries_text), style = MaterialTheme.typography.bodyMedium)
        SectionLabel(stringResource(R.string.licences_manrope), Modifier.padding(top = 8.dp))
        Text(
            ofl ?: stringResource(R.string.licences_ofl),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
