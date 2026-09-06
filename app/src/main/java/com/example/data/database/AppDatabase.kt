package com.example.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.dao.ControlledAppDao
import com.example.data.dao.CreditTransactionDao
import com.example.data.dao.HabitDao
import com.example.data.dao.TaskDao
import com.example.data.entities.ControlledAppEntity
import com.example.data.entities.CreditTransactionEntity
import com.example.data.entities.HabitEntity
import com.example.data.entities.TaskEntity

@Database(
    entities = [
        TaskEntity::class,
        HabitEntity::class,
        ControlledAppEntity::class,
        CreditTransactionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun controlledAppDao(): ControlledAppDao
    abstract fun creditTransactionDao(): CreditTransactionDao
}
