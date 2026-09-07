package com.example.presentation.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.EarnTimeApplication
import com.example.data.entities.HabitEntity
import com.example.data.entities.CreditTypes
import com.example.data.repository.CreditRepository
import com.example.data.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class HabitsUiState(
    val habits: List<HabitEntity> = emptyList(),
    val isAddHabitSheetVisible: Boolean = false
)

class HabitsViewModel(
    private val habitRepository: HabitRepository,
    private val creditRepository: CreditRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitsUiState())
    val uiState: StateFlow<HabitsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            habitRepository.activeHabits.collect { habitsList ->
                _uiState.update { it.copy(habits = habitsList) }
            }
        }
    }

    fun showAddHabitSheet() {
        _uiState.update { it.copy(isAddHabitSheetVisible = true) }
    }

    fun hideAddHabitSheet() {
        _uiState.update { it.copy(isAddHabitSheetVisible = false) }
    }

    fun addHabit(name: String, reward: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newHabit = HabitEntity(
                name = name,
                reward = reward
            )
            habitRepository.insertHabit(newHabit)
            hideAddHabitSheet()
        }
    }

    fun completeHabit(habit: HabitEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            
            // Basic streak calculation logic
            val lastCompleted = habit.lastCompletedDate ?: 0L
            val isNextDay = isNextDay(lastCompleted, now)
            val isSameDay = isSameDay(lastCompleted, now)

            if (isSameDay) {
                // Already completed today
                return@launch
            }

            val newStreak = if (isNextDay || lastCompleted == 0L) {
                habit.currentStreak + 1
            } else {
                1
            }
            
            val newLongestStreak = maxOf(newStreak, habit.longestStreak)

            val updatedHabit = habit.copy(
                currentStreak = newStreak,
                longestStreak = newLongestStreak,
                lastCompletedDate = now
            )

            // Reward credits
            creditRepository.addTransaction(
                amount = habit.reward,
                type = CreditTypes.HABIT_REWARD,
                reason = "Completed habit: ${habit.name}",
                habitId = habit.id
            )

            habitRepository.updateHabit(updatedHabit)
        }
    }

    fun deleteHabit(habitId: Int) {
        viewModelScope.launch {
            habitRepository.deleteHabit(habitId)
        }
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isNextDay(lastTime: Long, currentTime: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = lastTime }
        cal1.add(Calendar.DAY_OF_YEAR, 1)
        val cal2 = Calendar.getInstance().apply { timeInMillis = currentTime }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EarnTimeApplication)
                HabitsViewModel(
                    application.container.habitRepository,
                    application.container.creditRepository
                )
            }
        }
    }
}
