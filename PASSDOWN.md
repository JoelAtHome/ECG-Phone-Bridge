# Passdown — ECG-Phone-Bridge

## Android Studio / AGP (do next after networking is solid)

- **Wi‑Fi:** Laptop USB Wi‑Fi is flaky (TLS mid-download failures / `AEADBadTagException`). Ethernet is fine. A WiFi→Ethernet adapter (VONETS) arrives **tomorrow** — retest large downloads / Gradle sync after that.
- **Local uncommitted change:** `PolarH10Bridge/gradle/libs.versions.toml` has `agp = "9.4.0"` (was `9.1.1`).
  - **Commit** that bump after Android Studio sync is happy.
  - Or **revert to `9.1.1`** if 9.4 is still fighting you.
- Wrapper is on **Gradle 9.3.1** (already cached locally) to avoid downloading 9.6.0 over broken Wi‑Fi. Revisit 9.6 only if AGP requires it and the network is stable.
- AGP Upgrade Assistant often fails on this project (“Cannot find AGP version”) because AGP lives in the version catalog — edit `libs.versions.toml` manually; ignore the assistant.

## Repo context

- Extracted from HertzAndHearts with history preserved.
- GitHub: https://github.com/JoelAtHome/ECG-Phone-Bridge
- Local: `C:\Cursor_Projects\ECG-Phone-Bridge` — open `PolarH10Bridge` in Android Studio.
- HnH stub points here: `Android Bridge App/README.md` in HertzAndHearts.
