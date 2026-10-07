package com.fitlens.companion.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons FlexNotes needs that aren't in `material-icons-core`, drawn from the Material paths so they match the rest
 * (the extended icon set is too large to add for a few glyphs). Tint them like any other icon: the black fill is
 * replaced by `Icon`'s tint.
 */
object FitIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
            .build()

    /** A set that has a comment (#108). */
    val Comment: ImageVector = icon(
        "FlexNotes.Comment",
        "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z"
    )

    /** A graph not pinned to the Analysis overview yet (#55); pinned ones use the filled star. */
    val StarOutline: ImageVector = icon(
        "FlexNotes.StarOutline",
        "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24z" +
            "M12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z"
    )

    /** A set without a comment yet (#108). */
    val CommentOutline: ImageVector = icon(
        "FlexNotes.CommentOutline",
        "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM20,16H6l-2,2V4h16v12z"
    )

    /** Records and goals, as FitNotes's trophy (#122). */
    val Trophy: ImageVector = icon(
        "FlexNotes.Trophy",
        "M19,5h-2V3H7v2H5C3.9,5 3,5.9 3,7v1c0,2.55 1.92,4.63 4.39,4.94c0.63,1.5 1.98,2.63 3.61,2.96V19H7v2h10v-2h-4" +
            "v-3.1c1.63,-0.33 2.98,-1.46 3.61,-2.96C19.08,12.63 21,10.55 21,8V7C21,5.9 20.1,5 19,5z" +
            "M5,8V7h2v3.82C5.84,10.4 5,9.3 5,8zM19,8c0,1.3 -0.84,2.4 -2,2.82V7h2V8z"
    )

    /** Add to superset (#124): a chain link. */
    val Link: ImageVector = icon(
        "FlexNotes.Link",
        "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1z" +
            "M8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z"
    )

    /** Copy previous workout, as FitNotes draws it on an empty day. */
    val Copy: ImageVector = icon(
        "FlexNotes.Copy",
        "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1z" +
            "M19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z"
    )

    /** Save and add another (#126), FitNotes's ✓+ beside ✓ on the exercise editor. */
    val CheckPlus: ImageVector = icon(
        "FlexNotes.CheckPlus",
        "M7,15.17L3.83,12l-1.42,1.41L7,18l9,-9l-1.41,-1.41z" +
            "M19,3h-2v3h-3v2h3v3h2V8h3V6h-3z"
    )

    /** The 1RM calculator on the Records tab (#142), as FitNotes's calculator. */
    val Calculate: ImageVector = icon(
        "FlexNotes.Calculate",
        "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3z" +
            "M13.03,7.06L14.09,6l1.41,1.41L16.91,6l1.06,1.06l-1.41,1.41l1.41,1.41l-1.06,1.06L15.5,9.54l-1.41,1.41" +
            "l-1.06,-1.06l1.41,-1.41L13.03,7.06zM6.25,7.72h5v1.5h-5V7.72zM11.5,16h-2v2H8v-2H6v-1.5h2v-2h1.5v2h2V16z" +
            "M18,17.25h-5v-1.5h5V17.25zM18,14.75h-5v-1.5h5V14.75z"
    )

    /** The rest timer (#109): an alarm clock, where the bell read as notifications. */
    val Alarm: ImageVector = icon(
        "FlexNotes.Alarm",
        "M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85z" +
            "M12.5,8H11v6l4.75,2.85 0.75,-1.23 -4,-2.37V8z" +
            "M12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9z" +
            "M12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z"
    )

    /** Options (#92): the slideshow and video settings sheet. */
    val Tune: ImageVector = icon(
        "FlexNotes.Tune",
        "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z"
    )

    /** Pause the slideshow preview (#92). */
    val Pause: ImageVector = icon("FlexNotes.Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")

    /** The next photo (#92). */
    val SkipNext: ImageVector = icon("FlexNotes.SkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")

    /** The previous photo (#92). */
    val SkipPrevious: ImageVector = icon("FlexNotes.SkipPrevious", "M6,6h2v12H6zM9.5,12l8.5,6V6z")

    /** Swap the two sides of a comparison (#92). */
    val SwapHoriz: ImageVector = icon(
        "FlexNotes.SwapHoriz",
        "M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3zM21,9l-3.99,-4v3H10v2h7.01v3L21,9z"
    )

    /** Compare two photos side by side (#92). */
    val Compare: ImageVector = icon(
        "FlexNotes.Compare",
        "M10,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h5v2h2V1h-2v2zM10,18H5l5,-6v6z" +
            "M19,3h-5v2h5v13l-5,-6v9h5c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z"
    )

    /** A photo's pose (#92): a standing figure. */
    val Pose: ImageVector = icon(
        "FlexNotes.Pose",
        "M12,2c1.1,0 2,0.9 2,2s-0.9,2 -2,2 -2,-0.9 -2,-2 0.9,-2 2,-2zM21,9h-6v13h-2v-6h-2v6H9V9H3V7h18v2z"
    )
}
