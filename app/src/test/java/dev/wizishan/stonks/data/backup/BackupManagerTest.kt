package dev.wizishan.stonks.data.backup

import dev.wizishan.stonks.data.local.DatabaseTest
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BackupManagerTest : DatabaseTest() {

    private val manager by lazy { BackupManager(db) }

    /** Fixed, so two exports of the same data compare equal rather than differing by clock. */
    private val exportedAt: Instant = Instant.parse("2026-09-03T10:00:00Z")

    /** A database with one of everything, so a round trip has something to lose. */
    private suspend fun seedEverything() {
        val repository = repository()
        val foodId = seededCategoryId("Food & Drink")
        val tripId = repository.addTrip("Japan 2026", LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-14"))
        repository.addExpense(4250, LocalDate.parse("2026-09-01"), foodId, tripId, "Ramen")
        repository.addIncome(250_000, LocalDate.parse("2026-09-01"), "Salary")
        repository.setBudget(foodId, 50_000, 80)
        repository.setBudget(null, 300_000, 90)
        repository.addRecurringExpense(
            amountMinor = 95_000,
            startDate = LocalDate.parse("2026-09-01"),
            categoryId = seededCategoryId("Bills & Utilities"),
            frequency = RecurringFrequency.MONTHLY,
        )
    }

    @Test
    fun `everything survives an export and restore`() = runTest {
        seedEverything()
        val exported = manager.export()

        val summary = manager.import(exported).getOrThrow()

        assertEquals(8, summary.categories)
        assertEquals(1, summary.trips)
        assertEquals(1, summary.expenses)
        assertEquals(1, summary.income)
        assertEquals(2, summary.budgets)
        assertEquals(1, summary.recurringRules)
    }

    @Test
    fun `relations still point at the right rows after a restore`() = runTest {
        seedEverything()

        manager.import(manager.export()).getOrThrow()

        val expense = db.expenseDao().getAll().single()
        assertEquals("Food & Drink", db.categoryDao().getById(expense.categoryId)?.name)
        assertEquals("Japan 2026", db.tripDao().getById(requireNotNull(expense.tripId))?.name)
        assertEquals("Ramen", expense.note)
        assertEquals(4250, expense.amountMinor)
    }

    @Test
    fun `restoring replaces what is there rather than merging into it`() = runTest {
        seedEverything()
        val backup = manager.export()

        // Spend logged after the backup was taken.
        repository().addExpense(9999, LocalDate.parse("2026-09-20"), seededCategoryId("Transport"))
        assertEquals(2, db.expenseDao().getAll().size)

        manager.import(backup).getOrThrow()

        // Exactly what was saved, not what was saved plus what came after.
        assertEquals(1, db.expenseDao().getAll().size)
        assertEquals(4250, db.expenseDao().getAll().single().amountMinor)
    }

    @Test
    fun `a restore onto an empty database works`() = runTest {
        seedEverything()
        val backup = manager.export()
        // Wipe by restoring an empty file first.
        val empty = BackupSerializer.encode(
            BackupFile(exportedAt = "2026-09-03T10:00:00Z", currency = "EUR")
        )
        manager.import(empty).getOrThrow()
        assertEquals(0, db.categoryDao().getAll().size)

        manager.import(backup).getOrThrow()

        assertEquals(8, db.categoryDao().getAll().size)
        assertEquals(1, db.expenseDao().getAll().size)
    }

    @Test
    fun `a rejected file leaves the database exactly as it was`() = runTest {
        seedEverything()
        val before = manager.export(exportedAt)

        val result = manager.import("this is not a backup")

        assertTrue(result.isFailure)
        // Nothing half-applied: the export is byte-identical to the one taken before.
        assertEquals(before, manager.export(exportedAt))
    }

    @Test
    fun `a file that fails validation changes nothing`() = runTest {
        seedEverything()
        val countBefore = db.expenseDao().getAll().size

        val broken = BackupSerializer.encode(
            BackupFile(
                exportedAt = "2026-09-03T10:00:00Z",
                currency = "EUR",
                expenses = listOf(BackupExpense(1, 100, "2026-09-01", categoryId = 404)),
            )
        )
        val result = manager.import(broken)

        assertTrue(result.isFailure)
        assertEquals(countBefore, db.expenseDao().getAll().size)
        assertEquals(8, db.categoryDao().getAll().size)
    }

    @Test
    fun `a recurring rule keeps its schedule across a restore`() = runTest {
        seedEverything()

        manager.import(manager.export()).getOrThrow()

        val rule = db.recurringRuleDao().getAll().single()
        assertEquals(RecurringFrequency.MONTHLY, rule.frequency)
        assertEquals(LocalDate.parse("2026-09-01"), rule.startDate)
        assertEquals(LocalDate.parse("2026-09-01"), rule.nextDueDate)
        assertNotNull(db.categoryDao().getById(requireNotNull(rule.categoryId)))
    }

    @Test
    fun `the overall budget survives with its null category`() = runTest {
        seedEverything()

        manager.import(manager.export()).getOrThrow()

        val overall = db.budgetDao().getAll().single { it.categoryId == null }
        assertEquals(300_000, overall.monthlyLimitMinor)
        assertEquals(90, overall.alertThresholdPercent)
    }

    @Test
    fun `an export of an untouched install is still a valid backup`() = runTest {
        // Only the eight seeded categories exist.
        val summary = manager.import(manager.export()).getOrThrow()

        assertEquals(8, summary.categories)
        assertEquals(0, summary.entries)
    }
}
