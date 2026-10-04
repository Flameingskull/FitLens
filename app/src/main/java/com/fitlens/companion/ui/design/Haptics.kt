package com.fitlens.companion.ui.design

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The app's haptic patterns (#93), so the same moment feels the same everywhere. They go through the view, so they
 * follow the phone's own touch-feedback setting and need no permission; callers may be off the main thread.
 *
 * - [tick]: a light tick, for a stepper press or a small change.
 * - [confirm]: one firm pulse, for saving a set or ticking it done.
 * - [record]: a distinct rising triple pulse, for a new personal record.
 */
object Haptics {
    private val confirmConstant =
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY

    fun tick(view: View) {
        view.post { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }

    fun confirm(view: View) {
        view.post { view.performHapticFeedback(confirmConstant) }
    }

    fun record(view: View) {
        view.post { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        view.postDelayed({ view.performHapticFeedback(confirmConstant) }, RECORD_GAP_MS)
        view.postDelayed({ view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }, RECORD_GAP_MS * 2)
    }

    private const val RECORD_GAP_MS = 110L
}
