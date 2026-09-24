package com.fitlens.companion.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.FitShapes
import com.fitlens.companion.ui.Spacing

/** Which way a [StatTile]'s delta moved. Always shown with an arrow and words, never by colour alone. */
enum class Trend { Up, Down, Flat }

/**
 * A single statistic (#80): a letter-spaced label, a big serif value, an optional change with an up or down arrow,
 * and an optional date line. TalkBack reads it as one item.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    trend: Trend? = null,
    dateLine: String? = null
) {
    val shape = FitShapes.card
    Column(
        modifier
            .clip(shape)
            .background(Brand.Surface)
            .border(1.dp, Brand.Hairline, shape)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!delta.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (trend) {
                    Trend.Up -> Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Up", tint = Brand.Gold, modifier = Modifier.size(18.dp))
                    Trend.Down -> Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Down", tint = Brand.PurpleLight, modifier = Modifier.size(18.dp))
                    else -> {}
                }
                Text(delta, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!dateLine.isNullOrBlank()) {
            Text(dateLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A list row of at least 56dp (#80): an optional [leading] dot or icon, a title, an optional subtitle and a
 * trailing overflow [menu].
 *
 * When [onMoveUp] or [onMoveDown] is given, a 48dp drag handle appears: dragging it by a row's height calls the
 * matching callback, and TalkBack users get "Move up" / "Move down" actions on the row. The caller reorders its
 * list in the callbacks.
 */
@Composable
fun ListRowWithMenu(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    menu: List<MenuAction> = emptyList(),
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val up by rememberUpdatedState(onMoveUp)
    val down by rememberUpdatedState(onMoveDown)
    val tap = onClick
    val moveActions = listOfNotNull(
        onMoveUp?.let { f -> CustomAccessibilityAction("Move up") { f(); true } },
        onMoveDown?.let { f -> CustomAccessibilityAction("Move down") { f(); true } }
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .then(if (tap != null) Modifier.clickable(onClick = tap) else Modifier)
            .semantics(mergeDescendants = true) {
                if (moveActions.isNotEmpty()) customActions = moveActions
            }
            .padding(start = Spacing.lg, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (menu.isNotEmpty()) OverflowMenu(menu, description = "Options for $title")
        if (onMoveUp != null || onMoveDown != null) {
            Box(
                Modifier
                    .size(Spacing.touch)
                    .semantics { contentDescription = "Drag to reorder $title" }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { total = 0f },
                            onDragCancel = { total = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            total += dragAmount
                            val stepPx = Spacing.row.toPx()
                            if (total <= -stepPx) {
                                up?.invoke()
                                total += stepPx
                            } else if (total >= stepPx) {
                                down?.invoke()
                                total -= stepPx
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
