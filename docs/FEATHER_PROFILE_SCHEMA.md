# Feather / patient profile schema

**Date:** 2026-09-11  
**Status:** Draft — phone-local V1; export/import later  
**Related:** [SYSTEM_ARCHITECTURE.md](./SYSTEM_ARCHITECTURE.md) §6.2 / §7, [PROTOCOL.md](./PROTOCOL.md)

---

## 1. Purpose

Per-patient **profile** on the phone:

1. **Feather detector coeffs** (when using Feather) — pushed to the MCU over **BLE** at session start.  
2. **Session RMSSD timing suggestions** (later) — settle / analysis / final-trim for official bridge RMSSD; used for Polar and Feather.

**Link:** Product path is **phone ← Feather BLE** (same edge as Polar). Feather MCU Wi‑Fi is not the V1 session path. Hosts (including VNS-TA) keep consuming phone NDJSON (`rr` / `ecg` / optional bridge `rmssd`); they do not talk to Feather directly.

**Calibrate / Tuner:** Phone owns the profile store and session push. A thin Tech UI or thin Tuner may create/edit the same JSON; full Tuner (Polar referee, guided Accept) comes after the phone can connect, push coeffs, and stream. Do not put settle windows only in Tuner.

| Phase | Storage |
|-------|---------|
| **V1** | On the **phone that ran calibration** only (app private storage / SharedPreferences or a JSON file under app files) |
| **Later** | Export/import file (or host-backed sync) so another phone can reuse the same profile |

---

## 2. File / document shape

One JSON object per patient (or a store file containing many profiles — see §4).

```json
{
  "schema_version": 1,
  "profile_id": "payton",
  "display_name": "Payton",
  "created_at": "2026-09-03T21:00:00Z",
  "updated_at": "2026-09-03T22:15:00Z",
  "hardware": {
    "board": "feather_huzzah32",
    "frontend": "sparkfun_ad8232",
    "electrode_setup": "patch_torso",
    "sample_hz": 250,
    "notes": "Shoulders + lower right abdomen"
  },
  "coeffs": {
    "refractory_ms": 400,
    "ibi_min_ms": 400,
    "ibi_max_ms": 1500,
    "peak_search_ms": 220,
    "peak_search_min_ms": 60,
    "peak_end_frac": 0.55,
    "r_peak_refine_ms": 200,
    "fiducial_delay_ms": 40,
    "ibi_outlier_lo": 0.75,
    "ibi_outlier_hi": 1.3,
    "ibi_rmssd_max_ms": 1200,
    "qrs_threshold": null,
    "gain": null,
    "filter_alpha": null
  },
  "session_timing": {
    "settle_trim_s": 45,
    "analysis_window_s": 60,
    "final_trim_s": 15
  },
  "polar_agreement": {
    "checked": false,
    "checked_at": null,
    "polar_rmssd_ms": null,
    "bridge_rmssd_ms": null,
    "feather_rmssd_ms": null,
    "abs_delta_bridge_vs_polar_ms": null,
    "notes": null
  },
  "last_session": {
    "at": null,
    "source_device": "FEATHER",
    "ok": null
  }
}
```

`null` coeff fields mean “use firmware default / not yet exposed over BLE.”

---

## 3. Field reference

### 3.1 Identity & meta

| Field | Type | Notes |
|-------|------|------|
| `schema_version` | int | Bump when required fields change |
| `profile_id` | string | Stable slug (filesystem-safe), e.g. `payton` |
| `display_name` | string | UI label |
| `created_at` / `updated_at` | ISO-8601 string | UTC preferred |

### 3.2 Hardware context

| Field | Type | Suggested values |
|-------|------|------------------|
| `hardware.board` | string | `feather_huzzah32`, `esp32_devkit`, … |
| `hardware.frontend` | string | `sparkfun_ad8232` |
| `hardware.electrode_setup` | string | `patch_torso`, `handgrip`, `other` |
| `hardware.sample_hz` | number | Typically `250` |
| `hardware.notes` | string \| null | Free text lead placement |

### 3.3 Detector coeffs

