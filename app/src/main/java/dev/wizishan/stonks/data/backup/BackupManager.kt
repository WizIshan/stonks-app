package dev.wizishan.stonks.data.backup

import androidx.room.withTransaction
import dev.wizishan.stonks.data.local.StonksDatabase
import java.time.Instant

/**
 * Reads the whole database into a backup file and writes one back.
 *
 * **Restore replaces everything.** Merging was the alternative and it is worse: every
 * relation in the file is expressed by id, so merging into a database that already has
 * rows either collides on those ids or renumbers them, and renumbering means rewriting
 * every reference — where one missed reference silently reattaches an expense to the wrong
 * category. "Restore a backup" also means, to a person, that they get back exactly what
 * they saved. The UI says so before it happens.
 *
 * The whole restore is one transaction, so a file that fails part-way leaves the existing
 * data untouched rather than half-replaced.
 */
class BackupManager(
    private val database: StonksDatabase,
) {

    suspend fun export(now: Instant = Instant.now()): String {
        val snapshot = database.withTransaction {
            BackupSerializer.snapshot(
                exportedAt = now.toString(),
                categories = database.categoryDao().getAll(),
                trips = database.tripDao().getAll(),
                expenses = database.expenseDao().getAll(),
                income = database.incomeDao().getAll(),
                budgets = database.budgetDao().getAll(),
                rules = database.recurringRuleDao().getAll(),
            )
        }
        return BackupSerializer.encode(snapshot)
    }

    /** Parses, validates, then replaces. Returns how many rows were restored. */
    suspend fun import(text: String): Result<ImportSummary> {
        val file = BackupSerializer.decode(text).getOrElse { return Result.failure(it) }

        return runCatching {
            database.withTransaction {
                clearAll()

                // Categories and trips first: everything else points at them.
                database.categoryDao().insertAllKeepingIds(BackupSerializer.toCategories(file))
                database.tripDao().insertAll(BackupSerializer.toTrips(file))
                database.recurringRuleDao().insertAll(BackupSerializer.toRecurringRules(file))
                database.budgetDao().insertAll(BackupSerializer.toBudgets(file))
                database.expenseDao().insertAllKeepingIds(BackupSerializer.toExpenses(file))
                database.incomeDao().insertAllKeepingIds(BackupSerializer.toIncome(file))
            }
            ImportSummary(
                categories = file.categories.size,
                trips = file.trips.size,
                expenses = file.expenses.size,
                income = file.income.size,
                budgets = file.budgets.size,
                recurringRules = file.recurringRules.size,
            )
        }
    }

    /**
     * Children before parents, so the foreign keys never see a dangling reference
     * mid-clear. Budgets cascade from categories anyway, but clearing them explicitly
     * keeps the order readable rather than relying on a constraint to tidy up.
     */
    private suspend fun clearAll() {
        database.expenseDao().deleteAll()
        database.incomeDao().deleteAll()
        database.budgetDao().deleteAll()
        database.recurringRuleDao().deleteAll()
        database.tripDao().deleteAll()
        database.categoryDao().deleteAll()
    }
}

data class ImportSummary(
    val categories: Int,
    val trips: Int,
    val expenses: Int,
    val income: Int,
    val budgets: Int,
    val recurringRules: Int,
) {
    val entries: Int get() = expenses + income
}
