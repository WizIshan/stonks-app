package dev.wizishan.stonks.ui.history

import dev.wizishan.stonks.data.local.DatabaseTest
import dev.wizishan.stonks.data.local.query.HistorySort
import dev.wizishan.stonks.data.repository.FinanceRepository
import dev.wizishan.stonks.data.repository.HistoryPeriod
import dev.wizishan.stonks.data.repository.HistoryType
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest : DatabaseTest() {

    /**
     * One dispatcher shared by Main and by `runTest`.
     *
     * uiState is produced with `stateIn` on viewModelScope, which runs on Main. If the
     * test body ran on a different TestDispatcher, the two schedulers would never advance
     * each other and the flow would sit on its initial value forever — so every test
     * passes this dispatcher to runTest.
     */
    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: FinanceRepository
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUpViewModel() {
        Dispatchers.setMain(mainDispatcher)
        repository = FinanceRepository(db.categoryDao(), db.tripDao(), db.expenseDao(), db.incomeDao())
        viewModel = HistoryViewModel(repository)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    /**
     * uiState is shared WhileSubscribed, so it stays cold until something collects it.
     * Every test needs a live subscriber before reading `.value`.
     */
    private fun TestScope.subscribe() {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    @Test
    fun `an empty database reports empty overall, not empty for a filter`() = runTest(mainDispatcher) {
        subscribe()

        val state = viewModel.uiState.value
        assertTrue(state.isEmptyOverall)
        assertFalse(state.isEmptyForFilter)
    }

    @Test
    fun `a filter that matches nothing reports empty for the filter instead`() = runTest(mainDispatcher) {
        subscribe()
        repository.addExpense(1000, LocalDate.now(), seededCategoryId("Food & Drink"))

        viewModel.setType(HistoryType.INCOME)

        val state = viewModel.uiState.value
        assertFalse(state.isEmptyOverall)
        assertTrue(state.isEmptyForFilter)
    }

    @Test
    fun `the summary nets income against spend for what is on screen`() = runTest(mainDispatcher) {
        subscribe()
        repository.addExpense(4250, LocalDate.now(), seededCategoryId("Food & Drink"))
        repository.addIncome(250_000, LocalDate.now(), "Salary")

        val state = viewModel.uiState.value
        assertEquals(4250, state.spendMinor)
        assertEquals(250_000, state.incomeMinor)
        assertEquals(245_750, state.netMinor)
    }

    @Test
    fun `switching to income drops a sort that no longer means anything`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.setSort(HistorySort.CATEGORY_ASC)
        assertEquals(HistorySort.CATEGORY_ASC, viewModel.uiState.value.filter.sort)

        viewModel.setType(HistoryType.INCOME)

        assertEquals(HistorySort.DATE_DESC, viewModel.uiState.value.filter.sort)
    }

    @Test
    fun `switching to income keeps a sort that still applies`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.setSort(HistorySort.AMOUNT_DESC)

        viewModel.setType(HistoryType.INCOME)

        assertEquals(HistorySort.AMOUNT_DESC, viewModel.uiState.value.filter.sort)
    }

    @Test
    fun `clearing filters keeps the chosen sort`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.setSort(HistorySort.AMOUNT_ASC)
        viewModel.setType(HistoryType.EXPENSES)
        viewModel.setPeriod(HistoryPeriod.THIS_MONTH)
        viewModel.setCategory(1)

        viewModel.clearFilters()

        val filter = viewModel.uiState.value.filter
        assertFalse(filter.isFiltered)
        assertNull(filter.categoryId)
        assertEquals(HistoryType.ALL, filter.type)
        assertEquals(HistorySort.AMOUNT_ASC, filter.sort)
    }

    @Test
    fun `categories and trips are exposed for the filter menus`() = runTest(mainDispatcher) {
        subscribe()
        repository.addTrip("Japan 2026")

        val state = viewModel.uiState.value
        assertEquals(8, state.categories.size)
        assertEquals(listOf("Japan 2026"), state.trips.map { it.name })
    }

    @Test
    fun `the selected category resolves for the chip label`() = runTest(mainDispatcher) {
        subscribe()
        val foodId = seededCategoryId("Food & Drink")

        viewModel.setCategory(foodId)

        assertEquals("Food & Drink", viewModel.uiState.value.selectedCategory?.name)
    }
}
