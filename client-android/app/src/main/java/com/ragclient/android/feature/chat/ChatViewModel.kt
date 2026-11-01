package com.ragclient.android.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ragclient.android.core.mvi.BaseViewModel
import com.ragclient.android.data.network.RagStreamClient
import com.ragclient.android.data.repository.ConversationRepository
import com.ragclient.android.domain.model.RagSourceModel
import com.ragclient.android.domain.model.StreamEvent
import com.ragclient.android.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val conversations: ConversationRepository,
    private val ragStreamClient: RagStreamClient
) : BaseViewModel<ChatState, ChatIntent, ChatResult>(ChatState()) {

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            val requested = savedStateHandle.get<String>(ARG_CONVERSATION)
            val scope = Routes.scopeOf(savedStateHandle.get<String>(ARG_SCOPE))
            val conversationId = when {
                requested == NEW_CONVERSATION -> conversations.newConversation(scope)
                else -> conversations.openOrCreate(scope)
            }

            reduceResult(
                ChatResult.ConversationLoaded(
                    conversationId = conversationId,
                    scopedDocumentId = conversations.observeScopedDocumentId(conversationId)
                        .first()
                )
            )

            launch {
                conversations.observeMessages(conversationId)
                    .collect { reduceResult(ChatResult.MessagesUpdated(it)) }
            }
        }
    }

    override fun handle(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.ChangePrompt ->
                reduceResult(ChatResult.PromptChanged(intent.value))

            ChatIntent.Send -> ask()

            ChatIntent.Stop -> stop()

            ChatIntent.ClearScope -> scopeTo(null)

            is ChatIntent.ScopeTo -> scopeTo(intent.documentId)

            ChatIntent.DismissError -> reduceResult(ChatResult.ErrorDismissed)
        }
    }

    private fun scopeTo(documentId: String?) {
        val conversationId = state.value.conversationId
        viewModelScope.launch { conversations.setScope(conversationId, documentId) }
        reduceResult(ChatResult.ScopeChanged(documentId))
    }

    private fun ask() {
        val current = state.value
        val question = current.prompt.trim()
        if (question.isEmpty() || current.isGenerating) return

        val conversationId = current.conversationId
        reduceResult(ChatResult.QuestionSent)

        streamJob = viewModelScope.launch {
            val answer = StringBuilder()
            var sources: List<RagSourceModel> = emptyList()

            try {
                conversations.appendUserMessage(conversationId, question)
                conversations.renameToFirstQuestion(conversationId, question)

                ragStreamClient.askStream(
                    question = question,
                    topK = null,
                    minScore = null,
                    documentId = current.scopedDocumentId
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Sources -> {
                            sources = event.sources
                            reduceResult(ChatResult.SourcesReceived(event.sources))
                        }

                        is StreamEvent.Token -> {
                            answer.append(event.text)
                            reduceResult(ChatResult.TokenReceived(event.text))
                        }

                        is StreamEvent.Done -> {
                            val final = event.answer.ifBlank { answer.toString() }
                            conversations.persistAssistantMessage(
                                conversationId, final, sources
                            )
                            reduceResult(ChatResult.StreamCompleted(final))
                        }

                        is StreamEvent.Error -> fail(
                            conversationId,
                            copyFor(event.code, event.message),
                            answer.toString(),
                            sources
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                fail(
                    conversationId,
                    "Can't reach the assistant right now.",
                    answer.toString(),
                    sources
                )
            }
        }
    }

    private fun stop() {
        val current = state.value
        streamJob?.cancel()
        streamJob = null
        if (current.streamingText.isNotBlank()) {
            viewModelScope.launch {
                conversations.persistAssistantMessage(
                    current.conversationId,
                    current.streamingText,
                    current.streamingSources
                )
            }
            reduceResult(ChatResult.StreamCompleted(current.streamingText))
        } else {
            reduceResult(ChatResult.StreamCompleted(""))
        }
    }

    private fun fail(
        conversationId: String,
        message: String,
        partial: String,
        sources: List<RagSourceModel>
    ) {
        if (partial.isNotBlank()) {
            viewModelScope.launch {
                conversations.persistAssistantMessage(conversationId, partial, sources)
            }
        }
        reduceResult(ChatResult.StreamFailed(message, partial))
    }

    private fun copyFor(code: String, serverMessage: String): String = when (code) {
        "UPSTREAM_UNAVAILABLE" ->
            "The AI service is busy — please retry."
        "CONNECTION_FAILED" ->
            "Can't reach the server. Check that the backend is running."
        else -> serverMessage.ifBlank { "The assistant failed mid-answer." }
    }

    override fun reduce(state: ChatState, result: ChatResult): ChatState =
        ChatReducer.reduce(state, result)

    companion object {
        const val ARG_CONVERSATION = "conversationId"
        const val ARG_SCOPE = "scope"
        const val NEW_CONVERSATION = "new"
        const val LATEST_CONVERSATION = "latest"
    }
}
