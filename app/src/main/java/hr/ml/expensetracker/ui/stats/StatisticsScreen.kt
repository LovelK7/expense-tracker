package hr.ml.expensetracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.AnalyticsRange
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Percent
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.ui.common.CategoryBadge
import hr.ml.expensetracker.ui.common.CategoryVisuals
import hr.ml.expensetracker.ui.common.expenseCountLabel
import hr.ml.expensetracker.ui.common.label
import hr.ml.expensetracker.ui.common.longDate
import hr.ml.expensetracker.ui.common.monthLabel
import hr.ml.expensetracker.ui.common.paidByLabel
import hr.ml.expensetracker.ui.theme.AppTheme
import hr.ml.expensetracker.ui.theme.TroskoviTheme
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

object StatsTags {
    const val PREVIOUS = "stats_previous"
    const val NEXT = "stats_next"
    const val PERIOD_LABEL = "stats_period_label"
    const val TOTAL = "stats_total"
    const val PERSON_SUMMARY = "stats_person_summary"
    const val TRANSFERS = "stats_transfers"
    const val COMPARISON = "stats_comparison"

    fun category(name: String) = "stats_category:$name"

    fun trend(category: String) = "stats_trend:$category"

    fun year(year: Int) = "stats_year:$year"

    /** The person selector's button; null is "Oboje". */
    fun person(person: Person?) = "stats_person:${person?.name ?: "BOTH"}"

    const val TILES = "stats_tiles"
    const val TRENDS_LIST = "stats_trends_list"
    const val HABITS_LIST = "stats_habits_list"

    fun tab(tab: StatsTab) = "stats_tab:${tab.name}"

    fun range(range: AnalyticsRange) = "stats_range:${range.name}"

    /** A Trendovi/Navike chart card, e.g. "monthly", "calendar". */
    fun card(name: String) = "stats_card:$name"

    /** A stat tile's value, by its label. */
    fun tile(label: String) = "stats_tile:$label"
}

/**
 * Statistika: inner tabs Brojke (the Stage 8 figures for a month, a year or everything), Trendovi
 * (time series) and Navike (spending habits). The person selection applies to all three.
 */
@Composable
fun StatisticsScreen(modifier: Modifier = Modifier, viewModel: StatisticsViewModel = koinViewModel()) {
    val tab by viewModel.selectedTab.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        StatsTabRow(tab, viewModel::setTab)
        when (tab) {
            StatsTab.NUMBERS -> {
                val state by viewModel.state.collectAsStateWithLifecycle()
                StatisticsContent(
                    state = state,
                    onModeChange = viewModel::setMode,
                    onPersonChange = viewModel::setPerson,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onToggleCategory = viewModel::toggleCategory,
                )
            }
            StatsTab.TRENDS -> {
                val state by viewModel.trends.collectAsStateWithLifecycle()
                val selection by viewModel.analyticsSelection.collectAsStateWithLifecycle()
                TrendsContent(state, selection, viewModel::setRange, viewModel::setPerson)
            }
            StatsTab.HABITS -> {
                val state by viewModel.habits.collectAsStateWithLifecycle()
                val selection by viewModel.analyticsSelection.collectAsStateWithLifecycle()
                HabitsContent(state, selection, viewModel::setRange, viewModel::setPerson)
            }
        }
    }
}

@Composable
private fun StatsTabRow(selected: StatsTab, onSelect: (StatsTab) -> Unit) {
    SecondaryTabRow(selectedTabIndex = selected.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
        StatsTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(stringResource(tab.label)) },
                modifier = Modifier.testTag(StatsTags.tab(tab)),
            )
        }
    }
}

private val StatsTab.label: Int
    get() = when (this) {
        StatsTab.NUMBERS -> R.string.stats_tab_numbers
        StatsTab.TRENDS -> R.string.stats_tab_trends
        StatsTab.HABITS -> R.string.stats_tab_habits
    }

