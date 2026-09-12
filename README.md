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
| [`docs/FEATHER_BLE_GATT.md`](docs/FEATHER_BLE_GATT.md) | Draft Feather ↔ phone BLE GATT contract |
| [`docs/FEATHER_REPO_HANDOFF.md`](docs/FEATHER_REPO_HANDOFF.md) | Handoff for ecg-box / Feather firmware agents |
| [`docs/FEATHER_PHONE_TEST.md`](docs/FEATHER_PHONE_TEST.md) | Phone-first test procedure (sim + live BLE) |
| [`docs/HOST_HANDOFF.md`](docs/HOST_HANDOFF.md) | What FlareTracker / VNS-TA / HnH should expect |
| `docs/SYSTEM_ARCHITECTURE.pdf` | PDF export of the architecture doc |
| `docs/System Architecture FlowChart.pdf` | Architecture flowchart (PDF) |
| `docs/gemini-svg.svg` | Architecture diagram (SVG) |
| `Android Dev Workflow.txt` | USB debugging + deploy notes |

## Architecture

Start with [`docs/SYSTEM_ARCHITECTURE.md`](docs/SYSTEM_ARCHITECTURE.md) for system intent (sources, phone bridge, hosts, official RMSSD). Wire details: [`docs/PROTOCOL.md`](docs/PROTOCOL.md). Host app checklists: [`docs/HOST_HANDOFF.md`](docs/HOST_HANDOFF.md). Feather: [profile schema](docs/FEATHER_PROFILE_SCHEMA.md), [BLE GATT](docs/FEATHER_BLE_GATT.md), [ecg-box handoff](docs/FEATHER_REPO_HANDOFF.md), [phone test](docs/FEATHER_PHONE_TEST.md).

## Development

1. Open `PolarH10Bridge` in Android Studio.
2. Enable USB debugging on the phone and connect it.
3. Build/run from Android Studio (see `Android Dev Workflow.txt`).

Local clone path (Windows): `C:\Cursor_Projects\ECG-Phone-Bridge`

## Releases

Signed release APKs are built by GitHub Actions (`.github/workflows/android-bridge.yml`) and attached to GitHub Releases as:

`PolarH10Bridge-<tag>.apk`

Example: [v1.0.0-beta.4](https://github.com/JoelAtHome/ECG-Phone-Bridge/releases) (after publish).

**Install / upgrade notes**

- Package id is `com.joelathome.ecgphonebridge` (not `com.example.*`). Older “Polar H10 Bridge” installs with `com.example.polarh10bridge` will **not** update in place — uninstall the old app, then install the new APK.
- Sideload from the phone browser/Files; allow install from that source if prompted. Release-signed builds are less likely to trip Play Protect than debug/`com.example` APKs.
- From **v1.0.0-beta.20**, the app checks GitHub Releases on launch and shows a dismissible banner when a newer version is published.

## Protocol note

Discovery and TCP framing are currently HnH-oriented (`HnH_PHONE_BRIDGE_DISCOVER_V1`, etc.). See [`docs/PROTOCOL.md`](docs/PROTOCOL.md) for the versioned NDJSON sketch (RMSSD snapshots, record vs stream, multi-app `client_info`). Treat the wire protocol as a versioned contract when adding FlareTracker / VNS-TA clients; branding and multi-app identity can evolve in later releases.

Official session RMSSD math (testable, not yet on the wire) lives in `PolarH10Bridge/app/src/main/java/com/example/polarh10bridge/rmssd/`.
