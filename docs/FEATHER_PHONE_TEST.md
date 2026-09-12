# Phone-first Feather test procedure

**Date:** 2026-09-11  
**When to use:** Before Feather firmware BLE is flashing-ready (or anytime you want a no-hardware smoke test of the FEATHER host path).

---

## A. Without the ECG box (Simulate Feather)

1. Install a build that includes Tech **Simulate Feather IBIs** (this branch / next release after phone-first Feather work).  
2. Open the app → switch to **Tech view**.  
3. Tap **Simulate Feather IBIs**.  
4. Start **Stream** (or Record).  
5. Confirm Tech meters: IBI count rises, HR ~75, Stream RMSSD moves from `unsettled` to a number after settle.  
6. With VNS-TA (or any host) connected over Wi‑Fi:  
   - Live `rr` lines arrive  
   - `session_state.source_device` is **`FEATHER`**  
   - Optional rolling / stop `rmssd` with `rmssd_source: "bridge"`  
7. Stop sim before connecting a real Polar H10 (sim refuses to start while Polar is connected; connecting Polar stops sim).

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
