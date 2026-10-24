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
 * The categorical hues for category icons, bars and chart series. Which category gets which hue
 * (and icon) is decided in `ui/common/CategoryVisuals`; the values per light/dark theme live here.
 */
enum class CategoryHue { GREEN, ORANGE, BLUE, SKY, AMBER, BROWN, OLIVE, RUST, ROSE, PLUM, RED, TEAL, INDIGO, MAUVE, SLATE, NAVY, GREY }

/**
 * Chart colours (Stage 9). Every set was run through the dataviz palette validator against
 * [AppColors.card] (docs/DESIGN.md, "Analytics rules"): the category hues in their chart order,
 * the person pair, the year slots and the heatmap ramp.
 */
@Immutable
data class ChartColors(
    /** Lovel's and Marina's series ("Tko plaća"), a validated pair. */
    val lovel: Color,
    val marina: Color,
    /**
     * Year lines ("Kumulativno po godinama"): slot `floorMod(2026 − year, 6)`, so a year keeps its
     * colour and consecutive years are neighbours in the validated order.
     */
    val years: List<Color>,
    /** The calendar heatmap, light to dark (dark: dim to bright), one step per level 1..5. */
    val heatmap: List<Color>,
    /** A heatmap day without spending. */
    val heatmapEmpty: Color,
)

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
    val chart: ChartColors,
) {
    fun hue(hue: CategoryHue): Color = hues.getValue(hue)
}

// Category hues: re-stepped in Stage 9 (same hue families) so that the stacks the real history
// produces pass the validator in both themes; the dark set sits in the dark lightness band
// (OKLCH L 0.48–0.67) instead of the former pastels.
val LightAppColors = AppColors(
    owedToMe = ColorPair(Color(0xFF136B5E), Color(0xFFFFFFFF)),
    iOwe = ColorPair(Color(0xFFA14A5A), Color(0xFFFFFFFF)),
    settled = ColorPair(Color(0xFFEAE3D8), Color(0xFF1F1B16)),
    card = Color(0xFFFFFFFF),
    hues = mapOf(
        CategoryHue.GREEN to Color(0xFF4F9754),
        CategoryHue.ORANGE to Color(0xFFDC7033),
        CategoryHue.BLUE to Color(0xFF2665BF),
        CategoryHue.SKY to Color(0xFF0C89B2),
        CategoryHue.AMBER to Color(0xFFA58007),
        CategoryHue.BROWN to Color(0xFF814B0D),
        CategoryHue.OLIVE to Color(0xFF637122),
        CategoryHue.RUST to Color(0xFFB75112),
        CategoryHue.ROSE to Color(0xFFC15474),
        CategoryHue.PLUM to Color(0xFF8A4AA3),
        CategoryHue.RED to Color(0xFFBC4045),
        CategoryHue.TEAL to Color(0xFF028779),
        CategoryHue.INDIGO to Color(0xFF7E75E1),
        CategoryHue.MAUVE to Color(0xFFA55584),
        CategoryHue.SLATE to Color(0xFF5A6F7E),
        CategoryHue.NAVY to Color(0xFF4369A2),
        CategoryHue.GREY to Color(0xFF746C63),
    ),
    tintAlpha = 0.14f,
    chart = ChartColors(
        lovel = Color(0xFF1D9999),
        marina = Color(0xFF813450),
        years = listOf(0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300).map { Color(it) },
        heatmap = listOf(0xFF55C2BC, 0xFF18A7A1, 0xFF028984, 0xFF046C68, 0xFF04504D).map { Color(it) },
        heatmapEmpty = Color(0xFFEEE8DF),
    ),
)

val DarkAppColors = AppColors(
    owedToMe = ColorPair(Color(0xFF1C5A51), Color(0xFFD6F5EE)),
    iOwe = ColorPair(Color(0xFF6E2C3A), Color(0xFFFFDDE2)),
    settled = ColorPair(Color(0xFF2F2A24), Color(0xFFECE1D5)),
    card = Color(0xFF221E19),
    hues = mapOf(
        CategoryHue.GREEN to Color(0xFF52A958),
        CategoryHue.ORANGE to Color(0xFFE17024),
        CategoryHue.BLUE to Color(0xFF2B77BA),
        CategoryHue.SKY to Color(0xFF2BA2C4),
        CategoryHue.AMBER to Color(0xFFAD8F1D),
        CategoryHue.BROWN to Color(0xFF9A6A2A),
        CategoryHue.OLIVE to Color(0xFF668312),
        CategoryHue.RUST to Color(0xFFC44A37),
        CategoryHue.ROSE to Color(0xFFC4738B),
        CategoryHue.PLUM to Color(0xFFB876D2),
        CategoryHue.RED to Color(0xFFCC5E66),
        CategoryHue.TEAL to Color(0xFF129E8E),
        CategoryHue.INDIGO to Color(0xFF798DE6),
        CategoryHue.MAUVE to Color(0xFFB16D94),
        CategoryHue.SLATE to Color(0xFF738D9F),
        CategoryHue.NAVY to Color(0xFF6586C3),
        CategoryHue.GREY to Color(0xFF8C847B),
    ),
    tintAlpha = 0.20f,
    chart = ChartColors(
        lovel = Color(0xFF07A4A4),
        marina = Color(0xFFA4546F),
        years = listOf(0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF008300).map { Color(it) },
        heatmap = listOf(0xFF085955, 0xFF097974, 0xFF209993, 0xFF39BAB4, 0xFF60DBD4).map { Color(it) },
        heatmapEmpty = Color(0xFF2F2A24),
    ),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Access to the app-specific theme values, next to `MaterialTheme`. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable
        get() = LocalAppColors.current
}
