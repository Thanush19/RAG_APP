package com.ragclient.android.feature.search

import com.ragclient.android.core.mvi.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** M4 lands search results + tap-to-page deep links via RagApi.search. */

data class SearchState(
    val query: String = "",
    val results: List<com.ragclient.android.data.api.dto.RetrievedChunkDto> = emptyList(),
    val isLoading: Boolean = false
)

sealed interface SearchIntent {
    data class QueryChanged(val query: String) : SearchIntent
    data object Execute : SearchIntent
}

sealed interface SearchResult

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val ragApi: com.ragclient.android.data.api.RagApi
) : BaseViewModel<SearchState, SearchIntent, SearchResult>(SearchState()) {

    override fun handle(intent: SearchIntent) {
        if (intent is SearchIntent.QueryChanged) {
            setState { copy(query = intent.query) }
        }
    }

    override fun reduce(state: SearchState, result: SearchResult): SearchState = state
}
