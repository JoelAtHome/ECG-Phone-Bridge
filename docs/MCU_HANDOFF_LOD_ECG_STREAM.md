# MCU request — Keep BLE/USB ECG streaming during lead-off

**Date:** 2026-09-19  
**Role:** Phone → ECG-Box **request** (not finish-state)  
**From:** ECG-Phone-Bridge (phone ≥ **v1.0.0-beta.69**)  
**Not a medical device.**

---

## Where to look (ECG-Box)

| Need | Open first |
|------|------------|
| **Done / acceptance / Phase 3 / β.68–β.69 notes** | ECG-Box [`docs/TUNER_AGENT_HANDOFF.md`](https://github.com/JoelAtHome/ecg-box/blob/main/docs/TUNER_AGENT_HANDOFF.md) |
| **Wire / LOD contract** (Lead-off + client handoff table) | ECG-Box [`docs/FEATHER_BLE.md`](https://github.com/JoelAtHome/ecg-box/blob/main/docs/FEATHER_BLE.md) |

**Do not** treat this Phone-Bridge file as shipping status. MCU finish-state lives on the ECG-Box side above.

---

## Original request (context)

With electrodes off, older firmware returned before ADC + ECG emit → phone Tech strip froze on the last QRS while Contact showed “check electrodes”.

**Ask:** While LOD (ADC, not sim), still read ADC + emit USB/BLE ECG (noisy open-lead preferred); suppress IBI; keep `leads_off` status / `# usb leads_off` edges. No new GATT.

**Phone already (β.69):** gates host NDJSON `ecg` + ritual ECG when `use_leads_off && leads_off`; Tech strip updates if BLE samples arrive.

APK: https://github.com/JoelAtHome/ECG-Phone-Bridge/releases/tag/v1.0.0-beta.69
