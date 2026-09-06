package com.example.data.repository

import com.example.data.dao.TaskDao
import com.example.data.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()

    fun getTasksByStatus(status: String): Flow<List<TaskEntity>> = taskDao.getTasksByStatus(status)

    suspend fun insertTask(task: TaskEntity): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: TaskEntity) = taskDao.updateTask(task)

    suspend fun updateTaskStatus(id: Int, status: String, completedAt: Long? = null) {
        taskDao.updateTaskStatus(id, status, completedAt)
    }

    suspend fun deleteTaskById(id: Int) = taskDao.deleteTaskById(id)
}
