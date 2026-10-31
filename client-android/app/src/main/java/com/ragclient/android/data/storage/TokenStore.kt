package com.ragclient.android.data.storage

import android.content.Context
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

/**
 * Session token store (client plan section 3). Tokens are kept in
 * DataStore Preferences; moving this file to the encrypted
 * (security-crypto) provider is an M1 hardening step — the call
 * sites do not change.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_ACCESS = stringPreferencesKey("access_token")
        private val KEY_REFRESH = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
    }

    val accessToken: Flow<String?> =
        context.tokenDataStore.data.map { it[KEY_ACCESS] }

    val refreshToken: Flow<String?> =
        context.tokenDataStore.data.map { it[KEY_REFRESH] }

    suspend fun saveTokens(accessToken: String, refreshToken: String?, userId: String?) {
        context.tokenDataStore.edit {
            it[KEY_ACCESS] = accessToken
            refreshToken?.let { value -> it[KEY_REFRESH] = value }
            userId?.let { value -> it[KEY_USER_ID] = value }
        }
    }

    suspend fun currentAccessToken(): String? =
        accessToken.first()

    suspend fun currentRefreshToken(): String? =
        refreshToken.first()

    suspend fun clear() {
        context.tokenDataStore.edit { it.clear() }
    }
}
