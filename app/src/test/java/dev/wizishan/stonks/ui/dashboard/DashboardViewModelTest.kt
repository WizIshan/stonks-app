package dev.wizishan.stonks.ui.dashboard

import dev.wizishan.stonks.data.local.DatabaseTest
import dev.wizishan.stonks.data.repository.FinanceRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest : DatabaseTest() {

    /** See HistoryViewModelTest — stateIn on Main needs the test to share its scheduler. */
    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: FinanceRepository
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUpViewModel() {
        Dispatchers.setMain(mainDispatcher)
        repository = FinanceRepository(db.categoryDao(), db.tripDao(), db.expenseDao(), db.incomeDao())
        viewModel = DashboardViewModel(repository)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    private fun TestScope.subscribe() {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private fun today() = LocalDate.now()

    @Test
    fun `an untouched month reports empty`() = runTest(mainDispatcher) {
        subscribe()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `income alone is enough to stop the month reading as empty`() = runTest(mainDispatcher) {
        subscribe()
        repository.addIncome(250_000, today(), "Salary")

        val state = viewModel.uiState.value
        assertFalse(state.isEmpty)
        assertEquals(0, state.data.spendMinor)
        assertEquals(250_000, state.data.incomeMinor)
    }

    @Test
    fun `totals and net come from this month`() = runTest(mainDispatcher) {
        subscribe()
        repository.addExpense(4250, today(), seededCategoryId("Food & Drink"))
        repository.addIncome(250_000, today(), "Salary")

        val data = viewModel.uiState.value.data
        assertEquals(4250, data.spendMinor)
        assertEquals(250_000, data.incomeMinor)
        assertEquals(245_750, data.netMinor)
    }

    @Test
    fun `category breakdown is ranked and carries each category's own colour`() = runTest(mainDispatcher) {
        subscribe()
        repository.addExpense(1000, today(), seededCategoryId("Food & Drink"))
        repository.addExpense(9000, today(), seededCategoryId("Transport"))

        val slices = viewModel.uiState.value.data.byCategory
        assertEquals(listOf("Transport", "Food & Drink"), slices.map { it.label })
        assertEquals("#EB6834", slices.first().colorHex)
    }

    @Test
    fun `stepping back a month changes what is summarised`() = runTest(mainDispatcher) {
        subscribe()
        val lastMonth = today().minusMonths(1)
        repository.addExpense(7777, lastMonth, seededCategoryId("Food & Drink"))
        repository.addExpense(1000, today(), seededCategoryId("Food & Drink"))

        assertEquals(1000, viewModel.uiState.value.data.spendMinor)

        viewModel.previousMonth()

        assertEquals(YearMonth.from(lastMonth), viewModel.uiState.value.data.month)
        assertEquals(7777, viewModel.uiState.value.data.spendMinor)
    }

    @Test
    fun `the current month cannot step forward into an empty future`() = runTest(mainDispatcher) {
        subscribe()
        val startingMonth = viewModel.uiState.value.data.month
        assertFalse(viewModel.uiState.value.canGoForward)

        viewModel.nextMonth()

        assertEquals(startingMonth, viewModel.uiState.value.data.month)
    }

    @Test
    fun `stepping back then forward returns to this month`() = runTest(mainDispatcher) {
        subscribe()
        val startingMonth = viewModel.uiState.value.data.month

        viewModel.previousMonth()
        assertTrue(viewModel.uiState.value.canGoForward)
        viewModel.nextMonth()

        assertEquals(startingMonth, viewModel.uiState.value.data.month)
    }

    @Test
    fun `trip totals are not clipped to the selected month`() = runTest(mainDispatcher) {
        subscribe()
        val tripId = repository.addTrip("Japan 2026")
        val foodId = seededCategoryId("Food & Drink")
        repository.addExpense(5000, today().minusMonths(1), foodId, tripId)
        repository.addExpense(3000, today(), foodId, tripId)

        // A trip spans whatever dates it spans; reporting a fraction of it as the trip
        // would be a worse answer than reporting all of it.
        assertEquals(8000, viewModel.uiState.value.data.byTrip.single().amountMinor)
    }

    @Test
    fun `the trend covers twelve months ending at the selected one`() = runTest(mainDispatcher) {
        subscribe()

        val trend = viewModel.uiState.value.data.trend
        assertEquals(12, trend.size)
        assertEquals(viewModel.uiState.value.data.month, trend.last().month)
    }
}
