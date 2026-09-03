package dev.wizishan.stonks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.local.query.CategoryUsage
import kotlinx.coroutines.flow.Flow

/**
 * An abstract class rather than an interface so [reassignAndDelete] can be a real method
 * with `@Transaction` around several statements that span three tables.
 */
@Dao
abstract class CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    abstract fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    abstract suspend fun getAll(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    abstract suspend fun getById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun getByName(name: String): Category?

    /** Feeds slot assignment — see [dev.wizishan.stonks.core.CategorySlots.nextFree]. */
    @Query("SELECT colorHex FROM categories")
    abstract suspend fun usedColorHexes(): List<String>

    @Query("SELECT COUNT(*) FROM categories")
    abstract suspend fun count(): Int

    /**
     * How much each category is carrying, so the UI can say what a delete would move
     * before it happens rather than after.
     *
     * A LEFT JOIN, so a category with nothing in it still appears with a zero — an inner
     * join would silently drop exactly the categories that are safe to delete.
     */
    @Query(
        """
        SELECT c.id AS categoryId,
               (SELECT COUNT(*) FROM expenses e WHERE e.categoryId = c.id) AS expenseCount,
               (SELECT COUNT(*) FROM recurring_rules r WHERE r.categoryId = c.id) AS recurringRuleCount,
               (SELECT COUNT(*) FROM budgets b WHERE b.categoryId = c.id) AS budgetCount
        FROM categories c
        """
    )
    abstract fun observeUsage(): Flow<List<CategoryUsage>>

    @Insert
    abstract suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAll(categories: List<Category>): List<Long>

    @Insert
    abstract suspend fun insertAllKeepingIds(categories: List<Category>)

    @Update
    abstract suspend fun update(category: Category)

    @Query("DELETE FROM categories")
    abstract suspend fun deleteAll()

    /**
     * Throws `SQLiteConstraintException` if any expense or recurring rule still references
     * this category — both foreign keys are RESTRICT on purpose, so history can never be
     * silently destroyed. Use [reassignAndDelete] to move them out of the way first.
     */
    @Delete
    abstract suspend fun delete(category: Category)

    @Query("UPDATE expenses SET categoryId = :toId WHERE categoryId = :fromId")
    protected abstract suspend fun reassignExpenses(fromId: Long, toId: Long): Int

    @Query("UPDATE recurring_rules SET categoryId = :toId WHERE categoryId = :fromId")
    protected abstract suspend fun reassignRecurringRules(fromId: Long, toId: Long): Int

    @Query("DELETE FROM categories WHERE id = :id")
    protected abstract suspend fun deleteById(id: Long)

    /**
     * Move everything off a category and then remove it, atomically.
     *
     * Both dependants have to move, not just expenses: `recurring_rules` also RESTRICTs on
     * category, so leaving a rule behind makes the delete fail with a constraint error
     * that says nothing useful. Any budget on the category goes with it by CASCADE, which
     * is right — a limit is a setting, not history.
     */
    @Transaction
    open suspend fun reassignAndDelete(fromId: Long, toId: Long) {
        reassignExpenses(fromId, toId)
        reassignRecurringRules(fromId, toId)
        deleteById(fromId)
    }
}
