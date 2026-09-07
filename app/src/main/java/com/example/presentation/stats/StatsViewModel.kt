package com.example.presentation.stats

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.EarnTimeApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTimeMillis: Long
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val totalScreenTimeMillis: Long = 0L,
    val appUsages: List<AppUsageInfo> = emptyList()
)

class StatsViewModel(private val context: Context) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            withContext(Dispatchers.IO) {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val packageManager = context.packageManager
                
                // Get stats for today
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val startTime = cal.timeInMillis
                val endTime = System.currentTimeMillis()

                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                
                var totalTime = 0L
                val appUsageList = mutableListOf<AppUsageInfo>()

                if (stats != null) {
                    for (usageStats in stats) {
                        val timeInForeground = usageStats.totalTimeInForeground
                        if (timeInForeground > 60000) { // Only show apps used for > 1 minute
                            totalTime += timeInForeground
                            
                            val appName = try {
                                val appInfo = packageManager.getApplicationInfo(usageStats.packageName, 0)
                                packageManager.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                usageStats.packageName
                            }

                            appUsageList.add(
                                AppUsageInfo(
                                    packageName = usageStats.packageName,
                                    appName = appName,
                                    usageTimeMillis = timeInForeground
                                )
                            )
                        }
                    }
                }

                appUsageList.sortByDescending { it.usageTimeMillis }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totalScreenTimeMillis = totalTime,
                        appUsages = appUsageList
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EarnTimeApplication)
                StatsViewModel(application.applicationContext)
            }
        }
    }
}
