package dev.wizishan.stonks.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.local.entity.RecurringType
import dev.wizishan.stonks.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A rule as the list renders it, with its category name and colour already resolved. */
data class RecurringRuleRow(
    val id: Long,
    val title: String,
    val colorHex: String?,
    val amountMinor: Long,
    val frequency: RecurringFrequency,
    val nextDueDate: LocalDate,
    val isActive: Boolean,
    val isExpense: Boolean,
)

data class RecurringUiState(
    val rules: List<RecurringRuleRow> = emptyList(),
    val loading: Boolean = true,
    val pendingDelete: RecurringRuleRow? = null,
) {
    val isEmpty: Boolean get() = !loading && rules.isEmpty()
}

class RecurringViewModel(
    private val repository: FinanceRepository,
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<RecurringRuleRow?>(null)

    val uiState: StateFlow<RecurringUiState> = combine(
        repository.observeRecurringRules(),
        repository.observeCategories(),
        pendingDelete,
    ) { rules, categories, pending ->
        val categoriesById = categories.associateBy { it.id }
        RecurringUiState(
            rules = rules.map { rule ->
                val category = rule.categoryId?.let(categoriesById::get)
                RecurringRuleRow(
                    id = rule.id,
                    // An expense rule is named by its category, an income rule by its
                    // source; neither can be null for its own type, but a rule whose
                    // category vanished should still render rather than crash the list.
                    title = category?.name ?: rule.source.orEmpty(),
                    colorHex = category?.colorHex,
                    amountMinor = rule.amountMinor,
                    frequency = rule.frequency,
                    nextDueDate = rule.nextDueDate,
                    isActive = rule.isActive,
                    isExpense = rule.type == RecurringType.EXPENSE,
                )
            },
            loading = false,
            pendingDelete = pending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(StopTimeoutMillis),
        initialValue = RecurringUiState(),
    )

    fun setActive(id: Long, active: Boolean) {
        viewModelScope.launch { repository.setRuleActive(id, active) }
    }

    fun requestDelete(row: RecurringRuleRow) = pendingDelete.update { row }

    fun cancelDelete() = pendingDelete.update { null }

    fun confirmDelete() {
        val row = pendingDelete.value ?: return
        pendingDelete.update { null }
        viewModelScope.launch { repository.deleteRule(row.id) }
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}
