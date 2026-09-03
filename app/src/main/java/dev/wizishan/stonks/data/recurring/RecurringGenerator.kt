package dev.wizishan.stonks.data.recurring

import androidx.room.withTransaction
import dev.wizishan.stonks.data.local.StonksDatabase
import dev.wizishan.stonks.data.local.entity.Expense
import dev.wizishan.stonks.data.local.entity.Income
import dev.wizishan.stonks.data.local.entity.RecurringRule
import dev.wizishan.stonks.data.local.entity.RecurringType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Turns due recurring rules into real entries.
 *
 * Two things can trigger this — app launch and the daily [RecurringWorker] — and they run
 * in the same process, so a [Mutex] serialises them. Without it both could read the same
 * `nextDueDate` and generate the month's rent twice.
 *
 * Each rule's rows and its advanced `nextDueDate` are written in one transaction, so a
 * failure part-way cannot leave entries generated without the cursor having moved, which
 * would generate them again on the next run.
 */
class RecurringGenerator(
    private val database: StonksDatabase,
) {

    private val mutex = Mutex()

    /** Returns how many entries were created. */
    suspend fun generateDue(today: LocalDate = LocalDate.now()): Int = mutex.withLock {
        val dao = database.recurringRuleDao()
        var created = 0

        dao.getDue(today).forEach { rule ->
            val due = RecurrenceSchedule.occurrencesDue(rule, today)
            if (due.dates.isEmpty()) return@forEach

            database.withTransaction {
                due.dates.forEach { date -> insert(rule, date) }
                dao.setNextDueDate(rule.id, due.nextDueDate)
            }
            created += due.dates.size
        }

        created
    }

    private suspend fun insert(rule: RecurringRule, date: LocalDate) {
        when (rule.type) {
            RecurringType.EXPENSE -> database.expenseDao().insert(
                Expense(
                    amountMinor = rule.amountMinor,
                    date = date,
                    // A rule of type EXPENSE cannot be saved without one; see
                    // FinanceRepository.addRecurringRule.
                    categoryId = requireNotNull(rule.categoryId) {
                        "expense rule ${rule.id} has no category"
                    },
                    tripId = rule.tripId,
                    note = rule.note,
                    recurringRuleId = rule.id,
                )
            )

            RecurringType.INCOME -> database.incomeDao().insert(
                Income(
                    amountMinor = rule.amountMinor,
                    date = date,
                    source = requireNotNull(rule.source) { "income rule ${rule.id} has no source" },
                    note = rule.note,
                    recurringRuleId = rule.id,
                )
            )
        }
    }
}
