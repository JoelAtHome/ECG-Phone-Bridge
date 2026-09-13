# Phone Bridge System Architecture & Telemetry Specification

**Date:** 2026-09-11  
**Status:** Target architecture (intent) with explicit **now / next / later** vs shipping code  
**Scope:** BLE ingestion → Phone Bridge (edge) → Wi‑Fi session transport → laptop consumers  
**Priority (2026-09-11):** VNS-TA Stream field path, then Feather-on-phone (BLE) + profiles/calibrate; contact gates shipped β.21; park `session_control` / ritual buffer dump until after.

---

## 1. Purpose

ECG-Phone-Bridge is the shared Android edge for:

- [FlareTracker](https://github.com/JoelAtHome/FlareTracker)
- [VNS-TA](https://github.com/JoelAtHome/VNS-TA)
- [Hertz & Hearts](https://github.com/JoelAtHome/HertzAndHearts)

ECG sources (Polar H10, or Feather — see below) provide beat timing and optional ECG. The phone owns patient-facing session UI, the **official session RMSSD** (computed on the phone from beat intervals), and Wi‑Fi delivery to one PC connection at a time.

**Naming — “Feather”:** The custom path is **Feather MCU + SparkFun AD8232 + patch electrodes or handgrip electrodes**. Elsewhere in this doc, **Feather** means that whole front-end.

**Naming — “ritual” vs “session”:**

| Term | Meaning |
|------|---------|
| **Ritual** | Brief morning (or similar) capture, typically for FlareTracker or some Hertz & Hearts workflows |
| **Session** | Any bridge run, including longer VNS-TA or HnH work — not everything is a “ritual” |

Wi‑Fi carries **session** data (record or stream). Ritual is a *kind* of session, not the name of the transport.

---

## 2. System topology

The diagram below is **[Mermaid](https://mermaid.js.org/)** markup (a Markdown diagram language). On GitHub and many Markdown previews it renders as a flowchart. The first line `flowchart TD` means “flowchart, top-down.” It is not executable code.

```mermaid
flowchart TD
    subgraph Sources["Layer 1: ECG Data Sources"]
        P["Polar H10"]
        F["Feather MCU + AD8232<br/>+ patch or handgrip electrodes"]
        O["Other ECG Device (Future)"]
    end

    subgraph Edge["Layer 2: Phone Bridge"]
        direction TB
        subgraph UI["Patient UI (bridge-only)"]
            T["Countdown Timer"]
            B["Optional Breathing Pacer"]
            E["Optional ECG"]
        end
        subgraph Dash["Engineering"]
            D["Feather tuning + per-patient profiles"]
            R["Official session RMSSD from IBI"]
        end
    end

    subgraph Host["Layer 3: Laptop PC (one connection at a time)"]
        direction LR
        FT["FlareTracker<br/><i>Snapshot RMSSD + quality metadata</i>"]
        VNS["VNS-TA<br/><i>Real-time ECG, IBI, bridge RMSSD</i>"]
        HH["Hertz & Hearts<br/><i>Real-time ECG, IBI, optional bridge RMSSD</i>"]
    end

    P -->|BLE: IBI, ECG| Edge
    F -->|BLE: IBI, ECG, optional feather_rmssd, tuning coeffs| Edge
    O -.->|BLE / WiFi| Edge

    Edge ==>|WiFi session: Record buffer OR live stream| Host
```

**Sensor → phone transport:** Polar and Feather both use **BLE** into the phone edge. Feather may also support Wi‑Fi on the MCU, but product V1 does **not** use Feather Wi‑Fi as the session path — that would fork discovery, compete with the phone’s LAN role to the PC, and break the shared “either Polar or Feather” UX. Feather Wi‑Fi stays a later escape hatch (bandwidth / bench tooling), not a parallel host contract.

**Hard rules**

| Rule | Decision |
|------|----------|
| Dual source | **Either Polar or Feather** — never both in one session |
| Feather link | **BLE to phone** (same edge as Polar); Feather Wi‑Fi is not the V1 product path |
| PC clients | **One phone bridge-to-PC connection at a time**; multiple users at different times |
| Patient UI | **Bridge-only** (countdown, optional pacer, optional ECG). Hosts must **not** own the patient breathing pacer (drop / demote PC pacers in VNS-TA and HnH; FlareTracker never had one) |
| Peak / beat detection | **On Polar or Feather only** — phone does not primary-detect peaks from ECG |
| Official session RMSSD | **Always computed on the phone bridge from IBI** (the value FlareTracker and other apps should trust) |

---

## 3. Now / next / later

### Now (shipping PolarH10Bridge)

- Polar H10 via Polar BLE SDK: HR/IBI + ECG stream
- UDP discovery (`HnH_PHONE_BRIDGE_DISCOVER_V1`, port **45124**) + TCP NDJSON (default port **8765**)
- Wire types in use: `rr`, `ecg`, `status`; inbound `client_info`
- Ops UI: scan/connect, Wi‑Fi/port, flow diagram, foreground keepalive
- HnH-oriented discovery branding (multi-app contract still evolving)

### Next (shared session bridge — VNS-TA priority)

- ~~Phone **patient breathing pacer**~~ (shipping)
- ~~Phone UI mode select: **Record session** vs **Stream**~~ (shipping)
- ~~Bridge official RMSSD from IBI on the wire~~ (shipping; rolling in stream, final on stop)
- ~~FlareTracker / VNS-TA / HnH light host passes~~ (shipping; FT Companion **v1.0.3** + caregiver H10 path verified)
- **VNS-TA:** field-verify Stream on phone **≥ v1.0.0-beta.18** (prefer **β.21**); keep Polar Stream usable in parallel with Feather work
- ~~Phone **contact / quality gates** (RSSI alone is not on-chest / contact)~~ — shipped **β.21**
- **Feather BLE path** on the phone + **per-patient calibration profiles** (phone-local; see §7) — hard gate before Tuner polish
- Calibrate MVP (thin phone Tech inspect/tweak/Save **or** thin Tuner writing the same profile JSON), then build out **Tuner** as the durable coeff editor (Polar referee, guided Accept)

### Later

- `session_control` / full host–mode conflict UX (all hosts together; do not add for VNS alone)
- Record-mode buffer dump / last-session reuse / session package (FT-oriented; format TBD)
- Assisted / semi-auto Feather tune with human Accept; optional continuous auto-tune (never silent-overwrite last-known-good)
- Share / sync patient calibration profiles across phones (export/import or host-backed)
- Feather MCU Wi‑Fi as optional escape hatch (not a second host contract)
- Additional ECG peripherals
- Optional HTTP/WebSocket transport if TCP NDJSON proves insufficient
- **Phone UI chrome polish** (defer until Feather profiles/calibrate feature additions settle): unify Polar H10 vs Feather connect CTAs (today separate scan/list vs Tech Connect Feather), plus other Tech/Session layout/copy polish from field use — function first, chrome second

---

## 4. Division of labor: peaks vs RMSSD

**Accurate IBI is the hard problem** (peak detection + noise/morphology rejection).  
**RMSSD from a clean IBI series is easy** (successive differences → RMS).

| Stage | Owner | Notes |
|-------|--------|------|
| Peak / beat detection → IBI | Polar firmware **or** Feather detector | Phone does not re-detect from ECG as product path |
| Official session RMSSD | Phone bridge | Same algorithm for Polar and Feather sessions |
| Optional `feather_rmssd` | Feather | Diagnostics / tune validation only — **not** the FlareTracker value of record |
| Optional PC Python RMSSD | HnH / VNS-TA | Research / cross-check only — not FlareTracker path |

**Confidence in Feather IBI:** reasonable when ECG is fairly clean **and** detector coeffs match that patient/lead (Polar cross-check when available). Untuned coeffs on a different morphology (e.g. RBBB, long QRS, large S) can look “steady” and still be wrong.

**HnH import note:** HnH can compute RMSSD from imported **IBI/RR lists**; it does **not** derive RMSSD from raw imported ECG samples alone.

---

## 5. Telemetry contracts (target)

### 5.1 Polar → bridge (BLE)

- IBI / RR (ms)
- ECG samples (~130 Hz; millivolts on current bridge framing)

### 5.2 Feather → bridge (BLE, target)

Product path is **GATT/BLE into the phone**, same semantic role as Polar. Once on the phone, Feather sessions emit the same host NDJSON (`rr` / `ecg` / optional bridge `rmssd`) — VNS-TA and other hosts do not need a Feather-specific wire type.

```typescript
interface FeatherTelemetryPacket {
  timestamp_ms: number;
  ibi_ms: number[];           // primary beat timing
  ecg_raw?: number[];         // ADC or calibrated µV
  feather_rmssd?: number;     // optional debug twin
  tuning_coeffs?: {
    qrs_threshold: number;
    gain: number;
    filter_alpha: number;
    // extend as firmware exposes
  };
}
```

Exact GATT UUIDs/characteristic layout: TBD with Feather firmware; treat above as semantic contract. Do not plan a parallel Feather→PC Wi‑Fi session for V1.

### 5.3 Bridge → laptop (Wi‑Fi session)

**Mode A — Record** (typical **rituals** for FlareTracker / some HnH): buffer during the capture; upload/sync session package when complete.  
**Mode B — Stream** (typical longer **sessions** for HnH / VNS-TA): low-latency NDJSON (or successor) while connected.

Phone UI chooses A vs B.

```typescript
type SourceDevice = "POLAR_H10" | "FEATHER" | "OTHER";
type RmssdSource = "bridge"; // official value always bridge-from-IBI

interface SessionPayload {
  session_id: string;
  start_time_iso: string;
  source_device: SourceDevice;
  mode: "record" | "stream";
  kind?: "ritual" | "session"; // ritual = brief morning-style; optional tag
  sampling_rate_hz?: number;
  data: {
    ibi_series: number[];           // ms
    ecg_samples?: number[];
    rmssd_ms?: number;              // official snapshot when applicable
    rmssd_source: RmssdSource;
    rmssd_window?: {
      settle_trim_s: number;
      analysis_start_s: number;
      analysis_end_s: number;
      method: string;               // e.g. "median_rolling_quality"
    };
    feather_rmssd?: number;         // optional debug
    quality?: {
      skipped_beat_pct?: number;
      flags?: string[];             // e.g. "end_divergence", "no_stable_window"
    };
  };
}
```

**Shipping stream framing (now)** remains TCP newline JSON, e.g.:

- `{"type":"rr","rr_ms":...}`
- `{"type":"ecg","sample_rate_hz":130,"samples_mv":[...]}`
- `{"type":"status",...}`

Evolve with versioned types for `rmssd` snapshots and session control; do not break HnH clients without a protocol version bump.

---

## 6. Official session RMSSD policy (bridge)

### 6.1 Inputs

- IBI stream from either Polar or Feather (accepted beats after source-side rejection when available).

### 6.2 Timing (defaults — profile + tech override later)

Inspired by HnH phases, adapted for short kid rituals and FlareTracker snapshots.

| Parameter | Code default today | Notes |
|-----------|-------------------|-------|
| Sensor / settle trim | **45 s** (intent range 30–45, up to 120) | HnH chart settle default is 15 s; longer settle when the capture is long enough |
| Analysis window | **60 s** (intent ~60–120 s or ~60 beats) | Short-term HRV / HnH live ~60-beat window as reference |
| Final trim | **15 s** (intent ~15–20) | Avoids end-regime artifacts when session is long enough |

**Shipping now:** these live as hardcoded `RmssdCalculator.Config` defaults on the phone. Tech meters’ elapsed target is **settle + analysis** (e.g. 1:45); `short_session` uses the same threshold.

**Later (patient profile — cleaner shape):**

1. **Patient profile** (phone-local V1, same store as Feather coeffs when present) holds suggested `settle_trim_s`, `analysis_window_s`, `final_trim_s`. These apply to **Polar and Feather** sessions — they are ritual/session policy, not detector coeffs.  
2. **Phone is authority** at compute time: official bridge RMSSD and `rmssd.window` always use the values the phone actually applied.  
3. **Tech view** may override for the current run (and optionally “save as this patient’s default”).  
4. **Tuner** may *edit* those profile fields when working in a patient profile (convenient while calibrating Feather) — it is **not** the sole source of settle times, and Polar-only workflows must still set them on the phone / profile without opening Tuner.  
5. Hosts (FlareTracker, etc.) **persist what the bridge reported**; they do not redefine settle policy.

HnH live chart lock uses `SETTLING_DURATION` (default 15 s) + `BASELINE_DURATION` (default 30 s) and a rolling `RMSSD_WINDOW` of 60 beats — useful reference, not identical to FlareTracker snapshot policy.

### 6.3 Window selection (quality-aware)

Do **not** hard-code “always end of session” or “always mid clock time.”

1. After settle, compute rolling RMSSD over the analysis window length.  
2. Prefer a **stable plateau** (median of acceptable rolling estimates), optionally excluding a final trim.  
3. **Do not reject solely because RMSSD is low** (e.g. &lt; 20 ms). Polar-confirmed sessions have shown **~12–17 ms** as real for some morphologies.  
4. Reject / flag on **instability or detector failure** (high skip rate, HR jumps, no stable window, extreme mid↔end divergence without a plateau).  
5. Persist `rmssd_ms`, window bounds, method, and quality flags with every FlareTracker snapshot.

### 6.4 Evidence notes (tuning data)

- **Payton** (no Polar; sensory aversion to strap): mid-session plateau preferred over sudden late collapse to ~17 ms — supports avoiding naive end-of-log quotes when unvalidated.  
- **RBBB / long-QRS subject** (Aug 31 H-frame logs, tuning progressed by file number): later low RMSSD (~12–17) **agreed with simultaneous HnH+Polar** — low values can be correct after proper tune; earlier higher values reflected pre-tune detector error.

---

## 7. Feather calibration & patient profiles

Feather does **not** currently self-tune across patients with very different ECGs. Retune was required when switching subjects.

**V1 (required) — sequence matters**

1. Phone **Feather BLE** connect + stream IBI/ECG into the same edge pipeline as Polar.  
2. Per-patient **profile** on the phone (coeffs **and**, later, session RMSSD timing — see §6.2); push coeffs over BLE at session start.  
3. Calibrate MVP so coeffs are not guesswork (thin Tech UI and/or thin Tuner writing the same JSON).  
4. Build out **Tuner** as the durable editor (Polar referee, better plots, guided Accept).

Also: profiles stay on the **phone that ran calibration**; never silently overwrite; session timing applied by the phone RMSSD path. Untuned coeffs can look steady and still be wrong.

**Editors**

- **Phone Tech view / profile UI:** primary place to view and change session timing defaults; run-time override without losing the saved profile.  
- **Tuner:** co-editor of the same profile document (coeffs + optional session timing) — convenience, not sole authority; do not put settle windows *only* in Tuner.  
- Do not require Tuner for Polar-only patients. Do not block Polar Stream VNS work on Tuner polish.

**V1.5**

- Guided / assisted tune (short still capture → suggested coeffs → human Accept).

**Later**

- Optional continuous auto-tune with last-known-good rollback — never silent sole authority.  
- **Share profiles across phones** (export/import file, or sync via a host app) so a second phone can reuse the same patient’s tune (and timing defaults) without redoing calibration from scratch.

---

## 8. Host consumers

| App | Typical bridge mode | Primary payloads |
|-----|---------------------|------------------|
| FlareTracker | Record → snapshot (often a **ritual**) | Official **bridge** RMSSD + window/quality metadata only — no RMSSD math in FlareTracker |
| VNS-TA | Stream (often a longer **session**) | Real-time ECG, IBI; bridge RMSSD as available. Needs **Polar and Feather** (Feather via phone BLE + profiles). Bridge owns optional patient pacer (already removed from VNS-TA) |
| Hertz & Hearts | Stream or Record | Real-time ECG, IBI; patient pacer is on the phone (demote/remove stuttery PC pacer); PC RMSSD optional cross-check |

---

## 9. Protocol / ops constraints

- Treat discovery + framing as a **versioned contract** when adding FlareTracker / VNS-TA (today still HnH-branded).  
- **PC initiates** discovery + TCP; phone does not dial out.  
- One phone bridge-to-PC connection at a time; different users at different times.  
- **Host–mode negotiation (later):** soft preferences by `client_app` (FT→Record, VNS-TA→Stream, HnH→either); conflict UI on phone Tech + PC; phone remains mode authority — see `docs/PROTOCOL.md` §5.3.  
- Foreground service keepalive remains part of reliable bridging on Android.  
- Dev environment notes (AGP/Gradle/Wi‑Fi) live in `PASSDOWN.md` — not product architecture.

---

## 10. Open items (small)

**Near-term (VNS-TA priority)**

1. Field-verify VNS-TA Stream against phone **≥ v1.0.0-beta.18** (prefer **β.21** for contact gates).  
2. ~~Phone contact / quality gates (do not treat RSSI as contact).~~ — shipped **β.21**.  
3. Exact Feather GATT layout ([`FEATHER_BLE_GATT.md`](./FEATHER_BLE_GATT.md)) + phone BLE client (codecs/profile store/sim path landed; live GATT connect next).  
4. Phone-local profiles + calibrate MVP → then Tuner build-out (§7). See [`FEATHER_REPO_HANDOFF.md`](./FEATHER_REPO_HANDOFF.md).

**Later / parked (safe to defer)**

5. Final numeric defaults for settle / analysis / final-trim after more kid rituals.  
6. **Implement** patient-profile session timing UI; Tuner as optional co-editor (§6.2 / §7).  
7. **Implement** host–mode negotiation / `session_control` conflict UX (`PROTOCOL.md` §5.3) and record buffer dump / last-session reuse — all hosts together.  
8. Formal protocol version string and discovery rename timeline.  
9. Record-mode artifact format (CSV / EDF / JSON package) for host import.  
10. Cross-phone profile export/import UX (schema drafted; share flow later).  
11. Feather MCU Wi‑Fi session path (escape hatch only).

---

## 11. Related repo docs

- `README.md` — layout, releases, protocol caution  
- `docs/PROTOCOL.md` — NDJSON / discovery / RMSSD / session-control sketch  
- `docs/FEATHER_PROFILE_SCHEMA.md` — per-patient Feather calibration JSON  
- `docs/HOST_HANDOFF.md` — consumer checklists for FT / VNS-TA / HnH  
- `PolarH10Bridge/.../rmssd/RmssdCalculator.kt` — official RMSSD window logic (unit-tested; not yet wired to TCP)  
- `PASSDOWN.md` — Android Studio / network tooling passdown  
- `CHANGELOG.md` — bridge release notes  
- [ecg-box](https://github.com/JoelAtHome/ecg-box) — analog ECG firmware, plans, `protocol.md`; tuning notes under `firmware/Tuning Data/`  
- HnH: `SETTLING_DURATION` / `BASELINE_DURATION` / `RMSSD_WINDOW` in config; import computes RMSSD from IBIs only (`hnh/import_session.py`)
