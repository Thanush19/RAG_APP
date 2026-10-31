package com.ragclient.android.feature.auth

import com.ragclient.android.core.mvi.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** M1 lands the real flow (login/register/refresh via AuthApi). */

data class AuthState(
    val email: String = "",
    val password: String = "",
    val inFlight: Boolean = false,
    val errorMessage: String? = null
)

sealed interface AuthIntent {
    data class CredentialsChanged(val email: String, val password: String) : AuthIntent
    data object SubmitLogin : AuthIntent
    data object OpenRegister : AuthIntent
}

sealed interface AuthResult

@HiltViewModel
class AuthViewModel @Inject constructor(
) : BaseViewModel<AuthState, AuthIntent, AuthResult>(AuthState()) {

    override fun handle(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.CredentialsChanged ->
                setState { copy(email = intent.email, password = intent.password) }
            else -> Unit // M1
        }
    }

    override fun reduce(state: AuthState, result: AuthResult): AuthState = state
}
