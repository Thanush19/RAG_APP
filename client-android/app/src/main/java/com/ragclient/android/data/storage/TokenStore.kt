package com.ragclient.android.data.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore by preferencesDataStore(name = "rag_tokens")

/** What the UI needs to know about the signed-in user. */
data class Session(
    val isAuthenticated: Boolean = false,
    val userId: String? = null,
    val email: String? = null,
    val displayName: String? = null
) {
    val initials: String
        get() {
            val source = displayName?.takeIf { it.isNotBlank() } ?: email.orEmpty()
            val parts = source.split(" ", "@", ".").filter { it.isNotBlank() }
            return parts.take(2).joinToString("") { it.first().uppercase() }
                .ifBlank { "?" }
        }
}

/**
 * Session persistence. Tokens live in app-private DataStore Preferences;
 * we deliberately avoid androidx.security:security-crypto here because
 * the only released artifact is an alpha that is not stable against the
 * current Keystore (client plan D6).
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SessionStore {

    companion object {
        private val KEY_ACCESS = stringPreferencesKey("access_token")
        private val KEY_REFRESH = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_NAME = stringPreferencesKey("display_name")
        private val KEY_LOGGED_OUT = booleanPreferencesKey("logged_out")
    }

    override val accessToken: Flow<String?> =
        context.tokenDataStore.data.map { it[KEY_ACCESS] }

    override val session: Flow<Session> =
        context.tokenDataStore.data.map { prefs ->
            val access = prefs[KEY_ACCESS]
            Session(
                isAuthenticated = !access.isNullOrBlank() && prefs[KEY_LOGGED_OUT] != true,
                userId = prefs[KEY_USER_ID],
                email = prefs[KEY_EMAIL],
                displayName = prefs[KEY_NAME]
            )
        }

    override suspend fun saveSession(
        accessToken: String,
        refreshToken: String?,
        userId: String?,
        email: String?,
        displayName: String?
    ) {
        context.tokenDataStore.edit {
            it[KEY_ACCESS] = accessToken
            it[KEY_LOGGED_OUT] = false
            refreshToken?.let { value -> it[KEY_REFRESH] = value }
            userId?.let { value -> it[KEY_USER_ID] = value }
            email?.let { value -> it[KEY_EMAIL] = value }
            displayName?.let { value -> it[KEY_NAME] = value }
        }
    }

    override suspend fun currentAccessToken(): String? = accessToken.first()

    override suspend fun currentRefreshToken(): String? =
        context.tokenDataStore.data.map { it[KEY_REFRESH] }.first()

    override suspend fun clearTokens() {
        context.tokenDataStore.edit {
            it.remove(KEY_ACCESS)
            it.remove(KEY_REFRESH)
            it[KEY_LOGGED_OUT] = true
        }
    }

    override suspend fun wipe() {
        context.tokenDataStore.edit { it.clear() }
    }
}
