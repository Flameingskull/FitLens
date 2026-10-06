package com.fitlens.companion.ui.design

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.FitShapes

/**
 * A gold outline while the control after it has keyboard or switch-access focus (#41, section 7). Put it before the
 * `clickable`, `toggleable` or `selectable` it outlines. Those only take focus outside touch mode, so the outline
 * never shows for a finger.
 */
@Composable
fun Modifier.focusRing(shape: Shape = FitShapes.row): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(2.dp, Brand.Gold, shape) else Modifier)
}

/**
 * Gives focus back to the control after it when the sheet or dialog it opened closes (#41, section 7), so keyboard and
 * switch-access users carry on from the row they were on instead of the top of the page. [open] is whether that sheet
 * is showing. Put it before the `clickable` it belongs to. With a finger nothing changes: touch controls don't take
 * focus, so the request is simply refused.
 */
@Composable
fun Modifier.returnFocus(open: Boolean): Modifier {
    val requester = remember { FocusRequester() }
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            wasOpen = true
        } else if (wasOpen) {
            wasOpen = false
            runCatching { requester.requestFocus() }
        }
    }
    return this.focusRequester(requester)
}
