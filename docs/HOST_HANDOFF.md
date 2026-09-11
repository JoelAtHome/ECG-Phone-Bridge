# Host handoff notes (consumer checklist)

**Date:** 2026-09-07  
**Audience:** Maintainers of FlareTracker, VNS-TA, and Hertz & Hearts  
**Status:** Shipping phone contract through **v1.0.0-beta.19**; host light passes + FT bench path recorded. FlareTracker Bridge Companion (Windows tray) is implemented in the FlareTracker repo (Release asset still to publish).

Use this when wiring a laptop app to ECG-Phone-Bridge. No code changes in those repos are implied by this doc alone.

### Bridge docs (start here)

| Doc | In this repo | On GitHub |
|-----|--------------|-----------|
| This handoff | [HOST_HANDOFF.md](./HOST_HANDOFF.md) | [HOST_HANDOFF.md](https://github.com/JoelAtHome/ECG-Phone-Bridge/blob/main/docs/HOST_HANDOFF.md) |
| System architecture | [SYSTEM_ARCHITECTURE.md](./SYSTEM_ARCHITECTURE.md) | [SYSTEM_ARCHITECTURE.md](https://github.com/JoelAtHome/ECG-Phone-Bridge/blob/main/docs/SYSTEM_ARCHITECTURE.md) |
| Wire protocol (NDJSON) | [PROTOCOL.md](./PROTOCOL.md) | [PROTOCOL.md](https://github.com/JoelAtHome/ECG-Phone-Bridge/blob/main/docs/PROTOCOL.md) |
| Feather patient profiles | [FEATHER_PROFILE_SCHEMA.md](./FEATHER_PROFILE_SCHEMA.md) | [FEATHER_PROFILE_SCHEMA.md](https://github.com/JoelAtHome/ECG-Phone-Bridge/blob/main/docs/FEATHER_PROFILE_SCHEMA.md) |
| Repo README | [README.md](../README.md) | [ECG-Phone-Bridge](https://github.com/JoelAtHome/ECG-Phone-Bridge) |

---

## Shared facts (all hosts)

| Item | Expect |
|------|--------|
| Discovery | UDP **45124**, probe prefix **`HnH_PHONE_BRIDGE_DISCOVER_V1`** (unchanged for now) |
| Reply | JSON with at least `app`, `role`, `hostname`, `port` |
| Session | TCP to phone `port` (default **8765**); **one** PC connection at a time |
| Framing | NDJSON (one JSON object per line, UTF-8) |
| Shipping types today | Phone→PC: `status`, `session_state`, `rmssd`, `rr`, `ecg`. PC→phone: `client_info` (optional `pc_user`). Phone listens for `session_control` but hosts should not send it until a later pass. |
| Phone APK | Sideload **v1.0.0-beta.18** (`com.joelathome.ecgphonebridge`, versionCode 18) or newer. Earlier builds drop Stop `rmssd` (and any other write started on the UI thread). |
| Sources | **Either Polar or Feather** per session — never both |
| Official RMSSD | Computed **on the phone from IBI**; FlareTracker must not reimplement HRV math |
| Patient UI | Countdown / **breathing pacer** / optional ECG live on the **phone** — hosts do **not** drive the patient pacer |

Ignore unknown `type` values for forward compatibility.

---

## FlareTracker

**Goal:** Store a trustworthy ritual RMSSD (+ metadata) for longitudinal logging.

**Host status (2026-09-10, verified on the bench):** record path works end to end against phone **v1.0.0-beta.18**. Connect in the day log, Record on the phone, leave the TCP link up, Stop on the phone. FlareTracker saves one HRV row (`rmssd_source: "bridge"`) without a disconnect. Example: panel “Saved ritual RMSSD 8.21 ms” after `rmssd` then `session_state` (`record` / `completed`).

The browser cannot do LAN UDP/TCP. A **local companion** discovers the phone and holds the TCP session. The day log talks to that companion on loopback **`127.0.0.1:45126`** and POSTs the finished HRV event to the FlareTracker API.

**Packaging (FlareTracker repo):** **FlareTracker Bridge Companion** (Windows tray) lives under `tools/phone-bridge-companion` (same `127.0.0.1:45126` API). Distinct from the Android **ECG Phone Bridge** app. CI workflow `bridge-companion.yml` attaches **`FlareTracker-BridgeCompanion-Setup.exe`** to a `bridge-companion-v*` GitHub Release. Day log shows **Download Bridge Companion** when nothing is listening on `45126`, and **Update Bridge Companion** when `/status.version` is older than `PHONE_BRIDGE_COMPANION_VERSION`. Installer registers per-user `HKCU\...\Run` (hidden via `start-hidden.vbs`). Profile toggle stays **default off** until that Release asset exists and Download/Update are verified. Code signing / SmartScreen is follow-up. Dev still: `npm run phone-bridge`.

**Coordinator notes (do not regress):**

- Phone **v1.0.0-beta.18** required. Stop and the Record heartbeat used to write the socket on the UI thread. Android dropped those writes (`NetworkOnMainThreadException`) while the phone still looked connected. Greeting `status` arrived; Stop `rmssd` did not. beta.18 writes off the UI thread. Same bug hit any host that expected Stop `rmssd`.
- beta.15: PC disconnect must **not** stop Record. On reconnect, replay last stop (`rmssd` then `session_state`) if idle, or current `session_state` if still recording.
- beta.16+: `emitted_at` on `rmssd` / `session_state`. FlareTracker uses that for the HRV timestamp, not PC receive time. `session_state` heartbeat about every 15s while capture is active (keeps the link warm; not a save).
- Host must **not** recycle the TCP link because it looks quiet. A refresh loop made the phone flap. Stay connected through Stop.
- Short Start/Stop sends `session_state` only. `rmssd` is omitted when the phone calculator has no value. That is expected. A timeline row needs a full Record the phone itself can number.
- Soft preference is Record / ritual. Does **not** send `session_control`. Soft Stream conflict; Keep streaming dismisses only. Stream `rmssd` is QA, not saved. `feather_rmssd_ms` ignored. No PC RMSSD math or patient pacer.
- One PC connection at a time (phone accepts one TCP client).
- Ritual target remains **phone + sensor only**; PC during Record is today’s shipping path. Phone-persisted ritual + delayed transfer is later (this repo).

Later (phone bridge + hosts): ritual buffer dump, `session_summary`, last-session reuse, sending `session_control`, host–mode conflict UI.

### Expect from the bridge

- [ ] Prefer **`mode: record`** + **`kind: ritual`** when `session_control` exists  
- [x] Identify as `client_info.client_app = "flaretracker"` (+ `pc_user` / child profile name)  
- [x] Treat **`type: rmssd`** with `rmssd_source: "bridge"` as the **value of record**  
- [x] Persist `rmssd_ms`, `window` (settle/analysis bounds, method), and `quality.flags`  
- [x] Do **not** compute RMSSD in FlareTracker from ECG or IBI  
- [x] Do **not** prefer `feather_rmssd_ms` over bridge `rmssd_ms`  
- [x] Live `rr` / `ecg` optional (nice for QA; not required for the snapshot path)  
- [x] Handle quality flags (`insufficient_beats`, `no_stable_window`, `short_session`, …) — store with a warning rather than silently dropping, unless product policy says otherwise  
- [x] Low RMSSD (e.g. 12–17 ms) can be valid — do not reject on magnitude alone  

### Do not expect (V1)

- [x] Multi-phone profile sync of Feather tunes (phone-local)  
- [x] Phone breathing pacer driven by FlareTracker (patient starts/stops pacer on the phone)  

### Until protocol “next” ships

- Bridge now emits `rmssd` + `session_state` when a capture session is started/stopped on the phone (or via `session_control`). Prefer `mode: record` + `kind: ritual` for FT; persist bridge `rmssd` + quality metadata only.

---

## VNS-TA

**Goal:** Low-latency live ECG/IBI (and optional RMSSD) during stimulation / analysis sessions.

**Host status (2026-09-10):** light pass done. Shipped on VNS-TA main (`bf3a4da`): PC patient pacer removed from the monitoring layout (`pacer.py` stays dormant). Official bridge `rmssd` snapshots shown in the side-column Bridge RMSSD (`feather_rmssd_ms` ignored; live PC RMSSD chart unchanged). Side column stays fixed so charts do not shift. Already in place: stream client sends `client_app: "vns_ta"`; live `rr` / `ecg` consumed; unknown types ignored. Soft preference is Stream. VNS-TA does **not** send `session_control`. Soft conflict only while the phone is in Record (`recording` / `finalizing`); Keep Record dismisses the notice and does not change phone mode. Idle or Stream shows no conflict. Phone remains the mode authority.

### Expect from the bridge

- [ ] Prefer **`mode: stream`** + **`kind: session`** when `session_control` exists (not sent; phone remains mode authority)  
- [x] Identify as `client_info.client_app = "vns_ta"`  
- [x] Consume live **`rr`** and **`ecg`** continuously  
- [x] Optional: rolling or end-of-run **`rmssd`** from the bridge for display / logs  
- [x] Optional PC-side RMSSD from IBI for research — **not** a substitute if FT-style canonical value is needed later  
- [x] **Remove / stop relying on an in-app breathing pacer** — patient pacer is **always** bridge-owned  

### Do not expect

- [x] Ritual buffer-dump as the primary path (use stream)  
- [x] Dual Polar+Feather in one session  
- [x] A smooth PC-side patient pacer (use the phone)  

---

## Hertz & Hearts

**Goal:** Live biofeedback charts (HR / RMSSD / ECG) with existing discover + TCP client.

**Host status (2026-09-10):** light pass done. UI is Phone Bridge only (`PC BLE` code remains dormant and a stored `ble` pref is rewritten). PC patient pacer removed. Official bridge `rmssd` snapshots are displayed as a cross-check. Soft preference remains either Stream or Record; phone stays mode authority. HnH does **not** send `session_control` yet (optional / later).

### Expect from the bridge (today — shipping)

- [x] Keep using **`HnH_PHONE_BRIDGE_DISCOVER_V1`** + TCP NDJSON  
- [x] Parse `status`, `rr`, `ecg` as today  
- [x] Send `client_info` with `pc_user` (active profile) — already supported  

### Expect when adopting “next” protocol (optional, non-breaking)

- [x] Send `client_app: "hertz_and_hearts"` (or omit — treated as HnH-compatible)  
- [ ] May use `session_control` stream or record (not implemented; phone remains mode authority)  
- [x] May display bridge `rmssd` snapshots; **PC Python RMSSD remains OK as cross-check** (side-column **Bridge RMSSD**; live chart unchanged)  
- [x] **Demote or remove the PC breathing pacer** for patient use — phone owns the patient-facing pacer (PC one has been slow/stuttery across machines)  

### Import path (related)

- [x] HnH can import **IBI/RR lists** and compute RMSSD; it does **not** derive RMSSD from raw ECG-only imports  
- [x] Prefer feeding HnH **IBIs** (or native session CSV) if validating Feather offline  

### Compatibility

- [x] New optional fields must not break current HnH if ignored  
- [x] Coordinate before any discover-prefix rename  

---

## Quick matrix

| Need | FlareTracker | VNS-TA | HnH |
|------|--------------|--------|-----|
| Live IBI/ECG | Optional | Required | Required (today) |
| Official bridge RMSSD | Required | Optional | Optional |
| Record / ritual | Preferred | Rare | Optional |
| Stream / long session | Rare | Preferred | Common |
| Own pacer on PC | No | No (remove) | No (demote/remove; phone owns patient pacer) |
| Own RMSSD math | No | Optional cross-check | Optional cross-check |
| Mode if conflict | Prefer Record | Prefer Stream | Either OK |

---

## Host–mode negotiation (later)

**Link:** PC discovers and opens TCP (same for all hosts). One PC at a time.

**Soft preferences:** FT → Record/ritual; VNS-TA → Stream; HnH → either. Phone remains mode authority.

When preference conflicts with the phone’s current capture (e.g. FT while streaming):

- Show copy on **phone Tech view** and on the **PC host**.  
- Offer actions such as switch mode, keep current mode, or (later) use last recorded ritual.  
- Never silent mid-run flip — stop/finalize, then start.  
- Missing `client_app` → HnH-compatible, no nag.

Full intent + sketch messages: [PROTOCOL.md](./PROTOCOL.md) §5.3. Record buffer dump / “send last session” still later.

---

## Suggested integration order

1. **HnH** — light pass done (`client_app`, ignore unknown types, PC pacer removed, bridge `rmssd` displayed as cross-check). `session_control` still optional.  
2. **VNS-TA** — light pass done and shipped (`bf3a4da`). Soft conflict only if the phone is in Record. `session_control` still not sent.  
3. **FlareTracker** — bench-verified 2026-09-10 against phone **v1.0.0-beta.18**. **FlareTracker Bridge Companion** (Windows tray; not the Android ECG Phone Bridge app) is in the FlareTracker repo (`tools/phone-bridge-companion` + day-log Download/Update). Publish a `bridge-companion-v*` Release with `FlareTracker-BridgeCompanion-Setup.exe`, then push. Does not send `session_control`. Toggle stays off until that Release asset is live. Ritual buffer dump / last-session reuse later (phone).
