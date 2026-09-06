package com.example.presentation.apps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.EarnTimeApplication
import com.example.data.entities.ControlledAppEntity
import com.example.data.repository.ControlledAppRepository
import com.example.domain.usecases.ScanInstalledAppsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppSelectionUiState(
    val isLoading: Boolean = true,
    val installedApps: List<ControlledAppEntity> = emptyList(),
    val error: String? = null
)

class AppSelectionViewModel(
    private val controlledAppRepository: ControlledAppRepository,
    private val scanInstalledAppsUseCase: ScanInstalledAppsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSelectionUiState())
    val uiState: StateFlow<AppSelectionUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Get all installed apps
                val scannedApps = scanInstalledAppsUseCase()
                
                // For each app, check if it's already in the DB and get its saved mode
                val appsWithState = scannedApps.map { scannedApp ->
                    val savedApp = controlledAppRepository.getApp(scannedApp.packageName)
                    if (savedApp != null) {
                        scannedApp.copy(
                            mode = savedApp.mode,
                            isSelected = savedApp.isSelected,
                            maxDailyUsageMinutes = savedApp.maxDailyUsageMinutes
                        )
                    } else {
                        scannedApp
                    }
                }
                
                _uiState.update { it.copy(isLoading = false, installedApps = appsWithState) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun toggleAppSelection(app: ControlledAppEntity) {
        val newSelectionState = !app.isSelected
        val newMode = if (newSelectionState) "REWARD" else "FREE" // Default mode when selected
        
        val updatedApp = app.copy(isSelected = newSelectionState, mode = newMode)
        
        viewModelScope.launch {
            controlledAppRepository.saveApp(updatedApp)
            
            // Update local state
            _uiState.update { state ->
                val newApps = state.installedApps.map {
                    if (it.packageName == updatedApp.packageName) updatedApp else it
                }
                state.copy(installedApps = newApps)
            }
        }
    }
    
    fun setAppMode(app: ControlledAppEntity, mode: String) {
        val updatedApp = app.copy(mode = mode, isSelected = mode != "FREE")
        
        viewModelScope.launch {
            controlledAppRepository.saveApp(updatedApp)
            
            // Update local state
            _uiState.update { state ->
                val newApps = state.installedApps.map {
                    if (it.packageName == updatedApp.packageName) updatedApp else it
                }
                state.copy(installedApps = newApps)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EarnTimeApplication)
                AppSelectionViewModel(
                    application.container.controlledAppRepository,
                    ScanInstalledAppsUseCase(application.applicationContext)
                )
            }
        }
    }
}
