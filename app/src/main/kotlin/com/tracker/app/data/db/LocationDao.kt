package com.tracker.app.data.db

import androidx.room.*
import com.tracker.app.data.model.LocationEntry
import kotlinx.coroutines.flow.Flow


@Dao
interface LocationDao {

    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LocationEntry): Long

    
    @Query("SELECT * FROM location_queue WHERE isSent = 0 ORDER BY timestamp ASC")
    suspend fun getPendingEntries(): List<LocationEntry>

    
    @Query("SELECT COUNT(*) FROM location_queue WHERE isSent = 0")
    fun getPendingCount(): Flow<Int>

    
    @Query("UPDATE location_queue SET isSent = 1 WHERE id = :id")
    suspend fun markAsSent(id: Long)

    
    @Query("DELETE FROM location_queue WHERE isSent = 1")
    suspend fun deleteSentEntries()

    
    @Query("DELETE FROM location_queue")
    suspend fun deleteAll()

    
    @Query("SELECT COUNT(*) FROM location_queue WHERE isSent = 0")
    suspend fun getPendingCountOnce(): Int
}
