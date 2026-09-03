package dev.wizishan.stonks.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wizishan.stonks.core.CategorySlot
import dev.wizishan.stonks.core.CategorySlots
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.repository.AddCategoryResult
import dev.wizishan.stonks.data.repository.FinanceRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A category with what it is carrying, so the list can show it and a delete can warn. */
data class CategoryRow(
    val id: Long,
    val name: String,
    val colorHex: String,
    val expenseCount: Int,
    val recurringRuleCount: Int,
    val hasBudget: Boolean,
) {
    val isEmpty: Boolean get() = expenseCount == 0 && recurringRuleCount == 0 && !hasBudget
}

/** The add/edit sheet. A null [id] is a new category. */
data class CategoryEditor(
    val id: Long? = null,
    val name: String = "",
    val colorHex: String = CategorySlots.all.first().lightHex,
    val validationVisible: Boolean = false,
    val nameTaken: Boolean = false,
) {
    val isNew: Boolean get() = id == null
    val nameBlank: Boolean get() = name.isBlank()
    val nameError: Boolean get() = (validationVisible && nameBlank) || nameTaken
    val canSave: Boolean get() = !nameBlank
}

data class CategoriesUiState(
    val categories: List<CategoryRow> = emptyList(),
    val loading: Boolean = true,
    val editor: CategoryEditor? = null,
    val pendingDelete: CategoryRow? = null,
) {
    /** Somewhere to move entries when a category is deleted. */
    fun reassignTargets(excluding: Long): List<CategoryRow> = categories.filter { it.id != excluding }

    val canDeleteAny: Boolean get() = categories.size > 1
}

sealed interface CategoriesEvent {
    data object NoFreeSlot : CategoriesEvent
    data object LastCategory : CategoriesEvent
}

class CategoriesViewModel(
    private val repository: FinanceRepository,
) : ViewModel() {

    private val editor = MutableStateFlow<CategoryEditor?>(null)
    private val pendingDelete = MutableStateFlow<CategoryRow?>(null)

    private val _events = Channel<CategoriesEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<CategoriesUiState> = combine(
        repository.observeCategories(),
        repository.observeCategoryUsage(),
        editor,
        pendingDelete,
    ) { categories, usage, editor, pending ->
        val usageById = usage.associateBy { it.categoryId }
        CategoriesUiState(
            categories = categories.map { category ->
                val counts = usageById[category.id]
                CategoryRow(
                    id = category.id,
                    name = category.name,
                    colorHex = category.colorHex,
                    expenseCount = counts?.expenseCount ?: 0,
                    recurringRuleCount = counts?.recurringRuleCount ?: 0,
                    hasBudget = (counts?.budgetCount ?: 0) > 0,
                )
            },
            loading = false,
            editor = editor,
            pendingDelete = pending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(StopTimeoutMillis),
        initialValue = CategoriesUiState(),
    )

    /**
     * Opens the sheet on the next unused built-in colour.
     *
     * Any colour is allowed, but defaulting to a validated one means leaving the picker
     * alone still produces a set that reads well together.
     */
    fun startNew() {
        val used = uiState.value.categories.map { it.colorHex }
        val slot: CategorySlot = CategorySlots.nextFree(used) ?: CategorySlots.all.first()
        editor.update { CategoryEditor(colorHex = slot.lightHex) }
    }

    fun startEditing(row: CategoryRow) = editor.update {
        CategoryEditor(id = row.id, name = row.name, colorHex = row.colorHex)
    }

    fun cancelEditing() = editor.update { null }

    fun setEditorName(name: String) =
        editor.update { it?.copy(name = name, nameTaken = false) }

    fun setEditorColor(colorHex: String) = editor.update { it?.copy(colorHex = colorHex) }

    fun saveEditor() {
        val current = editor.value ?: return
        if (!current.canSave) {
            editor.update { it?.copy(validationVisible = true) }
            return
        }

        viewModelScope.launch {
            val result = if (current.id == null) {
                repository.addCategoryWithColor(current.name, current.colorHex)
            } else {
                repository.updateCategory(current.id, current.name, current.colorHex)
            }

            when (result) {
                is AddCategoryResult.Created -> editor.update { null }
                // Kept open with the clash marked, rather than closed with the edit lost.
                AddCategoryResult.NameTaken -> editor.update { it?.copy(nameTaken = true) }
                AddCategoryResult.InvalidName ->
                    editor.update { it?.copy(validationVisible = true) }

                AddCategoryResult.NoFreeSlot -> {
                    editor.update { null }
                    _events.send(CategoriesEvent.NoFreeSlot)
                }
            }
        }
    }

    fun requestDelete(row: CategoryRow) {
        // Every expense needs a category, so the last one cannot go.
        if (!uiState.value.canDeleteAny) {
            viewModelScope.launch { _events.send(CategoriesEvent.LastCategory) }
            return
        }
        pendingDelete.update { row }
    }

    fun cancelDelete() = pendingDelete.update { null }

    fun confirmDelete(moveToCategoryId: Long) {
        val row = pendingDelete.value ?: return
        pendingDelete.update { null }
        editor.update { null }
        viewModelScope.launch { repository.reassignAndDeleteCategory(row.id, moveToCategoryId) }
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}
