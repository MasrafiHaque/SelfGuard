# SelfGuard — Phased Build Plan

**Companions:** [PRD.md](./PRD.md) · [FRD.md](./FRD.md) · [ARCHITECTURE.md](./ARCHITECTURE.md) · [DEVICE_OWNER.md](./DEVICE_OWNER.md)

**Legend:** `[ ]` open · `[x]` done · ⏱ rough effort for one developer with AI assistance.

---

## Milestone overview

| Phase | Theme | Produces | ⏱ | Depends on |
|---|---|---|---|---|
| 0 | Skeleton & tooling | Building project, empty shell app | ✅ done | — |
| 1 | Blocking foundation | Data layer, matching engine, onboarding, website blocking, editable lists | ✅ done | 0 |
| 2 | App blocking | Blocked apps never open, phone stays usable | ✅ done | 1 |
| 3 | Self-protection | Settings shield, 24 h release cooldown, passphrase, auto-restart | ✅ done | 1, 2 |
| 4 | Blocklist management | Import/export, categories, richer editing | 1.5 days | 1, 2 |
| 5 | Protection hardening | Tamper pipeline, alarm, duress, DO suspend/hide | 1.5 days | 3 |
| 6 | Statistics & streaks | Visible progress | 1 day | 1, 4 |
| 7 | Panic button | A way to ride out the urge | 1 day | 1, 6 |
| 8 | Hardening & release | Battery, Samsung quirks, signed APK | 1 day | 2–7 |
| 9 | Acceptance & docs | Verified on the Galaxy A20 | 1 day | 8 |

Total ≈ 10–11 days of focused work.

Phase 1 was originally planned as three phases (foundation / onboarding / website blocking).
They were delivered together because the blocking engine needs all three to be demonstrable.
Phase 3 was originally "protection hardening"; its first half (self-protection) shipped early as
its own phase, and the remaining tamper pipeline stays as Phase 5.

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
- Blocked-site event rows and statistics → Phase 6
- Apps tab, import / export, categories and notes → Phases 2 / 4
- Diagnostic node-tree screen for browser breakage → Phase 4
- Samsung Internet and Firefox verification on the Galaxy A20 → Phase 9

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

## Phase 2 — App blocking engine ✅

**Goal:** a blocked app never opens, and the phone stays fully usable.

**Delivered**
- `SelfGuardAccessibilityService` `TYPE_WINDOW_STATE_CHANGED` path (`checkAppBlock`) detects the
  foreground package and blocks it with a 750 ms debounce.
- Three independent protection layers so the phone can never be locked out (FR-2.5 / FR-2.6):
  1. **Add-time** — `BlocklistRepository.addApp` rejects protected packages via
     `CriticalApps.protectReason` and surfaces the reason to the user.
  2. **Block-time** — `checkAppBlock` re-checks `protectReason` before acting, so even a
     hand-edited database cannot lock the owner out.
  3. **Picker** — protected packages are filtered out of the Apps-tab installed list.
- Hard allowlist resolved at runtime: **every** `CATEGORY_HOME` activity (`launcherPackages()`),
  the default IME (`DEFAULT_INPUT_METHOD`), SelfGuard itself, and the static critical list
  (SystemUI, dialer/telecom, clock, installer, Play Store, settings, and more).
- Block action → `BlockedActivity` (app name + icon) with a Home button; `BLOCKED_APP` event
  written to the log.
- Bottom-navigation shell (Home / Blocklists / Settings) landed with this phase; the Apps tab of
  Blocklists was built here too.

**Bug found and fixed during on-device testing**
On a vivo device with several `CATEGORY_HOME` activities, resolving only the *preferred* launcher
let `com.vivo.simplelauncher` through the add-time guard. `launcherPackages()` now collects **all**
HOME resolvers, and `CriticalApps.protectReason` takes a `Set` of launcher packages instead of a
single one. Covered by the `every launcher package is protected` unit test.

**Verification (Android 14, vivo):** blocking Drive end-to-end (block → launch → `BlockedActivity`
→ Home); launcher removed from the list; launchers filtered out of the picker; add-time and
block-time rejection of critical packages; unit tests + `lintDebug` green.

---

## Phase 3 — Self-protection ✅

**Goal:** disabling SelfGuard becomes slow and deliberate — while a safe exit always exists.

