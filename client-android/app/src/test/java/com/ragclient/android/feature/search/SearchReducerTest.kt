package com.ragclient.android.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchReducerTest {

    private val hit = SearchHit(
        chunkId = "c1",
        documentId = "d1",
        pageNumber = 3,
        text = "…retries with exponential backoff…",
        score = 0.58f,
        fileName = "architecture.pdf"
    )

    @Test
    fun `new query clears a stale error`() {
        val failed = SearchState(query = "abc", error = "Search failed.", hasSearched = true)

        val typed = SearchReducer.reduce(failed, SearchResult.QueryChanged("abcd"))

        assertEquals("abcd", typed.query)
        assertNull(typed.error)
    }

    @Test
    fun `running a search clears previous results`() {
        val loaded = SearchState(query = "abc", results = listOf(hit), hasSearched = true)

        val started = SearchReducer.reduce(loaded, SearchResult.Started)

        assertTrue(started.isLoading)
        assertFalse(started.canSearch)
        assertEquals(emptyList<SearchHit>(), started.results)
    }

    @Test
    fun `loaded results end the loading state`() {
        val started = SearchState(query = "abc", isLoading = true)

        val done = SearchReducer.reduce(started, SearchResult.Loaded(listOf(hit)))

        assertFalse(done.isLoading)
        assertTrue(done.hasSearched)
        assertEquals(1, done.results.size)
    }

    @Test
    fun `clear resets to the initial state`() {
        val used = SearchState(
            query = "abc",
            results = listOf(hit),
            hasSearched = true,
            error = "boom"
        )

        val cleared = SearchReducer.reduce(used, SearchResult.Cleared)

        assertEquals(SearchState(), cleared)
    }
}