@Composable
private fun StatisticsContent(
    state: StatsUiState,
    onModeChange: (StatsMode) -> Unit,
    onPersonChange: (Person?) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "period") { PeriodSelector(state, onModeChange, onPersonChange, onPrevious, onNext) }
        when {
            state.loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error -> item(key = "error") {
                Text(
                    text = stringResource(R.string.home_load_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            state.empty -> {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
                if (state.transferCents > 0) {
                    item(key = "transfers") { TransfersLine(state.transferCents, TextAlign.Center) }
                }
            }
            else -> statistics(state, onToggleCategory)
        }
    }
}

private fun LazyListScope.statistics(state: StatsUiState, onToggleCategory: (String) -> Unit) {
    val transfers = state.transferCents > 0
    item(key = "summary") { SummaryCard(state, Modifier.padding(top = 8.dp, bottom = if (transfers) 0.dp else 8.dp)) }
    if (transfers) item(key = "transfers") { TransfersLine(state.transferCents) }

    item(key = "categories_header") { SectionHeader(stringResource(R.string.stats_by_category)) }
    state.categories.forEach { row ->
        item(key = "category:${row.category}") {
            CategoryItem(row, state.totalCents, onClick = { onToggleCategory(row.category) })
        }
        if (row.expanded) {
            row.trend?.let { trend -> item(key = "trend:${row.category}") { CategoryTrendCard(row, trend) } }
            items(row.expenses, key = { "expense:${it.id}" }) { ExpenseItem(it, showPayer = state.person == null) }
        }
    }

    if (state.mode == StatsMode.MONTH) {
        if (state.people.isEmpty()) return
        item(key = "people_header") { SectionHeader(stringResource(R.string.stats_share_by_person)) }
        items(state.people, key = { "share:${it.person}" }) { person ->
            BarRow(
                title = stringResource(person.person.label),
                amount = Money.format(person.shareCents),
                detail = percentText(person.shareFraction * 100.0),
                fraction = person.shareFraction,
                color = personColor(person.person),
            )
        }
    } else {
        if (state.years.isNotEmpty()) {
            item(key = "years_header") { SectionHeader(stringResource(R.string.stats_by_year)) }
            items(state.years, key = { "year:${it.year}" }) { YearItem(it) }
        }
        item(key = "months_header") { SectionHeader(stringResource(R.string.stats_by_month)) }
        items(state.months, key = { "month:${it.month}" }) { month ->
            BarRow(
                title = monthLabel(month.month),
                amount = Money.format(month.cents),
                detail = null,
                fraction = month.barFraction,
            )
        }
        if (state.olderMonthsHidden) {
            item(key = "older_hidden") {
                Text(
                    text = stringResource(R.string.stats_older_hidden),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    state: StatsUiState,
    onModeChange: (StatsMode) -> Unit,
    onPersonChange: (Person?) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StatsMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, StatsMode.entries.size),
                    label = { Text(stringResource(mode.label)) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            val arrows = state.mode != StatsMode.ALL
            if (arrows) {
                IconButton(onClick = onPrevious, enabled = state.canGoPrevious, modifier = Modifier.testTag(StatsTags.PREVIOUS)) {
                    Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.stats_previous))
                }
            }
            Text(
                text = periodLabel(state),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag(StatsTags.PERIOD_LABEL),
            )
            if (arrows) {
                IconButton(onClick = onNext, enabled = state.canGoNext, modifier = Modifier.testTag(StatsTags.NEXT)) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.stats_next))
                }
            }
        }
        PersonSelector(state.person, onPersonChange)
    }
}

private val PERSON_CHOICES: List<Person?> = listOf(null) + Person.entries

/** "Oboje | Lovel | Marina": whose spending the statistics cover (all three inner tabs). */
@Composable
internal fun PersonSelector(selected: Person?, onChange: (Person?) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        PERSON_CHOICES.forEachIndexed { index, person ->
            SegmentedButton(
                selected = selected == person,
                onClick = { onChange(person) },
                shape = SegmentedButtonDefaults.itemShape(index, PERSON_CHOICES.size),
                label = { Text(stringResource(person?.label ?: R.string.stats_person_both)) },
                modifier = Modifier.testTag(StatsTags.person(person)),
            )
        }
    }
}

@Composable
private fun periodLabel(state: StatsUiState): String = when (state.mode) {
    StatsMode.MONTH -> monthLabel(state.selection.month)
    StatsMode.YEAR -> stringResource(R.string.year_label, state.selection.year)
    StatsMode.ALL -> {
        val from = state.allFrom
        val to = state.allTo
        when {
            from == null || to == null -> stringResource(R.string.stats_mode_all)
            from == to -> monthLabel(from)
            else -> stringResource(R.string.stats_range, monthLabel(from), monthLabel(to))
        }
    }
}

