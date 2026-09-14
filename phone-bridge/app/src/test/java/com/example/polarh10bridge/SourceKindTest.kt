package com.example.polarh10bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceKindTest {
    @Test
    fun wireValues_matchProtocol() {
        assertEquals("POLAR_H10", SourceKind.PolarH10.wireValue())
        assertEquals("FEATHER", SourceKind.Feather.wireValue())
    }

    @Test
    fun fromPref_parsesKnownAndDefaults() {
        assertEquals(SourceKind.Feather, SourceKind.fromPref("FEATHER"))
        assertEquals(SourceKind.PolarH10, SourceKind.fromPref("POLAR_H10"))
        assertEquals(SourceKind.PolarH10, SourceKind.fromPref(null))
        assertEquals(SourceKind.PolarH10, SourceKind.fromPref("nope"))
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
