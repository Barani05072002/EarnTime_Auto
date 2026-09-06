package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.database.AppDatabase
import com.example.data.repository.ControlledAppRepository
import com.example.data.repository.CreditRepository
import com.example.data.repository.TaskRepository
import com.example.service.AppTracker

interface AppContainer {
    val taskRepository: TaskRepository
    val creditRepository: CreditRepository
    val controlledAppRepository: ControlledAppRepository
    val appTracker: AppTracker
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "earntime_db")
            .fallbackToDestructiveMigration()
            .build()
    }
    override val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao())
    }
    
    override val creditRepository: CreditRepository by lazy {
        CreditRepository(database.creditTransactionDao())
    }
    
    override val controlledAppRepository: ControlledAppRepository by lazy {
        ControlledAppRepository(database.controlledAppDao())
    }
    
    override val appTracker: AppTracker by lazy {
        AppTracker(controlledAppRepository, creditRepository)
    }
}
