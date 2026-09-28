package com.fitlens.companion.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.FitShapes
import com.fitlens.companion.ui.LocalChartColors
import com.fitlens.companion.ui.Motion
import com.fitlens.companion.ui.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A labelled numeric field with large − and + buttons either side (#80).
 *
 * [onStep] receives −1 or +1; the caller applies its own increment (the exercise's weight step, one rep, and so on)
 * and clamping. A tap steps once; pressing and holding repeats after a short delay. Each step gives a light haptic
 * tick. A press that turns into a scroll does not step.
 */
@Composable
fun StepperField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Decimal
) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xxs)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StepButton(symbol = "−", description = "Decrease $label") { onStep(-1) }
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.weight(1f)
            )
            StepButton(symbol = "+", description = "Increase $label") { onStep(1) }
        }
    }
}

/** A round 56dp stepper button: steps on release, repeats while held, ticks on every step. */
@Composable
private fun StepButton(symbol: String, description: String, onStep: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(Spacing.stepper)
            .clip(CircleShape)
            .background(if (pressed) Brand.ImperialPurple.copy(alpha = 0.45f) else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick {
                    step()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    var repeated = false
                    val repeater = scope.launch {
                        delay(Motion.REPEAT_DELAY_MS)
                        repeated = true
                        while (true) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            step()
                            delay(Motion.REPEAT_INTERVAL_MS)
                        }
                    }
                    val released = tryAwaitRelease()
                    repeater.cancel()
                    pressed = false
                    if (released && !repeated) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        step()
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * One value in a set's column (#101): [value] in large figures ("85"), a small muted [unit] ("kg") and how TalkBack
 * says it ("85 kilograms").
 */
data class SetCell(val value: String, val unit: String = "", val spoken: String)

/** Width of the set-number column when a row shows [SetCell] columns: room for "12" and a set-type badge. */
private val SetIndexWidth = 48.dp
/** The trailing slots in column mode have fixed widths, so values line up under [SetColumnsHeader]. */
private val SetPrWidth = 32.dp
private val SetDoneWidth = 48.dp
private val SetHintWidth = 64.dp

/** Padding shared by [SetRow] and [SetColumnsHeader], so the header sits over the columns. */
private fun setOuterPadding(framed: Boolean) =
    if (framed) PaddingValues(horizontal = Spacing.md, vertical = 3.dp) else PaddingValues(0.dp)
private fun setInnerPadding(framed: Boolean) =
    if (framed) PaddingValues(horizontal = Spacing.md, vertical = 10.dp) else PaddingValues(start = 18.dp, top = Spacing.xxs)

/**
 * The column headings over a list of [SetRow]s shown as columns (#101): SET, then one label per value ("WEIGHT",
 * "REPS"), over a gold hairline. Pass the same [framed], [hasDone] and [hasHint] as the rows so the headings line up.
 * TalkBack skips it: every row already says what each value is.
 */
@Composable
fun SetColumnsHeader(
    labels: List<String>,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    hasDone: Boolean = false,
    hasHint: Boolean = false
) {
    val style = MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().padding(setOuterPadding(framed)).clearAndSetSemantics { }) {
        Row(
            Modifier.fillMaxWidth().padding(setInnerPadding(framed)).padding(bottom = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SET", Modifier.width(SetIndexWidth), style = style, color = muted)
            Row(Modifier.weight(1f)) {
                labels.forEach { Text(it.uppercase(), Modifier.weight(1f), style = style, color = muted, maxLines = 1) }
            }
            Spacer(Modifier.width(SetPrWidth))
            if (hasDone) Spacer(Modifier.width(SetDoneWidth))
            if (hasHint) Spacer(Modifier.width(SetHintWidth))
        }
        Box(
            Modifier
                .padding(start = if (framed) Spacing.md else 18.dp, end = if (framed) Spacing.md else 0.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Brand.Gold.copy(alpha = 0.35f))
        )
    }
}

/** A value and its unit on one line, in tabular figures so the columns line up down the list (#101). */
@Composable
private fun SetCellText(cell: SetCell, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            cell.value,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
        if (cell.unit.isNotEmpty()) {
            Text(
                cell.unit,
                Modifier.padding(start = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * One logged set (#80): number, what was done, the set's comment, a PR marker, an optional done checkbox and an
 * optional hint on the right such as "Edit".
 *
 * - With [cells] (#101) each value sits in its own column under a [SetColumnsHeader]: "85 kg" under WEIGHT and "6"
 *   under REPS, rather than one "85 kg × 6 reps" line. Without them, [summary] is shown as one line.
 * - [framed] draws the set as its own recessed glass well (#102), picked out in imperial purple with a gold outline
 *   when [selected]; unframed, it is a compact line for use inside an [ExerciseCard] or another clickable container.
 * - TalkBack reads the row as one sentence, for example "Set 2, 85 kilograms, 6 reps, personal record".
 * - [done] is null when the screen has no done state; otherwise a checkbox is shown and [onDoneChange] is called.
 */
@Composable
fun SetRow(
    index: Int,
    summary: String,
    modifier: Modifier = Modifier,
    cells: List<SetCell>? = null,
    comment: String? = null,
    isPr: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    done: Boolean? = null,
    onDoneChange: ((Boolean) -> Unit)? = null,
    trailingHint: String? = null,
    framed: Boolean = true,
    /** A one-letter set-type badge ("W", "D", "F") and how TalkBack says it ("warm-up"). */
    badge: String? = null,
    badgeSpoken: String? = null,
    /** Effort as shown ("RPE 8") and as spoken ("2 reps in reserve"). */
    effort: String? = null,
    effortSpoken: String? = null
) {
    val shape = FitShapes.row
    val spoken = buildString {
        append("Set ").append(index).append(", ")
        if (badgeSpoken != null) append(badgeSpoken).append(", ")
        append(if (cells != null) cells.joinToString(", ") { it.spoken } else summary)
        if (effortSpoken != null) append(", ").append(effortSpoken)
        if (isPr) append(", personal record")
        if (!comment.isNullOrBlank()) append(", comment: ").append(comment)
        if (done == true) append(", done")
    }
    val frame = when {
        !framed -> Modifier.fillMaxWidth()
        selected -> Modifier
            .fillMaxWidth()
            .background(Brand.ImperialPurple.copy(alpha = 0.35f), shape)
            .border(1.dp, Brand.Gold, shape)
            .clip(shape)
        else -> Modifier.fillMaxWidth().recessedGlass(shape)
    }
    val tap = onClick
    val isDone = done
    val toggleDone = onDoneChange
    val click = if (tap != null) Modifier.clickable(onClick = tap) else Modifier
    val columns = cells != null

    Box(modifier.fillMaxWidth().padding(setOuterPadding(framed))) {
        Row(
            frame
                .then(click)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    this.selected = selected
                    if (tap != null) {
                        this.onClick(label = null, action = {
                            tap()
                            true
                        })
                    }
                    if (isDone != null && toggleDone != null) {
                        stateDescription = if (isDone) "Done" else "Not done"
                        customActions = listOf(
                            CustomAccessibilityAction(if (isDone) "Mark not done" else "Mark done") {
                                toggleDone(!isDone)
                                true
                            }
                        )
                    }
                }
                .padding(setInnerPadding(framed)),
            verticalAlignment = if (framed || columns) Alignment.CenterVertically else Alignment.Top
        ) {
            if (columns) {
                Row(Modifier.width(SetIndexWidth), verticalAlignment = Alignment.CenterVertically) {
                    Text("$index", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (badge != null) {
                        Spacer(Modifier.width(6.dp))
                        SetTypeBadge(badge)
                    }
                }
            } else {
                Text("$index", Modifier.width(if (framed) 28.dp else 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                if (cells != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        cells.forEach { SetCellText(it, Modifier.weight(1f)) }
                    }
                    if (effort != null) {
                        Text(effort, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (badge != null) {
                            SetTypeBadge(badge)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(summary, style = MaterialTheme.typography.bodyLarge)
                        if (effort != null) {
                            Text(
                                "  ·  $effort",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (!comment.isNullOrBlank()) {
                    Text(
                        "“$comment”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // #23 replaces this with a live gold trophy; today only imported FitNotes flags are shown.
            if (columns) {
                Box(Modifier.width(SetPrWidth), contentAlignment = Alignment.Center) {
                    if (isPr) Text("PR", color = LocalChartColors.current.accent, fontWeight = FontWeight.Bold)
                }
            } else if (isPr) {
                Text("PR", color = LocalChartColors.current.accent, fontWeight = FontWeight.Bold)
                if (trailingHint != null || done != null) Spacer(Modifier.width(6.dp))
            }
            if (done != null) {
                Checkbox(checked = done, onCheckedChange = onDoneChange)
            }
            if (trailingHint != null) {
                Text(
                    trailingHint,
                    if (columns) Modifier.width(SetHintWidth) else Modifier,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Brand.Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (columns) TextAlign.End else null
                )
            }
        }
    }
}

/**
 * An exercise in a day's workout (#80), in raised glass (#102): a category colour bar on the left, the exercise name in serif, the set rows
 * in [sets] (usually unframed [SetRow]s), an optional comment line and a PR marker. Tapping the card calls [onClick];
 * [menu] adds an overflow button.
 */
@Composable
fun ExerciseCard(
    name: String,
    categoryColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    comment: String? = null,
    hasPr: Boolean = false,
    menu: List<MenuAction> = emptyList(),
    sets: @Composable ColumnScope.() -> Unit
) {
    val shape = FitShapes.card
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .height(IntrinsicSize.Min)
            .raisedGlass(shape)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(categoryColor))
        Column(Modifier.weight(1f).padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (hasPr) {
                    Text(
                        "PR",
                        color = LocalChartColors.current.accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = Spacing.sm)
                    )
                }
                if (menu.isNotEmpty()) OverflowMenu(menu, description = "Options for $name")
            }
            sets()
            if (!comment.isNullOrBlank()) {
                Text(
                    "“$comment”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs, end = Spacing.md)
                )
            }
        }
    }
}

/** The small gold letter that marks a warm-up, drop or failure set (#43). The letter carries the meaning. */
@Composable
fun SetTypeBadge(letter: String) {
    Text(
        letter,
        Modifier
            .border(1.dp, Brand.Gold, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Brand.Gold,
        fontWeight = FontWeight.Bold
    )
}
