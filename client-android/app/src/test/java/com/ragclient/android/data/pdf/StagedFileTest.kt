package com.ragclient.android.data.pdf

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StagedFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun batch(root: File, directory: String, name: String): File {
        val batch = File(root, directory).apply { mkdirs() }
        return File(batch, name).apply { writeText("pdf") }
    }

    @Test
    fun `a staged file resolves inside the uploads directory`() {
        val root = folder.newFolder("uploads")
        val staged = batch(root, "1793512746398", "report.pdf")
        assertEquals(staged.canonicalPath, stagedFile(root, "1793512746398", "report.pdf")?.canonicalPath)
    }

    @Test
    fun `a traversal in the batch segment cannot escape the uploads directory`() {
        val root = folder.newFolder("uploads2")
        batch(root, "1793512746398", "report.pdf")
        val outside = folder.newFolder("elsewhere")
        File(outside, "secret.pdf").writeText("not a staged upload")
        assertNull(stagedFile(root, "../elsewhere", "secret.pdf"))
    }

    @Test
    fun `a missing staged file has no preview`() {
        val root = folder.newFolder("uploads3")
        assertNull(stagedFile(root, "1793512746398", "gone.pdf"))
    }
}
