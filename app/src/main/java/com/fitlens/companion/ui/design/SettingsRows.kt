package com.fitlens.companion.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.fitlens.companion.ui.GoldHairline
import com.fitlens.companion.ui.Spacing

/*
 * The Settings rows (#86, catalogue in #41): every Settings page is built from these, so each setting looks, reads and
 * behaves the same. Each row is at least 56dp tall, the whole row is the touch target, and TalkBack reads it as one
 * sentence: its title, its current value or state, then its explanation.
 */

/** A Settings group: FitNotes's category heading, the name in capitals over a gold rule (#145). */
@Composable
fun SettingsGroup(title: String) {
    SectionLabel(title, Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.xs))
}

/**
 * A setting that's on or off, with FitNotes's checkbox on the right (#147). Tapping anywhere on the row switches it.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    summary: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, summary, Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.md))
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A setting with a few choices. The row shows the current one in gold; tapping it opens a bottom sheet with every
 * choice as a radio row, and an optional line under each in [descriptions]. Picking one closes the sheet.
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
    val current = options.getOrNull(selected).orEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .clickable(onClickLabel = "Change $title") { open = true }
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
        FitSheet(title = title, onDismiss = { open = false }, dismissLabel = "Close") {
            options.forEachIndexed { i, label ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.row)
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

/**
 * A row that opens something: a page, a picker or an action. [value] shows the current choice in gold, as on a
 * choice row (a folder, the exercises chosen). A disabled row is dimmed and doesn't respond.
 */
@Composable
fun SettingsActionRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title, summary, Modifier.weight(1f), value)
    }
}

/** An explanation under a group or a control that isn't a row (a stepper, a slider). [error] shows a problem. */
@Composable
fun SettingsNote(text: String, error: Boolean = false) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
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
