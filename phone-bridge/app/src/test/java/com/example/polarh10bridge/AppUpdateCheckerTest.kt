package com.example.polarh10bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun normalize_stripsLeadingV() {
        assertEquals("1.0.0-beta.19", normalizeVersionLabel("v1.0.0-beta.19"))
        assertEquals("1.0.0-beta.19", normalizeVersionLabel("  V1.0.0-beta.19 "))
    }

    @Test
    fun compare_betaNumbers() {
        assertTrue(compareAppVersions("1.0.0-beta.20", "1.0.0-beta.19") > 0)
        assertTrue(compareAppVersions("1.0.0-beta.19", "1.0.0-beta.20") < 0)
        assertEquals(0, compareAppVersions("v1.0.0-beta.19", "1.0.0-beta.19"))
    }

    @Test
    fun compare_releaseBeatsPrerelease() {
        assertTrue(compareAppVersions("1.0.0", "1.0.0-beta.99") > 0)
        assertTrue(compareAppVersions("1.0.0-beta.1", "1.0.0") < 0)
    }

    @Test
    fun compare_coreVersions() {
        assertTrue(compareAppVersions("1.0.1-beta.1", "1.0.0-beta.99") > 0)
        assertTrue(compareAppVersions("1.1.0", "1.0.9") > 0)
    }
}
