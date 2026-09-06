package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = "",
    val frequency: String = "DAILY", // DAILY, WEEKLY, CUSTOM
    val reward: Int = 10,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val isActive: Boolean = true
)
