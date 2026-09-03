package dev.wizishan.stonks.ui.categories

import dev.wizishan.stonks.data.local.DatabaseTest
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
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
class CategoriesViewModelTest : DatabaseTest() {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FinanceRepository
    private lateinit var viewModel: CategoriesViewModel

    @Before
    fun setUpViewModel() {
        Dispatchers.setMain(mainDispatcher)
        repository = repository()
        viewModel = CategoriesViewModel(repository)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    private fun TestScope.subscribe() {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private fun rowNamed(name: String) =
        viewModel.uiState.value.categories.single { it.name == name }

    /** Free a slot so a new category has somewhere to go without picking a duplicate. */
    private suspend fun freeOneSlot() {
        val groceries = requireNotNull(db.categoryDao().getByName("Groceries"))
        db.categoryDao().delete(groceries)
    }

    @Test
    fun `the eight defaults are listed with what they are carrying`() = runTest(mainDispatcher) {
        subscribe()
        repository.addExpense(1000, LocalDate.now(), seededCategoryId("Food & Drink"))

        val state = viewModel.uiState.value
        assertEquals(8, state.categories.size)
        assertEquals(1, rowNamed("Food & Drink").expenseCount)
        assertTrue(rowNamed("Transport").isEmpty)
    }

    @Test
    fun `usage counts rules and budgets as well as entries`() = runTest(mainDispatcher) {
        subscribe()
        val transportId = seededCategoryId("Transport")
        repository.addRecurringExpense(
            amountMinor = 1000,
            startDate = LocalDate.now(),
            categoryId = transportId,
            frequency = RecurringFrequency.MONTHLY,
        )
        repository.setBudget(transportId, 50_000)

        val row = rowNamed("Transport")
        assertEquals(1, row.recurringRuleCount)
        assertTrue(row.hasBudget)
        assertFalse(row.isEmpty)
    }

    @Test
    fun `a new category takes the next free slot by default`() = runTest(mainDispatcher) {
        subscribe()
        freeOneSlot()

        viewModel.startNew()

        assertEquals("#008300", viewModel.uiState.value.editor?.colorHex)
    }

    @Test
    fun `creating a category adds it and closes the sheet`() = runTest(mainDispatcher) {
        subscribe()
        freeOneSlot()
        viewModel.startNew()

        viewModel.setEditorName("Coffee")
        viewModel.saveEditor()

        assertNull(viewModel.uiState.value.editor)
        assertEquals("#008300", rowNamed("Coffee").colorHex)
    }

    @Test
    fun `a duplicate name keeps the sheet open with the clash marked`() = runTest(mainDispatcher) {
        subscribe()
        freeOneSlot()
        viewModel.startNew()

        viewModel.setEditorName("Transport")
        viewModel.saveEditor()

        // Closing here would throw away what was typed for no reason.
        assertTrue(viewModel.uiState.value.editor?.nameTaken == true)
        // Seven, because this test freed a slot first; the point is that none was added.
        assertEquals(7, viewModel.uiState.value.categories.size)
    }

    @Test
    fun `typing again clears the duplicate warning`() = runTest(mainDispatcher) {
        subscribe()
        freeOneSlot()
        viewModel.startNew()
        viewModel.setEditorName("Transport")
        viewModel.saveEditor()

        viewModel.setEditorName("Transport 2")

        assertFalse(viewModel.uiState.value.editor?.nameTaken == true)
    }

    @Test
    fun `a blank name is refused`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.startNew()

        viewModel.setEditorName("   ")
        viewModel.saveEditor()

        assertTrue(viewModel.uiState.value.editor?.validationVisible == true)
    }

    @Test
    fun `all eight colours in use is reported, and one can still be shared`() = runTest(mainDispatcher) {
        subscribe()
        assertTrue(viewModel.uiState.value.allSlotsUsed)

        viewModel.startNew()
        viewModel.setEditorName("Coffee")
        viewModel.setEditorColor("#2A78D6")
        viewModel.saveEditor()

        // Duplicates are fine — a category is always name-labelled (DESIGN.md §3b).
        assertEquals("#2A78D6", rowNamed("Coffee").colorHex)
        assertEquals(9, viewModel.uiState.value.categories.size)
    }

    @Test
    fun `renaming keeps the entries attached`() = runTest(mainDispatcher) {
        subscribe()
        val foodId = seededCategoryId("Food & Drink")
        repository.addExpense(1000, LocalDate.now(), foodId)

        viewModel.startEditing(rowNamed("Food & Drink"))
        viewModel.setEditorName("Eating out")
        viewModel.saveEditor()

        assertEquals(1, rowNamed("Eating out").expenseCount)
        assertEquals(foodId, rowNamed("Eating out").id)
    }

    @Test
    fun `recolouring changes it everywhere at once`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.startEditing(rowNamed("Food & Drink"))

        viewModel.setEditorColor("#E34948")
        viewModel.saveEditor()

        // The colour lives on the row, so the chart, the chips and the meter all follow.
        assertEquals("#E34948", db.categoryDao().getByName("Food & Drink")?.colorHex)
    }

