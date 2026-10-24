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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.ui.common.CategoryBadge
import hr.ml.expensetracker.ui.common.CategoryVisuals
import hr.ml.expensetracker.ui.common.label
import hr.ml.expensetracker.ui.common.longDate
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

    fun category(name: String) = "stats_category:$name"
}

@Composable
fun StatisticsScreen(modifier: Modifier = Modifier, viewModel: StatisticsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatisticsContent(
        state = state,
        onModeChange = viewModel::setMode,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onToggleCategory = viewModel::toggleCategory,
        modifier = modifier,
    )
}

@Composable
private fun StatisticsContent(
    state: StatsUiState,
    onModeChange: (StatsMode) -> Unit,
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
        item(key = "period") { PeriodSelector(state, onModeChange, onPrevious, onNext) }
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
            state.empty -> item(key = "empty") {
                Text(
                    text = stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
            else -> statistics(state, onToggleCategory)
        }
    }
}

private fun LazyListScope.statistics(state: StatsUiState, onToggleCategory: (String) -> Unit) {
    item(key = "summary") { SummaryCard(state, Modifier.padding(top = 8.dp, bottom = 8.dp)) }

    item(key = "categories_header") { SectionHeader(stringResource(R.string.stats_by_category)) }
    state.categories.forEach { row ->
        item(key = "category:${row.category}") {
            CategoryItem(row, state.totalCents, onClick = { onToggleCategory(row.category) })
        }
        if (row.expanded) {
            items(row.expenses, key = { "expense:${it.id}" }) { ExpenseItem(it) }
        }
    }

    if (state.mode == StatsMode.MONTH) {
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

/** "listopad 2026." (the nominative month name, as on the legacy sheet). */
@Composable
private fun monthLabel(month: YearMonth): String {
    val names = stringArrayResource(R.array.months_nominative)
    return stringResource(R.string.month_year, names[month.monthValue - 1], month.year)
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stats_total), style = MaterialTheme.typography.labelLarge, color = onContainer)
            Text(
                text = Money.format(state.totalCents),
                style = MaterialTheme.typography.displaySmall,
                color = onContainer,
                modifier = Modifier.testTag(StatsTags.TOTAL),
            )
            Spacer(Modifier.height(4.dp))
            SummaryLine("", stringResource(R.string.stats_share), stringResource(R.string.stats_paid), header = true)
            state.people.forEach { p ->
                SummaryLine(stringResource(p.person.label), Money.format(p.shareCents), Money.format(p.paidCents))
            }
        }
    }
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

@Composable
private fun ExpenseItem(expense: Expense) {
    ListItem(
        leadingContent = { CategoryBadge(expense.category, size = 32.dp) },
        headlineContent = { Text(expense.category) },
        supportingContent = {
            Column {
                if (expense.description.isNotBlank()) {
                    Text(expense.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = stringResource(R.string.row_meta, longDate(expense.date), stringResource(expense.paidBy.paidByLabel)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = { Text(Money.format(expense.amountCents), style = MaterialTheme.typography.titleSmall) },
        colors = ListItemDefaults.colors(containerColor = AppTheme.colors.card),
        modifier = Modifier.padding(start = 16.dp).clip(MaterialTheme.shapes.medium),
    )
}

@Preview(showBackground = true, heightDp = 900)
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
                people = listOf(
                    PersonRow(Person.LOVEL, 9_125, 4_250, 0.5f),
                    PersonRow(Person.MARINA, 9_125, 14_000, 0.5f),
                ),
                categories = listOf(
                    CategoryRow("Režije", 14_000, 76.7, 1f),
                    CategoryRow("Hrana", 4_250, 23.3, 0.3f, expanded = true, expenses = listOf(konzum)),
                ),
            ),
            onModeChange = {},
            onPrevious = {},
            onNext = {},
            onToggleCategory = {},
        )
    }
}
