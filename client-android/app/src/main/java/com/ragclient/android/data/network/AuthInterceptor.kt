package com.ragclient.android.data.network

import com.ragclient.android.data.storage.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the bearer token to every request. The 401 -> refresh ->
 * retry interceptor is M1 work; until then a 401 simply surfaces to
 * the repos.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenStore.currentAccessToken() }

        val request = chain.request().let { original ->
            if (token.isNullOrBlank()) {
                original
            } else {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
        }

        return chain.proceed(request)
    }
}
