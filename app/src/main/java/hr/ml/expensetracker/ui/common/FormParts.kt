package hr.ml.expensetracker.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.SplitType

/** Test tags shared by the expense form and the template form. */
object FormTags {
    /** Every chip of the expanded category grid. */
    const val CATEGORY_CHIP = "expense_category_chip"

    /** The single selected chip of the collapsed category picker; tapping it expands the grid. */
    const val CATEGORY_SELECTED = "expense_category_selected"
}

/**
 * The category picker. Expanded (or with nothing selected) it is the grid of every category;
 * collapsed it is only the selected chip with a chevron, which expands the grid again. The forms
 * collapse it after a selection so the fields below and the save button stay on screen.
 */
@Composable
fun CategoryPicker(
    categories: List<String>,
    selected: String?,
    expanded: Boolean,
    onSelect: (String) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected != null && !expanded) {
        val changeLabel = stringResource(R.string.category_change)
        CategoryChip(
            category = selected,
            selected = true,
            onClick = onExpand,
            trailing = true,
            modifier = modifier
                .semantics { onClick(label = changeLabel) { onExpand(); true } }
                .testTag(FormTags.CATEGORY_SELECTED),
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier,
        ) {
            categories.forEach { category ->
                CategoryChip(
                    category = category,
                    selected = category == selected,
                    onClick = { onSelect(category) },
                    modifier = Modifier.testTag(FormTags.CATEGORY_CHIP),
                )
            }
        }
    }
}

/** A filled tonal pill with the category icon; selected, it takes the category colour. */
@Composable
fun CategoryChip(
    category: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: Boolean = false,
) {
    val tint = CategoryVisuals.tint(category)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(category) },
        leadingIcon = {
            Icon(
                painterResource(CategoryVisuals.icon(category)),
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        trailingIcon = if (trailing) {
            {
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = CategoryVisuals.container(category),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = tint,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.height(36.dp),
    )
}

@Composable
fun SectionLabel(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** "Platio/la": Lovel | Marina. */
@Composable
fun PaidByField(paidBy: Person, onChange: (Person) -> Unit) {
    SectionLabel(R.string.paid_by_label)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Person.entries.forEachIndexed { index, person ->
            SegmentedButton(
                selected = paidBy == person,
                onClick = { onChange(person) },
                shape = SegmentedButtonDefaults.itemShape(index, Person.entries.size),
            ) { Text(stringResource(person.label)) }
        }
    }
}

/** "Raspodjela": Podjela | Zajam, with a line saying who owes what. */
@Composable
fun SplitField(split: SplitType, paidBy: Person, onChange: (SplitType) -> Unit) {
    SectionLabel(R.string.split_label)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SplitType.entries.forEachIndexed { index, s ->
                SegmentedButton(
                    selected = split == s,
                    onClick = { onChange(s) },
                    shape = SegmentedButtonDefaults.itemShape(index, SplitType.entries.size),
                ) { Text(stringResource(s.label)) }
            }
        }
        Text(
            text = when (split) {
                SplitType.HALF -> stringResource(R.string.split_half_hint)
                SplitType.FULL_FOR_OTHER -> stringResource(R.string.split_full_hint, stringResource(paidBy.other.label))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
