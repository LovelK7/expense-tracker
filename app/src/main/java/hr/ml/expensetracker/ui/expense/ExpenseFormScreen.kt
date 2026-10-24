package hr.ml.expensetracker.ui.expense

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import hr.ml.expensetracker.domain.RecurringTemplate
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.ui.common.CategoryPicker
import hr.ml.expensetracker.ui.common.CategoryVisuals
import hr.ml.expensetracker.ui.common.DateField
import hr.ml.expensetracker.ui.common.FormTags
import hr.ml.expensetracker.ui.common.PaidByField
import hr.ml.expensetracker.ui.common.SectionLabel
import hr.ml.expensetracker.ui.common.SplitField
import hr.ml.expensetracker.ui.theme.TroskoviTheme
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel

object ExpenseFormTags {
    const val AMOUNT = "expense_amount"
    const val SAVE = "expense_save"
    const val CATEGORY_CHIP = FormTags.CATEGORY_CHIP
    const val CATEGORY_SELECTED = FormTags.CATEGORY_SELECTED
    const val TEMPLATE_CHIPS = "expense_template_chips"
    const val EDIT_TEMPLATES = "expense_edit_templates"

    fun templateChip(id: String) = "expense_template_$id"
}

/**
 * The "Dodaj" tab: the "Predlošci" chips (when there are templates), amount (focused, keyboard
 * open), category, save. A template chip prefills the form; [onEditTemplates] opens Predlošci.
 */
@Composable
fun AddExpenseScreen(
    onSaved: () -> Unit,
    onEditTemplates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseFormViewModel = koinViewModel(key = "add-expense"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.resetIfPristine() }
    ExpenseForm(
        state = state,
        viewModel = viewModel,
        onSave = { if (viewModel.save()) onSaved() },
        focusAmount = true,
        modifier = modifier,
        header = if (templates.isEmpty()) {
            null
        } else {
            { TemplateChips(templates, onApply = viewModel::applyTemplate, onEditTemplates = onEditTemplates) }
        },
    )
}

/** "Predlošci" with "Uredi predloške", then one chip per active template; scrolls sideways. */
@Composable
private fun TemplateChips(
    templates: List<RecurringTemplate>,
    onApply: (RecurringTemplate) -> Unit,
    onEditTemplates: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionLabel(R.string.templates_title, Modifier.weight(1f))
            TextButton(onClick = onEditTemplates, modifier = Modifier.testTag(ExpenseFormTags.EDIT_TEMPLATES)) {
                Text(stringResource(R.string.templates_edit))
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag(ExpenseFormTags.TEMPLATE_CHIPS),
        ) {
            items(templates, key = { it.id }) { t ->
                AssistChip(
                    onClick = { onApply(t) },
                    label = { Text(stringResource(R.string.template_chip, t.name, Money.format(t.amountCents)), maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            painterResource(CategoryVisuals.icon(t.category)),
                            contentDescription = null,
                            tint = CategoryVisuals.tint(t.category),
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    shape = CircleShape,
                    border = null,
                    colors = AssistChipDefaults.assistChipColors(containerColor = CategoryVisuals.container(t.category)),
                    modifier = Modifier.height(36.dp).testTag(ExpenseFormTags.templateChip(t.id)),
                )
            }
        }
    }
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
    header: (@Composable () -> Unit)? = null,
) {
    ExpenseFormContent(
        state = state,
        onAmountChange = viewModel::onAmountChange,
        onCategorySelect = viewModel::onCategorySelect,
        onExpandCategories = viewModel::expandCategories,
        onDescriptionChange = viewModel::onDescriptionChange,
        onPaidByChange = viewModel::onPaidByChange,
        onSplitChange = viewModel::onSplitChange,
        onDateChange = viewModel::onDateChange,
        onSave = onSave,
        focusAmount = focusAmount,
        modifier = modifier,
        header = header,
    )
}

@Composable
private fun ExpenseFormContent(
    state: ExpenseFormState,
    onAmountChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onExpandCategories: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPaidByChange: (Person) -> Unit,
    onSplitChange: (SplitType) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onSave: () -> Unit,
    focusAmount: Boolean,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
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
            header?.invoke()
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
            CategoryPicker(
                categories = state.categories,
                selected = state.category,
                expanded = state.categoriesExpanded,
                onSelect = onCategorySelect,
                onExpand = onExpandCategories,
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.description_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            PaidByField(state.paidBy, onPaidByChange)
            SplitField(state.split, state.paidBy, onSplitChange)

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

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ExpenseFormPreview() {
    val today = LocalDate.of(2026, 10, 24)
    TroskoviTheme {
        ExpenseFormContent(
            state = ExpenseFormState(amountText = "12,5", category = "Hrana", date = today, today = today, categoriesExpanded = false),
            onAmountChange = {},
            onCategorySelect = {},
            onExpandCategories = {},
            onDescriptionChange = {},
            onPaidByChange = {},
            onSplitChange = {},
            onDateChange = {},
            onSave = {},
            focusAmount = false,
        )
    }
}
