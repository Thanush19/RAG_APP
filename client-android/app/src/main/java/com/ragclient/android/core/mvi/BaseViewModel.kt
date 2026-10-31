package com.ragclient.android.core.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MVI store skeleton (client plan section 4):
 * Intent -> actor -> Result -> reduce(State, Result) -> State.
 * Reducers stay pure so they are unit-testable without Android.
 */
abstract class BaseViewModel<State : Any, Intent : Any, Result : Any>(
    initialState: State
) : ViewModel() {

    private val _state: MutableStateFlow<State> = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    fun onIntent(intent: Intent) {
        handle(intent)
    }

    protected abstract fun handle(intent: Intent)

    abstract fun reduce(state: State, result: Result): State

    protected fun reduceResult(result: Result) {
        _state.update { current -> reduce(current, result) }
    }

    protected fun setState(transform: State.() -> State) {
        _state.value = _state.value.transform()
    }
}

private fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
