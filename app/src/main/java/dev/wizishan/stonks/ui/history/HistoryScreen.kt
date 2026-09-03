package dev.wizishan.stonks.ui.history

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.wizishan.stonks.R
import dev.wizishan.stonks.core.Money
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.local.entity.Trip
import dev.wizishan.stonks.data.local.query.HistorySort
import dev.wizishan.stonks.data.repository.HistoryFilter
import dev.wizishan.stonks.data.repository.HistoryItem
import dev.wizishan.stonks.data.repository.HistoryPeriod
import dev.wizishan.stonks.data.repository.HistoryType
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
fun HistoryRoute(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onAddClick = onAddClick,
        onTypeChange = viewModel::setType,
        onCategoryChange = viewModel::setCategory,
        onTripChange = viewModel::setTrip,
        onPeriodChange = viewModel::setPeriod,
        onSortChange = viewModel::setSort,
        onClearFilters = viewModel::clearFilters,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onAddClick: () -> Unit,
    onTypeChange: (HistoryType) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onTripChange: (Long?) -> Unit,
    onPeriodChange: (HistoryPeriod) -> Unit,
    onSortChange: (HistorySort) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                actions = {
                    SortMenu(
                        current = state.filter.sort,
                        available = state.filter.availableSorts,
                        onSelect = onSortChange,
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.history_add)) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            FilterRow(
                filter = state.filter,
                categories = state.categories,
                trips = state.trips,
                onTypeChange = onTypeChange,
                onCategoryChange = onCategoryChange,
                onTripChange = onTripChange,
                onPeriodChange = onPeriodChange,
            )

            when {
                state.isEmptyOverall -> EmptyState(
                    icon = Icons.Default.List,
                    headline = stringResource(R.string.history_empty_headline),
                    message = stringResource(R.string.history_empty_message),
                    actionLabel = stringResource(R.string.history_add),
                    onAction = onAddClick,
                )

                state.isEmptyForFilter -> EmptyState(
                    icon = Icons.Default.Search,
                    headline = stringResource(R.string.history_no_matches_headline),
                    message = stringResource(R.string.history_no_matches_message),
                    actionLabel = stringResource(R.string.history_clear_filters),
                    onAction = onClearFilters,
                )

                else -> {
                    SummaryLine(count = state.items.size, netMinor = state.netMinor)
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.items, key = { it.rowKey }) { item ->
                            HistoryRow(item)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(count: Int, netMinor: Long) {
    Text(
        text = stringResource(R.string.history_summary, count, Money.formatSigned(netMinor)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    )
}

@Composable
private fun FilterRow(
    filter: HistoryFilter,
    categories: List<Category>,
    trips: List<Trip>,
    onTypeChange: (HistoryType) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onTripChange: (Long?) -> Unit,
    onPeriodChange: (HistoryPeriod) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        DropdownFilterChip(
            label = stringResource(R.string.history_filter_type),
            value = stringResource(filter.type.labelRes),
            active = filter.type != HistoryType.ALL,
            options = HistoryType.entries.map { it to stringResource(it.labelRes) },
            onSelect = onTypeChange,
        )
        DropdownFilterChip(
            label = stringResource(R.string.history_filter_period),
            value = stringResource(filter.period.labelRes),
            active = filter.period != HistoryPeriod.ALL_TIME,
            options = HistoryPeriod.entries.map { it to stringResource(it.labelRes) },
            onSelect = onPeriodChange,
        )
        // Income has no category or trip, so offering these while viewing income alone
        // would only ever produce an empty list.
        if (filter.type != HistoryType.INCOME) {
            val anyLabel = stringResource(R.string.history_filter_any)
            DropdownFilterChip(
                label = stringResource(R.string.history_filter_category),
                value = categories.firstOrNull { it.id == filter.categoryId }?.name ?: anyLabel,
                active = filter.categoryId != null,
                options = listOf<Long?>(null).plus(categories.map { it.id })
                    .map { id -> id to (categories.firstOrNull { it.id == id }?.name ?: anyLabel) },
                onSelect = onCategoryChange,
            )
            if (trips.isNotEmpty()) {
                DropdownFilterChip(
                    label = stringResource(R.string.history_filter_trip),
                    value = trips.firstOrNull { it.id == filter.tripId }?.name ?: anyLabel,
                    active = filter.tripId != null,
                    options = listOf<Long?>(null).plus(trips.map { it.id })
                        .map { id -> id to (trips.firstOrNull { it.id == id }?.name ?: anyLabel) },
                    onSelect = onTripChange,
                )
            }
        }
    }
}

/**
 * A filter chip that opens its own menu.
 *
 * Four filters as four chip rows would push the list off the screen, so each collapses to
 * one chip showing its current value, in a single scrollable row (DESIGN.md §4).
 */
@Composable
private fun <T> DropdownFilterChip(
    label: String,
    value: String,
    active: Boolean,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(if (active) value else label) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(Spacing.lg),
                )
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (option, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SortMenu(
    current: HistorySort,
    available: List<HistorySort>,
    onSelect: (HistorySort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(current.labelRes))
            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.history_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            available.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelRes)) },
                    onClick = {
                        onSelect(sort)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val incomeLabel = stringResource(R.string.history_income_label)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Income keeps the same leading width as an expense so the two line up; it has no
        // category, so it gets no dot and says "Income" in its secondary line instead.
        Box(modifier = Modifier.size(Spacing.md), contentAlignment = Alignment.Center) {
            if (item is HistoryItem.ExpenseItem) ColorDot(item.categoryColorHex)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (item) {
                    is HistoryItem.ExpenseItem -> item.categoryName
                    is HistoryItem.IncomeItem -> item.source
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.secondaryLine(formatter, incomeLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = Money.formatSigned(item.signedAmountMinor),
            style = MaterialTheme.typography.bodyLarge.merge(TabularFigures),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Date, then whatever else distinguishes the row.
 *
 * The sign in front of the amount is what says expense or income, so this line does not
 * repeat it for expenses — but income says so explicitly, since it has no category name
 * to identify it.
 */
private fun HistoryItem.secondaryLine(
    formatter: DateTimeFormatter,
    incomeLabel: String,
): String {
    val parts = mutableListOf(date.format(formatter))
    when (this) {
        is HistoryItem.ExpenseItem -> tripName?.let(parts::add)
        is HistoryItem.IncomeItem -> parts.add(0, incomeLabel)
    }
    note?.let(parts::add)
    return parts.joinToString(" · ")
}

/** Ids repeat across the two tables, so the key has to carry the type as well. */
private val HistoryItem.rowKey: String
    get() = when (this) {
        is HistoryItem.ExpenseItem -> "expense-$id"
        is HistoryItem.IncomeItem -> "income-$id"
    }

private val HistorySort.labelRes: Int
    get() = when (this) {
        HistorySort.DATE_DESC -> R.string.history_sort_date_desc
        HistorySort.DATE_ASC -> R.string.history_sort_date_asc
        HistorySort.AMOUNT_DESC -> R.string.history_sort_amount_desc
        HistorySort.AMOUNT_ASC -> R.string.history_sort_amount_asc
        HistorySort.CATEGORY_ASC -> R.string.history_sort_category
        HistorySort.TRIP_ASC -> R.string.history_sort_trip
    }

private val HistoryType.labelRes: Int
    get() = when (this) {
        HistoryType.ALL -> R.string.history_type_all
        HistoryType.EXPENSES -> R.string.history_type_expenses
        HistoryType.INCOME -> R.string.history_type_income
    }

private val HistoryPeriod.labelRes: Int
    get() = when (this) {
        HistoryPeriod.ALL_TIME -> R.string.history_period_all
        HistoryPeriod.THIS_MONTH -> R.string.history_period_month
        HistoryPeriod.LAST_30_DAYS -> R.string.history_period_30
        HistoryPeriod.LAST_90_DAYS -> R.string.history_period_90
    }

// ---- previews --------------------------------------------------------------------

private val previewItems = listOf(
    HistoryItem.ExpenseItem(
        id = 1, date = LocalDate.parse("2026-09-02"), amountMinor = 4250, note = "Ramen",
        categoryId = 1, categoryName = "Food & Drink", categoryColorHex = "#2A78D6",
        tripId = 1, tripName = "Japan 2026",
    ),
    HistoryItem.IncomeItem(
        id = 1, date = LocalDate.parse("2026-09-01"), amountMinor = 250_000,
        note = null, source = "Salary",
    ),
    HistoryItem.ExpenseItem(
        id = 2, date = LocalDate.parse("2026-08-30"), amountMinor = 1299, note = null,
        categoryId = 2, categoryName = "Transport", categoryColorHex = "#EB6834",
        tripId = null, tripName = null,
    ),
)

@Preview(name = "History · light", showBackground = true)
@Preview(name = "History · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryPreview() {
    StonksTheme {
        HistoryScreen(
            state = HistoryUiState(
                items = previewItems,
                categories = listOf(Category(1, "Food & Drink", "#2A78D6")),
                trips = listOf(Trip(1, "Japan 2026")),
                loading = false,
            ),
            onAddClick = {}, onTypeChange = {}, onCategoryChange = {}, onTripChange = {},
            onPeriodChange = {}, onSortChange = {}, onClearFilters = {},
        )
    }
}

@Preview(name = "History · empty", showBackground = true)
@Composable
private fun HistoryEmptyPreview() {
    StonksTheme {
        HistoryScreen(
            state = HistoryUiState(loading = false),
            onAddClick = {}, onTypeChange = {}, onCategoryChange = {}, onTripChange = {},
            onPeriodChange = {}, onSortChange = {}, onClearFilters = {},
        )
    }
}

@Preview(name = "History · no matches", showBackground = true)
@Composable
private fun HistoryNoMatchesPreview() {
    StonksTheme {
        HistoryScreen(
            state = HistoryUiState(
                filter = HistoryFilter(type = HistoryType.INCOME),
                loading = false,
            ),
            onAddClick = {}, onTypeChange = {}, onCategoryChange = {}, onTripChange = {},
            onPeriodChange = {}, onSortChange = {}, onClearFilters = {},
        )
    }
}
