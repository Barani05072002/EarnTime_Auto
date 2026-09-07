package com.example.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.EarnTimeApplication
import com.example.data.entities.TaskEntity
import com.example.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.data.entities.CreditTypes
import com.example.data.repository.CreditRepository

class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val creditRepository: CreditRepository
) : ViewModel() {

    val tasks: StateFlow<List<TaskEntity>> = taskRepository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTask(title: String, description: String, reward: Int) {
        viewModelScope.launch {
            taskRepository.insertTask(
                TaskEntity(
                    title = title,
                    description = description,
                    reward = reward
                )
            )
        }
    }
    
    fun completeTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.updateTaskStatus(task.id, "COMPLETED", System.currentTimeMillis())
            creditRepository.addTransaction(
                amount = task.reward,
                type = CreditTypes.TASK_REWARD,
                reason = "Completed task: ${task.title}",
                taskId = task.id
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EarnTimeApplication)
                TasksViewModel(
                    application.container.taskRepository,
                    application.container.creditRepository
                )
            }
        }
    }
}
