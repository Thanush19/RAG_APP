package com.ragclient.android.data.auth

import com.ragclient.android.data.api.AuthApi
import com.ragclient.android.data.api.dto.LoginRequestDto
import com.ragclient.android.data.api.dto.LoginResponseDto
import com.ragclient.android.data.api.dto.RefreshTokenRequestDto
import com.ragclient.android.data.api.dto.RegisterRequestDto
import com.ragclient.android.data.network.ApiErrorMapper
import com.ragclient.android.data.storage.Session
import com.ragclient.android.data.storage.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AuthOutcome {
    data object SignedIn : AuthOutcome
    data class Rejected(val message: String, val code: String? = null) : AuthOutcome
}

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: SessionStore,
    private val errorMapper: ApiErrorMapper
) {

    val session: Flow<Session> = tokenStore.session

    suspend fun login(email: String, password: String): AuthOutcome {
        val trimmed = email.trim()
        return runCatching { authApi.login(LoginRequestDto(trimmed, password)) }
            .fold(
                onSuccess = { response ->
                    persist(response, fallbackEmail = trimmed)
                    AuthOutcome.SignedIn
                },
                onFailure = { rejected(it, "Sign in failed. Check your email and password.") }
            )
    }

    /**
     * The backend register endpoint returns the user only, not a token,
     * so a successful registration is followed by an immediate sign-in.
     */
    suspend fun register(name: String, email: String, password: String): AuthOutcome {
        val trimmed = email.trim()
        val registration = runCatching {
            authApi.register(RegisterRequestDto(name.trim(), trimmed, password))
        }
        registration.exceptionOrNull()?.let {
            return rejected(it, "Registration failed.")
        }
        return login(trimmed, password)
    }

    suspend fun signOut() {
        val refreshToken = tokenStore.currentRefreshToken()
        if (refreshToken != null) {
            runCatching { authApi.logout(RefreshTokenRequestDto(refreshToken)) }
        }
        tokenStore.clearTokens()
    }

    /**
     * Refreshes once, serialised so a burst of 401s produces a single
     * refresh call. Returns the new access token, or null when the
     * session is gone.
     */
    suspend fun refreshAccessToken(): String? = refreshMutex.withLock {
        val refreshToken = tokenStore.currentRefreshToken()
        if (refreshToken == null) {
            // Nothing to refresh with: the session is over, sign in again.
            tokenStore.clearTokens()
            return@withLock null
        }
        val response = runCatching {
            authApi.refresh(RefreshTokenRequestDto(refreshToken))
        }.getOrNull() ?: run {
            tokenStore.clearTokens()
            return@withLock null
        }
        persist(response, fallbackEmail = null)
        response.accessToken
    }

    private suspend fun persist(response: LoginResponseDto, fallbackEmail: String?) {
        tokenStore.saveSession(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            userId = response.user?.id,
            email = response.user?.email ?: fallbackEmail,
            displayName = response.user?.name
        )
    }

    private fun rejected(error: Throwable, fallback: String): AuthOutcome.Rejected {
        val failure = errorMapper.describe(error, fallback)
        return AuthOutcome.Rejected(failure.message, failure.code)
    }

    private companion object {
        val refreshMutex = Mutex()
    }
}
