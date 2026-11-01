package com.ragclient.android.data.upload

import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileProgressRequestBodyTest {

    @Test
    fun `reports each whole percent once and finishes at the full length`() {
        val file = tempFile(200 * 1024)
        val seen = mutableListOf<Pair<Long, Long>>()

        FileProgressRequestBody(file, MEDIA_TYPE) { written, total -> seen += written to total }
            .writeTo(Buffer())

        val percents = seen.map { (written, total) -> ((written * 100) / total).toInt() }

        assertEquals(200L * 1024, seen.last().second)
        assertEquals("the last tick must cover the whole file", 200L * 1024, seen.last().first)
        assertEquals("percent must never go backwards", percents.last(), percents.max())
        assertEquals(
            "each percentage may be reported only once",
            percents.distinct().size,
            percents.size
        )
    }

    @Test
    fun `content length is the file size`() {
        val file = tempFile(1234)
        assertEquals(1234L, FileProgressRequestBody(file, MEDIA_TYPE) { _, _ -> }.contentLength())
    }

    @Test
    fun `an empty file writes nothing and never divides by zero`() {
        val file = tempFile(0)
        val seen = mutableListOf<Pair<Long, Long>>()

        FileProgressRequestBody(file, MEDIA_TYPE) { written, total -> seen += written to total }
            .writeTo(Buffer())

        assertTrue("nothing was written, so no tick is expected", seen.isEmpty())
    }

    private fun tempFile(sizeBytes: Int): File =
        File.createTempFile("payload", ".bin").apply {
            deleteOnExit()
            writeBytes(ByteArray(sizeBytes))
        }

    private companion object {
        val MEDIA_TYPE = "application/octet-stream".toMediaType()
    }
}
