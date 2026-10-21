# SelfGuard — Functional Requirements Document

**Version:** FRD v1.0 · **Companion to:** [PRD.md](./PRD.md) · **Status:** approved for build

Notation: `FR-<feature>.<n>` · Priority **M** (must), **S** (should), **C** (could).
Every requirement carries at least one acceptance criterion. Requirements marked **D** are
deliberate design decisions that must not be "simplified" away during implementation (see PRD §5.1).

---

## FR-0 — Application shell, onboarding, navigation

| ID | Requirement | Pri |
|---|---|---|
| FR-0.1 | The app is a single-activity Jetpack Compose application with a bottom navigation containing: Dashboard, Blocklists, Statistics, Settings | M |
| FR-0.2 | A first-run onboarding flow walks through, in order: (1) accessibility service, (2) usage access, (3) Device Owner via ADB, (4) accountability passphrase, (5) first blocklists | M |
| FR-0.3 | The dashboard shows, at a glance: protection state, Device Owner state, Accessibility state, Usage-access state, current clean streak, blocks today, and the panic button | M |
| FR-0.4 | Every permission state is polled live; a stale or missing permission raises a visible, actionable warning on the dashboard (never a silent failure) | M |
| FR-0.5 | The app shows no onboarding or permission prompts that are not actionable by the owner (no dead-end dialogs) | M |
| FR-0.6 | Navigation to any screen is possible while protection is armed; the app never blocks itself | M |
| FR-0.7 | The app starts in under 1 s on the Galaxy A20 and never shows a blank Compose frame for more than 500 ms | S |

**Acceptance (FR-0.2):** after a fresh install, the owner can reach a fully armed state following
only the on-screen instructions, without external documentation, except for the single ADB command
(which is displayed as a copy-to-clipboard code block).

---

## FR-1 — Website blocking

| ID | Requirement | Pri |
|---|---|---|
| FR-1.1 | When a browser's address bar or page title matches a blocked host or keyword, the page must not be usable | M |
| FR-1.2 | URL extraction supports, in order: (a) browser-specific address-bar resource IDs, (b) any editable text node whose content parses as a URL/host, (c) the window/page title | M |
| FR-1.3 | Hosts are normalised before matching: lowercase, scheme and `www.` stripped, port and path removed, trailing dot removed, IDN/punycode converted to ASCII | M |
| FR-1.4 | A host rule matches the exact host and, unless the rule says otherwise, all of its subdomains (`example.com` also blocks `video.example.com`) | M |
| FR-1.5 | Host matching is public-suffix aware: blocking `it.com` must not block `reddit.com` | S |
| FR-1.6 | Keyword rules match case-insensitively against the normalised URL, the page title and — only when the rule enables it — the visible page text | M |
| FR-1.7 | Keyword rules support plain substring matching; optional regular-expression matching exists behind an explicit per-rule toggle and is guarded against pathological patterns | C |
| FR-1.8 | On a match the app: navigates the browser away (Home action), stops the in-progress load where the API allows it, and launches the SelfGuard block screen | M |
| FR-1.9 | The block screen names the rule that fired, the blocked target, the current clean streak, and the remaining cooldown (if any) | M |
| FR-1.10 | The block screen cannot be dismissed with the Back button; Back returns to Home | M |
| FR-1.11 | Blocking is instantaneous from the user's point of view: the blocked content must be unreachable in under 500 ms of the triggering event | M |
| FR-1.12 | Chrome, Samsung Internet, Firefox and the AOSP Browser are supported out of the box | M |
| FR-1.13 | Every block is recorded as an event with timestamp, matched rule, host and browser package | M |
| FR-1.14 | Blocking can be turned off globally (websites only) without disabling app blocking | S |
| FR-1.15 | A per-rule "trusted for 30 minutes" option exists for false positives; it never disables the rule permanently | C |

**Acceptance (FR-1.1):** with `example.com` on the blocklist, opening `https://www.example.com/videos`
in Chrome results in: the address bar being cleared, the browser leaving the foreground, and the
SelfGuard block screen being displayed, with an event row `BLOCKED_SITE / example.com / chrome`.

**Acceptance (FR-1.5):** with `it.com` blocked, `https://reddit.com/r/all` is **not** blocked.

