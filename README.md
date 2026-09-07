# ECG-Phone-Bridge

Android phone bridge that streams ECG / heart-rate data (Polar H10, Feather MCU, and related sensors) over Wi‑Fi to desktop apps.

Originally developed inside [HertzAndHearts](https://github.com/JoelAtHome/HertzAndHearts). Extracted here so it can be shared by:

- Hertz & Hearts
- [VNS-TA](https://github.com/JoelAtHome/VNS-TA)
- FlareTracker

## Layout

| Path | Purpose |
|------|---------|
| `PolarH10Bridge/` | Android Studio / Gradle project (open this folder) |
| `polar-ble-sdk/` | Polar BLE SDK used by the app |
| `CHANGELOG.md` | Bridge-specific release notes |
| `Android Dev Workflow.txt` | USB debugging + deploy notes |

## Development

1. Open `PolarH10Bridge` in Android Studio.
2. Enable USB debugging on the phone and connect it.
3. Build/run from Android Studio (see `Android Dev Workflow.txt`).

Local clone path (Windows): `C:\Cursor_Projects\ECG-Phone-Bridge`

## Releases

Debug APKs are built by GitHub Actions (`.github/workflows/android-bridge.yml`) and attached to GitHub Releases as:

`PolarH10Bridge-debug-<tag>.apk`

## Protocol note

Discovery and TCP framing are currently HnH-oriented (`HnH_PHONE_BRIDGE_DISCOVER_V1`, etc.). Treat the wire protocol as a versioned contract when adding VNS-TA / FlareTracker clients; branding and multi-app identity can evolve in later releases.
