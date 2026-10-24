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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One series of a column chart: a value per column. Several series stack, the first at the bottom. */
@Immutable
data class ColumnSeries(val label: String, val color: Color, val values: List<Double>)

/** A vertical reference line (e.g. the median) at [position] slots from the left (0..columns). */
@Immutable
data class ColumnMarker(val position: Float, val label: String)

/**
 * A column chart with one y-axis from 0: single, stacked ([series] bottom to top) or, with
 * [percent], every column scaled to 100 %. Columns are at most 24 dp thick with at least 2 dp
 * between them, stacked segments are separated by a 2 dp surface gap, the top is rounded.
 * [valueLabels] go on the caps when they fit; [selected] gets a soft band behind its column.
 */
@Composable
fun ColumnChart(
    series: List<ColumnSeries>,
    columnCount: Int,
    xLabels: List<AxisLabel?>,
    selected: Int,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    percent: Boolean = false,
    yFormatter: (Double) -> String = ::axisNumber,
    valueLabels: List<String>? = null,
    marker: ColumnMarker? = null,
    /** At least this much room for the y labels, to line the plot up with a chart below it. */
    minStartGutter: Dp = 0.dp,
) {
    val style = chartStyle()
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val geometry = remember { PlotGeometry() }
    val totals = remember(series, columnCount) {
        List(columnCount) { i -> series.sumOf { it.values.getOrElse(i) { 0.0 } } }
    }
    val ticks = remember(totals, percent) {
        if (percent) listOf(0.0, 25.0, 50.0, 75.0, 100.0) else niceTicks(totals.maxOrNull() ?: 0.0)
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .selectByTouch(columnCount, geometry::slotIndexAt, onSelect),
    ) {
        val yLabels = ticks.map { measurer.measure(if (percent) "${it.toInt()} %" else yFormatter(it), style.axisText) }
        val xLayouts = xLabels.map { label -> label?.let { measureAxisLabel(measurer, it, style) } }
        val capLayouts = valueLabels?.map { measurer.measure(it, style.axisText) }
        val markerLayout = marker?.let { measurer.measure(it.label, style.labelText) }
        val left = maxOf((yLabels.maxOfOrNull { it.size.width } ?: 0) + 6.dp.toPx(), minStartGutter.toPx())
        val xBand = (xLayouts.maxOfOrNull { it?.height ?: 0f } ?: 0f) + 6.dp.toPx()
        val topPad = maxOf(capLayouts?.maxOfOrNull { it.size.height }?.toFloat() ?: 0f, markerLayout?.size?.height?.toFloat() ?: 0f) + 4.dp.toPx()
        val plot = Rect(left, topPad, size.width, size.height - xBand)
        geometry.update(plot, columnCount)
        val top = ticks.last()
        fun yAt(v: Double) = plot.bottom - (v / top).toFloat() * plot.height

        val slot = plot.width / maxOf(columnCount, 1)
        val gap = ChartStyle.GAP.toPx()
        val barWidth = minOf(ChartStyle.MAX_BAR.toPx(), slot - gap).coerceAtLeast(1f)
        fun centerX(i: Int) = plot.left + slot * (i + 0.5f)

        if (selected in 0 until columnCount) {
            drawRect(style.selection, topLeft = Offset(plot.left + slot * selected, plot.top), size = Size(slot, plot.height))
        }
        drawGrid(ticks, yLabels, plot, style, ::yAt)
        // x labels: skip a label that would run into the previous one
        var lastRight = Float.NEGATIVE_INFINITY
        xLayouts.forEachIndexed { i, layout ->
            if (layout == null) return@forEachIndexed
            val l = centerX(i) - layout.width / 2f
            if (l < lastRight + 4.dp.toPx()) return@forEachIndexed
            lastRight = l + layout.width
            drawAxisLabel(layout, centerX(i), plot.bottom + 6.dp.toPx(), size.width)
        }

        // The marker's label sits above the plot; a cap label it would cover is left to the read-out.
        val markerRect = if (marker != null && markerLayout != null) {
            val x = plot.left + slot * marker.position
            val labelX = if (x + 4.dp.toPx() + markerLayout.size.width <= size.width) x + 4.dp.toPx() else x - 4.dp.toPx() - markerLayout.size.width
            Rect(labelX, plot.top - markerLayout.size.height, labelX + markerLayout.size.width, plot.top)
        } else {
            null
        }
        val radius = minOf(ChartStyle.BAR_RADIUS.toPx(), barWidth / 2f)
        for (i in 0 until columnCount) {
            val total = totals[i]
            if (total <= 0.0) continue
            val scale = if (percent) 100.0 / total else 1.0
            val x0 = centerX(i) - barWidth / 2f
            val segments = series.mapIndexedNotNull { s, ser ->
                ser.values.getOrElse(i) { 0.0 }.takeIf { it > 0.0 }?.let { s to it * scale }
            }
            var base = 0.0
            segments.forEachIndexed { k, (s, v) ->
                val yBottom = yAt(base) - if (k > 0) gap / 2f else 0f
                val yTop = yAt(base + v) + if (k < segments.lastIndex) gap / 2f else 0f
                base += v
                if (yBottom - yTop <= 0f) return@forEachIndexed
                val color = series[s].color
                if (k == segments.lastIndex) {
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(x0, yTop, x0 + barWidth, yBottom),
                                topLeft = CornerRadius(radius),
                                topRight = CornerRadius(radius),
                            ),
                        )
                    }
                    drawPath(path, color)
                } else {
                    drawRect(color, topLeft = Offset(x0, yTop), size = Size(barWidth, yBottom - yTop))
                }
            }
        }

        // The reference line goes over the columns but under the cap labels, which get a halo.
        if (marker != null && markerLayout != null && markerRect != null) {
            val x = plot.left + slot * marker.position
            drawLine(style.text, Offset(x, plot.top - markerLayout.size.height), Offset(x, plot.bottom), ChartStyle.HAIRLINE.toPx())
            drawText(markerLayout, topLeft = markerRect.topLeft)
        }
        capLayouts?.forEachIndexed { i, cap ->
            val total = totals.getOrElse(i) { 0.0 }
            if (total <= 0.0 && !percent) return@forEachIndexed
            val y = yAt(if (percent) 100.0 else total) - cap.size.height - 2.dp.toPx()
            val capRect = Rect(Offset(centerX(i) - cap.size.width / 2f, y), Size(cap.size.width.toFloat(), cap.size.height.toFloat()))
            if (cap.size.width <= slot + 2.dp.toPx() && markerRect?.overlaps(capRect) != true) {
                drawRoundRect(
                    color = style.surface,
                    topLeft = capRect.topLeft - Offset(2.dp.toPx(), 0f),
                    size = Size(capRect.width + 4.dp.toPx(), capRect.height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                drawText(cap, topLeft = capRect.topLeft)
            }
        }
    }
}
