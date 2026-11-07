package com.ragclient.android.feature.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnswerMarkupTest {

    private val AnnotatedString.boldRuns: List<String>
        get() = styledRuns { it.fontWeight == FontWeight.Bold }

    private val AnnotatedString.italicRuns: List<String>
        get() = styledRuns { it.fontStyle == FontStyle.Italic }

    private val AnnotatedString.codeRuns: List<String>
        get() = styledRuns { it.fontFamily == FontFamily.Monospace }

    private fun AnnotatedString.styledRuns(match: (SpanStyle) -> Boolean): List<String> {
        val plain = text
        return spanStyles.filter { match(it.item) }.map { plain.substring(it.start, it.end) }
    }

    @Test
    fun `bold markers become a style and leave the text`() {
        val markup = answerMarkup("**Languages:** Java, Python")
        assertEquals("Languages: Java, Python", markup.text)
        assertEquals(listOf("Languages:"), markup.boldRuns)
    }

    @Test
    fun `plain answers are untouched`() {
        val text = "Section 3 covers the ingestion pipeline."
        assertEquals(text, answerMarkup(text).text)
        assertEquals(emptyList<String>(), answerMarkup(text).boldRuns)
    }

    @Test
    fun `the resume answer from the device renders as bullets with bold labels`() {
        val markup = answerMarkup(
            "Based on the context, Rahul's skills are:\n\n" +
                "- **Languages:** Java, Python\n" +
                "- **Frameworks:** Spring Boot, React"
        )
        assertEquals(
            "Based on the context, Rahul's skills are:\n\n" +
                "• Languages: Java, Python\n" +
                "• Frameworks: Spring Boot, React",
            markup.text
        )
        assertEquals(listOf("Languages:", "Frameworks:"), markup.boldRuns)
    }

    @Test
    fun `inline code loses its backticks and turns monospace`() {
        val markup = answerMarkup("run `gradlew test` for this")
        assertEquals("run gradlew test for this", markup.text)
        assertEquals(listOf("gradlew test"), markup.codeRuns)
    }

    @Test
    fun `a fenced block keeps its lines and drops the fence`() {
        val markup = answerMarkup("```\nSELECT 1\n```")
        assertEquals("SELECT 1", markup.text)
        assertEquals(listOf("SELECT 1"), markup.codeRuns)
    }

    @Test
    fun `headings become bold lines without their hashes`() {
        val markup = answerMarkup("## Skills")
        assertEquals("Skills", markup.text)
        assertEquals(listOf("Skills"), markup.boldRuns)
    }

    @Test
    fun `emphasis needs to hug its content`() {
        // Arithmetic, not italic.
        assertEquals("2 * 3 * 4", answerMarkup("2 * 3 * 4").text)
        assertEquals(emptyList<String>(), answerMarkup("2 * 3 * 4").italicRuns)
        // Touching the content, so the markers become style.
        assertEquals("real emphasis here", answerMarkup("real *emphasis* here").text)
        assertEquals(listOf("emphasis"), answerMarkup("real *emphasis* here").italicRuns)
    }

    @Test
    fun `underscores in file names are never emphasis`() {
        val text = "from resume_srivastava_rahul.pdf"
        assertEquals(text, answerMarkup(text).text)
        assertEquals(emptyList<String>(), answerMarkup(text).italicRuns)
    }

    @Test
    fun `a marker split across tokens never shows one star`() {
        // One answer arriving one SSE token at a time: no frame may show a
        // literal `*`, because the next frame supplies the other half.
        val frames = listOf("*", "**", "**L", "**Lan", "**Languages", "**Languages:", "**Languages:**")
        frames.forEach { frame ->
            val rendered = answerMarkup(frame, streaming = true).text
            assertFalse("literal marker while streaming \"$frame\"", rendered.contains('*'))
        }
        assertEquals("Languages:", answerMarkup("**Languages:**", streaming = true).text)
    }

    @Test
    fun `an open run is styled as if it already closed`() {
        val markup = answerMarkup("**Languages:", streaming = true)
        assertEquals("Languages:", markup.text)
        assertEquals(listOf("Languages:"), markup.boldRuns)
    }

    @Test
    fun `a finished answer keeps a marker the model never closed`() {
        // Not streaming any more: this is the text, so it stays visible.
        assertEquals("**Languages:", answerMarkup("**Languages:").text)
        assertEquals("*", answerMarkup("*", streaming = false).text)
    }

    @Test
    fun `line breaks survive parsing`() {
        val markup = answerMarkup("first\n**second**\nthird")
        assertEquals("first\nsecond\nthird", markup.text)
        assertEquals(listOf("second"), markup.boldRuns)
    }
}
