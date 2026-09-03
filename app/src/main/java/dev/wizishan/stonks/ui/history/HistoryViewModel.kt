package dev.wizishan.stonks.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wizishan.stonks.data.local.query.HistorySort
import dev.wizishan.stonks.data.repository.FinanceRepository
import dev.wizishan.stonks.data.repository.HistoryFilter
import dev.wizishan.stonks.data.repository.HistoryPeriod
import dev.wizishan.stonks.data.repository.HistoryType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    repository: FinanceRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter())

    private val items = filter.flatMapLatest { repository.observeHistory(it) }

    val uiState: StateFlow<HistoryUiState> = combine(
        filter,
        items,
        repository.observeCategories(),
        repository.observeTrips(),
    ) { filter, items, categories, trips ->
        HistoryUiState(
            items = items,
            filter = filter,
            categories = categories,
            trips = trips,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(StopTimeoutMillis),
        initialValue = HistoryUiState(),
    )

    /**
     * Changing the type can invalidate the sort: income has no category and no trip, so
     * those orderings stop meaning anything. Falling back to newest-first is quieter than
     * leaving a sort selected that no longer does what its label says.
     */
    fun setType(type: HistoryType) = filter.update { current ->
        val next = current.copy(type = type)
        if (next.sort in next.availableSorts) next else next.copy(sort = HistorySort.DATE_DESC)
    }

    fun setCategory(categoryId: Long?) = filter.update { it.copy(categoryId = categoryId) }

    fun setTrip(tripId: Long?) = filter.update { it.copy(tripId = tripId) }

    fun setPeriod(period: HistoryPeriod) = filter.update { it.copy(period = period) }

    fun setSort(sort: HistorySort) = filter.update { it.copy(sort = sort) }

    fun clearFilters() = filter.update { HistoryFilter(sort = it.sort) }

    private companion object {
        /** Keeps the query alive across a rotation instead of tearing it down and re-running. */
        const val StopTimeoutMillis = 5_000L
    }
}
