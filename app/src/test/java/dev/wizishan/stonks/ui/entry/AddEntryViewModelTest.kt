package dev.wizishan.stonks.ui.entry

import dev.wizishan.stonks.data.local.DatabaseTest
import dev.wizishan.stonks.data.local.dao.observeFiltered
import dev.wizishan.stonks.data.repository.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AddEntryViewModelTest : DatabaseTest() {

    private lateinit var viewModel: AddEntryViewModel

    @Before
    fun setUpViewModel() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = AddEntryViewModel(
            FinanceRepository(db.categoryDao(), db.tripDao(), db.expenseDao(), db.incomeDao())
        )
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the seeded categories load into state`() = runTest {
        assertEquals(8, viewModel.uiState.value.categories.size)
        assertEquals("Food & Drink", viewModel.uiState.value.categories.first { it.id == 1L }.name)
    }

    @Test
    fun `saving an incomplete form reveals validation instead of writing`() = runTest {
        viewModel.setAmount("")

        viewModel.save()

        assertTrue(viewModel.uiState.value.validationVisible)
        assertEquals(0, db.expenseDao().observeFiltered().first().size)
    }

    @Test
    fun `saving an expense writes it and clears the amount`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        viewModel.setAmount("42.50")
        viewModel.setCategory(foodId)
        viewModel.setDate(LocalDate.parse("2026-09-01"))
        viewModel.setNote("Ramen")

        viewModel.save()

        val row = db.expenseDao().observeFiltered().first().single()
        assertEquals(4250, row.amountMinor)
        assertEquals("Food & Drink", row.categoryName)
        assertEquals("Ramen", row.note)
        assertEquals("", viewModel.uiState.value.amountInput)
        assertEquals("", viewModel.uiState.value.note)
    }

    @Test
    fun `the type, date and category survive a save so the next entry is quick`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        val date = LocalDate.parse("2026-09-01")
        viewModel.setAmount("10")
        viewModel.setCategory(foodId)
        viewModel.setDate(date)

        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals(EntryType.EXPENSE, state.type)
        assertEquals(date, state.date)
        assertEquals(foodId, state.categoryId)
        assertFalse(state.saving)
    }

    @Test
    fun `saving income writes to the income table, not expenses`() = runTest {
        viewModel.setType(EntryType.INCOME)
        viewModel.setAmount("2500")
        viewModel.setSource("Salary")

        viewModel.save()

        assertEquals(0, db.expenseDao().observeFiltered().first().size)
        val row = db.incomeDao().observeFiltered().first().single()
        assertEquals(250_000, row.amountMinor)
        assertEquals("Salary", row.source)
    }

    @Test
    fun `switching to income makes a missing category irrelevant`() = runTest {
        viewModel.setAmount("10")
        assertFalse("no category picked yet", viewModel.uiState.value.canSave)

        viewModel.setType(EntryType.INCOME)
        viewModel.setSource("Salary")

        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun `clearing the trip is possible after setting one`() = runTest {
        val tripId = db.tripDao().insert(
            dev.wizishan.stonks.data.local.entity.Trip(name = "Japan 2026")
        )
        viewModel.setTrip(tripId)
        assertEquals(tripId, viewModel.uiState.value.tripId)

        viewModel.setTrip(null)

        assertNull(viewModel.uiState.value.tripId)
    }

    @Test
    fun `a new income source becomes a suggestion for the next entry`() = runTest {
        viewModel.setType(EntryType.INCOME)
        viewModel.setAmount("2500")
        viewModel.setSource("Freelance")
        viewModel.save()

        assertTrue("Freelance" in viewModel.uiState.value.knownSources)
    }
}
