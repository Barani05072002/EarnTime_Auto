package com.example.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.EarnTimeApplication
import com.example.data.entities.ControlledAppEntity
import com.example.data.repository.ControlledAppRepository
import com.example.data.repository.CreditRepository
import com.example.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class DashboardUiState(
    val currentBalance: Int = 0,
    val tasksCompletedToday: Int = 0,
    val tasksTotalToday: Int = 0,
    val earnedToday: Int = 0,
    val spentToday: Int = 0,
    val selectedApps: List<ControlledAppEntity> = emptyList()
)

class DashboardViewModel(
    private val taskRepository: TaskRepository,
    private val creditRepository: CreditRepository,
    private val controlledAppRepository: ControlledAppRepository
) : ViewModel() {

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        creditRepository.currentBalance,
        taskRepository.allTasks,
        creditRepository.allTransactions,
        controlledAppRepository.selectedApps
    ) { balance, tasks, transactions, apps ->
        val startOfDay = getStartOfDay()
        
        val todayTasks = tasks.filter { it.createdTimestamp >= startOfDay }
        val tasksCompletedToday = todayTasks.count { it.status == "COMPLETED" }
        
        val todayTransactions = transactions.filter { it.timestamp >= startOfDay }
        val earnedToday = todayTransactions.filter { it.amount > 0 }.sumOf { it.amount }
        val spentToday = todayTransactions.filter { it.amount < 0 }.sumOf { -it.amount }

        DashboardUiState(
            currentBalance = balance,
            tasksCompletedToday = tasksCompletedToday,
            tasksTotalToday = todayTasks.size,
            earnedToday = earnedToday,
            spentToday = spentToday,
            selectedApps = apps
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EarnTimeApplication)
                DashboardViewModel(
                    application.container.taskRepository,
                    application.container.creditRepository,
                    application.container.controlledAppRepository
                )
            }
        }
    }
}
