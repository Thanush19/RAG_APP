package com.ragclient.android.data.network

import com.ragclient.android.data.storage.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches the bearer token to every request except the auth endpoints. */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: SessionStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.contains("/auth/")) {
            return chain.proceed(request)
        }

        val token = runBlocking { tokenStore.currentAccessToken() }
        val authorized = if (token.isNullOrBlank()) {
            request
        } else {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }

        return chain.proceed(authorized)
    }
}
