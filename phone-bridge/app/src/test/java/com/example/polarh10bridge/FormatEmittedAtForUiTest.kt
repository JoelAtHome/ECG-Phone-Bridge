package com.example.polarh10bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatEmittedAtForUiTest {
    @Test
    fun parsesUtcInstantToLocalWallClockPattern() {
        val out = formatEmittedAtForUi("2026-09-15T18:00:00Z")
        assertTrue(out.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")))
    }

    @Test
    fun blankPassthrough() {
        assertEquals("", formatEmittedAtForUi(""))
        assertEquals("", formatEmittedAtForUi("   "))
    }

    @Test
    fun invalidFallsBackToTruncated() {
        assertEquals("not-an-instant-valu", formatEmittedAtForUi("not-an-instant-value"))
    }
}
