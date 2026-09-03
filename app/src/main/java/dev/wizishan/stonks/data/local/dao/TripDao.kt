package dev.wizishan.stonks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.wizishan.stonks.data.local.entity.Trip
import dev.wizishan.stonks.data.local.query.TripUsage
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Query("SELECT * FROM trips ORDER BY COALESCE(startDate, '9999-12-31') DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): Trip?

    @Query("SELECT * FROM trips WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): Trip?

    @Query("SELECT * FROM trips")
    suspend fun getAll(): List<Trip>

    /**
     * How many expenses each trip is holding.
     *
     * A correlated subquery rather than a join, so a trip with nothing tagged to it still
     * comes back with a zero instead of dropping out of the list.
     */
    @Query(
        """
        SELECT t.id AS tripId,
               (SELECT COUNT(*) FROM expenses e WHERE e.tripId = t.id) AS expenseCount
        FROM trips t
        """
    )
    fun observeUsage(): Flow<List<TripUsage>>

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(trips: List<Trip>)

    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    /**
     * Expenses tagged to this trip are kept — their `tripId` is set to null by the
     * foreign key, so deleting a trip removes the grouping, not the spend.
     */
    @Delete
    suspend fun delete(trip: Trip)
}
