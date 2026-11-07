package com.ragclient.android.feature.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_760_000_000_000L

    @Test
    fun `under a minute reads just now`() {
        assertEquals("just now", relativeTime(now - 30_000, now))
    }

    @Test
    fun `future timestamps clamp to just now`() {
        assertEquals("just now", relativeTime(now + 60_000, now))
    }

    @Test
    fun `minutes and hours and days`() {
        assertEquals("5m ago", relativeTime(now - 5 * 60_000, now))
        assertEquals("59m ago", relativeTime(now - 59 * 60_000, now))
        assertEquals("1h ago", relativeTime(now - 60 * 60_000, now))
        assertEquals("23h ago", relativeTime(now - 23 * 60 * 60_000, now))
        assertEquals("1d ago", relativeTime(now - 24 * 60 * 60_000, now))
        assertEquals("6d ago", relativeTime(now - (6 * 24 + 23) * 60 * 60_000, now))
    }

    @Test
    fun `older than a week falls back to a date`() {
        val text = relativeTime(now - 10L * 24 * 60 * 60_000, now)
        assertTrue(text, Regex("""\d{1,2} \S+ \d{4}""").matches(text))
    }
}