package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entities.CreditTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditTransactionDao {
    @Query("SELECT * FROM credit_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<CreditTransactionEntity>>
    
    @Query("SELECT * FROM credit_transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<CreditTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: CreditTransactionEntity)

    @Query("SELECT balanceAfterTransaction FROM credit_transactions ORDER BY timestamp DESC, id DESC LIMIT 1")
    fun getLatestBalance(): Flow<Int?>
    
    @Query("SELECT balanceAfterTransaction FROM credit_transactions ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLatestBalanceSync(): Int?
}
