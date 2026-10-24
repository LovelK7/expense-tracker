package hr.ml.expensetracker.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import hr.ml.expensetracker.domain.SpendingCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * GitHub-style calendar of daily spending: one column per week (Monday on top), a cell per day
 * coloured by its [SpendingCalendar.level] ([ramp] for levels 1..5, [empty] for no spending),
 * month names above the week a month starts in. Scrolls horizontally, starting at the newest
 * week; tapping a day selects it ([onSelect]), the selected day is ringed.
 */
@Composable
fun CalendarHeatmap(
    calendar: SpendingCalendar,
    ramp: List<Color>,
    empty: Color,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    monthNames: List<String>,
    /** Monday, Wednesday and Friday labels (the rows in between stay unlabelled). */
    dayNames: List<String>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val style = chartStyle()
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val cell = 13.dp
    val gap = ChartStyle.GAP
    val firstMonday = remember(calendar) { calendar.first.with(DayOfWeek.MONDAY) }
    val weeks = remember(calendar) { (ChronoUnit.DAYS.between(firstMonday, calendar.last) / 7 + 1).toInt() }
    val monthBand = 18.dp
    val scroll = rememberScrollState()
    LaunchedEffect(calendar) { scroll.scrollTo(scroll.maxValue) }
    val density = LocalDensity.current
    val step = with(density) { (cell + gap).toPx() }
    val bandPx = with(density) { monthBand.toPx() }

    Row(modifier.semantics { contentDescription = description }) {
        // Day names stay put while the weeks scroll.
        Column(Modifier.padding(top = monthBand, end = 6.dp)) {
            for (row in 0 until 7) {
                Box(Modifier.height(cell + gap)) {
                    val label = when (row) {
                        0 -> dayNames.getOrNull(0)
                        2 -> dayNames.getOrNull(1)
                        4 -> dayNames.getOrNull(2)
                        else -> null
                    }
                    if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box(Modifier.horizontalScroll(scroll)) {
            Canvas(
                Modifier
                    .width((cell + gap) * weeks)
                    .height(monthBand + (cell + gap) * 7)
                    .pointerInput(calendar) {
                        detectTapGestures { p ->
                            val week = (p.x / step).toInt()
                            val row = ((p.y - bandPx) / step).toInt()
                            if (p.y < bandPx || row !in 0..6 || week !in 0 until weeks) return@detectTapGestures
                            val date = firstMonday.plusDays(week * 7L + row)
                            if (!date.isBefore(calendar.first) && !date.isAfter(calendar.last)) onSelect(date)
                        }
                    },
            ) {
                val c = cell.toPx()
                val radius = CornerRadius(2.dp.toPx())
                var lastLabelWeek = -10
                for (week in 0 until weeks) {
                    for (row in 0 until 7) {
                        val date = firstMonday.plusDays(week * 7L + row)
                        if (date.isBefore(calendar.first) || date.isAfter(calendar.last)) continue
                        val level = calendar.level(calendar.cents(date))
                        val topLeft = Offset(week * step, bandPx + row * step)
                        drawRoundRect(if (level == 0) empty else ramp[level - 1], topLeft, Size(c, c), radius)
                        if (date == selected) {
                            val ring = 2.dp.toPx()
                            drawRoundRect(
                                color = style.text,
                                topLeft = topLeft - Offset(ring / 2, ring / 2),
                                size = Size(c + ring, c + ring),
                                cornerRadius = radius,
                                style = Stroke(ring),
                            )
                        }
                        // Month label above the week holding the 1st (or the first week shown).
                        if ((date.dayOfMonth == 1 || (week == 0 && row == 0)) && week - lastLabelWeek >= 3) {
                            val layout = measurer.measure(monthNames[date.monthValue - 1], style.axisText)
                            drawText(layout, topLeft = Offset(week * step, 0f))
                            lastLabelWeek = week
                        }
                    }
                }
            }
        }
    }
}

/** The heatmap's scale: "0 €" for no spending, then one swatch per level with its range. */
@Composable
fun HeatmapLegend(ramp: List<Color>, empty: Color, labels: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (listOf(empty) + ramp).forEachIndexed { i, color ->
            val label = labels.getOrNull(i) ?: return@forEachIndexed
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Canvas(Modifier.padding(top = 2.dp).width(11.dp).height(11.dp)) {
                    drawRoundRect(color, cornerRadius = CornerRadius(2.dp.toPx()))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
