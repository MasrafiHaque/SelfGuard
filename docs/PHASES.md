# SelfGuard — Phased Build Plan

**Companions:** [PRD.md](./PRD.md) · [FRD.md](./FRD.md) · [ARCHITECTURE.md](./ARCHITECTURE.md) · [DEVICE_OWNER.md](./DEVICE_OWNER.md)

**Legend:** `[ ]` open · `[x]` done · ⏱ rough effort for one developer with AI assistance.

---

## Milestone overview

| Phase | Theme | Produces | ⏱ | Depends on |
|---|---|---|---|---|
| 0 | Skeleton & tooling | Building project, empty shell app | ✅ done | — |
| 1 | Blocking foundation | Data layer, matching engine, onboarding, website blocking, editable lists | ✅ done | 0 |
| 2 | App blocking | Blocked apps never open | 1.5 days | 1 |
| 3 | Blocklist management | Apps tab, import/export, categories | 1.5 days | 1, 2 |
| 4 | Protection hardening | Hard to disable, safe way out | 2 days | 1, 2 |
| 5 | Statistics & streaks | Visible progress | 1 day | 1, 3 |
| 6 | Panic button | A way to ride out the urge | 1 day | 1, 5 |
| 7 | Hardening & release | Battery, Samsung quirks, signed APK | 1 day | 2–6 |
| 8 | Acceptance & docs | Verified on the Galaxy A20 | 1 day | 7 |

Total ≈ 10–11 days of focused work.

Phase 1 was originally planned as three phases (foundation / onboarding / website blocking).
They were delivered together because the blocking engine needs all three to be demonstrable.

---

## Phase 0 — Skeleton and tooling ✅

**Goal:** a building project that encodes the platform decisions, with no feature code.

**Deliverables**
- Gradle 8.14.5 / AGP 8.13.2 / Kotlin 2.3.21 / KSP 2.3.12, version catalog in `gradle/libs.versions.toml`.
- `:app` module: `minSdk 26`, `targetSdk 30`, `compileSdk 36`, Compose BOM 2025.10.01.
- `AndroidManifest.xml` with the accessibility service, device admin receiver, boot receiver and the
  block activity declared.
- `res/xml/accessibility_service_config.xml`, `res/xml/device_admin.xml` (no `wipe-data`).
- Placeholder components: `SelfGuardApp`, `MainActivity`, `BlockedActivity`,
  `SelfGuardAccessibilityService`, `SelfGuardDeviceAdminReceiver`, `BootReceiver`.
- Documentation set: PRD, FRD, ARCHITECTURE, DEVICE_OWNER, PHASES.

**Checklist**
- [x] Gradle wrapper generated and committed
- [x] Version catalog with pinned, AGP-8.13-compatible dependency versions
- [x] `local.properties` created and git-ignored
- [x] Manifest declares `RECEIVE_BOOT_COMPLETED`, `PACKAGE_USAGE_STATS`, `QUERY_ALL_PACKAGES`
- [x] No `INTERNET` permission anywhere
- [x] `android:allowBackup="false"`
- [x] Device-admin XML contains only `<force-lock/>` and `<lock-now/>`
- [x] Debug and release share the `applicationId` (Device Owner command stability)
- [x] `./gradlew :app:assembleDebug` succeeds → `app-debug.apk` (minSdk 26 / targetSdk 30 verified with `aapt2`)
- [x] Adaptive launcher icon + notification icon resources
- [ ] Replace the placeholder components with real implementations (Phase 1+)

**Verification:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL;
`aapt2 dump badging app-debug.apk` shows `minSdkVersion:'26'`, `targetSdkVersion:'30'`.

**Exit criteria:** a clean checkout builds a debug APK on a machine with no Android Studio.

---

## Phase 1 — Blocking foundation (data, onboarding, website blocking) ✅

**Goal:** the phone blocks adult websites and keywords in the browser, and a first run ends with
the protection service actually enabled.

**Deliverables:** Room schema v1 + DAOs, DataStore settings, `BlocklistRepository`, `AppContainer`,
Material 3 theme, pure matching engine (`HostNormalizer` / `PublicSuffix` / `RuleMatcher`),
`BrowserProfiles` + `BrowserProbe`, the accessibility blocking service, `BlockedActivity`,
`DashboardScreen`, `BlocklistsScreen` (Websites / Keywords), `OnboardingScreen`, the bundled
starter list, and the JVM test suite.

