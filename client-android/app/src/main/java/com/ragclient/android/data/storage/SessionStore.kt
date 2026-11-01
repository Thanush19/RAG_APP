package com.ragclient.android.data.storage

import kotlinx.coroutines.flow.Flow

/**
 * Session persistence contract (client plan 4.0 binding convention):
 * the repositories and the OkHttp auth chain depend on this, not on a
 * concrete store, so they stay unit-testable without Android context.
 */
interface SessionStore {

    val session: Flow<Session>

    val accessToken: Flow<String?>

    suspend fun currentAccessToken(): String?

    suspend fun currentRefreshToken(): String?

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String?,
        userId: String?,
        email: String?,
        displayName: String?
    )

    suspend fun clearTokens()

    suspend fun wipe()
}
