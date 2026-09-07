package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.database.AppDatabase
import com.example.data.repository.AppUnlockRepository
import com.example.data.repository.ControlledAppRepository
import com.example.data.repository.CreditRepository
import com.example.data.repository.HabitRepository
import com.example.data.repository.TaskRepository
import com.example.service.AppSuspensionManager
import com.example.service.AppTracker

interface AppContainer {
    val taskRepository: TaskRepository
    val habitRepository: HabitRepository
    val creditRepository: CreditRepository
    val controlledAppRepository: ControlledAppRepository
    val appUnlockRepository: AppUnlockRepository
    val appTracker: AppTracker
    val appSuspensionManager: AppSuspensionManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "earntime_db")
            // A real migration, so existing tasks, habits and the credit ledger survive the upgrade.
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    override val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao())
    }

    override val habitRepository: HabitRepository by lazy {
        HabitRepository(database.habitDao())
    }

    override val creditRepository: CreditRepository by lazy {
        CreditRepository(database)
    }

    override val controlledAppRepository: ControlledAppRepository by lazy {
        ControlledAppRepository(database.controlledAppDao())
    }

    override val appUnlockRepository: AppUnlockRepository by lazy {
        AppUnlockRepository(database)
    }

    override val appTracker: AppTracker by lazy {
        AppTracker(controlledAppRepository, appUnlockRepository)
    }

    override val appSuspensionManager: AppSuspensionManager by lazy {
        AppSuspensionManager(context, controlledAppRepository, appUnlockRepository).apply {
            startListening()
        }
    }
}
