# Feather / ecg-box ↔ phone BLE GATT (draft v1)

**Date:** 2026-09-11  
**Status:** Draft locked for phone-first implementation — firmware may implement against this  
**Repos:** [ecg-box](https://github.com/JoelAtHome/ecg-box) (`firmware/hframe_ecg_hrv`), [FEATHER_PROFILE_SCHEMA.md](./FEATHER_PROFILE_SCHEMA.md), [SYSTEM_ARCHITECTURE.md](./SYSTEM_ARCHITECTURE.md) §5.2  
**Not a medical device.**

---

## 1. Goals

| Goal | Detail |
|------|--------|
| Product path | **Feather MCU ↔ phone over BLE** (same edge as Polar). Phone owns Wi‑Fi to the PC. |
| Hosts | Unchanged NDJSON: `rr` / `ecg` / bridge `rmssd` / `session_state` with `source_device: "FEATHER"`. |
| Peaks | On MCU only. Phone does **not** re-detect R-peaks from ECG. |
| Coeffs | Phone pushes patient profile coeffs at session start; firmware ignores unknown keys. |

Do **not** emulate Polar PMD. Do **not** use Feather Wi‑Fi as the V1 session path.

---

## 2. Advertising

| Item | Value |
|------|--------|
| Complete local name | `HnH-Feather` (preferred) or `ECG-Box` |
| Advertise service UUID | Primary service below |
| Appearance / manufacturer data | Optional; phone does not require them for V1 |

Phone scan filters on **service UUID** and/or name prefix `HnH-Feather` / `ECG-Box`.

---

## 3. GATT layout (128-bit custom)

Base namespace: `c3f0a000-7a1e-4f3b-9c2d-8e5f6a7b8c9d` family.

| Role | UUID | Properties |
|------|------|------------|
| **Primary service** | `c3f0a000-7a1e-4f3b-9c2d-8e5f6a7b8c9d` | — |
| **IBI notify** | `c3f0a001-7a1e-4f3b-9c2d-8e5f6a7b8c9d` | Notify |
| **ECG notify** | `c3f0a002-7a1e-4f3b-9c2d-8e5f6a7b8c9d` | Notify (optional but preferred) |
| **Coeffs write** | `c3f0a003-7a1e-4f3b-9c2d-8e5f6a7b8c9d` | Write / Write Without Response |
| **Control write** | `c3f0a004-7a1e-4f3b-9c2d-8e5f6a7b8c9d` | Write / Write Without Response |
| **Status notify** | `c3f0a005-7a1e-4f3b-9c2d-8e5f6a7b8c9d` | Notify (optional) |

Enable CCCD (0x2902) on all Notify characteristics.

Request MTU ≥ 185 when possible. Payloads below fit a default 20-byte ATT MTU when chunked carefully; prefer larger MTU.

---

## 4. Packet formats (little-endian)

### 4.1 IBI notify (`c3f0a001-…`)

| Offset | Type | Field |
|--------|------|--------|
| 0 | u16 | `version` = **1** |
| 2 | u32 | `timestamp_ms` (device uptime or session clock; monotonic preferred) |
| 6 | u8 | `count` (1–8 typical) |
| 7… | u16×N | `ibi_ms[i]` accepted beat intervals only |

Phone maps each `ibi_ms` to NDJSON `{"type":"rr","rr_ms":…}` and feeds `RmssdCalculator`.

### 4.2 ECG notify (`c3f0a002-…`)

| Offset | Type | Field |
|--------|------|--------|
| 0 | u16 | `version` = **1** |
| 2 | u32 | `timestamp_ms` |
| 6 | u16 | `sample_hz` (typically 250) |
| 8 | u8 | `count` |
| 9… | i16×N | `samples` — **microvolts** (µV), signed |

Phone converts µV → mV (`/ 1000.0`) for existing `{"type":"ecg","sample_rate_hz":…,"samples_mv":[…]}`.

If firmware only has ADC counts today, document a temporary `adc_scale_uv_per_count` in Status and still send i16; phone V1 assumes **µV**. Prefer converting on MCU.

### 4.3 Coeffs write (`c3f0a003-…`) — UTF-8 JSON

Body is a JSON object (subset of profile `coeffs`). Example:

```json
{
  "schema_version": 1,
  "refractory_ms": 400,
  "ibi_min_ms": 400,
  "ibi_max_ms": 1500,
  "peak_search_ms": 220,
  "peak_search_min_ms": 60,
  "peak_end_frac": 0.55,
  "r_peak_refine_ms": 200,
  "fiducial_delay_ms": 40,
  "ibi_outlier_lo": 0.65,
  "ibi_outlier_hi": 1.4,
  "ibi_rmssd_max_ms": 1000
}
```

Omit or `null` fields mean “leave firmware default.” Unknown keys → ignore (forward compatible).

If payload exceeds MTU, use **Write with long write** / queued chunks: phone sends one complete JSON per apply (prefer single write after MTU exchange). Firmware applies atomically on valid JSON parse.

### 4.4 Control write (`c3f0a004-…`) — UTF-8 JSON

```json
{"cmd":"start_stream"}
{"cmd":"stop_stream"}
{"cmd":"apply_coeffs"}
{"cmd":"ping"}
```

| `cmd` | Meaning |
|-------|---------|
| `start_stream` | Begin IBI (+ ECG if supported) notifies |
| `stop_stream` | Stop notifies |
| `apply_coeffs` | Apply last successfully written coeffs object (optional if write already applies) |
| `ping` | Optional; expect status notify |

### 4.5 Status notify (`c3f0a005-…`) — UTF-8 JSON (optional)

```json
{
  "version": 1,
  "streaming": true,
  "fw": "hframe_ecg_hrv",
  "board": "huzzah32-3591",
  "sample_hz": 250,
  "leads_off": false,
  "feather_rmssd_ms": 14.2
}
```

`feather_rmssd_ms` is **debug only** — never the FlareTracker value of record.

---

## 5. Session sequence (phone)

1. Scan → connect → discover services → request MTU.  
2. Load active patient profile (if any) → **Coeffs write**.  
3. Enable IBI (+ ECG/Status) CCCD.  
4. **Control** `start_stream`.  
5. Forward IBI/ECG into existing bridge pipeline; `source_device = "FEATHER"`.  
6. On disconnect / user stop: `stop_stream`, tear down GATT.  
7. **Either Polar or Feather** — never both.

---

## 6. Versioning

- Bump packet `version` / JSON `schema_version` only on breaking changes.  
- Phone codecs live in `PolarH10Bridge/.../feather/` and are unit-tested.  
- Firmware should match this doc; if you must diverge, update **this file first** in ECG-Phone-Bridge, then implement.

---

## 7. Test without a reflashed box

Phone includes a **Simulate Feather** Tech path that injects synthetic IBIs through the same `rr` / RMSSD / NDJSON edge (`source_device: "FEATHER"`). Use that until GATT firmware is flashing-ready. Live BLE tests: see [FEATHER_REPO_HANDOFF.md](./FEATHER_REPO_HANDOFF.md).
