package dev.wizishan.stonks.ui.budget

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wizishan.stonks.R
import dev.wizishan.stonks.core.Money
import dev.wizishan.stonks.data.budget.BudgetProgress
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.ui.AppViewModelProvider
import dev.wizishan.stonks.ui.components.BudgetMeter
import dev.wizishan.stonks.ui.components.ColorDot
import dev.wizishan.stonks.ui.components.EmptyState
import dev.wizishan.stonks.ui.theme.Radius
import dev.wizishan.stonks.ui.theme.Spacing
import dev.wizishan.stonks.ui.theme.StonksTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Notifications

@Composable
fun BudgetRoute(
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permission = notificationPermissionState { granted ->
        viewModel.setNotificationsAllowed(granted)
    }
    LaunchedEffect(Unit) { viewModel.setNotificationsAllowed(permission.isGranted()) }

    BudgetScreen(
        state = state,
        onAddBudget = viewModel::startNewBudget,
        onEditBudget = viewModel::startEditing,
        onEnableNotifications = permission::request,
        onEditorCategoryChange = viewModel::setEditorCategory,
        onEditorLimitChange = viewModel::setEditorLimit,
        onEditorThresholdChange = viewModel::setEditorThreshold,
        onEditorSave = viewModel::saveEditor,
        onEditorCancel = viewModel::cancelEditing,
        onEditorDelete = viewModel::deleteBudget,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    state: BudgetUiState,
    onAddBudget: () -> Unit,
    onEditBudget: (BudgetProgress) -> Unit,
    onEnableNotifications: () -> Unit,
    onEditorCategoryChange: (Long?) -> Unit,
    onEditorLimitChange: (String) -> Unit,
    onEditorThresholdChange: (Int) -> Unit,
    onEditorSave: () -> Unit,
    onEditorCancel: () -> Unit,
    onEditorDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.budgets_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBudget,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.budget_set)) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (state.showNotificationPrompt) {
                NotificationPrompt(onEnable = onEnableNotifications)
            }

            if (state.isEmpty) {
                EmptyState(
                    icon = Icons.Default.Notifications,
                    headline = stringResource(R.string.budget_empty_headline),
                    message = stringResource(R.string.budget_empty_message),
                    actionLabel = stringResource(R.string.budget_set),
                    onAction = onAddBudget,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    items(state.progress, key = { it.budgetId }) { progress ->
                        BudgetMeter(
                            progress = progress,
                            modifier = Modifier.clickable { onEditBudget(progress) },
                        )
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        BudgetEditorSheet(
            editor = editor,
            state = state,
            onCategoryChange = onEditorCategoryChange,
            onLimitChange = onEditorLimitChange,
            onThresholdChange = onEditorThresholdChange,
            onSave = onEditorSave,
            onCancel = onEditorCancel,
            onDelete = onEditorDelete,
        )
    }
}

@Composable
private fun NotificationPrompt(onEnable: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.budget_notifications_headline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.budget_notifications_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onEnable) {
                Text(stringResource(R.string.budget_notifications_enable))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BudgetEditorSheet(
    editor: BudgetEditor,
    state: BudgetUiState,
    onCategoryChange: (Long?) -> Unit,
    onLimitChange: (String) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(
                    if (editor.existingBudgetId != null) R.string.budget_edit else R.string.budget_set
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            CategoryChoice(
                editor = editor,
                categories = state.availableCategories(editor.categoryId),
                overallAvailable = !state.hasOverallBudget || editor.categoryId == null,
                onCategoryChange = onCategoryChange,
            )

            OutlinedTextField(
                value = editor.limitInput,
                onValueChange = onLimitChange,
                label = { Text(stringResource(R.string.budget_limit_label)) },
                prefix = { Text(Money.currency.symbol) },
                singleLine = true,
                isError = editor.limitError,
                supportingText = if (editor.limitError) {
                    { Text(stringResource(R.string.budget_amount_error)) }
                } else {
                    null
                },
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.budget_threshold_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ThresholdOptions.forEach { percent ->
                        FilterChip(
                            selected = percent == editor.thresholdPercent,
                            onClick = { onThresholdChange(percent) },
                            label = { Text("$percent%") },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (editor.existingBudgetId != null) {
                    TextButton(onClick = { onDelete(editor.existingBudgetId) }) {
                        Text(
                            text = stringResource(R.string.budget_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.budget_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChoice(
    editor: BudgetEditor,
    categories: List<Category>,
    overallAvailable: Boolean,
    onCategoryChange: (Long?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.budget_category_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Hidden once an overall budget exists — a second one would leave two limits
            // on the same thing with no way to say which an alert meant.
            if (overallAvailable) {
                FilterChip(
                    selected = editor.categoryId == null,
                    onClick = { onCategoryChange(null) },
                    label = { Text(stringResource(R.string.budget_overall)) },
                )
            }
            categories.forEach { category ->
                FilterChip(
                    selected = category.id == editor.categoryId,
                    onClick = { onCategoryChange(category.id) },
                    label = { Text(category.name) },
                    leadingIcon = { ColorDot(category.colorHex) },
                )
            }
        }
    }
}

/** Percentages worth offering; anything finer is fiddly on a phone and rarely meaningful. */
private val ThresholdOptions = listOf(50, 75, 80, 90, 100)

// ---- notification permission -----------------------------------------------------

private interface NotificationPermission {
    fun isGranted(): Boolean
    fun request()
}

/**
 * Android 13+ needs a runtime grant to post anything; below that the manifest entry is
 * enough. The prompt is only ever raised from the button on this screen, so the request
 * arrives with a visible reason attached.
 */
@Composable
private fun notificationPermissionState(onResult: (Boolean) -> Unit): NotificationPermission {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onResult,
    )

    return object : NotificationPermission {
        override fun isGranted(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            }

        override fun request() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onResult(isGranted())
            }
        }
    }
}

@Preview(name = "Budgets · light", showBackground = true)
@Preview(name = "Budgets · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BudgetScreenPreview() {
    StonksTheme {
        BudgetScreen(
            state = BudgetUiState(
                progress = listOf(
                    BudgetProgress(1, null, "Overall", null, 186_000, 300_000, 80),
                    BudgetProgress(2, 1, "Food & Drink", "#2A78D6", 43_500, 50_000, 80),
                    BudgetProgress(3, 2, "Transport", "#EB6834", 52_000, 40_000, 80),
                ),
                loading = false,
            ),
            onAddBudget = {}, onEditBudget = {}, onEnableNotifications = {},
            onEditorCategoryChange = {}, onEditorLimitChange = {}, onEditorThresholdChange = {},
            onEditorSave = {}, onEditorCancel = {}, onEditorDelete = {},
        )
    }
}

@Preview(name = "Budgets · empty", showBackground = true)
@Composable
private fun BudgetScreenEmptyPreview() {
    StonksTheme {
        BudgetScreen(
            state = BudgetUiState(loading = false),
            onAddBudget = {}, onEditBudget = {}, onEnableNotifications = {},
            onEditorCategoryChange = {}, onEditorLimitChange = {}, onEditorThresholdChange = {},
            onEditorSave = {}, onEditorCancel = {}, onEditorDelete = {},
        )
    }
}
