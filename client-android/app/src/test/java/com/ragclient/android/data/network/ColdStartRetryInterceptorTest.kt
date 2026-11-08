package com.ragclient.android.data.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ColdStartRetryInterceptorTest {

    @Test
    fun retriesGatewayErrorsThenReturnsTheSuccess() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setResponseCode(200).setBody("up"))
        server.start()
        try {
            val response = client(enabled = true)
                .newCall(Request.Builder().url(server.url("/")).build())
                .execute()
            assertEquals(200, response.code)
            assertEquals("up", response.body.string())
            assertEquals(3, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun doesNotReplayAClientError() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(400).setBody("no"))
        server.start()
        try {
            val response = client(enabled = true)
                .newCall(Request.Builder().url(server.url("/")).build())
                .execute()
            assertEquals(400, response.code)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun disabledInterceptorDoesNotRetry() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        server.start()
        try {
            val response = client(enabled = false)
                .newCall(Request.Builder().url(server.url("/")).build())
                .execute()
            assertEquals(503, response.code)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun doesNotReplayALargeBody() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("up"))
        server.start()
        try {
            val body = "x".repeat(70_000).toRequestBody("text/plain".toMediaType())
            val response = client(enabled = true)
                .newCall(Request.Builder().url(server.url("/")).post(body).build())
                .execute()
            assertEquals(503, response.code)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private fun client(enabled: Boolean): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(ColdStartRetryInterceptor(enabled = enabled, pauseMs = 0))
            .build()
}
