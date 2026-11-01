package com.ragclient.android.data.network

import com.ragclient.android.data.auth.AuthRepository
import com.ragclient.android.data.storage.SessionStore
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Refresh-once behaviour for 401 responses. Auth endpoints are never
 * retried here — refreshing in response to a failed login would loop.
 * Concurrent 401s reuse whichever token the refresh wrote first.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: SessionStore,
    private val authRepository: Lazy<AuthRepository>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (request.header("Authorization") == null) return null
        if (request.tag(RetryMarker::class.java) != null) return null
        if (isAuthEndpoint(request.url.encodedPath)) return null

        val failedToken = request.header("Authorization")?.removePrefix("Bearer ")

        return runBlocking {
            val stored = tokenStore.currentAccessToken()
            val token = if (!stored.isNullOrBlank() && stored != failedToken) {
                stored
            } else {
                authRepository.get().refreshAccessToken()
            } ?: return@runBlocking null

            request.withToken(token)
                .newBuilder()
                .tag(RetryMarker::class.java, RetryMarker())
                .build()
        }
    }

    /** Marks the single replay so a still-failing request is not looped. */
    private class RetryMarker

    private fun Request.withToken(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()

    private fun isAuthEndpoint(path: String): Boolean =
        path.contains("/auth/login") ||
            path.contains("/auth/register") ||
            path.contains("/auth/refresh") ||
            path.contains("/auth/logout")
}
