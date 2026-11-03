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
