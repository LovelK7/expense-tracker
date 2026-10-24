package hr.ml.expensetracker.ui.charts

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.ml.expensetracker.ui.theme.AppTheme
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * The fixed look of every chart (docs/DESIGN.md, "Analytics rules"): 2 dp lines, markers of at
 * least 8 dp with a 2 dp ring in the surface colour, columns at most 24 dp thick with 2 dp gaps,
 * hairline solid grid one step off the surface, text in the text colours (never a series colour).
 */
@Immutable
data class ChartStyle(
    /** The card the chart sits on: gaps between marks and rings around markers use it. */
    val surface: Color,
    val grid: Color,
    /** The zero line and the crosshair: one step stronger than [grid]. */
    val axis: Color,
    val text: Color,
    val mutedText: Color,
    val axisText: TextStyle,
    val labelText: TextStyle,
    val selection: Color,
) {
    companion object {
        val LINE_WIDTH: Dp = 2.dp
        val THIN_LINE_WIDTH: Dp = 1.5.dp
        val DASH: Dp = 5.dp
        val DASH_GAP: Dp = 4.dp
        val MARKER_RADIUS: Dp = 5.dp
        val MARKER_RING: Dp = 2.dp
        val MAX_BAR: Dp = 24.dp
        val GAP: Dp = 2.dp
        val BAR_RADIUS: Dp = 4.dp
        val HAIRLINE: Dp = 1.dp

        /** A 10 % wash under a single line. */
        const val AREA_ALPHA = 0.10f
    }
}

@Composable
@ReadOnlyComposable
fun chartStyle(): ChartStyle {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    return ChartStyle(
        surface = AppTheme.colors.card,
        grid = scheme.outlineVariant.copy(alpha = 0.7f),
        axis = scheme.outline,
        text = scheme.onSurface,
        mutedText = scheme.onSurfaceVariant,
        axisText = type.labelSmall.copy(color = scheme.onSurfaceVariant),
        labelText = type.labelMedium.copy(color = scheme.onSurface),
        selection = scheme.onSurface.copy(alpha = 0.06f),
    )
}

/** A label under the x-axis; [secondary] (e.g. the year) goes on a second line. */
@Immutable
data class AxisLabel(val primary: String, val secondary: String? = null)

/** "0", "500", "1.500": axis ticks in whole numbers; "0,25", "2,5" for fractional ticks below 10. */
fun axisNumber(value: Double): String = (if (value < 10.0 && value != floor(value)) UP_TO_TWO_DECIMALS else WHOLE).format(value)

private val HR = Locale.forLanguageTag("hr")
private val WHOLE: NumberFormat = NumberFormat.getIntegerInstance(HR).apply { isGroupingUsed = true }
private val UP_TO_TWO_DECIMALS: NumberFormat = NumberFormat.getNumberInstance(HR).apply { maximumFractionDigits = 2 }
private val ONE_DECIMAL: NumberFormat = NumberFormat.getNumberInstance(HR).apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}

/** "8,4": one decimal, Croatian comma. */
fun oneDecimal(value: Double): String = ONE_DECIMAL.format(value)

/**
 * Clean tick values from 0 up to at least [max]: about [count] steps of 1, 2, 2.5 or 5 × 10ⁿ.
 * Always at least [0, 1] so an all-zero chart still has an axis.
 */
fun niceTicks(max: Double, count: Int = 4): List<Double> {
    if (max <= 0.0 || max.isNaN()) return listOf(0.0, 1.0)
    val raw = max / count
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
    val top = ceil(max / step - 1e-9) * step
    return generateSequence(0.0) { it + step }.takeWhile { it <= top + step / 2 }.toList()
}

/**
 * Tap and horizontal drag select the nearest index; vertical drags stay with the scrolling list.
 * [indexAt] maps an x position (px) to an index or null.
 */
fun Modifier.selectByTouch(key: Any?, indexAt: (Float) -> Int?, onSelect: (Int) -> Unit): Modifier = this
    .pointerInput(key) { detectTapGestures { offset -> indexAt(offset.x)?.let(onSelect) } }
    .pointerInput(key) {
        detectHorizontalDragGestures(
            onDragStart = { offset -> indexAt(offset.x)?.let(onSelect) },
            onHorizontalDrag = { change, _ ->
                change.consume()
                indexAt(change.position.x)?.let(onSelect)
            },
        )
    }
