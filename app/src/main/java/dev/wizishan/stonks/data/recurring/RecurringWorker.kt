package dev.wizishan.stonks.data.recurring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.wizishan.stonks.StonksApplication
import java.util.concurrent.TimeUnit

/**
 * Generates due recurring entries once a day.
 *
 * WorkManager rather than an alarm or a launch-time check alone: the job has to survive
 * the app being killed and the device rebooting, which is exactly the case a rent rule
 * cares about. It is also run at every app launch (see [StonksApplication]), because
 * WorkManager's periodic window is approximate and someone opening the app expects this
 * month's rent to already be there.
 */
class RecurringWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val generator = (applicationContext as StonksApplication).container.recurringGenerator
        return runCatching { generator.generateDue() }
            .fold(
                onSuccess = { Result.success() },
                // Retry rather than failure: a transient database error should be tried
                // again, and generation is safe to repeat because each rule advances its
                // own cursor inside the same transaction that writes its entries.
                onFailure = { Result.retry() },
            )
    }

    companion object {
        private const val UNIQUE_NAME = "recurring-transactions"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                // KEEP, so a relaunch does not reset the schedule and push the next run out
                // by another day each time the app is opened.
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
