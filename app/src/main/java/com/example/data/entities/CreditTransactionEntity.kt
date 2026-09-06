package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credit_transactions")
data class CreditTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Int,
    val type: String, // TASK_REWARD, HABIT_REWARD, APP_USAGE, EXPIRATION, ADMIN_ADJUSTMENT, REVERSAL
    val reason: String,
    val taskId: Int? = null,
    val habitId: Int? = null,
    val appPackage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val balanceAfterTransaction: Int
)
