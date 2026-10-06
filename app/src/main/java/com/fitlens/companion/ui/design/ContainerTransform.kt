package com.fitlens.companion.ui.design

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/*
 * The container transform (#93): a card that opens a screen grows into that screen, and shrinks back into the card
 * when the screen closes. The navigation host provides both scopes; with the system's Remove animations on it
 * provides neither, and screens change at once as before.
 */

/** The shared-element scope around the navigation host, or null when animations are off. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The enter and exit animation of the screen being composed, or null when animations are off. */
val LocalScreenAnimationScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks this element as one end of a container transform. A card and the screen it opens use the same [key] (see
 * [exerciseTransformKey]); while navigating between them the card's bounds grow into the screen's. With no [key], or
 * with animations off, it changes nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransform(key: Any?): Modifier {
    val shared = LocalSharedTransitionScope.current
    val screen = LocalScreenAnimationScope.current
    if (key == null || shared == null || screen == null) return this
    return with(shared) {
        this@containerTransform.sharedBounds(rememberSharedContentState(key), animatedVisibilityScope = screen)
    }
}

/** The key shared by an exercise's card on the day log of [date] and its exercise screen for that day. */
fun exerciseTransformKey(date: String, exerciseId: Long): String = "exercise:${date.take(10)}:$exerciseId"
