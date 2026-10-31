package com.ragclient.android.domain.model

data class RagSourceModel(
    val chunkId: String,
    val documentId: String,
    val pageNumber: Int?,
    val score: Float
)
