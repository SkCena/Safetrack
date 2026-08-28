package com.example.safetrack

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TrackingDao {
    @Insert
    suspend fun insertLog(log: TrackingData)

    @Query("SELECT * FROM tracking_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<TrackingData>

    @Query("UPDATE tracking_logs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Int>)
}
