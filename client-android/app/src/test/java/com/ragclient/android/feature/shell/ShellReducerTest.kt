package com.ragclient.android.feature.shell

import com.ragclient.android.data.upload.UploadJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellReducerTest {

    private val queued = UploadJob(workId = "w1", fileName = "resume.pdf", percent = 0, running = false)
    private val running = UploadJob(workId = "w1", fileName = "resume.pdf", percent = 42, running = true)

    @Test
    fun `uploads mirror the work manager queue and leave everything else alone`() {
        val state = ShellReducer.reduce(ShellState(), ShellResult.UploadsChanged(listOf(running)))

        assertEquals(listOf(running), state.uploads)
    }

    @Test
    fun `a banner does not drop an in-flight upload row`() {
        val withUpload = ShellState().copy(uploads = listOf(queued))

        val afterBanner = ShellReducer.reduce(withUpload, ShellResult.Banner("Added to the queue"))
        val cleared = ShellReducer.reduce(afterBanner, ShellResult.BannerCleared)

        assertEquals("Added to the queue", afterBanner.banner)
        assertEquals(listOf(queued), afterBanner.uploads)
        assertEquals("the queue must outlive the toast", listOf(queued), cleared.uploads)
        assertTrue(cleared.banner == null)
    }

    @Test
    fun `an empty queue report clears the section once the work finishes`() {
        val withUpload = ShellState().copy(uploads = listOf(running))

        val done = ShellReducer.reduce(withUpload, ShellResult.UploadsChanged(emptyList()))

        assertTrue(done.uploads.isEmpty())
    }
}
