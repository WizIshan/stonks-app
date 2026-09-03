package dev.wizishan.stonks.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.recurring.RecurringGenerator
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
) : ViewModel() {

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

    fun setType(type: EntryType) = _uiState.update { it.copy(type = type) }

    fun setAmount(input: String) = _uiState.update { it.copy(amountInput = input) }

    fun setDate(date: LocalDate) = _uiState.update { it.copy(date = date) }

    fun setCategory(categoryId: Long) = _uiState.update { it.copy(categoryId = categoryId) }

    /** Passing null clears the trip — an expense does not have to belong to one. */
    fun setTrip(tripId: Long?) = _uiState.update { it.copy(tripId = tripId) }

    fun setSource(source: String) = _uiState.update { it.copy(source = source) }

    fun setNote(note: String) = _uiState.update { it.copy(note = note) }

    /** Null makes this a one-off entry again. */
    fun setFrequency(frequency: RecurringFrequency?) = _uiState.update { it.copy(frequency = frequency) }

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
                if (state.frequency != null) {
                    saveRecurring(state, amountMinor)
                } else if (state.isExpense) {
                    repository.addExpense(
                        amountMinor = amountMinor,
                        date = state.date,
                        categoryId = requireNotNull(state.categoryId),
                        tripId = state.tripId,
                        note = state.note,
                    )
                } else {
                    repository.addIncome(
                        amountMinor = amountMinor,
                        date = state.date,
                        source = state.source,
                        note = state.note,
                    )
                }
            }.onSuccess {
                // Keep the type, date and trip: logging several entries from one receipt
                // or one trip day is the common case, and retyping them each time is the
                // kind of friction that stops someone using a tracker at all.
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
}

sealed interface AddEntryEvent {
    data class Saved(
        val type: EntryType,
        val amountMinor: Long,
        val recurring: Boolean,
    ) : AddEntryEvent
    data class SaveFailed(val message: String?) : AddEntryEvent
}
