package com.fitlens.companion.ui

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.fitlens.companion.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand palette (1.0.71): black and vibrant gold, with red and green kept for rises, falls and graph lines. */
object Brand {
    val Black = Color(0xFF050505)
    val Onyx = Color(0xFF0D0D0D)
    val Surface = Color(0xFF141414)
    val SurfaceHigh = Color(0xFF1C1C1C)
    val SurfaceHighest = Color(0xFF262626)
    /** Polished black for the primary button and the top bar: a step lighter than the page. */
    val Graphite = Color(0xFF1A1A1A)
    /** Chosen items (selected day, active row, segmented choice): a deep gold-brown that carries gold text. */
    val GoldDusk = Color(0xFF3A2E0A)
    val Gold = Color(0xFFF2BE22)
    val GoldLight = Color(0xFFFFDB6E)
    val GoldDeep = Color(0xFF9A7608)
    val Ivory = Color(0xFFF7F3EA)
    val Muted = Color(0xFFBDB6A8)
    /** Decorative rules and chart grids only: too quiet to carry meaning on its own. */
    val Hairline = Color(0xFF38342B)
    /** Dims the screen behind a blocking progress card. */
    val Scrim = Color(0x99050505)
    /**
     * Borders that define a control (switches, outlined fields and buttons, chips, checkboxes).
     * A warm grey kept in the gold family, about 4.3:1 on [Black] and 3.3:1 on [SurfaceHighest], so every outlined
     * control clears the 3:1 minimum for user-interface components.
     */
    val Outline = Color(0xFF7D7462)
    /** Destructive buttons (#104): a deep oxblood that carries gold text, where the light error red would need black. */
    val Wine = Color(0xFF4A1416)
    /** A value that went up, and progress towards a goal. */
    val Rise = Color(0xFF4CC38A)
    /** A value that went down, progress away from a goal, and the line of every graph. */
    val Fall = Color(0xFFE5484D)
}

/** The colour of a change: green for a rise, red for a fall, plain for no change. */
fun deltaColour(delta: Double, plain: Color = Brand.Muted): Color = when {
    delta > 0 -> Brand.Rise
    delta < 0 -> Brand.Fall
    else -> plain
}

/**
 * Chart colours (#50, 1.0.71): [series] is the line, red, over a translucent gold [fill]; [accent] marks photo days
 * and [goal] is the goal line. [palette] is the series order for charts with several lines: red first, then gold,
 * ivory and gold light. Charts with more series reuse it lighter (see `seriesColor` in Charts.kt) and tell series
 * apart by marker shape too.
 */
data class ChartColors(
    val series: Color,
    val accent: Color,
    val goal: Color,
    val palette: List<Color> = listOf(Brand.Fall, Brand.Gold, Brand.Ivory, Brand.GoldLight),
    val fill: Color = Brand.Gold.copy(alpha = 0.32f)
)

val LocalChartColors = staticCompositionLocalOf { ChartColors(Brand.Fall, Brand.Gold, Brand.Ivory) }

/**
 * No text is ever black (#104): every filled control carries gold text on a dark fill. [onPrimary] stays black only
 * for the icons Material draws on gold (checkmarks, switch thumbs); no button or chip is filled with the gold
 * primary, and pickers use [fitDatePickerColors] and [fitTimePickerColors].
 */
private val Scheme = darkColorScheme(
    primary = Brand.Gold,
    onPrimary = Brand.Black,
    primaryContainer = Brand.GoldDusk,
    onPrimaryContainer = Brand.GoldLight,
    secondary = Brand.GoldLight,
    onSecondary = Brand.Black,
    secondaryContainer = Brand.GoldDusk,
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
    // Snackbars and tooltips: gold on raised onyx rather than black on ivory (#104).
    inverseSurface = Brand.SurfaceHighest,
    inverseOnSurface = Brand.GoldLight,
    inversePrimary = Brand.Gold,
    outline = Brand.Outline,
    outlineVariant = Color(0xFF2A2720),
    error = Color(0xFFFF7A7E),
    onError = Brand.Black,
    errorContainer = Brand.Wine,
    onErrorContainer = Brand.GoldLight,
    scrim = Color.Black
)

