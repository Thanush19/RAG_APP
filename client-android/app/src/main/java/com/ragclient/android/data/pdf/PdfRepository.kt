package com.ragclient.android.data.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ragclient.android.data.repository.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-house PDF rendering (client plan D2): `android.graphics.pdf` only,
 * no third-party dependency, and page targeting so search and citation
 * chips can deep-link straight to a page.
 */
class OpenedPdf(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    val pageCount: Int get() = renderer.pageCount

    fun render(pageIndex: Int, widthPx: Int): ImageBitmap {
        require(pageIndex in 0 until pageCount)
        return renderer.openPage(pageIndex).use { page ->
            val ratio = widthPx.toFloat() / page.width
            val height = (page.height * ratio).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap.asImageBitmap()
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

@Singleton
class PdfRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documents: DocumentRepository
) {

    suspend fun open(documentId: String): Result<OpenedPdf> = withContext(Dispatchers.IO) {
        documents.pdfBytes(documentId)
            .mapCatching { bytes ->
                val file = File(context.cacheDir, "pdf-$documentId.pdf")
                file.writeBytes(bytes)
                rendererFor(file)
            }
    }

    /**
     * A staged upload is a local file that has no server copy yet, so the
     * drawer can preview it while the bytes are still moving. The path arrives
     * as navigation arguments, which is untrusted input even when our own
     * staging code wrote it, so it is confined to the uploads directory.
     */
    suspend fun openStaged(directory: String, name: String): Result<OpenedPdf> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = File(context.filesDir, "uploads").canonicalFile
                val file = File(root, "$directory${File.separator}$name").canonicalFile
                require(file.parentFile == root && file.isFile) {
                    "That preview is no longer available."
                }
                rendererFor(file)
            }
        }

    private fun rendererFor(file: File): OpenedPdf {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return OpenedPdf(descriptor, PdfRenderer(descriptor))
    }
}
