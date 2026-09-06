package com.example.data.repository

import com.example.data.dao.ControlledAppDao
import com.example.data.entities.ControlledAppEntity
import kotlinx.coroutines.flow.Flow

class ControlledAppRepository(private val dao: ControlledAppDao) {
    val selectedApps: Flow<List<ControlledAppEntity>> = dao.getSelectedApps()

    suspend fun getApp(packageName: String): ControlledAppEntity? {
        return dao.getAppByPackage(packageName)
    }

    suspend fun saveApp(app: ControlledAppEntity) {
        dao.insertApp(app)
    }

    suspend fun updateApp(app: ControlledAppEntity) {
        dao.updateApp(app)
    }
}
