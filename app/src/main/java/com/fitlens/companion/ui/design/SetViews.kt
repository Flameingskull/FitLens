package com.fitlens.companion.ui.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
import androidx.compose.ui.unit.sp
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.FitShapes
import com.fitlens.companion.ui.LocalChartColors
import com.fitlens.companion.ui.Motion
import com.fitlens.companion.ui.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A FitNotes section label (owner, 2026-10-02): the [text] in uppercase over a fine gold rule, as FitNotes heads its
 * Track fields ("WEIGHT (kgs)") and its History days. Used by [StepperField] and anywhere a list needs a heading.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = Spacing.xxs)
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brand.Gold.copy(alpha = 0.7f)))
    }
}

/**
 * A labelled numeric field laid out as FitNotes's Track tab (#80, #136): a [SectionLabel], then the value centred
 * between square − and + boxes.
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
        SectionLabel(label)
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally)
        ) {
            StepButton(symbol = "−", description = "Decrease $label") { onStep(-1) }
            val outline = MaterialTheme.colorScheme.outline
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(Brand.Gold),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(min = 96.dp, max = 180.dp)
                    .height(Spacing.touch)
                    // A step is announced with its new value (#41).
                    .semantics {
                        contentDescription = label
                        liveRegion = LiveRegionMode.Polite
                    }
                    .drawBehind {
                        // FitNotes's underline under the value, as a fine rule.
                        val y = size.height - 1.dp.toPx()
                        drawLine(outline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    },
                decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } }
            )
            StepButton(symbol = "+", description = "Increase $label") { onStep(1) }
        }
    }
}

/** A square 48dp stepper box, as FitNotes's: steps on release, repeats while held, ticks on every step. */
@Composable
private fun StepButton(symbol: String, description: String, onStep: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(Spacing.touch)
            .raisedGlass(FitShapes.row, elevation = 2.dp, inset = 6.dp)
            .background(if (pressed) Brand.Gold.copy(alpha = 0.16f) else Color.Transparent, FitShapes.row)
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
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * One value in a set's column (#101, #112): [value] in large figures ("85"), a small muted [unit] after it ("kg",
 * "reps") and how TalkBack says it ("85 kilograms"). [unitSlot] keeps room for the unit even when this value has none
 * ("BW", "—"), so the column's figures still line up; a time ("1:30") has no unit and passes false.
 */
data class SetCell(val value: String, val unit: String = "", val spoken: String, val unitSlot: Boolean = true)

/** Width of the set-number column on the Track tab: room for "12" and a set-type badge. */
private val SetIndexWidth = 40.dp
/** The trailing slots have fixed widths, so the value columns line up down the list. */
private val SetMarkWidth = 32.dp
private val SetMarkWideWidth = 40.dp
private val SetDoneWidth = 48.dp
private val SetHintWidth = 64.dp
/** The unit after a value: room for "reps" or "lbs", so the figures before it line up (#112). */
private val SetUnitWidth = 28.dp

private fun setOuterPadding(framed: Boolean) =
    if (framed) PaddingValues(horizontal = Spacing.lg) else PaddingValues(0.dp)
private fun setInnerPadding(framed: Boolean, hasCommentButton: Boolean) = when {
    // The 48dp comment button supplies the row's height and its start padding.
    framed && hasCommentButton -> PaddingValues(end = Spacing.md, top = 2.dp, bottom = 2.dp)
    framed -> PaddingValues(horizontal = Spacing.md, vertical = 10.dp)
    else -> PaddingValues(start = 18.dp, top = 6.dp, bottom = 6.dp, end = Spacing.md)
}

/**
 * A value and its unit, right-aligned in its column as FitNotes shows them ("144.0 kgs   3 reps", #112), in tabular
 * figures so the columns line up down the list.
 */
