package dev.wizishan.stonks.ui.entry

import dev.wizishan.stonks.core.Money
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.local.entity.Trip
import java.time.LocalDate

enum class EntryType { EXPENSE, INCOME }

/**
 * Everything the Add screen renders, plus the validation derived from it.
 *
 * Validation lives here rather than in the ViewModel so it is plain, synchronous logic
 * that tests can exercise without a database, a dispatcher, or a Compose runtime.
 */
data class AddEntryUiState(
    val type: EntryType = EntryType.EXPENSE,
    val amountInput: String = "",
    val date: LocalDate = LocalDate.now(),
    val categoryId: Long? = null,
    val tripId: Long? = null,
    val source: String = "",
    val note: String = "",
    /** Null means a one-off entry; anything else creates a recurring rule instead. */
    val frequency: RecurringFrequency? = null,
    val categories: List<Category> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val knownSources: List<String> = emptyList(),
    /**
     * Set when an existing entry is open for correction rather than a new one being added.
     */
    val editingId: Long? = null,
    /** Errors stay hidden until the first save attempt, so an empty form is not shouting. */
    val validationVisible: Boolean = false,
    val deleteRequested: Boolean = false,
    val saving: Boolean = false,
) {

    /** Parsed amount, or null if the input isn't a usable positive amount. */
    val amountMinor: Long? = Money.parseOrNull(amountInput)?.takeIf { it > 0 }

    val isExpense: Boolean get() = type == EntryType.EXPENSE

    val isRecurring: Boolean get() = frequency != null

    val isEditing: Boolean get() = editingId != null

    /**
     * Repeat is only offered when adding.
     *
     * Turning an entry that already exists into a rule is ambiguous — is the entry the
     * first occurrence, or a separate one-off that now also repeats? Rules are created
     * from a new entry and managed on the Repeating screen.
     */
    val repeatAvailable: Boolean get() = !isEditing

    val amountError: AmountError? = when {
        amountInput.isBlank() -> AmountError.MISSING
        Money.parseOrNull(amountInput) == null -> AmountError.UNREADABLE
        amountMinor == null -> AmountError.NOT_POSITIVE
        else -> null
    }

    val categoryMissing: Boolean = isExpense && categoryId == null

    val sourceMissing: Boolean = !isExpense && source.isBlank()

    val canSave: Boolean =
        !saving && amountError == null && !categoryMissing && !sourceMissing

    /** The selected category, for showing its colour on the form. */
    val selectedCategory: Category? = categories.firstOrNull { it.id == categoryId }

    val selectedTrip: Trip? = trips.firstOrNull { it.id == tripId }

    /** Source suggestions narrowed to what has been typed so far. */
    val sourceSuggestions: List<String> =
        if (source.isBlank()) knownSources
        else knownSources.filter { it.startsWith(source, ignoreCase = true) && !it.equals(source, ignoreCase = true) }
}

enum class AmountError { MISSING, UNREADABLE, NOT_POSITIVE }
