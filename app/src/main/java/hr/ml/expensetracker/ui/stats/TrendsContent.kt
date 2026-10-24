package hr.ml.expensetracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.AnalyticsRange
import hr.ml.expensetracker.domain.CategorySeries
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.StackedSeries
import hr.ml.expensetracker.domain.TrendsReport
import hr.ml.expensetracker.ui.charts.AxisLabel
import hr.ml.expensetracker.ui.charts.ChartCard
import hr.ml.expensetracker.ui.charts.ChartReadout
import hr.ml.expensetracker.ui.charts.ColumnChart
import hr.ml.expensetracker.ui.charts.ColumnSeries
import hr.ml.expensetracker.ui.charts.Legend
import hr.ml.expensetracker.ui.charts.LegendEntry
import hr.ml.expensetracker.ui.charts.LineChart
import hr.ml.expensetracker.ui.charts.LineSeries
import hr.ml.expensetracker.ui.charts.ReadoutRow
import hr.ml.expensetracker.ui.charts.SeriesKey
import hr.ml.expensetracker.ui.charts.Sparkline
import hr.ml.expensetracker.ui.common.CategoryBadge
import hr.ml.expensetracker.ui.common.CategoryVisuals
import hr.ml.expensetracker.ui.common.label
import hr.ml.expensetracker.ui.common.monthLabel
import hr.ml.expensetracker.ui.theme.AppTheme
import java.time.YearMonth

/** The Trendovi inner tab: time series over the selected range. */
@Composable
internal fun TrendsContent(
    state: TrendsUiState,
    selection: AnalyticsSelection,
    onRangeChange: (AnalyticsRange) -> Unit,
    onPersonChange: (Person?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(StatsTags.TRENDS_LIST),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { AnalyticsHeader(selection, onRangeChange, onPersonChange) }
        val report = state.report
        if (analyticsStatus(state.loading, state.error, state.empty) && report != null) trends(report)
    }
}

