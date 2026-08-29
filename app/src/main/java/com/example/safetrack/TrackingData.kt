package com.example.safetrack

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_logs")
data class TrackingData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val packageName: String,
    val foregroundTimeMs: Long,
    val lastTimeUsed: Long,
    val isSynced: Boolean = false
)
