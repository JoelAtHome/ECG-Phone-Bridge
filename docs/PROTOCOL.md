# Phone Bridge Wire Protocol (sketch)

**Date:** 2026-09-07  
**Status:** Draft — additive to shipping behavior; not all types implemented yet  
**Transport today:** UDP discovery + TCP newline-delimited JSON (NDJSON), UTF-8  
**Related:** [SYSTEM_ARCHITECTURE.md](./SYSTEM_ARCHITECTURE.md), [HOST_HANDOFF.md](./HOST_HANDOFF.md) (per-app checklists)

---

## 1. Goals

- Keep the **existing HnH discovery prefix** working (`HnH_PHONE_BRIDGE_DISCOVER_V1`).
- Add **versioned** NDJSON so FlareTracker / VNS-TA / HnH can evolve without silent breakage.
- Cover **official session RMSSD** snapshots, **record vs stream** control, and **multi-app** identity.

Unknown `type` values: **ignore** (forward-compatible). Required fields missing: treat as malformed for that message only.

---

## 2. Ports & discovery (unchanged core)

| Item | Value |
|------|--------|
| UDP discovery listen (phone) | **45124** |
| Discover probe prefix | `HnH_PHONE_BRIDGE_DISCOVER_V1` (keep for now) |
| TCP session (phone listens) | Default **8765** (user-configurable in app) |

### 2.1 PC → phone (UDP probe)

Plain text starting with the prefix (HnH may append more). Phone ignores non-matching datagrams.

### 2.2 Phone → PC (UDP reply) — shipping today

Single JSON object + newline (or as currently sent):

```json
{"app":"PolarH10Bridge","role":"phone_bridge","hostname":"Pixel 7","port":8765}
```

### 2.3 Phone → PC (UDP reply) — additive fields (next)

Same object; new fields optional so old HnH still works:

```json
{
  "app": "PolarH10Bridge",
  "role": "phone_bridge",
  "hostname": "Pixel 7",
  "port": 8765,
  "protocol": "phone_bridge_ndjson_v1",
  "bridge_version": "1.0.0-beta.2",
  "features": ["stream", "record", "rmssd_snapshot"]
}
```

| Field | Meaning |
|-------|---------|
| `protocol` | Wire dialect id for NDJSON session |
| `bridge_version` | App `versionName` |
| `features` | Capability advertisement |

**Later (not now):** a second discover prefix or `protocol` bump for fully multi-app branding. Until then, leave `HnH_PHONE_BRIDGE_DISCOVER_V1` intact.

---

## 3. TCP session framing

- One **phone bridge ↔ PC** TCP connection at a time.
- Each message = one JSON object + `\n`.
- Phone → PC: telemetry / status / snapshots.
- PC → phone: `client_info`, session control (below).

### 3.1 Shipping types (phone → PC)

```json
{"type":"status","message":"Phone bridge connected","connected":true}
{"type":"rr","rr_ms":812}
{"type":"ecg","sample_rate_hz":130,"samples_mv":[0.12,-0.05,0.08]}
{"type":"sensor_quality","contact_state":"in_contact","contact":true,"contact_supported":true,"rssi_dbm":-67,"rssi_is_contact":false}
```

| `sensor_quality` field | Meaning |
|------------------------|---------|
| `contact_state` | `unknown` \| `in_contact` \| `no_contact` (from sensor contact bit, **not** RSSI) |
| `contact` | Present when `contact_supported` is true |
| `contact_supported` | Whether the sensor reports a contact bit |
| `rssi_dbm` | Optional BLE link strength |
| `rssi_is_contact` | Always `false` — hosts must not treat dBm as on-chest |

When `contact_state` is `no_contact`, the phone **gates** live `rr` / `ecg` (and does not feed those IBIs into official bridge RMSSD). Unknown / unsupported contact does not gate.
### 3.2 Shipping types (PC → phone)

```json
{"type":"client_info","pc_user":"Sandy"}
```

---

## 4. Protocol version

Session dialect id: **`phone_bridge_ndjson_v1`**.

Rules:

1. Additive optional fields never bump the id.
2. Removing/renaming required fields or changing units → new id (`…_v2`) and coordinated client updates.
3. Phone may send `"protocol":"phone_bridge_ndjson_v1"` on the first `status` after connect (optional).

---

## 5. Session control (record vs stream)

Phone UI chooses mode; PC may also request (phone remains authority if conflict — phone wins).

### 5.1 PC → phone

```json
{"type":"session_control","action":"start","mode":"stream","kind":"session"}
```

```json
{"type":"session_control","action":"start","mode":"record","kind":"ritual","session_id":"optional-client-id"}
```

```json
{"type":"session_control","action":"stop"}
```

| Field | Values | Notes |
|-------|--------|------|
| `action` | `start` \| `stop` | |
| `mode` | `stream` \| `record` | Stream = live NDJSON; record = buffer then dump/sync |
| `kind` | `ritual` \| `session` | Optional tag: ritual = brief morning-style |
| `session_id` | string | Optional; phone may mint if absent |