private fun LazyListScope.trends(report: TrendsReport) {
    item(key = "monthly") { MonthlyCard(report) }
    item(key = "categories") { CategoryStackCard(report.categoryStack) }
    item(key = "multiples_header") {
        Column(Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.trends_multiples_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.trends_multiples_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    report.categorySeries.chunked(2).forEachIndexed { row, pair ->
        item(key = "multiples:$row:${pair.joinToString { it.category }}") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { SmallMultiple(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    item(key = "cumulative") { CumulativeCard(report) }
    item(key = "payers") { PayersCard(report) }
}

@Composable
private fun MonthlyCard(report: TrendsReport) {
    val months = report.window.months
    val short = stringArrayResource(R.array.months_short)
    val accent = MaterialTheme.colorScheme.primary
    var selected by remember(report.window) { mutableIntStateOf(months.lastIndex) }
    val spending = stringResource(R.string.series_spending)
    val rolling = stringResource(R.string.series_rolling)
    val lastLabel = wholeEuros(report.monthly.last().cents)
    val series = remember(report, accent, lastLabel) {
        val rollingByMonth = report.rollingAverage.associate { it.month to it.cents.euros() }
        listOf(
            LineSeries(spending, accent, report.monthly.map { it.cents.euros() }, area = true, endLabel = lastLabel),
            LineSeries(rolling, accent, months.map { rollingByMonth[it] }, dashed = true),
        )
    }
    val labels = remember(months) { monthAxisLabels(months, short) }
    ChartCard(
        title = stringResource(R.string.trends_monthly_title),
        subtitle = stringResource(R.string.trends_monthly_subtitle),
        tag = StatsTags.card("monthly"),
    ) {
        Legend(listOf(LegendEntry(SeriesKey.Line(accent), spending), LegendEntry(SeriesKey.Line(accent, dashed = true), rolling)))
        val rollingValue = report.rollingAverage.firstOrNull { it.month == months[selected] }
        ChartReadout(
            title = monthLabel(months[selected]),
            rows = listOfNotNull(
                ReadoutRow(SeriesKey.Line(accent), spending, Money.format(report.monthly[selected].cents)),
                rollingValue?.let { ReadoutRow(SeriesKey.Line(accent, dashed = true), rolling, Money.format(it.cents)) },
            ),
        )
        LineChart(
            series = series,
            pointCount = months.size,
            xLabels = labels,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.trends_monthly_description),
        )
    }
}

@Composable
private fun CategoryStackCard(stack: StackedSeries<YearMonth>) {
    val months = stack.columns.map { it.period }
    val short = stringArrayResource(R.array.months_short)
    val ordered = remember(stack) { CategoryVisuals.chartOrder(stack.categories) }
    val colors = ordered.map { CategoryVisuals.tint(it) }
    val series = remember(stack, colors) {
        ordered.mapIndexed { k, category ->
            val i = stack.categories.indexOf(category)
            ColumnSeries(category, colors[k], stack.columns.map { it.cents[i].euros() })
        }
    }
    var selected by remember(stack) { mutableIntStateOf(months.lastIndex) }
    val labels = remember(months) { monthAxisLabels(months, short) }
    ChartCard(
        title = stringResource(R.string.trends_categories_title),
        subtitle = stringResource(R.string.trends_categories_subtitle),
        tag = StatsTags.card("categories"),
    ) {
        Legend(ordered.mapIndexed { k, c -> LegendEntry(SeriesKey.Box(colors[k]), c, CategoryVisuals.icon(c)) })
        val column = stack.columns[selected]
        ChartReadout(
            title = "${monthLabel(months[selected])} · ${wholeEuros(column.totalCents)}",
            // Only the categories with spending that month, in the stack's order.
            rows = ordered.mapIndexedNotNull { k, c ->
                val cents = column.cents[stack.categories.indexOf(c)]
                if (cents == 0L) null else ReadoutRow(SeriesKey.Box(colors[k]), c, wholeEuros(cents))
            },
        )
        ColumnChart(
            series = series,
            columnCount = months.size,
            xLabels = labels,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.trends_categories_description),
            height = 200.dp,
        )
    }
}

@Composable
private fun SmallMultiple(series: CategorySeries, modifier: Modifier = Modifier) {
    val color = CategoryVisuals.tint(series.category)
    var selected by remember(series) { mutableIntStateOf(series.months.lastIndex) }
    val values = remember(series) { series.months.map { it.cents.euros() } }
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(AppTheme.colors.card)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryBadge(series.category, size = 28.dp)
            Text(
                series.category,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column {
            Text(Money.format(series.totalCents), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.stats_monthly_average, Money.format(series.monthlyAverageCents)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Sparkline(
            values = values,
            color = color,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.trends_multiple_description, series.category),
        )
        val point = series.months[selected]
        Text(
            text = "${monthLabel(point.month)} · ${wholeEuros(point.cents)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CumulativeCard(report: TrendsReport) {
    val short = stringArrayResource(R.array.months_short)
    val years = report.cumulativeByYear
    val palette = AppTheme.colors.chart.years
    val colors = years.map { palette[Math.floorMod(2026 - it.year, palette.size)] }
    val yearLabels = years.map { stringResource(R.string.year_label, it.year) }
    val series = remember(years, colors, yearLabels) {
        years.mapIndexed { i, y ->
            LineSeries(yearLabels[i], colors[i], List(12) { m -> y.cumulativeCents.getOrNull(m)?.euros() }, endLabel = yearLabels[i])
        }
    }
    val labels = remember(short) { List(12) { m -> if (m % 2 == 0) AxisLabel(short[m]) else null } }
    var selected by remember(report.window) { mutableIntStateOf(report.window.last.monthValue - 1) }
    ChartCard(
        title = stringResource(R.string.trends_cumulative_title),
        subtitle = stringResource(R.string.trends_cumulative_subtitle),
        tag = StatsTags.card("cumulative"),
    ) {
        if (years.size >= 2) Legend(years.indices.map { LegendEntry(SeriesKey.Line(colors[it]), yearLabels[it]) })
        val months = stringArrayResource(R.array.months_nominative)
        ChartReadout(
            title = months[selected],
            rows = years.indices.mapNotNull { i ->
                years[i].cumulativeCents.getOrNull(selected)?.let { ReadoutRow(SeriesKey.Line(colors[i]), yearLabels[i], wholeEuros(it)) }
            },
        )
        LineChart(
            series = series,
            pointCount = 12,
            xLabels = labels,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.trends_cumulative_description),
            height = 220.dp,
        )
    }
}

@Composable
private fun PayersCard(report: TrendsReport) {
    val months = report.window.months
    val short = stringArrayResource(R.array.months_short)
    val chart = AppTheme.colors.chart
    val person = report.person
    val paid = stringResource(R.string.series_paid)
    val share = stringResource(R.string.series_share)
    val names = Person.entries.associateWith { stringResource(it.label) }
    fun colorOf(p: Person) = if (p == Person.LOVEL) chart.lovel else chart.marina

    data class Line(val key: SeriesKey.Line, val label: String, val cents: List<Long>, val endLabel: String?)
    val lines = if (person == null) {
        Person.entries.map { p -> Line(SeriesKey.Line(colorOf(p)), names.getValue(p), report.payers.paid.getValue(p).map { it.cents }, names.getValue(p)) }
    } else {
        listOf(
            Line(SeriesKey.Line(colorOf(person)), paid, report.payers.paid.getValue(person).map { it.cents }, paid),
            Line(SeriesKey.Line(colorOf(person), dashed = true), share, report.payers.share.getValue(person).map { it.cents }, share),
        )
    }
    val series = remember(report, lines.map { it.key }) {
        lines.map { LineSeries(it.label, it.key.color, it.cents.map { c -> c.euros() }, dashed = it.key.dashed, endLabel = it.endLabel) }
    }
    val labels = remember(months) { monthAxisLabels(months, short) }
    var selected by remember(report.window) { mutableIntStateOf(months.lastIndex) }
    ChartCard(
        title = stringResource(R.string.trends_payers_title),
        subtitle = stringResource(if (person == null) R.string.trends_payers_subtitle else R.string.trends_payers_person_subtitle),
        tag = StatsTags.card("payers"),
    ) {
        Legend(lines.map { LegendEntry(it.key, it.label) })
        ChartReadout(
            title = monthLabel(months[selected]),
            rows = lines.map { ReadoutRow(it.key, it.label, Money.format(it.cents[selected])) },
        )
        LineChart(
            series = series,
            pointCount = months.size,
            xLabels = labels,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.trends_payers_description),
        )
    }
}
