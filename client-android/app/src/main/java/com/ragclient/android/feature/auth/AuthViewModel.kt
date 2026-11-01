package com.ragclient.android.feature.auth

import androidx.lifecycle.viewModelScope
import com.ragclient.android.core.mvi.BaseViewModel
import com.ragclient.android.data.auth.AuthOutcome
import com.ragclient.android.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : BaseViewModel<AuthState, AuthIntent, AuthResult>(AuthState()) {

    init {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                if (session.isAuthenticated) {
                    reduceResult(AuthResult.SubmitSucceeded)
                } else if (state.value.isSignedIn) {
                    reduceResult(AuthResult.SignedOut)
                }
            }
        }
    }

    override fun handle(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.ChangeField ->
                reduceResult(AuthResult.FieldChanged(intent.field, intent.value))

            AuthIntent.SwitchMode -> reduceResult(
                AuthResult.ModeChanged(
                    if (state.value.mode == AuthMode.SignIn) AuthMode.Register else AuthMode.SignIn
                )
            )

            AuthIntent.Submit -> submit()

            AuthIntent.SignOut -> viewModelScope.launch {
                authRepository.signOut()
                reduceResult(AuthResult.SignedOut)
            }
        }
    }

    private fun submit() {
        val current = state.value
        AuthReducer.validate(current)?.let { problem ->
            reduceResult(AuthResult.SubmitRejected(problem))
            return
        }

        reduceResult(AuthResult.SubmitStarted)
        viewModelScope.launch {
            val outcome = when (current.mode) {
                AuthMode.SignIn ->
                    authRepository.login(current.email, current.password)

                AuthMode.Register ->
                    authRepository.register(current.name, current.email, current.password)
            }

            when (outcome) {
                AuthOutcome.SignedIn -> reduceResult(AuthResult.SubmitSucceeded)
                is AuthOutcome.Rejected ->
                    reduceResult(AuthResult.SubmitRejected(outcome.message))
            }
        }
    }

    override fun reduce(state: AuthState, result: AuthResult): AuthState =
        AuthReducer.reduce(state, result)
}
