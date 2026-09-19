# Phone-first Feather test procedure

**Date:** 2026-09-12  
**When to use:** Host-path smoke test (sim) or live MCU bring-up (Tech Feather BLE).

---

## A. Without the ECG-Box (Simulate Feather)

1. Install a build that includes Tech **Simulate Feather IBI + ECG** (**≥ v1.0.0-beta.34**; β.27+ for RSA IBIs only; β.26+ for Start-with-sim).  
2. Open the app → switch to **Tech view**.  
3. Tap **Simulate Feather IBI + ECG** (Start stays disabled until this is on, unless a Polar is connected).  
4. Start **Stream** (or Record) — Start should enable with the sim hint.  
5. Confirm Tech meters: IBI count rises, HR ~75 with gentle breathing variation, Stream RMSSD moves from `unsettled` to a number after settle and keeps drifting a bit; **ECG strip** scrolls textbook PQRST.  
6. With VNS-TA (or any host) connected over Wi‑Fi:  
   - Live `rr` and `ecg` lines arrive  
   - `session_state.source_device` is **`FEATHER`**  
   - Optional rolling / stop `rmssd` with `rmssd_source: "bridge"`  
7. **Disconnect sensor** (β.28+) if an H10 was connected; stop sim before connecting Polar (sim refuses while Polar is linked; connecting Polar stops sim).

**Unit tests (dev machine):**

```bat
cd phone-bridge
gradlew.bat :app:testDebugUnitTest --tests com.example.polarh10bridge.FeatherCodecAndProfileTest
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

**β.36+:** Factory labels — Typical patch torso / Patient 1 (Payton) / Patient 2 (Joel). Active profile coeffs are written on Connect (patch/torso known-good from β.32+ for the typical seed). **β.35+:** Tech **Patient profile** picker — Change / Add patient (clone typical) / edit coeffs → Save; Connect Feather pushes the **active** profile (Save while connected also pushes). Session timing remains phone-default until wired.

### B.1 Lead-off / IBI quality (phone ≥ **v1.0.0-beta.68**)

Prereqs: stock 3-lead board with `USE_LEADS_OFF=1` (default). Host on same Wi‑Fi optional.

1. Connect Feather → Streaming; Tech **Contact** should move to **leads OK (Feather)** once status/QC carries LOD (or stay “not reported” until the first LOD-bearing notify).  
2. With a host linked, open one RA/LA/RL snap (or float a lead). Expect:  
   - Tech: **Contact: check electrodes (leads off)** (red)  
   - Host NDJSON: `{"type":"status",…,"use_leads_off":true,"leads_off":true,"source_device":"FEATHER"}` (edge only — not every heartbeat)  
   - Live `rr` quiet or sparse (MCU publish gate); no steady fake ~110–120 bpm from phone  
3. Re-seat leads → Tech **leads OK**; host `status` with `leads_off:false`; IBIs resume within a few seconds.  
4. **Do not** expect `sensor_quality.contact_state` to flip for Feather (Polar-only). Hosts that show “check electrodes” must read `status.leads_off` when `use_leads_off` is true.

**Unit tests:**

```bat
cd phone-bridge
gradlew.bat :app:testDebugUnitTest --tests com.example.polarh10bridge.FeatherLeadOffTest
```

---

## C. Legacy external scanners

nRF Connect / BLE Scanner can still inspect GATT, but CCCD + UTF-8 control writes are awkward on some apps. Prefer **§B** for bring-up.
