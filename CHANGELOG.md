# Phone Bridge Changelog

This changelog tracks changes specific to the Android Phone Bridge app (`phone-bridge` project / **ECG-Phone-Bridge** product).

## Unreleased

## Version 1.0.0-beta.65
- fix: A new PC TCP connection now **replaces** a stuck/half-open host socket instead of waiting until the old `readLine()` ends. Restarted Hertz & Hearts / FlareTracker can reconnect without force-stopping the phone app. Phone-side capture is unchanged.
- enhancement: Official `rmssd` snapshot includes `hr_bpm` (mean HR over the analysis window) so FlareTracker can show heart rate on the HRV log.

## Version 1.0.0-beta.64
- fix: Offline coeff fields no longer clip values (removed too-short fixed height).
- polish: Offline help copy uses “from/to” instead of Unicode arrows (font baseline mismatch).
- polish: Breathing pacer back at bottom of **Tech** view (same as Patient).

## Version 1.0.0-beta.63
- enhancement: Tech ECG strip draws MCU lookback **R markers** from Feather `peak_flags` (same rolling window as the wave).

## Version 1.0.0-beta.62
- polish: Active patient dropdown — white menu + dark/blue text (was charcoal on dark theme surface).

## Version 1.0.0-beta.61
- polish: Tech Offline — **Active patient** dropdown (pick + Add new…); removed Change / Add patient links; **Edit name**; tighter spacing above “Offline working set…”.
- polish: **Delete name** requires typing `DELETE` to confirm (removes whole patient profile).

## Version 1.0.0-beta.60
- enhancement: Tech **Rename** patient display name (keeps `profile_id`); PC match-miss / ambiguous status is bold amber pulsing banner (visible even when Offline collapsed).

## Version 1.0.0-beta.59
- enhancement: PC `client_info.pc_user` soft-matches local Feather profiles; Tech **Keep / Switch** confirm when Feather/Simulate would override the active patient; status lines for hosts; 30s Keep debounce.
- docs: PROTOCOL §8.2 / HOST_HANDOFF + **HOST_FEATHER_PROFILE_HINT** — re-send `client_info` on PC patient change.
- polish: Last HRV time on Capture panel shows device-local wall clock (wire `emitted_at` stays UTC ISO).

## Version 1.0.0-beta.58
- polish: Startup Wizard Connect copy — “…or go back to choose a different sensor type.”
- fix: Startup Wizard Wait for PC — keep polling Wi‑Fi IP after radio turns on mid-wizard (DHCP lag); longer Activity IP retries.

## Version 1.0.0-beta.57
- polish: Startup Wizard — Caregiver job is Record/Stream only; Wait for PC lists ECG-Box Tuner; sensor picker includes Simulate.

## Version 1.0.0-beta.56
- polish: Startup Wizard — open on every cold start; Patient/Breathe skips sensor+PC; job-specific PC host copy; connected-sensor hints; animated looking/waiting ellipsis.

## Version 1.0.0-beta.55
- polish: Auto-hide Android navigation bar (sticky immersive; swipe to peek) so bottom chrome does not steal session UX.
- polish: Startup Wizard — Continue button width, status-bar inset on title, Find/Disconnect connect states, PC-link copy (not re-ask job), FlareTracker label, finish-tip Stop prompt, exit wording.

## Version 1.0.0-beta.54
- enhancement: **Startup Wizard** (session coach) — first-run + menu **Start session**; role → job → permissions → sensor → Find → optional PC → Start (see `docs/UI_LAYOUT_MAP.md` §7).
- docs: HOST_HANDOFF — wizard is phone-only (no host wire/code); optional FT/VNS-TA/HnH/Companion help strings for ☰ **Start session**.
- polish: Phone-alone Record Stop toast — **HRV saved — upload in FT or HnH**

## Version 1.0.0-beta.53
- polish: Patient/caregiver Capture copy — **Record HRV**, status **HRV recording**, **Last HRV** / **Send HRV**, phone-alone toast **HRV saved** (wire `kind: ritual` unchanged).
- fix: Tech **Last HRV** shows **sent** after TCP push to a connected PC (optimistic ack until hosts send `ritual_ack`).
- polish: Capture **Send HRV** disabled while Stream/Record session is active.

