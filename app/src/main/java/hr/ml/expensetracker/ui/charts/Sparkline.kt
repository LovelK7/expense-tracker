package hr.ml.expensetracker.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small multiple: one series as a 2 dp line with a 10 % wash, a baseline and a marker on
 * [selected] (tap or drag to move it). No axes: the card around it carries the numbers.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    selected: Int,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
) {
    val style = chartStyle()
    val geometry = remember { PlotGeometry() }
    val max = remember(values) { values.maxOrNull()?.takeIf { it > 0 } ?: 1.0 }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .selectByTouch(values.size, geometry::lineIndexAt, onSelect),
    ) {
        val inset = ChartStyle.MARKER_RADIUS.toPx() + ChartStyle.MARKER_RING.toPx()
        val plot = Rect(inset, inset, size.width - inset, size.height - 1.dp.toPx())
        geometry.update(plot, values.size)
        fun y(v: Double) = plot.bottom - (v / max).toFloat() * plot.height
        drawLine(style.axis, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), ChartStyle.HAIRLINE.toPx())
        if (values.isEmpty()) return@Canvas
        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(geometry.xAt(i), y(v)) else line.lineTo(geometry.xAt(i), y(v)) }
        val area = Path().apply {
            addPath(line)
            lineTo(geometry.xAt(values.lastIndex), plot.bottom)
            lineTo(geometry.xAt(0), plot.bottom)
            close()
        }
        drawPath(area, color.copy(alpha = ChartStyle.AREA_ALPHA))
        drawPath(line, color, style = Stroke(ChartStyle.LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (selected in values.indices) {
            drawLine(style.grid, Offset(geometry.xAt(selected), plot.top), Offset(geometry.xAt(selected), plot.bottom), ChartStyle.HAIRLINE.toPx())
            drawMarker(Offset(geometry.xAt(selected), y(values[selected])), color, style.surface)
        }
    }
}