---

## FR-2 — App blocking

| ID | Requirement | Pri |
|---|---|---|
| FR-2.1 | A blocklisted app must not open while protection is armed | M |
| FR-2.2 | Foreground detection uses `TYPE_WINDOW_STATE_CHANGED` Accessibility events as the primary source, with a bounded `UsageStatsManager` poll (default 1500 ms) as backup | M |
| FR-2.3 | The backup poll runs **only** while the screen is on and the device is unlocked, and stops within one poll interval of the screen turning off | M |
| FR-2.4 | The poll queries a bounded event window (≤ 10 s) and keeps only the latest `ACTIVITY_RESUMED` event | M |
| FR-2.5 | A hard allowlist can never be blocked: launcher, dialer/telecom, emergency services, IME, clock/alarm, SystemUI, package installer (for system updates), SelfGuard itself | M **D** |
| FR-2.6 | The allowlist is resolved dynamically where possible (current launcher, current IME) and extended by a static critical-package list | M |
| FR-2.7 | On detection the blocked app is sent to the background and the SelfGuard block screen is shown | M |
| FR-2.8 | When Device Owner is active, blocked apps are additionally suspended (`setPackagesSuspended`) so they cannot be launched or appear in recents | S |
| FR-2.9 | When Device Owner is active, blocked apps may be hidden from the launcher (`setApplicationHidden`) if the owner enables that mode | S |
| FR-2.10 | Suspending/hiding is always reversible through the documented flow: disabling the rule, the cooldown expiry path, or the passphrase path | M **D** |
| FR-2.11 | SelfGuard never hides or suspends its own package, and never disables ADB/USB debugging | M **D** |
| FR-2.12 | Every app block is recorded with timestamp, package and label | M |
| FR-2.13 | Blocking a single app can be temporarily suspended from its rule without editing the list ("allow for 30 minutes") | C |

**Acceptance (FR-2.1):** with `com.example.feed` blocked and the device unlocked, launching that app
results in the app not being visible within 500 ms and the SelfGuard block screen being shown.

**Acceptance (FR-2.5):** with the dialer package blocked by accident in a user-imported list, the
app still refuses to block it, logs a `RULE_SKIPPED_CRITICAL` event, and shows a warning on the
dashboard.

---

## FR-3 — Custom blocklists

| ID | Requirement | Pri |
|---|---|---|
| FR-3.1 | Three editable lists: **Apps**, **Websites** (host rules), **Keywords** | M |
| FR-3.2 | Every rule has: a value, an optional label, an enabled flag, a category, and a timestamp | M |
| FR-3.3 | Rules can be added, edited, enabled/disabled and deleted from the UI | M |
| FR-3.4 | The Apps tab lists all installed packages (via `QUERY_ALL_PACKAGES`) with their labels and icons, and allows blocking a package with one tap | M |
| FR-3.5 | The Websites tab validates the host on input (rejecting malformed values) and shows the normalised form | M |
| FR-3.6 | The Keywords tab shows the current rule count and warns about rules that are likely to over-match (single very short words) | S |
| FR-3.7 | Lists can be exported to a JSON file and to a plain-text file (one value per line), and imported back, with a preview and a merge/replace choice | M |
| FR-3.8 | Import validates every entry, reports skipped/invalid lines, and never silently drops data | M |
| FR-3.9 | The rule set used by the matching engine is kept in memory and refreshed immediately (within 1 s) after any list change | M |
| FR-3.10 | Rules can be reordered (priority: first match wins) | C |
| FR-3.11 | A starter pack of common adult domains and keywords can be added in one tap, editable afterwards | S |
| FR-3.12 | Deleting a rule requires confirmation and shows the number of times it has fired | S |

**Acceptance (FR-3.7):** exporting produces `selfguard-blocklists-<timestamp>.json` containing
`apps`, `sites`, `keywords` arrays; importing it back reproduces an identical rule set.

---

## FR-4 — Protection durability (Device Admin / Device Owner, cooldown, passphrase)

