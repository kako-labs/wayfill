package dev.fuelpilot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.fuelpilot.feature.assist.AssistViewModel

class AppHostViewModel(dependencies: AppDependencies) : ViewModel() {
    private val coordinator = dependencies.coordinator()
    val assist = AssistViewModel(dependencies.store, coordinator.liveIntents(), scope = viewModelScope)
    init { coordinator.connectNavigation(assist, viewModelScope) }

    class Factory(private val dependencies: AppDependencies) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == AppHostViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return AppHostViewModel(dependencies) as T
        }
    }
}
