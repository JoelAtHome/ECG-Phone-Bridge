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
```

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
| `protocol` / `features` on discover reply | Next |
| `session_control` / `session_state` | Next |
| `rmssd` snapshot messages | Next (Kotlin calculator drafted in-app; not yet on wire) |
| Record buffer dump format | Later |
| New discover prefix | Later |