    @Test
    fun `renaming to another category's name is refused`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.startEditing(rowNamed("Food & Drink"))

        viewModel.setEditorName("Transport")
        viewModel.saveEditor()

        assertTrue(viewModel.uiState.value.editor?.nameTaken == true)
        assertNotNull(db.categoryDao().getByName("Food & Drink"))
    }

    @Test
    fun `keeping its own name while recolouring is not a clash`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.startEditing(rowNamed("Food & Drink"))

        viewModel.setEditorColor("#EDA100")
        viewModel.saveEditor()

        assertNull(viewModel.uiState.value.editor)
        assertEquals("#EDA100", rowNamed("Food & Drink").colorHex)
    }

    @Test
    fun `deleting moves entries to the chosen category`() = runTest(mainDispatcher) {
        subscribe()
        val foodId = seededCategoryId("Food & Drink")
        val groceriesId = seededCategoryId("Groceries")
        repository.addExpense(1000, LocalDate.now(), foodId)
        repository.addExpense(2000, LocalDate.now(), foodId)

        viewModel.requestDelete(rowNamed("Food & Drink"))
        viewModel.confirmDelete(groceriesId)

        assertNull(db.categoryDao().getById(foodId))
        assertEquals(2, rowNamed("Groceries").expenseCount)
    }

    @Test
    fun `deleting moves recurring rules too, not just entries`() = runTest(mainDispatcher) {
        subscribe()
        val foodId = seededCategoryId("Food & Drink")
        val groceriesId = seededCategoryId("Groceries")
        repository.addRecurringExpense(
            amountMinor = 1000,
            startDate = LocalDate.now(),
            categoryId = foodId,
            frequency = RecurringFrequency.MONTHLY,
        )

        // recurring_rules also RESTRICTs on category, so a rule left behind would make the
        // delete fail with a constraint error rather than doing anything useful.
        viewModel.requestDelete(rowNamed("Food & Drink"))
        viewModel.confirmDelete(groceriesId)

        assertNull(db.categoryDao().getById(foodId))
        assertEquals(groceriesId, db.recurringRuleDao().getAll().single().categoryId)
    }

    @Test
    fun `deleting takes its budget with it`() = runTest(mainDispatcher) {
        subscribe()
        val foodId = seededCategoryId("Food & Drink")
        repository.setBudget(foodId, 50_000)

        viewModel.requestDelete(rowNamed("Food & Drink"))
        viewModel.confirmDelete(seededCategoryId("Groceries"))

        assertEquals(0, db.budgetDao().getAll().size)
    }

    @Test
    fun `cancelling a delete changes nothing`() = runTest(mainDispatcher) {
        subscribe()
        viewModel.requestDelete(rowNamed("Food & Drink"))

        viewModel.cancelDelete()

        assertNull(viewModel.uiState.value.pendingDelete)
        assertEquals(8, viewModel.uiState.value.categories.size)
    }

    @Test
    fun `the last category cannot be deleted`() = runTest(mainDispatcher) {
        subscribe()
        // Every expense needs a category, so there has to be one left to move things to.
        val keep = seededCategoryId("Food & Drink")
        viewModel.uiState.value.categories.filter { it.id != keep }
            .forEach { viewModel.confirmDeleteFor(it.id, keep) }

        assertEquals(1, viewModel.uiState.value.categories.size)
        viewModel.requestDelete(rowNamed("Food & Drink"))

        assertNull("no delete should even be armed", viewModel.uiState.value.pendingDelete)
        assertEquals(1, viewModel.uiState.value.categories.size)
    }
}

/** Delete without going through the dialog, for setting a test up. */
private fun CategoriesViewModel.confirmDeleteFor(id: Long, moveTo: Long) {
    requestDelete(uiState.value.categories.single { it.id == id })
    confirmDelete(moveTo)
}