/** Date pickers: the chosen day and year in gold on gold dusk, never black on gold (#104). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun fitDatePickerColors(): DatePickerColors = DatePickerDefaults.colors(
    selectedDayContainerColor = Brand.GoldDusk,
    selectedDayContentColor = Brand.GoldLight,
    selectedYearContainerColor = Brand.GoldDusk,
    selectedYearContentColor = Brand.GoldLight,
    todayContentColor = Brand.Gold,
    todayDateBorderColor = Brand.Gold
)

/** Time pickers: the dial's chosen number in gold on gold dusk (#104). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun fitTimePickerColors(): TimePickerColors = TimePickerDefaults.colors(
    selectorColor = Brand.GoldDusk,
    clockDialSelectedContentColor = Brand.GoldLight
)

/**
 * Manrope (owner decision, 2026-10-02, #135), bundled under the SIL Open Font License (`res/raw/manrope_ofl.txt`).
 * One family for everything: headings are no longer serif. Weights are static instances of the variable font.
 */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold)
)

/**
 * Manrope for the screens drawn on an Android `Canvas` (share images, the PDF report, video frames), so they match
 * the app (#135). [init] runs from `App`; before it (or in JVM tests) the system sans-serif stands in.
 */
object BrandFonts {
    @Volatile private var regular: Typeface? = null
    @Volatile private var bold: Typeface? = null

    fun init(context: Context) {
        regular = runCatching { ResourcesCompat.getFont(context, R.font.manrope_regular) }.getOrNull()
        bold = runCatching { ResourcesCompat.getFont(context, R.font.manrope_bold) }.getOrNull()
    }

    fun typeface(bold: Boolean = false, italic: Boolean = false): Typeface {
        val base = (if (bold) this.bold else regular) ?: Typeface.SANS_SERIF
        // Manrope has no italic: Android slants it, and only fakes bold when the bold file is missing.
        val style = when {
            italic && bold && this.bold == null -> Typeface.BOLD_ITALIC
            italic -> Typeface.ITALIC
            bold && this.bold == null -> Typeface.BOLD
            else -> Typeface.NORMAL
        }
        return if (style == Typeface.NORMAL) base else Typeface.create(base, style)
    }
}

// One calm scale in Manrope (#135): display 40/34/28, headline 24/22/20, title 20/16/14, body 16/14/12, labels
// 14/12/11. Letter-spacing only on the small uppercase labels (`titleSmall`, `labelSmall`), so text is never squeezed.
// Every style wraps between words with balanced lines (#128): headings as headings, running text as paragraphs, and
// no hyphenation, so a word is never split across lines.
private val LuxuryType = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 48.sp, lineBreak = LineBreak.Heading),
    displayMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 42.sp, lineBreak = LineBreak.Heading),
    displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, lineBreak = LineBreak.Heading),
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, lineBreak = LineBreak.Heading),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, lineBreak = LineBreak.Heading),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, lineBreak = LineBreak.Heading),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, lineBreak = LineBreak.Heading),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, lineBreak = LineBreak.Heading),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.6.sp, lineBreak = LineBreak.Heading),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, lineBreak = LineBreak.Paragraph),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, lineBreak = LineBreak.Paragraph),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, lineBreak = LineBreak.Paragraph),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, lineBreak = LineBreak.Paragraph),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, lineBreak = LineBreak.Paragraph),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp, lineBreak = LineBreak.Paragraph)
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

/**
 * False when the system's "Remove animations" accessibility setting (animator duration scale 0) is on, so screen
 * transitions can be skipped rather than merely sped up (#93). Read when the screen is first composed.
 */
@Composable
fun animationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** The category colour bar when a category has no colour of its own. Matches [categoryColour]'s fallback. */
val CategoryFallbackColour: Color get() = Brand.Outline

/**
 * FlexNotes always uses its black and gold theme, whatever the system setting.
 *
 * Every background is dark, so text is never black. Compose's own default content colour is black, and it is what
 * any text without a colour falls back to wherever its container isn't one of the theme's colours: the transparent
 * screen scaffold, glass surfaces, translucent fills. Ivory is provided here as that default, for every screen,
 * sheet and dialog.
 */
@Composable
fun FlexNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = LuxuryType, shapes = LuxuryShapes) {
        CompositionLocalProvider(
            LocalContentColor provides Brand.Ivory,
            LocalChartColors provides ChartColors(Brand.Fall, Brand.Gold, Brand.Ivory)
        ) {
            content()
        }
    }
}
