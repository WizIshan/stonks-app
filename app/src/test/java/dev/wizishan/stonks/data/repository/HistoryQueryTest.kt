package dev.wizishan.stonks.data.repository

import dev.wizishan.stonks.data.local.DatabaseTest
import dev.wizishan.stonks.data.local.query.HistorySort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Covers the merge of the two tables into one History list. */
class HistoryQueryTest : DatabaseTest() {

    private val repository by lazy {
        repository()
    }

    private val today = LocalDate.parse("2026-09-15")

    private suspend fun history(
        filter: HistoryFilter = HistoryFilter(),
    ): List<HistoryItem> = repository.observeHistory(filter, today).first()

    /** Two expenses and one income, spread across two months. */
    private suspend fun seedMixedHistory(): Pair<Long, Long> {
        val foodId = seededCategoryId("Food & Drink")
        val transportId = seededCategoryId("Transport")
        val tripId = repository.addTrip("Japan 2026")
        repository.addExpense(4250, LocalDate.parse("2026-09-10"), foodId, tripId)
        repository.addExpense(1299, LocalDate.parse("2026-08-01"), transportId)
        repository.addIncome(250_000, LocalDate.parse("2026-09-05"), "Salary")
        return foodId to transportId
    }

    @Test
    fun `expenses and income merge into one list, newest first`() = runTest {
        seedMixedHistory()

        val items = history()

        assertEquals(3, items.size)
        assertEquals(
            listOf(
                LocalDate.parse("2026-09-10"),
                LocalDate.parse("2026-09-05"),
                LocalDate.parse("2026-08-01"),
            ),
            items.map { it.date },
        )
    }

    @Test
    fun `spend is negative and income positive`() = runTest {
        seedMixedHistory()

        val items = history()
        val expense = items.filterIsInstance<HistoryItem.ExpenseItem>().first()
        val income = items.filterIsInstance<HistoryItem.IncomeItem>().single()

        assertEquals(-4250, expense.signedAmountMinor)
        assertEquals(250_000, income.signedAmountMinor)
        assertTrue("the stored amount itself stays positive", expense.amountMinor > 0)
    }

    @Test
    fun `the type filter selects one side or the other`() = runTest {
        seedMixedHistory()

        val expenses = history(HistoryFilter(type = HistoryType.EXPENSES))
        val income = history(HistoryFilter(type = HistoryType.INCOME))

        assertTrue(expenses.all { it is HistoryItem.ExpenseItem })
        assertEquals(2, expenses.size)
        assertTrue(income.all { it is HistoryItem.IncomeItem })
        assertEquals(1, income.size)
    }

    @Test
    fun `filtering by category drops income rather than showing it unfiltered`() = runTest {
        val (foodId, _) = seedMixedHistory()

        val items = history(HistoryFilter(categoryId = foodId))

        assertEquals(1, items.size)
        assertEquals("Food & Drink", (items.single() as HistoryItem.ExpenseItem).categoryName)
    }

    @Test
    fun `filtering by trip also drops income`() = runTest {
        seedMixedHistory()
        val tripId = requireNotNull(db.tripDao().getByName("Japan 2026")).id

        val items = history(HistoryFilter(tripId = tripId))

        assertEquals(1, items.size)
        assertEquals("Japan 2026", (items.single() as HistoryItem.ExpenseItem).tripName)
    }

    @Test
    fun `the period filter narrows both sides to the window`() = runTest {
        seedMixedHistory()

        val thisMonth = history(HistoryFilter(period = HistoryPeriod.THIS_MONTH))

        assertEquals(2, thisMonth.size)
        assertTrue(thisMonth.all { it.date >= LocalDate.parse("2026-09-01") })
    }

    @Test
    fun `a rolling window reaches back across the month boundary`() = runTest {
        seedMixedHistory()

        // 2026-08-01 is 45 days before 2026-09-15: inside 90 days, outside 30.
        assertEquals(2, history(HistoryFilter(period = HistoryPeriod.LAST_30_DAYS)).size)
        assertEquals(3, history(HistoryFilter(period = HistoryPeriod.LAST_90_DAYS)).size)
    }

    @Test
    fun `amount sort orders across both tables`() = runTest {
        seedMixedHistory()

        val descending = history(HistoryFilter(sort = HistorySort.AMOUNT_DESC))
        val ascending = history(HistoryFilter(sort = HistorySort.AMOUNT_ASC))

        assertEquals(listOf(250_000L, 4250L, 1299L), descending.map { it.amountMinor })
        assertEquals(listOf(1299L, 4250L, 250_000L), ascending.map { it.amountMinor })
    }

    @Test
    fun `date ascending reverses the default`() = runTest {
        seedMixedHistory()

        val items = history(HistoryFilter(sort = HistorySort.DATE_ASC))

        assertEquals(LocalDate.parse("2026-08-01"), items.first().date)
        assertEquals(LocalDate.parse("2026-09-10"), items.last().date)
    }

    @Test
    fun `sorting by category puts income last, since it has none`() = runTest {
        seedMixedHistory()

        val items = history(HistoryFilter(sort = HistorySort.CATEGORY_ASC))

        assertEquals(
            listOf("Food & Drink", "Transport"),
            items.filterIsInstance<HistoryItem.ExpenseItem>().map { it.categoryName },
        )
        assertTrue("income sorts to the end", items.last() is HistoryItem.IncomeItem)
    }

    @Test
    fun `sorting by trip puts untagged spend after tagged, and income last`() = runTest {
        seedMixedHistory()

        val items = history(HistoryFilter(sort = HistorySort.TRIP_ASC))
        val expenses = items.filterIsInstance<HistoryItem.ExpenseItem>()

        assertEquals(listOf("Japan 2026", null), expenses.map { it.tripName })
        assertTrue(items.last() is HistoryItem.IncomeItem)
    }

    @Test
    fun `an empty database gives an empty list rather than failing`() = runTest {
        assertEquals(emptyList<HistoryItem>(), history())
    }

    @Test
    fun `the list reflects a later insert`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        assertEquals(0, history().size)

        repository.addExpense(1000, LocalDate.parse("2026-09-01"), foodId)

        assertEquals(1, history().size)
    }
}
