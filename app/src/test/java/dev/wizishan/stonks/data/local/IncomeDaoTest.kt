package dev.wizishan.stonks.data.local

import dev.wizishan.stonks.data.local.dao.observeFiltered
import dev.wizishan.stonks.data.local.query.HistorySort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class IncomeDaoTest : DatabaseTest() {

    private val dao get() = db.incomeDao()

    @Test
    fun `income is listed newest first by default`() = runTest {
        income(amountMinor = 250_000, date = "2026-09-01", source = "Salary")
        income(amountMinor = 40_000, date = "2026-09-15", source = "Freelance")

        val rows = dao.observeFiltered().first()

        assertEquals(listOf("Freelance", "Salary"), rows.map { it.source })
    }

    @Test
    fun `filters by source and date range`() = runTest {
        income(amountMinor = 250_000, date = "2026-08-01", source = "Salary")
        income(amountMinor = 250_000, date = "2026-09-01", source = "Salary")
        income(amountMinor = 40_000, date = "2026-09-15", source = "Freelance")

        assertEquals(2, dao.observeFiltered(source = "Salary").first().size)
        assertEquals(1, dao.observeFiltered(source = "freelance").first().size)
        assertEquals(
            2,
            dao.observeFiltered(
                from = LocalDate.parse("2026-09-01"),
                to = LocalDate.parse("2026-09-30"),
            ).first().size,
        )
    }

    @Test
    fun `sorts by amount`() = runTest {
        income(amountMinor = 40_000, date = "2026-09-15", source = "Freelance")
        income(amountMinor = 250_000, date = "2026-09-01", source = "Salary")

        assertEquals(
            listOf(250_000L, 40_000L),
            dao.observeFiltered(sort = HistorySort.AMOUNT_DESC).first().map { it.amountMinor },
        )
    }

    @Test
    fun `month total sums only that month and is zero when empty`() = runTest {
        income(amountMinor = 250_000, date = "2026-09-01", source = "Salary")
        income(amountMinor = 40_000, date = "2026-09-15", source = "Freelance")
        income(amountMinor = 999, date = "2026-08-15", source = "Salary")

        assertEquals(290_000, dao.observeMonthTotal("2026-09").first())
        assertEquals(0, dao.observeMonthTotal("2026-07").first())
    }

    @Test
    fun `distinct sources feed autocomplete`() = runTest {
        income(amountMinor = 1, date = "2026-09-01", source = "Salary")
        income(amountMinor = 2, date = "2026-09-02", source = "Salary")
        income(amountMinor = 3, date = "2026-09-03", source = "Freelance")

        assertEquals(listOf("Freelance", "Salary"), dao.observeSources().first())
    }

    @Test
    fun `monthly totals come back oldest first`() = runTest {
        income(amountMinor = 300, date = "2026-09-01", source = "Salary")
        income(amountMinor = 100, date = "2026-07-01", source = "Salary")

        val monthly = dao.observeMonthlyTotals().first()

        assertEquals(listOf("2026-07", "2026-09"), monthly.map { it.yearMonth })
        assertEquals(listOf(100L, 300L), monthly.map { it.totalMinor })
    }

    @Test
    fun `net cash flow for a month is income minus expenses`() = runTest {
        val foodId = seededCategoryId("Food & Drink")
        income(amountMinor = 250_000, date = "2026-09-01", source = "Salary")
        expense(amountMinor = 82_050, date = "2026-09-02", categoryId = foodId)

        val net = dao.observeMonthTotal("2026-09").first() -
            db.expenseDao().observeMonthTotal("2026-09").first()

        assertEquals(167_950, net)
    }
}