@Composable
private fun SetCellText(cell: SetCell, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text(
            cell.value,
            Modifier.alignByBaseline(),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        if (cell.unitSlot) {
            Text(
                cell.unit,
                Modifier.alignByBaseline().padding(start = 3.dp).widthIn(min = SetUnitWidth),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * One logged set (#80): what was done, the set's comment, a PR marker, an optional done checkbox and an optional hint
 * on the right such as "Edit".
 *
 * - With [cells] (#101, #112) each value sits right-aligned in its own column with its unit after it, "85 kg   6 reps",
 *   and there's no heading row, as in FitNotes. Without them, [summary] is shown as one line.
 * - [showIndex] shows the set number, as the Track tab does. The day log and History leave it out, as FitNotes does;
 *   the set-type badge then sits beside the PR marker.
 * - [onComment] (#108) adds a speech-bubble button at the row's start, gold and filled when the set has a comment,
 *   outlined when it hasn't. It opens the set's Comment box without selecting the row.
 * - [framed] draws the set as a full row over a fine rule, as FitNotes lists them on its Track tab (#141), picked
 *   out in a gold wash when [selected]; unframed, it is a compact line for use inside an [ExerciseCard] or another
 *   clickable container.
 * - TalkBack reads the row as one sentence, for example "Set 2, 85 kilograms, 6 reps, personal record". The comment
 *   button is a separate stop.
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
    showIndex: Boolean = true,
    onComment: (() -> Unit)? = null,
    /** A one-letter set-type badge ("W", "D", "F") and how TalkBack says it ("warm-up"). */
    badge: String? = null,
    badgeSpoken: String? = null,
    /** Effort as shown ("RPE 8") and as spoken ("2 reps in reserve"). */
    effort: String? = null,
    effortSpoken: String? = null,
    /** What TalkBack calls the row: "Set 2", or "Value 2" for a body measurement. */
    noun: String = "Set"
) {
    val shape = FitShapes.row
    val spoken = buildString {
        append(noun).append(' ').append(index).append(", ")
        if (badgeSpoken != null) append(badgeSpoken).append(", ")
        append(if (cells != null) cells.joinToString(", ") { it.spoken } else summary)
        if (effortSpoken != null) append(", ").append(effortSpoken)
        if (isPr) append(", personal record")
        if (!comment.isNullOrBlank()) append(", comment: ").append(comment)
        if (done == true) append(", done")
    }
    // FitNotes's Track rows (#141): values on the glass with a rule under each, no box; the selected set is washed in
    // gold, as FitNotes washes it in blue.
    val rule = Brand.Hairline
    val frame = when {
        !framed -> Modifier.fillMaxWidth()
        else -> Modifier
            .fillMaxWidth()
            .background(if (selected) Brand.Gold.copy(alpha = 0.16f) else Color.Transparent)
            .drawBehind {
                val y = size.height - 1.dp.toPx() / 2
                drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
    }
    val tap = onClick
    val isDone = done
    val toggleDone = onDoneChange
    val click = if (tap != null) Modifier.clickable(onClick = tap) else Modifier
    val columns = cells != null
    val hasComment = !comment.isNullOrBlank()
    val align = if (framed || columns) Alignment.CenterVertically else Alignment.Top

    Box(modifier.fillMaxWidth().padding(setOuterPadding(framed))) {
        Row(frame.then(click).padding(setInnerPadding(framed, onComment != null)), verticalAlignment = align) {
            if (onComment != null) {
                val label = if (hasComment) "Edit comment on ${noun.lowercase()} $index" else "Add comment to ${noun.lowercase()} $index"
                IconButton(onClick = onComment) {
                    Icon(
                        if (hasComment) FitIcons.Comment else FitIcons.CommentOutline,
                        contentDescription = label,
                        tint = if (hasComment) Brand.Gold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                Modifier
                    .weight(1f)
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
                    },
                verticalAlignment = align
            ) {
                if (columns && showIndex) {
                    Row(Modifier.width(SetIndexWidth), verticalAlignment = Alignment.CenterVertically) {
                        Text("$index", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (badge != null) {
                            Spacer(Modifier.width(6.dp))
                            SetTypeBadge(badge)
                        }
                    }
                } else if (!columns) {
                    Text("$index", Modifier.width(if (framed) 28.dp else 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f)) {
                    if (cells != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            cells.forEach { SetCellText(it, Modifier.weight(1f)) }
                        }
                        if (effort != null) {
                            Text(
                                effort,
                                Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End
                            )
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
                    if (hasComment) {
                        Text(
                            "“$comment”",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // #23 replaces this with a live gold trophy; today only imported FitNotes flags are shown.
                if (columns) {
                    val badgeHere = !showIndex && badge != null
                    Row(
                        Modifier.width(if (showIndex) SetMarkWidth else SetMarkWideWidth),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (badgeHere) SetTypeBadge(badge ?: "")
                        if (isPr) {
                            Text(
                                "PR",
                                Modifier.padding(start = if (badgeHere) 4.dp else 0.dp),
                                color = LocalChartColors.current.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (isPr) {
                    Text("PR", color = LocalChartColors.current.accent, fontWeight = FontWeight.Bold)
                    if (trailingHint != null || done != null) Spacer(Modifier.width(6.dp))
                }
                if (done != null) {
                    Box(Modifier.width(SetDoneWidth), contentAlignment = Alignment.Center) {
                        Checkbox(checked = done, onCheckedChange = onDoneChange)
                    }
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
}

/**
 * The Comment box for one set (#108), as FitNotes opens it from the speech bubble: the set's text, Cancel and Save.
 * [describe] names the set ("Set 2 · 85 kg · 6 reps"). [onSave] receives the trimmed text, or null when it's empty,
 * which removes the comment.
 */
/**
 * The exercise comment row (#107): a note on this exercise in one day's workout, under its sets. Shows the comment
 * (or "Add exercise comment") beside a speech bubble, gold when there is one; tapping it opens [onEdit].
 */
@Composable
fun ExerciseCommentRow(
    comment: String?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    /** The exercise's comment from the last time it was logged, as (when, text), so notes carry forward (owner, 2026-10-03). */
    previous: Pair<String, String>? = null
) {
    val has = !comment.isNullOrBlank()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch)
            .clickable(onClickLabel = if (has) "Edit exercise comment" else "Add exercise comment", onClick = onEdit)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (has) FitIcons.Comment else FitIcons.CommentOutline,
            contentDescription = null,
            tint = if (has) Brand.Gold else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text("EXERCISE COMMENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (has) comment.orEmpty() else "Add exercise comment",
                style = MaterialTheme.typography.bodyMedium,
                color = if (has) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (previous != null) {
                Text(
                    "LAST TIME · ${previous.first.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Brand.Gold,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
                Text(
                    previous.second,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SetCommentSheet(
    describe: String,
    initial: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
    /**
     * An exercise comment (owner, 2026-10-03): detailed notes on the exercise in this workout, kept to read later, so the
     * box is large and its earlier notes are listed under it. A set's comment stays a short note.
     */
    title: String = "Comment",
    detailed: Boolean = false,
    earlier: List<Pair<String, String>> = emptyList()
) {
    var text by remember { mutableStateOf(initial.orEmpty()) }
    FitSheet(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = "Save",
        onConfirm = {
            onSave(text.trim().ifBlank { null })
            onDismiss()
        }
    ) {
        Text(describe.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(if (detailed) "Notes on this exercise: form, feel, equipment, what to change next time…" else "Comment text…") },
            minLines = if (detailed) 6 else 2,
            maxLines = if (detailed) 16 else 5,
            modifier = Modifier.fillMaxWidth()
        )
        if (earlier.isNotEmpty()) {
            SectionLabel("Earlier notes", Modifier.padding(top = Spacing.md))
            earlier.forEach { (whenText, note) ->
                Text(whenText.uppercase(), style = MaterialTheme.typography.labelSmall, color = Brand.Gold, modifier = Modifier.padding(top = Spacing.sm))
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * One exercise on the day log, laid out as FitNotes's (owner, 2026-10-02): a raised card with the exercise's name and,
 * when every set is [done], a gold tick, over a solid rule in [categoryColor]. Otherwise, when [setsTotal] is given
 * (sets are being marked complete), "2/4" in gold shows how many of its sets are done so far; then its [sets] (usually unframed
 * [SetRow]s, values in right-aligned columns) and an optional [comment]. Tapping the card calls [onClick];
 * long-pressing it opens [menu], as FitNotes does.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExerciseCard(
    name: String,
    categoryColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    comment: String? = null,
    hasPr: Boolean = false,
    done: Boolean = false,
    setsDone: Int = 0,
    setsTotal: Int? = null,
    menu: List<MenuAction> = emptyList(),
    sets: @Composable ColumnScope.() -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            // FitNotes's margins (#141): 16dp either side, 16dp between cards.
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .raisedGlass(FitShapes.card)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = if (menu.isNotEmpty()) "Options for $name" else null,
                onLongClick = if (menu.isNotEmpty()) ({ menuOpen = true }) else null
            )
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.row).padding(start = Spacing.lg, end = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                name,
                // FitNotes sets the exercise's name large but not bold (#141).
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
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
            if (done) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Every set done", tint = Brand.Gold, modifier = Modifier.size(28.dp))
            } else if (setsTotal != null && setsTotal > 0) {
                Text(
                    "$setsDone/$setsTotal",
                    style = MaterialTheme.typography.titleMedium,
                    color = Brand.Gold,
                    modifier = Modifier
                        .padding(start = Spacing.xs)
                        .semantics { contentDescription = "$setsDone of $setsTotal sets done" }
                )
            }
            Box {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menu.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            enabled = item.enabled,
                            onClick = { menuOpen = false; item.onClick() }
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(categoryColor))
        Column(Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.md, end = Spacing.sm)) {
            sets()
            if (!comment.isNullOrBlank()) {
                Text(
                    "“$comment”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.lg, top = Spacing.xs, end = Spacing.md)
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
