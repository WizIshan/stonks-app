package dev.wizishan.stonks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.wizishan.stonks.data.local.entity.RecurringRule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rules ORDER BY isActive DESC, nextDueDate ASC")
    fun observeAll(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRule?

    /**
     * The rules the generator has to catch up on.
     *
     * Paused rules are excluded here rather than filtered afterwards, so a rule the user
     * paused a month ago does not quietly accumulate a backlog to dump on them when they
     * resume it.
     */
    @Query("SELECT * FROM recurring_rules WHERE isActive = 1 AND nextDueDate <= :today ORDER BY nextDueDate ASC")
    suspend fun getDue(today: LocalDate): List<RecurringRule>

    @Query("SELECT COUNT(*) FROM recurring_rules WHERE isActive = 1")
    suspend fun activeCount(): Int

    @Insert
    suspend fun insert(rule: RecurringRule): Long

    @Update
    suspend fun update(rule: RecurringRule)

    @Query("UPDATE recurring_rules SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("UPDATE recurring_rules SET nextDueDate = :nextDueDate WHERE id = :id")
    suspend fun setNextDueDate(id: Long, nextDueDate: LocalDate)

    /** Entries this rule already generated are kept — they are real spend that happened. */
    @Delete
    suspend fun delete(rule: RecurringRule)
}
