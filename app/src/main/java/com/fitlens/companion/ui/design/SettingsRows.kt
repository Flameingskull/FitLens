package com.fitlens.companion.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.FitShapes
import com.fitlens.companion.ui.Motion
import com.fitlens.companion.ui.GoldHairline
import com.fitlens.companion.ui.Spacing
import kotlinx.coroutines.delay

/*
 * The Settings rows (#86, catalogue in #41): every Settings page is built from these, so each setting looks, reads and
 * behaves the same. Each row is at least 56dp tall, the whole row is the touch target, and TalkBack reads it as one
 * sentence: its title, its current value or state, then its explanation. A row that can't be used yet stays in place,
 * dimmed, and says why in its `disabledReason` instead of its explanation (#41), so its state never rests on colour.
 */

/** How much a disabled row is dimmed. */
private const val DISABLED_ALPHA = 0.45f

/**
 * The setting a Settings search result opened its page at (#41, section 5.1). The row or group whose title is [title]
 * scrolls into view and is outlined in gold for a moment, then [onShown] runs, so it happens once per search.
 */
class SettingsTarget(val title: String, val onShown: () -> Unit)

/** The setting the page on screen was opened at, or null. Provided by the Settings page, read by every row here. */
val LocalSettingsTarget = compositionLocalOf<SettingsTarget?> { null }

/** How long the gold outline stays on a searched-for setting before it fades. */
private const val TARGET_HOLD_MS = 1400L

/**
 * When this row or group is the [LocalSettingsTarget], scrolls it into view once the page has laid out, then outlines
 * it in gold and fades the outline away. Elsewhere it changes nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.searchTarget(title: String, shape: Shape = FitShapes.row): Modifier {
    val target = LocalSettingsTarget.current
    if (target == null || target.title != title) return this
    val requester = remember { BringIntoViewRequester() }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Let the page finish sliding in first, so the scroll and the outline are seen.
        delay(Motion.EMPHASIS.toLong())
        requester.bringIntoView()
        glow.snapTo(1f)
        delay(TARGET_HOLD_MS)
        glow.animateTo(0f, tween(Motion.EMPHASIS * 2))
        target.onShown()
    }
    return this
        .bringIntoViewRequester(requester)
        .border(2.dp, Brand.Gold.copy(alpha = glow.value), shape)
}

/** A Settings group: FitNotes's category heading, the name in capitals over a gold rule (#145). */
@Composable
fun SettingsGroup(title: String) {
    SectionLabel(title, Modifier.searchTarget(title).padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.xs))
}

/**
 * A setting that's on or off, with FitNotes's checkbox on the right (#147). Tapping anywhere on the row switches it,
 * and TalkBack reads the row as one checkbox: its title, checked or not, then its explanation.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    summary: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .searchTarget(title)
            .focusRing()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, if (enabled) summary else disabledReason ?: summary, Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.md))
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A setting with a few choices. The row shows the current one in gold (or Not set); tapping it opens a bottom sheet
 * with every choice as a radio row, and an optional line under each in [descriptions]. Picking one closes the sheet.
 * TalkBack reads the row's title and current choice, and the choices as one radio group (#41).
 */
