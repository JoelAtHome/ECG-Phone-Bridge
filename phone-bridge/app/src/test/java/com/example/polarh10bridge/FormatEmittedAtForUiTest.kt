package com.example.polarh10bridge

import com.example.polarh10bridge.ritual.RitualRecordingSummary
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

    @Test
    fun recordingRow_isOneCompactLine() {
        assertEquals("3:01", formatDurationForUi(181.2))
        val label =
            hrvRecordingRowLabel(
                RitualRecordingSummary(
                    sessionId = "s",
                    acked = false,
                    rmssdMs = 42.4,
                    emittedAt = "2026-09-25T15:14:00Z",
                    durationS = 181.0,
                    profileId = "patient-2",
                    profileDisplayName = "Joel",
                ),
            )
        assertTrue(label.endsWith(" · 42 ms · 3:01 · Joel · pending"))
        assertTrue(label.startsWith("2026-09-25 "))
    }

    @Test
    fun capacityLine_namesLimitStoredAndRemaining() {
        assertEquals(
            "Phone keeps the last 5 recordings — 0 stored, 5 more can be stored.",
            hrvRecordingCapacityLine(0),
        )
        assertEquals(
            "Phone keeps the last 5 recordings — 3 stored, 2 more can be stored.",
            hrvRecordingCapacityLine(3),
        )
        assertEquals(
            "Phone keeps the last 5 recordings — 5 stored, 0 more can be stored. Next replaces the oldest.",
            hrvRecordingCapacityLine(5),
        )
    }

    @Test
    fun stopToast_reportsStorageAndUploadHintWhenPcIsAway() {
        assertEquals(
            "HRV saved — 1 stored, 4 more can be stored. Upload in FT or HnH",
            recordStopStorageToast(stored = 1, pcConnected = false),
        )
        assertEquals(
            "HRV saved — 4 stored, 1 more can be stored",
            recordStopStorageToast(stored = 4, pcConnected = true),
        )
        assertEquals(
            "HRV saved — 5 stored, 0 more can be stored. Next replaces the oldest. Upload in FT or HnH",
            recordStopStorageToast(stored = 9, pcConnected = false),
        )
    }
}
