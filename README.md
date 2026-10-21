# SelfGuard

A personal, offline self-control app for a single Samsung Galaxy A20 (Android 11 / One UI 3.1).
It blocks adult websites, keywords, and chosen apps, makes itself hard to disable, and keeps a
streak of clean days.

Built with Kotlin and Jetpack Compose. Sideloaded only — never published to Google Play, so no
Play policies apply (`QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, Device Admin and Device Owner
are used without Play justification).

> **Status: skeleton + documentation.** No feature code has been written yet. The project builds a
> placeholder APK so the toolchain is verified; the implementation follows
> [docs/PHASES.md](docs/PHASES.md).

---

## Documentation

| Document | What it covers |
|---|---|
| [docs/PRD.md](docs/PRD.md) | Product requirements: goal, user, features, success criteria, risks, non-goals |
| [docs/FRD.md](docs/FRD.md) | Functional requirements per feature, with acceptance criteria and traceability |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, data storage (Room + DataStore), services, matching engine, protection state machine, battery strategy |
| [docs/PHASES.md](docs/PHASES.md) | Phased build plan with a checklist per phase and the on-device acceptance matrix |
| [docs/DEVICE_OWNER.md](docs/DEVICE_OWNER.md) | Exact ADB setup for Device Owner, every failure mode, and the safe release flow |

## Feature overview

1. **Website blocking** — an AccessibilityService reads the browser address bar and screen text,
   matches against host and keyword rules, then sends the phone Home and shows a block screen.
2. **App blocking** — foreground app detection (Accessibility events first, `UsageStatsManager` poll
   as backup, only while the screen is on) blocks listed apps; Device Owner additionally suspends
   and hides them.
3. **Custom blocklists** — editable lists of apps, websites, and keywords, with import/export.
4. **Protection durability** — Device Admin + Device Owner, a 24–72 h cooldown, an accountability
   passphrase, a loud tamper alarm, and a controlled, non-destructive release flow.
   **No data-wipe policy exists anywhere in this app.**
5. **Settings shielding** — the Settings screens that would let you switch off the service,
   revoke usage access, or uninstall the app are intercepted.
6. **Streak & statistics** — clean-day streak, best streak, block counts, tamper log, 30-day chart.
7. **Panic button** — a breathing exercise (box / 4-7-8 / custom) with a cool-down window.

## Build

```bash
./gradlew :app:assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:test                 # JVM unit tests
./gradlew :app:lint                 # Android lint
```

Requirements: JDK 17, Android SDK with platform 36 and build-tools 36 (`ANDROID_HOME` set, or
`sdk.dir` in `local.properties`). Gradle 8.14.5 comes from the wrapper.

## Toolchain

| | |
|---|---|
| Gradle / AGP / Kotlin | 8.14.5 / 8.13.2 / 2.3.21 |
| Compose BOM | 2025.10.01 (Material 3) |
| SDK | minSdk 26 · targetSdk 30 · compileSdk 36 |
| Storage | Room 2.8.5 + DataStore Preferences 1.1.7 |

`targetSdk 30` is deliberate: it keeps the app on Android 11-era runtime behaviour (no notification
runtime permission, no Android 12 foreground-service restrictions).

## Privacy

No `INTERNET` permission. No accounts, no analytics, no crash reporting. The blocklist, the event
log, and the passphrase hash never leave the device (`android:allowBackup="false"`).
