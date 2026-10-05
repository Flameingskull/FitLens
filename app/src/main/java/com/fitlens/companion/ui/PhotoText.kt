package com.fitlens.companion.ui

import android.content.res.Resources
import com.fitlens.companion.R
import com.fitlens.companion.data.DateSources
import com.fitlens.companion.data.MediaPrefs

/*
 * What stored photo values read as on screen (#94). Poses, photo groupings and date sources are stored as their
 * English keys; their words come from strings.xml here.
 */

/** "Front", "Side", "Back", "Other"; any other stored pose is shown as it is. */
internal fun poseText(res: Resources, pose: String): String = when (pose) {
    "Front" -> res.getString(R.string.pose_front)
    "Side" -> res.getString(R.string.pose_side)
    "Back" -> res.getString(R.string.pose_back)
    "Other" -> res.getString(R.string.pose_other)
    "" -> res.getString(R.string.pose_not_set)
    else -> pose
}

/** "Day", "Week", "Month", "Year", "Pose" for a [MediaPrefs] grouping key. */
internal fun groupText(res: Resources, key: String): String = res.getString(
    when (key) {
        MediaPrefs.GROUP_DAY -> R.string.grp_day
        MediaPrefs.GROUP_WEEK -> R.string.grp_week
        MediaPrefs.GROUP_YEAR -> R.string.grp_year
        MediaPrefs.GROUP_POSE -> R.string.grp_pose
        else -> R.string.grp_month
    }
)

/** Where a photo's date came from, for the viewer and the date review. */
internal fun dateSourceText(res: Resources, source: String): String = res.getString(
    when (source) {
        DateSources.EXIF -> R.string.ds_exif
        DateSources.MEDIA -> R.string.ds_media
        DateSources.FILENAME -> R.string.ds_filename
        DateSources.FILE -> R.string.ds_file
        DateSources.MANUAL -> R.string.ds_manual
        else -> R.string.ds_none
    }
)
