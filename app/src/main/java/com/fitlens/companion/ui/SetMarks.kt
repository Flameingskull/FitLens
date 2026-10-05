package com.fitlens.companion.ui

import android.content.res.Resources
import com.fitlens.companion.data.Effort
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.SetTypes
import com.fitlens.companion.data.Settings

/**
 * How a set's type (#43) and effort (#44) show in set lists, following the settings: the W, D or F badge when
 * "Show set type" is on, and "RPE 8" or "2 RIR" when effort tracking is on and the set has a value. Each has a spoken
 * form for TalkBack. Turning a setting off only hides the mark; nothing stored changes.
 */
data class SetMarks(val badge: String?, val badgeSpoken: String?, val effort: String?, val effortSpoken: String?)

fun setMarks(res: Resources, s: SetRow, prefs: PortableSettings = Settings.currentPortable()): SetMarks {
    val badge = if (prefs.showSetType) SetTypes.badge(s.setType) else null
    val rpe = s.rpe?.takeIf { prefs.effortMode != Effort.OFF }
    return SetMarks(
        badge = badge,
        badgeSpoken = badge?.let { setTypeText(res, s.setType).lowercase() },
        effort = rpe?.let { effortText(res, it, prefs.effortMode) },
        effortSpoken = rpe?.let { effortSpoken(res, it, prefs.effortMode) }
    )
}
