package com.fitlens.companion.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons FitLens needs that aren't in `material-icons-core`, drawn from the Material paths so they match the rest
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
        "FitLens.Comment",
        "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z"
    )

    /** A set without a comment yet (#108). */
    val CommentOutline: ImageVector = icon(
        "FitLens.CommentOutline",
        "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM20,16H6l-2,2V4h16v12z"
    )

    /** The rest timer (#109): an alarm clock, where the bell read as notifications. */
    val Alarm: ImageVector = icon(
        "FitLens.Alarm",
        "M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85z" +
            "M12.5,8H11v6l4.75,2.85 0.75,-1.23 -4,-2.37V8z" +
            "M12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9z" +
            "M12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z"
    )
}
