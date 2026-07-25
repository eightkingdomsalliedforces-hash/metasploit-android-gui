package dev.mago.android.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mago.android.installation.BootstrapCoordinator
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val coordinator: BootstrapCoordinator,
) : ViewModel() {
    val state = coordinator.state

    init {
        viewModelScope.launch { coordinator.inspectEnvironment() }
    }

    fun retry() {
        viewModelScope.launch { coordinator.retryCurrentStage() }
    }

    fun openTermux() {
        coordinator.openTermux()
    }

    companion object {
        fun factory(coordinator: BootstrapCoordinator): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OnboardingViewModel(coordinator) as T
            }
    }
}
