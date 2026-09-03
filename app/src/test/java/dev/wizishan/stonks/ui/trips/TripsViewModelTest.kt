package dev.wizishan.stonks.ui.trips

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModelTest : DatabaseTest() {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FinanceRepository
    private lateinit var viewModel: TripsViewModel

    @Before
    fun setUpViewModel() {
        Dispatchers.setMain(mainDispatcher)
        repository = repository()
        viewModel = TripsViewModel(repository)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    private fun TestScope.subscribe() {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private fun rowNamed(name: String) = viewModel.uiState.value.trips.single { it.name == name }

    @Test
    fun `a fresh install has no trips`() = runTest(mainDispatcher) {
        subscribe()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `creating a trip adds it and closes the sheet`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.startNew()

        viewModel.setEditorName("Japan 2026")
        viewModel.saveEditor()

        assertNull(viewModel.uiState.value.editor)
        assertEquals(1, viewModel.uiState.value.trips.size)
        assertEquals("Japan 2026", rowNamed("Japan 2026").name)
    }

    @Test
    fun `dates are optional`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.startNew()
        viewModel.setEditorName("Someday")

        viewModel.saveEditor()

        val row = rowNamed("Someday")
        assertNull(row.startDate)
        assertNull(row.endDate)
    }

    @Test
    fun `dates are stored when given`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.startNew()
        viewModel.setEditorName("Japan 2026")
        viewModel.setEditorStart(LocalDate.parse("2026-04-01"))
        viewModel.setEditorEnd(LocalDate.parse("2026-04-14"))

        viewModel.saveEditor()

        assertEquals(LocalDate.parse("2026-04-01"), rowNamed("Japan 2026").startDate)
        assertEquals(LocalDate.parse("2026-04-14"), rowNamed("Japan 2026").endDate)
    }

    @Test
    fun `an end date before the start is refused as a typo`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.startNew()
        viewModel.setEditorName("Backwards")
        viewModel.setEditorStart(LocalDate.parse("2026-04-14"))
        viewModel.setEditorEnd(LocalDate.parse("2026-04-01"))

        assertTrue(requireNotNull(viewModel.uiState.value.editor).datesInverted)
        assertFalse(requireNotNull(viewModel.uiState.value.editor).canSave)

        viewModel.saveEditor()
        assertTrue("nothing saved", viewModel.uiState.value.trips.isEmpty())
    }

    @Test
    fun `a start date on its own is fine for a trip still running`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.startNew()
        viewModel.setEditorName("Open ended")
        viewModel.setEditorStart(LocalDate.parse("2026-04-01"))

        assertTrue(requireNotNull(viewModel.uiState.value.editor).canSave)
        viewModel.saveEditor()

        assertNotNull(rowNamed("Open ended").startDate)
        assertNull(rowNamed("Open ended").endDate)
    }

    @Test
    fun `a duplicate name keeps the sheet open with the clash marked`() = runTest(mainDispatcher) {
        subscribe()
        repository.addTrip("Japan 2026")
        viewModel.startNew()

        viewModel.setEditorName("Japan 2026")
        viewModel.saveEditor()

        assertTrue(viewModel.uiState.value.editor?.nameTaken == true)
        assertEquals(1, viewModel.uiState.value.trips.size)
    }

    @Test
    fun `a blank name is refused`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.startNew()

        viewModel.setEditorName("  ")
        viewModel.saveEditor()

        assertTrue(viewModel.uiState.value.editor?.validationVisible == true)
        assertTrue(viewModel.uiState.value.trips.isEmpty())
    }

    @Test
    fun `usage counts the expenses tagged to each trip`() = runTest(mainDispatcher) {
        subscribe()
        val tripId = repository.addTrip("Japan 2026")
        val foodId = seededCategoryId("Food & Drink")
        repository.addExpense(1000, LocalDate.now(), foodId, tripId)
        repository.addExpense(2000, LocalDate.now(), foodId, tripId)
        repository.addExpense(3000, LocalDate.now(), foodId)

        assertEquals(2, rowNamed("Japan 2026").expenseCount)
    }

    @Test
    fun `renaming keeps the entries tagged`() = runTest(mainDispatcher) {
        subscribe()
        val tripId = repository.addTrip("Japan")
        repository.addExpense(1000, LocalDate.now(), seededCategoryId("Food & Drink"), tripId)

        viewModel.startEditing(rowNamed("Japan"))
        viewModel.setEditorName("Japan 2026")
        viewModel.saveEditor()

        assertEquals(1, rowNamed("Japan 2026").expenseCount)
        assertEquals(tripId, rowNamed("Japan 2026").id)
    }

    @Test
    fun `deleting a trip keeps its expenses and just untags them`() = runTest(mainDispatcher) {
        subscribe()
        val tripId = repository.addTrip("Japan 2026")
        val foodId = seededCategoryId("Food & Drink")
        val expenseId = repository.addExpense(4250, LocalDate.now(), foodId, tripId)

        viewModel.requestDelete(rowNamed("Japan 2026"))
        viewModel.confirmDelete()

        // SET_NULL, not RESTRICT: a trip is a grouping, not something an expense needs.
        assertTrue(viewModel.uiState.value.trips.isEmpty())
        val survivor = db.expenseDao().getById(expenseId)
        assertNotNull(survivor)
        assertNull(survivor?.tripId)
        assertEquals(4250L, survivor?.amountMinor)
    }

    @Test
    fun `cancelling a delete changes nothing`() = runTest(mainDispatcher) {
        subscribe()
        repository.addTrip("Japan 2026")

        viewModel.requestDelete(rowNamed("Japan 2026"))
        viewModel.cancelDelete()

        assertNull(viewModel.uiState.value.pendingDelete)
        assertEquals(1, viewModel.uiState.value.trips.size)
    }

    @Test
    fun `renaming to another trip's name is refused`() = runTest(mainDispatcher) {
        subscribe()
        repository.addTrip("Japan 2026")
        repository.addTrip("Lisbon 2025")

        viewModel.startEditing(rowNamed("Lisbon 2025"))
        viewModel.setEditorName("Japan 2026")
        viewModel.saveEditor()

        assertTrue(viewModel.uiState.value.editor?.nameTaken == true)
        assertNotNull(db.tripDao().getByName("Lisbon 2025"))
    }
}
