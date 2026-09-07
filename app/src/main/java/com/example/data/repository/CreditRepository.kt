package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.entities.CreditTransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CreditRepository(private val database: AppDatabase) {

    private val creditTransactionDao = database.creditTransactionDao()

    val allTransactions: Flow<List<CreditTransactionEntity>> = creditTransactionDao.getAllTransactions()

    val currentBalance: Flow<Int> = creditTransactionDao.getLatestBalance().map { it ?: 0 }

    suspend fun getBalance(): Int = creditTransactionDao.getLatestBalanceSync() ?: 0

    /**
     * Appends a ledger entry. Reading the running balance and writing the new row happen in one
     * DB transaction, so two concurrent writers (say a task reward and an app unlock) cannot both
     * base their `balanceAfterTransaction` on the same stale value.
     */
    suspend fun addTransaction(
        amount: Int,
        type: String,
        reason: String,
        taskId: Int? = null,
        habitId: Int? = null,
        appPackage: String? = null
    ) {
        database.withTransaction {
            val newBalance = (creditTransactionDao.getLatestBalanceSync() ?: 0) + amount
            creditTransactionDao.insertTransaction(
                CreditTransactionEntity(
                    amount = amount,
                    type = type,
                    reason = reason,
                    taskId = taskId,
                    habitId = habitId,
                    appPackage = appPackage,
                    balanceAfterTransaction = newBalance
                )
            )
        }
    }
}
