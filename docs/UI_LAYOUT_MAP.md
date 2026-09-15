# ECG Phone Bridge — UI layout map (β.45)

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
│  │   [ TAP TO FIND / Polar·Feather·Sim]│ │  ← remembered kind (Sim = Tech)
│  │     "Polar H10: Bluetooth"         │ │
│  │     [opt in-progress line]         │ │
│  │     [ Change ECG sensor ]          │ │
│  │              │                     │ │
│  │         [ this phone ]             │ │
│  │              │                     │ │
│  │         [ PC / host ]              │ │  ← user + client_app
│  └────────────────────────────────────┘ │
│                                         │
│  [if Polar or Feather BLE linked]       │
│    Connected to: name · contact · …     │
│    (Disconnect via sensor pill modal)   │
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
│  [Change ECG sensor] [Find…]            │
│  [optional connected block]             │
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

**Patient can:** Change ECG sensor (Polar / Feather only), Find / Disconnect, pace breath, open menu (settings / Tech / About).  
**Patient cannot:** Start/Stop capture, Simulate, edit coeffs, see Tech meters / ECG strip.

---

## 3. Tech view (scroll body detail)

```
┌─────────────────────────────────────────┐
│  [hints + Data path diagram]            │
│  [optional connected block]             │
│                                         │
│  ┌─ CAPTURE SESSION ──────────────────┐ │
│  │  Stream ○     Record ○             │ │
│  │  blurb: Stream=VNS-TA / Record=FT  │ │
│  │         HnH: either                │ │
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
│  │  ── Offline ECG-box tuning coeffs ─│ │
│  │  (collapsed by default)            │ │
│  │                                    │ │
│  │  ── ECG-Box detector ──            │ │
│  │  human phase · Last IBI            │ │
│  │  ECG strip                         │ │
│  │  ┌─────────────────────────────┐   │ │
│  │  │     live / sim ECG strip    │   │ │
│  │  └─────────────────────────────┘   │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘
  * gray until coeffs dirty
  * Simulate is Tech-only via Change ECG sensor (not a meters link)
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
  ├─ Check for updates ──► force GitHub check (banner + toast)
  └─ About ──► Dialog (date, version, Close)

Change ECG sensor (under Data path node)
  Patient: Polar H10 / Feather
  Tech: Polar H10 / Feather / Simulate
  [Close]
  Simulate blocked (live Polar / ECG-Box) → disconnect-first dialog
  Connected pill colors: Polar teal · Feather green · Simulate amber (+pulse)

Connected sensor pill → ECG sensor
  [Disconnect]
  [Rescan]
  [Cancel]   (stacked, center-aligned)

Sensor list (Find when Polar is selected)
  Scanning… / radio list / Connecting…
  [Cancel] [Connect]

Feather connecting (Find when Feather is selected)
  Spinner + "Looking for ECG-Box-Feather…" / "Connecting…" / "Setting up link…"
  Error stays with message + [Close]
  [Cancel] stops BLE; tap outside hides overlay but connect continues

Tech-only nested dialogs
  ├─ Profile picker (Change)
  ├─ Add patient (name → OK)
  ├─ Delete confirm
  ├─ Quality flag help [i]
  └─ Simulate Feather blocked (live Polar / ECG-Box connected → OK)
```

---

## 5. View / overlay flow (Mermaid)

```mermaid
flowchart TB
  subgraph main [Main scroll screen]
    Banner[Red banner + hamburger]
    Flow[Data path diagram]
    SrcBlk[Connected block Polar or Feather]
    Banner --> Flow --> SrcBlk
  end

  Menu{☰ menu}
  Banner --> Menu
  Menu --> Conn[Connection settings]
  Menu --> Toggle[Patient ↔ Tech]
  Menu --> About[About]

  Flow -->|Change ECG sensor| KindDlg[Source picker Tech: +Simulate]
  Flow -->|Find Polar| SensorDlg[Sensor list dialog]
  Flow -->|Find Feather| FeatherDlg[Feather connecting dialog]
  Flow -->|Find Simulate| SimPath[Toggle Feather sim]

  Toggle -->|Patient| Pacer[Breathing pacer]
  Toggle -->|Tech| Cap[Capture session]
  Cap --> Tech[Tech meters]
  Tech --> Prof[Offline ECG-box tuning coeffs]
  Tech --> Fble[ECG-Box detector + strip]
  KindDlg -->|Simulate Tech-only| SimPath
```

---

## 6. Markup scratch (print & scribble)

Known parked / cleanup themes from handoff (not commitments):

| Area | Today | Notes / ideas |
|------|--------|----------------|
| Source CTA | Polar / Feather / Simulate (Tech) + Change | Connected pill: Polar teal, Feather green, Sim amber+pulse |
| Capture Start/Stop | Tech only | OK for patient? |
| Breathing pacer | Patient only | |
| Profile + coeffs | Collapsed Offline section | |
| ECG strip | Under ECG-Box detector | |
| First-run | None | **Startup Wizard** wishlist |
| Phone-alone ritual | Persist + delayed push (PROTOCOL §7) | Hosts: `ritual_ack` + dedupe |

---

*Generated from Compose layout as of v1.0.0-beta.52. Update when chrome changes.*