## Version 1.0.0-beta.52
- enhancement: Tech **Change ECG sensor** adds **Simulate** (Patient stays Polar / Feather); Find with Simulate toggles sim; meters Simulate link removed.
- polish: Connected sensor pill — Polar **teal**, Feather **green**, Simulate **amber** with gentle pulse; soft name-text pulse when live linked.
- polish: Shorten phone-alone Record Stop toast so it fits on one line.

## Version 1.0.0-beta.51
- enhancement: **Phone-persisted ritual packages** — Record Stop saves rmssd + IBI + compact ECG on disk; auto-push to FT/HnH on connect; Tech **Send**; host `ritual_ack` / `ritual_request` (PROTOCOL §7).
- docs: Mark Tuner Online / VNS-TA Stream / assisted tune done; fix architecture drift; add Startup Wizard wishlist.

## Version 1.0.0-beta.50
- polish: ECG sensor modal — Disconnect / Rescan / Cancel stacked and center-aligned (full labels).

## Version 1.0.0-beta.49
- fix: ECG sensor modal action labels no longer wrap vertically (Cancel was stacking letter-by-letter).

## Version 1.0.0-beta.48
- polish: Capture session blurb — Stream for VNS-TA, Record for FlareTracker, Hertz & Hearts either.
- polish: ECG sensor modal — Disconnect / Rescan / Cancel in one horizontal row.
- fix: **Simulate Feather** shows a modal when Polar or ECG-Box is connected (was a silent refuse).

## Version 1.0.0-beta.47
- polish: Menu **Check for updates** (force GitHub check + toast); remove redundant **Disconnect sensor** / Feather **Disconnect** / **Start|Stop stream** (Find auto-starts detector; pill Disconnect/Rescan).
- polish: Tech **ECG-Box detector** (was Feather BLE); Simulate moved below Offline coeffs; shorter coeff fields; breathing pacer Patient-only; Quality flags ⓘ centering; Offline section title **Offline ECG-box tuning coefficients**.
- fix: Stop dumping raw Feather status JSON into Tech detail line.

## Version 1.0.0-beta.46
- polish: Data path shows phone/PC IPs on node captions; connected sensor pill opens **Disconnect / Rescan** dialog; **Change ECG sensor** label; gray unselected radios; tighter Change link spacing.
- polish: Tech **Offline coeffs** collapsed by default (chevron expand).
- fix: Feather/sim no longer fakes **Skin contact OK** (GATT has no contact bit — stays Unknown / “not reported”).

## Version 1.0.0-beta.45
- enhancement: Unified **source picker** on the Data path — Polar H10 or Feather (remembered), Patient and Tech. **Change source** opens the chooser; Find connects that kind. Feather Find shows a connecting overlay (scan can take ~12s) plus an in-progress line so the bridge does not look stuck. Tech **Connect Feather** link removed; sim / coeffs / ECG strip stay Tech-only.

## Version 1.0.0-beta.44
- enhancement: NDJSON `coeffs_get` → BLE `get_coeffs`; forward status `type:coeffs` to Tuner as `mcu_coeffs` (Online GET).

## Version 1.0.0-beta.43
- enhancement: Tech Offline **Get / Store / Send to Feather** chrome; Offline locked (read-only) while Tuner linked.
- enhancement: NDJSON `offline_echo` — Tuner Offline → phone Tech display (no SoR / MCU write).
- change: Tech Store is library-only; Send to Feather is MCU-only (aligned with Tuner Send/Store split).

## Version 1.0.0-beta.42
- enhancement: NDJSON `coeffs_push` / `coeffs_ack` — Tuner **Send** → Feather BLE coeffs only (no SoR write).
- change: `profile_put` (Tuner **Store**) is phone library only; no longer auto-pushes BLE coeffs.

## Version 1.0.0-beta.41
- enhancement: While Tuner is linked, Tech active-patient change / Add / Delete / Save re-pushes the active `profile` over NDJSON.
- docs: Printable UI layout map (`docs/UI_LAYOUT_MAP.md`) for chrome cleanup markup.

## Version 1.0.0-beta.40
- enhancement: Feather ECG notify **v2** lookback peak bit-mask → host `ecg.peak_flags`; control `get_coeffs`.
- enhancement: Tuner link auto-starts Feather stream when connected; `profile_put` pushes coeffs to Feather over BLE.

