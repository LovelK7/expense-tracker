package hr.ml.expensetracker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.AnalyticsRange
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.ui.charts.AxisLabel
import hr.ml.expensetracker.ui.charts.axisNumber
import java.time.YearMonth
import kotlin.math.roundToLong

/** Range chips "12 mj | 24 mj | Sve" and the person selector, above the Trendovi/Navike charts. */
@Composable
internal fun AnalyticsHeader(
    selection: AnalyticsSelection,
    onRangeChange: (AnalyticsRange) -> Unit,
    onPersonChange: (Person?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AnalyticsRange.entries.forEachIndexed { index, range ->
                SegmentedButton(
                    selected = selection.range == range,
                    onClick = { onRangeChange(range) },
                    shape = SegmentedButtonDefaults.itemShape(index, AnalyticsRange.entries.size),
                    label = { Text(stringResource(range.label)) },
                    modifier = Modifier.testTag(StatsTags.range(range)),
                )
            }
        }
        PersonSelector(selection.person, onPersonChange)
    }
}

private val AnalyticsRange.label: Int
    get() = when (this) {
        AnalyticsRange.MONTHS_12 -> R.string.range_12
        AnalyticsRange.MONTHS_24 -> R.string.range_24
        AnalyticsRange.ALL -> R.string.range_all
    }

/** Loading, error and empty states of an analytics tab; true when the charts should follow. */
internal fun LazyListScope.analyticsStatus(loading: Boolean, error: Boolean, empty: Boolean): Boolean {
    when {
        loading -> item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        error -> item(key = "error") {
            Text(
                text = stringResource(R.string.home_load_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        empty -> item(key = "empty") {
            Text(
                text = stringResource(R.string.stats_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            )
        }
        else -> return true
    }
    return false
}

/**
 * Month labels for a time axis: every month, every 2nd, 3rd, 6th or 12th (aligned to January)
 * depending on how many there are, the year on a second line in January and on the first label.
 */
internal fun monthAxisLabels(months: List<YearMonth>, shortNames: Array<String>): List<AxisLabel?> {
    val step = when {
        months.size <= 7 -> 1
        months.size <= 14 -> 2
        months.size <= 27 -> 3
        months.size <= 54 -> 6
        else -> 12
    }
    var first = true
    return months.map { m ->
        if ((m.monthValue - 1) % step != 0) {
            null
        } else {
            val withYear = first || m.monthValue == 1
            first = false
            AxisLabel(shortNames[m.monthValue - 1], if (withYear) "${m.year}." else null)
        }
    }
}

/** Cents as euros for the chart's y values. */
internal fun Long.euros(): Double = this / 100.0

/** "1.234 €": whole euros, for direct labels and compact read-outs. */
@Composable
internal fun wholeEuros(cents: Long): String = stringResource(R.string.euro_whole, axisNumber((cents / 100.0).roundToLong().toDouble()))
