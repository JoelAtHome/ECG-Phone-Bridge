# ECG Phone Bridge — UI layout map (β.40)

Printable wireframes of **what ships today**. Mark up freely; this is not a redesign proposal.

**Views:** one main scroll screen. Menu toggles **Patient** ↔ **Tech**. Overlays are dialogs.

---

## 1. Screen stack (top → bottom)

```
┌─────────────────────────────────────────┐
│  RED BANNER                             │
│  "ECG Phone Bridge"              [☰]    │
│                         ┌─────────────┐ │
│                         │ Conn settings│ │
│                         │ Patient/Tech │ │
│                         │ About        │ │
│                         └─────────────┘ │
├─────────────────────────────────────────┤
│  [opt] Update banner: Get update | Later│
├─────────────────────────────────────────┤
│  [opt] Wi‑Fi client off (hotspot note)  │
├─────────────────────────────────────────┤
│                                         │
│           ▼ SCROLL BODY ▼               │
│                                         │
│  [opt] "On your PC… IP:port"            │
│  [opt] Background keep-alive note       │
│                                         │
│  ┌─ DATA PATH (BridgeFlowDiagram) ────┐ │
│  │         [ heart glyph ]            │ │
│  │              │                     │ │
│  │   [ TAP TO FIND SENSORS / H10 ]    │ │  ← Polar CTA
│  │     "Polar H10 button: Bluetooth"  │ │
│  │              │                     │ │
│  │         [ this phone ]             │ │
│  │              │                     │ │
│  │         [ PC / host ]              │ │  ← user + client_app
│  └────────────────────────────────────┘ │
│                                         │
│  [if Polar linked]                      │
│    Connected to: name · contact · dBm   │
│    [ Disconnect sensor ]                │
│    Phone IP / hotspot line              │
│                                         │
│  ╔═══════════════════════════════════╗  │
│  ║  TECH VIEW ONLY                   ║  │
│  ║  · Capture session panel          ║  │
│  ║  · Tech meters (+ profile/BLE)    ║  │
│  ║  · (no breathing pacer)           ║  │
│  ╚═══════════════════════════════════╝  │
│                                         │
│  ╔═══════════════════════════════════╗  │
│  ║  PATIENT VIEW ONLY                ║  │
│  ║  · Breathing pacer                ║  │
│  ║  · (no Capture / Tech meters)     ║  │
│  ╚═══════════════════════════════════╝  │
│                                         │
├─────────────────────────────────────────┤
│  Footer: version · "J. Kobe Software"   │
└─────────────────────────────────────────┘
```

---

## 2. Patient view (scroll body detail)

```
┌─────────────────────────────────────────┐
│  [hints + Data path diagram]            │
│  [optional Polar connected block]       │
│                                         │
│  ┌─ BREATHING PACER ──────────────────┐ │
│  │  title / short help                │ │
│  │  presets:  5.5/5.5  5/5  4/6  4/4  │ │
│  │                                    │ │
│  │         ( expanding circle )       │ │
│  │           Inhale / Exhale          │ │
│  │              [n]                   │ │
│  │                                    │ │
│  │         [ Start / Stop pacer ]     │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

**Patient can:** find Polar (diagram button), disconnect Polar, pace breath, open menu (settings / Tech / About).  
**Patient cannot:** Start/Stop capture, Feather connect, edit coeffs, see Tech meters.

---

## 3. Tech view (scroll body detail)

```
┌─────────────────────────────────────────┐
│  [hints + Data path diagram]            │
│  [optional Polar connected block]       │
│                                         │
│  ┌─ CAPTURE SESSION ──────────────────┐ │
│  │  Stream ○     Record ○             │ │
│  │  Idle|Running · kind · session_id  │ │
│  │  IBIs · RMSSD                      │ │
│  │  [hint if no source]               │ │
│  │  [ Start ]          [ Stop ]       │ │
│  └────────────────────────────────────┘ │
│                                         │
│  ┌─ TECH METERS ──────────────────────┐ │
│  │  Mode / Settling / Elapsed         │ │
│  │  Contact · IBIs · bpm · dBm        │ │
│  │  Bridge RMSSD · Accepted beats     │ │
│  │  Quality flags  [i]  + chips       │ │
│  │                                    │ │
│  │  [ Simulate Feather IBI + ECG ]    │ │
│  │                                    │ │
│  │  ── Patient profile ──             │ │
│  │  Active: name (id)                 │ │
│  │  [Change] [Add patient] [Delete]   │ │
│  │  coeff fields (editable keys)      │ │
│  │  [ Save profile / Save+push ] *    │ │
│  │                                    │ │
│  │  ── Feather BLE test ──            │ │
│  │  phase — detail · Last IBI         │ │
│  │  [Connect Feather]                 │ │
│  │    or [Start][Stop][Disconnect]    │ │
│  │  ECG strip label                   │ │
│  │  ┌─────────────────────────────┐   │ │
│  │  │     live / sim ECG strip    │   │ │
│  │  └─────────────────────────────┘   │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘
  * gray until coeffs dirty
```

---

## 4. Overlays (dialogs)

```
☰ menu
  ├─ Connection settings ──► Dialog
  │     Bridge port [____] [Select ▾]
  │     IP · subnet (read-only)
  │     Keep bridge active in background [switch]
  │     [Cancel]  [Save]*
  │       └─ confirm: Change port? / Disconnect PC?
  │
  ├─ Switch Patient ↔ Tech
  └─ About ──► Dialog (date, version, Close)

Sensor list (from Data path H10 button)
  Scanning… / radio list / Connecting…
  [Cancel] [Connect]

Tech-only nested dialogs
  ├─ Profile picker (Change)
  ├─ Add patient (name → OK)
  ├─ Delete confirm
  └─ Quality flag help [i]
```

---

## 5. View / overlay flow (Mermaid)

```mermaid
flowchart TB
  subgraph main [Main scroll screen]
    Banner[Red banner + hamburger]
    Flow[Data path diagram]
    PolarBlk[Polar connected block]
    Banner --> Flow --> PolarBlk
  end

  Menu{☰ menu}
  Banner --> Menu
  Menu --> Conn[Connection settings]
  Menu --> Toggle[Patient ↔ Tech]
  Menu --> About[About]

  Flow -->|TAP TO FIND SENSORS| SensorDlg[Sensor list dialog]

  Toggle -->|Patient| Pacer[Breathing pacer]
  Toggle -->|Tech| Cap[Capture session]
  Cap --> Tech[Tech meters]
  Tech --> Prof[Patient profile + coeffs]
  Tech --> Fble[Feather BLE + ECG strip]
```

---

## 6. Markup scratch (print & scribble)

Known parked / cleanup themes from handoff (not commitments):

| Area | Today | Notes / ideas |
|------|--------|----------------|
| Polar CTA | Big capsule in Data path | |
| Feather CTA | Buried in Tech meters | Unify with Polar? |
| Capture Start/Stop | Tech only | OK for patient? |
| Breathing pacer | Patient only | |
| Profile + coeffs | Long Tech scroll | Collapse / Tuner-only? |
| ECG strip | Always under Feather BLE | |
| Two connect stories | H10 vs Feather vs Sim | |

---

*Generated from Compose layout as of v1.0.0-beta.40. Update when chrome changes.*
