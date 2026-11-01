package com.ragclient.android.feature.chat

import com.ragclient.android.domain.model.ChatMessage
import com.ragclient.android.domain.model.ChatRole
import com.ragclient.android.domain.model.RagSourceModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReducerTest {

    private val transcript = listOf(
        ChatMessage(id = 1, role = ChatRole.User, text = "Summarise section 3"),
        ChatMessage(id = 2, role = ChatRole.Assistant, text = "Section 3 covers ingestion.")
    )

    @Test
    fun `tokens append progressively`() {
        val started = ChatReducer.reduce(
            ChatState(messages = transcript),
            ChatResult.QuestionSent
        )
        val streaming = ChatReducer.reduce(
            started,
            ChatResult.TokenReceived("The answer ")
        )
        val more = ChatReducer.reduce(streaming, ChatResult.TokenReceived("is here."))

        assertEquals("The answer is here.", more.streamingText)
        assertTrue(more.isGenerating)
        assertEquals(2, more.messages.size)
    }

    @Test
    fun `failure keeps the transcript and shows a banner`() {
        val streaming = ChatState(
            messages = transcript,
            isGenerating = true,
            streamingText = "Partial"
        )

        val failed = ChatReducer.reduce(
            streaming,
            ChatResult.StreamFailed("The AI service is busy — please retry.", "Partial")
        )

        assertEquals(transcript, failed.messages)
        assertEquals("Partial", failed.streamingText)
        assertEquals("The AI service is busy — please retry.", failed.error)
        assertFalse(failed.isGenerating)
    }

    @Test
    fun `completion clears the streaming bubble`() {
        val streaming = ChatState(
            messages = transcript,
            isGenerating = true,
            streamingText = "Full answer",
            streamingSources = listOf(RagSourceModel("c", "d", 3, 0.7f))
        )

        val done = ChatReducer.reduce(streaming, ChatResult.StreamCompleted("Full answer"))

        assertFalse(done.isGenerating)
        assertEquals("", done.streamingText)
        assertTrue(done.streamingSources.isEmpty())
        assertNull(done.error)
    }

    @Test
    fun `question clears the prompt and starts generation`() {
        val typed = ChatState(prompt = "  What is in page 4?  ")

        val sent = ChatReducer.reduce(typed, ChatResult.QuestionSent)

        assertEquals("", sent.prompt)
        assertTrue(sent.isGenerating)
        assertEquals("", sent.streamingText)
    }

    @Test
    fun `scope change stops the current stream`() {
        val generating = ChatState(isGenerating = true, streamingText = "half")

        val scoped = ChatReducer.reduce(generating, ChatResult.ScopeChanged("doc-1"))

        assertEquals("doc-1", scoped.scopedDocumentId)
        assertFalse(scoped.isGenerating)
        assertEquals("", scoped.streamingText)
    }

    @Test
    fun `send is disabled while generating`() {
        assertFalse(ChatState(isGenerating = true, prompt = "hi").canSend)
        assertFalse(ChatState(isGenerating = false, prompt = "   ").canSend)
        assertTrue(ChatState(isGenerating = false, prompt = "hi").canSend)
    }
}
