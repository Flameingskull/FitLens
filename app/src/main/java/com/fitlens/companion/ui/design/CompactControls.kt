package com.fitlens.companion.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.ui.Spacing

/*
 * Compact controls (#115): one small dropdown per choice and one menu for on/off options, instead of rows of chips,
 * so graphs and data get the screen. Each keeps a 48dp touch target and is read by TalkBack as its name and current
 * choice.
 */

/** A choice shown as its current value in gold with ▾; tapping opens a menu of every option, the chosen one ticked. */
@Composable
fun DropdownPill(
    label: String,
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val current = options.getOrNull(selected).orEmpty()
    Box(modifier) {
        TextButton(
            onClick = { open = true },
            contentPadding = PaddingValues(start = Spacing.sm, end = Spacing.xs),
            modifier = Modifier.heightIn(min = Spacing.touch).semantics { contentDescription = "$label: $current. Change" }
        ) {
            Text(current, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(
                    text = { Text(o) },
                    onClick = { onSelect(i); open = false },
                    leadingIcon = {
                        if (i == selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        else Box(Modifier.size(24.dp))
                    }
                )
            }
        }
    }
}

/** One on/off option in an [OptionsMenu]. */
data class ToggleOption(val label: String, val on: Boolean, val onToggle: () -> Unit)

/** A one-off command in an [OptionsMenu], such as "Share graph" (#22). It closes the menu. */
data class MenuAction(val label: String, val onClick: () -> Unit)

/**
 * A ⋮ button with on/off options (a tick shows which are on). Stays open so several can be changed at once; any
 * [actions] follow the options and close the menu.
 */
@Composable
fun OptionsMenu(options: List<ToggleOption>, description: String = "Graph options", actions: List<MenuAction> = emptyList()) {
    if (options.isEmpty() && actions.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.label) },
                    onClick = o.onToggle,
                    leadingIcon = {
                        if (o.on) Icon(Icons.Filled.Check, contentDescription = "On", tint = MaterialTheme.colorScheme.primary)
                        else Box(Modifier.size(24.dp))
                    }
                )
            }
            actions.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.label) },
                    onClick = { open = false; a.onClick() },
                    leadingIcon = { Box(Modifier.size(24.dp)) }
                )
            }
        }
    }
}

/**
 * A period choice: [options] followed by "Custom…", which opens a date-range picker. [selected] is -1 when the custom
 * range is in use, and the pill then shows its dates.
 */
@Composable
fun PeriodDropdown(
    label: String,
    options: List<String>,
    selected: Int,
    custom: Pair<String, String>?,
    onSelect: (Int) -> Unit,
    onCustom: (from: String, to: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var picking by remember { mutableStateOf(false) }
    val customLabel = if (selected < 0 && custom != null) "${Dates.short(custom.first)} – ${Dates.short(custom.second)}" else "Custom…"
    DropdownPill(label, options + customLabel, if (selected < 0) options.size else selected, modifier) { i ->
        if (i < options.size) onSelect(i) else picking = true
    }
    if (picking) {
        DateRangePickerDialog(
            initialFrom = custom?.first,
            initialTo = custom?.second,
            onDismiss = { picking = false },
            onPicked = { from, to -> onCustom(from, to) }
        )
    }
}