**Delivered**
- **Settings shield** (pure `domain/protection/SettingsShield`): on `TYPE_WINDOW_STATE_CHANGED`,
  while `protection.armed`, protected screens are classified from the event class name (AOSP
  `AccessibilitySettingsActivity`, `DeviceAdminSettingsActivity`, `InstalledAppDetailsActivity`)
  with a `SubSettings` exact-title fallback for OEMs. Covered: Accessibility, Device admin, and the
  app-info page of SelfGuard itself (title match, so other apps' info pages stay usable).
  Matches fire as `RuleKind.SHIELD` → `BlockedActivity`.
- **Arm / release flow** (`ProtectionRepository` + Settings → Protection card): arming turns the
  shield on and starts the watchdog. Release is never instant — either a fixed **24-hour cooldown**
  started at the release request, or the **partner passphrase** skips it entirely. The shield stays
  armed for the whole cooldown.
- **Passphrase** (pure `PassphraseHasher`): PBKDF2-HMAC-SHA256, 200 000 iterations, 16-byte
  random salt, constant-time compare; only salt + hash are stored. Rate limiting (pure
  `PassphrasePolicy`): 5 failed attempts → 15-minute lockout, persisted.
- **Uninstall blocking**: `DeviceAdminGateway.setUninstallBlocked` is toggled with arm/release —
  but it requires the **Device Owner** role; a plain Device Admin is refused by the system, so the
  Settings card shows the `adb shell dpm set-device-owner …` command (copyable) and the honest
  "Device Owner: No" state. No wipe policy, no destructive DPM call anywhere.
- **Auto-restart**: `ProtectionWatchdogService` is a `START_STICKY` foreground service with a
  low-importance notification, started by `BootReceiver` on `BOOT_COMPLETED` / `QUICKBOOT_POWERON`
  / `MY_PACKAGE_REPLACED` (only while armed) and by `arm()`. A `ContentObserver` on
  `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` reacts to the service being switched off and
  requests `AccessibilityManager.enableSelf()` (greylist reflection — the API is `@hide`; best
  effort on OEM skins, failures logged, never fatal).
- **Device admin receiver**: `onEnabled` / `onDisabled` logged (`onDisabled` marked as tamper for
  the Phase 6 event log).

**Verification**
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` green: 48 unit tests total,
  27 new here (`SettingsShieldTest` 11, `PassphraseHasherTest` 9, `ReleaseGateTest` 5 + policy);
  no new lint issues.
- On-device matrix (arm → shield blocks Accessibility / Device admin / SelfGuard app-info,
  non-sensitive settings stay usable, watchdog re-enables the service after
  `settings put secure enabled_accessibility_services null`, cooldown + passphrase release,
  `dpm set-device-owner` attempt) is pending reconnection of the Android 14 development device;
  rows are tracked in Phase 9's Protection-durability section.

**Known limits (documented, not hidden)**
- Shield detection is best-effort: OEMs rename settings activities; `SubSettings` uses an exact
  visible-heading match only.
- `enableSelf()` is hidden API (reflection): reliable on stock Android, may be ignored by
  aggressive OEM skins — ADB recovery always exists (DEVICE_OWNER §6).
- Uninstall blocking, suspend/hide, alarm, duress and the full tamper pipeline need the Device
  Owner groundwork → Phase 5.

---

## Phase 4 — Blocklist management

**Goal:** the lists are comfortable to edit, including from a PC.

**Already in Phase 1:** the Websites and Keywords tabs with add / delete / enable and validation.
**Already in Phase 2:** the Blocklists screen with three tabs (Apps / Websites / Keywords) and the
Apps tab — installed packages with labels and icons, search, one-tap block, "system apps" filter,
and a protected-packages filter so launcher / IME / critical apps are never offered.
Remaining here: import / export, rule notes, and the richer Websites/Keywords editing.

**Checklist**
- [x] Blocklists screen with three tabs (Apps / Websites / Keywords)
- [x] Apps tab: list of installed packages with labels and icons (via `QUERY_ALL_PACKAGES`),
      search, one-tap block, and a "system apps hidden" filter
- [x] Websites tab: host validation, normalised preview, subdomain toggle, wildcard support
- [x] Keywords tab: over-match warning for very short rules, regex toggle, page-text toggle
- [ ] Add / edit / enable / disable / delete (with a confirmation showing the fire count)
- [ ] Categories and notes on every rule
- [ ] Export → JSON and plain text, into a user-chosen location (SAF `ACTION_CREATE_DOCUMENT`)
- [ ] Import → file picker, preview, validation report (accepted / skipped / invalid),
      merge or replace choice
- [ ] Starter pack (common adult domains + keywords), one tap, fully editable afterwards
- [x] Rule list refreshes the engine snapshot within 1 s
- [ ] Empty states and first-run guidance

**Verification:** export → reinstall → import reproduces an identical rule set; the ruleset
snapshot updates immediately after an edit (no app restart).

**Exit criteria:** all three lists are fully editable, import/export round-trips exactly, and the
engine picks up changes instantly.

---

## Phase 5 — Protection hardening (remainder)

**Goal:** every tamper attempt is loud, logged and survivable — while the safe exit keeps working.

> **Deferred from Phase 2:** Device Owner app-suspension (`setPackagesSuspended`) and the optional
> `setApplicationHidden` (API 30) per-rule toggle, plus the suspend/undelete restore path and the
> `RULE_SKIPPED_CRITICAL` dashboard warning. These need the Device Owner groundwork built in
> Phase 3, so they land here rather than in Phase 2.

**Checklist**
- [ ] `ProtectionStateMachine` (pure): UNINITIALISED → ARMED → TAMPERED → RELEASE_PENDING → RELEASED
- [ ] `BreachDetector` classifies tamper events: accessibility disable attempt, usage-access
      revocation, uninstall attempt, admin deactivation, protection-settings change
- [ ] Shield extension: Usage access, Battery optimisation, Notification access, Permissions,
      Install unknown apps (Accessibility / Device admin / SelfGuard app-info are done)
- [ ] Tamper response pipeline, in order: log → `lockNow()` (DO/DA only) → high-priority
      notification → alarm → cooldown start
- [ ] `AlarmPlayer`: ≤ 30 s, auto-stop, dismissible, no wakelock held afterwards, plays once per event
- [ ] Cooldown configurable 24–72 h (fixed 24 h shipped in Phase 3); changing the value during a
      cooldown requires the passphrase; the countdown uses `setChronometerCountDown` (no timer work)
- [ ] Ongoing cooldown notification with the remaining time and the editable "why" message
- [ ] Blocking stays fully active during a cooldown (verified by test)
- [ ] Passphrase can unlock protected settings (skipping the cooldown is done)
- [ ] Duress passphrase (optional): appears to succeed, records a tamper, starts a cooldown
- [ ] Release logged with the reason and whether the passphrase was used (logcat now, event
      table in Phase 6)
- [ ] Device Owner suspend/hide per rule + restore path + `RULE_SKIPPED_CRITICAL` warning
- [ ] ADB release path re-tested end-to-end (DEVICE_OWNER §7.2)
- [ ] Confirmed: no `wipe-data` policy, no `wipeData*` call anywhere (grep + review)

**Verification:** the FR-4 acceptance cases; attempt to disable the service through every known
path; confirm each attempt is logged, alarms once, and starts a cooldown; confirm release works
both ways (cooldown elapsed and passphrase).

**Exit criteria:** no path to disable protection is shorter than a cooldown, every attempt is
logged and audible, and the owner can still exit safely with either method.

---

## Phase 6 — Statistics and streaks

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

## Phase 7 — Panic button

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

## Phase 8 — Hardening, battery, and release build

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

## Phase 9 — Acceptance test matrix on the Galaxy A20

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

*Already exercised on an Android 14 development device: Drive blocked through the Apps tab, then
launched → `BlockedActivity` shown with the app name and Home button, event logged. Launcher
removed from the blocklist and filtered out of the picker; add-time and block-time rejection of
critical packages confirmed. The Device Owner rows below still need the Galaxy A20.*

- [ ] Blocklisted app → does not open, block screen shown, logged
- [ ] Blocklisted app hidden from the launcher (Device Owner)
- [ ] Blocklisted app suspended → cannot be launched even from Settings
- [ ] Un-suspending via the rule toggle restores the app
- [ ] Dialer, clock, calculator, IME, launcher, SystemUI, Play Store → never blocked
- [ ] A system package added to the blocklist by mistake → refused, warning shown

### Protection durability

*The settings shield (Accessibility, Device admin, SelfGuard's own app-info), the 24-hour release
cooldown, the passphrase release and the boot/watchdog auto-restart shipped in Phase 3 with unit
tests; their on-device rows below still need a run on the Android 14 development device and then
the Galaxy A20, because OEMs rename settings activities.*

- [ ] Settings → Accessibility → redirected to Home + block screen
- [ ] Settings → Device admin apps → redirected to Home + block screen
- [ ] Settings → App info (SelfGuard) → redirected (uninstall attempt)
- [ ] Settings → App info for another app / Wi-Fi / Display → NOT blocked
- [ ] Settings → Force stop / Uninstall → redirected
- [ ] Accessibility service switched off while armed → watchdog re-enables it (`enableSelf`)
- [ ] Phone reboot while armed → watchdog restarts, shield still active
- [ ] Release requested → 24 h cooldown starts, shield stays on; passphrase releases immediately
- [ ] 5 wrong passphrase attempts → 15-minute lockout
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
