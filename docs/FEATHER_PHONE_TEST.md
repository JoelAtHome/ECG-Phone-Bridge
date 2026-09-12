# Phone-first Feather test procedure

**Date:** 2026-09-12  
**When to use:** Host-path smoke test (sim) or live MCU bring-up (Tech Feather BLE).

---

## A. Without the ECG box (Simulate Feather)

1. Install a build that includes Tech **Simulate Feather IBIs** (**≥ v1.0.0-beta.27** for RSA liveliness; β.26+ for Start-with-sim).  
2. Open the app → switch to **Tech view**.  
3. Tap **Simulate Feather IBIs** (Start stays disabled until this is on, unless a Polar is connected).  
4. Start **Stream** (or Record) — Start should enable with the sim hint.  
5. Confirm Tech meters: IBI count rises, HR ~75 with gentle breathing variation, Stream RMSSD moves from `unsettled` to a number after settle and keeps drifting a bit.  
6. With VNS-TA (or any host) connected over Wi‑Fi:  
   - Live `rr` lines arrive  
   - `session_state.source_device` is **`FEATHER`**  
   - Optional rolling / stop `rmssd` with `rmssd_source: "bridge"`  
7. **Disconnect sensor** (β.28+) if an H10 was connected; stop sim before connecting Polar (sim refuses while Polar is linked; connecting Polar stops sim).

**Unit tests (dev machine):**

```bat
cd PolarH10Bridge
gradlew.bat test --tests com.example.polarh10bridge.FeatherCodecAndProfileTest
```

---

## B. With real Feather BLE (Tech GATT client — ≥ β.29)

Prereqs: Feather flashed with `hframe_ecg_hrv` `ENABLE_BLE=1`, advertising **`ECG-Box-Feather`** (service `c3f0a000-…`). Prefer this over third-party BLE scanner apps for IBI/ECG notify + `start_stream`.

1. Power Feather (USB or LiPo).  
2. Phone → **Tech view** → **Connect Feather**.  
3. Status should move Scanning → Connecting → Ready → Streaming; **Last IBI** updates (~600–1000 ms at rest); **ECG strip** scrolls.  
4. Optional: **Stop stream** / **Start stream** / **Disconnect**.  
5. **Start Stream** on the capture panel — `source_device: FEATHER`; hosts see live `rr` / `ecg`.  
6. XOR: disconnect Feather before connecting Polar (and the reverse).

Demo profile coeffs are written on connect when present (patch/torso known-good from β.32+).

---

## C. Legacy external scanners

nRF Connect / BLE Scanner can still inspect GATT, but CCCD + UTF-8 control writes are awkward on some apps. Prefer **§B** for bring-up.
