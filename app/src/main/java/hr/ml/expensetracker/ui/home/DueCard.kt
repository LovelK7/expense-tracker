package hr.ml.expensetracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.DueItem
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.ui.common.CategoryBadge
import hr.ml.expensetracker.ui.common.label
import hr.ml.expensetracker.ui.common.longDate
import hr.ml.expensetracker.ui.common.monthLabel
import hr.ml.expensetracker.ui.common.paidByLabel
import hr.ml.expensetracker.ui.theme.AppTheme

object DueTags {
    fun card(item: DueItem) = "due_card_${item.key}"

    fun add(item: DueItem) = "due_add_${item.key}"

    fun skip(item: DueItem) = "due_skip_${item.key}"
}

/** "Stanarina · listopad 2026." */
@Composable
fun dueHeadline(item: DueItem): String = stringResource(R.string.due_headline, item.template.name, monthLabel(item.period))

/**
 * One due template: "Dospjelo" (or "Zaostalo" for last month), the template and month, amount,
 * payer and split, with "Preskoči" and "Dodaj".
 */
@Composable
fun DueCard(item: DueItem, onAdd: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val t = item.template
    Card(
        modifier = modifier.fillMaxWidth().testTag(DueTags.card(item)),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(t.category)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(if (item.overdue) R.string.due_overdue else R.string.due_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = dueHeadline(item),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.row_meta,
                            stringResource(t.paidBy.paidByLabel),
                            stringResource(t.split.label),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.due_date, longDate(item.date)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(Money.format(t.amountCents), style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSkip, modifier = Modifier.testTag(DueTags.skip(item))) {
                    Text(stringResource(R.string.due_skip))
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onAdd, modifier = Modifier.testTag(DueTags.add(item))) {
                    Text(stringResource(R.string.due_add))
                }
            }
        }
    }
}
