package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD
    val estimatedEffortMinutes: Int = 30,
    val deadline: Long? = null,
    val reward: Int = 0,
    val status: String = "TODO", // TODO, IN_PROGRESS, COMPLETED, MISSED, CANCELLED
    val createdTimestamp: Long = System.currentTimeMillis(),
    val completedTimestamp: Long? = null,
    val recurrenceType: String? = null // null, DAILY, WEEKLY, MONTHLY
)
