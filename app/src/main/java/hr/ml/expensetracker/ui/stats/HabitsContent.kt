package hr.ml.expensetracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.AmountBucket
import hr.ml.expensetracker.domain.AnalyticsRange
import hr.ml.expensetracker.domain.CroatianPlural
import hr.ml.expensetracker.domain.DescriptionStat
import hr.ml.expensetracker.domain.HabitTiles
import hr.ml.expensetracker.domain.HabitsReport
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Percent
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.Quarter
import hr.ml.expensetracker.domain.SpendingCalendar
import hr.ml.expensetracker.domain.StackedSeries
import hr.ml.expensetracker.ui.charts.AxisLabel
import hr.ml.expensetracker.ui.charts.CalendarHeatmap
import hr.ml.expensetracker.ui.charts.ChartCard
import hr.ml.expensetracker.ui.charts.ChartNote
import hr.ml.expensetracker.ui.charts.ChartReadout
import hr.ml.expensetracker.ui.charts.ColumnChart
import hr.ml.expensetracker.ui.charts.ColumnMarker
import hr.ml.expensetracker.ui.charts.ColumnSeries
import hr.ml.expensetracker.ui.charts.HeatmapLegend
import hr.ml.expensetracker.ui.charts.Legend
import hr.ml.expensetracker.ui.charts.LegendEntry
import hr.ml.expensetracker.ui.charts.LineChart
import hr.ml.expensetracker.ui.charts.LineSeries
import hr.ml.expensetracker.ui.charts.ReadoutRow
import hr.ml.expensetracker.ui.charts.SeriesKey
import hr.ml.expensetracker.ui.charts.axisNumber
import hr.ml.expensetracker.ui.charts.oneDecimal
import hr.ml.expensetracker.ui.common.CategoryBadge
import hr.ml.expensetracker.ui.common.CategoryVisuals
import hr.ml.expensetracker.ui.common.expenseCountLabel
import hr.ml.expensetracker.ui.common.longDate
import hr.ml.expensetracker.ui.common.monthLabel
import hr.ml.expensetracker.ui.theme.AppTheme
import java.time.LocalDate

/** The Navike inner tab: how often, when and how much we spend. */
@Composable
internal fun HabitsContent(
    state: HabitsUiState,
    selection: AnalyticsSelection,
    onRangeChange: (AnalyticsRange) -> Unit,
    onPersonChange: (Person?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(StatsTags.HABITS_LIST),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { AnalyticsHeader(selection, onRangeChange, onPersonChange) }
        val report = state.report
        if (analyticsStatus(state.loading, state.error, state.empty) && report != null) habits(report)
    }
}

private fun LazyListScope.habits(report: HabitsReport) {
    item(key = "tiles") { StatTiles(report.tiles) }
    item(key = "frequency") { FrequencyCard(report) }
    item(key = "weekdays") { WeekdaysCard(report) }
    item(key = "days_of_month") { DayOfMonthCard(report) }
    item(key = "calendar") { CalendarCard(report.calendar) }
    item(key = "histogram") { HistogramCard(report.histogram, report.tiles.medianCents) }
    item(key = "quarters") { QuartersCard(report.quarterStack) }
    item(key = "top") { TopDescriptionsCard(report.topDescriptions) }
}

// ---- 6. stat tiles ----

