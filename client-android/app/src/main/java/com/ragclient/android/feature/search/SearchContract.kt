package com.ragclient.android.feature.search

data class SearchHit(
    val chunkId: String,
    val documentId: String,
    val pageNumber: Int?,
    val text: String,
    val score: Float,
    val fileName: String
)

data class SearchState(
    val query: String = "",
    val minScore: Float = DEFAULT_MIN_SCORE,
    val results: List<SearchHit> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false
) {
    val canSearch: Boolean get() = !isLoading && query.isNotBlank()

    companion object {
        const val DEFAULT_MIN_SCORE = 0.05f
    }
}

sealed interface SearchIntent {
    data class ChangeQuery(val query: String) : SearchIntent
    data class ChangeMinScore(val minScore: Float) : SearchIntent
    data object Run : SearchIntent
    data object Clear : SearchIntent
}

sealed interface SearchResult {
    data class QueryChanged(val query: String) : SearchResult
    data class MinScoreChanged(val minScore: Float) : SearchResult
    data object Started : SearchResult
    data class Loaded(val hits: List<SearchHit>) : SearchResult
    data class Failed(val message: String) : SearchResult
    data object Cleared : SearchResult
}

object SearchReducer {

    fun reduce(state: SearchState, result: SearchResult): SearchState = when (result) {
        is SearchResult.QueryChanged -> state.copy(query = result.query, error = null)

        is SearchResult.MinScoreChanged -> state.copy(minScore = result.minScore)

        SearchResult.Started -> state.copy(
            isLoading = true,
            error = null,
            results = emptyList()
        )

        is SearchResult.Loaded -> state.copy(
            isLoading = false,
            hasSearched = true,
            results = result.hits,
            error = null
        )

        is SearchResult.Failed -> state.copy(
            isLoading = false,
            hasSearched = true,
            error = result.message,
            results = emptyList()
        )

        SearchResult.Cleared -> SearchState()
    }
}
