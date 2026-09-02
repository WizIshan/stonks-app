package dev.wizishan.stonks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.wizishan.stonks.data.local.entity.Expense
import dev.wizishan.stonks.data.local.query.CategoryTotal
import dev.wizishan.stonks.data.local.query.ExpenseListItem
import dev.wizishan.stonks.data.local.query.ExpenseSort
import dev.wizishan.stonks.data.local.query.MonthTotal
import dev.wizishan.stonks.data.local.query.TripTotal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExpenseDao {

    // ---- reads -------------------------------------------------------------------

    /**
     * The History list: every filter is optional and every sort mode is one query.
     *
     * A null filter argument means "don't filter on this", which keeps the four filters
     * independent without building SQL by hand. Sorting uses a CASE per mode — the
     * branches that don't match evaluate to NULL for every row, so those terms compare
     * equal and ordering falls through to the next one. The trailing `e.id DESC` makes
     * the order total, so two expenses on the same date never swap places between reads.
     *
     * Prefer the [observeFiltered] extension below, which takes the enum.
     */
    @Query(
        """
        SELECT e.id AS id,
               e.amountMinor AS amountMinor,
               e.date AS date,
               e.categoryId AS categoryId,
               c.name AS categoryName,
               c.colorHex AS categoryColorHex,
               e.tripId AS tripId,
               t.name AS tripName,
               e.note AS note
        FROM expenses e
        INNER JOIN categories c ON c.id = e.categoryId
        LEFT JOIN trips t ON t.id = e.tripId
        WHERE (:categoryId IS NULL OR e.categoryId = :categoryId)
          AND (:tripId IS NULL OR e.tripId = :tripId)
          AND (:from IS NULL OR e.date >= :from)
          AND (:to IS NULL OR e.date <= :to)
        ORDER BY
          CASE WHEN :sort = 'DATE_DESC' THEN e.date END DESC,
          CASE WHEN :sort = 'DATE_ASC' THEN e.date END ASC,
          CASE WHEN :sort = 'AMOUNT_DESC' THEN e.amountMinor END DESC,
          CASE WHEN :sort = 'AMOUNT_ASC' THEN e.amountMinor END ASC,
          CASE WHEN :sort = 'CATEGORY_ASC' THEN c.name END COLLATE NOCASE ASC,
          e.id DESC
        """
    )
    fun observeFilteredRaw(
        categoryId: Long?,
        tripId: Long?,
        from: LocalDate?,
        to: LocalDate?,
        sort: String,
    ): Flow<List<ExpenseListItem>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId")
    suspend fun countInCategory(categoryId: Long): Int

    // ---- aggregates --------------------------------------------------------------

    /**
     * Total spend for one calendar month, `yearMonth` as `YYYY-MM`.
     *
     * COALESCE keeps this returning 0 rather than null for a month with no spend, so the
     * dashboard's hero figure has nothing to special-case.
     */
    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM expenses WHERE substr(date, 1, 7) = :yearMonth")
    fun observeMonthTotal(yearMonth: String): Flow<Long>

    /** Spend per category for one month, biggest first — the ranked bar chart. */
    @Query(
        """
        SELECT c.id AS categoryId,
               c.name AS categoryName,
               c.colorHex AS colorHex,
               SUM(e.amountMinor) AS totalMinor
        FROM expenses e
        INNER JOIN categories c ON c.id = e.categoryId
        WHERE substr(e.date, 1, 7) = :yearMonth
        GROUP BY c.id, c.name, c.colorHex
        ORDER BY totalMinor DESC, c.name COLLATE NOCASE ASC
        """
    )
    fun observeTotalsByCategory(yearMonth: String): Flow<List<CategoryTotal>>

    /** Spend per trip across all time — trips span months, so this is not month-scoped. */
    @Query(
        """
        SELECT t.id AS tripId,
               t.name AS tripName,
               SUM(e.amountMinor) AS totalMinor
        FROM expenses e
        INNER JOIN trips t ON t.id = e.tripId
        GROUP BY t.id, t.name
        ORDER BY totalMinor DESC, t.name COLLATE NOCASE ASC
        """
    )
    fun observeTotalsByTrip(): Flow<List<TripTotal>>

    /** Monthly spend totals, oldest first — the trend line. */
    @Query(
        """
        SELECT substr(date, 1, 7) AS yearMonth,
               SUM(amountMinor) AS totalMinor
        FROM expenses
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
        """
    )
    fun observeMonthlyTotals(): Flow<List<MonthTotal>>

    // ---- writes ------------------------------------------------------------------

    @Insert
    suspend fun insert(expense: Expense): Long

    @Insert
    suspend fun insertAll(expenses: List<Expense>): List<Long>

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("UPDATE expenses SET categoryId = :toCategoryId WHERE categoryId = :fromCategoryId")
    suspend fun reassignCategory(fromCategoryId: Long, toCategoryId: Long): Int
}

/**
 * Type-safe wrapper over [ExpenseDao.observeFilteredRaw].
 *
 * An extension rather than an interface default so Room only ever sees the `@Query`
 * method, and the enum can't drift from the strings the SQL compares against.
 */
fun ExpenseDao.observeFiltered(
    categoryId: Long? = null,
    tripId: Long? = null,
    from: LocalDate? = null,
    to: LocalDate? = null,
    sort: ExpenseSort = ExpenseSort.DATE_DESC,
): Flow<List<ExpenseListItem>> = observeFilteredRaw(categoryId, tripId, from, to, sort.name)