| ID | Requirement | Pri |
|---|---|---|
| FR-4.1 | The app declares a Device Admin receiver whose policy set contains **only** `<force-lock/>` and `<lock-now/>` — never `<wipe-data/>` | M **D** |
| FR-4.2 | No code path in the app calls `wipeData()`, `wipeDataLocked()`, `setStorageEncryptionStatus()`, or any other destructive `DevicePolicyManager` API | M **D** |
| FR-4.3 | The app supports Device Owner mode, set once via ADB, documented in [DEVICE_OWNER.md](./DEVICE_OWNER.md) | M |
| FR-4.4 | When Device Owner is active, the app exposes: app suspension/hiding, screen lock, and the ability to release ownership | M |
| FR-4.5 | **Release flow:** the owner can release Device Owner ownership from Settings → "Release device protection", which calls `clearDeviceOwnerApp()` and deactivates Device Admin | M **D** |
| FR-4.6 | Release is permitted only when (a) the cooldown is not running **and** the minimum hold period (24 h) since the last protection change has elapsed, **or** (b) the accountability passphrase is supplied | M |
| FR-4.7 | The cooldown duration is configurable between 24 and 72 hours; the default is 48 hours | M |
| FR-4.8 | Changing the cooldown duration, the minimum hold, the protection strength, or the passphrase **while a cooldown is running** requires the passphrase | M |
| FR-4.9 | A tamper attempt (attempt to disable the service, revoke usage access, uninstall, or remove admin) triggers, in this order: log the attempt → lock the screen → post a high-priority notification → start the alarm → start the cooldown | M |
| FR-4.10 | The alarm is loud, plays for at most 30 seconds, auto-stops, can be dismissed by the owner, and never loops indefinitely or keeps the CPU awake afterwards | M **D** |
| FR-4.11 | The alarm must not be started more than once per tamper event | M |
| FR-4.12 | During the cooldown a persistent, non-dismissable notification shows: remaining time (counting down), the reason, and the editable "why I started this" message | M |
| FR-4.13 | Blocking remains fully active during a cooldown; a cooldown never weakens the blocklists | M **D** |
| FR-4.14 | The accountability passphrase is stored only as a salted, iterated hash (PBKDF2-HMAC-SHA256, ≥ 200 000 iterations, 16-byte random salt) — never in plain text, never in a backup | M **D** |
| FR-4.15 | Passphrase verification is constant-time and rate-limited (exponential back-off after repeated failures, with a lockout of up to 15 minutes) | M |
| FR-4.16 | Setting or changing the passphrase requires the current passphrase (or, on first setup, an explicit confirmation) | M |
| FR-4.17 | A duress passphrase is optional: entering it appears to succeed but instead records a tamper event and starts a cooldown | C |
| FR-4.18 | The owner can always fall back to ADB (`dpm clear-device-owner`) — the in-app release flow is never the *only* way out | M **D** |
| FR-4.19 | The stats screen lists every tamper attempt with timestamp and kind | M |
| FR-4.20 | If Device Owner is not active, the app still works (Accessibility + Usage Access only) but clearly states that protection is weaker | M |

**Acceptance (FR-4.5/4.6):** after a tamper attempt, the Release button is disabled and shows the
remaining cooldown. Once the cooldown expires (and 24 h have passed since the last protection
change), the button enables; tapping it and confirming calls `clearDeviceOwnerApp()`, and
`adb shell dpm list-owners` afterwards shows no owner for the device.

**Acceptance (FR-4.14):** `run-as com.selfguard cat files/datastore/…` shows only
`pbkdf2$200000$<salt-hex>$<hash-hex>`; the plaintext passphrase is not present anywhere on disk.

---

## FR-5 — Blocking the Settings screens that would disable protection

