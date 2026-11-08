package com.ragclient.android.data.network

import com.ragclient.android.BuildConfig
import com.ragclient.android.data.api.AuthApi
import com.ragclient.android.data.api.DocumentApi
import com.ragclient.android.data.api.RagApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun hostWakeMonitor(): HostWakeMonitor = HostWakeMonitor(
        // The LAN dev server does not sleep. Only the Render prod host does.
        enabled = !BuildConfig.IS_DEV_BUILD,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        slowAfterMs = HostWakeMonitor.SLOW_AFTER_MS
    )

    @Provides
    @Singleton
    fun okHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        hostWakeMonitor: HostWakeMonitor
    ): OkHttpClient {
        val prod = !BuildConfig.IS_DEV_BUILD
        return OkHttpClient.Builder()
            .addInterceptor(WakeTrackingInterceptor(hostWakeMonitor))
            .addInterceptor(authInterceptor)
            .addInterceptor(
                ColdStartRetryInterceptor(enabled = prod)
            )
            .authenticator(tokenAuthenticator)
            // Render's proxy accepts the socket quickly, then holds it while
            // the free instance boots. The read wait is per-call (see the
            // retry interceptor); SSE keeps an unlimited read timeout.
            .connectTimeout(if (prod) 30 else 15, TimeUnit.SECONDS)
            .apply {
                // Default write timeout is 10s, which is enough on the LAN.
                // A cold prod host plus a PDF needs much longer.
                if (prod) writeTimeout(5, TimeUnit.MINUTES)
            }
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    @Provides
    @Singleton
    @BaseUrl
    fun baseUrl(): String = BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json, @BaseUrl baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun documentApi(retrofit: Retrofit): DocumentApi = retrofit.create(DocumentApi::class.java)

    @Provides
    @Singleton
    fun ragApi(retrofit: Retrofit): RagApi = retrofit.create(RagApi::class.java)
}
