package dev.wizishan.stonks.ui.history

import dev.wizishan.stonks.data.local.DatabaseTest
import dev.wizishan.stonks.data.repository.FinanceRepository
import dev.wizishan.stonks.data.repository.HistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryDeleteTest : DatabaseTest() {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: FinanceRepository
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUpViewModel() {
        Dispatchers.setMain(mainDispatcher)
        repository = repository()
        viewModel = HistoryViewModel(repository)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    private fun TestScope.subscribe() {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private suspend fun seedExpense() =
        repository.addExpense(4250, LocalDate.now(), seededCategoryId("Food & Drink"))

    @Test
    fun `requesting a delete does not remove anything yet`() = runTest(mainDispatcher) {
        subscribe()
        seedExpense()
        val item = viewModel.uiState.value.items.single()

        viewModel.requestDelete(item)

        assertNotNull("the dialog should be asking", viewModel.uiState.value.pendingDelete)
        assertEquals("nothing is deleted until it is confirmed", 1, viewModel.uiState.value.items.size)
    }

    @Test
    fun `cancelling leaves the entry alone`() = runTest(mainDispatcher) {
        subscribe()
        seedExpense()
        viewModel.requestDelete(viewModel.uiState.value.items.single())

        viewModel.cancelDelete()

        assertNull(viewModel.uiState.value.pendingDelete)
        assertEquals(1, viewModel.uiState.value.items.size)
    }

    @Test
    fun `confirming removes the expense and closes the dialog`() = runTest(mainDispatcher) {
        subscribe()
        seedExpense()
        viewModel.requestDelete(viewModel.uiState.value.items.single())

        viewModel.confirmDelete()

        assertNull(viewModel.uiState.value.pendingDelete)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun `deleting income works the same way`() = runTest(mainDispatcher) {
        subscribe()
        repository.addIncome(250_000, LocalDate.now(), "Salary")
        val item = viewModel.uiState.value.items.single()
        assertTrue(item is HistoryItem.IncomeItem)

        viewModel.requestDelete(item)
        viewModel.confirmDelete()

        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun `only the requested entry is removed`() = runTest(mainDispatcher) {
        subscribe()
        val foodId = seededCategoryId("Food & Drink")
        repository.addExpense(1000, LocalDate.parse("2026-09-01"), foodId)
        repository.addExpense(2000, LocalDate.parse("2026-09-02"), foodId)
        repository.addIncome(3000, LocalDate.parse("2026-09-03"), "Salary")

        val target = viewModel.uiState.value.items.first { it.amountMinor == 2000L }
        viewModel.requestDelete(target)
        viewModel.confirmDelete()

        assertEquals(listOf(3000L, 1000L), viewModel.uiState.value.items.map { it.amountMinor })
    }

    @Test
    fun `confirming with nothing pending is a no-op`() = runTest(mainDispatcher) {
        subscribe()
        seedExpense()

        viewModel.confirmDelete()

        assertEquals(1, viewModel.uiState.value.items.size)
    }

    @Test
    fun `deleting an entry that is already gone does not fail`() = runTest(mainDispatcher) {
        subscribe()
        seedExpense()
        val item = viewModel.uiState.value.items.single()

        viewModel.requestDelete(item)
        viewModel.confirmDelete()
        // The same stale row, requested again from a list that no longer has it.
        viewModel.requestDelete(item)
        viewModel.confirmDelete()

        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun `an expense and an income sharing an id do not delete each other`() = runTest(mainDispatcher) {
        subscribe()
        // Both tables autogenerate from 1, so the first row of each shares id 1.
        repository.addExpense(1000, LocalDate.now(), seededCategoryId("Food & Drink"))
        repository.addIncome(2000, LocalDate.now(), "Salary")

        val income = viewModel.uiState.value.items.filterIsInstance<HistoryItem.IncomeItem>().single()
        assertEquals(1, income.id)
        viewModel.requestDelete(income)
        viewModel.confirmDelete()

        val remaining = viewModel.uiState.value.items.single()
        assertTrue(remaining is HistoryItem.ExpenseItem)
        assertEquals(1000, remaining.amountMinor)
    }
}
