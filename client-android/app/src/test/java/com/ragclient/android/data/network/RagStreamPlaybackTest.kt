package com.ragclient.android.data.network

import com.ragclient.android.domain.model.StreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Backend plan section 22 protocol, replayed: sources -> token* -> done,
 * and the mid-stream error case where tokens already sent must survive.
 */
class RagStreamPlaybackTest {

    private lateinit var server: MockWebServer
    private lateinit var client: RagStreamClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = RagStreamClient(
            client = OkHttpClient(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
            baseUrl = server.url("/").toString()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sse(vararg frames: String): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(frames.joinToString(separator = "\n\n") + "\n\n")

    @Test
    fun `happy path emits sources then tokens then done`() = runBlocking {
        server.enqueue(
            sse(
                """
                event: sources
                data: [{"chunkId":"c1","documentId":"d1","pageNumber":2,"score":0.71}]
                """.trimIndent(),
                """
                event: token
                data: {"t":"Hello"}
                """.trimIndent(),
                """
                event: token
                data: {"t":" there"}
                """.trimIndent(),
                """
                event: done
                data: {"answer":"Hello there"}
                """.trimIndent()
            )
        )

        val events = client.askStream("Hello?", null, null, null).toList()

        assertEquals(4, events.size)
        assertTrue(events[0] is StreamEvent.Sources)
        assertEquals("Hello", (events[1] as StreamEvent.Token).text)
        assertEquals(" there", (events[2] as StreamEvent.Token).text)
        assertEquals("Hello there", (events[3] as StreamEvent.Done).answer)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.endsWith("/api/v1/rag/ask/stream"))
    }

    @Test
    fun `mid-stream error keeps the tokens already delivered`() = runBlocking {
        server.enqueue(
            sse(
                """
                event: sources
                data: [{"chunkId":"c1","documentId":"d1","pageNumber":4,"score":0.6}]
                """.trimIndent(),
                """
                event: token
                data: {"t":"Partial"}
                """.trimIndent(),
                """
                event: error
                data: {"code":"UPSTREAM_UNAVAILABLE","message":"model timed out"}
                """.trimIndent()
            )
        )

        val events = client.askStream("Explain page 4", null, null, null).toList()

        assertEquals(3, events.size)
        assertEquals("Partial", (events[1] as StreamEvent.Token).text)
        val error = events[2] as StreamEvent.Error
        assertEquals("UPSTREAM_UNAVAILABLE", error.code)
        assertEquals(1, (events[0] as StreamEvent.Sources).sources.size)
    }

    @Test
    fun `document scope is sent in the request body`() = runBlocking {
        server.enqueue(
            sse(
                """
                event: done
                data: {"answer":"ok"}
                """.trimIndent()
            )
        )

        client.askStream("question", null, 0.2f, "doc-9").toList()

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"documentId\":\"doc-9\""))
        assertTrue(body.contains("\"minScore\":0.2"))
    }
}
