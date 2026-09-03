package dev.wizishan.stonks.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wizishan.stonks.core.Money
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.recurring.RecurringGenerator
import dev.wizishan.stonks.data.repository.AddCategoryResult
import dev.wizishan.stonks.data.repository.FinanceRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class AddEntryViewModel(
    private val repository: FinanceRepository,
    private val recurringGenerator: RecurringGenerator,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val editingId: Long? = savedStateHandle.get<Long>(ENTRY_ID_ARG)?.takeIf { it >= 0 }

    /**
     * Which table the row being edited came from.
     *
     * Carried on the route rather than looked up: expenses and income both autogenerate
     * ids from 1, so an id alone does not say which table it belongs to.
     */
    private val editingType: EntryType = savedStateHandle.get<String>(ENTRY_TYPE_ARG)
        ?.let { runCatching { EntryType.valueOf(it) }.getOrNull() }
        ?: EntryType.EXPENSE

    private val _uiState = MutableStateFlow(AddEntryUiState())
    val uiState: StateFlow<AddEntryUiState> = _uiState.asStateFlow()

    /**
     * One-shot results (a confirmation, a failure) delivered as events rather than state.
     *
     * A snackbar held in state would fire again on every rotation; a channel is consumed
     * exactly once.
     */
    private val _events = Channel<AddEntryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (editingId != null) loadForEditing(editingId, editingType)

        viewModelScope.launch {
            combine(
                repository.observeCategories(),
                repository.observeTrips(),
                repository.observeIncomeSources(),
            ) { categories, trips, sources -> Triple(categories, trips, sources) }
                .collect { (categories, trips, sources) ->
                    _uiState.update {
                        it.copy(categories = categories, trips = trips, knownSources = sources)
                    }
                }
        }
    }

    private fun loadForEditing(id: Long, type: EntryType) {
        viewModelScope.launch {
            if (type == EntryType.EXPENSE) {
                val expense = repository.getExpense(id) ?: return@launch
                _uiState.update {
                    it.copy(
                        editingId = id,
                        type = EntryType.EXPENSE,
                        amountInput = Money.toMajor(expense.amountMinor).toPlainString(),
                        date = expense.date,
                        categoryId = expense.categoryId,
                        tripId = expense.tripId,
                        note = expense.note.orEmpty(),
                    )
                }
            } else {
                val income = repository.getIncome(id) ?: return@launch
                _uiState.update {
                    it.copy(
                        editingId = id,
                        type = EntryType.INCOME,
                        amountInput = Money.toMajor(income.amountMinor).toPlainString(),
                        date = income.date,
                        source = income.source,
                        note = income.note.orEmpty(),
                    )
                }
            }
        }
    }

    /** Switching type is not offered while editing; an entry does not change table. */
    fun setType(type: EntryType) = _uiState.update {
        if (it.isEditing) it else it.copy(type = type)
    }

    fun setAmount(input: String) = _uiState.update { it.copy(amountInput = input) }

    fun setDate(date: LocalDate) = _uiState.update { it.copy(date = date) }

    fun setCategory(categoryId: Long) = _uiState.update { it.copy(categoryId = categoryId) }

    /** Passing null clears the trip — an expense does not have to belong to one. */
    fun setTrip(tripId: Long?) = _uiState.update { it.copy(tripId = tripId) }

    fun setSource(source: String) = _uiState.update { it.copy(source = source) }

    fun setNote(note: String) = _uiState.update { it.copy(note = note) }

    /** Null makes this a one-off entry again. */
    fun setFrequency(frequency: RecurringFrequency?) =
        _uiState.update { it.copy(frequency = frequency) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) {
            _uiState.update { it.copy(validationVisible = true) }
            return
        }

        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val amountMinor = requireNotNull(state.amountMinor)
            runCatching {
                when {
                    state.editingId != null -> saveEdit(state, amountMinor)
                    state.frequency != null -> saveRecurring(state, amountMinor)
                    state.isExpense -> repository.addExpense(
                        amountMinor = amountMinor,
                        date = state.date,
                        categoryId = requireNotNull(state.categoryId),
                        tripId = state.tripId,
                        note = state.note,
                    )

                    else -> repository.addIncome(
                        amountMinor = amountMinor,
                        date = state.date,
                        source = state.source,
                        note = state.note,
                    )
                }
            }.onSuccess {
                if (state.editingId != null) {
                    // An edit is finished once saved, so the form keeps its values and the
                    // screen closes, rather than clearing itself ready for another entry.
                    _uiState.update { it.copy(saving = false) }
                    _events.send(AddEntryEvent.Updated(state.type, amountMinor))
                    return@launch
                }
                // Keep the type, date, category and trip: logging several entries from one
                // receipt or one trip day is the common case, and retyping them each time
                // is the kind of friction that stops someone using a tracker at all.
                _uiState.update {
                    it.copy(
                        amountInput = "",
                        note = "",
                        saving = false,
                        validationVisible = false,
                    )
                }
                _events.send(AddEntryEvent.Saved(state.type, amountMinor, state.isRecurring))
            }.onFailure { error ->
                _uiState.update { it.copy(saving = false) }
                _events.send(AddEntryEvent.SaveFailed(error.message))
            }
        }
    }

    fun startNewCategory() = _uiState.update { it.copy(newCategoryName = "") }

    fun setNewCategoryName(name: String) =
        _uiState.update { it.copy(newCategoryName = name) }

    fun cancelNewCategory() = _uiState.update { it.copy(newCategoryName = null) }

    /**
     * Create a category from the entry form and select it straight away.
     *
     * Takes the next free slot without asking. Someone halfway through logging an expense
     * wants the category to exist, not a colour decision; the colour is changeable in
     * Settings afterwards. Once all eight are used this needs a choice, so it hands off
     * to the Categories screen rather than picking a duplicate on their behalf.
     */
    fun confirmNewCategory() {
        val name = _uiState.value.newCategoryName?.trim().orEmpty()
        if (name.isEmpty()) return

        viewModelScope.launch {
            when (val result = repository.addCategory(name)) {
                is AddCategoryResult.Created -> {
                    _uiState.update { it.copy(newCategoryName = null, categoryId = result.id) }
                }

                AddCategoryResult.NameTaken -> {
                    // It already exists, which is what the user wanted anyway — select it.
                    val existing = repository.findCategoryByName(name)
                    _uiState.update { it.copy(newCategoryName = null, categoryId = existing?.id ?: it.categoryId) }
                }

                AddCategoryResult.NoFreeSlot -> {
                    _uiState.update { it.copy(newCategoryName = null) }
                    _events.send(AddEntryEvent.NoFreeCategorySlot)
                }

                AddCategoryResult.InvalidName -> _uiState.update { it.copy(newCategoryName = null) }
            }
        }
    }

    fun requestDelete() = _uiState.update { it.copy(deleteRequested = true) }

    fun cancelDelete() = _uiState.update { it.copy(deleteRequested = false) }

    fun confirmDelete() {
        val state = _uiState.value
        val id = state.editingId ?: return
        _uiState.update { it.copy(deleteRequested = false, saving = true) }
        viewModelScope.launch {
            if (state.isExpense) repository.deleteExpense(id) else repository.deleteIncome(id)
            _uiState.update { it.copy(saving = false) }
            _events.send(AddEntryEvent.Deleted)
        }
    }

    private suspend fun saveEdit(state: AddEntryUiState, amountMinor: Long) {
        val id = requireNotNull(state.editingId)
        if (state.isExpense) {
            repository.updateExpense(
                id = id,
                amountMinor = amountMinor,
                date = state.date,
                categoryId = requireNotNull(state.categoryId),
                tripId = state.tripId,
                note = state.note,
            )
        } else {
            repository.updateIncome(
                id = id,
                amountMinor = amountMinor,
                date = state.date,
                source = state.source,
                note = state.note,
            )
        }
    }

    /**
     * Creating a rule generates today's entry through the generator rather than writing it
     * here, so a repeating entry is produced by exactly the same path on the day it is
     * created as on every day after.
     */
    private suspend fun saveRecurring(state: AddEntryUiState, amountMinor: Long) {
        val frequency = requireNotNull(state.frequency)
        if (state.isExpense) {
            repository.addRecurringExpense(
                amountMinor = amountMinor,
                startDate = state.date,
                categoryId = requireNotNull(state.categoryId),
                frequency = frequency,
                tripId = state.tripId,
                note = state.note,
            )
        } else {
            repository.addRecurringIncome(
                amountMinor = amountMinor,
                startDate = state.date,
                source = state.source,
                frequency = frequency,
                note = state.note,
            )
        }
        recurringGenerator.generateDue()
    }

    companion object {
        const val ENTRY_ID_ARG = "entryId"
        const val ENTRY_TYPE_ARG = "entryType"
    }
}

sealed interface AddEntryEvent {
    data class Saved(
        val type: EntryType,
        val amountMinor: Long,
        val recurring: Boolean,
    ) : AddEntryEvent

    data class Updated(val type: EntryType, val amountMinor: Long) : AddEntryEvent

    data object Deleted : AddEntryEvent

    data object NoFreeCategorySlot : AddEntryEvent

    data class SaveFailed(val message: String?) : AddEntryEvent
}
