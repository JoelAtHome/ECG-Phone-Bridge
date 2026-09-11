package com.example.polarh10bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorContactGateTest {

    @Test
    fun fromPolar_unsupported_isUnknown() {
        assertEquals(
            SensorContactState.Unknown,
            SensorContactGate.fromPolarHrSample(
                contactStatusSupported = false,
                contactStatus = false,
            ),
        )
        assertEquals(
            SensorContactState.Unknown,
            SensorContactGate.fromPolarHrSample(
                contactStatusSupported = false,
                contactStatus = true,
            ),
        )
    }

    @Test
    fun fromPolar_supported_mapsContactBit() {
        assertEquals(
            SensorContactState.InContact,
            SensorContactGate.fromPolarHrSample(
                contactStatusSupported = true,
                contactStatus = true,
            ),
        )
        assertEquals(
            SensorContactState.NoContact,
            SensorContactGate.fromPolarHrSample(
                contactStatusSupported = true,
                contactStatus = false,
            ),
        )
    }

    @Test
    fun shouldForward_onlyBlocksKnownNoContact() {
        assertTrue(SensorContactGate.shouldForwardTelemetry(SensorContactState.Unknown))
        assertTrue(SensorContactGate.shouldForwardTelemetry(SensorContactState.InContact))
        assertFalse(SensorContactGate.shouldForwardTelemetry(SensorContactState.NoContact))
    }

    @Test
    fun displayLabel_hidesUnknown() {
        assertNull(SensorContactState.Unknown.displayLabel())
        assertEquals("Skin contact OK", SensorContactState.InContact.displayLabel())
        assertEquals("No skin contact", SensorContactState.NoContact.displayLabel())
    }

    @Test
    fun wireValue_stable() {
        assertEquals("unknown", SensorContactState.Unknown.wireValue())
        assertEquals("in_contact", SensorContactState.InContact.wireValue())
        assertEquals("no_contact", SensorContactState.NoContact.wireValue())
    }
}
