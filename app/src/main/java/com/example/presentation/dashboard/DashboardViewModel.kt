package com.example.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.EarnTimeApplication
import com.example.data.entities.ControlledAppEntity
import com.example.data.entities.ControlledAppModes
import com.example.data.entities.UnlockEndReasons
import com.example.data.repository.AppUnlockRepository
import com.example.data.repository.ControlledAppRepository
import com.example.data.repository.CreditRepository
import com.example.data.repository.TaskRepository
import com.example.data.repository.UnlockResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/** A controlled app plus its live access state, as shown on the dashboard. */
data class ControlledAppStatus(
    val app: ControlledAppEntity,
    val isLocked: Boolean,
    val remainingSeconds: Int,
    /** False for ALWAYS_BLOCKED apps, which credits cannot open. */
    val canUnlock: Boolean
) {
    val remainingMinutesLabel: String
        get() = when {
            remainingSeconds >= 60 -> "${remainingSeconds / 60}m LEFT"
            remainingSeconds > 0 -> "${remainingSeconds}s LEFT"
            else -> "LOCKED"
        }
}

data class DashboardUiState(
    val currentBalance: Int = 0,
    val tasksCompletedToday: Int = 0,
    val tasksTotalToday: Int = 0,
    val earnedToday: Int = 0,
    val spentToday: Int = 0,
    val controlledApps: List<ControlledAppStatus> = emptyList()
)

class DashboardViewModel(
    private val taskRepository: TaskRepository,
    private val creditRepository: CreditRepository,
    private val controlledAppRepository: ControlledAppRepository,
    private val appUnlockRepository: AppUnlockRepository
) : ViewModel() {

    private val _unlockError = MutableStateFlow<String?>(null)
    val unlockError: StateFlow<String?> = _unlockError.asStateFlow()

    private val _isUnlocking = MutableStateFlow(false)
    val isUnlocking: StateFlow<Boolean> = _isUnlocking.asStateFlow()

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
        controlledAppRepository.selectedApps,
        appUnlockRepository.activeSessions
    ) { balance, tasks, transactions, apps, sessions ->
        val startOfDay = getStartOfDay()

        val todayTasks = tasks.filter { it.createdTimestamp >= startOfDay }
        val tasksCompletedToday = todayTasks.count { it.status == "COMPLETED" }

        val todayTransactions = transactions.filter { it.timestamp >= startOfDay }
        val earnedToday = todayTransactions.filter { it.amount > 0 }.sumOf { it.amount }
        val spentToday = todayTransactions.filter { it.amount < 0 }.sumOf { -it.amount }

        val sessionsByPackage = sessions.associateBy { it.packageName }
        val statuses = apps.map { app ->
            val alwaysBlocked = app.mode == ControlledAppModes.ALWAYS_BLOCKED
            val remaining = if (alwaysBlocked) 0 else sessionsByPackage[app.packageName]?.remainingSeconds ?: 0
            ControlledAppStatus(
                app = app,
                isLocked = alwaysBlocked || remaining <= 0,
                remainingSeconds = remaining,
                canUnlock = !alwaysBlocked
            )
        }

        DashboardUiState(
            currentBalance = balance,
            tasksCompletedToday = tasksCompletedToday,
            tasksTotalToday = todayTasks.size,
            earnedToday = earnedToday,
            spentToday = spentToday,
            controlledApps = statuses
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    /** Spend credits to open an access window for [packageName]. */
    fun unlockApp(packageName: String, minutes: Int) {
        if (_isUnlocking.value) return
        _isUnlocking.value = true
        _unlockError.value = null

        viewModelScope.launch {
            _unlockError.value = when (val result = appUnlockRepository.unlock(packageName, minutes)) {
                is UnlockResult.Success, is UnlockResult.AlreadyUnlocked -> null
                is UnlockResult.InsufficientCredits ->
                    "You need ${result.required} credits but only have ${result.available}."
                is UnlockResult.Rejected -> result.reason
            }
            _isUnlocking.value = false
        }
    }

    /** End an access window early and refund the whole unused minutes. */
    fun lockAppNow(packageName: String) {
        viewModelScope.launch {
            appUnlockRepository.endSessionsFor(
                packageName = packageName,
                reason = UnlockEndReasons.CANCELLED,
                refundUnused = true
            )
        }
    }

    fun dismissUnlockError() {
        _unlockError.update { null }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EarnTimeApplication
                DashboardViewModel(
                    application.container.taskRepository,
                    application.container.creditRepository,
                    application.container.controlledAppRepository,
                    application.container.appUnlockRepository
                )
            }
        }
    }
}
