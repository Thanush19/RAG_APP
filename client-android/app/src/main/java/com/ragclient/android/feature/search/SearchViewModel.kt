package com.ragclient.android.feature.search

import androidx.lifecycle.viewModelScope
import com.ragclient.android.core.mvi.BaseViewModel
import com.ragclient.android.data.api.RagApi
import com.ragclient.android.data.api.dto.SearchRequestDto
import com.ragclient.android.data.network.ApiErrorMapper
import com.ragclient.android.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val ragApi: RagApi,
    private val documents: DocumentRepository,
    private val errorMapper: ApiErrorMapper
) : BaseViewModel<SearchState, SearchIntent, SearchResult>(SearchState()) {

    override fun handle(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.ChangeQuery ->
                reduceResult(SearchResult.QueryChanged(intent.query))

            SearchIntent.Clear -> reduceResult(SearchResult.Cleared)

            SearchIntent.Run -> search()
        }
    }

    private fun search() {
        val current = state.value
        if (!current.canSearch) return

        reduceResult(SearchResult.Started)
        viewModelScope.launch {
            runCatching {
                ragApi.search(
                    SearchRequestDto(
                        query = current.query.trim(),
                        topK = RESULTS_LIMIT,
                        minScore = MIN_SCORE
                    )
                )
            }.onSuccess { response ->
                val names = documents.observeLibrary().first()
                    .associate { document -> document.id to document.fileName }
                reduceResult(
                    SearchResult.Loaded(
                        response.results.map { chunk ->
                            SearchHit(
                                chunkId = chunk.chunkId,
                                documentId = chunk.documentId,
                                pageNumber = chunk.pageNumber,
                                text = chunk.text,
                                score = chunk.score,
                                fileName = names[chunk.documentId] ?: "document"
                            )
                        }
                    )
                )
            }.onFailure { error ->
                reduceResult(
                    SearchResult.Failed(
                        errorMapper.describe(error, "Search failed.").message
                    )
                )
            }
        }
    }

    override fun reduce(state: SearchState, result: SearchResult): SearchState =
        SearchReducer.reduce(state, result)

    private companion object {
        const val RESULTS_LIMIT = 12

        /**
         * Cosine cut-off the server applies *after* top-K retrieval, so raising
         * it can only remove results. Measured on the live index: correct
         * matches land at 0.07–0.23, so anything stricter starts eating them.
         */
        const val MIN_SCORE = 0.05f
    }
}
