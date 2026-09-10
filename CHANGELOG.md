# Phone Bridge Changelog

This changelog tracks changes specific to the Android Phone Bridge app (`PolarH10Bridge`).

## Unreleased

## Version 1.0.0-beta.8
- enhancement: Patient vs Tech view (hamburger menu). Tech meters show settle, HR/IBI, RMSSD, quality flags, RSSI.
- Patient view stays pacer-clean (optional “Session in progress”); capture controls live in Tech view.

## Version 1.0.0-beta.7
- enhancement: Capture session UI — Stream vs Record, Start/Stop, IBI count, last bridge RMSSD.
- enhancement: Official bridge `rmssd` on TCP (final on stop; rolling ~30s in stream mode).
- enhancement: `session_control` / `session_state`, `client_app` on `client_info`, discover `protocol`/`features`.

## Version 1.0.0-beta.6
- enhancement: Patient breathing pacer on the phone (smooth expand/contract presets); hosts should drop PC patient pacers.
- enhancement: Pacer phase count-up (restarts each inhale/exhale); compact preset labels (`5.5/5.5`, etc.).
- fix: Clear system navigation-bar overlap at the bottom of the main screen.
- docs: Architecture / host handoff / protocol — phone owns patient pacer for HnH, VNS-TA, and FlareTracker.

## Version 1.0.0-beta.5
- enhancement: Updated in-app title to “ECG Phone Bridge” and About text to list Hertz & Hearts, VNS-TA, and FlareTracker.

## Version 1.0.0-beta.4
- enhancement: Changed application id to `com.joelathome.ecgphonebridge` (away from `com.example.*`) and display name to “ECG Phone Bridge” for clearer sideloads / less Play Protect friction.
- enhancement: CI now builds a **signed release** APK for GitHub Releases (`PolarH10Bridge-<tag>.apk`) instead of a debug artifact.
- fix: Bumped in-app `versionName` to match release tagging (was stuck at `1.0.0-beta.2` while GitHub already had a `v1.0.0-beta.3` tag).
- docs: Architecture, protocol, Feather profile schema, and host handoff notes.
- enhancement: Pure-Kotlin `RmssdCalculator` with unit tests (not yet on the TCP wire).

## Version 1.0.0-beta.2 (April 08 2026)
- enhancement: Added client identity exchange so the bridge can report the active Hertz & Hearts user profile to the phone app.
- enhancement: Improved Wi-Fi address handling and LAN IP selection flow for more reliable host/connection setup.
- enhancement: Added foreground-service keepalive behavior and safer bridge-port change confirmation while a PC session is connected.
- enhancement: Improved BLE reliability and RSSI display behavior (scan-backed updates, UI throttling, dead-code cleanup).
- maintenance: Updated Android bridge CI/release workflow reliability (gradle wrapper executable handling, release lookup retry before APK attach).
- docs: Added an Android Bridge development workflow note (`Android Dev Workflow.txt`) covering USB debugging and deploy-from-Android-Studio steps.

## Version 1.0.0-beta.1
- release: Published `PolarH10Bridge-debug-v1.0.0-beta.1.apk` to GitHub Releases via the Android bridge release workflow.
- maintenance: Aligned APK asset naming with release tag format (`PolarH10Bridge-debug-<tag>.apk`) for predictable install/update guidance.
