package dev.wizishan.stonks.data.backup

import kotlinx.serialization.Serializable

/**
 * The on-disk backup format.
 *
 * Deliberately its own set of types rather than serialising the Room entities directly.
 * The entities are free to change shape as the app grows; this format is a promise to
 * every file a user has already saved, and the two should not be able to drift into each
 * other by accident. Converting between them is where a schema change is forced to decide
 * what an old file means.
 *
 * [version] is the format's own version, not the database's. It changes only when the
 * shape here changes.
 */
@Serializable
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val exportedAt: String,
    val currency: String,
    val categories: List<BackupCategory> = emptyList(),
    val trips: List<BackupTrip> = emptyList(),
    val expenses: List<BackupExpense> = emptyList(),
    val income: List<BackupIncome> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    val recurringRules: List<BackupRecurringRule> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1

        /**
         * The oldest format this app still understands.
         *
         * A file from the future is refused rather than guessed at — importing a newer
         * format by ignoring the fields it does not recognise would silently drop data the
         * user believes they just restored.
         */
        const val MINIMUM_SUPPORTED_VERSION = 1
    }
}

@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val colorHex: String,
)

@Serializable
data class BackupTrip(
    val id: Long,
    val name: String,
    val startDate: String? = null,
    val endDate: String? = null,
)

@Serializable
data class BackupExpense(
    val id: Long,
    val amountMinor: Long,
    val date: String,
    val categoryId: Long,
    val tripId: Long? = null,
    val note: String? = null,
    val recurringRuleId: Long? = null,
)

@Serializable
data class BackupIncome(
    val id: Long,
    val amountMinor: Long,
    val date: String,
    val source: String,
    val note: String? = null,
    val recurringRuleId: Long? = null,
)

@Serializable
data class BackupBudget(
    val id: Long,
    val categoryId: Long? = null,
    val monthlyLimitMinor: Long,
    val alertThresholdPercent: Int,
    val notifiedThresholdMonth: String? = null,
    val notifiedOverMonth: String? = null,
)

@Serializable
data class BackupRecurringRule(
    val id: Long,
    val type: String,
    val amountMinor: Long,
    val categoryId: Long? = null,
    val source: String? = null,
    val tripId: Long? = null,
    val frequency: String,
    val startDate: String,
    val nextDueDate: String,
    val note: String? = null,
    val isActive: Boolean = true,
)

/** Why an import was refused, so the UI can say something specific. */
sealed interface ImportFailure {
    data object NotJson : ImportFailure
    data class UnsupportedVersion(val found: Int) : ImportFailure
    data class Invalid(val reason: String) : ImportFailure
}
