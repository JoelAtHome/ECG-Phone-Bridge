package com.example.polarh10bridge

import com.example.polarh10bridge.feather.FeatherBleContract

/** User-selected capture source. Persist the kind; connect is a separate tap. */
enum class SourceKind {
    PolarH10,
    Feather,
    /** Tech-only synthetic IBI + ECG; host wire stays FEATHER. */
    Simulate,
    ;

    fun wireValue(): String =
        when (this) {
            PolarH10 -> "POLAR_H10"
            Feather, Simulate -> FeatherBleContract.SOURCE_DEVICE_WIRE
        }

    fun displayName(): String =
        when (this) {
            PolarH10 -> "Polar H10"
            Feather -> "Feather"
            Simulate -> "Simulate"
        }

    fun diagramCaption(): String =
        when (this) {
            PolarH10 -> "Polar H10: Bluetooth"
            Feather -> "Feather: Bluetooth"
            Simulate -> "Feather: Simulate"
        }

    fun pickerSubtitle(): String =
        when (this) {
            PolarH10 -> "Chest-strap HR / ECG"
            Feather -> "ECG-Box (BLE)"
            Simulate -> "Synthetic IBI + ECG (Tech)"
        }

    /** Feather node art / glow (live ECG-Box or sim). */
    fun usesFeatherNodeArt(): Boolean = this == Feather || this == Simulate

    companion object {
        fun fromPref(raw: String?): SourceKind =
            when (raw?.trim()?.uppercase()) {
                "FEATHER", "FEATHER_BLE" -> Feather
                // Never restore Simulate from prefs (sim is session-only / Tech-only).
                "SIMULATE", "FEATHER_SIM" -> Feather
                "POLAR_H10", "POLAR", "H10" -> PolarH10
                else -> PolarH10
            }

        /** Patient: Polar + Feather. Tech: + Simulate. */
        fun pickerEntries(techView: Boolean): List<SourceKind> =
            if (techView) {
                listOf(PolarH10, Feather, Simulate)
            } else {
                listOf(PolarH10, Feather)
            }
    }
}

fun featherHumanPhase(phase: String, detail: String = ""): String =
    when (phase) {
        "Scanning" -> "Looking for ECG-Box-Feather…"
        "Connecting" -> "Connecting…"
        "Discovering", "Ready" -> "Setting up link…"
        "Streaming" -> "Streaming"
        "Error" -> detail.ifBlank { "Feather connect failed" }
        else -> ""
    }

fun featherPhaseIsBusy(phase: String): Boolean =
    phase == "Scanning" ||
        phase == "Connecting" ||
        phase == "Discovering" ||
        phase == "Ready"

internal fun BridgeScreenState.diagramSourceActive(): Boolean =
    when (selectedSourceKind) {
        SourceKind.PolarH10 -> sensorConnected
        SourceKind.Feather -> featherBleConnected
        SourceKind.Simulate -> featherSimActive
    }

internal fun BridgeScreenState.anySourceLinked(): Boolean = sensorConnected || featherBleConnected

internal fun BridgeScreenState.featherInProgressLine(): String? {
    if (featherSimActive && !featherBleConnected) return "Feather sim active"
    if (selectedSourceKind != SourceKind.Feather) return null
    if (featherPhaseIsBusy(featherBlePhase)) {
        return featherHumanPhase(featherBlePhase, featherBleDetail)
    }
    return null
}

internal fun BridgeScreenState.sourceDeviceWire(): String =
    if (featherSimActive) {
        SourceKind.Feather.wireValue()
    } else {
        selectedSourceKind.wireValue()
    }
