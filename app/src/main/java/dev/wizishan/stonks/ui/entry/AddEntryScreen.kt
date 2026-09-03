package dev.wizishan.stonks.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wizishan.stonks.R
import dev.wizishan.stonks.core.Money
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.local.entity.Trip
import dev.wizishan.stonks.ui.AppViewModelProvider
import dev.wizishan.stonks.ui.components.ColorDot
import dev.wizishan.stonks.ui.theme.MinTouchTarget
import dev.wizishan.stonks.ui.theme.Spacing
import dev.wizishan.stonks.ui.theme.StonksTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AddEntryRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEntryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val savedExpense = stringResource(R.string.add_saved_expense)
    val savedIncome = stringResource(R.string.add_saved_income)
    val savedRecurring = stringResource(R.string.add_saved_recurring)
    val saveFailed = stringResource(R.string.add_save_failed)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddEntryEvent.Saved -> {
                    val template = when {
                        event.recurring -> savedRecurring
                        event.type == EntryType.EXPENSE -> savedExpense
                        else -> savedIncome
                    }
                    snackbarHostState.showMessage(template.format(Money.format(event.amountMinor)))
                }

                // Editing and deleting both finish the job the screen was opened for, so
                // they close it. The confirmation belongs on the list the user lands back
                // on, not on a screen that is about to disappear.
                is AddEntryEvent.Updated, is AddEntryEvent.Deleted -> onBack()

                is AddEntryEvent.SaveFailed -> snackbarHostState.showMessage(saveFailed)
            }
        }
    }

    AddEntryScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onTypeChange = viewModel::setType,
        onAmountChange = viewModel::setAmount,
        onDateChange = viewModel::setDate,
        onCategoryChange = viewModel::setCategory,
        onTripChange = viewModel::setTrip,
        onSourceChange = viewModel::setSource,
        onNoteChange = viewModel::setNote,
        onSave = viewModel::save,
        onFrequencyChange = viewModel::setFrequency,
        onDeleteRequest = viewModel::requestDelete,
        onDeleteCancel = viewModel::cancelDelete,
        onDeleteConfirm = viewModel::confirmDelete,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(
    state: AddEntryUiState,
    snackbarHostState: SnackbarHostState,
    onTypeChange: (EntryType) -> Unit,
    onAmountChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onCategoryChange: (Long) -> Unit,
    onTripChange: (Long?) -> Unit,
    onSourceChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onFrequencyChange: (RecurringFrequency?) -> Unit,
    onDeleteRequest: () -> Unit = {},
    onDeleteCancel: () -> Unit = {},
    onDeleteConfirm: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.add_title_edit else R.string.add_title
                        )
                    )
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = onDeleteRequest) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            // Hidden while editing: an entry does not move between tables, and offering
            // the switch would imply it could.
            if (!state.isEditing) {
                EntryTypeSelector(selected = state.type, onSelect = onTypeChange)
            }

            AmountField(
                value = state.amountInput,
                error = state.amountError.takeIf { state.validationVisible },
                onValueChange = onAmountChange,
            )

            DateField(date = state.date, onDateChange = onDateChange)

            if (state.isExpense) {
                CategoryPicker(
                    categories = state.categories,
                    selectedId = state.categoryId,
                    showError = state.validationVisible && state.categoryMissing,
                    onSelect = onCategoryChange,
                )
                if (state.trips.isNotEmpty()) {
                    TripPicker(
                        trips = state.trips,
                        selected = state.selectedTrip,
                        onSelect = onTripChange,
                    )
                }
            } else {
                SourceField(
                    value = state.source,
                    suggestions = state.sourceSuggestions,
                    showError = state.validationVisible && state.sourceMissing,
                    onValueChange = onSourceChange,
                )
            }

            if (state.repeatAvailable) {
                RepeatPicker(selected = state.frequency, onSelect = onFrequencyChange)
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.add_note_label)) },
                placeholder = { Text(stringResource(R.string.add_note_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onSave,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget),
            ) {
                Text(
                    stringResource(
                        if (state.isEditing) R.string.add_save_changes else R.string.add_save
                    )
                )
            }
        }
    }

    if (state.deleteRequested) {
        AlertDialog(
            onDismissRequest = onDeleteCancel,
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.add_delete_message)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteCancel) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTypeSelector(
    selected: EntryType,
    onSelect: (EntryType) -> Unit,
) {
    val options = listOf(
        EntryType.EXPENSE to stringResource(R.string.add_type_expense),
        EntryType.INCOME to stringResource(R.string.add_type_income),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatPicker(
    selected: RecurringFrequency?,
    onSelect: (RecurringFrequency?) -> Unit,
) {
    val options: List<Pair<RecurringFrequency?, Int>> = listOf(
        null to R.string.add_repeat_never,
        RecurringFrequency.DAILY to R.string.add_repeat_daily,
        RecurringFrequency.WEEKLY to R.string.add_repeat_weekly,
        RecurringFrequency.MONTHLY to R.string.add_repeat_monthly,
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.add_repeat_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            options.forEach { (frequency, labelRes) ->
                FilterChip(
                    selected = frequency == selected,
                    onClick = { onSelect(frequency) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
        if (selected != null) {
            Text(
                text = stringResource(R.string.add_repeat_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AmountField(
    value: String,
    error: AmountError?,
    onValueChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.add_amount_label)) },
        prefix = { Text(Money.currency.symbol) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it.messageRes)) } },
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val formatted = date.format(formatter)
    val pickLabel = stringResource(R.string.add_date_pick)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.add_date_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedButton(
            onClick = { picking = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .semantics { contentDescription = "$pickLabel, $formatted" },
        ) {
            Text(formatted)
        }
    }

    if (picking) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateChange(it.toLocalDateUtc()) }
                    picking = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: Long?,
    showError: Boolean,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.add_category_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (categories.isEmpty()) {
            Text(
                text = stringResource(R.string.add_no_categories),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = category.id == selectedId,
                        onClick = { onSelect(category.id) },
                        label = { Text(category.name) },
                        leadingIcon = { ColorDot(category.colorHex) },
                    )
                }
            }
        }
        if (showError) {
            Text(
                text = stringResource(R.string.add_category_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripPicker(
    trips: List<Trip>,
    selected: Trip?,
    onSelect: (Long?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.add_trip_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.add_trip_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.add_trip_none)) },
            )
            trips.forEach { trip ->
                FilterChip(
                    selected = trip.id == selected?.id,
                    onClick = { onSelect(trip.id) },
                    label = { Text(trip.name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceField(
    value: String,
    suggestions: List<String>,
    showError: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.add_source_label)) },
            placeholder = { Text(stringResource(R.string.add_source_hint)) },
            singleLine = true,
            isError = showError,
            supportingText = if (showError) {
                { Text(stringResource(R.string.add_source_error)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                suggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = { onValueChange(suggestion) },
                        label = { Text(suggestion) },
                    )
                }
            }
        }
    }
}

private val AmountError.messageRes: Int
    get() = when (this) {
        AmountError.MISSING -> R.string.add_amount_error_missing
        AmountError.UNREADABLE -> R.string.add_amount_error_unreadable
        AmountError.NOT_POSITIVE -> R.string.add_amount_error_not_positive
    }

/**
 * The picker works in UTC because [androidx.compose.material3.DatePickerState] does; the
 * value is a calendar date, not an instant, so converting through the device time zone
 * would shift it by a day near midnight.
 */
private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private suspend fun SnackbarHostState.showMessage(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}

// ---- previews --------------------------------------------------------------------

private val previewCategories = listOf(
    Category(id = 1, name = "Food & Drink", colorHex = "#2A78D6"),
    Category(id = 2, name = "Transport", colorHex = "#EB6834"),
    Category(id = 3, name = "Lodging", colorHex = "#1BAF7A"),
    Category(id = 4, name = "Shopping", colorHex = "#EDA100"),
)

@Preview(name = "Expense · light", showBackground = true)
@Preview(name = "Expense · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddEntryExpensePreview() {
    StonksTheme {
        AddEntryScreen(
            state = AddEntryUiState(
                amountInput = "12.50",
                categoryId = 1,
                categories = previewCategories,
                trips = listOf(Trip(id = 1, name = "Japan 2026")),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onTypeChange = {}, onAmountChange = {}, onDateChange = {},
            onCategoryChange = {}, onTripChange = {}, onSourceChange = {},
            onNoteChange = {}, onSave = {}, onFrequencyChange = {},
        )
    }
}

@Preview(name = "Income · light", showBackground = true)
@Preview(name = "Income · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddEntryIncomePreview() {
    StonksTheme {
        AddEntryScreen(
            state = AddEntryUiState(
                type = EntryType.INCOME,
                source = "Sal",
                knownSources = listOf("Salary", "Freelance"),
                categories = previewCategories,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onTypeChange = {}, onAmountChange = {}, onDateChange = {},
            onCategoryChange = {}, onTripChange = {}, onSourceChange = {},
            onNoteChange = {}, onSave = {}, onFrequencyChange = {},
        )
    }
}

@Preview(name = "Validation errors", showBackground = true)
@Composable
private fun AddEntryErrorsPreview() {
    StonksTheme {
        AddEntryScreen(
            state = AddEntryUiState(
                amountInput = "",
                categories = previewCategories,
                validationVisible = true,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onTypeChange = {}, onAmountChange = {}, onDateChange = {},
            onCategoryChange = {}, onTripChange = {}, onSourceChange = {},
            onNoteChange = {}, onSave = {}, onFrequencyChange = {},
        )
    }
}
