package com.ragclient.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchRequestDto(
    val query: String,
    val topK: Int? = null,
    val documentId: String? = null,
    val minScore: Float? = null
)

@Serializable
data class RetrievedChunkDto(
    val chunkId: String,
    val documentId: String,
    val pageNumber: Int? = null,
    val text: String = "",
    val score: Float = 0f
)

@Serializable
data class SearchResponseDto(
    val results: List<RetrievedChunkDto> = emptyList()
)

@Serializable
data class AskRequestDto(
    val question: String,
    val topK: Int? = null,
    val minScore: Float? = null
)

@Serializable
data class RagSourceDto(
    val chunkId: String,
    val documentId: String,
    val pageNumber: Int? = null,
    val score: Float = 0f
)

@Serializable
data class AskResponseDto(
    val answer: String,
    val sources: List<RagSourceDto> = emptyList()
)

@Serializable
data class StreamTokenPayloadDto(
    val t: String = ""
)

@Serializable
data class StreamDonePayloadDto(
    val answer: String = ""
)

@Serializable
data class StreamErrorPayloadDto(
    val code: String = "INTERNAL_ERROR",
    val message: String = ""
)
