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

{"app":"ECG-Phone-Bridge","role":"phone_bridge","hostname":"Pixel 7","port":8765}

```



Hosts **must** match on `role: "phone_bridge"`. The `app` string is display/metadata; shipping value is **`ECG-Phone-Bridge`** (older phones may still send `PolarH10Bridge` — treat as equivalent).



### 2.3 Phone → PC (UDP reply) — additive fields (next)



Same object; new fields optional so old HnH still works:



```json

{

  "app": "ECG-Phone-Bridge",

  "role": "phone_bridge",

  "hostname": "Pixel 7",

  "port": 8765,

  "protocol": "phone_bridge_ndjson_v1",

  "bridge_version": "1.0.0-beta.2",

  "features": ["stream", "record", "rmssd_snapshot", "feather_profiles", "ritual_persist"]

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

{"type":"ecg","sample_rate_hz":130,"samples_mv":[0.12,-0.05,0.08],"peak_flags":[0,1,0]}

{"type":"sensor_quality","contact_state":"in_contact","contact":true,"contact_supported":true,"rssi_dbm":-67,"rssi_is_contact":false}

```



Optional on Feather ECG notifies (packet v2+): `peak_flags` — parallel `0`/`1` array marking lookback R-peak samples (same length as `samples_mv`). Absent or all zeros on Polar / legacy Feather.

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

3. **Use last recorded session** — phone retains ritual packages (PROTOCOL §7); conflict UI can call Tech Send / `ritual_request` (host–mode conflict chrome still later).



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

- **Record transfer to FT/HnH:** after Stop, live TCP + final `rmssd` when PC is connected; otherwise the phone **persists** the ritual package and auto-pushes on FT/HnH connect (or Tech **Send** / `ritual_request`). Hosts should `ritual_ack` and dedupe by `session_id` (PROTOCOL §7).



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



## 7. Record-mode ritual persist + delayed transfer



**Status:** Shipping on the phone (persist + auto/manual push). Hosts should `ritual_ack` and dedupe by `session_id`.



After Record Stop (`finalizing` → `completed`), the phone **always** persists a ritual package (even with no PC). Stream Stop does **not** create a package.



### 7.1 Package contents (semantic)



| Field | Required | Notes |

|-------|----------|-------|

| Official `rmssd` snapshot | yes (when calculator has a value) | Same wire object as live Stop — FT value of record |

| `session_state` completed | yes | Includes `session_id`, mode/kind, `emitted_at` |

| IBI series | yes | All accepted IBIs for the capture (ms) |

| ECG samples | preferred | Compact int16 µV; may be empty / truncated |

| `session_summary` | yes (on transfer) | Envelope before payload |



On-disk ECG encoding: **int16 microvolts** + `sample_rate_hz` + `scale_uv_per_lsb` (usually `1.0`). Do **not** store live NDJSON `ecg` batches.



Retention: last **5** Record packages (ring). Ack state is per `session_id`.



### 7.2 Transfer reasons



`transfer_reason` on `session_summary`:



| Value | When |

|-------|------|

| `live_stop` | PC connected at Record Stop |

| `reconnect_replay` | Short-lived RAM/package replay after TCP drop mid-stop (compat) |

| `delayed_push` | Auto after FT/HnH `client_info` (or omitted app → HnH-compatible) when unacked |

| `manual_send` | Tech **Send last ritual** or host `ritual_request` |



VNS-TA / ECG-Box Tuner: **no** auto-push. Manual send still allowed.



### 7.3 Wire messages



**Phone → host — summary**



```json

{

  "type": "session_summary",

  "session_id": "20260915T120000Z-a1b2",

  "mode": "record",

  "kind": "ritual",

  "duration_s": 180.5,

  "ibi_count": 220,

  "has_ecg": true,

  "ecg_sample_hz": 250,

  "ecg_truncated": false,

  "source_device": "FEATHER",

  "emitted_at": "2026-09-15T12:03:00Z",

  "transfer_reason": "delayed_push",

  "rmssd_ms": 52.0

}

```



Then the existing official snapshot (unchanged semantics):



```json

{"type":"rmssd","session_id":"…","rmssd_ms":52.0,"rmssd_source":"bridge","window":{…},"quality":{…},"emitted_at":"…"}

```



```json

{"type":"session_state","session_id":"…","mode":"record","kind":"ritual","state":"completed","source_device":"FEATHER","emitted_at":"…"}

```



Then zero or more chunks (IBI first, then ECG), ~64–128 KiB of payload per line:



```json

{

  "type": "ritual_chunk",

  "session_id": "…",

  "seq": 1,

  "of": 1,

  "content": "ibi",

  "encoding": "int_ms_json",

  "samples": [800, 812, 790]

}

```



```json

{

  "type": "ritual_chunk",

  "session_id": "…",

  "seq": 1,

  "of": 3,

  "content": "ecg",

  "encoding": "int16_uv_b64",

  "sample_rate_hz": 250,

  "scale_uv_per_lsb": 1.0,

  "data": "<base64 little-endian int16>"

}

```



**Host → phone — ack / request**



```json

{"type":"ritual_ack","session_id":"20260915T120000Z-a1b2"}

```



```json

{"type":"ritual_request","session_id":null}

```



`session_id: null` (or omit) = latest unacked, else latest package. Specific id requests that package even if already acked (re-send).



Discover `features` includes `"ritual_persist"`.



Hosts **must** dedupe saves by `session_id` (and may use `emitted_at`). FlareTracker may ignore IBI/ECG and still `ritual_ack` after persisting the official `rmssd`.



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

| `ecg_box_tuner` | ECG-Box PC Tuner (profile sync / Accept) |



Phone may show user + app on the connection UI.

### 8.2 Patient hint → Feather profile (phone)

Hosts **should re-send** `client_info` whenever the active PC patient/profile changes (not only on TCP connect). Each `client_info` is a patient hint:

| Phone behavior | When |
|----------------|------|
| Soft-match `pc_user` | Exact `profile_id` → exact `display_name` → sanitized slug vs `profile_id` |
| Tech **Keep / Switch** confirm | Unique match ≠ active profile **and** source is Feather or Simulate |
| No dialog | Already active; Polar source; no/ambiguous match; `client_app: ecg_box_tuner` |
| Keep debounce | Same `pc_user` suppressed ~30s after Keep (reconnect nags) |
| BLE coeffs | Unchanged — Connect / Save-while-connected still push; Switch only changes active profile |

Phone may emit `status` messages hosts can show as a non-blocking banner (no second confirm on PC):

```json
{"type":"status","message":"Feather profile confirm: Payton?","connected":true}
{"type":"status","message":"Feather profile switched: Payton","connected":true}
{"type":"status","message":"Feather profile kept: Patient 2","connected":true}
{"type":"status","message":"No Feather profile for Sandy","connected":true}
```

Later (not this slice): optional `patient_id` on `client_info` + phone `host_aliases`.

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

Phone:   {"app":"ECG-Phone-Bridge","role":"phone_bridge","hostname":"Pixel","port":8765,"protocol":"phone_bridge_ndjson_v1"}

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

| Record buffer dump / last-session reuse | **Shipping** (ritual persist + delayed transfer §7) |

| New discover prefix | Later |

| Feather **profile sync** for ECG-Box Tuner (`profile_*`) | **Shipping** β.38 (§13) |

| Phone **Startup Wizard** | MVP (first-run + Start session; UI_LAYOUT_MAP §7) |



---



## 13. Feather profile sync (ECG-Box Tuner)



**Product rules (Phase 3):**



- Phone remains **system of record** (`files/feather_profiles/*.json`).

- **Phone Tech picks** the active patient; Tuner **syncs** that profile (does not own a second picker as SoR).

- Tuner **Send to ECG box** → MCU RAM only: Bench USB `SET_COEFFS`, or Live phone NDJSON `coeffs_push` → BLE write (Feather must be connected). Does **not** write the phone library.

- Tuner **Store to phone** → `profile_put` (library SoR only). Does **not** push MCU coeffs.

- While Tuner is linked (`client_app: ecg_box_tuner`), phone Tech Offline is **read-only** and mirrors Tuner via `offline_echo` (blur / ~2–3 s idle).



Advertise capability: discover `features` may include `"feather_profiles"`.



### 13.1 PC → phone



```json

{"type":"client_info","pc_user":"Joel","client_app":"ecg_box_tuner","client_version":"0.1.0"}

{"type":"profile_get_active"}

{"type":"profile_list"}

{"type":"profile_get","profile_id":"patient-2"}

{"type":"profile_put","profile":{ "...": "FEATHER_PROFILE_SCHEMA document" }}

{"type":"coeffs_push","coeffs":{"refractory_ms":420,"peak_end_frac":0.55}}

{"type":"offline_echo","coeffs":{"refractory_ms":420,"peak_end_frac":0.55}}

{"type":"coeffs_get"}

```



| `type` | Purpose |

|--------|---------|

| `profile_get_active` | Return the Tech-selected active profile |

| `profile_list` | Summaries + `active_profile_id` |

| `profile_get` | Full document by id |

| `profile_put` | Save full document (Store). Phone stamps `updated_at`. SoR only. |

| `coeffs_push` | Push `coeffs` JSON to Feather over BLE (Send). No library write. |

| `offline_echo` | Mirror Tuner Offline coeffs to Tech display while linked. No SoR / MCU write. |

| `coeffs_get` | Request Feather BLE `get_coeffs`; phone replies with `mcu_coeffs`. |



When `client_app` is `ecg_box_tuner`, phone **should** push the active `profile` once after `client_info` (same as an implicit `profile_get_active`), and again whenever Tech changes the active patient (or saves Offline coeffs) while the Tuner is linked.



### 13.2 Phone → PC



```json

{

  "type": "profile",

  "active": true,

  "profile": { "schema_version": 1, "profile_id": "patient-2", "display_name": "Joel", "coeffs": { } }

}

```



```json

{

  "type": "profile_list",

  "active_profile_id": "patient-2",

  "profiles": [

    {"profile_id": "demo", "display_name": "Typical patch torso"},

    {"profile_id": "patient-2", "display_name": "Joel"}

  ]

}

```



```json

{"type":"profile_ack","ok":true,"profile_id":"patient-2","message":"Saved Joel"}

{"type":"profile_error","ok":false,"message":"Profile not found"}

{"type":"coeffs_ack","ok":true,"ble":true,"message":"Pushed coeffs to Feather"}

{"type":"coeffs_ack","ok":false,"ble":false,"message":"Feather not connected — Connect Feather on phone, then Send"}

{"type":"mcu_coeffs","ok":true,"coeffs":{"refractory_ms":420}}

{"type":"mcu_coeffs","ok":false,"message":"Feather not connected — Connect Feather on phone, then Refresh Online"}

```



Unknown keys inside `profile` follow the schema (phone may ignore extras). `profile_put` for an unknown `profile_id` → `profile_error` (Tuner does not create patients over the wire in V1).

