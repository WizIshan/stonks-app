package dev.wizishan.stonks.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wizishan.stonks.core.Money
import dev.wizishan.stonks.data.budget.BudgetProgress
import dev.wizishan.stonks.data.local.entity.Budget
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/** The sheet for setting one budget. Null [categoryId] is the overall limit. */
data class BudgetEditor(
    val categoryId: Long? = null,
    val limitInput: String = "",
    val thresholdPercent: Int = Budget.DEFAULT_THRESHOLD_PERCENT,
    val existingBudgetId: Long? = null,
    val validationVisible: Boolean = false,
) {
    val limitMinor: Long? = Money.parseOrNull(limitInput)?.takeIf { it > 0 }
    val limitError: Boolean = validationVisible && limitMinor == null
    val canSave: Boolean = limitMinor != null
}

data class BudgetUiState(
    val progress: List<BudgetProgress> = emptyList(),
    val categories: List<Category> = emptyList(),
    val loading: Boolean = true,
    val editor: BudgetEditor? = null,
    val notificationsAllowed: Boolean = true,
) {
    val isEmpty: Boolean get() = !loading && progress.isEmpty()

    /**
     * Only nag about notifications once a budget exists.
     *
     * Asking for the permission on a screen with nothing to alert about is a prompt with
     * no reason attached, which is how people learn to deny them.
     */
    val showNotificationPrompt: Boolean get() = !notificationsAllowed && progress.isNotEmpty()

    /** Categories that do not already have a budget, plus whichever one is being edited. */
    fun availableCategories(editingCategoryId: Long?): List<Category> {
        val taken = progress.mapNotNull { it.categoryId }.toSet() - setOfNotNull(editingCategoryId)
        return categories.filter { it.id !in taken }
    }

    val hasOverallBudget: Boolean get() = progress.any { it.isOverall }
}

class BudgetViewModel(
    private val repository: FinanceRepository,
) : ViewModel() {

    private val editor = MutableStateFlow<BudgetEditor?>(null)
    private val notificationsAllowed = MutableStateFlow(true)

    val uiState: StateFlow<BudgetUiState> = combine(
        repository.observeBudgetProgress(YearMonth.now()),
        repository.observeCategories(),
        editor,
        notificationsAllowed,
    ) { progress, categories, editor, allowed ->
        BudgetUiState(
            progress = progress,
            categories = categories,
            loading = false,
            editor = editor,
            notificationsAllowed = allowed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(StopTimeoutMillis),
        initialValue = BudgetUiState(),
    )

    fun setNotificationsAllowed(allowed: Boolean) = notificationsAllowed.update { allowed }

    fun startNewBudget() = editor.update { BudgetEditor() }

    fun startEditing(progress: BudgetProgress) = editor.update {
        BudgetEditor(
            categoryId = progress.categoryId,
            limitInput = Money.toMajor(progress.limitMinor).toPlainString(),
            thresholdPercent = progress.thresholdPercent,
            existingBudgetId = progress.budgetId,
        )
    }

    fun cancelEditing() = editor.update { null }

    fun setEditorCategory(categoryId: Long?) = editor.update { it?.copy(categoryId = categoryId) }

    fun setEditorLimit(input: String) = editor.update { it?.copy(limitInput = input) }

    fun setEditorThreshold(percent: Int) = editor.update { it?.copy(thresholdPercent = percent) }

    fun saveEditor() {
        val current = editor.value ?: return
        if (!current.canSave) {
            editor.update { it?.copy(validationVisible = true) }
            return
        }
        editor.update { null }
        viewModelScope.launch {
            repository.setBudget(
                categoryId = current.categoryId,
                monthlyLimitMinor = requireNotNull(current.limitMinor),
                alertThresholdPercent = current.thresholdPercent,
            )
        }
    }

    fun deleteBudget(budgetId: Long) {
        editor.update { null }
        viewModelScope.launch { repository.deleteBudget(budgetId) }
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}
