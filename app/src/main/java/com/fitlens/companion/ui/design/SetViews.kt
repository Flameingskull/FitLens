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
 * One logged set (#80): number, what was done (for example "100 kg × 5 reps", built by the caller), the set's
 * comment, a PR marker, an optional done checkbox and an optional hint on the right such as "Edit".
 *
 * - [framed] draws the set as its own card, picked out in imperial purple with a gold outline when [selected];
 *   unframed, it is a compact line for use inside an [ExerciseCard] or another clickable container.
 * - TalkBack reads the row as one sentence, for example "Set 2, 100 kg × 5 reps, personal record".
 * - [done] is null when the screen has no done state; otherwise a checkbox is shown and [onDoneChange] is called.
 */
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

@Composable
fun SetRow(
    index: Int,
    summary: String,
    modifier: Modifier = Modifier,
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
        append(summary)
        if (effortSpoken != null) append(", ").append(effortSpoken)
        if (isPr) append(", personal record")
        if (!comment.isNullOrBlank()) append(", comment: ").append(comment)
        if (done == true) append(", done")
    }
    val frame = if (framed) {
        Modifier
            .fillMaxWidth()
            .background(if (selected) Brand.ImperialPurple.copy(alpha = 0.35f) else Brand.Surface, shape)
            .border(1.dp, if (selected) Brand.Gold else Brand.Hairline, shape)
            .clip(shape)
    } else {
        Modifier.fillMaxWidth()
    }
    val tap = onClick
    val isDone = done
    val toggleDone = onDoneChange
    val click = if (tap != null) Modifier.clickable(onClick = tap) else Modifier
    val inner = if (framed) PaddingValues(horizontal = Spacing.md, vertical = 10.dp) else PaddingValues(start = 18.dp, top = Spacing.xxs)

    Box(modifier.fillMaxWidth().padding(if (framed) PaddingValues(horizontal = Spacing.md, vertical = 3.dp) else PaddingValues(0.dp))) {
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
                .padding(inner),
            verticalAlignment = if (framed) Alignment.CenterVertically else Alignment.Top
        ) {
            Text("$index", Modifier.width(if (framed) 28.dp else 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
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
                if (!comment.isNullOrBlank()) {
                    Text(
                        "“$comment”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // #23 replaces this with a live gold trophy; today only imported FitNotes flags are shown.
            if (isPr) {
                Text("PR", color = LocalChartColors.current.accent, fontWeight = FontWeight.Bold)
                if (trailingHint != null || done != null) Spacer(Modifier.width(6.dp))
            }
            if (done != null) {
                Checkbox(checked = done, onCheckedChange = onDoneChange)
            }
            if (trailingHint != null) {
                Text(
                    trailingHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Brand.Gold else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * An exercise in a day's workout (#80): a category colour bar on the left, the exercise name in serif, the set rows
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
            .clip(shape)
            .background(Brand.Surface)
            .border(1.dp, Brand.Hairline, shape)
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
