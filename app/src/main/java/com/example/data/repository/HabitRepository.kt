package com.example.data.repository

import com.example.data.dao.HabitDao
import com.example.data.entities.HabitEntity
import kotlinx.coroutines.flow.Flow

class HabitRepository(private val habitDao: HabitDao) {
    val activeHabits: Flow<List<HabitEntity>> = habitDao.getActiveHabits()

    suspend fun insertHabit(habit: HabitEntity) {
        habitDao.insertHabit(habit)
    }

    suspend fun updateHabit(habit: HabitEntity) {
        habitDao.updateHabit(habit)
    }

    suspend fun deleteHabit(habitId: Int) {
        habitDao.deleteHabitById(habitId)
    }
}
