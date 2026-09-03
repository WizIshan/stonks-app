package dev.wizishan.stonks.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wizishan.stonks.R
import dev.wizishan.stonks.core.Money
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.ui.AppViewModelProvider
import dev.wizishan.stonks.ui.components.ColorDot
import dev.wizishan.stonks.ui.components.EmptyState
import dev.wizishan.stonks.ui.theme.Spacing
import dev.wizishan.stonks.ui.theme.StonksTheme
import dev.wizishan.stonks.ui.theme.TabularFigures
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun RecurringRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecurringScreen(
        state = state,
        onBack = onBack,
        onSetActive = viewModel::setActive,
        onDeleteRequest = viewModel::requestDelete,
        onDeleteCancel = viewModel::cancelDelete,
        onDeleteConfirm = viewModel::confirmDelete,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    state: RecurringUiState,
    onBack: () -> Unit,
    onSetActive: (Long, Boolean) -> Unit,
    onDeleteRequest: (RecurringRuleRow) -> Unit,
    onDeleteCancel: () -> Unit,
    onDeleteConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurring_title)) },
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
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (state.isEmpty) {
                EmptyState(
                    icon = Icons.Default.Refresh,
                    headline = stringResource(R.string.recurring_empty_headline),
                    message = stringResource(R.string.recurring_empty_message),
                    actionLabel = null,
                    onAction = null,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rules, key = { it.id }) { rule ->
                        RecurringRow(
                            rule = rule,
                            onSetActive = onSetActive,
                            onDeleteRequest = onDeleteRequest,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = onDeleteCancel,
            title = { Text(stringResource(R.string.recurring_delete_title)) },
            text = { Text(stringResource(R.string.recurring_delete_message, rule.title)) },
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
private fun RecurringRow(
    rule: RecurringRuleRow,
    onSetActive: (Long, Boolean) -> Unit,
    onDeleteRequest: (RecurringRuleRow) -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(Spacing.md), contentAlignment = Alignment.Center) {
            rule.colorHex?.let { ColorDot(it) }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = rule.secondaryLine(dateFormatter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = Money.formatSigned(if (rule.isExpense) -rule.amountMinor else rule.amountMinor),
            style = MaterialTheme.typography.bodyLarge.merge(TabularFigures),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Switch(
            checked = rule.isActive,
            onCheckedChange = { onSetActive(rule.id, it) },
        )

        IconButton(onClick = { onDeleteRequest(rule) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
            )
        }
    }
}

/**
 * Frequency, then either the next date or the fact that it is paused.
 *
 * A paused rule shows no next date on purpose — it does not have one, and showing a stale
 * date would suggest it is still going to fire.
 */
@Composable
private fun RecurringRuleRow.secondaryLine(formatter: DateTimeFormatter): String {
    val frequency = stringResource(frequency.labelRes)
    return if (isActive) {
        "$frequency · " + stringResource(R.string.recurring_next_due, nextDueDate.format(formatter))
    } else {
        "$frequency · " + stringResource(R.string.recurring_paused)
    }
}

private val RecurringFrequency.labelRes: Int
    get() = when (this) {
        RecurringFrequency.DAILY -> R.string.add_repeat_daily
        RecurringFrequency.WEEKLY -> R.string.add_repeat_weekly
        RecurringFrequency.MONTHLY -> R.string.add_repeat_monthly
    }

@Preview(name = "Repeating · light", showBackground = true)
@Preview(name = "Repeating · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecurringPreview() {
    StonksTheme {
        RecurringScreen(
            state = RecurringUiState(
                rules = listOf(
                    RecurringRuleRow(1, "Bills & Utilities", "#E34948", 95_000, RecurringFrequency.MONTHLY, LocalDate.parse("2026-10-01"), true, true),
                    RecurringRuleRow(2, "Salary", null, 250_000, RecurringFrequency.MONTHLY, LocalDate.parse("2026-10-01"), true, false),
                    RecurringRuleRow(3, "Entertainment", "#E87BA4", 1_299, RecurringFrequency.MONTHLY, LocalDate.parse("2026-09-20"), false, true),
                ),
                loading = false,
            ),
            onBack = {}, onSetActive = { _, _ -> }, onDeleteRequest = {},
            onDeleteCancel = {}, onDeleteConfirm = {},
        )
    }
}

@Preview(name = "Repeating · empty", showBackground = true)
@Composable
private fun RecurringEmptyPreview() {
    StonksTheme {
        RecurringScreen(
            state = RecurringUiState(loading = false),
            onBack = {}, onSetActive = { _, _ -> }, onDeleteRequest = {},
            onDeleteCancel = {}, onDeleteConfirm = {},
        )
    }
}
