package com.example.polarh10bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceKindTest {
    @Test
    fun wireValues_matchProtocol() {
        assertEquals("POLAR_H10", SourceKind.PolarH10.wireValue())
        assertEquals("FEATHER", SourceKind.Feather.wireValue())
        assertEquals("FEATHER", SourceKind.Simulate.wireValue())
    }

    @Test
    fun fromPref_parsesKnownAndDefaults() {
        assertEquals(SourceKind.Feather, SourceKind.fromPref("FEATHER"))
        assertEquals(SourceKind.PolarH10, SourceKind.fromPref("POLAR_H10"))
        assertEquals(SourceKind.PolarH10, SourceKind.fromPref(null))
        assertEquals(SourceKind.PolarH10, SourceKind.fromPref("nope"))
        // Simulate is never restored from prefs.
        assertEquals(SourceKind.Feather, SourceKind.fromPref("SIMULATE"))
    }

    @Test
    fun pickerEntries_patientOmitsSimulate() {
        assertEquals(
            listOf(SourceKind.PolarH10, SourceKind.Feather),
            SourceKind.pickerEntries(techView = false),
        )
        assertEquals(
            listOf(SourceKind.PolarH10, SourceKind.Feather, SourceKind.Simulate),
            SourceKind.pickerEntries(techView = true),
        )
    }

    @Test
    fun diagramSourceActive_routesByKind() {
        assertTrue(
            BridgeScreenState(
                selectedSourceKind = SourceKind.Simulate,
                featherSimActive = true,
            ).diagramSourceActive(),
        )
        assertFalse(
            BridgeScreenState(
                selectedSourceKind = SourceKind.Feather,
                featherSimActive = true,
            ).diagramSourceActive(),
        )
        assertTrue(
            BridgeScreenState(
                selectedSourceKind = SourceKind.Feather,
                featherBleConnected = true,
            ).diagramSourceActive(),
        )
    }

    @Test
    fun sourceDeviceWire_simForcesFeather() {
        val s =
            BridgeScreenState(
                featherSimActive = true,
                selectedSourceKind = SourceKind.PolarH10,
            )
        assertEquals("FEATHER", s.sourceDeviceWire())
    }

    @Test
    fun featherHumanPhase_mapsBusyAndError() {
        assertEquals("Looking for ECG-Box-Feather…", featherHumanPhase("Scanning"))
        assertEquals("Connecting…", featherHumanPhase("Connecting"))
        assertEquals("Setting up link…", featherHumanPhase("Discovering"))
        assertEquals("scan failed", featherHumanPhase("Error", "scan failed"))
    }
}
