package dev.wizishan.stonks.data.budget

import dev.wizishan.stonks.data.local.entity.Budget

/**
 * How a budget is doing, in bands rather than a raw percentage.
 *
 * The bands are what the meter colours and the alerts key off, so they are defined once
 * here instead of being re-derived with slightly different boundaries in each place.
 */
enum class BudgetStatus {
    /** Below the alert threshold. */
    HEALTHY,

    /** At or past the threshold, still within the limit. */
    WARNING,

    /** At or past the limit. */
    OVER,

    /** Far enough past the limit that "over" understates it. */
    CRITICAL,
}

data class BudgetProgress(
    val budgetId: Long,
    val categoryId: Long?,
    val label: String,
    /** Null for the overall budget, which has no category colour of its own. */
    val colorHex: String?,
    val spentMinor: Long,
    val limitMinor: Long,
    val thresholdPercent: Int,
) {
    val isOverall: Boolean get() = categoryId == null

    /**
     * Spend as a percentage of the limit, uncapped.
     *
     * Deliberately allowed past 100 — the label says how far over, even though the bar
     * itself stops at the end of its track (DESIGN.md §5).
     */
    val percent: Int
        get() = if (limitMinor <= 0) 0 else ((spentMinor * 100) / limitMinor).toInt()

    /** Negative once the limit is passed. */
    val remainingMinor: Long get() = limitMinor - spentMinor

    val status: BudgetStatus get() = statusFor(percent, thresholdPercent)

    /** What the meter fills, clamped so an overspend cannot run past its track. */
    val fillFraction: Float
        get() = if (limitMinor <= 0) 0f else (spentMinor.toFloat() / limitMinor).coerceIn(0f, 1f)

    companion object {
        /** Past this multiple of the limit, "over" stops conveying the size of the problem. */
        const val CRITICAL_PERCENT = 120

        fun statusFor(percent: Int, thresholdPercent: Int): BudgetStatus = when {
            percent >= CRITICAL_PERCENT -> BudgetStatus.CRITICAL
            percent >= 100 -> BudgetStatus.OVER
            percent >= thresholdPercent -> BudgetStatus.WARNING
            else -> BudgetStatus.HEALTHY
        }
    }
}

/** Whether [budget] should raise an alert now, given where its spend has reached. */
fun Budget.alertFor(status: BudgetStatus, month: String): BudgetAlert? = when {
    // Crossing the limit notifies even if the threshold alert already went out this
    // month — "you are close" and "you are over" are different things to be told.
    status >= BudgetStatus.OVER && notifiedOverMonth != month -> BudgetAlert.OVER
    status == BudgetStatus.WARNING && notifiedThresholdMonth != month -> BudgetAlert.THRESHOLD
    else -> null
}

enum class BudgetAlert { THRESHOLD, OVER }
