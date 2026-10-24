package hr.ml.expensetracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** A container colour and the colour of text and icons on it. */
@Immutable
data class ColorPair(val container: Color, val content: Color)

/**
 * The categorical hues for category icons and bars. Which category gets which hue (and icon) is
 * decided in `ui/common/CategoryVisuals`; the values per light/dark theme live here.
 */
enum class CategoryHue { GREEN, ORANGE, BLUE, SKY, AMBER, BROWN, OLIVE, RUST, ROSE, PLUM, RED, TEAL, INDIGO, MAUVE, SLATE, NAVY, GREY }

/** Colours Material 3 has no role for. Read them through [AppTheme.colors]. */
@Immutable
data class AppColors(
    /** The signed-in person is owed money (balance card). */
    val owedToMe: ColorPair,
    /** The signed-in person owes money. */
    val iOwe: ColorPair,
    /** Nobody owes anything. */
    val settled: ColorPair,
    /** Raised surface of list groups and cards: a touch lighter than the background in both themes. */
    val card: Color,
    private val hues: Map<CategoryHue, Color>,
    /** How strongly a hue tints the circle behind a category icon and a selected chip. */
    val tintAlpha: Float,
) {
    fun hue(hue: CategoryHue): Color = hues.getValue(hue)
}

val LightAppColors = AppColors(
    owedToMe = ColorPair(Color(0xFF136B5E), Color(0xFFFFFFFF)),
    iOwe = ColorPair(Color(0xFFA14A5A), Color(0xFFFFFFFF)),
    settled = ColorPair(Color(0xFFEAE3D8), Color(0xFF1F1B16)),
    card = Color(0xFFFFFFFF),
    hues = mapOf(
        CategoryHue.GREEN to Color(0xFF3B8A55),
        CategoryHue.ORANGE to Color(0xFFC56A24),
        CategoryHue.BLUE to Color(0xFF3B6CB3),
        CategoryHue.SKY to Color(0xFF1F819A),
        CategoryHue.AMBER to Color(0xFFB07B00),
        CategoryHue.BROWN to Color(0xFF85603F),
        CategoryHue.OLIVE to Color(0xFF6A7524),
        CategoryHue.RUST to Color(0xFFAD5236),
        CategoryHue.ROSE to Color(0xFFBF5277),
        CategoryHue.PLUM to Color(0xFF8A4D9C),
        CategoryHue.RED to Color(0xFFBC4045),
        CategoryHue.TEAL to Color(0xFF2A877A),
        CategoryHue.INDIGO to Color(0xFF5559B4),
        CategoryHue.MAUVE to Color(0xFF93617C),
        CategoryHue.SLATE to Color(0xFF5A6F7E),
        CategoryHue.NAVY to Color(0xFF39598A),
        CategoryHue.GREY to Color(0xFF746C63),
    ),
    tintAlpha = 0.14f,
)

val DarkAppColors = AppColors(
    owedToMe = ColorPair(Color(0xFF1C5A51), Color(0xFFD6F5EE)),
    iOwe = ColorPair(Color(0xFF6E2C3A), Color(0xFFFFDDE2)),
    settled = ColorPair(Color(0xFF2F2A24), Color(0xFFECE1D5)),
    card = Color(0xFF221E19),
    hues = mapOf(
        CategoryHue.GREEN to Color(0xFF8FD3A3),
        CategoryHue.ORANGE to Color(0xFFF2B07A),
        CategoryHue.BLUE to Color(0xFF9DBDF0),
        CategoryHue.SKY to Color(0xFF86CFE3),
        CategoryHue.AMBER to Color(0xFFEECB6A),
        CategoryHue.BROWN to Color(0xFFD7B597),
        CategoryHue.OLIVE to Color(0xFFC5CE80),
        CategoryHue.RUST to Color(0xFFEFA68C),
        CategoryHue.ROSE to Color(0xFFF2A7BF),
        CategoryHue.PLUM to Color(0xFFD9AAE4),
        CategoryHue.RED to Color(0xFFF2A2A4),
        CategoryHue.TEAL to Color(0xFF86D5C7),
        CategoryHue.INDIGO to Color(0xFFB0B3F2),
        CategoryHue.MAUVE to Color(0xFFDDB4CA),
        CategoryHue.SLATE to Color(0xFFB2C4D0),
        CategoryHue.NAVY to Color(0xFFA9BFE8),
        CategoryHue.GREY to Color(0xFFC4BBB0),
    ),
    tintAlpha = 0.20f,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Access to the app-specific theme values, next to `MaterialTheme`. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable
        get() = LocalAppColors.current
}
