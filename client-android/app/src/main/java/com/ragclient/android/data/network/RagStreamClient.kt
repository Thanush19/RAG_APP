package com.ragclient.android.data.network

import com.ragclient.android.data.api.dto.RagSourceDto
import com.ragclient.android.data.api.dto.StreamDonePayloadDto
import com.ragclient.android.data.api.dto.StreamErrorPayloadDto
import com.ragclient.android.data.api.dto.StreamTokenPayloadDto
import com.ragclient.android.domain.model.RagSourceModel
import com.ragclient.android.domain.model.StreamEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

/**
 * POST-capable SSE client for /api/v1/rag/ask/stream (client plan 6:
 * EventSource is GET-only in browsers, but okhttp-sse supports POST;
 * we additionally parse the named events into StreamEvent).
 */
@Singleton
class RagStreamClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {

    fun askStream(
        question: String,
        topK: Int?,
        minScore: Float?,
        documentId: String? = null
    ): Flow<StreamEvent> =
        callbackFlow {
            val bodyJson = json.encodeToString(
                AskStreamRequestSerializer.serializer(),
                AskStreamRequestSerializer(question, topK, minScore, documentId)
            )

            val request = Request.Builder()
                .url(BASE_URL + "api/v1/rag/ask/stream")
                .header("Accept", "text/event-stream")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val listener = object : EventSourceListener() {

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    when (type) {
                        "sources" -> {
                            val sources = json.decodeFromString(
                                kotlinx.serialization.builtins.ListSerializer(
                                    RagSourceDto.serializer()
                                ), data
                            )
                            trySend(
                                StreamEvent.Sources(
                                    sources.map {
                                        RagSourceModel(
                                            chunkId = it.chunkId,
                                            documentId = it.documentId,
                                            pageNumber = it.pageNumber,
                                            score = it.score
                                        )
                                    }
                                )
                            )
                        }

                        "token" -> {
                            val token = json.decodeFromString(
                                StreamTokenPayloadDto.serializer(), data
                            )
                            trySend(StreamEvent.Token(token.t))
                        }

                        "done" -> {
                            val answer = json.decodeFromString(
                                StreamDonePayloadDto.serializer(), data
                            )
                            trySend(StreamEvent.Done(answer.answer))
                            close()
                        }

                        "error" -> {
                            val error = json.decodeFromString(
                                StreamErrorPayloadDto.serializer(), data
                            )
                            trySend(StreamEvent.Error(error.code, error.message))
                            close()
                        }
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: okhttp3.Response?
                ) {
                    trySend(
                        StreamEvent.Error(
                            code = "CONNECTION_FAILED",
                            message = t?.message ?: "stream failed"
                        )
                    )
                    close()
                }
            }

            val eventSource = EventSources.createFactory(client)
                .newEventSource(request, listener)

            awaitClose { eventSource.cancel() }
        }

    private companion object {
        // BuildConfig is not visible from this generic spot; reuse the
        // same constant source as NetworkModule.
        val BASE_URL: String = com.ragclient.android.BuildConfig.API_BASE_URL
    }
}

@kotlinx.serialization.Serializable
private data class AskStreamRequestSerializer(
    val question: String,
    val topK: Int? = null,
    val minScore: Float? = null,
    val documentId: String? = null
)
