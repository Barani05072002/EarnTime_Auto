package com.example.data.repository

import com.example.data.dao.CreditTransactionDao
import com.example.data.entities.CreditTransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CreditRepository(private val creditTransactionDao: CreditTransactionDao) {
    val allTransactions: Flow<List<CreditTransactionEntity>> = creditTransactionDao.getAllTransactions()
    
    val currentBalance: Flow<Int> = creditTransactionDao.getLatestBalance().map { it ?: 0 }

    suspend fun addTransaction(amount: Int, type: String, reason: String, taskId: Int? = null) {
        val currentBalanceSync = creditTransactionDao.getLatestBalanceSync() ?: 0
        val newBalance = currentBalanceSync + amount
        
        val transaction = CreditTransactionEntity(
            amount = amount,
            type = type,
            reason = reason,
            taskId = taskId,
            balanceAfterTransaction = newBalance
        )
        creditTransactionDao.insertTransaction(transaction)
    }
}
