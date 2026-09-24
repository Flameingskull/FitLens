package com.fitlens.companion.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand palette: black, imperial purple and gold. */
object Brand {
    val Black = Color(0xFF050308)
    val Onyx = Color(0xFF0E0A12)
    val Surface = Color(0xFF151019)
    val SurfaceHigh = Color(0xFF1E1724)
    val SurfaceHighest = Color(0xFF281F30)
    val ImperialPurple = Color(0xFF4B1E6E)
    val PurpleDeep = Color(0xFF2E1245)
    val PurpleLight = Color(0xFFB48BDB)
    val Gold = Color(0xFFD4AF37)
    val GoldLight = Color(0xFFF1D98A)
    val GoldDeep = Color(0xFF8C6D1F)
    val Ivory = Color(0xFFF7F3EA)
    val Muted = Color(0xFFBDB3C6)
    /** Decorative rules and chart grids only: too quiet to carry meaning on its own. */
    val Hairline = Color(0xFF3A2F44)
    /**
     * Borders that define a control (switches, outlined fields and buttons, chips, checkboxes).
     * A dusk-purple grey kept in the brand's cool family, at 4.22:1 on [Black] and 3.25:1 on [SurfaceHighest],
     * so every outlined control clears the 3:1 minimum for user-interface components.
     */
    val Outline = Color(0xFF7A6C86)
}

/** Chart colours: gold series, purple photo markers, ivory goal line. */
data class ChartColors(val series: Color, val accent: Color, val goal: Color)

val LocalChartColors = staticCompositionLocalOf { ChartColors(Brand.Gold, Brand.PurpleLight, Brand.Ivory) }

private val Scheme = darkColorScheme(
    primary = Brand.Gold,
    onPrimary = Brand.Black,
    primaryContainer = Brand.PurpleDeep,
    onPrimaryContainer = Brand.GoldLight,
    secondary = Brand.PurpleLight,
    onSecondary = Brand.Black,
    secondaryContainer = Brand.ImperialPurple,
    onSecondaryContainer = Brand.GoldLight,
    tertiary = Brand.GoldLight,
    onTertiary = Brand.Black,
    tertiaryContainer = Brand.GoldDeep,
    onTertiaryContainer = Brand.Ivory,
    background = Brand.Black,
    onBackground = Brand.Ivory,
    surface = Brand.Black,
    onSurface = Brand.Ivory,
    surfaceVariant = Brand.SurfaceHigh,
    onSurfaceVariant = Brand.Muted,
    surfaceTint = Brand.Gold,
    surfaceContainerLowest = Brand.Black,
    surfaceContainerLow = Brand.Onyx,
    surfaceContainer = Brand.Onyx,
    surfaceContainerHigh = Brand.Surface,
    surfaceContainerHighest = Brand.SurfaceHighest,
    inverseSurface = Brand.Ivory,
    inverseOnSurface = Brand.Black,
    inversePrimary = Brand.ImperialPurple,
    outline = Brand.Outline,
    outlineVariant = Color(0xFF2A2231),
    error = Color(0xFFE8798A),
    onError = Brand.Black,
    scrim = Color.Black
)

private val Serif = FontFamily.Serif
private val Sans = FontFamily.SansSerif

private val LuxuryType = Typography(
    displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 54.sp, lineHeight = 60.sp),
    displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 42.sp, lineHeight = 50.sp),
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.3.sp),
    titleMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 1.6.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.8.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.0.sp)
)

private val LuxuryShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

/**
 * Spacing scale for the shared components (#80). New layout code picks from here rather than inventing one-off
 * dp values, so screens line up with each other.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** The minimum touch target for anything tappable. */
    val touch = 48.dp
    /** Stepper buttons and other primary logging controls: larger than the minimum, for use mid-set. */
    val stepper = 56.dp
    /** The minimum height of a list row. */
    val row = 56.dp
}

/** Shapes the shared components use beyond Material's scale (#80). */
object FitShapes {
    /** Set rows, selectable list rows, snackbars. */
    val row = RoundedCornerShape(8.dp)
    /** Exercise cards and stat tiles. */
    val card = RoundedCornerShape(12.dp)
    /** The top of a modal bottom sheet. */
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}

/** Motion durations in milliseconds, plus the stepper's press-and-hold repeat timing (#80). */
object Motion {
    const val FAST = 120
    const val STANDARD = 220
    const val EMPHASIS = 360
    /** How long a stepper button must be held before it starts repeating. */
    const val REPEAT_DELAY_MS = 400L
    /** The gap between repeats while a stepper button stays held. */
    const val REPEAT_INTERVAL_MS = 70L
}

/** The category colour bar when a category has no colour of its own. Matches [categoryColour]'s fallback. */
val CategoryFallbackColour: Color get() = Brand.Outline

/** FitLens always uses its black, imperial purple and gold theme, whatever the system setting. */
@Composable
fun FitLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = LuxuryType, shapes = LuxuryShapes) {
        CompositionLocalProvider(LocalChartColors provides ChartColors(Brand.Gold, Brand.PurpleLight, Brand.Ivory)) {
            content()
        }
    }
}
