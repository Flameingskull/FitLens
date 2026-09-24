package com.fitlens.companion.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Width buckets for switching a screen between one and two columns (#80).
 *
 * [Compact] is under 400dp (small phones, or any phone at a large display size), [Medium] is a typical phone up to
 * 600dp, and [Expanded] is 600dp and wider (unfolded foldables, landscape, tablets).
 * Computed from the configuration rather than the window-size-class library, so no extra dependency is needed.
 */
enum class WidthBucket {
    Compact, Medium, Expanded;

    /** How many content columns a list-style screen should use in this bucket. */
    val columns: Int get() = if (this == Expanded) 2 else 1
}

/** The bucket for a width in dp. Pure, so it can be used outside composition. */
fun widthBucketFor(widthDp: Int): WidthBucket = when {
    widthDp < 400 -> WidthBucket.Compact
    widthDp < 600 -> WidthBucket.Medium
    else -> WidthBucket.Expanded
}

/** The current window's width bucket. Recomposes when the window is resized, folded or rotated. */
@Composable
fun currentWidthBucket(): WidthBucket = widthBucketFor(LocalConfiguration.current.screenWidthDp)