@Composable
private fun StatTiles(tiles: HabitTiles) {
    val none = stringResource(R.string.none)
    val gap = tiles.longestGap
    val busiest = tiles.busiestDay
    val entries = listOf(
        Triple(stringResource(R.string.habits_tile_monthly), Money.format(tiles.monthlyAverageCents), null),
        Triple(stringResource(R.string.habits_tile_median), tiles.medianCents?.let(Money::format) ?: none, null),
        Triple(
            stringResource(R.string.habits_tile_per_week),
            oneDecimal(tiles.expensesPerWeek),
            stringResource(R.string.habits_per_week_detail, expenseCountLabel(tiles.expenseCount)),
        ),
        Triple(
            stringResource(R.string.habits_tile_between),
            tiles.averageDaysBetween?.let { stringResource(R.string.days_decimal, oneDecimal(it)) } ?: none,
            stringResource(R.string.habits_between_detail),
        ),
        Triple(
            stringResource(R.string.habits_tile_gap),
            gap?.let { dayCountLabel(it.days) } ?: none,
            gap?.let { dateRangeLabel(it.from, it.to) },
        ),
        Triple(stringResource(R.string.habits_tile_busiest), busiest?.let { Money.format(it.cents) } ?: none, busiest?.let { longDate(it.date) }),
    )
    Column(Modifier.testTag(StatsTags.TILES), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (label, value, detail) -> StatTile(label, value, detail, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/** A hero number: label, value (proportional figures), optional detail. */
@Composable
private fun StatTile(label: String, value: String, detail: String?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(AppTheme.colors.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "pnum"),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(StatsTags.tile(label)),
        )
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "4. svibnja 2026.", "1.–18. travnja 2026.", "28. ožujka – 3. travnja 2026.", or two full dates. */
@Composable
private fun dateRangeLabel(from: LocalDate, to: LocalDate): String {
    val months = stringArrayResource(R.array.months_genitive)
    return when {
        from == to -> longDate(from)
        from.year == to.year && from.month == to.month ->
            stringResource(R.string.date_range_same_month, from.dayOfMonth, to.dayOfMonth, months[to.monthValue - 1], to.year)
        from.year == to.year ->
            stringResource(R.string.date_range_same_year, from.dayOfMonth, months[from.monthValue - 1], to.dayOfMonth, months[to.monthValue - 1], to.year)
        else -> stringResource(R.string.date_range, longDate(from), longDate(to))
    }
}

@Composable
private fun dayCountLabel(days: Int): String = stringResource(
    CroatianPlural.choose(days.toLong(), R.string.day_count_one, R.string.day_count_few, R.string.day_count_many),
    days,
)

// ---- 7. how often ----

@Composable
private fun FrequencyCard(report: HabitsReport) {
    val activity = report.activity
    val months = activity.map { it.month }
    val short = stringArrayResource(R.array.months_short)
    val accent = MaterialTheme.colorScheme.primary
    var selected by remember(report.window) { mutableIntStateOf(months.lastIndex) }
    val labels = remember(months) { monthAxisLabels(months, short) }
    val counts = remember(activity, accent) { listOf(ColumnSeries("count", accent, activity.map { it.count.toDouble() })) }
    val averages = remember(activity, accent) {
        listOf(LineSeries("average", accent, activity.map { it.averageCents?.euros() }, area = true))
    }
    ChartCard(
        title = stringResource(R.string.habits_frequency_title),
        subtitle = stringResource(R.string.habits_frequency_subtitle),
        tag = StatsTags.card("frequency"),
    ) {
        val point = activity[selected]
        ChartReadout(
            title = monthLabel(point.month),
            rows = listOfNotNull(
                ReadoutRow(SeriesKey.Box(accent), "", expenseCountLabel(point.count)),
                point.averageCents?.let { ReadoutRow(SeriesKey.Line(accent), stringResource(R.string.series_average), Money.format(it)) },
            ),
        )
        ColumnChart(
            series = counts,
            columnCount = months.size,
            xLabels = labels,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.habits_frequency_description),
            height = 160.dp,
            minStartGutter = FREQUENCY_GUTTER,
        )
        // A second chart, not a second axis: the average per expense on its own scale.
        Column(Modifier.padding(top = 6.dp)) {
            Text(stringResource(R.string.habits_average_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.habits_average_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LineChart(
            series = averages,
            pointCount = months.size,
            xLabels = labels,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.habits_average_description),
            height = 150.dp,
            minStartGutter = FREQUENCY_GUTTER,
            slotAligned = true,
        )
    }
}

/** Both "Koliko često" charts reserve the same y-label room, so months line up. */
private val FREQUENCY_GUTTER = 36.dp

// ---- 8. weekdays ----

@Composable
private fun WeekdaysCard(report: HabitsReport) {
    val stats = report.weekdays
    val short = stringArrayResource(R.array.weekdays_short)
    val full = stringArrayResource(R.array.weekdays_full)
    val accent = MaterialTheme.colorScheme.primary
    val total = stats.sumOf { it.cents }
    val shares = remember(stats) { stats.map { if (total > 0) it.cents * 100.0 / total else 0.0 } }
    var selected by remember(report.window) { mutableIntStateOf(stats.indexOf(stats.maxBy { it.cents })) }
    val labels = remember(short) { short.map { AxisLabel(it) } }
    val shareLabels = shares.map { percentLabel(it) }
    val countLabels = stats.map { oneDecimal(it.averageCount) }
    ChartCard(
        title = stringResource(R.string.habits_weekdays_title),
        subtitle = stringResource(R.string.habits_weekdays_subtitle),
        tag = StatsTags.card("weekdays"),
    ) {
        val day = stats[selected]
        ChartReadout(
            title = full[selected],
            rows = listOf(
                ReadoutRow(SeriesKey.Box(accent), stringResource(R.string.series_share_of_spending), shareLabels[selected]),
                ReadoutRow(null, stringResource(R.string.habits_top_total), Money.format(day.cents)),
                ReadoutRow(null, stringResource(R.string.series_per_day), countLabels[selected]),
            ),
        )
        ColumnChart(
            series = listOf(ColumnSeries("share", accent, shares)),
            columnCount = 7,
            xLabels = labels,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.habits_weekdays_description),
            height = 150.dp,
            yFormatter = { "${axisNumber(it)} %" },
            valueLabels = shareLabels,
        )
        Text(
            stringResource(R.string.habits_weekday_count_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        ColumnChart(
            series = listOf(ColumnSeries("count", accent, stats.map { it.averageCount })),
            columnCount = 7,
            xLabels = labels,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.habits_weekday_count_description),
            height = 110.dp,
            valueLabels = countLabels,
        )
    }
}

/** "18 %" (whole percent, half-even like the rest of Statistika). */
private fun percentLabel(percent: Double): String = Percent.format(Math.rint(percent).toInt())

// ---- 9. day of month ----

@Composable
private fun DayOfMonthCard(report: HabitsReport) {
    val stats = report.daysOfMonth
    val accent = MaterialTheme.colorScheme.primary
    var selected by remember(report.window) { mutableIntStateOf(stats.indexOf(stats.maxBy { it.averageCents })) }
    val labels = remember { List(31) { i -> if (i == 0 || (i + 1) % 5 == 0) AxisLabel("${i + 1}.") else null } }
    val series = remember(stats, accent) { listOf(ColumnSeries("average", accent, stats.map { it.averageCents.euros() })) }
    ChartCard(
        title = stringResource(R.string.habits_day_of_month_title),
        subtitle = stringResource(R.string.habits_day_of_month_subtitle),
        tag = StatsTags.card("days_of_month"),
    ) {
        val day = stats[selected]
        ChartReadout(
            title = stringResource(R.string.day_of_month, day.day),
            rows = listOf(
                ReadoutRow(SeriesKey.Box(accent), stringResource(R.string.series_average_day), Money.format(day.averageCents)),
                ReadoutRow(null, stringResource(R.string.habits_top_total), Money.format(day.cents)),
            ),
        )
        ColumnChart(
            series = series,
            columnCount = 31,
            xLabels = labels,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.habits_day_of_month_description),
            height = 170.dp,
        )
    }
}

// ---- 10. calendar ----

@Composable
private fun CalendarCard(calendar: SpendingCalendar) {
    val chart = AppTheme.colors.chart
    var selected by remember(calendar) { mutableStateOf(calendar.last) }
    val short = stringArrayResource(R.array.months_short)
    val days = stringArrayResource(R.array.weekdays_short)
    val legend = heatmapLegendLabels(calendar.thresholds)
    ChartCard(
        title = stringResource(R.string.habits_calendar_title),
        subtitle = stringResource(R.string.habits_calendar_subtitle),
        tag = StatsTags.card("calendar"),
    ) {
        val cents = calendar.cents(selected)
        ChartReadout(
            title = longDate(selected),
            rows = listOf(ReadoutRow(null, "", if (cents > 0) Money.format(cents) else stringResource(R.string.heatmap_none))),
        )
        CalendarHeatmap(
            calendar = calendar,
            ramp = chart.heatmap,
            empty = chart.heatmapEmpty,
            selected = selected,
            onSelect = { selected = it },
            monthNames = short.toList(),
            dayNames = listOf(days[0], days[2], days[4]),
            description = stringResource(R.string.habits_calendar_description),
        )
        HeatmapLegend(chart.heatmap, chart.heatmapEmpty, legend)
    }
}

/** "bez troška", "do 20 €", "20–40 €", …, "više od 80 €" for the empty cell and levels 1..5. */
@Composable
private fun heatmapLegendLabels(thresholds: List<Long>): List<String> {
    if (thresholds.size != SpendingCalendar.LEVELS - 1) return listOf(stringResource(R.string.heatmap_none))
    val euros = thresholds.map { wholeEuros(it) }
    val plain = thresholds.map { axisNumber(it / 100.0) }
    return buildList {
        add(stringResource(R.string.heatmap_none))
        add(stringResource(R.string.heatmap_up_to, euros[0]))
        for (i in 1 until thresholds.size) add(stringResource(R.string.bucket_range_euro, plain[i - 1], plain[i]))
        add(stringResource(R.string.heatmap_above, euros.last()))
    }
}

// ---- 11. histogram ----

@Composable
private fun HistogramCard(buckets: List<AmountBucket>, medianCents: Long?) {
    val accent = MaterialTheme.colorScheme.primary
    // Two lines ("100–" over "250") so seven buckets fit a phone's width.
    val labels = buckets.map { b ->
        val from = axisNumber(b.fromCents / 100.0)
        val to = b.toCents
        if (to == null) AxisLabel(stringResource(R.string.bucket_open, from)) else AxisLabel("$from–", axisNumber(to / 100.0))
    }
    val fullLabels = buckets.map { b ->
        val from = axisNumber(b.fromCents / 100.0)
        val to = b.toCents
        if (to == null) stringResource(R.string.bucket_open_euro, from) else stringResource(R.string.bucket_range_euro, from, axisNumber(to / 100.0))
    }
    val medianIndex = medianCents?.let { m -> buckets.indexOfFirst { m in it } }?.takeIf { it >= 0 }
    var selected by remember(buckets) { mutableIntStateOf(medianIndex ?: 0) }
    val marker = if (medianCents != null && medianIndex != null) {
        val b = buckets[medianIndex]
        val fraction = b.toCents?.let { ((medianCents - b.fromCents).toFloat() / (it - b.fromCents)).coerceIn(0f, 1f) } ?: 0.5f
        ColumnMarker(medianIndex + fraction, stringResource(R.string.habits_median_marker, Money.format(medianCents)))
    } else {
        null
    }
    val series = remember(buckets, accent) { listOf(ColumnSeries("count", accent, buckets.map { it.count.toDouble() })) }
    ChartCard(
        title = stringResource(R.string.habits_histogram_title),
        subtitle = stringResource(R.string.habits_histogram_subtitle),
        tag = StatsTags.card("histogram"),
    ) {
        val b = buckets[selected]
        ChartReadout(
            title = fullLabels[selected],
            rows = listOf(
                ReadoutRow(SeriesKey.Box(accent), "", expenseCountLabel(b.count)),
                ReadoutRow(null, stringResource(R.string.habits_top_total), Money.format(b.cents)),
            ),
        )
        ColumnChart(
            series = series,
            columnCount = buckets.size,
            xLabels = labels,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.habits_histogram_description),
            height = 180.dp,
            valueLabels = buckets.map { it.count.toString() },
            marker = marker,
        )
    }
}

// ---- 12. category share per quarter ----

@Composable
private fun QuartersCard(stack: StackedSeries<Quarter>) {
    val quarters = stack.columns.map { it.period }
    val ordered = remember(stack) { CategoryVisuals.chartOrder(stack.categories) }
    val colors = ordered.map { CategoryVisuals.tint(it) }
    val series = remember(stack, colors) {
        ordered.mapIndexed { k, category ->
            val i = stack.categories.indexOf(category)
            ColumnSeries(category, colors[k], stack.columns.map { it.cents[i].euros() })
        }
    }
    val labels = quarters.mapIndexed { i, q ->
        val step = if (quarters.size > 8) 2 else 1
        if (i % step != 0) {
            null
        } else {
            AxisLabel(stringResource(R.string.quarter_short, q.quarter), if (i == 0 || q.quarter <= step) stringResource(R.string.year_label, q.year) else null)
        }
    }
    var selected by remember(stack) { mutableIntStateOf(quarters.lastIndex) }
    ChartCard(
        title = stringResource(R.string.habits_quarters_title),
        subtitle = stringResource(R.string.habits_quarters_subtitle),
        tag = StatsTags.card("quarters"),
    ) {
        Legend(ordered.mapIndexed { k, c -> LegendEntry(SeriesKey.Box(colors[k]), c, CategoryVisuals.icon(c)) })
        val column = stack.columns[selected]
        val q = quarters[selected]
        ChartReadout(
            title = stringResource(R.string.quarter_label, q.quarter, q.year),
            // Only the categories with spending in that quarter, in the stack's order.
            rows = ordered.mapIndexedNotNull { k, c ->
                val cents = column.cents[stack.categories.indexOf(c)]
                if (cents == 0L) null else ReadoutRow(SeriesKey.Box(colors[k]), c, Percent.format(Percent.of(cents, column.totalCents) ?: 0))
            },
        )
        ColumnChart(
            series = series,
            columnCount = quarters.size,
            xLabels = labels,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.habits_quarters_description),
            height = 200.dp,
            percent = true,
        )
    }
}

// ---- 13. most frequent descriptions ----

@Composable
private fun TopDescriptionsCard(rows: List<DescriptionStat>) {
    ChartCard(
        title = stringResource(R.string.habits_top_title),
        subtitle = stringResource(R.string.habits_top_subtitle),
        tag = StatsTags.card("top"),
    ) {
        if (rows.isEmpty()) {
            ChartNote(stringResource(R.string.habits_top_empty))
            return@ChartCard
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            HeaderCell(stringResource(R.string.habits_top_count), Modifier.width(38.dp))
            HeaderCell(stringResource(R.string.habits_top_total), Modifier.width(96.dp))
        }
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CategoryBadge(row.category, size = 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.habits_top_detail, row.category, Money.format(row.averageCents)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(row.count.toString(), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End, modifier = Modifier.width(38.dp))
                Text(Money.format(row.totalCents), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End, modifier = Modifier.width(96.dp))
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = modifier,
    )
}


