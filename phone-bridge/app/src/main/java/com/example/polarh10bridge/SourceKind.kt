package com.example.polarh10bridge

import com.example.polarh10bridge.feather.FeatherBleContract

/** User-selected capture source. Persist the kind; connect is a separate tap. */
enum class SourceKind {
    PolarH10,
    Feather,
    ;

    fun wireValue(): String =
        when (this) {
            PolarH10 -> "POLAR_H10"
            Feather -> FeatherBleContract.SOURCE_DEVICE_WIRE
        }

    fun displayName(): String =
        when (this) {
            PolarH10 -> "Polar H10"
            Feather -> "Feather"
        }

    fun diagramCaption(): String =
        when (this) {
            PolarH10 -> "Polar H10: Bluetooth"
            Feather -> "Feather: Bluetooth"
        }

    fun pickerSubtitle(): String =
        when (this) {
            PolarH10 -> "Chest-strap HR / ECG"
            Feather -> "ECG-Box (BLE)"
        }

    companion object {
        fun fromPref(raw: String?): SourceKind =
            when (raw?.trim()?.uppercase()) {
                "FEATHER", "FEATHER_BLE" -> Feather
                "POLAR_H10", "POLAR", "H10" -> PolarH10
                else -> PolarH10
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
        SourceKind.Feather -> featherBleConnected || featherSimActive
    }

internal fun BridgeScreenState.anySourceLinked(): Boolean = sensorConnected || featherBleConnected

internal fun BridgeScreenState.featherInProgressLine(): String? {
    if (selectedSourceKind != SourceKind.Feather) return null
    if (featherSimActive && !featherBleConnected) return "Feather sim active"
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