## Version 1.0.0-beta.39
- polish: Gray out Tech **Save profile** and Connection **Save** until the associated fields are dirty.
- fix: Tuner `profile_put` merges coeffs/hardware instead of wiping phone metadata; stop re-polishing demo on every UI refresh; only migrate legacy demo outliers (0.65/1.4/1000).

## Version 1.0.0-beta.38
- maintenance: Retire **PolarH10Bridge** naming — Gradle project folder `phone-bridge/`, discovery `app` **`ECG-Phone-Bridge`**, release APK `ECG-Phone-Bridge-<tag>.apk`. `applicationId` unchanged (`com.joelathome.ecgphonebridge`). Hosts still match on `role: phone_bridge`.
- enhancement: NDJSON **Feather profile sync** for ECG-Box Tuner (`PROTOCOL.md` §13) — `profile_get_active` / `profile_list` / `profile_get` / `profile_put`; discover `features` includes `feather_profiles`; `client_app: ecg_box_tuner` auto-pushes active profile.

## Version 1.0.0-beta.37
- enhancement: Tech **Delete** active patient profile (confirm; keep ≥1); factory seeds no longer reappear after delete.

## Version 1.0.0-beta.36
- polish: Factory profile labels — **Typical patch torso** (was Demo), **Patient 1** (Payton patch/torso), **Patient 2** (Joel handgrip; migrates legacy `joel` id).

## Version 1.0.0-beta.35
- enhancement: Tech **patient profile** picker + thin coeff editor (explicit Save); Add patient clones demo; factory seeds **Demo** (patch/torso) + **Joel** (handgrip Aug 31 knobs); Connect Feather / Save-while-connected push **active** profile coeffs. Session timing stored on profile JSON but not yet applied to RMSSD. On-device Saves stay phone-local (not in git).

## Version 1.0.0-beta.34
- fix: Tech ECG strip always visible under Feather BLE (stronger chrome + packet count); high BLE connection priority.
- enhancement: **Simulate Feather IBI + ECG** — textbook PQRST feeds Tech strip and host `ecg` (with existing RSA IBIs).

## Version 1.0.0-beta.33
- enhancement: Feather ECG notify enabled — host `ecg` NDJSON while streaming (like `rr`); Tech rolling ECG strip (~3 s).

## Version 1.0.0-beta.32
- enhancement: Demo Feather coeffs match ECG-Box patch/torso known-good (`ibi_outlier` 0.75/1.30, `ibi_rmssd_max_ms` 1200); migrate on-device `demo` via `demo_seed`.

## Version 1.0.0-beta.31
- fix: Update banner always re-checks GitHub on app open (6h cache was hiding newer releases published the same day).

## Version 1.0.0-beta.30
- polish: Tech Capture **Start**/**Stop** share the same enabled/disabled colors; Mode shows **Settling** with `m:ss / m:ss` like Elapsed.

## Version 1.0.0-beta.29
- enhancement: Tech **Connect Feather** live GATT client — scan `ECG-Box-Feather`, enable IBI notify, push demo coeffs, `start_stream`, show Last IBI; Start session uses `source_device: FEATHER`. Prefer over third-party BLE scanners for MCU bring-up.
- docs: Advertise name `ECG-Box-Feather` (HnH-only names deprecated); phone test §B for live BLE.

## Version 1.0.0-beta.28
- enhancement: **Disconnect sensor** under the connected H10 line — drops Polar without closing the app (PC bridge stays up).

## Version 1.0.0-beta.27
- enhancement: Feather sim uses RSA-style breathing IBI variation + slow wander so Tech HR and RMSSD keep moving after settle (not a frozen flatline).

## Version 1.0.0-beta.26
- fix: Capture **Start** enables when Tech Feather sim is active (was gated on Polar `sensorConnected` only).

## Version 1.0.0-beta.25
- enhancement: Phone-first Feather path — BLE GATT contract, profile store, packet codecs, Tech **Simulate Feather IBIs** (`source_device: FEATHER`), plus ECG-Box handoff docs. Live GATT connect still next.

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
- enhancement: CI now builds a **signed release** APK for GitHub Releases (`ECG-Phone-Bridge-<tag>.apk`) instead of a debug artifact.
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
- release: Published `ECG-Phone-Bridge-debug-v1.0.0-beta.1.apk` to GitHub Releases via the Android bridge release workflow.
- maintenance: Aligned APK asset naming with release tag format (`ECG-Phone-Bridge-debug-<tag>.apk`) for predictable install/update guidance.
