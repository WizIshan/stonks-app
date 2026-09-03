package dev.wizishan.stonks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.wizishan.stonks.data.local.entity.Income
import dev.wizishan.stonks.data.local.query.HistorySort
import dev.wizishan.stonks.data.local.query.IncomeListItem
import dev.wizishan.stonks.data.local.query.MonthTotal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface IncomeDao {

    // ---- reads -------------------------------------------------------------------

    /** See [ExpenseDao.observeFilteredRaw] for how the optional filters and CASE sort work. */
    @Query(
        """
        SELECT i.id AS id,
               i.amountMinor AS amountMinor,
               i.date AS date,
               i.source AS source,
               i.note AS note
        FROM income i
        WHERE (:from IS NULL OR i.date >= :from)
          AND (:to IS NULL OR i.date <= :to)
          AND (:source IS NULL OR i.source = :source COLLATE NOCASE)
        ORDER BY
          CASE WHEN :sort = 'DATE_DESC' THEN i.date END DESC,
          CASE WHEN :sort = 'DATE_ASC' THEN i.date END ASC,
          CASE WHEN :sort = 'AMOUNT_DESC' THEN i.amountMinor END DESC,
          CASE WHEN :sort = 'AMOUNT_ASC' THEN i.amountMinor END ASC,
          i.date DESC,
          i.id DESC
        """
    )
    fun observeFilteredRaw(
        from: LocalDate?,
        to: LocalDate?,
        source: String?,
        sort: String,
    ): Flow<List<IncomeListItem>>

    @Query("SELECT * FROM income WHERE id = :id")
    suspend fun getById(id: Long): Income?

    /** Distinct sources for autocomplete on the entry form. */
    @Query("SELECT DISTINCT source FROM income ORDER BY source COLLATE NOCASE ASC")
    fun observeSources(): Flow<List<String>>

    // ---- aggregates --------------------------------------------------------------

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM income WHERE substr(date, 1, 7) = :yearMonth")
    fun observeMonthTotal(yearMonth: String): Flow<Long>

    @Query(
        """
        SELECT substr(date, 1, 7) AS yearMonth,
               SUM(amountMinor) AS totalMinor
        FROM income
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
        """
    )
    fun observeMonthlyTotals(): Flow<List<MonthTotal>>

    // ---- writes ------------------------------------------------------------------

    @Query("SELECT * FROM income")
    suspend fun getAll(): List<Income>

    @Query("DELETE FROM income")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(income: Income): Long

    @Insert
    suspend fun insertAll(income: List<Income>): List<Long>

    @Insert
    suspend fun insertAllKeepingIds(income: List<Income>)

    @Update
    suspend fun update(income: Income)

    @Delete
    suspend fun delete(income: Income)
}

/** Type-safe wrapper over [IncomeDao.observeFilteredRaw]. */
fun IncomeDao.observeFiltered(
    from: LocalDate? = null,
    to: LocalDate? = null,
    source: String? = null,
    sort: HistorySort = HistorySort.DATE_DESC,
): Flow<List<IncomeListItem>> = observeFilteredRaw(from, to, source, sort.name)
