# Phone Bridge Changelog

This changelog tracks changes specific to the Android Phone Bridge app (`PolarH10Bridge`).

## Unreleased

## Version 1.0.0-beta.27
- enhancement: Feather sim uses RSA-style breathing IBI variation + slow wander so Tech HR and RMSSD keep moving after settle (not a frozen flatline).

## Version 1.0.0-beta.26
- fix: Capture **Start** enables when Tech Feather sim is active (was gated on Polar `sensorConnected` only).

## Version 1.0.0-beta.25
- enhancement: Phone-first Feather path — BLE GATT contract, profile store, packet codecs, Tech **Simulate Feather IBIs** (`source_device: FEATHER`), plus ecg-box handoff docs. Live GATT connect still next.

## Version 1.0.0-beta.24
- fix: Keep Polar H10 and PC bridge connections across screen rotation (`configChanges` so the Activity is not recreated and torn down).

## Version 1.0.0-beta.23
- enhancement: Stream mode shows RMSSD as **unsettled** (instead of —) while settling / until the first rolling value.

## Version 1.0.0-beta.22
- fix: Closing/finishing the app tears down the PC TCP link (closes the client socket, stops background keep-alive on finish) so VNS-TA / HnH detect disconnect instead of a zombie "still connected" / no-data state.

## Version 1.0.0-beta.21
- enhancement: Skin-contact / quality gate from Polar HR `contactStatus` — drop RR/ECG (and RMSSD intake) when the sensor reports no contact. BLE dBm stays link-only (`rssi_is_contact: false`); UI labels say "dBm link".
- enhancement: Additive `sensor_quality` NDJSON for hosts (`contact_state`, optional `contact` / `contact_supported`, optional `rssi_dbm`).

## Version 1.0.0-beta.20
- enhancement: Check GitHub Releases on launch and show a dismissible banner when a newer APK is available (also in About).

## Version 1.0.0-beta.19
- fix: Connected-sensor dBm keeps updating after Connect on the already-linked H10. Rescan stopped RSSI polling; Cancel resumed it, but Connect did not.
- fix: RSSI watchdog re-arms the LE scan if no samples arrive for ~4s (zombie scans that stay registered but never deliver).

## Version 1.0.0-beta.18
- fix: Bridge NDJSON writes run off the UI thread. Stop and the Record heartbeat were hitting NetworkOnMainThreadException, so the phone stayed connected and the host never received `rmssd`.

## Version 1.0.0-beta.17
- fix: Bridge writes use the socket stream and close a dead PC connection on write failure. `PrintWriter` was swallowing IO errors, so Stop `rmssd` never left the phone until a later reconnect.
- enhancement: Restart the Record heartbeat when a PC reconnects during an active session.

## Version 1.0.0-beta.16
- enhancement: Wire `emitted_at` (ISO instant) on `session_state` and official `rmssd` so hosts timestamp rituals at phone stop time, not PC reconnect time.
- enhancement: Periodic `session_state` heartbeat every 15s while capture is active — keeps the PC TCP link warm during long Record sessions (FlareTracker / other hosts).
- fix: Flush bridge NDJSON after each line so stop payloads reach the host promptly.

## Version 1.0.0-beta.15
- fix: Capture continues on the phone when the PC TCP link drops (session no longer auto-stops).
- enhancement: On PC reconnect, replay last `rmssd` / `session_state` if the host missed the live stop lines; send current `session_state` if capture is still running.
- docs: Host handoff light-pass status for FlareTracker, VNS-TA, and Hertz & Hearts (2026-09-10).

## Version 1.0.0-beta.14
- fix: Tech meters — Elapsed on its own line.
- enhancement: Flow diagram PC label — `PC: J. Kobe Host App` until connected, then `PC: [host app]` when `client_app` is known.
- fix: Wi-Fi connect hint shows whenever a LAN/hotspot IPv4 is available (no longer hidden when Wi-Fi client is off); copy is multi-app (`J. Kobe host app`).
- docs: Host–mode negotiation intent (PC-initiated link; soft FT/VNS-TA/HnH preferences; dual-sided conflict UX) — build later.

## Version 1.0.0-beta.13
- fix: Quality-flags ⓘ help dialog uses dark panel + light gray text for readability.

## Version 1.0.0-beta.12
- enhancement: Tech meters — elapsed with target (`m:ss / m:ss` = settle+analysis), positive empty-flag message, ⓘ flag help, Accepted beats on its own line; Record RMSSD shows TBD until stop.
- enhancement: About Date follows phone locale/medium date format.
- docs: Patient-profile session timing ownership (phone authority, Tech override, Tuner optional co-editor) — build later.

## Version 1.0.0-beta.11
- fix: About link uses https://jkobelabs.com/ (correct domain).

## Version 1.0.0-beta.10
- enhancement: About link opens J. Kobe Labs site (replaces Buy Me a Coffee).

## Version 1.0.0-beta.9
- fix: Stream/Record radio — deselected option keeps a visible gray ring (Material default was blending away).

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
