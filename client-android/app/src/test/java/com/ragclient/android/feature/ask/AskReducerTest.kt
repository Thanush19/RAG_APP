package com.ragclient.android.feature.ask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure MVI reducer tests (client plan 7) — no Android required. */
class AskReducerTest {

    @Test
    fun `tokens append progressively`() {
        var state = AskState()
        state = AskReducer.reduce(state, AskResult.GenerationStarted)
        state = AskReducer.reduce(state, AskResult.TokenAppended("Hello"))
        state = AskReducer.reduce(state, AskResult.TokenAppended(" world"))
        assertEquals("Hello world", state.answerSoFar)
        assertEquals(true, state.isGenerating)
    }

    @Test
    fun `error keeps already streamed tokens and shows banner`() {
        var state = AskReducer.reduce(AskState(), AskResult.GenerationStarted)
        state = AskReducer.reduce(state, AskResult.TokenAppended("Partial"))
        state = AskReducer.reduce(
            state,
            AskResult.Failed("UPSTREAM_UNAVAILABLE", "LLM service is unavailable")
        )
        assertEquals("Partial", state.answerSoFar)
        assertEquals(false, state.isGenerating)
        assertEquals("The AI service is busy — please retry.", state.errorMessage)
    }

    @Test
    fun `done clears generating and prefers full answer`() {
        var state = AskReducer.reduce(AskState(), AskResult.GenerationStarted)
        state = AskReducer.reduce(state, AskResult.TokenAppended("Full "))
        state = AskReducer.reduce(state, AskResult.Completed("Full answer"))
        assertEquals("Full answer", state.answerSoFar)
        assertNull(state.errorMessage)
        assertEquals(false, state.isGenerating)
    }
}
