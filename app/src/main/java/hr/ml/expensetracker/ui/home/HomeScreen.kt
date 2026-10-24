package hr.ml.expensetracker.ui.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.DueItem
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.RecurringTemplate
import hr.ml.expensetracker.domain.Settlement
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.ui.common.CategoryBadge
import hr.ml.expensetracker.ui.common.DateField
import hr.ml.expensetracker.ui.common.IconBadge
import hr.ml.expensetracker.ui.common.dayLabel
import hr.ml.expensetracker.ui.common.expenseCountLabel
import hr.ml.expensetracker.ui.common.label
import hr.ml.expensetracker.ui.common.monthLabel
import hr.ml.expensetracker.ui.common.paidByLabel
import hr.ml.expensetracker.ui.theme.AppTheme
import hr.ml.expensetracker.ui.theme.TroskoviTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

object HomeTags {
    const val SETTLE_UP = "home_settle_up"
    const val SETTLE_AMOUNT = "settle_amount"
    const val MONTH_CHIPS = "home_month_chips"
    const val ALL_MONTHS_CHIP = "home_month_all"
    const val SUMMARY = "home_filter_summary"
    const val SEARCH_OPEN = "home_search_open"
    const val SEARCH_FIELD = "home_search_field"
    const val SEARCH_CLEAR = "home_search_clear"
    const val SEARCH_CLOSE = "home_search_close"

    fun monthChip(month: YearMonth) = "home_month_$month"
}

/**
 * Balance, "Podmiri", the month chips and every shown record grouped by day. The search field
 * itself sits in the shell's top bar (`AppShell`) and drives the same [viewModel]. Messages go to
 * the shell's [snackbar] through [scope] (which outlives this tab, so "Poništi" still works after
 * switching tabs).
 */
