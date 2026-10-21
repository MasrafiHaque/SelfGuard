# SelfGuard — Phased Build Plan

**Companions:** [PRD.md](./PRD.md) · [FRD.md](./FRD.md) · [ARCHITECTURE.md](./ARCHITECTURE.md) · [DEVICE_OWNER.md](./DEVICE_OWNER.md)

**Legend:** `[ ]` open · `[x]` done · ⏱ rough effort for one developer with AI assistance.

---

## Milestone overview

| Phase | Theme | Produces | ⏱ | Depends on |
|---|---|---|---|---|
| 0 | Skeleton & tooling | Building project, empty shell app | ✅ done | — |
| 1 | Foundation | Data layer, state, theme, navigation, tests | 1 day | 0 |
| 2 | Onboarding & permissions | A guided setup that ends in a working, armed app | 1 day | 1 |
| 3 | Website blocking | Blocked sites never open | 1.5 days | 1, 2 |
| 4 | App blocking | Blocked apps never open | 1.5 days | 1, 2 |
| 5 | Blocklist management | Editable apps/sites/keywords + import/export | 1.5 days | 1, 3, 4 |
| 6 | Protection hardening | Hard to disable, safe way out | 2 days | 1, 2, 4 |
| 7 | Statistics & streaks | Visible progress | 1 day | 1, 5 |
| 8 | Panic button | A way to ride out the urge | 1 day | 1, 7 |
| 9 | Hardening & release | Battery, Samsung quirks, signed APK | 1 day | 3–8 |
| 10 | Acceptance & docs | Verified on the Galaxy A20 | 1 day | 9 |

Total ≈ 12–13 days of focused work.

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

## Phase 1 — Foundation

**Goal:** the app has real data, real state, and a navigable UI shell.

**Deliverables:** Room schema v1 + DAOs, DataStore settings store, repositories, `AppContainer`,
theme, navigation graph, domain models, pure matching engine skeleton, JVM test setup.

**Checklist**
- [ ] `AppContainer` created in `SelfGuardApp`, exposing repositories to the UI and the services
- [ ] Room: `SelfGuardDatabase` + `BlockedAppEntity`, `BlockedSiteEntity`, `BlockedKeywordEntity`,
      `EventEntity`, `DailyStatEntity` + DAOs; `exportSchema = true`, schemas committed
- [ ] Room migrations folder ready (migration test harness in place)
- [ ] `SettingsStore` (DataStore Preferences) with every key from ARCHITECTURE §5.2
- [ ] `BlocklistRepository`, `EventRepository`, `StatsRepository`, `ProtectionRepository`
- [ ] `RuleSnapshot` published as an immutable `StateFlow`; rebuilt on every list change (≤ 1 s)
- [ ] `Clock` abstraction (timezone-aware `LocalDate` handling) + `AppDispatchers`
- [ ] `HostNormalizer`, `PublicSuffix` (asset + fallback list), `RuleMatcher` — pure Kotlin
- [ ] Material 3 theme (light/dark), colour scheme, typography
- [ ] Compose navigation: Dashboard / Blocklists / Statistics / Settings (placeholder content)
- [ ] Repositories exposed to the UI through `ViewModel`s with `collectAsStateWithLifecycle`
- [ ] `Logging` with redaction of passphrase-like values
- [ ] Unit tests: normaliser, public suffix, matcher, streak calculator (placeholder), protection state machine (placeholder)
- [ ] `./gradlew :app:test` runs green

**Verification:** `./gradlew :app:test :app:assembleDebug`; the app opens, navigates between four
tabs, and survives a process death with settings intact.

**Exit criteria:** the data layer is complete and covered by JVM tests; no Android logic in `domain/`.

---

## Phase 2 — Onboarding and permission orchestration

**Goal:** a first run that ends with the service enabled, usage access granted, and the owner
understanding what protection level is active.

**Checklist**
- [ ] `PermissionState` model: accessibility, usage access, device admin, device owner, notifications
- [ ] Live polling of permission state → dashboard indicators (FR-0.4)
- [ ] Onboarding wizard: 5 steps (accessibility → usage access → device owner → passphrase → lists)
- [ ] Deep links to the correct Settings pages (`Settings.ACTION_ACCESSIBILITY_SETTINGS`,
      `ACTION_USAGE_ACCESS_SETTINGS`, `ACTION_MANAGE_DEVICE_ADMIN_ENROLL`, `ACTION_APPLICATION_DETAILS_SETTINGS`)
- [ ] ADB instructions for Device Owner with a copy-to-clipboard button (mirrors DEVICE_OWNER.md)
- [ ] `SettingsShield` stays **relaxed** until the accessibility service is confirmed running (FR-5.4)
- [ ] Verification of the accessibility service via `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`
      **and** the service's own heartbeat
- [ ] `DevicePolicyGateway` created: `isDeviceOwnerApp`, `isAdminActive`, `lockNow`, `clearDeviceOwnerApp`
- [ ] First-run "why" message prompt, stored in `why_message`
- [ ] All onboarding steps are skippable except the Accessibility service

**Verification:** fresh install → follow the wizard → the dashboard shows every permission green;
the accessibility service is running; usage access is granted.

**Exit criteria:** a non-technical owner can reach an armed state with only the on-screen
instructions and one copy-pasted ADB command.

---

## Phase 3 — Website blocking engine

**Goal:** a blocked URL never opens.

**Checklist**
- [ ] `BrowserProfiles`: per-package address-bar resource-id candidates (Chrome, Samsung Internet,
      Firefox, AOSP Browser) — **confirmed on the device**, not guessed
- [ ] `BrowserProbe`: extract candidate URL/title text from the active window
      (resource-id match → EditText fallback → title fallback)
- [ ] Diagnostic screen dumping the last-seen node tree (resource IDs, classes, text) to
      speed up browser breakage (Risk R3)
- [ ] `HostNormalizer` wired into the probe (strip scheme/www/port/path, IDN → ASCII)
- [ ] Public-suffix-aware host matching, exact + subdomain + wildcard patterns
- [ ] Keyword matching (substring; optional regex and page-text matching per rule)
- [ ] `MatchEngine` orchestration → `MatchResult`
- [ ] `BlockController`: `GLOBAL_ACTION_HOME` → launch `BlockedActivity` (singleTask, noHistory,
      excludeFromRecents, showWhenLocked)
- [ ] `BlockedActivity` UI: matched rule, target host, current streak, cooldown state, panic button
- [ ] Back button on the block screen returns Home instead of closing it (FR-1.10)
- [ ] Re-entrancy guard + 750 ms minimum interval between block actions
- [ ] `BLOCKED_SITE` event written with host, URL, browser package, rule id
- [ ] Chrome + Samsung Internet verified on the Galaxy A20 (typed URL, pasted URL, link tap,
      history-typed URL, redirect to a blocked host)
- [ ] Incognito / custom-tab behaviour documented (known limitation or handled)

**Verification:** the FR-1 acceptance cases plus the Phase 10 matrix rows for websites.

**Exit criteria:** every URL in the acceptance matrix is blocked in under 500 ms, with an event row.

---

## Phase 4 — App blocking engine

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

## Phase 5 — Blocklist management

**Goal:** the lists are comfortable to edit, including from a PC.

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

## Phase 6 — Protection hardening

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

## Phase 7 — Statistics and streaks

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

## Phase 8 — Panic button

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

## Phase 9 — Hardening, battery, and release build

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

## Phase 10 — Acceptance test matrix on the Galaxy A20

**Goal:** a written, executed test matrix; the only definition of "done".

### Website blocking
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
