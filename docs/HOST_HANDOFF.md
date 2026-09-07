# Host handoff notes (consumer checklist)

**Date:** 2026-09-07  
**Audience:** Maintainers of FlareTracker, VNS-TA, and Hertz & Hearts  
**Status:** Intent / checklist — phone bridge implementation of newer messages is still **next**  
**Related:** [SYSTEM_ARCHITECTURE.md](./SYSTEM_ARCHITECTURE.md), [PROTOCOL.md](./PROTOCOL.md)

Use this when wiring a laptop app to ECG-Phone-Bridge. No code changes in those repos are implied by this doc alone.

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
| Patient UI | Countdown / optional pacer / optional ECG live on the **phone**, not required on the PC |

Ignore unknown `type` values for forward compatibility.

---

## FlareTracker

**Goal:** Store a trustworthy ritual RMSSD (+ metadata) for longitudinal logging.

### Expect from the bridge

- [ ] Prefer **`mode: record`** + **`kind: ritual`** when `session_control` exists  
- [ ] Identify as `client_info.client_app = "flaretracker"` (+ `pc_user` / child profile name)  
- [ ] Treat **`type: rmssd`** with `rmssd_source: "bridge"` as the **value of record**  
- [ ] Persist `rmssd_ms`, `window` (settle/analysis bounds, method), and `quality.flags`  
- [ ] Do **not** compute RMSSD in FlareTracker from ECG or IBI  
- [ ] Do **not** prefer `feather_rmssd_ms` over bridge `rmssd_ms`  
- [ ] Live `rr` / `ecg` optional (nice for QA; not required for the snapshot path)  
- [ ] Handle quality flags (`insufficient_beats`, `no_stable_window`, `short_session`, …) — store with a warning rather than silently dropping, unless product policy says otherwise  
- [ ] Low RMSSD (e.g. 12–17 ms) can be valid — do not reject on magnitude alone  

### Do not expect (V1)

- [ ] Multi-phone profile sync of Feather tunes (phone-local)  
- [ ] Phone breathing pacer driven by FlareTracker  

### Until protocol “next” ships

- Today you may only get live `rr`/`ecg` like HnH. Plan the FT client against [PROTOCOL.md](./PROTOCOL.md) §6 / §11; bridge will emit `rmssd` when that work lands.

---

## VNS-TA

**Goal:** Low-latency live ECG/IBI (and optional RMSSD) during stimulation / analysis sessions.

### Expect from the bridge

- [ ] Prefer **`mode: stream`** + **`kind: session`** (often longer than a morning ritual)  
- [ ] Identify as `client_info.client_app = "vns_ta"`  
- [ ] Consume live **`rr`** and **`ecg`** continuously  
- [ ] Optional: rolling or end-of-run **`rmssd`** from the bridge for display / logs  
- [ ] Optional PC-side RMSSD from IBI for research — **not** a substitute if FT-style canonical value is needed later  
- [ ] **Remove / stop relying on an in-app breathing pacer** — patient pacer is bridge-owned when used  

### Do not expect

- [ ] Ritual buffer-dump as the primary path (use stream)  
- [ ] Dual Polar+Feather in one session  

---

## Hertz & Hearts

**Goal:** Live biofeedback charts (HR / RMSSD / ECG) with existing discover + TCP client.

### Expect from the bridge (today — shipping)

- [ ] Keep using **`HnH_PHONE_BRIDGE_DISCOVER_V1`** + TCP NDJSON  
- [ ] Parse `status`, `rr`, `ecg` as today  
- [ ] Send `client_info` with `pc_user` (active profile) — already supported  

### Expect when adopting “next” protocol (optional, non-breaking)

- [ ] Send `client_app: "hertz_and_hearts"` (or omit — treated as HnH-compatible)  
- [ ] May use `session_control` stream or record  
- [ ] May display bridge `rmssd` snapshots; **PC Python RMSSD remains OK as cross-check**  
- [ ] **May keep HnH’s own pacer** — different workflow from VNS-TA  

### Import path (related)

- [ ] HnH can import **IBI/RR lists** and compute RMSSD; it does **not** derive RMSSD from raw ECG-only imports  
- [ ] Prefer feeding HnH **IBIs** (or native session CSV) if validating Feather offline  

### Compatibility

- [ ] New optional fields must not break current HnH if ignored  
- [ ] Coordinate before any discover-prefix rename  

---

## Quick matrix

| Need | FlareTracker | VNS-TA | HnH |
|------|--------------|--------|-----|
| Live IBI/ECG | Optional | Required | Required (today) |
| Official bridge RMSSD | Required | Optional | Optional |
| Record / ritual | Preferred | Rare | Optional |
| Stream / long session | Rare | Preferred | Common |
| Own pacer on PC | No | No (remove) | Yes (allowed) |
| Own RMSSD math | No | Optional cross-check | Optional cross-check |

---

## Suggested integration order

1. **HnH** — already works; stay compatible while phone adds types.  
2. **VNS-TA** — stream client + drop duplicate pacer; adopt `client_app`.  
3. **FlareTracker** — wait for (or co-develop) `rmssd` + record/ritual; persist snapshot + quality metadata only.
