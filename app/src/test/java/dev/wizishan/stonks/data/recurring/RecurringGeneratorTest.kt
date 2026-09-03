package dev.wizishan.stonks.data.recurring

import dev.wizishan.stonks.data.local.DatabaseTest
import dev.wizishan.stonks.data.local.dao.observeFiltered
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringGeneratorTest : DatabaseTest() {

    private val repository by lazy {
repository()
    }
    private val generator by lazy { recurringGenerator() }

    private suspend fun monthlyRent(start: String = "2026-09-01"): Long =
        repository.addRecurringExpense(
            amountMinor = 95_000,
            startDate = LocalDate.parse(start),
            categoryId = seededCategoryId("Bills & Utilities"),
            frequency = RecurringFrequency.MONTHLY,
            note = "Rent",
        )

    @Test
    fun `a rule generates its first entry on its start date`() = runTest {
        monthlyRent("2026-09-01")

        val created = generator.generateDue(LocalDate.parse("2026-09-01"))

        assertEquals(1, created)
        val row = db.expenseDao().observeFiltered().first().single()
        assertEquals(95_000, row.amountMinor)
        assertEquals("Bills & Utilities", row.categoryName)
        assertEquals("Rent", row.note)
    }

    @Test
    fun `generated entries record which rule made them`() = runTest {
        val ruleId = monthlyRent("2026-09-01")

        generator.generateDue(LocalDate.parse("2026-09-01"))

        assertEquals(ruleId, db.expenseDao().getById(1)?.recurringRuleId)
    }

    @Test
    fun `nothing is generated before the start date`() = runTest {
        monthlyRent("2026-10-01")

        assertEquals(0, generator.generateDue(LocalDate.parse("2026-09-15")))
        assertTrue(db.expenseDao().observeFiltered().first().isEmpty())
    }

    @Test
    fun `running twice on the same day does not double up`() = runTest {
        monthlyRent("2026-09-01")
        val today = LocalDate.parse("2026-09-01")

        generator.generateDue(today)
        val second = generator.generateDue(today)

        assertEquals(0, second)
        assertEquals(1, db.expenseDao().observeFiltered().first().size)
    }

    @Test
    fun `a gap of months is caught up, one entry per month`() = runTest {
        monthlyRent("2026-07-01")

        val created = generator.generateDue(LocalDate.parse("2026-09-15"))

        assertEquals(3, created)
        assertEquals(
            listOf("2026-09-01", "2026-08-01", "2026-07-01").map(LocalDate::parse),
            db.expenseDao().observeFiltered().first().map { it.date },
        )
    }

    @Test
    fun `the cursor advances past today so the next run is a no-op`() = runTest {
        val ruleId = monthlyRent("2026-09-01")

        generator.generateDue(LocalDate.parse("2026-09-15"))

        assertEquals(
            LocalDate.parse("2026-10-01"),
            db.recurringRuleDao().getById(ruleId)?.nextDueDate,
        )
    }

    @Test
    fun `a paused rule generates nothing`() = runTest {
        val ruleId = monthlyRent("2026-09-01")
        repository.setRuleActive(ruleId, active = false)

        assertEquals(0, generator.generateDue(LocalDate.parse("2026-12-01")))
        assertTrue(db.expenseDao().observeFiltered().first().isEmpty())
    }

    @Test
    fun `resuming does not dump the backlog accumulated while paused`() = runTest {
        val ruleId = monthlyRent("2026-06-01")
        repository.setRuleActive(ruleId, active = false)
        val resumedOn = LocalDate.parse("2026-09-15")

        repository.setRuleActive(ruleId, active = true, today = resumedOn)
        val created = generator.generateDue(resumedOn)

        // One entry for today, not four months of rent someone did not ask for.
        assertEquals(1, created)
        assertEquals(resumedOn, db.expenseDao().observeFiltered().first().single().date)
    }

    @Test
    fun `income rules write to the income table`() = runTest {
        repository.addRecurringIncome(
            amountMinor = 250_000,
            startDate = LocalDate.parse("2026-09-01"),
            source = "Salary",
            frequency = RecurringFrequency.MONTHLY,
        )

        generator.generateDue(LocalDate.parse("2026-09-01"))

        assertTrue(db.expenseDao().observeFiltered().first().isEmpty())
        val row = db.incomeDao().observeFiltered().first().single()
        assertEquals(250_000, row.amountMinor)
        assertEquals("Salary", row.source)
    }

    @Test
    fun `several rules are all caught up in one pass`() = runTest {
        monthlyRent("2026-09-01")
        repository.addRecurringExpense(
            amountMinor = 1_299,
            startDate = LocalDate.parse("2026-09-01"),
            categoryId = seededCategoryId("Entertainment"),
            frequency = RecurringFrequency.DAILY,
        )

        val created = generator.generateDue(LocalDate.parse("2026-09-03"))

        // One month of rent, three days of the daily rule.
        assertEquals(4, created)
    }

    @Test
    fun `a trip tag carries onto every generated entry`() = runTest {
        val tripId = repository.addTrip("Japan 2026")
        repository.addRecurringExpense(
            amountMinor = 5_000,
            startDate = LocalDate.parse("2026-09-01"),
            categoryId = seededCategoryId("Food & Drink"),
            frequency = RecurringFrequency.DAILY,
            tripId = tripId,
        )

        generator.generateDue(LocalDate.parse("2026-09-03"))

        val rows = db.expenseDao().observeFiltered().first()
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.tripName == "Japan 2026" })
    }

    @Test
    fun `deleting a rule keeps the entries it already created`() = runTest {
        val ruleId = monthlyRent("2026-07-01")
        generator.generateDue(LocalDate.parse("2026-09-15"))

        repository.deleteRule(ruleId)

        // The rent really was paid; removing the rule stops the future, not the past.
        assertEquals(3, db.expenseDao().observeFiltered().first().size)
    }
}
