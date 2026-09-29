package com.fitlens.companion.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitlens.companion.ui.Brand
import kotlin.math.max

/**
 * FitLens's glass depth (#102): surfaces lit like tinted glass in the brand colours instead of flat blocks. It is
 * drawn with gradients, rims and shadows only (no backdrop blur), so it looks the same on every supported phone.
 *
 * - [ambientBackdrop]: soft purple and gold glows on black behind every screen, for the glass to catch.
 * - [raisedGlass]: cards, tiles and bars that sit above the page.
 * - [recessedGlass]: set rows and wells pressed into a card.
 * - [GoldButton] and [GlassOutlinedButton]: the primary and secondary buttons.
 *
 * Brushes are built once here, not on every frame.
 */
object Glass {
    val raisedFill = Brush.verticalGradient(
        0f to Brand.PurpleLight.copy(alpha = 0.22f),
        0.4f to Brand.ImperialPurple.copy(alpha = 0.20f),
        1f to Brand.Surface.copy(alpha = 0.60f)
    )
    /** A bright gold top edge fading into a faint purple rim down the sides. */
    val rim = Brush.verticalGradient(
        0f to Brand.GoldLight.copy(alpha = 0.55f),
        0.35f to Brand.PurpleLight.copy(alpha = 0.26f),
        1f to Brand.PurpleLight.copy(alpha = 0.16f)
    )
    val sheen = Brand.Ivory.copy(alpha = 0.22f)
    val recessedFill = Brand.Black.copy(alpha = 0.35f)
    val recessedShade = Brush.verticalGradient(listOf(Brand.Black.copy(alpha = 0.45f), Brand.Black.copy(alpha = 0f)))
    val recessedRim = Brand.PurpleLight.copy(alpha = 0.10f)
    val recessedLip = Brand.Ivory.copy(alpha = 0.08f)
    /** Polished gold: light at the top, deep at the bottom. */
    val gold = Brush.verticalGradient(
        0f to Brand.GoldLight,
        0.48f to Brand.Gold,
        1f to Brand.GoldDeep
    )
    val goldHighlight = Brand.Ivory.copy(alpha = 0.55f)
    /** The primary button's fill (#104): imperial purple, deepening towards the bottom, under gold text. */
    val royal = Brush.verticalGradient(0f to Brand.ImperialPurple, 1f to Brand.PurpleDeep)
    /** The primary button's polished gold rim: light at the top, deep at the bottom. */
    val royalRim = Brush.verticalGradient(0f to Brand.GoldLight, 1f to Brand.GoldDeep)
    val purple = Brush.verticalGradient(listOf(Brand.PurpleLight.copy(alpha = 0.20f), Brand.ImperialPurple.copy(alpha = 0.18f)))
    val purpleRim = Brand.Gold.copy(alpha = 0.45f)
    /** The top bar: deep purple fading to the page. */
    val bar = Brush.verticalGradient(listOf(Brand.PurpleDeep.copy(alpha = 0.55f), Brand.Black.copy(alpha = 0.25f)))
    val barRule = Brand.Gold.copy(alpha = 0.35f)
}

/** A one-pixel highlight just inside the top edge, stopping short of the rounded corners. */
private fun Modifier.topSheen(color: Color, inset: Dp): Modifier = drawWithContent {
    drawContent()
    val i = inset.toPx()
    val y = 1.dp.toPx()
    if (size.width > 2 * i) drawLine(color, Offset(i, y), Offset(size.width - i, y), strokeWidth = 1.dp.toPx())
}

/**
 * A raised glass panel (#102): a purple-tinted translucent fill, lighter at the top, a gold top rim, an inner sheen
 * and a soft shadow underneath. [inset] keeps the sheen inside the shape's rounded corners.
 */
fun Modifier.raisedGlass(shape: Shape, elevation: Dp = 6.dp, inset: Dp = 12.dp): Modifier = this
    .shadow(elevation, shape, clip = false, ambientColor = Brand.Black, spotColor = Brand.Black)
    .clip(shape)
    .background(Glass.raisedFill)
    .border(1.dp, Glass.rim, shape)
    .topSheen(Glass.sheen, inset)

/** A recessed glass well (#102): darker than the card around it, shaded at the top and lit along the bottom lip. */
fun Modifier.recessedGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Glass.recessedFill)
    .drawWithContent {
        drawRect(Glass.recessedShade, size = size.copy(height = 4.dp.toPx()))
        val y = size.height - 1.dp.toPx() / 2
        drawLine(Glass.recessedLip, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        drawContent()
    }
    .border(1.dp, Glass.recessedRim, shape)

/** The page behind everything (#102): black, with imperial purple glows top-left and right and a faint gold glow low down. */
fun Modifier.ambientBackdrop(): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val big = max(w, h)
    val topLeft = Brush.radialGradient(
        listOf(Brand.ImperialPurple.copy(alpha = 0.55f), Brand.ImperialPurple.copy(alpha = 0f)),
        center = Offset(0f, 0f),
        radius = big * 0.6f
    )
    val right = Brush.radialGradient(
        listOf(Brand.PurpleDeep.copy(alpha = 0.6f), Brand.PurpleDeep.copy(alpha = 0f)),
        center = Offset(w, h * 0.65f),
        radius = big * 0.45f
    )
    val low = Brush.radialGradient(
        listOf(Brand.Gold.copy(alpha = 0.08f), Brand.Gold.copy(alpha = 0f)),
        center = Offset(w * 0.8f, h * 1.02f),
        radius = big * 0.3f
    )
    onDrawBehind {
        drawRect(Brand.Black)
        drawRect(topLeft)
        drawRect(right)
        drawRect(low)
    }
}

/**
 * The primary button (#102, #104): imperial purple glass with a polished gold rim, gold text, a glossy top edge and a
 * soft gold glow. Gold text on a dark fill, as the owner asked: no button text is ever black.
 */
@Composable
fun GoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val shape = ButtonDefaults.shape
    val glass = if (enabled) {
        Modifier
            .shadow(6.dp, shape, clip = false, ambientColor = Brand.Gold, spotColor = Brand.Gold)
            .clip(shape)
            .background(Glass.royal)
            .border(1.dp, Glass.royalRim, shape)
            .topSheen(Glass.sheen, 16.dp)
    } else {
        Modifier
    }
    Button(
        onClick = onClick,
        modifier = modifier.then(glass),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Brand.Gold.copy(alpha = 0f), contentColor = Brand.GoldLight),
        contentPadding = contentPadding,
        content = content
    )
}

/** A secondary button in clear purple glass with a gold rim (#102). */
@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val shape = ButtonDefaults.outlinedShape
    OutlinedButton(
        onClick = onClick,
        modifier = if (enabled) modifier.clip(shape).background(Glass.purple).topSheen(Glass.sheen, 16.dp) else modifier,
        enabled = enabled,
        shape = shape,
        border = BorderStroke(1.dp, if (enabled) Glass.purpleRim else Brand.Outline.copy(alpha = 0.38f)),
        contentPadding = contentPadding,
        content = content
    )
}
