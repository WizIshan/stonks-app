package dev.wizishan.stonks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.wizishan.stonks.data.local.entity.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<Budget>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): Budget?

    /**
     * `IS` rather than `=` so a null [categoryId] matches the overall budget. SQLite's `=`
     * never matches null, so `= NULL` would silently return nothing and the app would
     * create a second overall budget every time.
     */
    @Query("SELECT * FROM budgets WHERE categoryId IS :categoryId")
    suspend fun getForCategory(categoryId: Long?): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: Budget): Long

    @Query("UPDATE budgets SET notifiedThresholdMonth = :month WHERE id = :id")
    suspend fun markThresholdNotified(id: Long, month: String)

    @Query("UPDATE budgets SET notifiedOverMonth = :month WHERE id = :id")
    suspend fun markOverNotified(id: Long, month: String)

    @Delete
    suspend fun delete(budget: Budget)
}
