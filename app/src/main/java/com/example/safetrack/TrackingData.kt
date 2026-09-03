package com.example.safetrack

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "tracking_logs", indices = [Index(value = ["timestamp"])])
data class TrackingData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val packageName: String,
    val foregroundTimeMs: Long,
    val lastTimeUsed: Long,
    val category: String? = null,
    val usageType: String? = null,
    val isSynced: Boolean = false
)
