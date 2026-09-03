package dev.wizishan.stonks.ui.history

import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.local.entity.Trip
import dev.wizishan.stonks.data.repository.HistoryFilter
import dev.wizishan.stonks.data.repository.HistoryItem

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val filter: HistoryFilter = HistoryFilter(),
    val categories: List<Category> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val loading: Boolean = true,
    /** The row a delete has been requested for, awaiting confirmation. */
    val pendingDelete: HistoryItem? = null,
) {

    /**
     * Nothing has ever been logged, as opposed to nothing matching the current filters.
     *
     * The two need different empty states: one asks you to add your first expense, the
     * other to clear a filter. Offering "Add expense" to someone who has fifty entries
     * and a narrow filter would be useless (DESIGN.md §6).
     */
    val isEmptyOverall: Boolean get() = !loading && items.isEmpty() && !filter.isFiltered

    val isEmptyForFilter: Boolean get() = !loading && items.isEmpty() && filter.isFiltered

    val spendMinor: Long get() = items.filterIsInstance<HistoryItem.ExpenseItem>().sumOf { it.amountMinor }

    val incomeMinor: Long get() = items.filterIsInstance<HistoryItem.IncomeItem>().sumOf { it.amountMinor }

    /** Income minus spend for whatever is currently on screen. */
    val netMinor: Long get() = incomeMinor - spendMinor

    val selectedCategory: Category? get() = categories.firstOrNull { it.id == filter.categoryId }

    val selectedTrip: Trip? get() = trips.firstOrNull { it.id == filter.tripId }
}
