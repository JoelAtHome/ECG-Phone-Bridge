# Handoff — Feather / ecg-box agents (BLE → phone)

**Date:** 2026-09-11  
**From:** ECG-Phone-Bridge (phone edge going first)  
**To:** Agents / humans working in [ecg-box](https://github.com/JoelAtHome/ecg-box)  
**Not a medical device.**

---

## 1. Context

Polar H10 → phone → VNS-TA is field-verified and shipping. Next product path:

**Feather MCU (ecg-box) → phone over BLE → same Wi‑Fi NDJSON hosts already consume.**

Phone work is starting **before** convenient firmware reflash. GATT contract is drafted here so firmware can implement against a fixed target.

| Doc in this repo | Why open it |
|------------------|-------------|
| [FEATHER_BLE_GATT.md](./FEATHER_BLE_GATT.md) | **Authoritative BLE contract** (UUIDs, packets, session sequence) |
| [FEATHER_PROFILE_SCHEMA.md](./FEATHER_PROFILE_SCHEMA.md) | Patient profile JSON / coeffs names |
| [SYSTEM_ARCHITECTURE.md](./SYSTEM_ARCHITECTURE.md) §5.2 / §7 | System intent, XOR Polar/Feather |
| [PROTOCOL.md](./PROTOCOL.md) | Host NDJSON (do **not** fork) |
| [HOST_HANDOFF.md](./HOST_HANDOFF.md) | What VNS-TA / FT / HnH expect |

Phone codecs (unit-tested): `PolarH10Bridge/app/src/main/java/com/example/polarh10bridge/feather/`.

---

## 2. What firmware must do (MVP)

Current `firmware/hframe_ecg_hrv` is serial/plotter oriented. Add a **BLE path** without changing peak ownership:

1. **Advertise** as `HnH-Feather` (or `ECG-Box`) with primary service UUID from GATT doc.  
2. **Notify IBI** (`c3f0a001-…`) for each accepted beat interval (binary v1).  
3. **Optional ECG notify** (`c3f0a002-…`) batches of i16 **µV** @ `sample_hz` (250).  
4. **Accept coeffs JSON write** (`c3f0a003-…`) mapping to the knobs already in `config.h` / runtime detector state.  
5. **Control** `start_stream` / `stop_stream` (`c3f0a004-…`).  
6. Keep serial bench mode working for USB bring-up (BLE can be compile-flagged).

**Do not:**

- Emulate Polar PMD  
- Talk to VNS-TA / FlareTracker / HnH directly over Wi‑Fi for V1 sessions  
- Put official session RMSSD authority on the MCU (optional on-device `feather_rmssd` is debug only)

---

## 3. Coeff mapping (`config.h` ↔ JSON)

| JSON key (profile / BLE) | Firmware today |
|--------------------------|----------------|
| `refractory_ms` | `REFRACTORY_MS` |
| `ibi_min_ms` / `ibi_max_ms` | `IBI_MIN_MS` / `IBI_MAX_MS` |
| `peak_search_ms` / `peak_search_min_ms` | `PEAK_SEARCH_MS` / `PEAK_SEARCH_MIN_MS` |
| `peak_end_frac` | `PEAK_END_FRAC` |
| `r_peak_refine_ms` | `R_PEAK_REFINE_MS` |
| `fiducial_delay_ms` | `FIDUCIAL_DELAY_MS` |
| `ibi_outlier_lo` / `ibi_outlier_hi` | `IBI_OUTLIER_LO` / `IBI_OUTLIER_HI` |
| `ibi_rmssd_max_ms` | `IBI_RMSSD_MAX_MS` |
| `qrs_threshold` / `gain` / `filter_alpha` | Optional later; ignore if unused |

Prefer **runtime mutable** copies of these so BLE write applies without reboot.

---

## 4. Acceptance checks (firmware)

Use nRF Connect (or similar) before phone integration:

1. Device appears as `HnH-Feather` / `ECG-Box` with service `c3f0a000-…`.  
2. After enabling IBI CCCD + `start_stream`, notifications match binary layout in GATT doc; IBIs look physiologic at rest (~600–1000 ms).  
3. Writing a coeffs JSON with e.g. `refractory_ms: 350` changes behavior (or is ACK’d in status) without crash.  
4. `stop_stream` stops notifies.  
5. Disconnect / reconnect recovers cleanly.  
6. Serial plotter path still works when BLE is idle (if both compiled in).

Then phone: scan → connect → profile push → Stream → VNS-TA sees `rr` / `ecg` with `source_device: "FEATHER"`.

---

## 5. What phone already owns (do not reimplement in ecg-box)

- UDP discovery + TCP NDJSON to PC  
- Official bridge RMSSD from IBI  
- Patient profile **store** (phone-local)  
- Record vs Stream UI / breathing pacer  
- Host `client_info` / soft mode preferences  

Tuner / deep calibrate UI may live partly in ecg-box `tools/pc-tuner/` later; **profile system of record for V1 is the phone.**

---

## 6. Suggested firmware task order

1. NimBLE/Bluedroid GATT skeleton + advertise name + service UUID.  
2. IBI notify only (wire existing accepted-beat path).  
3. Control start/stop.  
4. Coeffs JSON write → runtime knobs.  
5. ECG notify (µV).  
6. Optional status notify + `feather_rmssd_ms`.  
7. Joint test with phone APK that includes Feather BLE client.

If GATT doc and firmware must diverge, **update `docs/FEATHER_BLE_GATT.md` in ECG-Phone-Bridge first**, then match codecs.

---

## 7. Out of scope / parked

- Feather MCU Wi‑Fi session to PC  
- Dual Polar+Feather in one session  
- Silent auto-tune overwriting last-known-good  
- Host-backed multi-phone profile sync  

---

## 8. Copy-paste prompt for a Feather/ecg-box agent

```text
Implement BLE GATT for hframe_ecg_hrv so the phone bridge can consume IBI/ECG.

Authoritative contract (do not invent a parallel one):
https://github.com/JoelAtHome/ECG-Phone-Bridge/blob/main/docs/FEATHER_BLE_GATT.md

Also read:
- https://github.com/JoelAtHome/ECG-Phone-Bridge/blob/main/docs/FEATHER_PROFILE_SCHEMA.md
- https://github.com/JoelAtHome/ECG-Phone-Bridge/blob/main/docs/FEATHER_REPO_HANDOFF.md

Requirements:
- Advertise HnH-Feather (or ECG-Box) with service c3f0a000-7a1e-4f3b-9c2d-8e5f6a7b8c9d
- IBI notify binary v1; optional ECG i16 µV; coeffs JSON write mapped to detector knobs; control start_stream/stop_stream
- Keep peak detection on MCU; no Polar PMD emulation; no Wi-Fi session path for V1
- Keep USB serial bench path working
- Document any unavoidable contract change as a PR note so Phone-Bridge codecs can update
```
