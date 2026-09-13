package com.example.polarh10bridge

/**
 * Skin/electrode contact from the sensor — not BLE RSSI.
 *
 * Polar H10 reports contact via HR samples (`contactStatus` /
 * `contactStatusSupported`). RSSI only describes radio link quality.
 */
enum class SensorContactState {
    /** No HR sample yet, or sensor does not report contact. */
    Unknown,

    /** Sensor reports measurable contact (e.g. on skin). */
    InContact,

    /** Sensor supports contact and reports no contact. */
    NoContact,
    ;

    fun wireValue(): String =
        when (this) {
            Unknown -> "unknown"
            InContact -> "in_contact"
            NoContact -> "no_contact"
        }

    /** Patient/Tech short label. Null when nothing useful to show. */
    fun displayLabel(): String? =
        when (this) {
            Unknown -> null
            InContact -> "Skin contact OK"
            NoContact -> "No skin contact"
        }
}

object SensorContactGate {
    fun fromPolarHrSample(
        contactStatusSupported: Boolean,
        contactStatus: Boolean,
    ): SensorContactState =
        when {
            !contactStatusSupported -> SensorContactState.Unknown
            contactStatus -> SensorContactState.InContact
            else -> SensorContactState.NoContact
        }

    /**
     * Forward RR/ECG to hosts and into official RMSSD only when contact is
     * not known-bad. Unknown (unsupported / not yet seen) stays open so we
     * do not block Polar-only paths that lack a contact bit.
     */
    fun shouldForwardTelemetry(state: SensorContactState): Boolean =
        state != SensorContactState.NoContact
}