**Checklist**
- [x] `AppContainer` created in `SelfGuardApp`: database, settings store, matcher, repository
- [x] Room v1: `blocked_sites` + `blocked_keywords` + DAOs; `exportSchema = true`, schema JSON committed
- [x] `SettingsStore` (DataStore): `blocking_enabled`, `match_keywords_in_page_text`,
      `onboarding_complete`, `defaults_seeded`
- [x] `BlocklistRepository`: seed from assets, add / delete / enable, transactional restore of defaults
- [x] `HostNormalizer`, `PublicSuffix` (built-in list + `assets/public_suffixes.txt`), `RuleMatcher`
      — pure Kotlin, no Android imports under `domain/`
- [x] Public-suffix rules rejected at entry (blocking `it.com` or `co.uk` is refused)
- [x] Material 3 theme (light/dark)
- [x] Onboarding: opens the Accessibility settings page, live service-status check;
      "Start using SelfGuard" stays disabled until the service is confirmed running
- [x] Dashboard: protection status, rule counts, master switch, page-text toggle, restore defaults
- [x] Blocklists screen: Websites / Keywords tabs, add with validation, delete, subdomain note
- [x] `BrowserProfiles`: address-bar resource-id candidates for Chrome, Samsung Internet,
      Firefox and the AOSP browser
- [x] `BrowserProbe`: bounded tree walk (node / depth / text caps, no polling), address-bar first,
      top-of-screen fallback
- [x] `SelfGuardAccessibilityService`: event-driven only; 1200 ms content-changed throttle,
      750 ms block debounce; rules reloaded from Room on every change
- [x] Block action: launch `BlockedActivity` first, confirm the top window, fall back to
      `GLOBAL_ACTION_HOME` if the device refuses the background start
- [x] `BlockedActivity`: matched rule text, `FLAG_SECURE`, show-when-locked, back goes Home
- [x] Starter list in `assets/default_blocklist.txt` (editable; section-aware parser; 67 domains,
      39 keywords)
- [x] Unit tests: normaliser (3), public suffix (2), matcher (7), asset parser (4) — 16 green
- [x] `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` green

**Deferred to later phases**
- Blocked-site event rows and statistics → Phase 5
- Apps tab, import / export, categories and notes → Phase 3
- Diagnostic node-tree screen for browser breakage → Phase 3
- Samsung Internet and Firefox verification on the Galaxy A20 → Phase 8

**Verification:** build + lint + unit tests green, and a device walkthrough on Android 14 during
development: onboarding blocks until the service is on → seeded list (67 / 39) → keyword rule
redirects Chrome and shows the block screen → domain rule added through the UI redirects Chrome →
unblocked host still loads → block-screen home button works → restore defaults drops the user rule
and the host loads again.

**Device finding (kept for later phases):** on Android 14 an activity started from the background
into a task marked `excludeFromRecents` + `noHistory` is destroyed about 250 ms after creation
(`finish-imm:recent-task-trimmed`). SelfGuard therefore launches the block screen **first** and
only falls back to Home when the block screen did not take over, and the manifest no longer uses
those two flags on `BlockedActivity`.

**Exit criteria:** blocked hosts and keywords never stay open in Chrome; the list is editable in
the app and the bundled asset; the service survives a process restart (re-armed by the system).

---

## Phase 2 — App blocking engine

**Goal:** a blocked app never opens, and the phone stays fully usable.

**Checklist**
- [ ] `ForegroundDetector`: Accessibility `TYPE_WINDOW_STATE_CHANGED` path
- [ ] UsageStats backup poll (1500 ms, ≤ 10 s event window, screen-on + unlocked only)
- [ ] Cross-check of the two sources to suppress dialog/IME false positives
- [ ] Hard allowlist: dynamic launcher, dynamic IME, telecom/dialer, static critical list (FR-2.5)
- [ ] Critical-package rule rejection surfaced as a `RULE_SKIPPED_CRITICAL` event + dashboard warning
- [ ] `ScreenStateReceiver`: `ACTION_SCREEN_ON/OFF`, `ACTION_USER_PRESENT`; poll stops when the
      screen is off or the keyguard is engaged (FR-2.3)
