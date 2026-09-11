# Host handoff notes (consumer checklist)

**Date:** 2026-09-07  
**Audience:** Maintainers of FlareTracker, VNS-TA, and Hertz & Hearts  
**Status:** Intent / checklist — phone bridge implementation of newer messages is still **next**

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
| Shipping types today | Phone→PC: `status`, `rr`, `ecg`. PC→phone: `client_info` (optional `pc_user`) |
| Coming next | `protocol` / `features` on discover; `session_control` / `session_state`; `rmssd` snapshots |
| Sources | **Either Polar or Feather** per session — never both |
| Official RMSSD | Computed **on the phone from IBI**; FlareTracker must not reimplement HRV math |
| Patient UI | Countdown / **breathing pacer** / optional ECG live on the **phone** — hosts do **not** drive the patient pacer |

Ignore unknown `type` values for forward compatibility.

---

## FlareTracker

**Goal:** Store a trustworthy ritual RMSSD (+ metadata) for longitudinal logging.

**Host status (2026-09-10):** light pass done. Record client on the day log (`npm run phone-bridge`) sends `client_app: "flaretracker"` and `pc_user` (child profile name). Does **not** send `session_control`. Soft preference is Record / ritual; phone remains mode authority; capture is not auto-started. Soft conflict only while the phone is in Stream (`streaming`, or `finalizing` if mode is still stream); Keep streaming dismisses the notice and does not change phone mode. Official bridge `rmssd` from a completed Record / ritual is persisted with `window` and `quality.flags` (flagged snapshots stored with a warning; `feather_rmssd_ms` ignored). No PC RMSSD math or chart. No patient pacer. Ritual buffer dump / last-session reuse / `session_control` later.

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
3. **FlareTracker** — light pass done. Record client; sends `client_app: "flaretracker"`; persists official bridge `rmssd` + `window` + `quality.flags`. Does not send `session_control`. Soft conflict only if the phone is in Stream. Ritual buffer dump / last-session reuse later.
