package dev.wizishan.stonks

import android.app.Application
import android.content.Context
import dev.wizishan.stonks.data.local.StonksDatabase
import dev.wizishan.stonks.data.repository.FinanceRepository

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
        )
    }
}

class StonksApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
