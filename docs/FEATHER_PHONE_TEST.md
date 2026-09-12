# Phone-first Feather test procedure

**Date:** 2026-09-11  
**When to use:** Before Feather firmware BLE is flashing-ready (or anytime you want a no-hardware smoke test of the FEATHER host path).

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

## B. With real Feather BLE (after firmware handoff)

Prereqs: firmware implements [FEATHER_BLE_GATT.md](./FEATHER_BLE_GATT.md); phone GATT client wired to scan/connect (follow-on to phone-first codecs).

1. Power Feather; confirm nRF Connect sees `HnH-Feather` / `ECG-Box` + service `c3f0a000-…`.  
2. Phone: scan → connect Feather (not Polar).  
3. Active demo/patient profile coeffs are written; stream starts.  
4. VNS-TA Stream: live `rr` / `ecg`, `source_device: FEATHER`.  
5. Disconnect / reconnect; XOR with Polar still holds.

Until step B’s phone GATT client lands, use **A** for host-path validation and [FEATHER_REPO_HANDOFF.md](./FEATHER_REPO_HANDOFF.md) for firmware work.
