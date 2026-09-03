package dev.wizishan.stonks.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.wizishan.stonks.StonksApplication
import java.util.concurrent.TimeUnit

/**
 * The app's once-a-day background pass: generate due recurring entries, then check budgets.
 *
 * One job rather than two, and in that order, because the second depends on the first —
 * rent generated this morning is spend that counts against this month's budget. Two
 * independent jobs could run in either order and report a budget as fine minutes before
 * blowing through it.
 *
 * WorkManager rather than an alarm: this has to survive the app being killed and the
 * device rebooting, which is exactly when a monthly rule matters. It also runs at every
 * app launch (see [StonksApplication]), because WorkManager's periodic window is
 * approximate and someone opening the app expects to already be up to date.
 */
class DailyMaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as StonksApplication).container
        return runCatching {
            container.recurringGenerator.generateDue()
            container.budgetChecker.check()
        }.fold(
            onSuccess = { Result.success() },
            // Retry, not failure: a transient database error deserves another go, and both
            // steps are safe to repeat — each rule advances its own cursor in the same
            // transaction that writes its entries, and each alert records the month it was
            // sent for.
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val UNIQUE_NAME = "daily-maintenance"

        /**
         * The name this work used before budgets existed.
         *
         * Its enqueued request still names a worker class that no longer exists, so
         * WorkManager would fail to instantiate it and retry forever. Cancelling it on the
         * way past is what makes the rename safe for an already-installed app.
         */
        private const val LEGACY_NAME = "recurring-transactions"

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(LEGACY_NAME)
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                // KEEP, so relaunching does not reset the schedule and push the next run
                // out by another day every time the app is opened.
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyMaintenanceWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
