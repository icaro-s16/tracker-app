package com.tracker.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "location_queue")
data class LocationEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,       // Unix timestamp em milissegundos
    val isSent: Boolean = false
)


data class LocationPayload(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
)
