package dev.wizishan.stonks.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.wizishan.stonks.data.local.entity.Trip
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
