package com.ragclient.android.domain.model

enum class ChatRole { User, Assistant }

data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val scopedDocumentId: String? = null
)

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val sources: List<RagSourceModel> = emptyList()
)
