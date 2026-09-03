package dev.wizishan.stonks.data.repository

import dev.wizishan.stonks.data.local.query.HistorySort
import java.time.LocalDate

/**
 * One row of the History list. Expenses and income live in separate tables but read as
 * one stream, so the list needs a common shape.
 */
sealed interface HistoryItem {
    val id: Long
    val date: LocalDate

    /** Always positive. [signedAmountMinor] carries the direction. */
    val amountMinor: Long
    val note: String?

    /** Negative for spend, positive for income — what the `+`/`−` in the UI comes from. */
    val signedAmountMinor: Long

    data class ExpenseItem(
        override val id: Long,
        override val date: LocalDate,
        override val amountMinor: Long,
        override val note: String?,
        val categoryId: Long,
        val categoryName: String,
        val categoryColorHex: String,
        val tripId: Long?,
        val tripName: String?,
    ) : HistoryItem {
        override val signedAmountMinor: Long get() = -amountMinor
    }

    data class IncomeItem(
        override val id: Long,
        override val date: LocalDate,
        override val amountMinor: Long,
        override val note: String?,
        val source: String,
    ) : HistoryItem {
        override val signedAmountMinor: Long get() = amountMinor
    }
}

enum class HistoryType { ALL, EXPENSES, INCOME }

/**
 * Date-range presets. A custom range is deliberately absent from v1 — these four cover
 * the questions people actually ask a spend tracker, and each is one tap.
 */
enum class HistoryPeriod { ALL_TIME, THIS_MONTH, LAST_30_DAYS, LAST_90_DAYS }

/** Resolved against a supplied date rather than the clock, so tests are deterministic. */
fun HistoryPeriod.rangeOrNull(today: LocalDate): ClosedRange<LocalDate>? = when (this) {
    HistoryPeriod.ALL_TIME -> null
    HistoryPeriod.THIS_MONTH -> today.withDayOfMonth(1)..today
    HistoryPeriod.LAST_30_DAYS -> today.minusDays(29)..today
    HistoryPeriod.LAST_90_DAYS -> today.minusDays(89)..today
}

data class HistoryFilter(
    val type: HistoryType = HistoryType.ALL,
    val categoryId: Long? = null,
    val tripId: Long? = null,
    val period: HistoryPeriod = HistoryPeriod.ALL_TIME,
    val sort: HistorySort = HistorySort.DATE_DESC,
) {
    val isFiltered: Boolean
        get() = type != HistoryType.ALL ||
            categoryId != null ||
            tripId != null ||
            period != HistoryPeriod.ALL_TIME

    /**
     * Whether income can possibly match.
     *
     * Income has no category and no trip, so either of those filters excludes it —
     * showing an unfiltered income list beside a filtered expense list would be wrong.
     */
    val includesIncome: Boolean
        get() = type != HistoryType.EXPENSES && categoryId == null && tripId == null

    val includesExpenses: Boolean
        get() = type != HistoryType.INCOME

    /** Sorts that only make sense once the list is expenses alone. */
    val availableSorts: List<HistorySort>
        get() = if (type == HistoryType.INCOME) {
            listOf(
                HistorySort.DATE_DESC,
                HistorySort.DATE_ASC,
                HistorySort.AMOUNT_DESC,
                HistorySort.AMOUNT_ASC,
            )
        } else {
            HistorySort.entries
        }
}
