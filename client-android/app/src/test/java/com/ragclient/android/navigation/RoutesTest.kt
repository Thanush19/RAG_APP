package com.ragclient.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutesTest {

    @Test
    fun `staged viewer route uses the staging directory and file name`() {
        val staged = "/data/user/0/com.ragclient.android.dev/files/uploads/1793512746398/report.pdf"
        assertEquals("viewerStaged/1793512746398/report.pdf", Routes.stagedViewer(staged))
    }

    @Test
    fun `a staged path without a file name has no route`() {
        assertNull(Routes.stagedViewer("/data/user/0/com.ragclient.android.dev/files/uploads/"))
    }
}

class DocumentChatRoutesTest {

    @Test
    fun `asking a document routes to its own resumable thread`() {
        assertEquals(
            "chat/scoped?scope=doc-1",
            Routes.chatForDocument("doc-1")
        )
    }

    @Test
    fun `a new thread inside a document keeps the scope`() {
        assertEquals(
            "chat/new?scope=doc-1",
            Routes.newChatInDocument("doc-1")
        )
    }

    @Test
    fun `a plain chat has no scope and a tapped thread keeps its id`() {
        assertEquals("chat/new?scope=none", Routes.chat(Routes.CHAT_NEW))
        assertEquals("chat/6f2a?scope=none", Routes.chat("6f2a"))
    }
}
