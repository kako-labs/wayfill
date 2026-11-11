package dev.fuelpilot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** The Application owns the live session; rotation only recreates this wrapper. */
class AppHostViewModel(val runtime: AssistRuntime) : ViewModel() {
    val state = runtime.state
    fun dispatch(intent: dev.fuelpilot.feature.assist.AssistIntent) = runtime.dispatch(intent)
    class Factory(private val runtime: AssistRuntime) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == AppHostViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return AppHostViewModel(runtime) as T
        }
    }
}
