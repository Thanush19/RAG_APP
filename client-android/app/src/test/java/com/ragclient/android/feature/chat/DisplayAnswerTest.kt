package com.ragclient.android.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayAnswerTest {

    @Test
    fun stripsMarkerBeforePunctuation() {
        assertEquals(
            "The email ID is posh@example.com, as listed in document.",
            displayAnswer("The email ID is posh@example.com, as listed in document [S1].")
        )
    }

    @Test
    fun stripsMarkerMidSentenceAndMultiIndexBrackets() {
        assertEquals(
            "Section 3 covers ingestion. Retries are bounded.",
            displayAnswer("Section 3 covers ingestion [S1][S2]. Retries are bounded [S1, S3].")
        )
    }

    @Test
    fun leavesPlainAnswersAndLineBreaksAlone() {
        val text = "First line.\n\nSecond line with [ b ] not a citation."
        assertEquals(text, displayAnswer(text))
    }

    @Test
    fun stripsApartialMarkerDuringStreaming() {
        // The closing bracket has not arrived yet, so this frame still shows it.
        assertEquals("Answer so far [S", displayAnswer("Answer so far [S"))
    }
}
