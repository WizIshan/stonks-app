package dev.wizishan.stonks.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wizishan.stonks.data.repository.DashboardData
import dev.wizishan.stonks.data.repository.FinanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

data class DashboardUiState(
    val data: DashboardData = DashboardData(),
    val loading: Boolean = true,
) {
    /**
     * Future months are not reachable.
     *
     * Nothing can be logged in them yet, so a "next" arrow would only ever lead to an
     * empty screen the user has to back out of.
     */
    val canGoForward: Boolean get() = data.month < YearMonth.now()

    val isEmpty: Boolean get() = !loading && !data.hasAnyActivity
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    repository: FinanceRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<DashboardUiState> = month
        .flatMapLatest { repository.observeDashboard(it) }
        .map { DashboardUiState(data = it, loading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(StopTimeoutMillis),
            initialValue = DashboardUiState(),
        )

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update {
        if (it < YearMonth.now()) it.plusMonths(1) else it
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}