private val StatsMode.label: Int
    get() = when (this) {
        StatsMode.MONTH -> R.string.stats_mode_month
        StatsMode.YEAR -> R.string.stats_mode_year
        StatsMode.ALL -> R.string.stats_mode_all
    }

private val PERCENT_FORMAT: NumberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("hr")).apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}

/** "34,5 %". */
@Composable
private fun percentText(percent: Double): String = stringResource(R.string.stats_percent, PERCENT_FORMAT.format(percent))

@Composable
private fun personColor(person: Person): Color = when (person) {
    Person.LOVEL -> MaterialTheme.colorScheme.primary
    Person.MARINA -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SummaryCard(state: StatsUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        val person = state.person
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (person == null) {
                    stringResource(R.string.stats_total)
                } else {
                    stringResource(R.string.row_meta, stringResource(R.string.stats_paid), stringResource(person.label))
                },
                style = MaterialTheme.typography.labelLarge,
                color = onContainer,
            )
            Text(
                text = Money.format(state.totalCents),
                style = MaterialTheme.typography.displaySmall,
                color = onContainer,
                modifier = Modifier.testTag(StatsTags.TOTAL),
            )
            if (person != null) {
                val count = expenseCountLabel(state.expenseCount)
                val percent = state.personPercent
                Text(
                    text = if (percent == null) count else stringResource(R.string.stats_person_summary, count, Percent.format(percent)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                    modifier = Modifier.testTag(StatsTags.PERSON_SUMMARY),
                )
            }
            state.comparisons.forEach { ComparisonLine(it) }
            if (state.people.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SummaryLine("", stringResource(R.string.stats_share), stringResource(R.string.stats_paid), header = true)
                state.people.forEach { p ->
                    SummaryLine(stringResource(p.person.label), Money.format(p.shareCents), Money.format(p.paidCents))
                }
            }
        }
    }
}

/** "+12 % u odnosu na rujan 2026.", "−5 % u odnosu na 2025. (do danas)". */
@Composable
private fun ComparisonLine(row: ComparisonRow) {
    val reference = when (val ref = row.reference) {
        is ComparisonReference.Month -> monthLabel(ref.month)
        is ComparisonReference.Year -> {
            val year = stringResource(R.string.year_label, ref.year)
            if (ref.toDate) stringResource(R.string.stats_year_to_date, year) else year
        }
    }
    Text(
        text = stringResource(R.string.stats_compared_to, Percent.formatChange(row.changePercent), reference),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.testTag(StatsTags.COMPARISON),
    )
}

/** "Prijenosi (isključeni): 1.000,00 €": the period's transfers, which are not spending. */
@Composable
private fun TransfersLine(cents: Long, align: TextAlign = TextAlign.Start) {
    Text(
        text = stringResource(R.string.stats_transfers_excluded, Money.format(cents)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).testTag(StatsTags.TRANSFERS),
    )
}

@Composable
private fun SummaryLine(name: String, share: String, paid: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge
    val color = MaterialTheme.colorScheme.onPrimaryContainer
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = style, color = color, fontWeight = if (header) null else FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(share, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.width(110.dp))
        Text(paid, style = style, color = color, textAlign = TextAlign.End, modifier = Modifier.width(110.dp))
    }
}

@Composable
private fun CategoryItem(row: CategoryRow, totalCents: Long, onClick: () -> Unit) {
    val state = stringResource(if (row.expanded) R.string.stats_collapse else R.string.stats_expand)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { stateDescription = state }
            .testTag(StatsTags.category(row.category))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(row.category, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BarContent(
                title = row.category,
                amount = Money.format(row.cents),
                detail = if (totalCents > 0) percentText(row.percent) else null,
                fraction = row.barFraction,
                color = CategoryVisuals.tint(row.category),
                bold = row.expanded,
            )
        }
    }
}

private val TREND_HEIGHT = 64.dp

