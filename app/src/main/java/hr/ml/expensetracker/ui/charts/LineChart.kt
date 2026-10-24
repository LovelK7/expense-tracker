package hr.ml.expensetracker.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** One line: a value per x index (null: no point there; the line breaks). */
@Immutable
data class LineSeries(
    val label: String,
    val color: Color,
    val values: List<Double?>,
    /** Thinner and dashed: a derived series (rolling average, a share). */
    val dashed: Boolean = false,
    /** A 10 % wash under the line (a single main series). */
    val area: Boolean = false,
    /** Text drawn right of the line's last point (a direct label), in the text colour. */
    val endLabel: String? = null,
)

/**
 * A line chart over x indices 0 until [pointCount] with one y-axis from 0. [selected] gets a
 * crosshair and a marker on every series; tap or drag moves it ([onSelect]).
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    pointCount: Int,
    xLabels: List<AxisLabel?>,
    selected: Int,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    yFormatter: (Double) -> String = ::axisNumber,
    /** Leaves room at the right for end labels even when they are short. */
    minEndGutter: Dp = 0.dp,
    /** At least this much room for the y labels, to line the plot up with a chart above it. */
    minStartGutter: Dp = 0.dp,
    /**
     * Points at the centres of equal slots (like [ColumnChart]'s columns) instead of edge to edge,
     * so a line under a column chart with the same [minStartGutter] lines up with its columns.
     */
    slotAligned: Boolean = false,
) {
    val style = chartStyle()
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val geometry = remember { PlotGeometry() }
    val maxValue = remember(series) { series.maxOfOrNull { s -> s.values.maxOfOrNull { it ?: 0.0 } ?: 0.0 } ?: 0.0 }
    val ticks = remember(maxValue) { niceTicks(maxValue) }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .selectByTouch(pointCount, if (slotAligned) geometry::slotIndexAt else geometry::lineIndexAt, onSelect),
    ) {
        val yLabels = ticks.map { measurer.measure(yFormatter(it), style.axisText) }
        val xLayouts = xLabels.map { label -> label?.let { measureAxisLabel(measurer, it, style) } }
        val endLayouts = series.map { s -> s.endLabel?.let { measurer.measure(it, style.labelText) } }
        val left = maxOf((yLabels.maxOfOrNull { it.size.width } ?: 0) + 6.dp.toPx(), minStartGutter.toPx())
        val endWidth = maxOf(endLayouts.maxOfOrNull { it?.size?.width ?: 0 }?.toFloat() ?: 0f, minEndGutter.toPx())
        val right = size.width - when {
            endWidth > 0f -> endWidth + 10.dp.toPx()
            slotAligned -> 0f
            else -> ChartStyle.MARKER_RADIUS.toPx() + 2.dp.toPx()
        }
        val xBand = (xLayouts.maxOfOrNull { it?.height ?: 0f } ?: 0f) + 6.dp.toPx()
        val plot = Rect(left, 8.dp.toPx(), right, size.height - xBand)
        geometry.update(plot, pointCount, slotAligned)
        val top = ticks.last()
        fun xAt(i: Int) = geometry.xAt(i)
        fun yAt(v: Double) = plot.bottom - (v / top).toFloat() * plot.height

        drawGrid(ticks, yLabels, plot, style, ::yAt)
        // x labels, centred under their point but kept inside the canvas
        xLayouts.forEachIndexed { i, layout ->
            if (layout != null) drawAxisLabel(layout, xAt(i), plot.bottom + 6.dp.toPx(), size.width)
        }
        // crosshair
        if (selected in 0 until pointCount) {
            drawLine(style.axis, Offset(xAt(selected), plot.top), Offset(xAt(selected), plot.bottom), ChartStyle.HAIRLINE.toPx())
        }
        series.forEach { s -> drawSeries(s, ::xAt, ::yAt, plot) }
        // End labels right of each series' last point, the last series first (the newest year, the
        // derived line); a label that would collide is left to the legend and the read-out. A halo
        // in the surface colour keeps a label readable where another line passes behind it.
        val placed = ArrayList<Rect>()
        series.indices.reversed().forEach { index ->
            val s = series[index]
            val layout = endLayouts[index] ?: return@forEach
            val last = s.values.indexOfLast { it != null }
            if (last < 0) return@forEach
            val x = xAt(last) + ChartStyle.MARKER_RADIUS.toPx() + 4.dp.toPx()
            val y = (yAt(s.values[last]!!) - layout.size.height / 2f).coerceIn(0f, size.height - xBand - layout.size.height)
            val box = Rect(x, y, x + layout.size.width, y + layout.size.height)
            if (placed.none { it.overlaps(box) } && box.right <= size.width + 0.5f) {
                placed += box
                drawRoundRect(
                    color = style.surface,
                    topLeft = Offset(x - 2.dp.toPx(), y),
                    size = Size(box.width + 4.dp.toPx(), box.height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                drawText(layout, topLeft = Offset(x, y))
            }
        }
        if (selected in 0 until pointCount) {
            series.forEach { s ->
                val v = s.values.getOrNull(selected) ?: return@forEach
                drawMarker(Offset(xAt(selected), yAt(v)), s.color, style.surface)
            }
        }
    }
}

/** Where the plot area is, so touch positions map to indices. Updated on every draw. */
internal class PlotGeometry {
    var plot: Rect = Rect.Zero
        private set
    var count: Int = 0
        private set

    private var slots = false

    fun update(plot: Rect, count: Int, slots: Boolean = false) {
        this.plot = plot
        this.count = count
        this.slots = slots
    }

    /** Points spread from the plot's left to its right edge, or at the centres of equal slots. */
    fun xAt(i: Int): Float = when {
        slots -> plot.left + plot.width / maxOf(count, 1) * (i + 0.5f)
        count <= 1 -> plot.center.x
        else -> plot.left + plot.width * i / (count - 1)
    }

    fun lineIndexAt(x: Float): Int? {
        if (count == 0 || plot.width <= 0f) return null
        if (count == 1) return 0
        return ((x - plot.left) / plot.width * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }

    /** Columns: one equal slot each. */
    fun slotIndexAt(x: Float): Int? {
        if (count == 0 || plot.width <= 0f) return null
        return ((x - plot.left) / (plot.width / count)).toInt().coerceIn(0, count - 1)
    }
}

internal class AxisLabelLayout(val primary: TextLayoutResult, val secondary: TextLayoutResult?) {
    val height: Float get() = primary.size.height + (secondary?.size?.height ?: 0).toFloat()
    val width: Float get() = maxOf(primary.size.width, secondary?.size?.width ?: 0).toFloat()
}

internal fun measureAxisLabel(measurer: TextMeasurer, label: AxisLabel, style: ChartStyle) = AxisLabelLayout(
    measurer.measure(label.primary, style.axisText),
    label.secondary?.let { measurer.measure(it, style.axisText) },
)

/** Draws [layout] centred on [centerX] below [top], shifted to stay within 0..[maxX]. */
internal fun DrawScope.drawAxisLabel(layout: AxisLabelLayout, centerX: Float, top: Float, maxX: Float) {
    fun draw(text: TextLayoutResult, y: Float) {
        val x = (centerX - text.size.width / 2f).coerceIn(0f, maxOf(0f, maxX - text.size.width))
        drawText(text, topLeft = Offset(x, y))
    }
    draw(layout.primary, top)
    layout.secondary?.let { draw(it, top + layout.primary.size.height) }
}

/** Horizontal hairlines at the ticks (the zero line one step stronger) and their labels. */
internal fun DrawScope.drawGrid(
    ticks: List<Double>,
    labels: List<TextLayoutResult>,
    plot: Rect,
    style: ChartStyle,
    yAt: (Double) -> Float,
) {
    ticks.forEachIndexed { i, tick ->
        val y = yAt(tick)
        drawLine(
            color = if (i == 0) style.axis else style.grid,
            start = Offset(plot.left, y),
            end = Offset(plot.right, y),
            strokeWidth = ChartStyle.HAIRLINE.toPx(),
        )
        val label = labels[i]
        drawText(label, topLeft = Offset(plot.left - 6.dp.toPx() - label.size.width, y - label.size.height / 2f))
    }
}

private fun DrawScope.drawSeries(s: LineSeries, xAt: (Int) -> Float, yAt: (Double) -> Float, plot: Rect) {
    val path = Path()
    val area = Path()
    var open = false
    var segmentStart = 0
    var lastIndex = -1
    fun closeArea() {
        if (s.area && lastIndex >= 0) {
            area.lineTo(xAt(lastIndex), plot.bottom)
            area.lineTo(xAt(segmentStart), plot.bottom)
            area.close()
        }
    }
    s.values.forEachIndexed { i, v ->
        if (v == null) {
            if (open) closeArea()
            open = false
            return@forEachIndexed
        }
        val p = Offset(xAt(i), yAt(v))
        if (!open) {
            path.moveTo(p.x, p.y)
            if (s.area) area.moveTo(p.x, p.y)
            segmentStart = i
            open = true
        } else {
            path.lineTo(p.x, p.y)
            if (s.area) area.lineTo(p.x, p.y)
        }
        lastIndex = i
    }
    if (open) closeArea()
    if (s.area) drawPath(area, s.color.copy(alpha = ChartStyle.AREA_ALPHA))
    val width = if (s.dashed) ChartStyle.THIN_LINE_WIDTH.toPx() else ChartStyle.LINE_WIDTH.toPx()
    drawPath(
        path,
        s.color,
        style = Stroke(
            width = width,
            cap = if (s.dashed) StrokeCap.Butt else StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(ChartStyle.DASH.toPx(), ChartStyle.DASH_GAP.toPx())) else null,
        ),
    )
    // A lone point between gaps would be invisible as a path: draw it as a dot.
    s.values.forEachIndexed { i, v ->
        if (v != null && s.values.getOrNull(i - 1) == null && s.values.getOrNull(i + 1) == null) {
            drawCircle(s.color, radius = width * 1.5f, center = Offset(xAt(i), yAt(v)))
        }
    }
}

/** The selected point: a 10 dp dot in the series colour with a 2 dp ring in the surface colour. */
internal fun DrawScope.drawMarker(center: Offset, color: Color, surface: Color) {
    drawCircle(surface, radius = ChartStyle.MARKER_RADIUS.toPx() + ChartStyle.MARKER_RING.toPx(), center = center)
    drawCircle(color, radius = ChartStyle.MARKER_RADIUS.toPx(), center = center)
}
