package com.ragclient.android.feature.ask

import androidx.lifecycle.viewModelScope
import com.ragclient.android.core.mvi.BaseViewModel
import com.ragclient.android.data.network.RagStreamClient
import com.ragclient.android.domain.model.StreamEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AskViewModel @Inject constructor(
    private val ragStreamClient: RagStreamClient
) : BaseViewModel<AskState, AskIntent, AskResult>(AskState()) {

    private var streamJob: Job? = null

    override fun handle(intent: AskIntent) {
        when (intent) {
            is AskIntent.QuestionChanged ->
                setState { copy(question = intent.question) }

            is AskIntent.Submit -> {
                if (intent.question.isBlank() || state.value.isGenerating) {
                    return
                }
                reduceResult(AskResult.GenerationStarted)
                streamJob = viewModelScope.launch {
                    ragStreamClient
                        .askStream(intent.question, topK = 5, minScore = null)
                        .collect { event ->
                            when (event) {
                                is StreamEvent.Sources ->
                                    reduceResult(AskResult.SourcesReceived(event.sources))

                                is StreamEvent.Token ->
                                    reduceResult(AskResult.TokenAppended(event.text))

                                is StreamEvent.Done ->
                                    reduceResult(AskResult.Completed(event.answer))

                                is StreamEvent.Error ->
                                    reduceResult(
                                        AskResult.Failed(event.code, event.message)
                                    )
                            }
                        }
                }
            }

            AskIntent.Stop -> {
                streamJob?.cancel()
                streamJob = null
                reduceResult(AskResult.Stopped)
            }
        }
    }

    override fun reduce(state: AskState, result: AskResult): AskState =
        AskReducer.reduce(state, result)
}
