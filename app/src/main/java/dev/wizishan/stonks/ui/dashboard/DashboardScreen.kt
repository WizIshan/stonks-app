package dev.wizishan.stonks.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wizishan.stonks.R
import dev.wizishan.stonks.core.Money
import dev.wizishan.stonks.data.repository.DashboardData
import dev.wizishan.stonks.data.repository.MonthPoint
import dev.wizishan.stonks.data.repository.RankedSlice
import dev.wizishan.stonks.ui.AppViewModelProvider
import dev.wizishan.stonks.ui.components.EmptyState
import dev.wizishan.stonks.ui.components.RankedBarChart
import dev.wizishan.stonks.ui.components.StatTile
import dev.wizishan.stonks.ui.components.TrendLineChart
import dev.wizishan.stonks.ui.theme.Spacing
import dev.wizishan.stonks.ui.theme.StonksTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun DashboardRoute(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onAddClick = onAddClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.dashboard_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            MonthSelector(
                month = state.data.month,
                canGoForward = state.canGoForward,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
            )

            if (state.isEmpty) {
                EmptyState(
                    icon = Icons.Default.DateRange,
                    headline = stringResource(R.string.dashboard_empty_headline),
                    message = stringResource(R.string.dashboard_empty_message),
                    actionLabel = stringResource(R.string.history_add),
                    onAction = onAddClick,
                )
            } else {
                Totals(state.data)

                if (state.data.byCategory.isNotEmpty()) {
                    Section(stringResource(R.string.dashboard_by_category)) {
                        RankedBarChart(state.data.byCategory)
                    }
                }

                if (state.data.byTrip.isNotEmpty()) {
                    Section(stringResource(R.string.dashboard_by_trip)) {
                        RankedBarChart(state.data.byTrip)
                    }
                }

                if (state.data.trend.size >= 2) {
                    Section(stringResource(R.string.dashboard_trend)) {
                        TrendLineChart(state.data.trend)
                    }
                }
            }
        }
    }
}

/**
 * The hero figure, then the two supporting numbers.
 *
 * Month spend gets `displaySmall` on its own because it is the one number the screen leads
 * with; income and net are a KPI row beside it rather than three equal tiles, so the
 * hierarchy says which question the screen answers first.
 */
@Composable
private fun Totals(data: DashboardData) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.dashboard_spent_this_month),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Money.format(data.spendMinor),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile(
                label = stringResource(R.string.dashboard_income),
                value = Money.format(data.incomeMinor),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.dashboard_net),
                // Signed, always — polarity never depends on colour (DESIGN.md §3d).
                value = Money.formatSigned(data.netMinor),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.dashboard_previous_month),
            )
        }
        Text(
            text = month.format(formatter),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = stringResource(R.string.dashboard_next_month),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

// ---- previews --------------------------------------------------------------------

private val previewData = DashboardData(
    month = YearMonth.of(2026, 9),
    spendMinor = 156_750,
    incomeMinor = 250_000,
    byCategory = listOf(
        RankedSlice("Bills & Utilities", "#E34948", 82_000),
        RankedSlice("Food & Drink", "#2A78D6", 45_250),
        RankedSlice("Transport", "#EB6834", 21_000),
        RankedSlice("Groceries", "#008300", 8_500),
    ),
    byTrip = listOf(RankedSlice("Japan 2026", "#898781", 61_200)),
    trend = (0..5).map {
        val month = YearMonth.of(2026, 4).plusMonths(it.toLong())
        MonthPoint(month, 120_000 + it * 9_000L, 250_000)
    },
)

@Preview(name = "Dashboard · light", showBackground = true, heightDp = 1200)
@Preview(name = "Dashboard · dark", showBackground = true, heightDp = 1200, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardPreview() {
    StonksTheme {
        DashboardScreen(
            state = DashboardUiState(data = previewData, loading = false),
            onPreviousMonth = {}, onNextMonth = {}, onAddClick = {},
        )
    }
}

@Preview(name = "Dashboard · empty", showBackground = true)
@Composable
private fun DashboardEmptyPreview() {
    StonksTheme {
        DashboardScreen(
            state = DashboardUiState(data = DashboardData(month = YearMonth.of(2026, 9)), loading = false),
            onPreviousMonth = {}, onNextMonth = {}, onAddClick = {},
        )
    }
}
