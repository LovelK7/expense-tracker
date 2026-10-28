package hr.ml.expensetracker.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.ml.expensetracker.R
import hr.ml.expensetracker.ui.theme.AppTheme
import hr.ml.expensetracker.ui.theme.CategoryHue

/**
 * The icon and colour of each category (the domain only knows the names). Icons are Material
 * Symbols Rounded (filled) vector drawables; hues come from the theme ([AppTheme.colors]).
 * A category not listed here (a new or legacy name) gets [FALLBACK].
 */
object CategoryVisuals {
    class Visual(@param:DrawableRes val icon: Int, val hue: CategoryHue)

    private val FALLBACK = Visual(R.drawable.ic_cat_category, CategoryHue.GREY)

    private val BY_NAME: Map<String, Visual> = mapOf(
        "Hrana" to Visual(R.drawable.ic_cat_shopping_cart, CategoryHue.GREEN),
        "Hrana van" to Visual(R.drawable.ic_cat_restaurant, CategoryHue.ORANGE),
        "Prijevoz" to Visual(R.drawable.ic_cat_directions_car, CategoryHue.BLUE),
        "Putovanja" to Visual(R.drawable.ic_cat_flight, CategoryHue.SKY),
        "Režije" to Visual(R.drawable.ic_cat_bolt, CategoryHue.AMBER),
        "Stanarina" to Visual(R.drawable.ic_cat_home, CategoryHue.BROWN),
        "Potrepštine" to Visual(R.drawable.ic_cat_inventory_2, CategoryHue.OLIVE),
        "Adaptacija" to Visual(R.drawable.ic_cat_handyman, CategoryHue.RUST),
        "MPO" to Visual(R.drawable.ic_cat_family_restroom, CategoryHue.ROSE),
        "Odjeća i oprema" to Visual(R.drawable.ic_cat_checkroom, CategoryHue.PLUM),
        "Poklon" to Visual(R.drawable.ic_cat_redeem, CategoryHue.RED),
        "Zdravlje" to Visual(R.drawable.ic_cat_health_and_safety, CategoryHue.TEAL),
        "Zabava" to Visual(R.drawable.ic_cat_theater_comedy, CategoryHue.INDIGO),
        "Ostalo" to Visual(R.drawable.ic_cat_more_horiz, CategoryHue.GREY),
        "Usluge" to Visual(R.drawable.ic_cat_room_service, CategoryHue.MAUVE),
        "Prijenos" to Visual(R.drawable.ic_cat_swap_horiz, CategoryHue.SLATE),
        "Banka" to Visual(R.drawable.ic_cat_account_balance, CategoryHue.NAVY),
        "Edukacija" to Visual(R.drawable.ic_cat_school, CategoryHue.INDIGO),
        "Gotovina" to Visual(R.drawable.ic_cat_payments, CategoryHue.GREEN),
        "Kredit" to Visual(R.drawable.ic_cat_credit_card, CategoryHue.NAVY),
        "Osiguranje" to Visual(R.drawable.ic_cat_shield, CategoryHue.SLATE),
        "Armus stan" to Visual(R.drawable.ic_cat_apartment, CategoryHue.TEAL),
    )

    fun of(category: String): Visual = BY_NAME[category] ?: FALLBACK

    /**
     * The fixed order of categories in a multi-series chart (stack bottom to top, legend order).
     * Chosen with the dataviz palette validator so that the neighbours the real history produces
     * stay apart in both themes, also for colour-blind readers (docs/DESIGN.md, "Analytics rules").
     * Categories not listed follow alphabetically; "Ostalo" (the folded rest) always comes last.
     */
    val CHART_ORDER: List<String> = listOf(
        "Režije",
        "MPO",
        "Potrepštine",
        "Putovanja",
        "Stanarina",
        "Hrana",
        "Odjeća i oprema",
        "Adaptacija",
        "Zabava",
        "Armus stan",
        "Hrana van",
        "Prijevoz",
        "Zdravlje",
        "Poklon",
        "Usluge",
        "Banka",
        "Kredit",
        "Edukacija",
        "Gotovina",
        "Osiguranje",
        "Prijenos",
    )

    private const val OTHER = "Ostalo"

    /** [categories] in [CHART_ORDER]. */
    fun chartOrder(categories: Collection<String>): List<String> = categories.sortedWith(
        compareBy<String> { if (it == OTHER) 2 else if (it in CHART_ORDER) 0 else 1 }
            .thenBy { CHART_ORDER.indexOf(it) }
            .thenBy { it },
    )

    @DrawableRes
    fun icon(category: String): Int = of(category).icon

    /** The category's colour in the current (light or dark) theme. */
    @Composable
    @ReadOnlyComposable
    fun tint(category: String): Color = AppTheme.colors.hue(of(category).hue)

    /** A soft background in the category's colour (icon circles, selected chips). */
    @Composable
    @ReadOnlyComposable
    fun container(category: String): Color = tint(category).copy(alpha = AppTheme.colors.tintAlpha)
}

/** The category icon in a circle tinted with the category colour (list rows, Statistika). */
@Composable
fun CategoryBadge(category: String, modifier: Modifier = Modifier, size: Dp = 40.dp) = IconBadge(
    icon = CategoryVisuals.icon(category),
    tint = CategoryVisuals.tint(category),
    container = CategoryVisuals.container(category),
    modifier = modifier,
    size = size,
)

/** An icon in a tinted circle; [CategoryBadge] for categories, also used for settlements. */
@Composable
fun IconBadge(@DrawableRes icon: Int, tint: Color, container: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier.size(size).background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}
