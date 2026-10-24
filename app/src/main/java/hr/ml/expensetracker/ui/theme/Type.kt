package hr.ml.expensetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.ml.expensetracker.R

// Bundled static instances of the Google Fonts variable fonts (OFL, see assets/licenses/).
// Outfit for headlines, titles and amounts; Inter for body text and labels.
private val Outfit = FontFamily(
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
)

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
)

/** Tabular figures: amounts in a column line up digit by digit. */
private const val TABULAR = "tnum"

private fun outfit(size: Int, line: Int, weight: FontWeight = FontWeight.SemiBold, spacing: Double = 0.0) = TextStyle(
    fontFamily = Outfit,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.em,
    fontFeatureSettings = TABULAR,
)

private fun inter(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.em,
    fontFeatureSettings = TABULAR,
)

val AppTypography = Typography(
    displayLarge = outfit(57, 64, spacing = -0.01),
    displayMedium = outfit(45, 52, spacing = -0.01),
    displaySmall = outfit(36, 44, spacing = -0.005),
    headlineLarge = outfit(32, 40),
    headlineMedium = outfit(28, 36),
    headlineSmall = outfit(24, 32),
    titleLarge = outfit(22, 28),
    titleMedium = outfit(16, 24, FontWeight.Medium, spacing = 0.005),
    titleSmall = outfit(14, 20, FontWeight.Medium, spacing = 0.005),
    bodyLarge = inter(16, 24),
    bodyMedium = inter(14, 20, spacing = 0.0015),
    bodySmall = inter(12, 16, spacing = 0.003),
    labelLarge = inter(14, 20, FontWeight.Medium, spacing = 0.005),
    labelMedium = inter(12, 16, FontWeight.Medium, spacing = 0.03),
    labelSmall = inter(11, 16, FontWeight.Medium, spacing = 0.03),
)