**Patient breathing pacer:** owned and rendered on the **phone** (not the PC). Hosts should not drive a patient-facing pacer. Optional later: PC may suggest inhale/exhale timings in `session_control`; phone remains authority for the animation.

### 5.2 Phone → PC (ack / state)

```json
{
  "type": "session_state",
  "session_id": "20260907T210000Z-ab12",
  "mode": "record",
  "kind": "ritual",
  "state": "recording",
  "source_device": "POLAR_H10"
}
```

`state`: `idle` \| `recording` \| `streaming` \| `finalizing` \| `completed` \| `error`

`source_device`: `POLAR_H10` \| `FEATHER` \| `OTHER`

### 5.3 Host–mode negotiation (later — intent)

**Discovery / link:** Always **PC-initiated**. Host sends UDP discover probe; phone replies; host opens TCP. Same for Hertz & Hearts, VNS-TA, and FlareTracker. The phone does not dial out to hosts.

**One TCP client at a time.** A second host connecting while another is connected: refuse or replace (pick one policy when implementing; document in CHANGELOG). Do not run two PC sessions in parallel.

**Soft preferences (not hard locks at first):**

| `client_app` | Preferred mode / kind | Notes |
|--------------|----------------------|-------|
| `flaretracker` | `record` + `ritual` | Official bridge `rmssd` snapshot is the value of record; Stream is not a substitute for a ritual package |
| `vns_ta` | `stream` + `session` | Live `rr` / `ecg` required |
| `hertz_and_hearts` (or omitted) | either | Backward compatible; no nag if mode differs |

When `client_app` arrives (or changes) and the phone’s current capture mode conflicts with the preference, **surface a conflict on both sides**:

- **Phone (Tech view):** short explanation + actions (examples below). Patient view stays quiet.  
- **PC host:** same situation in host copy (“waiting for phone” / “operator chose …”).  

**Example — FlareTracker while Stream is active**

Copy intent: “FlareTracker needs Record (ritual), not Stream.”

Actions (phone Tech; PC mirrors status):

1. **Switch to Record** — stop stream cleanly (optional final stream `rmssd`), then start Record / ritual.  
2. **Keep streaming** — FT may wait or use live data for QA only; no ritual snapshot until Record completes.  
3. **Use last recorded session** — only once the phone retains a last ritual package / last official `rmssd` + metadata (not shipping yet).

**Example — VNS-TA while Record is active**

Copy intent: “VNS-TA prefers live Stream.”

Actions:

1. **Switch to Stream** — finalize Record (emit official `rmssd` if possible), then start Stream.  
2. **Keep Record** — VNS-TA waits or gets limited live if still emitted.  
3. **Finish Record, then Stream** — explicit two-step.

**Other rules**

- **Phone is authority** for mode changes. PC may *request* via `session_control`; phone confirms or offers the conflict UI.  
- **Mode change mid-run:** always stop → finalize (Record → `rmssd` / state) → then start the new mode. Never silent flip.  
- **`client_app` late:** re-evaluate conflict when `client_info` arrives after connect.  
- **Missing `client_app`:** treat as HnH-compatible; do not show FT/VNS-TA nags.  
- **Idle (no capture):** on connect, optionally apply host default mode as a *suggestion* in Tech UI (FT→Record, VNS-TA→Stream); do not auto-start capture without operator/start intent unless product later says so.  
- **Record transfer to FT/HnH:** after Stop, shipping path today is live TCP + final `rmssd`. Full ritual buffer dump / “send last session” is **later** (`session_summary` + package — see §7).

Wire shape for conflicts (sketch — additive later): phone may emit something like

```json
{"type":"session_conflict","client_app":"flaretracker","current_mode":"stream","preferred_mode":"record","options":["switch_to_record","keep_streaming","use_last_record"]}
```

PC may reply with `session_control` or a small `conflict_choice` once defined. Until then, Tech UI can resolve locally and hosts watch `session_state`.

---

## 6. Official RMSSD snapshot (phone → PC)

Computed **on the phone from IBI** (see architecture). Not a substitute for live `rr` / `ecg` streams.

```json
{
  "type": "rmssd",
  "session_id": "20260907T210000Z-ab12",
  "rmssd_ms": 48.2,
  "rmssd_source": "bridge",
  "source_device": "FEATHER",
  "window": {
    "settle_trim_s": 45,
    "analysis_start_s": 45.0,
    "analysis_end_s": 150.0,
    "method": "median_rolling_quality"
  },
  "quality": {
    "skipped_beat_pct": 3.2,
    "accepted_beats": 180,
    "flags": []
  },
  "feather_rmssd_ms": 51.0
}
```

| Field | Required | Notes |
|-------|----------|------|
| `type` | yes | `"rmssd"` |
| `rmssd_ms` | yes | Official value |
| `rmssd_source` | yes | Always `"bridge"` for official |
| `window` | recommended | For FlareTracker audit / trends |
| `quality.flags` | recommended | See §6.1 |
| `feather_rmssd_ms` | no | Debug twin only |

### 6.1 Quality flags (examples)

