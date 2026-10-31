package com.ragclient.android.feature.ask

import com.ragclient.android.domain.model.RagSourceModel
import com.ragclient.android.domain.model.StreamEvent

/**
 * Reference MVI feature (client plan section 4).
 * The reducer is a pure function: SSE rule from the plan — an error
 * NEVER wipes already-streamed tokens, it only adds the banner.
 */

data class AskState(
    val question: String = "",
    val answerSoFar: String = "",
    val sources: List<RagSourceModel> = emptyList(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)

sealed interface AskIntent {
    data class Submit(val question: String) : AskIntent
    data object Stop : AskIntent
    data class QuestionChanged(val question: String) : AskIntent
}

sealed interface AskResult {
    data object GenerationStarted : AskResult
    data class SourcesReceived(val sources: List<RagSourceModel>) : AskResult
    data class TokenAppended(val token: String) : AskResult
    data class Completed(val answer: String) : AskResult
    data class Failed(val code: String, val message: String) : AskResult
    data object Stopped : AskResult
}

object AskReducer {

    fun reduce(state: AskState, result: AskResult): AskState = when (result) {
        AskResult.GenerationStarted ->
            state.copy(
                answerSoFar = "",
                sources = emptyList(),
                errorMessage = null,
                isGenerating = true
            )

        is AskResult.SourcesReceived ->
            state.copy(sources = result.sources)

        is AskResult.TokenAppended ->
            state.copy(answerSoFar = state.answerSoFar + result.token)

        is AskResult.Completed ->
            state.copy(
                isGenerating = false,
                answerSoFar = result.answer.ifBlank { state.answerSoFar }
            )

        is AskResult.Failed ->
            state.copy(
                isGenerating = false,
                errorMessage = when (result.code) {
                    "UPSTREAM_UNAVAILABLE", "CONNECTION_FAILED" ->
                        "The AI service is busy — please retry."
                    else -> result.message.ifBlank { "Something went wrong." }
                }
            )

        AskResult.Stopped -> state.copy(isGenerating = false)
    }
}
