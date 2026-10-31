package com.ragclient.android.domain.model

/**
 * SSE contract from backend plan section 22:
 * sources -> token* -> done | error.
 */
sealed interface StreamEvent {

    data class Sources(val sources: List<RagSourceModel>) : StreamEvent

    data class Token(val text: String) : StreamEvent

    data class Done(val answer: String) : StreamEvent

    data class Error(val code: String, val message: String) : StreamEvent
}
