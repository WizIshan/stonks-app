package dev.wizishan.stonks.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wizishan.stonks.R
import dev.wizishan.stonks.ui.AppViewModelProvider
import dev.wizishan.stonks.ui.components.EmptyState
import dev.wizishan.stonks.ui.theme.MinTouchTarget
import dev.wizishan.stonks.ui.theme.Spacing
import dev.wizishan.stonks.ui.theme.StonksTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TripsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TripsScreen(
        state = state,
        onBack = onBack,
        onNew = viewModel::startNew,
        onEdit = viewModel::startEditing,
        onDeleteRequest = viewModel::requestDelete,
        onDeleteCancel = viewModel::cancelDelete,
        onDeleteConfirm = viewModel::confirmDelete,
        onEditorName = viewModel::setEditorName,
        onEditorStart = viewModel::setEditorStart,
        onEditorEnd = viewModel::setEditorEnd,
        onEditorSave = viewModel::saveEditor,
        onEditorCancel = viewModel::cancelEditing,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    state: TripsUiState,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (TripRow) -> Unit,
    onDeleteRequest: (TripRow) -> Unit,
    onDeleteCancel: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onEditorName: (String) -> Unit,
    onEditorStart: (LocalDate?) -> Unit,
    onEditorEnd: (LocalDate?) -> Unit,
    onEditorSave: () -> Unit,
    onEditorCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trips_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.trips_new)) },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (state.isEmpty) {
                EmptyState(
                    icon = Icons.Default.Place,
                    headline = stringResource(R.string.trips_empty_headline),
                    message = stringResource(R.string.trips_empty_message),
                    actionLabel = stringResource(R.string.trips_new),
                    onAction = onNew,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.trips, key = { it.id }) { row ->
                        TripListRow(row = row, onEdit = onEdit, onDeleteRequest = onDeleteRequest)
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        TripEditorSheet(
            editor = editor,
            onName = onEditorName,
            onStart = onEditorStart,
            onEnd = onEditorEnd,
            onSave = onEditorSave,
            onCancel = onEditorCancel,
        )
    }

    state.pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = onDeleteCancel,
            title = { Text(stringResource(R.string.trips_delete_title, row.name)) },
            text = {
                Text(
                    if (row.expenseCount == 0) {
                        stringResource(R.string.trips_delete_message_empty)
                    } else {
                        // No reassignment step: the foreign key is SET_NULL, so the
                        // expenses survive and simply stop being tagged.
                        stringResource(R.string.trips_delete_message, row.expenseCount)
                    }
                )
            },
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

@Composable
private fun TripListRow(
    row: TripRow,
    onEdit: (TripRow) -> Unit,
    onDeleteRequest: (TripRow) -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(row) }
            .padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.secondaryLine(formatter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onDeleteRequest(row) }) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
        }
    }
}

/** Entry count, then the dates if there are any — they are optional on a trip. */
@Composable
private fun TripRow.secondaryLine(formatter: DateTimeFormatter): String {
    val count = if (expenseCount == 0) {
        stringResource(R.string.trips_usage_empty)
    } else {
        stringResource(R.string.trips_usage_entries, expenseCount)
    }
    val dates = listOfNotNull(startDate, endDate).map { it.format(formatter) }
    return if (dates.isEmpty()) count else "$count · ${dates.joinToString(" – ")}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripEditorSheet(
    editor: TripEditor,
    onName: (String) -> Unit,
    onStart: (LocalDate?) -> Unit,
    onEnd: (LocalDate?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(
                    if (editor.isNew) R.string.trips_new else R.string.trips_edit
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = editor.name,
                onValueChange = onName,
                label = { Text(stringResource(R.string.trips_name_label)) },
                placeholder = { Text(stringResource(R.string.trips_name_hint)) },
                singleLine = true,
                isError = editor.nameError,
                supportingText = when {
                    editor.nameTaken -> {
                        { Text(stringResource(R.string.trips_name_taken)) }
                    }

                    editor.validationVisible && editor.nameBlank -> {
                        { Text(stringResource(R.string.trips_name_error)) }
                    }

                    else -> null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            OptionalDateField(
                label = stringResource(R.string.trips_start_label),
                date = editor.startDate,
                onChange = onStart,
            )
            OptionalDateField(
                label = stringResource(R.string.trips_end_label),
                date = editor.endDate,
                onChange = onEnd,
            )

            if (editor.datesInverted) {
                Text(
                    text = stringResource(R.string.trips_dates_inverted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = onSave,
                enabled = editor.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.trips_save))
            }
        }
    }
}

/**
 * A date that can also be no date at all.
 *
 * Trips genuinely have open ends — one being planned has no dates yet, one still running
 * has no end — so clearing has to be as easy as setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionalDateField(
    label: String,
    date: LocalDate?,
    onChange: (LocalDate?) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(
                onClick = { picking = true },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MinTouchTarget),
            ) {
                Text(date?.format(formatter) ?: stringResource(R.string.trips_date_any))
            }
            if (date != null) {
                TextButton(onClick = { onChange(null) }) {
                    Text(stringResource(R.string.trips_date_clear))
                }
            }
        }
    }

    if (picking) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
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

@Preview(name = "Trips · light", showBackground = true)
@Preview(name = "Trips · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TripsPreview() {
    StonksTheme {
        TripsScreen(
            state = TripsUiState(
                trips = listOf(
                    TripRow(1, "Japan 2026", LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-14"), 32),
                    TripRow(2, "Lisbon weekend", LocalDate.parse("2026-06-12"), null, 7),
                    TripRow(3, "Someday", null, null, 0),
                ),
                loading = false,
            ),
            onBack = {}, onNew = {}, onEdit = {}, onDeleteRequest = {},
            onDeleteCancel = {}, onDeleteConfirm = {}, onEditorName = {},
            onEditorStart = {}, onEditorEnd = {}, onEditorSave = {}, onEditorCancel = {},
        )
    }
}

@Preview(name = "Trips · empty", showBackground = true)
@Composable
private fun TripsEmptyPreview() {
    StonksTheme {
        TripsScreen(
            state = TripsUiState(loading = false),
            onBack = {}, onNew = {}, onEdit = {}, onDeleteRequest = {},
            onDeleteCancel = {}, onDeleteConfirm = {}, onEditorName = {},
            onEditorStart = {}, onEditorEnd = {}, onEditorSave = {}, onEditorCancel = {},
        )
    }
}