| Flag | Meaning |
|------|---------|
| `insufficient_beats` | Not enough IBIs after trims |
| `no_stable_window` | No rolling window passed quality gates |
| `end_divergence` | Late window differs sharply from selected plateau (informational) |
| `high_skip_rate` | Many beats excluded upstream |
| `short_session` | Capture shorter than settle + analysis intent |

**Do not** flag solely because `rmssd_ms` is low (e.g. &lt; 20); that can be real.

Settle / analysis lengths are phone defaults today; later they come from the **patient profile** (with Tech override). See [SYSTEM_ARCHITECTURE.md](./SYSTEM_ARCHITECTURE.md) §6.2. The `window` object on this message always reports what the phone **actually used**.

### 6.2 When to send

| Mode | Typical emit |
|------|----------------|
| `record` / ritual | Once at end (or on stop), with full window metadata — **FlareTracker** |
| `stream` | Optional periodic rolling snapshot + final on stop — **VNS-TA / HnH** |

---

## 7. Record-mode buffer dump (sketch)

After `state: finalizing` → `completed`, phone may send a summary then bulk data (exact packaging TBD):

```json
{"type":"session_summary","session_id":"…","duration_s":180.5,"ibi_count":220,"has_ecg":true}
```

Options under discussion (open item in architecture): NDJSON replay of buffered `rr`/`ecg`, or a single file artifact (CSV/JSON) transferred out-of-band. Prefer defining **semantic content** first (IBI series + optional ECG + one `rmssd` snapshot).

---

## 8. Multi-app identity

### 8.1 PC → phone (extend `client_info`)

```json
{
  "type": "client_info",
  "pc_user": "Sandy",
  "client_app": "flaretracker",
  "client_version": "0.1.0"
}
```

| `client_app` | Suggested |
|--------------|-----------|
| `hertz_and_hearts` | HnH (default if omitted — backward compatible) |
| `vns_ta` | VNS-TA |
| `flaretracker` | FlareTracker |

Phone may show user + app on the connection UI.

---

## 9. Consumer cheat sheet

| App | Connect | Live `rr`/`ecg` | `rmssd` snapshot | Control |
|-----|---------|-----------------|------------------|---------|
| FlareTracker | Discover + TCP | Optional | **Required** (official) | Prefer `record` + `kind: ritual` |
| VNS-TA | Discover + TCP | Yes | Optional rolling | Prefer `stream` |
| Hertz & Hearts | Discover + TCP (today) | Yes | Optional | Stream or record; omit new fields OK |

---

## 10. Compatibility matrix

| Client | Discovery prefix | Minimum TCP types |
|--------|------------------|-------------------|
| HnH (today) | `HnH_PHONE_BRIDGE_DISCOVER_V1` | `status`, `rr`, `ecg`; sends `client_info` |
| New apps | Same prefix for now | + `rmssd`, `session_state`; may send `session_control` |

---

## 11. Examples (happy paths)

### Stream session (VNS-TA)

```text
PC UDP:  HnH_PHONE_BRIDGE_DISCOVER_V1
Phone:   {"app":"PolarH10Bridge","role":"phone_bridge","hostname":"Pixel","port":8765,"protocol":"phone_bridge_ndjson_v1"}
PC TCP:  {"type":"client_info","pc_user":"Joel","client_app":"vns_ta"}
PC TCP:  {"type":"session_control","action":"start","mode":"stream","kind":"session"}
Phone:   {"type":"session_state","session_id":"…","mode":"stream","state":"streaming","source_device":"POLAR_H10"}
Phone:   {"type":"rr","rr_ms":800}
Phone:   {"type":"ecg","sample_rate_hz":130,"samples_mv":[…]}
… 
PC TCP:  {"type":"session_control","action":"stop"}
Phone:   {"type":"rmssd","rmssd_ms":42.1,"rmssd_source":"bridge",…}
Phone:   {"type":"session_state","state":"completed",…}
```

### Ritual record (FlareTracker)

```text
… discover + connect …
PC TCP:  {"type":"client_info","client_app":"flaretracker","pc_user":"Payton"}
PC TCP:  {"type":"session_control","action":"start","mode":"record","kind":"ritual"}
Phone:   {"type":"session_state","mode":"record","kind":"ritual","state":"recording",…}
… buffered locally; optional live rr if desired …
PC TCP:  {"type":"session_control","action":"stop"}
Phone:   {"type":"rmssd","rmssd_ms":52.0,"rmssd_source":"bridge","window":{…},"quality":{"flags":[]}}
Phone:   {"type":"session_summary",…}
Phone:   {"type":"session_state","state":"completed",…}
```

---

## 12. Implementation status

| Item | Status |
|------|--------|
| UDP discover + TCP `rr`/`ecg`/`status`/`client_info` | **Shipping** |
| `protocol` / `features` on discover reply | **Shipping** (additive) |
| `session_control` / `session_state` | **Shipping** (phone UI + PC control) |
| `rmssd` snapshot messages | **Shipping** (final on stop; rolling in stream) |
| `client_app` on `client_info` | **Shipping** (additive) |
| Host–mode negotiation / conflict UI | Later (intent in §5.3) |
| Record buffer dump / last-session reuse | Later |
| New discover prefix | Later |
