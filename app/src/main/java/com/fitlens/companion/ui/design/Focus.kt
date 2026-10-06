package com.fitlens.companion.ui.design

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
