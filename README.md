# ECG-Phone-Bridge

Android phone bridge that streams ECG / heart-rate data (Polar H10, Feather MCU, and related sensors) over Wi‑Fi to desktop apps.

Originally developed inside [HertzAndHearts](https://github.com/JoelAtHome/HertzAndHearts). Extracted here so it can be shared by:

- Hertz & Hearts
- [VNS-TA](https://github.com/JoelAtHome/VNS-TA)
- FlareTracker
- Analog capture hardware: [ecg-box](https://github.com/JoelAtHome/ecg-box)

## Layout

| Path | Purpose |
|------|---------|
| `PolarH10Bridge/` | Android Studio / Gradle project (open this folder) |
| `polar-ble-sdk/` | Polar BLE SDK used by the app |
| `CHANGELOG.md` | Bridge-specific release notes |
| [`docs/SYSTEM_ARCHITECTURE.md`](docs/SYSTEM_ARCHITECTURE.md) | Target architecture & RMSSD / session intent |
| [`docs/PROTOCOL.md`](docs/PROTOCOL.md) | Wire protocol sketch (NDJSON, discovery, RMSSD) |
| [`docs/FEATHER_PROFILE_SCHEMA.md`](docs/FEATHER_PROFILE_SCHEMA.md) | Per-patient Feather calibration JSON (phone-local V1) |
| [`docs/HOST_HANDOFF.md`](docs/HOST_HANDOFF.md) | What FlareTracker / VNS-TA / HnH should expect |
| `docs/SYSTEM_ARCHITECTURE.pdf` | PDF export of the architecture doc |
| `docs/System Architecture FlowChart.pdf` | Architecture flowchart (PDF) |
| `docs/gemini-svg.svg` | Architecture diagram (SVG) |
| `Android Dev Workflow.txt` | USB debugging + deploy notes |

## Architecture

Start with [`docs/SYSTEM_ARCHITECTURE.md`](docs/SYSTEM_ARCHITECTURE.md) for system intent (sources, phone bridge, hosts, official RMSSD). Wire details: [`docs/PROTOCOL.md`](docs/PROTOCOL.md). Host app checklists: [`docs/HOST_HANDOFF.md`](docs/HOST_HANDOFF.md). Feather patient profiles: [`docs/FEATHER_PROFILE_SCHEMA.md`](docs/FEATHER_PROFILE_SCHEMA.md).

## Development

1. Open `PolarH10Bridge` in Android Studio.
2. Enable USB debugging on the phone and connect it.
3. Build/run from Android Studio (see `Android Dev Workflow.txt`).

Local clone path (Windows): `C:\Cursor_Projects\ECG-Phone-Bridge`

## Releases

Debug APKs are built by GitHub Actions (`.github/workflows/android-bridge.yml`) and attached to GitHub Releases as:

`PolarH10Bridge-debug-<tag>.apk`

## Protocol note

Discovery and TCP framing are currently HnH-oriented (`HnH_PHONE_BRIDGE_DISCOVER_V1`, etc.). See [`docs/PROTOCOL.md`](docs/PROTOCOL.md) for the versioned NDJSON sketch (RMSSD snapshots, record vs stream, multi-app `client_info`). Treat the wire protocol as a versioned contract when adding FlareTracker / VNS-TA clients; branding and multi-app identity can evolve in later releases.

Official session RMSSD math (testable, not yet on the wire) lives in `PolarH10Bridge/app/src/main/java/com/example/polarh10bridge/rmssd/`.
