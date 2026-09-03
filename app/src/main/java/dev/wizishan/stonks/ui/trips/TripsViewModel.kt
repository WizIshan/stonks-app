package dev.wizishan.stonks.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.wizishan.stonks.data.repository.FinanceRepository
import dev.wizishan.stonks.data.repository.TripResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TripRow(
    val id: Long,
    val name: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val expenseCount: Int,
)

/** The add/edit sheet. A null [id] is a new trip. */
data class TripEditor(
    val id: Long? = null,
    val name: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val validationVisible: Boolean = false,
    val nameTaken: Boolean = false,
) {
    val isNew: Boolean get() = id == null
    val nameBlank: Boolean get() = name.isBlank()
    val nameError: Boolean get() = (validationVisible && nameBlank) || nameTaken

    /**
     * Dates are optional, but a range that runs backwards is a typo rather than a choice.
     */
    val datesInverted: Boolean
        get() = startDate != null && endDate != null && endDate.isBefore(startDate)

    val canSave: Boolean get() = !nameBlank && !datesInverted
}

data class TripsUiState(
    val trips: List<TripRow> = emptyList(),
    val loading: Boolean = true,
    val editor: TripEditor? = null,
    val pendingDelete: TripRow? = null,
) {
    val isEmpty: Boolean get() = !loading && trips.isEmpty()
}

class TripsViewModel(
    private val repository: FinanceRepository,
) : ViewModel() {

    private val editor = MutableStateFlow<TripEditor?>(null)
    private val pendingDelete = MutableStateFlow<TripRow?>(null)

    val uiState: StateFlow<TripsUiState> = combine(
        repository.observeTrips(),
        repository.observeTripUsage(),
        editor,
        pendingDelete,
    ) { trips, usage, editor, pending ->
        val usageById = usage.associateBy { it.tripId }
        TripsUiState(
            trips = trips.map { trip ->
                TripRow(
                    id = trip.id,
                    name = trip.name,
                    startDate = trip.startDate,
                    endDate = trip.endDate,
                    expenseCount = usageById[trip.id]?.expenseCount ?: 0,
                )
            },
            loading = false,
            editor = editor,
            pendingDelete = pending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(StopTimeoutMillis),
        initialValue = TripsUiState(),
    )

    fun startNew() = editor.update { TripEditor() }

    fun startEditing(row: TripRow) = editor.update {
        TripEditor(id = row.id, name = row.name, startDate = row.startDate, endDate = row.endDate)
    }

    fun cancelEditing() = editor.update { null }

    fun setEditorName(name: String) = editor.update { it?.copy(name = name, nameTaken = false) }

    fun setEditorStart(date: LocalDate?) = editor.update { it?.copy(startDate = date) }

    fun setEditorEnd(date: LocalDate?) = editor.update { it?.copy(endDate = date) }

    fun saveEditor() {
        val current = editor.value ?: return
        if (!current.canSave) {
            editor.update { it?.copy(validationVisible = true) }
            return
        }

        viewModelScope.launch {
            val result = if (current.id == null) {
                repository.addTripChecked(current.name, current.startDate, current.endDate)
            } else {
                repository.updateTrip(current.id, current.name, current.startDate, current.endDate)
            }

            when (result) {
                is TripResult.Saved -> editor.update { null }
                // Kept open with the clash marked, rather than closed with the edit lost.
                TripResult.NameTaken -> editor.update { it?.copy(nameTaken = true) }
                TripResult.InvalidName -> editor.update { it?.copy(validationVisible = true) }
            }
        }
    }

    fun requestDelete(row: TripRow) = pendingDelete.update { row }

    fun cancelDelete() = pendingDelete.update { null }

    fun confirmDelete() {
        val row = pendingDelete.value ?: return
        pendingDelete.update { null }
        editor.update { null }
        // No reassignment: the foreign key is SET_NULL, so the expenses just stop being
        // tagged. A trip is a grouping, not something an expense needs one of.
        viewModelScope.launch { repository.deleteTrip(row.id) }
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}
