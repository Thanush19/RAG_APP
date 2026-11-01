package com.ragclient.android.data.upload

import java.io.File
import java.io.FileInputStream
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * Streams a file upward and reports completion once per whole percent, so
 * a 50 MB document produces ~100 callbacks instead of one per chunk.
 *
 * Percent is measured against bytes handed to the sink; OkHttp buffers
 * ahead of the socket, so the number is an upper bound on what the server
 * has actually received.
 */
class FileProgressRequestBody(
    private val file: File,
    private val mediaType: MediaType,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType = mediaType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        var lastPercent = -1

        FileInputStream(file).use { input ->
            val chunk = ByteArray(CHUNK_BYTES)
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                sink.write(chunk, 0, read)
                written += read

                val percent = if (total <= 0L) 100 else ((written * 100) / total).toInt()
                if (percent != lastPercent || written == total) {
                    lastPercent = percent
                    onProgress(written, total)
                }
            }
        }
    }

    private companion object {
        const val CHUNK_BYTES = 16 * 1024
    }
}
