package com.ragclient.android.feature.chat

import com.ragclient.android.data.repository.LibraryDocument
import com.ragclient.android.domain.model.ChatMessage
import com.ragclient.android.domain.model.ChatRole
import com.ragclient.android.domain.model.DocumentStatus
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

class ScopedDocumentNameTest {

    private val library = listOf(
        LibraryDocument("d1", "resume_srivastava_rahul.pdf", "pdf", 128_000L, DocumentStatus.READY, null)
    )

    @Test
    fun `either order resolves the name once both halves are known`() {
        val libraryFirst = ChatReducer.reduce(
            ChatState(),
            ChatResult.LibraryChanged(library)
        )
        val scoped = ChatReducer.reduce(
            libraryFirst,
            ChatResult.ConversationLoaded("c1", "d1")
        )
        assertEquals("resume_srivastava_rahul.pdf", scoped.scopedDocumentName)

        val loadedFirst = ChatReducer.reduce(ChatState(), ChatResult.ConversationLoaded("c1", "d1"))
        assertNull(loadedFirst.scopedDocumentName)
        assertEquals(
            "resume_srivastava_rahul.pdf",
            ChatReducer.reduce(loadedFirst, ChatResult.LibraryChanged(library)).scopedDocumentName
        )
    }

    @Test
    fun `an unscoped thread and a deleted document both have no name`() {
        val scoped = ChatState(
            scopedDocumentId = "d1",
            documents = library,
            scopedDocumentName = "resume_srivastava_rahul.pdf"
        )

        assertNull(ChatReducer.reduce(scoped, ChatResult.ScopeChanged(null)).scopedDocumentName)
        assertNull(
            ChatReducer.reduce(scoped, ChatResult.LibraryChanged(emptyList())).scopedDocumentName
        )
    }

    @Test
    fun `a deleted document widens its thread back to the whole library`() {
        val scoped = ChatState(
            scopedDocumentId = "d1",
            documents = library,
            scopedDocumentName = "resume_srivastava_rahul.pdf"
        )

        val widened = ChatReducer.reduce(scoped, ChatResult.LibraryChanged(emptyList()))
        assertNull(widened.scopedDocumentId)

        val stillScoped = ChatReducer.reduce(scoped, ChatResult.LibraryChanged(library))
        assertEquals("d1", stillScoped.scopedDocumentId)
    }
}
