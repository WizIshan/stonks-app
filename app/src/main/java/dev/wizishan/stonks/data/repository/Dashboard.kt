package dev.wizishan.stonks.data.repository

import dev.wizishan.stonks.core.CategorySlots
import dev.wizishan.stonks.data.local.query.CategoryTotal
import dev.wizishan.stonks.data.local.query.MonthTotal
import dev.wizishan.stonks.data.local.query.TripTotal
import java.time.YearMonth

/** One labelled bar in a ranked bar chart (DESIGN.md §4). */
data class RankedSlice(
    val label: String,
    val colorHex: String,
    val amountMinor: Long,
)

/** One point on the trend line. */
data class MonthPoint(
    val month: YearMonth,
    val spendMinor: Long,
    val incomeMinor: Long,
) {
    val netMinor: Long get() = incomeMinor - spendMinor
}

data class DashboardData(
    val month: YearMonth = YearMonth.now(),
    val spendMinor: Long = 0,
    val incomeMinor: Long = 0,
    val byCategory: List<RankedSlice> = emptyList(),
    val byTrip: List<RankedSlice> = emptyList(),
    val trend: List<MonthPoint> = emptyList(),
) {
    val netMinor: Long get() = incomeMinor - spendMinor
    val hasSpend: Boolean get() = spendMinor > 0
    val hasAnyActivity: Boolean get() = spendMinor > 0 || incomeMinor > 0
}

/** SQLite's `YYYY-MM` grouping key. */
fun YearMonth.storageKey(): String = toString()

/**
 * Rank categories and fold the tail into "Other".
 *
 * Past about seven colour classes adjacent ones start to blur, so the chart caps at
 * [limit] and folds the rest into the reserved grey. A tail of exactly one is left alone —
 * relabelling a single category as "Other" hides its name and gains nothing.
 */
fun List<CategoryTotal>.toRankedSlices(limit: Int = 6): List<RankedSlice> {
    val ranked = sortedByDescending { it.totalMinor }
    if (ranked.size <= limit + 1) {
        return ranked.map { RankedSlice(it.categoryName, it.colorHex, it.totalMinor) }
    }

    val head = ranked.take(limit).map { RankedSlice(it.categoryName, it.colorHex, it.totalMinor) }
    val tail = ranked.drop(limit)
    return head + RankedSlice(
        label = OTHER_LABEL,
        colorHex = CategorySlots.other.lightHex,
        amountMinor = tail.sumOf { it.totalMinor },
    )
}

/** Trips get the same treatment, without a palette colour of their own. */
fun List<TripTotal>.toTripSlices(limit: Int = 6): List<RankedSlice> {
    val ranked = sortedByDescending { it.totalMinor }
    val colorHex = CategorySlots.other.lightHex
    if (ranked.size <= limit + 1) {
        return ranked.map { RankedSlice(it.tripName, colorHex, it.totalMinor) }
    }
    val head = ranked.take(limit).map { RankedSlice(it.tripName, colorHex, it.totalMinor) }
    return head + RankedSlice(OTHER_LABEL, colorHex, ranked.drop(limit).sumOf { it.totalMinor })
}

/**
 * Build a continuous run of months ending at [endMonth].
 *
 * Months with no activity are filled with zero rather than skipped. A line drawn only
 * through the months that happen to have data would put a straight segment across a gap
 * and read as steady spending through a period with none.
 */
fun buildTrend(
    expenses: List<MonthTotal>,
    income: List<MonthTotal>,
    endMonth: YearMonth,
    months: Int = 12,
): List<MonthPoint> {
    val spendByMonth = expenses.associate { it.yearMonth to it.totalMinor }
    val incomeByMonth = income.associate { it.yearMonth to it.totalMinor }

    return (months - 1 downTo 0).map { back ->
        val month = endMonth.minusMonths(back.toLong())
        val key = month.storageKey()
        MonthPoint(
            month = month,
            spendMinor = spendByMonth[key] ?: 0,
            incomeMinor = incomeByMonth[key] ?: 0,
        )
    }
}

private const val OTHER_LABEL = "Other"
