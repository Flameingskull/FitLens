package com.fitlens.companion.ui.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.ui.FitShapes
import com.fitlens.companion.ui.GoldHairline
import com.fitlens.companion.ui.PickDateDialog
import com.fitlens.companion.ui.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RelativeDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/**
 * How a day reads in a [DayNavigator]: "Today", "Yesterday" or "Tomorrow", otherwise a short weekday date
 * ("Mon 3 Aug"), with the year added when it isn't the current one. [date] is an ISO date.
 */
fun relativeDayLabel(date: String, today: LocalDate = LocalDate.now()): String {
    val d = Dates.parse(date) ?: return date
    return when (d.toEpochDay() - today.toEpochDay()) {
        0L -> "Today"
        -1L -> "Yesterday"
        1L -> "Tomorrow"
        else -> if (d.year == today.year) d.format(RelativeDayFormat) else Dates.long(date)
    }
}

/**
 * Previous / date / next bar for day-based screens (#80).
 *
 * - The arrows call [onPrevious] and [onNext]; pass null to disable one (for example when there is no earlier day).
 * - Tapping the date opens a date picker and reports the ISO date through [onPickDate].
 * - Long-pressing the date calls [onToday], when given.
 * - A horizontal swipe on the bar itself moves a day: right for previous, left for next. The swipe is on the bar
 *   only, so it never competes with list scrolling or a tab pager below it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayNavigator(
    date: String,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onPickDate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onToday: (() -> Unit)? = null,
    previousDescription: String = "Previous day",
    nextDescription: String = "Next day"
) {
    var picking by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val label = relativeDayLabel(date)
    val full = Dates.long(date)
    val longClick: (() -> Unit)? = if (onToday == null) null else {
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onToday()
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.row)
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            val threshold = 56.dp.toPx()
                            if (total > threshold) previous?.invoke() else if (total < -threshold) next?.invoke()
                            total = 0f
                        },
                        onDragCancel = { total = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                    }
                }
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = previousDescription)
            }
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = Spacing.touch)
                    .clip(FitShapes.row)
                    .combinedClickable(
                        onClickLabel = "Choose a date",
                        onLongClickLabel = if (onToday != null) "Go to today" else null,
                        onLongClick = longClick,
                        onClick = { picking = true }
                    )
                    .padding(vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (label != full) {
                    Text(
                        full.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = nextDescription)
            }
        }
        GoldHairline()
    }

    if (picking) PickDateDialog(date, onDismiss = { picking = false }) { onPickDate(it) }
}
