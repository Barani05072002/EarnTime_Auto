package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entities.ControlledAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ControlledAppDao {
    @Query("SELECT * FROM controlled_apps WHERE isSelected = 1 ORDER BY appName ASC")
    fun getSelectedApps(): Flow<List<ControlledAppEntity>>

    @Query("SELECT * FROM controlled_apps WHERE packageName = :packageName")
    suspend fun getAppByPackage(packageName: String): ControlledAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: ControlledAppEntity)

    @Update
    suspend fun updateApp(app: ControlledAppEntity)
}