/** An expanded category's last 12 months as small columns, its monthly average and its share of the period. */
@Composable
private fun CategoryTrendCard(row: CategoryRow, trend: CategoryTrendUi) {
    val color = CategoryVisuals.tint(row.category)
    val shortMonths = stringArrayResource(R.array.months_short)
    val description = stringResource(R.string.stats_trend_description)
    Column(
        Modifier
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(AppTheme.colors.card)
            .testTag(StatsTags.trend(row.category))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(TREND_HEIGHT).semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            trend.bars.forEach { bar ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    // Every month gets a thin baseline; a month with spending at least a sliver of a column.
                    Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                    if (bar.cents > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(bar.fraction.coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (bar.inPeriod) color else color.copy(alpha = 0.4f)),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            trend.bars.forEach { bar ->
                Text(
                    text = shortMonths[bar.month.monthValue - 1],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bar.inPeriod) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.stats_monthly_average, Money.format(trend.monthlyAverageCents)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.stats_period_share, percentText(row.percent)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "2026." with its total and bar, and the year's monthly average below. */
@Composable
private fun YearItem(row: YearRow) {
    Column(Modifier.fillMaxWidth().testTag(StatsTags.year(row.year)).padding(horizontal = 8.dp, vertical = 8.dp)) {
        BarContent(
            title = stringResource(R.string.year_label, row.year),
            amount = Money.format(row.cents),
            detail = null,
            fraction = row.barFraction,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.stats_monthly_average, Money.format(row.monthlyAverageCents)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BarRow(title: String, amount: String, detail: String?, fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        BarContent(title, amount, detail, fraction, color)
    }
}

@Composable
private fun BarContent(title: String, amount: String, detail: String?, fraction: Float, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(amount, style = MaterialTheme.typography.titleMedium)
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(64.dp),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Bar(fraction, color)
}

/** A proportional horizontal bar: [fraction] (0..1) of the width filled with [color]. */
@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}

/** One expense of an expanded category: date, payer (both people only) and split. */
@Composable
private fun ExpenseItem(expense: Expense, showPayer: Boolean) {
    ListItem(
        leadingContent = { CategoryBadge(expense.category, size = 32.dp) },
        headlineContent = { Text(expense.category) },
        supportingContent = {
            Column {
                if (expense.description.isNotBlank()) {
                    Text(expense.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val split = stringResource(expense.split.label)
                Text(
                    text = if (showPayer) {
                        stringResource(R.string.row_meta_three, longDate(expense.date), stringResource(expense.paidBy.paidByLabel), split)
                    } else {
                        stringResource(R.string.row_meta, longDate(expense.date), split)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expense.split == SplitType.FULL_FOR_OTHER) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        trailingContent = { Text(Money.format(expense.amountCents), style = MaterialTheme.typography.titleSmall) },
        colors = ListItemDefaults.colors(containerColor = AppTheme.colors.card),
        modifier = Modifier.padding(start = 16.dp).clip(MaterialTheme.shapes.medium),
    )
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun StatisticsMonthPreview() {
    val at = Instant.parse("2026-10-24T08:00:00Z")
    val konzum = Expense("1", LocalDate.of(2026, 10, 20), "Hrana", "Konzum", 4_250, Person.LOVEL, SplitType.HALF, at, at, Person.LOVEL)
    TroskoviTheme {
        StatisticsContent(
            state = StatsUiState(
                loading = false,
                selection = StatsSelection(StatsMode.MONTH, YearMonth.of(2026, 10)),
                canGoPrevious = true,
                expenseCount = 3,
                totalCents = 18_250,
                transferCents = 100_000,
                comparisons = listOf(
                    ComparisonRow(12, ComparisonReference.Month(YearMonth.of(2026, 9))),
                    ComparisonRow(-5, ComparisonReference.Month(YearMonth.of(2025, 10))),
                ),
                people = listOf(
                    PersonRow(Person.LOVEL, 9_125, 4_250, 0.5f),
                    PersonRow(Person.MARINA, 9_125, 14_000, 0.5f),
                ),
                categories = listOf(
                    CategoryRow("Režije", 14_000, 76.7, 1f),
                    CategoryRow(
                        category = "Hrana",
                        cents = 4_250,
                        percent = 23.3,
                        barFraction = 0.3f,
                        expanded = true,
                        expenses = listOf(konzum),
                        trend = CategoryTrendUi(
                            bars = (0L until 12L).map { i ->
                                val cents = (i * 397 % 1_000) * 10
                                TrendBar(YearMonth.of(2025, 11).plusMonths(i), cents, cents / 9_990f, inPeriod = i == 11L)
                            },
                            monthlyAverageCents = 4_312,
                        ),
                    ),
                ),
            ),
            onModeChange = {},
            onPersonChange = {},
            onPrevious = {},
            onNext = {},
            onToggleCategory = {},
        )
    }
}
