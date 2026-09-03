package dev.wizishan.stonks.data.budget

import dev.wizishan.stonks.data.local.DatabaseTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The checker with a notifier that records instead of posting.
 *
 * Posting a real notification is not something a JVM test can observe, and it is not what
 * is worth pinning down — the decisions are which budgets alert, for what, and how often.
 */
private class RecordingNotifier : BudgetAlerts {
    val sent = mutableListOf<Pair<String, BudgetAlert>>()

    override fun notify(progress: BudgetProgress, alert: BudgetAlert) {
        sent += progress.label to alert
    }
}

class BudgetCheckerTest : DatabaseTest() {

    private val notifier = RecordingNotifier()

    private val checker by lazy {
        BudgetChecker(db.budgetDao(), db.categoryDao(), db.expenseDao(), notifier)
    }

    private val month = YearMonth.of(2026, 9)
    private val inMonth = LocalDate.parse("2026-09-10")

    private suspend fun budgetFor(categoryName: String?, limitMinor: Long, threshold: Int = 80) =
        repository().setBudget(
            categoryId = categoryName?.let { seededCategoryId(it) },
            monthlyLimitMinor = limitMinor,
            alertThresholdPercent = threshold,
        )

    private suspend fun spend(categoryName: String, amountMinor: Long, date: LocalDate = inMonth) =
        repository().addExpense(amountMinor, date, seededCategoryId(categoryName))

    @Test
    fun `no budgets means no work`() = runTest {
        spend("Food & Drink", 50_00)

        assertEquals(0, checker.check(month))
    }

    @Test
    fun `spend below the threshold is silent`() = runTest {
        budgetFor("Food & Drink", limitMinor = 100_00)
        spend("Food & Drink", 50_00)

        assertEquals(0, checker.check(month))
        assertEquals(emptyList<Pair<String, BudgetAlert>>(), notifier.sent)
    }

    @Test
    fun `crossing the threshold alerts once, not every day`() = runTest {
        budgetFor("Food & Drink", limitMinor = 100_00)
        spend("Food & Drink", 85_00)

        assertEquals(1, checker.check(month))
        assertEquals(0, checker.check(month))
        assertEquals(listOf("Food & Drink" to BudgetAlert.THRESHOLD), notifier.sent)
    }

    @Test
    fun `going over alerts again, even after the threshold warning`() = runTest {
        budgetFor("Food & Drink", limitMinor = 100_00)
        spend("Food & Drink", 85_00)
        checker.check(month)

        spend("Food & Drink", 30_00)
        assertEquals(1, checker.check(month))

        assertEquals(
            listOf(
                "Food & Drink" to BudgetAlert.THRESHOLD,
                "Food & Drink" to BudgetAlert.OVER,
            ),
            notifier.sent,
        )
    }

    @Test
    fun `a budget that jumps straight past the limit alerts once, as over`() = runTest {
        budgetFor("Food & Drink", limitMinor = 100_00)
        spend("Food & Drink", 250_00)

        assertEquals(1, checker.check(month))
        assertEquals(listOf("Food & Drink" to BudgetAlert.OVER), notifier.sent)
        // The threshold is recorded too, so it cannot fire afterwards as a lesser alert.
        assertEquals(0, checker.check(month))
    }

    @Test
    fun `a new month alerts again`() = runTest {
        budgetFor("Food & Drink", limitMinor = 100_00)
        spend("Food & Drink", 85_00, date = LocalDate.parse("2026-09-10"))
        checker.check(month)

        spend("Food & Drink", 85_00, date = LocalDate.parse("2026-10-10"))

        assertEquals(1, checker.check(YearMonth.of(2026, 10)))
    }

    @Test
    fun `only spend in the checked month counts`() = runTest {
        budgetFor("Food & Drink", limitMinor = 100_00)
        spend("Food & Drink", 95_00, date = LocalDate.parse("2026-08-10"))

        assertEquals(0, checker.check(month))
    }

    @Test
    fun `a category budget ignores spend in other categories`() = runTest {
        budgetFor("Food & Drink", limitMinor = 100_00)
        spend("Transport", 200_00)

        assertEquals(0, checker.check(month))
    }

    @Test
    fun `the overall budget counts every category, budgeted or not`() = runTest {
        budgetFor(null, limitMinor = 100_00)
        spend("Food & Drink", 50_00)
        spend("Transport", 40_00)

        assertEquals(1, checker.check(month))
        assertEquals(listOf(BudgetChecker.OVERALL_LABEL to BudgetAlert.THRESHOLD), notifier.sent)
    }

    @Test
    fun `an overall and a category budget alert independently`() = runTest {
        budgetFor(null, limitMinor = 1000_00)
        budgetFor("Food & Drink", limitMinor = 100_00)
        spend("Food & Drink", 95_00)

        // The category is at 95%; overall is at 9.5% and stays quiet.
        assertEquals(1, checker.check(month))
        assertEquals(listOf("Food & Drink" to BudgetAlert.THRESHOLD), notifier.sent)
    }

    @Test
    fun `deleting a category takes its budget with it`() = runTest {
        budgetFor("Groceries", limitMinor = 100_00)
        val groceries = requireNotNull(db.categoryDao().getByName("Groceries"))

        db.categoryDao().delete(groceries)

        // The cascade removes the budget; a limit on a category that no longer exists
        // could only ever produce an alert with nothing to name.
        assertEquals(0, db.budgetDao().getAll().size)
    }
}
