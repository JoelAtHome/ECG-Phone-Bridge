package com.example.polarh10bridge

import com.example.polarh10bridge.feather.FeatherProfileHintMatcher
import com.example.polarh10bridge.feather.FeatherProfileSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatherProfileHintMatcherTest {
    private val profiles =
        listOf(
            FeatherProfileSummary("demo", "Typical patch torso"),
            FeatherProfileSummary("patient-1", "Patient 1"),
            FeatherProfileSummary("payton", "Payton"),
            FeatherProfileSummary("joel_handgrip", "Joel"),
        )

    @Test
    fun matchesExactProfileId() {
        val r = FeatherProfileHintMatcher.resolve("Payton", profiles)
        assertTrue(r is FeatherProfileHintMatcher.Result.Match)
        r as FeatherProfileHintMatcher.Result.Match
        assertEquals("payton", r.profileId)
        assertEquals("profile_id", r.via)
    }

    @Test
    fun matchesExactDisplayName() {
        val r = FeatherProfileHintMatcher.resolve("Typical patch torso", profiles)
        assertTrue(r is FeatherProfileHintMatcher.Result.Match)
        r as FeatherProfileHintMatcher.Result.Match
        assertEquals("demo", r.profileId)
        assertEquals("display_name", r.via)
    }

    @Test
    fun matchesSlugFromSpacedName() {
        val r = FeatherProfileHintMatcher.resolve("Joel Handgrip", profiles)
        assertTrue(r is FeatherProfileHintMatcher.Result.Match)
        r as FeatherProfileHintMatcher.Result.Match
        assertEquals("joel_handgrip", r.profileId)
        assertEquals("slug", r.via)
    }

    @Test
    fun noneWhenUnknown() {
        assertEquals(
            FeatherProfileHintMatcher.Result.None,
            FeatherProfileHintMatcher.resolve("Sandy", profiles),
        )
    }

    @Test
    fun noneWhenBlank() {
        assertEquals(
            FeatherProfileHintMatcher.Result.None,
            FeatherProfileHintMatcher.resolve("  ", profiles),
        )
    }

    @Test
    fun ambiguousDuplicateDisplayNames() {
        val dup =
            profiles + FeatherProfileSummary("payton-b", "Payton")
        val r = FeatherProfileHintMatcher.resolve("Payton", dup)
        // Exact profile_id "payton" wins before display_name ambiguity.
        assertTrue(r is FeatherProfileHintMatcher.Result.Match)
        assertEquals("payton", (r as FeatherProfileHintMatcher.Result.Match).profileId)

        val rName = FeatherProfileHintMatcher.resolve("Patient 1",
            listOf(
                FeatherProfileSummary("a", "Patient 1"),
                FeatherProfileSummary("b", "Patient 1"),
            ),
        )
        assertTrue(rName is FeatherProfileHintMatcher.Result.Ambiguous)
    }

    @Test
    fun clientAppLabel() {
        assertEquals("VNS-TA", FeatherProfileHintMatcher.clientAppLabel("vns_ta"))
        assertEquals("Hertz & Hearts", FeatherProfileHintMatcher.clientAppLabel(null))
        assertEquals("FlareTracker", FeatherProfileHintMatcher.clientAppLabel("flaretracker"))
    }
}