- [ ] `PowerManager.isInteractive` check before every tick
- [ ] Block action → Home + `BlockedActivity` with the app name and icon
- [ ] `BLOCKED_APP` event written
- [ ] Device Owner: `setPackagesSuspended` for blocked apps (when enabled per rule)
- [ ] Device Owner: optional `setApplicationHidden` (API 30) behind a per-rule toggle
- [ ] Unsuspend/undelete path verified: disabling a rule restores the app
- [ ] SelfGuard never suspends or hides itself; never disables USB debugging (FR-2.11)
- [ ] Verified on device: Phone, Clock, Calculator, Settings, Play Store, SystemUI, and the IME
      all remain fully usable with a maximal blocklist

**Verification:** the FR-2 acceptance cases; the "never lock yourself out" test with every system
package added to the blocklist by mistake.

**Exit criteria:** blocked apps do not open; the dialer, clock, and IME still work even if
somebody blocklists them by mistake.

---

## Phase 3 — Blocklist management

**Goal:** the lists are comfortable to edit, including from a PC.

**Already in Phase 1:** the Websites and Keywords tabs with add / delete / enable and validation.
Remaining here: the Apps tab, import / export, and rule notes.

**Checklist**
- [ ] Blocklists screen with three tabs (Apps / Websites / Keywords)
- [ ] Apps tab: list of installed packages with labels and icons (via `QUERY_ALL_PACKAGES`),
      search, one-tap block, and a "system apps hidden" filter
- [ ] Websites tab: host validation, normalised preview, subdomain toggle, wildcard support
- [ ] Keywords tab: over-match warning for very short rules, regex toggle, page-text toggle
- [ ] Add / edit / enable / disable / delete (with a confirmation showing the fire count)
- [ ] Categories and notes on every rule
- [ ] Export → JSON and plain text, into a user-chosen location (SAF `ACTION_CREATE_DOCUMENT`)
- [ ] Import → file picker, preview, validation report (accepted / skipped / invalid),
      merge or replace choice
- [ ] Starter pack (common adult domains + keywords), one tap, fully editable afterwards
- [ ] Rule list refreshes the engine snapshot within 1 s
- [ ] Empty states and first-run guidance

**Verification:** export → reinstall → import reproduces an identical rule set; the ruleset
snapshot updates immediately after an edit (no app restart).

**Exit criteria:** all three lists are fully editable, import/export round-trips exactly, and the
engine picks up changes instantly.

---

## Phase 4 — Protection hardening

**Goal:** disabling SelfGuard becomes slow, loud and logged — while a safe exit always exists.

**Checklist**
- [ ] `ProtectionStateMachine` (pure): UNINITIALISED → ARMED → TAMPERED → RELEASE_PENDING → RELEASED
- [ ] `ProtectionRepository` persists the state in DataStore and exposes it as a `StateFlow`
- [ ] `BreachDetector` classifies tamper events: accessibility disable attempt, usage-access
      revocation, uninstall attempt, admin deactivation, protection-settings change
- [ ] Settings shield: protected-screen detection (activity class + resource IDs + visible text)
- [ ] Shield covers: Accessibility, Installed apps, App info, Force stop, Uninstall, Usage access,
      Battery optimisation, Device admin apps, Notification access, Permissions, Install unknown apps
- [ ] Non-sensitive Settings screens stay fully usable (explicit regression test)
- [ ] Shield re-arms automatically once the accessibility service is connected
- [ ] Tamper response pipeline, in order: log → `lockNow()` (DO/DA only) → high-priority
      notification → alarm → cooldown start
- [ ] `AlarmPlayer`: ≤ 30 s, auto-stop, dismissible, no wakelock held afterwards, plays once per event
- [ ] Cooldown: 48 h default, configurable 24–72 h; changing the value during a cooldown requires
      the passphrase; the countdown uses `setChronometerCountDown` (no timer work)
