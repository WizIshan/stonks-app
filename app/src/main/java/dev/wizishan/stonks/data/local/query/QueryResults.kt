package dev.wizishan.stonks.data.local.query

import java.time.LocalDate

/**
 * One row of the History list, joined to its category and trip names in SQL.
 *
 * The list needs the category's name and colour for every row; reading them through a
 * relation would issue a query per row, so the join does it once.
 */
data class ExpenseListItem(
    val id: Long,
    val amountMinor: Long,
    val date: LocalDate,
    val categoryId: Long,
    val categoryName: String,
    val categoryColorHex: String,
    val tripId: Long?,
    val tripName: String?,
    val note: String?,
)

/** One row of the History list for income entries. */
data class IncomeListItem(
    val id: Long,
    val amountMinor: Long,
    val date: LocalDate,
    val source: String,
    val note: String?,
)

/** Spend for one category over some period — drives the ranked bar chart (DESIGN.md §4). */
data class CategoryTotal(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val totalMinor: Long,
)

/** Spend grouped under one trip. */
data class TripTotal(
    val tripId: Long,
    val tripName: String,
    val totalMinor: Long,
)

/** A single point on the trend line. [yearMonth] is `YYYY-MM`. */
data class MonthTotal(
    val yearMonth: String,
    val totalMinor: Long,
)

/**
 * How the History list is ordered.
 *
 * CATEGORY_ASC and TRIP_ASC only mean something for expenses; income rows have neither,
 * so they sort to the end of a mixed list.
 */
enum class HistorySort {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC,
    CATEGORY_ASC,
    TRIP_ASC,
}
