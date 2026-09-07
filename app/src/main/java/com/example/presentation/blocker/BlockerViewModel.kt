package com.example.presentation.blocker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.EarnTimeApplication
import com.example.data.repository.AppUnlockRepository
import com.example.data.repository.CreditRepository
import com.example.data.repository.UnlockResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlockerUiState(
    val balance: Int = 0,
    val isUnlocking: Boolean = false,
    val errorMessage: String? = null,
    /** Set once credits have been spent, so the screen can hand control back to the app. */
    val unlockedMinutes: Int? = null
)

class BlockerViewModel(
    private val unlockRepository: AppUnlockRepository,
    creditRepository: CreditRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockerUiState())
    val uiState: StateFlow<BlockerUiState> = _uiState.asStateFlow()

    private val balance: StateFlow<Int> = creditRepository.currentBalance
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            balance.collect { value -> _uiState.update { it.copy(balance = value) } }
        }
    }

    fun unlock(packageName: String, minutes: Int) {
        if (_uiState.value.isUnlocking) return
        _uiState.update { it.copy(isUnlocking = true, errorMessage = null) }

        viewModelScope.launch {
            val message = when (val result = unlockRepository.unlock(packageName, minutes)) {
                is UnlockResult.Success -> {
                    _uiState.update {
                        it.copy(isUnlocking = false, errorMessage = null, unlockedMinutes = minutes)
                    }
                    return@launch
                }

                is UnlockResult.AlreadyUnlocked -> {
                    _uiState.update {
                        it.copy(
                            isUnlocking = false,
                            errorMessage = null,
                            unlockedMinutes = result.session.remainingSeconds / 60
                        )
                    }
                    return@launch
                }

                is UnlockResult.InsufficientCredits ->
                    "You need ${result.required} credits but only have ${result.available}. " +
                        "Complete tasks or habits to earn more."

                is UnlockResult.Rejected -> result.reason
            }
            _uiState.update { it.copy(isUnlocking = false, errorMessage = message) }
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EarnTimeApplication
                BlockerViewModel(
                    application.container.appUnlockRepository,
                    application.container.creditRepository
                )
            }
        }
    }
}