- [ ] Ongoing cooldown notification with the remaining time and the editable "why" message
- [ ] Blocking stays fully active during a cooldown (verified by test)
- [ ] Passphrase: PBKDF2-HMAC-SHA256, 200k iterations, 16-byte salt, constant-time compare
- [ ] Passphrase rate limiting: 5 failures → 15-minute lockout, persisted
- [ ] Passphrase can skip a cooldown, unlock protected settings, and authorise release
- [ ] Release flow: `clearDeviceOwnerApp()` + admin deactivation, with cooldown/hold enforcement
- [ ] Release logged with the reason and whether the passphrase was used
- [ ] Duress passphrase (optional): appears to succeed, records a tamper, starts a cooldown
- [ ] Admin receiver: `onEnabled` / `onDisabled` logged; `onDisabled` triggers the tamper pipeline
- [ ] ADB release path re-tested end-to-end (DEVICE_OWNER §7.2)
- [ ] Confirmed: no `wipe-data` policy, no `wipeData*` call anywhere (grep + review)

**Verification:** the FR-4 acceptance cases; attempt to disable the service through every known
path; confirm each attempt is logged, alarms once, and starts a cooldown; confirm release works
both ways (cooldown elapsed and passphrase).

**Exit criteria:** no path to disable protection is shorter than a cooldown, every attempt is
logged and audible, and the owner can still exit safely with either method.

---

## Phase 5 — Statistics and streaks

**Goal:** visible progress that rewards good days.

**Checklist**
- [ ] `EventRepository` writes every event type; `EventType` sealed set complete
- [ ] `StreakCalculator` (pure): clean-day definition, current streak, best streak, pending today
- [ ] `DailyRollupCalculator` (pure) + cached `daily_stats` rows written by a WorkManager job
- [ ] Statistics screen: today / 7 days / 30 days / all time, split by category
- [ ] 30-day bar chart drawn with Compose Canvas, tamper days marked
- [ ] Top blocked hosts and top blocked apps
- [ ] Tamper log list (timestamp, kind, outcome)
- [ ] "Days protected" and "total blocked" counters
- [ ] Broken streak shown with the date it ended (FR-6.10)
- [ ] Event export to JSON and CSV
- [ ] "Delete all statistics" screen, passphrase-gated, and itself logged
- [ ] Dashboard shows the current streak, today’s counts, and the last panic session

**Verification:** a scripted scenario (block 5 times, 1 tamper, 1 panic) produces the expected
counts, streak, and chart on the device.

**Exit criteria:** the numbers on screen always match the event log, verified by a manual
cross-check of 3 days of simulated activity.

---

## Phase 6 — Panic button

**Goal:** a private, offline way to ride out the urge.

**Checklist**
- [ ] Panic button on the dashboard and on the block screen
- [ ] `BreathingController`: BOX 4-4-4-4, 4-7-8, CALM 5-5, custom pattern; 1–10 minutes
- [ ] Animated circle (Compose `animateFloatAsState` on a Canvas), phase label, countdown
- [ ] Optional haptics per phase change
- [ ] "Stop" action → recorded as `PANIC_ABANDONED` (a bypass event, breaks the streak)
- [ ] Completing → `PANIC_RESISTED` (not a streak break)
- [ ] Cool-down window after a session (default 15 min): blocks stay enforced even if the owner
      tries the blocked item (enforced by the breach pause, not by the rule lists)
- [ ] Reminder text + a slot for the accountability partner's contact details
- [ ] Session summary on the dashboard (last 7 days)
- [ ] Reachability from the block screen (the most common moment of need)

**Verification:** run a full session, abandon one, confirm the streak behaves as specified
(FR-7 acceptance cases).

**Exit criteria:** the panic flow works end to end, is logged correctly, and applies the
cool-down window.

---

## Phase 7 — Hardening, battery, and release build

**Goal:** the app is quiet, stable, and installable as a signed APK.

**Checklist**
- [ ] Battery: no wakelock in the normal path; polling provably stops when the screen is off
- [ ] 24 h battery measurement on the Galaxy A20 (idle + normal use) → < 1 %/day extra drain
- [ ] CPU measurement while the screen is on → < 3 % on the Exynos 850
- [ ] Samsung specifics: "Sleeping apps" auto-add guidance, battery-optimisation exemption
      request, One UI 3 navigation quirks
