package dev.wizishan.stonks.data.local

import dev.wizishan.stonks.data.local.dao.observeFiltered
import dev.wizishan.stonks.data.local.query.ExpenseSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExpenseDaoTest : DatabaseTest() {

    private val dao get() = db.expenseDao()

    @Test
    fun `list rows carry the joined category and trip names`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        val tripId = trip("Japan 2026")
        expense(amountMinor = 4250, date = "2026-09-01", categoryId = foodId, tripId = tripId, note = "Ramen")

        val row = dao.observeFiltered().first().single()

        assertEquals(4250, row.amountMinor)
        assertEquals(LocalDate.parse("2026-09-01"), row.date)
        assertEquals("Food & Drink", row.categoryName)
        assertEquals("#2A78D6", row.categoryColorHex)
        assertEquals("Japan 2026", row.tripName)
        assertEquals("Ramen", row.note)
    }

    @Test
    fun `an expense with no trip still appears in the list`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        expense(amountMinor = 900, date = "2026-09-01", categoryId = foodId)

        val row = dao.observeFiltered().first().single()

        assertNull(row.tripId)
        assertNull(row.tripName)
    }

    @Test
    fun `filters are independent and a null filter matches everything`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        val transportId = seededCategoryId("Transport")
        val japanId = trip("Japan 2026")
        expense(amountMinor = 1000, date = "2026-08-15", categoryId = foodId)
        expense(amountMinor = 2000, date = "2026-09-01", categoryId = foodId, tripId = japanId)
        expense(amountMinor = 3000, date = "2026-09-20", categoryId = transportId, tripId = japanId)

        assertEquals(3, dao.observeFiltered().first().size)
        assertEquals(2, dao.observeFiltered(categoryId = foodId).first().size)
        assertEquals(2, dao.observeFiltered(tripId = japanId).first().size)
        assertEquals(1, dao.observeFiltered(categoryId = foodId, tripId = japanId).first().size)
    }

    @Test
    fun `date range filter is inclusive at both ends`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        expense(amountMinor = 100, date = "2026-08-31", categoryId = foodId)
        expense(amountMinor = 200, date = "2026-09-01", categoryId = foodId)
        expense(amountMinor = 300, date = "2026-09-30", categoryId = foodId)
        expense(amountMinor = 400, date = "2026-10-01", categoryId = foodId)

        val inSeptember = dao.observeFiltered(
            from = LocalDate.parse("2026-09-01"),
            to = LocalDate.parse("2026-09-30"),
        ).first()

        assertEquals(listOf(300L, 200L), inSeptember.map { it.amountMinor })
    }

    @Test
    fun `every sort mode orders the list as named`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        val transportId = seededCategoryId("Transport")
        expense(amountMinor = 3000, date = "2026-09-01", categoryId = transportId)
        expense(amountMinor = 1000, date = "2026-09-15", categoryId = foodId)
        expense(amountMinor = 2000, date = "2026-09-10", categoryId = foodId)

        suspend fun amountsSortedBy(sort: ExpenseSort) =
            dao.observeFiltered(sort = sort).first().map { it.amountMinor }

        assertEquals(listOf(1000L, 2000L, 3000L), amountsSortedBy(ExpenseSort.DATE_DESC))
        assertEquals(listOf(3000L, 2000L, 1000L), amountsSortedBy(ExpenseSort.DATE_ASC))
        assertEquals(listOf(3000L, 2000L, 1000L), amountsSortedBy(ExpenseSort.AMOUNT_DESC))
        assertEquals(listOf(1000L, 2000L, 3000L), amountsSortedBy(ExpenseSort.AMOUNT_ASC))

        val byCategory = dao.observeFiltered(sort = ExpenseSort.CATEGORY_ASC).first()
        assertEquals(listOf("Food & Drink", "Food & Drink", "Transport"), byCategory.map { it.categoryName })
    }

    @Test
    fun `ties are broken by id so the order is stable`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        val first = expense(amountMinor = 500, date = "2026-09-01", categoryId = foodId)
        val second = expense(amountMinor = 500, date = "2026-09-01", categoryId = foodId)

        repeat(3) {
            assertEquals(listOf(second, first), dao.observeFiltered().first().map { it.id })
        }
    }

    @Test
    fun `month total sums only that month and is zero when empty`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        expense(amountMinor = 1000, date = "2026-08-31", categoryId = foodId)
        expense(amountMinor = 2050, date = "2026-09-01", categoryId = foodId)
        expense(amountMinor = 3025, date = "2026-09-30", categoryId = foodId)

        assertEquals(5075, dao.observeMonthTotal("2026-09").first())
        assertEquals(1000, dao.observeMonthTotal("2026-08").first())
        assertEquals(0, dao.observeMonthTotal("2026-07").first())
    }

    @Test
    fun `totals by category are grouped and ranked biggest first`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        val transportId = seededCategoryId("Transport")
        expense(amountMinor = 1000, date = "2026-09-01", categoryId = foodId)
        expense(amountMinor = 1500, date = "2026-09-02", categoryId = foodId)
        expense(amountMinor = 4000, date = "2026-09-03", categoryId = transportId)
        expense(amountMinor = 9999, date = "2026-08-03", categoryId = transportId)

        val totals = dao.observeTotalsByCategory("2026-09").first()

        assertEquals(listOf("Transport", "Food & Drink"), totals.map { it.categoryName })
        assertEquals(listOf(4000L, 2500L), totals.map { it.totalMinor })
        assertEquals("#EB6834", totals.first().colorHex)
    }

    @Test
    fun `totals by category exclude categories with no spend that month`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        expense(amountMinor = 1000, date = "2026-09-01", categoryId = foodId)

        assertEquals(listOf("Food & Drink"), dao.observeTotalsByCategory("2026-09").first().map { it.categoryName })
    }

    @Test
    fun `totals by trip span months and ignore untagged spend`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        val japanId = trip("Japan 2026")
        expense(amountMinor = 5000, date = "2026-08-28", categoryId = foodId, tripId = japanId)
        expense(amountMinor = 2500, date = "2026-09-02", categoryId = foodId, tripId = japanId)
        expense(amountMinor = 9999, date = "2026-09-03", categoryId = foodId)

        val totals = dao.observeTotalsByTrip().first()

        assertEquals(1, totals.size)
        assertEquals("Japan 2026", totals.single().tripName)
        assertEquals(7500, totals.single().totalMinor)
    }

    @Test
    fun `monthly totals come back oldest first for the trend line`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        expense(amountMinor = 300, date = "2026-09-01", categoryId = foodId)
        expense(amountMinor = 100, date = "2026-07-01", categoryId = foodId)
        expense(amountMinor = 200, date = "2026-08-01", categoryId = foodId)

        val monthly = dao.observeMonthlyTotals().first()

        assertEquals(listOf("2026-07", "2026-08", "2026-09"), monthly.map { it.yearMonth })
        assertEquals(listOf(100L, 200L, 300L), monthly.map { it.totalMinor })
    }

    @Test
    fun `deleting a trip keeps its expenses and only drops the grouping`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        val japanId = trip("Japan 2026")
        val expenseId = expense(amountMinor = 5000, date = "2026-09-01", categoryId = foodId, tripId = japanId)

        db.tripDao().delete(requireNotNull(db.tripDao().getById(japanId)))

        val survivor = dao.getById(expenseId)
        assertNotNull("deleting a trip must not delete its expenses", survivor)
        assertNull("the trip tag should be cleared", survivor!!.tripId)
        assertEquals(5000, survivor.amountMinor)
    }

    @Test
    fun `sums stay exact across many awkward amounts`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        // 0.10 + 0.20 is the classic case that drifts when money is held in a Double.
        repeat(100) { expense(amountMinor = 10, date = "2026-09-01", categoryId = foodId) }
        repeat(100) { expense(amountMinor = 20, date = "2026-09-01", categoryId = foodId) }

        assertEquals(3000, dao.observeMonthTotal("2026-09").first())
    }
}
