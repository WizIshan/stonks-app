package dev.wizishan.stonks.ui.entry

import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.local.entity.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validation is plain synchronous logic, so it is tested without a database, a
 * dispatcher, or a Compose runtime.
 */
class AddEntryUiStateTest {

    private val categories = listOf(
        Category(id = 1, name = "Food & Drink", colorHex = "#2A78D6"),
        Category(id = 2, name = "Transport", colorHex = "#EB6834"),
    )

    private fun expense(
        amount: String = "12.50",
        categoryId: Long? = 1,
    ) = AddEntryUiState(amountInput = amount, categoryId = categoryId, categories = categories)

    private fun income(amount: String = "12.50", source: String = "Salary") =
        AddEntryUiState(type = EntryType.INCOME, amountInput = amount, source = source)

    @Test
    fun `a complete expense can be saved`() {
        val state = expense()
        assertTrue(state.canSave)
        assertNull(state.amountError)
        assertEquals(1250L, state.amountMinor)
    }

    @Test
    fun `a complete income can be saved`() {
        assertTrue(income().canSave)
    }

    @Test
    fun `an expense needs a category`() {
        val state = expense(categoryId = null)
        assertTrue(state.categoryMissing)
        assertFalse(state.canSave)
    }

    @Test
    fun `income does not need a category`() {
        val state = AddEntryUiState(type = EntryType.INCOME, amountInput = "10", source = "Salary")
        assertFalse(state.categoryMissing)
        assertTrue(state.canSave)
    }

    @Test
    fun `income needs a source`() {
        val state = income(source = "   ")
        assertTrue(state.sourceMissing)
        assertFalse(state.canSave)
    }

    @Test
    fun `an expense does not need a source`() {
        assertFalse(expense().sourceMissing)
    }

    @Test
    fun `amount errors distinguish missing, unreadable and non-positive`() {
        assertEquals(AmountError.MISSING, expense(amount = "").amountError)
        assertEquals(AmountError.MISSING, expense(amount = "   ").amountError)
        assertEquals(AmountError.UNREADABLE, expense(amount = "abc").amountError)
        assertEquals(AmountError.UNREADABLE, expense(amount = "12.505").amountError)
        assertEquals(AmountError.NOT_POSITIVE, expense(amount = "0").amountError)
        assertEquals(AmountError.NOT_POSITIVE, expense(amount = "-5").amountError)
    }

    @Test
    fun `a zero or negative amount is never saveable`() {
        assertFalse(expense(amount = "0").canSave)
        assertFalse(expense(amount = "-5").canSave)
        assertNull(expense(amount = "-5").amountMinor)
    }

    @Test
    fun `saving in flight blocks a second save`() {
        assertFalse(expense().copy(saving = true).canSave)
    }

    @Test
    fun `the selected category and trip are resolved for display`() {
        val trips = listOf(Trip(id = 7, name = "Japan 2026"))
        val state = AddEntryUiState(
            amountInput = "10",
            categoryId = 2,
            tripId = 7,
            categories = categories,
            trips = trips,
        )

        assertEquals("Transport", state.selectedCategory?.name)
        assertEquals("Japan 2026", state.selectedTrip?.name)
    }

    @Test
    fun `an expense with no trip resolves to null rather than failing`() {
        assertNull(expense().selectedTrip)
    }

    @Test
    fun `source suggestions narrow as you type and drop an exact match`() {
        val known = listOf("Salary", "Freelance", "Sale of bike")

        fun suggestionsFor(typed: String) =
            AddEntryUiState(type = EntryType.INCOME, source = typed, knownSources = known).sourceSuggestions

        assertEquals(known, suggestionsFor(""))
        assertEquals(listOf("Salary", "Sale of bike"), suggestionsFor("Sa"))
        assertEquals(listOf("Salary", "Sale of bike"), suggestionsFor("sa"))
        assertEquals(listOf("Freelance"), suggestionsFor("Free"))
        // Once it matches exactly there is nothing left to suggest.
        assertEquals(emptyList<String>(), suggestionsFor("Freelance"))
    }
}
