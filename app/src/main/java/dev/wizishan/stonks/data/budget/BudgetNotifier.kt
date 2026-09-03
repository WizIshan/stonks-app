package dev.wizishan.stonks.data.budget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.wizishan.stonks.R
import dev.wizishan.stonks.core.Money

/**
 * What [BudgetChecker] needs from the notification layer.
 *
 * An interface rather than the concrete notifier so the checker's decisions — which
 * budgets alert, for what, how often — can be tested without a notification manager, which
 * a JVM test could not observe anyway.
 */
interface BudgetAlerts {
    fun notify(progress: BudgetProgress, alert: BudgetAlert)
}

/**
 * Local notifications for budget alerts. No server, no push — the check runs on-device and
 * posts straight to the notification manager.
 */
class BudgetNotifier(private val context: Context) : BudgetAlerts {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.budget_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.budget_channel_description)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Android 13 made notifications an opt-in permission. Without it, posting is a silent
     * no-op — so the check reports whether it could actually tell anyone.
     */
    fun canNotify(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    override fun notify(progress: BudgetProgress, alert: BudgetAlert) {
        if (!canNotify()) return
        ensureChannel()

        val title = when (alert) {
            BudgetAlert.THRESHOLD ->
                context.getString(R.string.budget_alert_threshold_title, progress.label, progress.percent)

            BudgetAlert.OVER ->
                context.getString(R.string.budget_alert_over_title, progress.label)
        }

        val body = context.getString(
            R.string.budget_alert_body,
            Money.format(progress.spentMinor),
            Money.format(progress.limitMinor),
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // Keyed by budget so a later alert for the same budget replaces the earlier one
        // rather than stacking a second card about the same limit.
        NotificationManagerCompat.from(context).notify(progress.budgetId.toInt(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "budget-alerts"
    }
}