- [ ] `ServiceWatchdog`: detect a killed Accessibility service and warn on the dashboard
- [ ] `FLAG_SECURE` on the block screen and the blocklist screens
- [ ] Release signing: local keystore outside the repository, `keystore.properties` git-ignored
- [ ] `assembleRelease` produces an installable APK; sideload tested over a reinstall
- [ ] Upgrade path tested: install over the previous version without losing the blocklist
- [ ] `allowBackup=false` verified: `adb backup` produces nothing useful
- [ ] Logcat noise check: no spam, no secrets logged
- [ ] 30-minute soak test with continuous app switching — no ANR, no crash
- [ ] Optional stretch: SQLCipher-encrypted database, quick-settings tile, home-screen widget

**Verification:** the measurements above plus a fresh-install run of the whole onboarding.

**Exit criteria:** battery and stability targets met; a signed release APK installs and runs.

---

## Phase 8 — Acceptance test matrix on the Galaxy A20

**Goal:** a written, executed test matrix; the only definition of "done".

### Website blocking

*Already exercised on an Android 14 development device (Chrome): keyword rule in the URL bar,
domain rule added through the UI, allowed host not blocked, public-suffix rejection at entry,
block screen shown and its Home button working. Every row below must still be re-run on the
Galaxy A20 (Android 11 / One UI 3).*

- [ ] Typed blocked URL in Chrome → blocked, redirected, logged
- [ ] Pasted blocked URL in Chrome → blocked
- [ ] Tapped a link to a blocked host → blocked
- [ ] `www.` variant, subdomain variant, uppercase variant, URL with a path and query → blocked
- [ ] Blocked host reached via a redirect → blocked
- [ ] Allowed host → not blocked, no false positive
- [ ] Keyword rule matching a URL path → blocked
- [ ] Keyword rule matching a page title → blocked
- [ ] Rule matching a public suffix (`it.com`) is rejected at entry
- [ ] Samsung Internet repeats the Chrome cases
- [ ] Firefox repeats the Chrome cases

### App blocking
- [ ] Blocklisted app → does not open, block screen shown, logged
- [ ] Blocklisted app hidden from the launcher (Device Owner)
- [ ] Blocklisted app suspended → cannot be launched even from Settings
- [ ] Un-suspending via the rule toggle restores the app
- [ ] Dialer, clock, calculator, IME, launcher, SystemUI, Play Store → never blocked
- [ ] A system package added to the blocklist by mistake → refused, warning shown

### Protection durability
- [ ] Settings → Accessibility → redirected to Home + block screen
- [ ] Settings → App info (SelfGuard) → treated as an uninstall attempt
- [ ] Settings → Force stop / Uninstall → redirected
- [ ] Usage access revoked → tamper pipeline fires
- [ ] Every tamper attempt: screen locks, alarm plays ≤ 30 s, notification appears, cooldown starts
- [ ] Cooldown blocks Release and protected settings; the passphrase unblocks them
- [ ] Cooldown expiry enables Release; `adb shell dpm list-owners` shows no owner afterwards
- [ ] Release with the passphrase works immediately
- [ ] ADB release (`dpm clear-device-owner`) works even with the app force-stopped
- [ ] The app cannot be uninstalled while it is Device Owner (system refuses)
- [ ] No data wipe anywhere: `adb shell dpm list-active-admins` shows only `lock-now`/`force-lock`

### Statistics, panic, usability
- [ ] Blocks, tampers, and panic sessions all appear in the stats
- [ ] Streak increments on clean days and breaks on a bypass
- [ ] Panic session completes and is logged; abandoning breaks the streak
- [ ] Cool-down window keeps blocks enforced
- [ ] The app survives a reboot and re-arms protection
- [ ] The app survives an app update (reinstall over the top)
- [ ] Screen off for 8 hours → no measurable battery drain, no polling in `dumpsys`

**Exit criteria:** every row above is executed and recorded (pass/fail + note). Any failing row
either gets fixed or is moved into the known-limitations list in ARCHITECTURE §16 with an explicit
statement of the limitation.

---

## Cross-phase rules

1. **No destructive capability is ever introduced.** Any PR that adds a `wipe-*` policy or a
   destructive DPM call is rejected on sight (FR-4.1, FR-4.2).
2. **The domain layer stays pure.** No Android imports under `domain/`.
3. **Every new rule is covered by a JVM test** before the UI work.
4. **Every new screen states what it will do** before asking for a permission.
5. **The release flow is re-tested after every change to the protection layer** (it is the safety
   net; it must never regress).