| ID | Requirement | Pri |
|---|---|---|
| FR-5.1 | When the foreground app is the Settings app and the current screen is a protection-sensitive one, the app navigates Home and shows the block screen | M |
| FR-5.2 | Protected screens include: Accessibility (incl. Installed/Downloaded apps), App info, Force stop, Uninstall, Usage access / Special app access, Battery optimisation, Device admin apps, Notification access, Permissions, Install unknown apps | M |
| FR-5.3 | Protection-sensitive screens are detected by a combination of: activity class name, view resource IDs, and visible text — any one matching is sufficient | M |
| FR-5.4 | The redirect is **relaxed while onboarding is incomplete or the Accessibility service is not yet running**, so the owner can grant the required permissions | M **D** |
| FR-5.5 | The redirect re-arms automatically as soon as the Accessibility service reports it is connected | M |
| FR-5.6 | Non-sensitive Settings screens remain fully usable (Wi-Fi, Bluetooth, Display, Sound, Battery, Wallpaper, etc.) | M |
| FR-5.7 | The block screen explains which screen was blocked and how to reach it legitimately (e.g. via the passphrase/cooldown flow), never just "denied" | M |
| FR-5.8 | A Settings visit that matches a protected screen is recorded as a `TAMPER_ATTEMPT` event of kind `SETTINGS_SHIELD` | M |
| FR-5.9 | Reaching "App info" for SelfGuard itself is treated as an uninstall attempt | M |
| FR-5.10 | The owner can temporarily turn the Settings shield off from within the app (it re-arms automatically) | S |

**Acceptance (FR-5.1):** with protection armed, opening Settings → Accessibility results in
Settings leaving the foreground within 500 ms and the SelfGuard block screen appearing.

**Acceptance (FR-5.6):** Settings → Wi-Fi opens normally and is not intercepted.

---

## FR-6 — Streak and statistics

| ID | Requirement | Pri |
|---|---|---|
| FR-6.1 | Every block, tamper, override and panic session is stored as an immutable event | M |
| FR-6.2 | A **clean day** is a local calendar day with zero bypass events, where a bypass event = tamper attempt, override used, or abandoned panic session | M |
| FR-6.3 | The current streak counts consecutive clean days ending today; today is displayed as *pending* until the day ends | M |
| FR-6.4 | The best streak ever achieved is stored and displayed | M |
| FR-6.5 | The statistics screen shows: blocks today / 7 days / 30 days / all time, split by category, the top blocked hosts and apps, the tamper log, days protected, and total blocked count | M |
| FR-6.6 | A 30-day bar chart shows blocks per day, with tamper days marked | S |
| FR-6.7 | Events are queryable by date range and type, and exportable to JSON and CSV | S |
| FR-6.8 | The daily rollup is computed lazily from the event log and cached in a daily-stats table, so the event log stays the source of truth | S |
| FR-6.9 | Deleting all statistics is possible only from a dedicated screen, requires the passphrase, and is itself logged | S |
| FR-6.10 | The streak never resets silently; a broken streak is shown with the date it ended | M |

**Acceptance (FR-6.2):** a day in which the owner was blocked 12 times but attempted no bypass is a
**clean** day and the streak continues.

---

## FR-7 — Panic button

| ID | Requirement | Pri |
|---|---|---|
| FR-7.1 | A panic button is available on the dashboard and on the block screen | M |
| FR-7.2 | It runs a breathing exercise with an animated circle, a countdown, and optional haptic cues | M |
| FR-7.3 | Supported patterns: Box 4-4-4-4, 4-7-8, Calm 5-5, and a custom 4-value pattern; duration configurable from 1 to 10 minutes | M |
| FR-7.4 | The exercise can be stopped early; stopping early is recorded as an abandoned session (a bypass event that breaks the streak) | M |
| FR-7.5 | Completing a session is recorded as a resisted session and is **not** a streak break | M |
| FR-7.6 | After a session, a cool-down window (default 15 minutes, configurable 0–60) applies during which blocked apps and sites stay blocked even if the owner tries them | M |
| FR-7.7 | The panic screen includes a short reminder that talking to a person or a support service is an option, and shows the accountability partner's contact if configured | S |
| FR-7.8 | The screen must be reachable from the lock screen context when the phone is picked up in the moment of the urge | C |
| FR-7.9 | The panic session duration and outcome are shown on the dashboard for the last 7 days | C |

**Acceptance (FR-7.4):** starting a 3-minute session and stopping it after 40 s records
`PANIC_ABANDONED` with `durationSeconds=40`, and the current streak drops by one.

---

## FR-8 — Data storage, privacy, and integrity

