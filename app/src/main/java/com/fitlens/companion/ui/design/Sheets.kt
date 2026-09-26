@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.Dot
import com.fitlens.companion.ui.FitShapes
import com.fitlens.companion.ui.GoldHairline
import com.fitlens.companion.ui.Spacing

/**
 * A modal bottom sheet in the FitLens style (#80): serif [title], gold hairline, scrolling [content] and a sticky
 * button row. The confirm button appears when both [confirmLabel] and [onConfirm] are given; use a specific verb
 * ("Save set", "Delete workout"), never "OK". [destructive] colours the confirm button as an error action.
 * The caller closes the sheet (usually inside [onConfirm]); [onDismiss] runs on Cancel, back or a tap outside.
 * [secondaryLabel] and [onSecondary] add an outlined button before the confirm button, such as "Save and add another".
 */
@Composable
fun FitSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = FitShapes.sheet,
        containerColor = Brand.Onyx,
        contentColor = Brand.Ivory
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.lg))
            GoldHairline(Modifier.padding(top = Spacing.sm, bottom = Spacing.md))
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                content()
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
                val secondary = onSecondary
                if (secondary != null && secondaryLabel != null) {
                    OutlinedButton(onClick = secondary, enabled = confirmEnabled) { Text(secondaryLabel) }
                }
                val confirm = onConfirm
                if (confirm != null && confirmLabel != null) {
                    Button(
                        onClick = confirm,
                        enabled = confirmEnabled,
                        colors = if (destructive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) { Text(confirmLabel) }
                }
            }
        }
    }
}

/**
 * A confirmation sheet for destructive or significant actions (#80). [confirmLabel] must name the action
 * ("Delete set", "Replace data"); the sheet closes itself after [onConfirm].
 */
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = true
) {
    FitSheet(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = { onConfirm(); onDismiss() },
        destructive = destructive
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One choice in a [SearchablePicker]. [section] is the chip it files under (a category, for example). */
data class PickerItem(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val section: String? = null,
    val color: Color? = null
)

/**
 * A full-height picker sheet (#80) with a search field, section chips (for example categories) and a list.
 *
 * Single-select calls [onPick] with one id as soon as a row is tapped. With [multiSelect], rows get checkboxes and
 * a sticky "Add N" button confirms the choice. The caller closes the sheet in [onPick]. Serves as the category
 * picker too: pass the categories as [PickerItem]s with no sections.
 */
@Composable
fun SearchablePicker(
    title: String,
    items: List<PickerItem>,
    onDismiss: () -> Unit,
    onPick: (List<Long>) -> Unit,
    multiSelect: Boolean = false,
    searchLabel: String = "Search",
    emptyText: String = "Nothing matches that search."
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf<String?>(null) }
    val chosen = remember { mutableStateListOf<Long>() }
    val sections = remember(items) { items.mapNotNull { it.section }.distinct() }
    val q = query.trim()
    val shown = items.filter { item ->
        (section == null || item.section == section) &&
            (q.isEmpty() || item.title.contains(q, ignoreCase = true) || (item.subtitle?.contains(q, ignoreCase = true) == true))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = FitShapes.sheet,
        containerColor = Brand.Onyx,
        contentColor = Brand.Ivory
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.lg))
            GoldHairline(Modifier.padding(top = Spacing.sm, bottom = Spacing.md))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(searchLabel) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )
            if (sections.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item {
                        FilterChip(selected = section == null, onClick = { section = null }, label = { Text("All") })
                    }
                    items(sections) { s ->
                        FilterChip(selected = section == s, onClick = { section = if (section == s) null else s }, label = { Text(s) })
                    }
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                if (shown.isEmpty()) {
                    item {
                        Text(
                            emptyText,
                            modifier = Modifier.padding(Spacing.lg),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(shown, key = { it.id }) { item ->
                    val isChosen = item.id in chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.row)
                            .clickable {
                                if (multiSelect) {
                                    if (isChosen) chosen.remove(item.id) else chosen.add(item.id)
                                } else {
                                    onPick(listOf(item.id))
                                }
                            }
                            .semantics(mergeDescendants = true) {
                                if (multiSelect) contentDescription = item.title + if (isChosen) ", selected" else ""
                            }
                            .padding(horizontal = Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val c = item.color
                        if (c != null) {
                            Dot(c, Spacing.md)
                            Spacer(Modifier.width(Spacing.md))
                        }
                        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val sub = item.subtitle
                            if (!sub.isNullOrBlank()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (multiSelect) Checkbox(checked = isChosen, onCheckedChange = null)
                    }
                }
            }
            if (multiSelect) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onPick(chosen.toList()) }, enabled = chosen.isNotEmpty()) {
                        Text(if (chosen.isEmpty()) "Add" else "Add ${chosen.size}")
                    }
                }
            }
        }
    }
}
