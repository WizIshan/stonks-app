package dev.wizishan.stonks

import android.app.Application
import android.content.Context
import dev.wizishan.stonks.data.backup.BackupFiles
import dev.wizishan.stonks.data.backup.BackupManager
import dev.wizishan.stonks.data.budget.BudgetChecker
import dev.wizishan.stonks.data.budget.BudgetNotifier
import dev.wizishan.stonks.data.local.StonksDatabase
import dev.wizishan.stonks.data.recurring.RecurringGenerator
import dev.wizishan.stonks.data.work.DailyMaintenanceWorker
import dev.wizishan.stonks.data.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container.
 *
 * A DI framework would be more machinery than this app needs: there is one database, one
 * repository, and no build-variant or test-double swapping. Constructing them here keeps
 * the wiring readable in one screenful.
 */
class AppContainer(context: Context) {

    private val database: StonksDatabase by lazy { StonksDatabase.build(context) }

    val repository: FinanceRepository by lazy {
        FinanceRepository(
            categoryDao = database.categoryDao(),
            tripDao = database.tripDao(),
            expenseDao = database.expenseDao(),
            incomeDao = database.incomeDao(),
            recurringRuleDao = database.recurringRuleDao(),
            budgetDao = database.budgetDao(),
        )
    }

    val recurringGenerator: RecurringGenerator by lazy { RecurringGenerator(database) }

    val backupManager: BackupManager by lazy { BackupManager(database) }

    val backupFiles: BackupFiles by lazy { BackupFiles(context.applicationContext) }

    val budgetNotifier: BudgetNotifier by lazy { BudgetNotifier(context.applicationContext) }

    val budgetChecker: BudgetChecker by lazy {
        BudgetChecker(
            budgetDao = database.budgetDao(),
            categoryDao = database.categoryDao(),
            expenseDao = database.expenseDao(),
            notifier = budgetNotifier,
        )
    }
}

class StonksApplication : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * Outlives any screen, so catch-up generation is not cancelled by the user navigating
     * away while it runs.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        container.budgetNotifier.ensureChannel()

        // Two paths on purpose. This one makes the app correct the moment it is opened;
        // the worker makes it correct even when it is not. The generator serialises them,
        // and the budget check runs after generation for the reason described on
        // DailyMaintenanceWorker.
        applicationScope.launch {
            container.recurringGenerator.generateDue()
            container.budgetChecker.check()
        }
        DailyMaintenanceWorker.schedule(this)
    }
}