| ID | Requirement | Pri |
|---|---|---|
| FR-8.1 | Blocklists, events and daily statistics are stored in a Room database | M |
| FR-8.2 | Settings and the protection state machine are stored in DataStore (Preferences) | M |
| FR-8.3 | No app data is uploaded anywhere; the app holds no INTERNET permission | M **D** |
| FR-8.4 | `android:allowBackup="false"`; the blocklists, event log and passphrase hash are excluded from any backup path | M **D** |
| FR-8.5 | The passphrase hash is never written to logs, crash output or the event log | M |
| FR-8.6 | The database and the DataStore file live in the app's private storage only | M |
| FR-8.7 | The event log is append-only; entries are never modified after write (except by an explicit, logged "clear statistics" action) | M |
| FR-8.8 | The database schema is versioned and migrations are provided for every schema change | M |
| FR-8.9 | Blocklist import/export is the only way data leaves the app, and it is user-initiated | M |
| FR-8.10 | Uninstalling the app removes all data (no external storage is used) | M |

---

## FR-9 — Non-functional requirements

| ID | Requirement | Pri |
|---|---|---|
| FR-9.1 | Total idle battery impact < 1 %/day over a 24 h measurement with the screen off most of the time | M |
| FR-9.2 | With the screen on, the foreground poll costs < 3 % CPU on the Galaxy A20's Exynos 850 | M |
| FR-9.3 | Blocking latency < 500 ms from trigger to redirect (FR-1.11, FR-2.1) | M |
| FR-9.4 | The app works fully offline | M |
| FR-9.5 | The app works on Android 11 / One UI 3.1 on the Galaxy A20; other devices are best-effort | M |
| FR-9.6 | No ANR and no crash during a 30-minute soak test with continuous foreground app switching | M |
| FR-9.7 | All matching logic is unit-testable as pure Kotlin (no Android dependencies) and covered by JVM unit tests | M |
| FR-9.8 | The app never blocks the launcher, the dialer, or emergency services, even with a corrupt rule set | M **D** |
| FR-9.9 | A release APK is signed with a locally generated keystore that is stored outside the repository | M |
| FR-9.10 | The debug and release variants share the same `applicationId` so Device Owner commands are identical | M |

---

## FR-10 — Requirement → phase traceability

| Phase | Requirements delivered |
|---|---|
| 0 — Skeleton | FR-0.1 (shell), FR-8.3, FR-8.4 (manifest baseline), FR-9.10 |
| 1 — Foundation | FR-0.1, FR-8.1, FR-8.2, FR-8.7, FR-8.8, FR-9.7 |
| 2 — Onboarding | FR-0.2 … FR-0.6, FR-2.6, FR-5.4 |
| 3 — Website blocking | FR-1.1 … FR-1.14, FR-8.1 |
| 4 — App blocking | FR-2.1 … FR-2.13, FR-5.3 |
| 5 — Blocklists UI | FR-3.1 … FR-3.12, FR-8.9 |
| 6 — Protection hardening | FR-4.1 … FR-4.20, FR-5.1 … FR-5.10, FR-2.8 … FR-2.11 |
| 7 — Statistics | FR-6.1 … FR-6.10 |
| 8 — Panic button | FR-7.1 … FR-7.9 |
| 9 — Hardening & release | FR-9.1 … FR-9.10, FR-2.3, FR-4.10 |
| 10 — Acceptance | All of the above, verified on device |

---

## FR-11 — Explicitly rejected requirements (and why)

| Rejected | Reason |
|---|---|
| `wipe-data` device-admin policy | Irreversible data loss; violates PRD C8 and FR-4.1/4.2 |
| Blocking the Settings app entirely | The owner needs Wi-Fi, Bluetooth, and system updates; selective shield only |
| Hiding SelfGuard's own launcher icon | The owner must always be able to open the app; FR-2.11 |
| Disabling USB debugging | Removes the documented recovery path; FR-2.11, FR-4.18 |
| Root / system-app / SELinux changes | Unsupported, breaks updates, not needed |
| Network-based partner notifications | Requires a backend and INTERNET permission; out of scope (PRD §5) |
| AI/content classification | Non-deterministic, slow, battery-heavy; the blocklist is the product |
| Blocking all browsers wholesale | The owner must be able to browse; only listed targets are blocked |
