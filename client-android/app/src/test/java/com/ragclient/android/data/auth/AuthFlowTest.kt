package com.ragclient.android.data.auth

import com.ragclient.android.data.api.AuthApi
import com.ragclient.android.data.api.DocumentApi
import com.ragclient.android.data.network.ApiErrorMapper
import com.ragclient.android.data.network.AuthInterceptor
import com.ragclient.android.data.network.TokenAuthenticator
import com.ragclient.android.data.storage.Session
import com.ragclient.android.data.storage.SessionStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Exercises the real auth chain — interceptor, refresh-once
 * authenticator and repository — against MockWebServer.
 */
class AuthFlowTest {

    private class InMemorySession(
        var access: String? = null,
        var refresh: String? = null,
        var userId: String? = null,
        var email: String? = null,
        var name: String? = null
    ) : SessionStore {
        private val accessFlow = MutableStateFlow(access)
        private val sessionFlow = MutableStateFlow(Session(false))

        override val session: Flow<Session> get() = sessionFlow
        override val accessToken: Flow<String?> get() = accessFlow

        override suspend fun currentAccessToken(): String? = accessFlow.first()

        override suspend fun currentRefreshToken(): String? = refresh

        override suspend fun saveSession(
            accessToken: String,
            refreshToken: String?,
            userId: String?,
            email: String?,
            displayName: String?
        ) {
            access = accessToken
            refresh = refreshToken ?: refresh
            this.userId = userId
            this.email = email
            this.name = displayName
            accessFlow.value = accessToken
            sessionFlow.value = Session(true, userId, email, displayName)
        }

        override suspend fun clearTokens() {
            access = null
            refresh = null
            accessFlow.value = null
            sessionFlow.value = Session(false, userId, email, name)
        }

        override suspend fun wipe() = clearTokens()
    }

    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySession
    private lateinit var client: OkHttpClient
    private lateinit var authRepository: AuthRepository
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        store = InMemorySession()
        val errorMapper = ApiErrorMapper(json)
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val authApi = retrofit.create(AuthApi::class.java)
        authRepository = AuthRepository(authApi, store, errorMapper)

        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(TokenAuthenticator(store, dagger.Lazy { authRepository }))
            .build()

        val protectedRetrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        documentApi = protectedRetrofit.create(DocumentApi::class.java)
    }

    private lateinit var documentApi: DocumentApi

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login stores the session`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "accessToken": "access-1",
                  "tokenType": "Bearer",
                  "expiresIn": 3600,
                  "refreshToken": "refresh-1",
                  "user": { "id": "u-1", "name": "Demo User", "email": "demo@example.com" }
                }
                """.trimIndent()
            )
        )

        val outcome = authRepository.login("demo@example.com", "hunter2abc")

        assertEquals(AuthOutcome.SignedIn, outcome)
        assertTrue(store.session.first().isAuthenticated)
        assertEquals("Demo User", store.session.first().displayName)
        assertEquals("access-1", store.currentAccessToken())
    }

    @Test
    fun `rejected login surfaces the backend message and keeps the session closed`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"status":401,"code":"INVALID_CREDENTIALS","message":"Invalid email or password"}"""
            )
        )

        val outcome = authRepository.login("demo@example.com", "wrongpass") as AuthOutcome.Rejected

        assertEquals("Invalid email or password", outcome.message)
        assertEquals("INVALID_CREDENTIALS", outcome.code)
        assertFalse(store.session.first().isAuthenticated)
    }

    @Test
    fun `a 401 refreshes once and replays the original request`() = runTest {
        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path?.endsWith("/auth/login") == true ->
                    MockResponse().setBody(
                        """
                        {
                          "accessToken": "access-1",
                          "tokenType": "Bearer",
                          "expiresIn": 3600,
                          "refreshToken": "refresh-1",
                          "user": { "id": "u-1", "name": "Demo", "email": "demo@example.com" }
                        }
                        """.trimIndent()
                    )

                request.path?.endsWith("/auth/refresh") == true ->
                    MockResponse().setBody(
                        """{"accessToken":"access-2","tokenType":"Bearer","expiresIn":3600,"refreshToken":"refresh-2"}"""
                    )

                request.path?.endsWith("/api/v1/documents") == true &&
                    request.getHeader("Authorization") == "Bearer access-2" ->
                    MockResponse().setBody("""[{"id":"d-1","fileName":"a.pdf","status":"READY"}]""")

                else -> MockResponse().setResponseCode(401).setBody(
                    """{"status":401,"code":"TOKEN_EXPIRED","message":"expired"}"""
                )
            }
        }
        server.dispatcher = dispatcher

        authRepository.login("demo@example.com", "hunter2abc")
        val documents = documentApi.list()

        assertEquals(1, documents.size)
        assertEquals("access-2", store.currentAccessToken())
    }

    @Test
    fun `failed refresh clears the session and does not loop`() = runTest {
        var attempts = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.endsWith("/api/v1/documents") == true) attempts++
                return MockResponse().setResponseCode(401).setBody(
                    """{"status":401,"code":"TOKEN_EXPIRED","message":"expired"}"""
                )
            }
        }
        store.saveSession("access-1", null, "u-1", "demo@example.com", "Demo")

        kotlin.runCatching { documentApi.list() }

        assertFalse(store.session.first().isAuthenticated)
        assertEquals(1, attempts)
    }

    @Test
    fun `auth endpoints never carry a bearer header`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"accessToken":"access-1","tokenType":"Bearer","expiresIn":60,"refreshToken":"r1"}"""
            )
        )
        authRepository.login("demo@example.com", "hunter2abc")

        assertNull(server.takeRequest().getHeader("Authorization"))
    }
}
