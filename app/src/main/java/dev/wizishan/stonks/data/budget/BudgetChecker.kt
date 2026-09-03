package dev.wizishan.stonks.data.budget

import dev.wizishan.stonks.data.local.dao.BudgetDao
import dev.wizishan.stonks.data.local.dao.CategoryDao
import dev.wizishan.stonks.data.local.dao.ExpenseDao
import kotlinx.coroutines.flow.first
import java.time.YearMonth

/**
 * Compares this month's spend against every budget and raises any alert that is newly due.
 *
 * Runs after recurring generation in the same daily job, not before: a rent entry created
 * this morning is spend, and checking first would report a budget as fine and then blow
 * through it seconds later without saying anything until tomorrow.
 */
class BudgetChecker(
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    private val expenseDao: ExpenseDao,
    private val notifier: BudgetAlerts,
) {

    /** Returns how many alerts were raised. */
    suspend fun check(month: YearMonth = YearMonth.now()): Int {
        val budgets = budgetDao.getAll()
        if (budgets.isEmpty()) return 0

        val key = month.toString()
        val categoriesById = categoryDao.observeAll().first().associateBy { it.id }
        val spendByCategory = expenseDao.observeTotalsByCategory(key).first().associate {
            it.categoryId to it.totalMinor
        }
        val totalSpend = expenseDao.observeMonthTotal(key).first()

        var raised = 0
        budgets.forEach { budget ->
            val category = budget.categoryId?.let(categoriesById::get)
            // A budget whose category was deleted cascades away with it, so a missing
            // category here means the row is mid-delete; skip rather than name it "null".
            if (budget.categoryId != null && category == null) return@forEach

            val progress = BudgetProgress(
                budgetId = budget.id,
                categoryId = budget.categoryId,
                label = category?.name ?: OVERALL_LABEL,
                colorHex = category?.colorHex,
                spentMinor = if (budget.categoryId == null) totalSpend else spendByCategory[budget.categoryId] ?: 0,
                limitMinor = budget.monthlyLimitMinor,
                thresholdPercent = budget.alertThresholdPercent,
            )

            val alert = budget.alertFor(progress.status, key) ?: return@forEach
            notifier.notify(progress, alert)
            when (alert) {
                BudgetAlert.THRESHOLD -> budgetDao.markThresholdNotified(budget.id, key)
                // Passing the limit implies passing the threshold, so record both. Without
                // this, a budget that jumps straight past 100% would still fire a
                // "you're close" alert afterwards if spend somehow dipped back.
                BudgetAlert.OVER -> {
                    budgetDao.markOverNotified(budget.id, key)
                    budgetDao.markThresholdNotified(budget.id, key)
                }
            }
            raised++
        }
        return raised
    }

    companion object {
        const val OVERALL_LABEL = "Overall"
    }
}