@Composable
fun HomeScreen(
    person: Person,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    onEditExpense: (id: String) -> Unit,
    onSaveAsTemplate: (expenseId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    dueViewModel: DueViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val due by dueViewModel.due.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<HomeRecord?>(null) }
    val added = stringResource(R.string.due_added)
    val undo = stringResource(R.string.undo)
    val skippedFormat = stringResource(R.string.due_skipped)
    // Resolved here (composable) for the snackbar text of whichever card is skipped.
    val headlines = due.associate { it.key to dueHeadline(it) }

    HomeContent(
        state = state,
        me = person,
        due = due,
        onSettleUp = viewModel::openSettleUp,
        onSelectMonth = viewModel::selectMonth,
        onEdit = { onEditExpense(it.id) },
        onDelete = { pendingDelete = it },
        onSaveAsTemplate = { onSaveAsTemplate(it.id) },
        onPostDue = { item -> if (dueViewModel.post(item)) scope.launch { snackbar.showSnackbar(added) } },
        onSkipDue = { item ->
            dueViewModel.skip(item)
            val message = skippedFormat.format(headlines[item.key].orEmpty())
            scope.launch {
                val result = snackbar.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) dueViewModel.undoSkip(item)
            }
        },
        modifier = modifier,
    )

    state.settleUp?.let { draft ->
        val saved = stringResource(R.string.settlement_saved)
        SettleUpDialog(
            draft = draft,
            today = state.today,
            onAmountChange = viewModel::onSettleAmountChange,
            onNoteChange = viewModel::onSettleNoteChange,
            onDateChange = viewModel::onSettleDateChange,
            onConfirm = { if (viewModel.confirmSettleUp()) scope.launch { snackbar.showSnackbar(saved) } },
            onDismiss = viewModel::dismissSettleUp,
        )
    }

    pendingDelete?.let { record ->
        val deleted = stringResource(
            when (record) {
                is HomeRecord.ExpenseItem -> R.string.expense_deleted
                is HomeRecord.SettlementItem -> R.string.settlement_deleted
            },
        )
        DeleteDialog(
            record = record,
            onConfirm = {
                pendingDelete = null
                viewModel.delete(record)
                scope.launch {
                    val result = snackbar.showSnackbar(deleted, actionLabel = undo, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(record)
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    me: Person,
    due: List<DueItem>,
    onSettleUp: () -> Unit,
    onSelectMonth: (YearMonth?) -> Unit,
    onEdit: (Expense) -> Unit,
    onDelete: (HomeRecord) -> Unit,
    onSaveAsTemplate: (Expense) -> Unit,
    onPostDue: (DueItem) -> Unit,
    onSkipDue: (DueItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error -> item {
                Text(text = stringResource(R.string.home_load_error), color = MaterialTheme.colorScheme.error)
            }
            else -> {
                // The balance is global: the month filter and the search narrow the list below only.
                item(key = "balance") {
                    BalanceCard(state.balanceCents, me, onSettleUp, Modifier.padding(bottom = 8.dp))
                }
                // Templates due this month (or overdue), above the month chips; gone once posted.
                items(due, key = { "due:${it.key}" }, contentType = { "due" }) { item ->
                    DueCard(
                        item = item,
                        onAdd = { onPostDue(item) },
                        onSkip = { onSkipDue(item) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (state.months.isNotEmpty()) {
                    item(key = "months") {
                        MonthChips(state.months, state.selectedMonth, onSelectMonth)
                    }
                }
                state.summary?.let { summary ->
                    item(key = "summary") { FilterSummaryLine(summary) }
                }
                if (state.days.isEmpty() && state.summary == null) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.home_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )
                    }
                }
                state.days.forEach { day ->
                    item(key = "day:${day.date}", contentType = "day") {
                        Text(
                            text = dayLabel(day.date, state.today),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
                        )
                    }
                    // A day's rows form one rounded group.
                    itemsIndexed(
                        day.records,
                        key = { _, record -> record.key },
                        contentType = { _, record -> record::class },
                    ) { index, record ->
                        val shape = groupShape(index, day.records.size)
                        when (record) {
                            is HomeRecord.ExpenseItem -> ExpenseRow(
                                expense = record.expense,
                                shape = shape,
                                onClick = { onEdit(record.expense) },
                                onDelete = { onDelete(record) },
                                onSaveAsTemplate = { onSaveAsTemplate(record.expense) },
                            )
                            is HomeRecord.SettlementItem -> SettlementRow(
                                settlement = record.settlement,
                                shape = shape,
                                onDelete = { onDelete(record) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The balance, coloured from [me]'s point of view: owed to me, I owe, or settled. Accessibility
 * services read it as one sentence ("Marina duguje Lovelu 534,70 €").
 */
@Composable
private fun BalanceCard(balanceCents: Long, me: Person, onSettleUp: () -> Unit, modifier: Modifier = Modifier) {
    val amount = Money.format(balanceCents.absoluteValue)
    val sentence = when {
        balanceCents > 0 -> stringResource(R.string.balance_lovel_owes, amount)
        balanceCents < 0 -> stringResource(R.string.balance_marina_owes, amount)
        else -> stringResource(R.string.balance_settled)
    }
    val direction = when {
        balanceCents > 0 -> stringResource(R.string.balance_lovel_owes_short)
        balanceCents < 0 -> stringResource(R.string.balance_marina_owes_short)
        else -> stringResource(R.string.balance_settled)
    }
    // Positive balance: Lovel owes Marina.
    val debtor = when {
        balanceCents > 0 -> Person.LOVEL
        balanceCents < 0 -> Person.MARINA
        else -> null
    }
    val colors = when (debtor) {
        null -> AppTheme.colors.settled
        me -> AppTheme.colors.iOwe
        else -> AppTheme.colors.owedToMe
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(sentence) },
            ) {
                Text(
                    text = stringResource(R.string.balance_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.content.copy(alpha = 0.75f),
                )
                Text(text = direction, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = amount,
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 1,
                )
            }
            Button(
                onClick = onSettleUp,
                enabled = balanceCents != 0L,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.content,
                    contentColor = colors.container,
                    disabledContainerColor = colors.content.copy(alpha = 0.10f),
                    disabledContentColor = colors.content.copy(alpha = 0.45f),
                ),
                modifier = Modifier.align(Alignment.End).testTag(HomeTags.SETTLE_UP),
            ) {
                Icon(painterResource(R.drawable.ic_swap), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.settle_up))
            }
        }
    }
}

/** "Sve" and one chip per month with records, newest first; scrolls sideways. */
@Composable
private fun MonthChips(months: List<YearMonth>, selected: YearMonth?, onSelect: (YearMonth?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(HomeTags.MONTH_CHIPS),
    ) {
        item(key = "all") {
            MonthChip(stringResource(R.string.filter_all), selected == null, { onSelect(null) }, Modifier.testTag(HomeTags.ALL_MONTHS_CHIP))
        }
        items(months, key = { it.toString() }) { month ->
            MonthChip(monthLabel(month), month == selected, { onSelect(month) }, Modifier.testTag(HomeTags.monthChip(month)))
        }
    }
}

@Composable
private fun MonthChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier.height(36.dp),
    )
}

/** "23 troška · 312,40 €" over the shown expenses (settlements not summed), or "Nema rezultata". */
@Composable
private fun FilterSummaryLine(summary: FilterSummary) {
    val text = if (summary.noResults) {
        stringResource(R.string.filter_no_results)
    } else {
        stringResource(R.string.filter_summary, expenseCountLabel(summary.expenseCount), Money.format(summary.totalCents))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (summary.noResults) TextAlign.Center else TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = if (summary.noResults) 24.dp else 0.dp)
            .testTag(HomeTags.SUMMARY),
    )
}

/**
 * The search field that replaces the top bar title on "Početna": focused when it opens, a clear
 * button while it has text. Matching is in [HomeViewModel] (description and category, ignoring
 * case and diacritics).
 */
@Composable
fun HomeSearchField(text: String, onTextChange: (String) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        singleLine = true,
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.testTag(HomeTags.SEARCH_CLEAR)) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth().focusRequester(focus).testTag(HomeTags.SEARCH_FIELD),
    )
}

private val GROUP_OUTER = 20.dp
private val GROUP_INNER = 6.dp

/** Row [index] of [count] in a day's group: the group's outer corners are round, inner ones tight. */
private fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GROUP_OUTER else GROUP_INNER
    val bottom = if (index == count - 1) GROUP_OUTER else GROUP_INNER
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun ExpenseRow(expense: Expense, shape: Shape, onClick: () -> Unit, onDelete: () -> Unit, onSaveAsTemplate: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = { CategoryBadge(expense.category) },
        headlineContent = {
            Text(expense.category, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            // One line each, also with a large font scale: the amount column stays put.
            Column {
                if (expense.description.isNotBlank()) {
                    Text(expense.description, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = stringResource(
                        R.string.row_meta,
                        stringResource(expense.paidBy.paidByLabel),
                        stringResource(expense.split.label),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expense.split == SplitType.FULL_FOR_OTHER) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Money.format(expense.amountCents), style = MaterialTheme.typography.titleMedium)
                RowMenu(menuOpen, { menuOpen = it }, onEdit = onClick, onDelete = onDelete, onSaveAsTemplate = onSaveAsTemplate)
            }
        },
        colors = ListItemDefaults.colors(containerColor = AppTheme.colors.card),
        modifier = Modifier
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
    )
}

@Composable
private fun SettlementRow(settlement: Settlement, shape: Shape, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = {
            IconBadge(
                icon = R.drawable.ic_swap,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                container = MaterialTheme.colorScheme.secondaryContainer,
            )
        },
        headlineContent = { Text(stringResource(R.string.settlement_title), style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Column {
                Text(
                    stringResource(
                        R.string.settlement_direction,
                        stringResource(settlement.from.label),
                        stringResource(settlement.to.label),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (settlement.note.isNotBlank()) {
                    Text(settlement.note, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Money.format(settlement.amountCents), style = MaterialTheme.typography.titleMedium)
                RowMenu(menuOpen, { menuOpen = it }, onEdit = null, onDelete = onDelete)
            }
        },
        colors = ListItemDefaults.colors(containerColor = AppTheme.colors.card),
        modifier = Modifier
            .clip(shape)
            .combinedClickable(onClick = {}, onLongClick = { menuOpen = true }),
    )
}

@Composable
private fun RowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    onSaveAsTemplate: (() -> Unit)? = null,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    onClick = {
                        onExpandedChange(false)
                        onEdit()
                    },
                )
            }
            if (onSaveAsTemplate != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.save_as_template)) },
                    onClick = {
                        onExpandedChange(false)
                        onSaveAsTemplate()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    onExpandedChange(false)
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun DeleteDialog(record: HomeRecord, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, summary) = when (record) {
        is HomeRecord.ExpenseItem -> R.string.delete_expense_title to stringResource(
            R.string.row_meta,
            record.expense.category,
            Money.format(record.expense.amountCents),
        )
        is HomeRecord.SettlementItem -> R.string.delete_settlement_title to stringResource(
            R.string.row_meta,
            stringResource(
                R.string.settlement_direction,
                stringResource(record.settlement.from.label),
                stringResource(record.settlement.to.label),
            ),
            Money.format(record.settlement.amountCents),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(summary) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SettleUpDialog(
    draft: SettleUpDraft,
    today: LocalDate,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settle_up)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        when (draft.from) {
                            Person.LOVEL -> R.string.settle_lovel_pays
                            Person.MARINA -> R.string.settle_marina_pays
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = draft.amountText,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(R.string.amount_label)) },
                    suffix = { Text(stringResource(R.string.euro_suffix)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag(HomeTags.SETTLE_AMOUNT),
                )
                DateField(date = draft.date, today = today, onDateChange = onDateChange)
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = onNoteChange,
                    label = { Text(stringResource(R.string.note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = draft.canConfirm) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun HomeContentPreview() {
    val today = LocalDate.of(2026, 10, 24)
    val at = Instant.parse("2026-10-24T08:00:00Z")
    val expense = Expense("1", today, "Hrana", "Konzum", 1250, Person.LOVEL, SplitType.HALF, at, at, Person.LOVEL)
    val loan = Expense("2", today.minusDays(1), "Prijevoz", "", 4000, Person.MARINA, SplitType.FULL_FOR_OTHER, at, at, Person.MARINA)
    val settlement = Settlement("3", today.minusDays(3), Person.MARINA, Person.LOVEL, 53_470, "", at, at, Person.MARINA)
    TroskoviTheme {
        HomeContent(
            state = HomeUiState(
                loading = false,
                balanceCents = -53_470,
                expenseCount = 2,
                days = listOf(
                    HomeDay(today, listOf(HomeRecord.ExpenseItem(expense))),
                    HomeDay(today.minusDays(1), listOf(HomeRecord.ExpenseItem(loan))),
                    HomeDay(today.minusDays(3), listOf(HomeRecord.SettlementItem(settlement))),
                ),
                today = today,
                months = listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9)),
                selectedMonth = YearMonth.of(2026, 10),
                summary = FilterSummary(expenseCount = 2, totalCents = 5_250, recordCount = 3),
            ),
            me = Person.LOVEL,
            due = listOf(
                DueItem(
                    RecurringTemplate(
                        "t", "Stanarina", "Stanarina", "", 50_000, Person.MARINA, SplitType.HALF, 15, true, emptySet(),
                        at, at, Person.MARINA,
                    ),
                    YearMonth.of(2026, 10),
                    overdue = false,
                ),
            ),
            onSettleUp = {},
            onSelectMonth = {},
            onEdit = {},
            onDelete = {},
            onSaveAsTemplate = {},
            onPostDue = {},
            onSkipDue = {},
        )
    }
}
