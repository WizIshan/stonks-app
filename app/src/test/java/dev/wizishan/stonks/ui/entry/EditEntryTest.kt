package dev.wizishan.stonks.ui.entry

import androidx.lifecycle.SavedStateHandle
import dev.wizishan.stonks.data.local.DatabaseTest
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.repository.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class EditEntryTest : DatabaseTest() {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FinanceRepository

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(mainDispatcher)
        repository = repository()
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModelFor(id: Long, type: EntryType) = AddEntryViewModel(
        repository,
        recurringGenerator(),
        SavedStateHandle(
            mapOf(
                AddEntryViewModel.ENTRY_ID_ARG to id,
                AddEntryViewModel.ENTRY_TYPE_ARG to type.name,
            )
        ),
    )

    private fun addingViewModel() =
        AddEntryViewModel(repository, recurringGenerator(), SavedStateHandle())

    @Test
    fun `opening without an id is a blank form`() = runTest(mainDispatcher) {
        val viewModel = addingViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isEditing)
        assertEquals("", state.amountInput)
        assertTrue("repeat is offered when adding", state.repeatAvailable)
    }

    @Test
    fun `opening an expense fills the form from the row`() = runTest(mainDispatcher) {
        val foodId = seededCategoryId("Food & Drink")
        val tripId = repository.addTrip("Japan 2026")
        val id = repository.addExpense(4250, LocalDate.parse("2026-09-01"), foodId, tripId, "Ramen")

        val state = viewModelFor(id, EntryType.EXPENSE).uiState.value

        assertTrue(state.isEditing)
        assertEquals("42.50", state.amountInput)
        assertEquals(LocalDate.parse("2026-09-01"), state.date)
        assertEquals(foodId, state.categoryId)
        assertEquals(tripId, state.tripId)
        assertEquals("Ramen", state.note)
    }

    @Test
    fun `opening income fills the form from the income table`() = runTest(mainDispatcher) {
        val id = repository.addIncome(250_000, LocalDate.parse("2026-09-01"), "Salary")

        val state = viewModelFor(id, EntryType.INCOME).uiState.value

        assertEquals(EntryType.INCOME, state.type)
        assertEquals("2500.00", state.amountInput)
        assertEquals("Salary", state.source)
    }

    @Test
    fun `an expense and an income sharing an id are told apart by the route`() = runTest(mainDispatcher) {
        // Both tables number from 1, so the id alone is ambiguous.
        repository.addExpense(1000, LocalDate.now(), seededCategoryId("Food & Drink"))
        repository.addIncome(2000, LocalDate.now(), "Salary")

        assertEquals("10.00", viewModelFor(1, EntryType.EXPENSE).uiState.value.amountInput)
        assertEquals("20.00", viewModelFor(1, EntryType.INCOME).uiState.value.amountInput)
    }

    @Test
    fun `saving an edit updates the row instead of adding another`() = runTest(mainDispatcher) {
        val foodId = seededCategoryId("Food & Drink")
        val id = repository.addExpense(4250, LocalDate.parse("2026-09-01"), foodId)
        val viewModel = viewModelFor(id, EntryType.EXPENSE)

        viewModel.setAmount("99.99")
        viewModel.setNote("Corrected")
        viewModel.save()

        val all = db.expenseDao().getAll()
        assertEquals("no second row", 1, all.size)
        assertEquals(9999, all.single().amountMinor)
        assertEquals("Corrected", all.single().note)
        assertEquals(id, all.single().id)
    }

    @Test
    fun `an edit can move an entry to another category and date`() = runTest(mainDispatcher) {
        val foodId = seededCategoryId("Food & Drink")
        val transportId = seededCategoryId("Transport")
        val id = repository.addExpense(1000, LocalDate.parse("2026-09-01"), foodId)
        val viewModel = viewModelFor(id, EntryType.EXPENSE)

        viewModel.setCategory(transportId)
        viewModel.setDate(LocalDate.parse("2026-08-15"))
        viewModel.save()

        val row = db.expenseDao().getById(id)
        assertEquals(transportId, row?.categoryId)
        assertEquals(LocalDate.parse("2026-08-15"), row?.date)
    }

    @Test
    fun `clearing the note stores null rather than an empty string`() = runTest(mainDispatcher) {
        val id = repository.addExpense(1000, LocalDate.now(), seededCategoryId("Food & Drink"), note = "typo")
        val viewModel = viewModelFor(id, EntryType.EXPENSE)

        viewModel.setNote("")
        viewModel.save()

        assertNull(db.expenseDao().getById(id)?.note)
    }

    @Test
    fun `an edit keeps the link to the rule that generated the entry`() = runTest(mainDispatcher) {
        repository.addRecurringExpense(
            amountMinor = 95_000,
            startDate = LocalDate.parse("2026-09-01"),
            categoryId = seededCategoryId("Bills & Utilities"),
            frequency = RecurringFrequency.MONTHLY,
        )
        recurringGenerator().generateDue(LocalDate.parse("2026-09-01"))
        val generated = db.expenseDao().getAll().single()
        assertNotNull(generated.recurringRuleId)

        val viewModel = viewModelFor(generated.id, EntryType.EXPENSE)
        viewModel.setAmount("960.00")
        viewModel.save()

        // Losing the link would let the rule generate a duplicate for the same date.
        assertEquals(generated.recurringRuleId, db.expenseDao().getById(generated.id)?.recurringRuleId)
    }

    @Test
    fun `an invalid edit is refused and the row is untouched`() = runTest(mainDispatcher) {
        val id = repository.addExpense(4250, LocalDate.now(), seededCategoryId("Food & Drink"))
        val viewModel = viewModelFor(id, EntryType.EXPENSE)

        viewModel.setAmount("nonsense")
        viewModel.save()

        assertTrue(viewModel.uiState.value.validationVisible)
        assertEquals(4250L, db.expenseDao().getById(id)?.amountMinor)
    }

    @Test
    fun `deleting from the edit screen removes the entry`() = runTest(mainDispatcher) {
        val id = repository.addExpense(4250, LocalDate.now(), seededCategoryId("Food & Drink"))
        val viewModel = viewModelFor(id, EntryType.EXPENSE)

        viewModel.requestDelete()
        assertTrue(viewModel.uiState.value.deleteRequested)
        viewModel.confirmDelete()

        assertNull(db.expenseDao().getById(id))
    }

    @Test
    fun `cancelling the delete leaves the entry alone`() = runTest(mainDispatcher) {
        val id = repository.addExpense(4250, LocalDate.now(), seededCategoryId("Food & Drink"))
        val viewModel = viewModelFor(id, EntryType.EXPENSE)

        viewModel.requestDelete()
        viewModel.cancelDelete()

        assertFalse(viewModel.uiState.value.deleteRequested)
        assertNotNull(db.expenseDao().getById(id))
    }

    @Test
    fun `deleting income removes it from the income table only`() = runTest(mainDispatcher) {
        repository.addExpense(1000, LocalDate.now(), seededCategoryId("Food & Drink"))
        val incomeId = repository.addIncome(2000, LocalDate.now(), "Salary")
        val viewModel = viewModelFor(incomeId, EntryType.INCOME)

        viewModel.requestDelete()
        viewModel.confirmDelete()

        assertNull(db.incomeDao().getById(incomeId))
        assertEquals(1, db.expenseDao().getAll().size)
    }

    @Test
    fun `an entry cannot be switched between expense and income while editing`() = runTest(mainDispatcher) {
        val id = repository.addExpense(1000, LocalDate.now(), seededCategoryId("Food & Drink"))
        val viewModel = viewModelFor(id, EntryType.EXPENSE)

        viewModel.setType(EntryType.INCOME)

        assertEquals(EntryType.EXPENSE, viewModel.uiState.value.type)
    }

    @Test
    fun `repeat is not offered while editing`() = runTest(mainDispatcher) {
        val id = repository.addExpense(1000, LocalDate.now(), seededCategoryId("Food & Drink"))

        assertFalse(viewModelFor(id, EntryType.EXPENSE).uiState.value.repeatAvailable)
    }

    @Test
    fun `an id that no longer exists leaves a blank form rather than crashing`() = runTest(mainDispatcher) {
        val state = viewModelFor(999, EntryType.EXPENSE).uiState.value

        assertFalse(state.isEditing)
        assertEquals("", state.amountInput)
    }
}