Aligned with current [ecg-box](https://github.com/JoelAtHome/ecg-box) firmware knobs (`firmware/hframe_ecg_hrv/config.h`) plus placeholders for BLE-tunable analog/DSP gains:

| Field | Unit | Role |
|-------|------|------|
| `refractory_ms` | ms | Block re-triggers (e.g. T-wave) |
| `ibi_min_ms` / `ibi_max_ms` | ms | Acceptable IBI range |
| `peak_search_ms` / `peak_search_min_ms` | ms | Peak-search window |
| `peak_end_frac` | 0–1 | End-of-peak fraction |
| `r_peak_refine_ms` | ms | Refine search around crest |
| `fiducial_delay_ms` | ms | Stamp bias vs chest reference |
| `ibi_outlier_lo` / `ibi_outlier_hi` | ratio | vs recent median |
| `ibi_rmssd_max_ms` | ms | Absolute IBI cap for RMSSD path on device |
| `qrs_threshold` | number \| null | When exposed over BLE |
| `gain` | number \| null | When exposed over BLE |
| `filter_alpha` | number \| null | When exposed over BLE |

Phone → Feather push uses whatever subset GATT supports; unknown keys are ignored by older firmware.

### 3.4 Session timing (bridge RMSSD — later)

Suggested settle / analysis / final-trim for **official phone bridge RMSSD**. Applies to Polar **and** Feather captures for this patient. See [SYSTEM_ARCHITECTURE.md](./SYSTEM_ARCHITECTURE.md) §6.2.

| Field | Unit | Default if omitted | Notes |
|-------|------|--------------------|-------|
| `settle_trim_s` | s | 45 | Skip after t=0 before analysis |
| `analysis_window_s` | s | 60 | Rolling / snapshot window length |
| `final_trim_s` | s | 15 | Drop end when session long enough |

**Authority:** phone applies these at compute time; Tech view may override for the current run. Tuner may edit the same profile fields but is not required (Polar-only patients). Hosts persist `rmssd.window` from the bridge, not their own settle policy.

Omit the whole `session_timing` object until the phone implements profile-backed timing (shipping code still uses hardcoded calculator defaults).

### 3.5 Polar agreement (optional referee)

Filled when a **simultaneous Polar H10** (or sequential same-conditions) check was done:

| Field | Notes |
|-------|------|
| `checked` | `true` if a comparison was recorded |
| `checked_at` | When |
| `polar_rmssd_ms` | From HnH/Polar path |
| `bridge_rmssd_ms` | Official phone RMSSD from Feather IBIs |
| `feather_rmssd_ms` | Optional on-device debug twin |
| `abs_delta_bridge_vs_polar_ms` | \|bridge − polar\| |
| `notes` | e.g. “dual-wear morning; agreed ~12–17 ms” |

Absence of Polar data (sensory aversion, etc.) is normal: leave `checked: false`.

---

## 4. On-phone store (V1)

**Option A (recommended):** one file per profile:

`files/feather_profiles/{profile_id}.json`

**Option B:** single store:

```json
{
  "schema_version": 1,
  "active_profile_id": "payton",
  "profiles": [ { "...": "..." }, { "...": "..." } ]
}
```

V1 does **not** sync to PC hosts or cloud.

---

## 5. Export / import (later)

When cross-phone share lands:

- **Export:** write a single-profile `.json` (this schema) or a zip of many.
- **Import:** validate `schema_version`, require `profile_id` + `coeffs`; merge or replace by id.
- Optional host relay: PC apps may *transport* the file; they are not the system of record in V1.

Open item: exact share UX (share sheet vs Files app).

---

## 6. Example — RBBB subject after Polar-agreeing tune

```json
{
  "schema_version": 1,
  "profile_id": "rbbb-adult",
  "display_name": "RBBB adult",
  "created_at": "2026-08-31T18:00:00Z",
  "updated_at": "2026-08-31T20:30:00Z",
  "hardware": {
    "board": "feather_huzzah32",
    "frontend": "sparkfun_ad8232",
    "electrode_setup": "handgrip",
    "sample_hz": 250,
    "notes": "Long QRS, large S; tuning progressed through Aug 31 runs"
  },
  "coeffs": {
    "refractory_ms": 400,
    "ibi_min_ms": 400,
    "ibi_max_ms": 1500,
    "peak_search_ms": 220,
    "peak_search_min_ms": 60,
    "peak_end_frac": 0.55,
    "r_peak_refine_ms": 200,
    "fiducial_delay_ms": 40,
    "ibi_outlier_lo": 0.75,
    "ibi_outlier_hi": 1.3,
    "ibi_rmssd_max_ms": 1200,
    "qrs_threshold": null,
    "gain": null,
    "filter_alpha": null
  },
  "session_timing": {
    "settle_trim_s": 45,
    "analysis_window_s": 60,
    "final_trim_s": 15
  },
  "polar_agreement": {
    "checked": true,
    "checked_at": "2026-08-31T20:00:00Z",
    "polar_rmssd_ms": 15.0,
    "bridge_rmssd_ms": 14.0,
    "feather_rmssd_ms": 12.0,
    "abs_delta_bridge_vs_polar_ms": 1.0,
    "notes": "Simultaneous HnH+Polar; ~12–17 ms band agreed after retune"
  },
  "last_session": {
    "at": "2026-08-31T20:25:00Z",
    "source_device": "FEATHER",
    "ok": true
  }
}
```

---

## 7. Implementation notes (phone)

1. UI (Tech-only): pick patient → load profile → connect Feather **over BLE** → write coeffs → start IBI/ECG.  
2. Never silently overwrite a profile; “Save” / “Recheck” is explicit.  
3. Official session RMSSD stays on the phone from IBI (`RmssdCalculator`); do not treat `feather_rmssd` or Polar agreement fields as the FlareTracker value of record.  
4. Sequence: BLE connect/stream + profile push first; calibrate MVP next; Tuner build-out after that contract works.  
5. **Shipping (β.35+):** Tech picker + Add patient (clone demo) + thin coeff editor + Save; factory seeds **Typical patch torso**, **Patient 1** (Payton), **Patient 2** (Joel; β.36 labels); active id in `feather_profiles/_active_profile_id`; Connect/Save push active coeffs. `session_timing` is stored on the JSON for Tuner co-edit but **not** applied to `RmssdCalculator` yet. Delete-profile UI later.
