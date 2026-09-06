package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "controlled_apps")
data class ControlledAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val mode: String = "FREE", // FREE, REWARD, ALWAYS_BLOCKED
    val maxDailyUsageMinutes: Int? = null,
    val isSelected: Boolean = false // Only explicitly selected apps are tracked
)
