package hr.ml.expensetracker.ui.expense

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.ui.common.CategoryVisuals
import hr.ml.expensetracker.ui.common.DateField
import hr.ml.expensetracker.ui.common.label
import hr.ml.expensetracker.ui.theme.TroskoviTheme
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel

object ExpenseFormTags {
    const val AMOUNT = "expense_amount"
    const val SAVE = "expense_save"
    const val CATEGORY_CHIP = "expense_category_chip"
}

/** The "Dodaj" tab: amount (focused, keyboard open), category, save. */
@Composable
fun AddExpenseScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseFormViewModel = koinViewModel(key = "add-expense"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.resetIfPristine() }
    ExpenseForm(
        state = state,
        viewModel = viewModel,
        onSave = { if (viewModel.save()) onSaved() },
        focusAmount = true,
        modifier = modifier,
    )
}

/** Full-screen editor for an existing expense, layered over the tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseScreen(
    expenseId: String,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ExpenseFormViewModel = koinViewModel(key = "edit-expense"),
) {
    LaunchedEffect(expenseId) { viewModel.startEdit(expenseId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close = {
        viewModel.reset()
        onClose()
    }
    LaunchedEffect(state.missing) { if (state.missing) close() }
    BackHandler(onBack = close)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_title)) },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        if (state.editingId == expenseId && state.loaded) {
            ExpenseForm(
                state = state,
                viewModel = viewModel,
                onSave = { if (viewModel.save()) onSaved() },
                focusAmount = false,
                modifier = modifier,
            )
        } else {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun ExpenseForm(
    state: ExpenseFormState,
    viewModel: ExpenseFormViewModel,
    onSave: () -> Unit,
    focusAmount: Boolean,
    modifier: Modifier = Modifier,
) {
    ExpenseFormContent(
        state = state,
        onAmountChange = viewModel::onAmountChange,
        onCategorySelect = viewModel::onCategorySelect,
        onDescriptionChange = viewModel::onDescriptionChange,
        onPaidByChange = viewModel::onPaidByChange,
        onSplitChange = viewModel::onSplitChange,
        onDateChange = viewModel::onDateChange,
        onSave = onSave,
        focusAmount = focusAmount,
        modifier = modifier,
    )
}

@Composable
private fun ExpenseFormContent(
    state: ExpenseFormState,
    onAmountChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPaidByChange: (Person) -> Unit,
    onSplitChange: (SplitType) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onSave: () -> Unit,
    focusAmount: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val amountFocus = remember { FocusRequester() }
    if (focusAmount) {
        LaunchedEffect(Unit) { if (state.amountText.isEmpty()) amountFocus.requestFocus() }
    }
    // The save button stays above the keyboard: type the amount, tap a category, tap save.
    Column(modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.amountText,
                onValueChange = onAmountChange,
                label = { Text(stringResource(R.string.amount_label)) },
                suffix = { Text(stringResource(R.string.euro_suffix)) },
                textStyle = MaterialTheme.typography.headlineMedium,
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus).testTag(ExpenseFormTags.AMOUNT),
            )

            SectionLabel(R.string.category_label)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.categories.forEach { category ->
                    CategoryChip(category, selected = category == state.category, onClick = { onCategorySelect(category) })
                }
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.description_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(R.string.paid_by_label)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Person.entries.forEachIndexed { index, person ->
                    SegmentedButton(
                        selected = state.paidBy == person,
                        onClick = { onPaidByChange(person) },
                        shape = SegmentedButtonDefaults.itemShape(index, Person.entries.size),
                    ) { Text(stringResource(person.label)) }
                }
            }

            SectionLabel(R.string.split_label)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SplitType.entries.forEachIndexed { index, split ->
                    SegmentedButton(
                        selected = state.split == split,
                        onClick = { onSplitChange(split) },
                        shape = SegmentedButtonDefaults.itemShape(index, SplitType.entries.size),
                    ) { Text(stringResource(split.label)) }
                }
            }
            Text(
                text = when (state.split) {
                    SplitType.HALF -> stringResource(R.string.split_half_hint)
                    SplitType.FULL_FOR_OTHER ->
                        stringResource(R.string.split_full_hint, stringResource(state.paidBy.other.label))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel(R.string.date_label)
            DateField(date = state.date, today = state.today, onDateChange = onDateChange)
        }
        val cents = state.amountCents
        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .height(52.dp)
                .testTag(ExpenseFormTags.SAVE),
        ) {
            Text(
                text = if (cents != null && cents > 0) {
                    stringResource(R.string.save_amount, Money.format(cents))
                } else {
                    stringResource(R.string.save)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** A filled tonal pill with the category icon; selected, it takes the category colour. */
@Composable
private fun CategoryChip(category: String, selected: Boolean, onClick: () -> Unit) {
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
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = CategoryVisuals.container(category),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = tint,
        ),
        modifier = Modifier.height(36.dp).testTag(ExpenseFormTags.CATEGORY_CHIP),
    )
}

@Composable
private fun SectionLabel(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ExpenseFormPreview() {
    val today = LocalDate.of(2026, 10, 24)
    TroskoviTheme {
        ExpenseFormContent(
            state = ExpenseFormState(amountText = "12,5", category = "Hrana", date = today, today = today),
            onAmountChange = {},
            onCategorySelect = {},
            onDescriptionChange = {},
            onPaidByChange = {},
            onSplitChange = {},
            onDateChange = {},
            onSave = {},
            focusAmount = false,
        )
    }
}