@Composable
fun SettingsChoiceRow(
    title: String,
    options: List<String>,
    selected: Int,
    summary: String? = null,
    descriptions: List<String?> = emptyList(),
    onSelect: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val current = options.getOrNull(selected) ?: stringResource(R.string.settings_not_set)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .searchTarget(title)
            .focusRing()
            .clickable(onClickLabel = stringResource(R.string.settings_change, title)) { open = true }
            .semantics(mergeDescendants = true) { stateDescription = current }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(current, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (!summary.isNullOrBlank()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (open) {
        FitSheet(title = title, onDismiss = { open = false }, dismissLabel = stringResource(R.string.settings_close)) {
            Column(Modifier.selectableGroup()) {
                options.forEachIndexed { i, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.row)
                            .focusRing()
                            .selectable(selected = i == selected, role = Role.RadioButton) {
                                onSelect(i)
                                open = false
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = i == selected, onClick = null)
                        Spacer(Modifier.width(Spacing.md))
                        RowText(label, descriptions.getOrNull(i), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * A row that opens something: a page, a picker or an action. [value] shows the current choice in gold, as on a
 * choice row (a folder, the exercises chosen). A disabled row is dimmed, doesn't respond, and shows [disabledReason].
 * [warning] marks a problem behind the row (automatic backups failing, #41): a warning icon and the text in the error
 * colour, in place of the explanation, so it never rests on colour alone.
 */
@Composable
fun SettingsActionRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    warning: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .searchTarget(title)
            .focusRing()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            val detail = when {
                warning != null -> null
                enabled -> summary
                else -> disabledReason ?: summary
            }
            RowText(title, detail, Modifier, value)
            if (warning != null) WarningLine(warning)
        }
    }
}

/**
 * A folder FitLens reads or writes (#41, section 5.3): the title, the folder's name in gold (or Not set), its
 * explanation, and Choose or Change on the right. When [lost], the phone has withdrawn FitLens's access to the folder,
 * and [lostText] says so with a warning icon in place of the explanation. Tapping anywhere on the row picks a folder.
 */
@Composable
fun SettingsFolderRow(
    title: String,
    folder: String?,
    summary: String? = null,
    lost: Boolean = false,
    lostText: String? = null,
    onChange: () -> Unit
) {
    val warn = lost && lostText != null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .searchTarget(title)
            .focusRing()
            .clickable(onClickLabel = stringResource(R.string.settings_change, title), onClick = onChange)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            RowText(title, if (warn) null else summary, Modifier, folder ?: stringResource(R.string.settings_not_set))
            if (warn) WarningLine(lostText!!)
        }
        Spacer(Modifier.width(Spacing.md))
        Text(
            stringResource(if (folder == null) R.string.settings_folder_choose else R.string.settings_folder_change),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * An action that removes data (#41, section 5.3): a warning icon and the title in the error colour, so it reads as
 * dangerous without relying on colour. The caller always confirms before acting, with the action named on the button.
 */
@Composable
fun SettingsDangerRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .searchTarget(title)
            .focusRing()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            val detail = if (enabled) summary else disabledReason ?: summary
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A whole number set with − and + (#41, section 5.3): the title and explanation on the left, the value between two
 * 48dp buttons on the right. [format] words the value (0 as None, for example). Each button says what it does, and
 * the new value is announced.
 */
@Composable
fun SettingsNumberRow(
    title: String,
    value: Int,
    range: IntRange,
    summary: String? = null,
    format: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit
) {
    val shown = format(value)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .searchTarget(title)
            .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, summary, Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.sm))
        NumberButton("−", stringResource(R.string.settings_number_decrease, title), value > range.first) {
            onChange((value - 1).coerceIn(range))
        }
        Text(
            shown,
            Modifier
                .widthIn(min = Spacing.touch)
                .semantics {
                    contentDescription = title
                    stateDescription = shown
                    liveRegion = LiveRegionMode.Polite
                },
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        NumberButton("+", stringResource(R.string.settings_number_increase, title), value < range.last) {
            onChange((value + 1).coerceIn(range))
        }
    }
}

@Composable
private fun NumberButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Spacing.touch)
            .focusRing(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** One line of a [SettingsStatusCard]: its text, and whether it reports a problem. */
data class StatusLine(val text: String, val problem: Boolean = false)

/**
 * The state of something that runs on its own, such as automatic backups (#41, section 5.3): a raised card with each
 * line beside a tick, or beside a warning icon in the error colour for a problem. TalkBack reads the card as one
 * block, and announces it when a problem appears.
 */
@Composable
fun SettingsStatusCard(lines: List<StatusLine>) {
    if (lines.isEmpty()) return
    val problem = lines.any { it.problem }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .raisedGlass(FitShapes.card, elevation = 2.dp)
            .semantics(mergeDescendants = true) { if (problem) liveRegion = LiveRegionMode.Polite }
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (line.problem) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (line.problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** A problem under a row's title: a warning icon and the text, in the error colour. */
@Composable
private fun WarningLine(text: String) {
    Row(Modifier.padding(top = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/**
 * An explanation under a group or a control that isn't a row (a stepper, a slider). [error] shows a problem, which
 * TalkBack announces when it appears.
 */
@Composable
fun SettingsNote(text: String, error: Boolean = false) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .then(if (error) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RowText(title: String, summary: String?, modifier: Modifier, value: String? = null) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!value.isNullOrBlank()) {
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (!summary.isNullOrBlank()) {
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
