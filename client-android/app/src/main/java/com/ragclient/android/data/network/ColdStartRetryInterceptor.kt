package com.ragclient.android.data.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Render's free instance answers a cold start with a hung socket or an
 * immediate 502/503/504. Prod retries those a few times. Large or one-shot
 * bodies (uploads) are not replayed — a second copy of the file would land
 * in the library.
 */
class ColdStartRetryInterceptor(
    private val enabled: Boolean,
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val pauseMs: Long = PAUSE_MS
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!enabled) return chain.proceed(request)

        val timed = if (request.header("Accept")?.contains("text/event-stream") == true) {
            chain
        } else {
            // Read timeout bounds the wait for the first response byte. SSE
            // keeps the client's unlimited read timeout so a slow token
            // stream is not cut off.
            chain.withReadTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }

        var attempt = 1
        while (true) {
            try {
                val response = timed.proceed(request)
                if (!shouldReplay(request, response.code) || attempt >= maxAttempts) {
                    return response
                }
                response.close()
            } catch (error: IOException) {
                if (!canReplay(request) || attempt >= maxAttempts) throw error
            }
            if (pauseMs > 0) Thread.sleep(pauseMs * attempt)
            attempt += 1
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val PAUSE_MS = 2_000L
        const val READ_TIMEOUT_SECONDS = 90
        /** Login and refresh bodies are a few hundred bytes; a PDF is not. */
        const val MAX_REPLAY_BYTES = 64L * 1024L

        private val RETRY_STATUS = setOf(502, 503, 504)

        fun shouldReplay(request: Request, code: Int): Boolean =
            code in RETRY_STATUS && canReplay(request)

        fun canReplay(request: Request): Boolean {
            val body = request.body ?: return true
            if (body.isOneShot()) return false
            val length = body.contentLength()
            return length in 0..MAX_REPLAY_BYTES
        }
    }
}
