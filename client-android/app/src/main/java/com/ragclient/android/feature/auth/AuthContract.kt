package com.ragclient.android.feature.auth

enum class AuthMode { SignIn, Register }

enum class AuthField { Email, Password, Name }

data class AuthState(
    val mode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val inFlight: Boolean = false,
    val error: String? = null,
    val isSignedIn: Boolean = false
) {
    val submitEnabled: Boolean
        get() = !inFlight && email.isNotBlank() && password.length >= MIN_PASSWORD

    val canSubmitRegister: Boolean
        get() = submitEnabled && name.isNotBlank()

    companion object {
        const val MIN_PASSWORD = 8
    }
}

sealed interface AuthIntent {
    data class ChangeField(val field: AuthField, val value: String) : AuthIntent
    data object SwitchMode : AuthIntent
    data object Submit : AuthIntent
    data object SignOut : AuthIntent
}

sealed interface AuthResult {
    data class FieldChanged(val field: AuthField, val value: String) : AuthResult
    data class ModeChanged(val mode: AuthMode) : AuthResult
    data object SubmitStarted : AuthResult
    data class SubmitRejected(val message: String) : AuthResult
    data object SubmitSucceeded : AuthResult
    data object SignedOut : AuthResult
}

/** Pure state transitions — unit tested without Android. */
object AuthReducer {

    fun reduce(state: AuthState, result: AuthResult): AuthState = when (result) {
        is AuthResult.FieldChanged -> when (result.field) {
            AuthField.Email -> state.copy(email = result.value, error = null)
            AuthField.Password -> state.copy(password = result.value, error = null)
            AuthField.Name -> state.copy(name = result.value, error = null)
        }

        is AuthResult.ModeChanged -> state.copy(
            mode = result.mode,
            error = null,
            password = "",
            isSignedIn = false
        )

        AuthResult.SubmitStarted -> state.copy(inFlight = true, error = null)

        is AuthResult.SubmitRejected -> state.copy(
            inFlight = false,
            error = result.message
        )

        AuthResult.SubmitSucceeded -> state.copy(
            inFlight = false,
            error = null,
            password = "",
            isSignedIn = true
        )

        AuthResult.SignedOut -> state.copy(
            mode = AuthMode.SignIn,
            email = "",
            password = "",
            name = "",
            inFlight = false,
            error = null,
            isSignedIn = false
        )
    }

    /** Client-side mirror of the backend bean validation, so obvious
     *  mistakes never cost a round trip. */
    fun validate(state: AuthState): String? = when {
        state.email.isBlank() -> "Email is required."
        !state.email.contains("@") -> "Email must be valid."
        state.password.length < AuthState.MIN_PASSWORD ->
            "Password must be at least ${AuthState.MIN_PASSWORD} characters."
        state.mode == AuthMode.Register && state.name.isBlank() -> "Name is required."
        else -> null
    }
}
