package com.ragclient.android.feature.chat

import com.ragclient.android.domain.model.ChatMessage
import com.ragclient.android.domain.model.RagSourceModel

data class ChatState(
    val conversationId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val prompt: String = "",
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val streamingSources: List<RagSourceModel> = emptyList(),
    val scopedDocumentId: String? = null,
    val error: String? = null
) {
    val canSend: Boolean get() = !isGenerating && prompt.isNotBlank()
}

sealed interface ChatIntent {
    data class ChangePrompt(val value: String) : ChatIntent
    data object Send : ChatIntent
    data object Stop : ChatIntent
    data object ClearScope : ChatIntent
    data class ScopeTo(val documentId: String) : ChatIntent
    data object DismissError : ChatIntent
}

sealed interface ChatResult {
    data class ConversationLoaded(
        val conversationId: String,
        val scopedDocumentId: String?
    ) : ChatResult

    data class MessagesUpdated(val messages: List<ChatMessage>) : ChatResult
    data class PromptChanged(val value: String) : ChatResult
    data object QuestionSent : ChatResult
    data class SourcesReceived(val sources: List<RagSourceModel>) : ChatResult
    data class TokenReceived(val token: String) : ChatResult
    data class StreamCompleted(val answer: String) : ChatResult

    /** Message-level failure: the transcript is never discarded. */
    data class StreamFailed(
        val message: String,
        val partialAnswer: String
    ) : ChatResult

    data class ScopeChanged(val documentId: String?) : ChatResult
    data object ErrorDismissed : ChatResult
}

/** Pure state transitions — unit tested without Android. */
object ChatReducer {

    fun reduce(state: ChatState, result: ChatResult): ChatState = when (result) {
        is ChatResult.ConversationLoaded -> state.copy(
            conversationId = result.conversationId,
            scopedDocumentId = result.scopedDocumentId,
            isLoading = false
        )

        is ChatResult.MessagesUpdated -> state.copy(
            messages = result.messages,
            isLoading = false
        )

        is ChatResult.PromptChanged -> state.copy(prompt = result.value)

        ChatResult.QuestionSent -> state.copy(
            prompt = "",
            error = null,
            isGenerating = true,
            streamingText = "",
            streamingSources = emptyList()
        )

        is ChatResult.SourcesReceived -> state.copy(streamingSources = result.sources)

        is ChatResult.TokenReceived -> state.copy(
            streamingText = state.streamingText + result.token
        )

        is ChatResult.StreamCompleted -> state.copy(
            isGenerating = false,
            streamingText = "",
            streamingSources = emptyList(),
            error = null
        )

        is ChatResult.StreamFailed -> state.copy(
            isGenerating = false,
            // The partial answer survives the failure: it is either still
            // on screen or persisted as a message by the view model.
            streamingText = result.partialAnswer,
            error = result.message
        )

        is ChatResult.ScopeChanged -> state.copy(
            scopedDocumentId = result.documentId,
            streamingText = "",
            streamingSources = emptyList(),
            isGenerating = false
        )

        ChatResult.ErrorDismissed -> state.copy(
            error = null,
            streamingText = "",
            streamingSources = emptyList()
        )
    }
}
