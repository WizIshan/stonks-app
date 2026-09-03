package dev.wizishan.stonks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.wizishan.stonks.data.local.entity.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): Category?

    /** Feeds slot assignment — see [dev.wizishan.stonks.core.CategorySlots.nextFree]. */
    @Query("SELECT colorHex FROM categories")
    suspend fun usedColorHexes(): List<String>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(categories: List<Category>): List<Long>

    @Update
    suspend fun update(category: Category)

    /**
     * Throws `SQLiteConstraintException` if any expense still references this category —
     * the foreign key is RESTRICT on purpose, so history can never be silently destroyed.
     * Callers must reassign those expenses first.
     */
    @Delete
    suspend fun delete(category: Category)
}
