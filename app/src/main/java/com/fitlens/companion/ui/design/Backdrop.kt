package com.fitlens.companion.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.fitlens.companion.R

/**
 * The full-body FlexNotes character (the owner's artwork, `branding/flexnotes-icon-source.png`, placed unedited) drawn
 * faintly behind every screen, over the ambient glow and under all content. It's kept dim and fades out towards the
 * top and edges, so text and controls stay as legible as before. Decorative: TalkBack skips it.
 */
@Composable
fun BoxScope.CharacterBackdrop() {
    Image(
        painterResource(R.drawable.backdrop_character),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomCenter,
        alpha = 0.16f,
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                // Fade the square's edges away, top heaviest, so no hard outline of the artwork shows.
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black, 1f to Color.Black),
                    blendMode = BlendMode.DstIn
                )
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn
                )
            }
    )
}
