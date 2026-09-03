package dev.wizishan.stonks.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wizishan.stonks.R
import dev.wizishan.stonks.ui.AppViewModelProvider
import dev.wizishan.stonks.ui.components.ColorDot
import dev.wizishan.stonks.ui.components.ColorPicker
import dev.wizishan.stonks.ui.theme.CategoryPalette
import dev.wizishan.stonks.ui.theme.Spacing
import dev.wizishan.stonks.ui.theme.StonksTheme

@Composable
fun CategoriesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val lastCategory = stringResource(R.string.categories_last_category)
    val noFreeSlot = stringResource(R.string.categories_no_free_slot)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    CategoriesEvent.LastCategory -> lastCategory
                    CategoriesEvent.NoFreeSlot -> noFreeSlot
                }
            )
        }
    }

    CategoriesScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onNew = viewModel::startNew,
        onEdit = viewModel::startEditing,
        onDeleteRequest = viewModel::requestDelete,
        onDeleteCancel = viewModel::cancelDelete,
        onDeleteConfirm = viewModel::confirmDelete,
        onEditorName = viewModel::setEditorName,
        onEditorColor = viewModel::setEditorColor,
        onEditorSave = viewModel::saveEditor,
        onEditorCancel = viewModel::cancelEditing,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (CategoryRow) -> Unit,
    onDeleteRequest: (CategoryRow) -> Unit,
    onDeleteCancel: () -> Unit,
    onDeleteConfirm: (Long) -> Unit,
    onEditorName: (String) -> Unit,
    onEditorColor: (String) -> Unit,
    onEditorSave: () -> Unit,
    onEditorCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.categories_new)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            items(state.categories, key = { it.id }) { row ->
                CategoryListRow(
                    row = row,
                    canDelete = state.canDeleteAny,
                    onEdit = onEdit,
                    onDeleteRequest = onDeleteRequest,
                )
                HorizontalDivider()
            }
        }
    }

    state.editor?.let { editor ->
        CategoryEditorSheet(
            editor = editor,
            onName = onEditorName,
            onColor = onEditorColor,
            onSave = onEditorSave,
            onCancel = onEditorCancel,
        )
    }

    state.pendingDelete?.let { row ->
        DeleteCategoryDialog(
            row = row,
            targets = state.reassignTargets(row.id),
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteCancel,
        )
    }
}

@Composable
private fun CategoryListRow(
    row: CategoryRow,
    canDelete: Boolean,
    onEdit: (CategoryRow) -> Unit,
    onDeleteRequest: (CategoryRow) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(row) }
            .padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(row.colorHex)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.usageLine(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canDelete) {
            IconButton(onClick = { onDeleteRequest(row) }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun CategoryRow.usageLine(): String {
    if (isEmpty) return stringResource(R.string.categories_usage_empty)
    return buildString {
        append(stringResource(R.string.categories_usage_entries, expenseCount))
        if (recurringRuleCount > 0) {
            append(stringResource(R.string.categories_usage_rules, recurringRuleCount))
        }
        if (hasBudget) append(stringResource(R.string.categories_usage_budget))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorSheet(
    editor: CategoryEditor,
    onName: (String) -> Unit,
    onColor: (String) -> Unit,
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
                    if (editor.isNew) R.string.categories_new else R.string.categories_edit
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = editor.name,
                onValueChange = onName,
                label = { Text(stringResource(R.string.categories_name_label)) },
                singleLine = true,
                isError = editor.nameError,
                supportingText = when {
                    editor.nameTaken -> {
                        { Text(stringResource(R.string.categories_name_taken)) }
                    }

                    editor.validationVisible && editor.nameBlank -> {
                        { Text(stringResource(R.string.categories_name_error)) }
                    }

                    else -> null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.categories_colour_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ColorPicker(selectedHex = editor.colorHex, onSelect = onColor)
            }

            Button(
                onClick = onSave,
                enabled = editor.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.categories_save))
            }
        }
    }
}


@Composable
private fun DeleteCategoryDialog(
    row: CategoryRow,
    targets: List<CategoryRow>,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(row.id) { mutableLongStateOf(targets.firstOrNull()?.id ?: -1L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.categories_delete_title, row.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = stringResource(
                        if (row.isEmpty) R.string.categories_delete_empty_note
                        else R.string.categories_delete_note
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!row.isEmpty) {
                    Text(
                        text = stringResource(R.string.categories_delete_move),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Column {
                        targets.forEach { target ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = target.id }
                                    .padding(vertical = Spacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ColorDot(target.colorHex)
                                Text(
                                    text = target.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (target.id == selected) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected) },
                enabled = selected > 0,
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Preview(name = "Categories · light", showBackground = true)
@Preview(name = "Categories · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoriesPreview() {
    StonksTheme {
        CategoriesScreen(
            state = CategoriesUiState(
                categories = listOf(
                    CategoryRow(1, "Food & Drink", "#2A78D6", 24, 0, false),
                    CategoryRow(2, "Transport", "#EB6834", 8, 1, true),
                    CategoryRow(3, "Coffee", "#1BAF7A", 0, 0, false),
                ),
                loading = false,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {}, onNew = {}, onEdit = {}, onDeleteRequest = {},
            onDeleteCancel = {}, onDeleteConfirm = {}, onEditorName = {},
            onEditorColor = {}, onEditorSave = {}, onEditorCancel = {},
        )
    }
}
