package com.fitlens.companion.ui.design

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.FitShapes

/**
 * Shows [message] with an Undo action for a reversible change, and runs [onUndo] if it is tapped (#80).
 * Suspends until the snackbar goes. Results worth re-reading belong in the kept result history (#62), not here.
 */
suspend fun SnackbarHostState.showUndo(message: String, onUndo: () -> Unit, undoLabel: String = "Undo") {
    val result = showSnackbar(
        message = message,
        actionLabel = undoLabel,
        withDismissAction = true,
        duration = SnackbarDuration.Long
    )
    if (result == SnackbarResult.ActionPerformed) onUndo()
}

/** A snackbar host styled for FitLens: raised surface, ivory text, gold action (#80). */
@Composable
fun UndoSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = FitShapes.row,
            containerColor = Brand.SurfaceHighest,
            contentColor = Brand.Ivory,
            actionColor = Brand.Gold,
            dismissActionContentColor = Brand.Muted
        )
    }
}
