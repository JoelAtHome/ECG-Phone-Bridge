# Host handoff notes (consumer checklist)

**Date:** 2026-09-11  
**Audience:** Maintainers of FlareTracker, VNS-TA, and Hertz & Hearts  
**Status:** Shipping phone contract through **v1.0.0-beta.28**; host light passes + FT bench/caregiver path recorded. **FlareTracker Bridge Companion** is live (**v1.0.6**). **Priority:** phone Feather BLE (live GATT connect next) + profiles/calibrate → Tuner. β.27 sim RSA; β.28 Disconnect sensor without closing app.

Use this when wiring a laptop app to ECG-Phone-Bridge. No code changes in those repos are implied by this doc alone.

### Bridge docs (start here)

| Doc | In this repo | On GitHub |
|-----|--------------|-----------|
| This handoff | [HOST_HANDOFF.md](./HOST_HANDOFF.md) | [HOST_HANDOFF.md](https://github.com/JoelAtHome/ECG-Phone-Bridge/blob/main/docs/HOST_HANDOFF.md) |
| System architecture | [SYSTEM_ARCHITECTURE.md](./SYSTEM_ARCHITECTURE.md) | [SYSTEM_ARCHITECTURE.md](https://github.com/JoelAtHome/ECG-Phone-Bridge/blob/main/docs/SYSTEM_ARCHITECTURE.md) |
| Wire protocol (NDJSON) | [PROTOCOL.md](./PROTOCOL.md) | [PROTOCOL.md](https://github.com/JoelAtHome/ECG-Phone-Bridge/blob/main/docs/PROTOCOL.md) |
| Feather patient profiles | [FEATHER_PROFILE_SCHEMA.md](./FEATHER_PROFILE_SCHEMA.md) | [FEATHER_PROFILE_SCHEMA.md](https://github.com/JoelAtHome/ECG-Phone-Bridge/blob/main/docs/FEATHER_PROFILE_SCHEMA.md) |
| Feather BLE GATT + ecg-box handoff | [FEATHER_BLE_GATT.md](./FEATHER_BLE_GATT.md) · [FEATHER_REPO_HANDOFF.md](./FEATHER_REPO_HANDOFF.md) | same paths on GitHub |
| Phone-first Feather test | [FEATHER_PHONE_TEST.md](./FEATHER_PHONE_TEST.md) | same on GitHub |
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
| Sources | **Either Polar or Feather** per session — never both. Feather → phone is **BLE** (not Feather Wi‑Fi as the V1 product path). |
| Official RMSSD | Computed **on the phone from IBI**; FlareTracker must not reimplement HRV math |
| Patient UI | Countdown / **breathing pacer** / optional ECG live on the **phone** — hosts do **not** drive the patient pacer |

Ignore unknown `type` values for forward compatibility.

---

## FlareTracker

**Goal:** Store a trustworthy ritual RMSSD (+ metadata) for longitudinal logging.

**Host status (2026-09-11):** Record path verified on the bench (2026-09-10, phone **v1.0.0-beta.18**) and again with caregiver packaging + **Polar H10** on house Wi‑Fi (Connect → Record → Stop → one HRV row). Stay connected through Stop. Official RMSSD is stored as **whole ms**; FlareTracker **rejects** bridge `rmssd_ms` **above 200** as artifactual (day-log notice; not saved). Low values (e.g. 12–17 ms) remain valid.

The browser cannot do LAN UDP/TCP. **FlareTracker Bridge Companion** (Windows tray) discovers the phone and holds TCP. The day log talks to loopback **`127.0.0.1:45126`** and POSTs the finished HRV event to the FlareTracker API.

**Packaging (FlareTracker repo):** Companion source under `tools/phone-bridge-companion`. CI `bridge-companion.yml` attaches **`FlareTracker-BridgeCompanion-Setup.exe`** to `bridge-companion-v*` Releases (current **v1.0.6** / `PHONE_BRIDGE_COMPANION_VERSION`). Day log: **Start** / **Restart** via `flaretracker-bridge://`; **Download** when nothing listens on `45126`; **Update** when `/status.version` is older than the site. Autostart via `HKCU\...\Run` + `start-hidden.vbs`. FlareTracker GitHub is **private** — Download needs repo access. Profile **Phone bridge** toggle still **default off**. Code signing / SmartScreen follow-up. Dev: `npm run phone-bridge`.

**Coordinator notes (do not regress):**

- Phone **v1.0.0-beta.18** required. Stop and the Record heartbeat used to write the socket on the UI thread. Android dropped those writes (`NetworkOnMainThreadException`) while the phone still looked connected. Greeting `status` arrived; Stop `rmssd` did not. beta.18 writes off the UI thread. Same bug hit any host that expected Stop `rmssd`.
- beta.15: PC disconnect must **not** stop Record. On reconnect, replay last stop (`rmssd` then `session_state`) if idle, or current `session_state` if still recording.
- beta.16+: `emitted_at` on `rmssd` / `session_state`. FlareTracker uses that for the HRV timestamp, not PC receive time. `session_state` heartbeat about every 15s while capture is active (keeps the link warm; not a save).
- Host must **not** recycle the TCP link because it looks quiet. A refresh loop made the phone flap. Stay connected through Stop.
- Short Start/Stop sends `session_state` only. `rmssd` is omitted when the phone calculator has no value. That is expected. A timeline row needs a full Record the phone itself can number.
- Soft preference is Record / ritual. Does **not** send `session_control`. Soft Stream conflict; Keep streaming dismisses only. Stream `rmssd` is QA, not saved. `feather_rmssd_ms` ignored. No PC RMSSD math or patient pacer.
- Persist official bridge RMSSD as **whole ms**. Reject **> 200 ms** as artifactual (notice; not saved). Low values remain valid.
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
- [x] Low RMSSD (e.g. 12–17 ms) can be valid — do not reject *low* magnitude alone  
- [x] Reject absurdly high bridge RMSSD (**> 200 ms**) as artifactual (not saved; day-log notice)  

### Do not expect (V1)

- [x] Multi-phone profile sync of Feather tunes (phone-local)  
- [x] Phone breathing pacer driven by FlareTracker (patient starts/stops pacer on the phone)  

### Until protocol “next” ships

- Bridge now emits `rmssd` + `session_state` when a capture session is started/stopped on the phone (or via `session_control`). Prefer `mode: record` + `kind: ritual` for FT; persist bridge `rmssd` + quality metadata only.

---

## VNS-TA

**Goal:** Low-latency live ECG/IBI (and optional RMSSD) during stimulation / analysis sessions. Product needs **Polar and Feather** sources (one at a time); Feather reaches VNS only via the phone edge.

**Host status (2026-09-10):** light pass done. Shipped on VNS-TA main (`bf3a4da`): PC patient pacer removed from the monitoring layout (`pacer.py` stays dormant). Official bridge `rmssd` snapshots shown in the side-column Bridge RMSSD (`feather_rmssd_ms` ignored; live PC RMSSD chart unchanged). Side column stays fixed so charts do not shift. Already in place: stream client sends `client_app: "vns_ta"`; live `rr` / `ecg` consumed; unknown types ignored. Soft preference is Stream. VNS-TA does **not** send `session_control`. Soft conflict only while the phone is in Record (`recording` / `finalizing`); Keep Record dismisses the notice and does not change phone mode. Idle or Stream shows no conflict. Phone remains the mode authority.

**Next (phone + verify — 2026-09-11):**

1. Field-verify Stream with phone **≥ v1.0.0-beta.18** (same write-thread fixes FT needed). Keep Polar Stream usable while Feather lands.  
2. ~~Phone **contact / quality gates** (RSSI ≠ on-chest)~~ — shipped **β.21** (`sensor_quality`; RR/ECG gated on Polar `contactStatus`).  
3. Phone **Feather BLE** + per-patient profiles + calibrate MVP; then build out **Tuner** as co-editor of the same profile JSON (Polar referee). Hosts keep consuming the same `rr` / `ecg` / optional bridge `rmssd` — no Feather-specific host wire.  
4. Park: `session_control`, ritual buffer dump / last-session reuse, Feather MCU Wi‑Fi as a session path.

### Expect from the bridge

- [ ] Prefer **`mode: stream`** + **`kind: session`** when `session_control` exists (not sent; phone remains mode authority)  
- [x] Identify as `client_info.client_app = "vns_ta"`  
- [x] Consume live **`rr`** and **`ecg`** continuously  
- [x] Optional: rolling or end-of-run **`rmssd`** from the bridge for display / logs  
- [x] Optional PC-side RMSSD from IBI for research — **not** a substitute if FT-style canonical value is needed later  
- [x] **Remove / stop relying on an in-app breathing pacer** — patient pacer is **always** bridge-owned  
- [ ] Feather sessions via phone BLE + profiles (same NDJSON as Polar once the phone edge supports it)  

### Do not expect

- [x] Ritual buffer-dump as the primary path (use stream)  
- [x] Dual Polar+Feather in one session  
- [x] A smooth PC-side patient pacer (use the phone)  
- [x] A parallel Feather→PC Wi‑Fi session in V1 (phone owns Wi‑Fi to the laptop)  
- [x] `session_control` this pass
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
| Polar source | Verified | Required (now) | Common |
| Feather source | Later OK | Required (via phone BLE + profiles) | Optional |
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

## Suggested integration order / priority (2026-09-11)

1. **HnH** — light pass done (`client_app`, ignore unknown types, PC pacer removed, bridge `rmssd` displayed as cross-check). `session_control` still optional.  
2. **FlareTracker** — bench + H10 caregiver path verified (phone **≥ v1.0.0-beta.18**; Companion **v1.0.3** shipped). Day log Start/Download/Update/Restart; does not send `session_control`. Toggle still default off. Phone β.19 (live dBm) / β.20 (in-app update notify) / β.21 (contact gates) shipped. Ritual buffer dump / last-session reuse later.  
3. **VNS-TA (current priority)** — light pass done (`bf3a4da`). Next: Stream field-verify on β.18+ (prefer **β.21** for contact gates) → Feather BLE + profiles/calibrate MVP → Tuner build-out. Do **not** send `session_control` yet. Keep Polar Stream usable in parallel.
