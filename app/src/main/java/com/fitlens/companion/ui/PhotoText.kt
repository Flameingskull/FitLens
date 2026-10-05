package com.fitlens.companion.ui

import android.content.res.Resources
import com.fitlens.companion.R
import com.fitlens.companion.data.DateSources
import com.fitlens.companion.data.MediaPrefs
import com.fitlens.companion.data.PhotoImportResult

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

/** "3 photos added, 1 already imported. 2 dated from photo metadata, 1 needs its date checked." (#156) */
internal fun importResultText(res: Resources, r: PhotoImportResult): String {
    fun count(id: Int, n: Int) = res.getQuantityString(id, n, n)
    val parts = buildList {
        add(count(R.plurals.pir_added, r.added))
        if (r.duplicates > 0) add(count(R.plurals.pir_duplicates, r.duplicates))
        if (r.failed > 0) add(count(R.plurals.pir_failed, r.failed))
    }
    val detail = buildList {
        r.bySource[DateSources.EXIF]?.takeIf { it > 0 }?.let { add(count(R.plurals.pir_exif, it)) }
        r.bySource[DateSources.MEDIA]?.takeIf { it > 0 }?.let { add(count(R.plurals.pir_media, it)) }
        r.bySource[DateSources.FILENAME]?.takeIf { it > 0 }?.let { add(count(R.plurals.pir_file_name, it)) }
        if (r.needsReview > 0) add(count(R.plurals.pir_review, r.needsReview))
    }
    return if (detail.isEmpty()) res.getString(R.string.pir_whole, parts.joinToString(", "))
    else res.getString(R.string.pir_whole_detail, parts.joinToString(", "), detail.joinToString(", "))
}

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
