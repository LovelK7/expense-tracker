package hr.ml.expensetracker.ui.recurring

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.RecurringTemplate
import hr.ml.expensetracker.ui.common.CategoryBadge
import hr.ml.expensetracker.ui.common.CategoryPicker
import hr.ml.expensetracker.ui.common.PaidByField
import hr.ml.expensetracker.ui.common.SectionLabel
import hr.ml.expensetracker.ui.common.SplitField
import hr.ml.expensetracker.ui.common.label
import hr.ml.expensetracker.ui.common.paidByLabel
import hr.ml.expensetracker.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

object TemplatesTags {
    const val NEW = "templates_new"
    const val NAME = "template_name"
    const val AMOUNT = "template_amount"
    const val DAY = "template_day"
    const val ACTIVE = "template_active"
    const val SAVE = "template_save"
    const val DELETE = "template_delete"
    const val CLOSE = "templates_close"

    fun row(id: String) = "template_row_$id"

    fun activeSwitch(id: String) = "template_active_$id"
}

/**
 * "Predlošci", full screen over the tabs: the templates with their active switch, and the add/edit
 * form. With [fromExpenseId] it opens straight into a new template prefilled from that expense
 * and closes again once that form is saved ([onSavedAndClosed]) or dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    fromExpenseId: String?,
    onClose: () -> Unit,
    onSavedAndClosed: () -> Unit,
    viewModel: TemplatesViewModel = koinViewModel(),
) {
    // Once per opening (not again after rotation): the list, or the prefilled form.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            if (fromExpenseId != null) viewModel.openFromExpense(fromExpenseId) else viewModel.showList()
        }
    }
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.template_saved)
    val deletedMessage = stringResource(R.string.template_deleted)
    val undo = stringResource(R.string.undo)

    val back = { if (form == null || viewModel.closeForm()) onClose() }
    BackHandler(onBack = back)

    val current = form
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                current == null -> R.string.templates_title
                                current.editingId == null -> R.string.template_new
                                else -> R.string.template_edit_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = back, modifier = Modifier.testTag(TemplatesTags.CLOSE)) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (current?.editingId != null) {
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag(TemplatesTags.DELETE)) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (current == null && fromExpenseId == null) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::openNew,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.template_new)) },
                    modifier = Modifier.testTag(TemplatesTags.NEW),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        when {
            current != null -> TemplateFormContent(
                form = current,
                viewModel = viewModel,
                onSave = {
                    val closeScreen = current.fromExpense
                    if (viewModel.save()) {
                        if (closeScreen) onSavedAndClosed() else scope.launch { snackbar.showSnackbar(savedMessage) }
                    }
                },
                modifier = modifier,
            )
            // Opened from an expense: the prefilled form follows as soon as the expense is read.
            fromExpenseId != null || templates == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> TemplateList(
                templates = templates.orEmpty(),
                onOpen = viewModel::openEdit,
                onActiveChange = viewModel::setActive,
                modifier = modifier,
            )
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_template_title)) },
            text = { Text(stringResource(R.string.row_meta, current.name, Money.format(current.amountCents ?: 0))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        val deleted = viewModel.deleteEditing() ?: return@TextButton
                        scope.launch {
                            val result = snackbar.showSnackbar(deletedMessage, actionLabel = undo, duration = SnackbarDuration.Long)
                            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(deleted)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TemplateList(
    templates: List<RecurringTemplate>,
    onOpen: (RecurringTemplate) -> Unit,
    onActiveChange: (RecurringTemplate, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (templates.isEmpty()) {
        Text(
            text = stringResource(R.string.templates_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Room for the floating button below the last row.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(templates, key = { it.id }) { t ->
            ListItem(
                leadingContent = { CategoryBadge(t.category) },
                headlineContent = {
                    Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Column {
                        Text(
                            stringResource(R.string.template_row_meta, Money.format(t.amountCents), t.dayOfMonth),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (t.active) {
                                stringResource(R.string.row_meta, stringResource(t.paidBy.paidByLabel), stringResource(t.split.label))
                            } else {
                                stringResource(R.string.template_inactive)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                trailingContent = {
                    Switch(
                        checked = t.active,
                        onCheckedChange = { onActiveChange(t, it) },
                        modifier = Modifier.testTag(TemplatesTags.activeSwitch(t.id)),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = AppTheme.colors.card),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onOpen(t) }
                    .testTag(TemplatesTags.row(t.id)),
            )
        }
    }
}

@Composable
private fun TemplateFormContent(
    form: TemplateForm,
    viewModel: TemplatesViewModel,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.template_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().testTag(TemplatesTags.NAME),
            )
            OutlinedTextField(
                value = form.amountText,
                onValueChange = viewModel::onAmountChange,
                label = { Text(stringResource(R.string.amount_label)) },
                suffix = { Text(stringResource(R.string.euro_suffix)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().testTag(TemplatesTags.AMOUNT),
            )

            SectionLabel(R.string.category_label)
            CategoryPicker(
                categories = form.categories,
                selected = form.category,
                expanded = form.categoriesExpanded,
                onSelect = viewModel::onCategorySelect,
                onExpand = viewModel::expandCategories,
            )

            OutlinedTextField(
                value = form.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.description_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            PaidByField(form.paidBy, viewModel::onPaidByChange)
            SplitField(form.split, form.paidBy, viewModel::onSplitChange)

            OutlinedTextField(
                value = form.dayText,
                onValueChange = viewModel::onDayChange,
                label = { Text(stringResource(R.string.template_day_label)) },
                supportingText = { Text(stringResource(R.string.template_day_hint)) },
                isError = form.dayText.isNotEmpty() && form.dayOfMonth == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().testTag(TemplatesTags.DAY),
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.template_active_label), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.template_active_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = form.active,
                    onCheckedChange = viewModel::onActiveChange,
                    modifier = Modifier.testTag(TemplatesTags.ACTIVE),
                )
            }
        }
        Button(
            onClick = onSave,
            enabled = form.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .height(52.dp)
                .testTag(TemplatesTags.SAVE),
        ) {
            Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
        }
    }
}
