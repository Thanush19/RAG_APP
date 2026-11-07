package com.ragclient.android.feature.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

private val Bold = SpanStyle(fontWeight = FontWeight.Bold)
private val Italic = SpanStyle(fontStyle = FontStyle.Italic)
private val Mono = SpanStyle(fontFamily = FontFamily.Monospace)

private val BulletLine = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val HeadingLine = Regex("""^(\s*)#{1,6}\s+(.*)$""")
private const val Fence = "```"

/**
 * Turns the markdown the model writes into span styles, so `**Languages:**`
 * arrives on screen as bold text rather than as its own markers.
 *
 * `streaming` is why this is a scanner and not a regex. SSE tokens land a few
 * characters at a time, so any moment the buffer can hold a delimiter that is
 * half-arrived (a lone `*` whose pair is still in flight) or still open
 * (`**Languages:` with the closer two tokens away). While streaming those
 * markers are hidden and the run is styled as if closed; once the answer is
 * finished — the transcript, a stopped or failed stream — an unmatched marker
 * is content the model actually wrote, so it stays visible.
 *
 * Deliberately narrow: bold, italic, inline code, bullets, headings and fenced
 * blocks. Nested emphasis and links are left literal rather than half-parsed.
 */
internal fun answerMarkup(text: String, streaming: Boolean = false): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = text.split("\n")
    var insideFence = false
    var wroteAnyLine = false

    lines.forEachIndexed { index, line ->
        if (line.trimStart().startsWith(Fence)) {
            insideFence = !insideFence
            return@forEachIndexed
        }
        if (wroteAnyLine) builder.append("\n")
        wroteAnyLine = true

        val atEndOfStream = streaming && index == lines.lastIndex
        if (insideFence) {
            val start = builder.length
            builder.append(line)
            builder.addStyle(Mono, start, builder.length)
            return@forEachIndexed
        }

        val heading = HeadingLine.matchEntire(line)
        val bullet = if (heading == null) BulletLine.matchEntire(line) else null
        val content = heading?.groupValues?.get(2)
            ?: bullet?.groupValues?.get(2)
            ?: line
        val indent = heading?.groupValues?.get(1).orEmpty() +
            bullet?.groupValues?.get(1).orEmpty()

        val start = builder.length
        if (indent.isNotEmpty()) builder.append(indent)
        if (bullet != null) builder.append("• ")
        builder.appendStyled(content, atEndOfStream)
        if (heading != null) builder.addStyle(Bold, start, builder.length)
    }

    return builder.toAnnotatedString()
}

private fun AnnotatedString.Builder.appendStyled(line: String, atEndOfStream: Boolean) {
    var cursor = 0
    while (cursor < line.length) {
        val marker = line.indexOfDelimiter(cursor) ?: run {
            append(line.substring(cursor))
            return
        }
        append(line.substring(cursor, marker.index))

        val contentStart = marker.index + marker.token.length
        val closer = line.indexOf(marker.token, contentStart)
        val style = when (marker.token) {
            "**" -> Bold
            "`" -> Mono
            else -> Italic
        }

        when {
            closer in contentStart until line.length &&
                !(marker.token == "*" && isFlankingSpace(line, contentStart, closer)) -> {
                val start = length
                append(line.substring(contentStart, closer))
                addStyle(style, start, length)
                cursor = closer + marker.token.length
            }
            // Nothing after it yet: style the run as if it closed here and
            // drop the marker, so the user never sees a stray `**` flash.
            atEndOfStream -> {
                val start = length
                append(line.substring(contentStart))
                if (start < length) addStyle(style, start, length)
                return
            }
            // `2 * 3` and other prose that merely contains the character.
            else -> {
                append(marker.token)
                cursor = contentStart
            }
        }
    }
}

/** A `*` only emphasises when it hugs its content, as CommonMark requires. */
private fun isFlankingSpace(line: String, contentStart: Int, closer: Int): Boolean =
    line[contentStart] == ' ' || line[closer - 1] == ' '

private data class Delimiter(val index: Int, val token: String)

private fun String.indexOfDelimiter(from: Int): Delimiter? {
    var i = from
    while (i < length) {
        when (this[i]) {
            '*' -> return Delimiter(i, if (i + 1 < length && this[i + 1] == '*') "**" else "*")
            '`' -> return Delimiter(i, "`")
            else -> i++
        }
    }
    return null
}
