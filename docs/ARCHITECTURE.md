# SelfGuard — Architecture

**Version:** v1.0 · **Companions:** [PRD.md](./PRD.md) · [FRD.md](./FRD.md) · [PHASES.md](./PHASES.md) · [DEVICE_OWNER.md](./DEVICE_OWNER.md)

---

## 1. Principles

1. **The matcher is pure.** No Android APIs in the rule engine. It takes a normalised string and an
   immutable rule snapshot and returns a decision. Everything else is plumbing around it. This is
   what makes the product testable and fast enough to run inside an Accessibility callback.
2. **No network, ever.** The app does not request `INTERNET`. There is no code path that could
   send the blocklist, the event log, or the passphrase anywhere.
3. **Never irreversible.** No wipe policy, no factory reset, no destructive `DevicePolicyManager`
   call. Every guard has an owner-operated escape hatch (cooldown or passphrase).
4. **Fail safe, fail loud.** If a protection mechanism breaks (service killed, permission revoked),
   the dashboard says so in plain language. Silent failure is the enemy of this product.
5. **The hot path must not touch the disk.** Rules are cached in memory and refreshed on change;
   the database is never read while deciding whether to block.

## 2. Stack

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin 2.3.21 | — |
| UI | Jetpack Compose (BOM 2025.10.01, Material 3) | Requested; single-activity UI |
| Build | AGP 8.13.2 / Gradle 8.14.5 / JDK 17 | Newest generation that still supports `compileSdk 36` without AGP 9 breaking changes |
| SDK | `minSdk 26`, `targetSdk 30`, `compileSdk 36` | `targetSdk 30` is deliberate: it keeps the app out of Android 12+ behaviour changes (notification runtime permission, exported-component rules are moot, FGS restrictions) |
| Storage | Room 2.8.5 (KSP) | Relational data: rules, events, daily rollups |
| Settings | DataStore Preferences 1.1.7 | Small, typed key/value state; flows for reactive UI |
| Async | Coroutines 1.11.0 | — |
| Background work | WorkManager 2.10.1 | Daily rollup / housekeeping (not for the hot path) |
| DI | Manual `AppContainer` (no Hilt) | A personal app does not need a DI framework; manual wiring keeps the build fast and the code obvious. Hilt is a drop-in later if the graph grows. |

**Note on `targetSdk 30`:** it is below Play's minimum, which is irrelevant here, and it keeps
Android 11-era behaviour. Consequences that matter: no `POST_NOTIFICATIONS` runtime prompt is
needed (notifications just work), and `QUERY_ALL_PACKAGES` is granted at install time.

## 3. Module and package layout

Single Gradle module `:app` (a personal app does not benefit from multi-module overhead), organised
into logical layers:

```
:app
└── src/main/java/com/selfguard/
    ├── SelfGuardApp.kt              Application; builds the AppContainer
    ├── core/
    │   ├── AppContainer.kt           Manual DI graph
    │   ├── AppDispatchers.kt         Dispatcher provider (testable)
    │   ├── Clock.kt                  Time source abstraction (timezone-aware)
    │   ├── Logging.kt                Redacting logger (never logs secrets)
    │   └── result/                   Result / Outcome wrappers
    ├── domain/
    │   ├── model/                    BlockedApp, BlockedSite, BlockedKeyword, Event, DailyStat,
    │   │                              ProtectionState, CooldownState, EventType
    │   ├── match/                    ← pure Kotlin matching engine
    │   │   ├── HostNormalizer.kt     scheme/www/port/path stripping, IDN → ASCII
    │   │   ├── PublicSuffix.kt       PSL lookup (asset-backed) + registrable domain
    │   │   ├── RuleMatcher.kt        host rules, keyword rules, wildcard host patterns
    │   │   └── MatchResult.kt        rule id, kind, matched span
    │   ├── protection/               ProtectionStateMachine (armed/tampered/cooldown)
    │   ├── stats/                    StreakCalculator, DailyRollupCalculator (pure)
    │   └── breach/                   BreachDetector: reacts to a tamper event (pure decisions)
    ├── data/
    │   ├── local/
    │   │   ├── db/                   SelfGuardDatabase, entities, DAOs, migrations
    │   │   ├── prefs/                SettingsStore (DataStore), SecretStore
    │   │   └── packages/             InstalledPackagesRepository (PackageManager)
    │   └── repo/
    │       ├── BlocklistRepository.kt
    │       ├── EventRepository.kt
    │       ├── StatsRepository.kt
    │       └── ProtectionRepository.kt   (state machine + DataStore persistence)
    ├── service/
    │   ├── accessibility/
    │   │   ├── SelfGuardAccessibilityService.kt   the engine host
    │   │   ├── BrowserProbe.kt       URL/title extraction per browser profile
    │   │   ├── ForegroundDetector.kt Accessibility-first + UsageStats fallback
    │   │   ├── ScreenGuard.kt        protected-Settings-screen detection
    │   │   └── BrowserProfiles.kt    per-browser resource-id candidates
    │   ├── deviceadmin/
    │   │   ├── SelfGuardDeviceAdminReceiver.kt
    │   │   └── DevicePolicyGateway.kt  suspend / hide / lockNow / clearDeviceOwnerApp
    │   ├── receiver/
    │   │   ├── BootReceiver.kt
    │   │   └── ScreenStateReceiver.kt  screen on/off, user present
    │   └── notification/
    │       ├── NotificationChannels.kt
    │       ├── ProtectionNotifier.kt  ongoing status + cooldown countdown
    │       └── AlarmPlayer.kt         ≤30 s tamper alarm
    └── ui/
        ├── MainActivity.kt
        ├── navigation/                Compose Navigation routes
        ├── dashboard/                 status, streak, today, panic
        ├── blocklists/                apps / sites / keywords tabs, import-export
        ├── stats/                     charts, history, tamper log
        ├── settings/                  protection, cooldown, passphrase, release
        ├── onboarding/                permission wizard + ADB guide
        └── blocked/                   BlockedActivity (the block screen)
```

## 4. Runtime components

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Android                                                                  │
│                                                                          │
│  ┌──────────────────────────┐        ┌───────────────────────────────┐   │
│  │ SelfGuardAccessibility   │        │ SelfGuardDeviceAdminReceiver │   │
│  │ Service                  │        │ (DO / DA entry point)        │   │
│  │  • URL extraction        │◄───────┤ onEnabled / onDisabled log   │   │
│  │  • foreground detection  │        └───────────────────────────────┘   │
│  │  • Settings shield       │                                            │
│  │  • triggers BlockedAct.  │        ┌───────────────────────────────┐   │
│  └────────────┬─────────────┘        │ BootReceiver                 │   │
│               │                      │  • re-arm state, schedule job │   │
│               │                      └───────────────────────────────┘   │
│               ▼                                                           │
│  ┌──────────────────────────┐        ┌───────────────────────────────┐   │
│  │ BlockedActivity          │        │ DevicePolicyGateway (DO)     │   │
│  │  • reason, streak, panic │        │  • suspend / hide packages   │   │
│  │  • cooldown state        │        │  • lockNow()                 │   │
│  └──────────────────────────┘        │  • clearDeviceOwnerApp()     │   │
│                                      └───────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
```

### 4.1 `SelfGuardAccessibilityService` — the engine host

The Accessibility service is the app's long-lived host. It is chosen over a foreground service
because:

- it is exempt from background-start restrictions and survives app standby,
- the system restarts it automatically after a reboot if it was enabled,
- it is the only component allowed to read window content.

Responsibilities:

| Task | Trigger | Notes |
|---|---|---|
| URL extraction + match | `TYPE_WINDOW_STATE_CHANGED`, `TYPE_VIEW_TEXT_CHANGED` | Only for known browser packages |
| Foreground app detection | `TYPE_WINDOW_STATE_CHANGED` | Primary source, instant |
| UsageStats backup poll | coroutine, 1500 ms | Only while screen on **and** unlocked |
| Settings shield | `TYPE_WINDOW_STATE_CHANGED` for `com.android.settings` | Text/ID based |
| Block action | any match | Home + launch `BlockedActivity` |
| Protection state observation | `ProtectionRepository` flow | Re-arms/un-arms behaviour |
| Service liveness heartbeat | internal | Surfaces "service died" on the dashboard |

All work is delegated to coroutines; the Accessibility callbacks themselves only enqueue work.

### 4.2 `SelfGuardDeviceAdminReceiver`

A thin receiver: `onEnabled`, `onDisabled`, `onPasswordChanged`, `onPackageRemoved`. It records
events and refreshes the protection state. Because the app never declares `wipe-data`, the
standard "deactivating the admin wipes the device" behaviour is impossible by construction.

### 4.3 `BootReceiver`

On `BOOT_COMPLETED`, `QUICKBOOT_POWERON` and `MY_PACKAGE_REPLACED`: re-reads the protection state,
re-applies the persistent notification, re-schedules the daily rollup job, and re-validates the
suspended-package set. If Device Owner is active, suspended packages stay suspended across
reboots anyway (system state), so the receiver only verifies it.

### 4.4 `BlockedActivity`

A dedicated activity (not an overlay) so it can be shown over the blocked app without the
`SYSTEM_ALERT_WINDOW` permission, and so it survives being launched from a service context.
Flags: `singleTask`, `noHistory`, `excludeFromRecents`, `taskAffinity=com.selfguard.blocked`,
`showWhenLocked` + `turnScreenOn` set programmatically (API 27+, and the app is `minSdk 26`, so the
flags are applied defensively).

## 5. Data storage

### 5.1 Room — `SelfGuardDatabase` (schema v1)

```
blocked_apps
  id              INTEGER PK autoincrement
  package_name    TEXT NOT NULL UNIQUE
  label           TEXT NOT NULL
  category        TEXT NOT NULL DEFAULT 'GENERAL'
  is_enabled      INTEGER NOT NULL DEFAULT 1
  suspend_on_block INTEGER NOT NULL DEFAULT 1   -- Device Owner suspension
  hide_on_block   INTEGER NOT NULL DEFAULT 0     -- Device Owner hiding
  notes           TEXT
  added_at        INTEGER NOT NULL
  INDEX (is_enabled)

blocked_sites
  id              INTEGER PK autoincrement
  domain          TEXT NOT NULL UNIQUE          -- normalised, no scheme/www
  label           TEXT NOT NULL
  include_subdomains INTEGER NOT NULL DEFAULT 1
  is_enabled      INTEGER NOT NULL DEFAULT 1
  category        TEXT NOT NULL DEFAULT 'GENERAL'
  notes           TEXT
  added_at        INTEGER NOT NULL
  INDEX (is_enabled)

blocked_keywords
  id              INTEGER PK autoincrement
  keyword         TEXT NOT NULL UNIQUE
  label           TEXT NOT NULL
  match_in_page_text INTEGER NOT NULL DEFAULT 0
  use_regex       INTEGER NOT NULL DEFAULT 0
  is_enabled      INTEGER NOT NULL DEFAULT 1
  category        TEXT NOT NULL DEFAULT 'GENERAL'
  notes           TEXT
  added_at        INTEGER NOT NULL
  INDEX (is_enabled)

events                      -- append-only log
  id              INTEGER PK autoincrement
  type            TEXT NOT NULL                 -- BLOCKED_SITE | BLOCKED_APP | ...
  occurred_at     INTEGER NOT NULL              -- epoch millis, UTC
  local_date      TEXT NOT NULL                 -- yyyy-MM-dd in the device timezone
  package_name    TEXT
  url             TEXT
  host            TEXT
  keyword         TEXT
  rule_id         INTEGER
  detail          TEXT
  severity        TEXT NOT NULL DEFAULT 'INFO'
  INDEX (occurred_at), INDEX (type), INDEX (local_date)

daily_stats                   -- cached rollups, source of truth is `events`
  local_date      TEXT PK      -- yyyy-MM-dd
  blocked_sites   INTEGER NOT NULL DEFAULT 0
  blocked_apps    INTEGER NOT NULL DEFAULT 0
  blocked_keywords INTEGER NOT NULL DEFAULT 0
  tamper_attempts INTEGER NOT NULL DEFAULT 0
  relapses        INTEGER NOT NULL DEFAULT 0
  panic_sessions  INTEGER NOT NULL DEFAULT 0
  panic_seconds   INTEGER NOT NULL DEFAULT 0
  cooldown_seconds INTEGER NOT NULL DEFAULT 0
```

DAO surface: `BlocklistDao` (upsert/delete/toggle/all-enabled), `EventDao`
(`insert`, `since(timestamp)`, `countByType`, `range(date)`), `DailyStatsDao` (`upsert`, `range`).

**Room specifics**
- `exportSchema = true`; schemas are committed under `app/schemas/` so migrations can be diffed.
- All suspend DAOs; `withTransaction` for multi-table writes (e.g. event + daily rollup).
- Database file: `databases/selfguard.db` in app-private storage. No encryption dependency is
  added; `allowBackup=false` keeps the data on the device. (Optional SQLCipher is listed as a
  Phase 9 stretch goal — see §14.)

### 5.2 DataStore — `selfguard_settings` (Preferences)

| Key | Type | Purpose |
|---|---|---|
| `protection_enabled` | Boolean | Master switch for blocking (tamper protection stays armed) |
| `protection_armed_at` | Long | When protection was last armed |
| `last_protection_change_at` | Long | Start of the minimum hold period |
| `cooldown_hours` | Int | 24…72, default 48 (FR-4.7) |
| `min_hold_hours` | Int | 24…72, default 24 (FR-4.6) |
| `cooldown_started_at` | Long | 0 = no cooldown |
| `cooldown_until` | Long | Epoch millis |
| `cooldown_reason` | String | e.g. `UNINSTALL_ATTEMPT` |
| `why_message` | String | "Why I started this", editable, shown in the cooldown notification |
| `passphrase_hash` | String | `pbkdf2$iters$saltHex$hashHex` |
| `passphrase_salt` | String | 16 random bytes, hex |
| `passphrase_iterations` | Int | ≥ 200 000 |
| `passphrase_configured` | Boolean | — |
| `passphrase_fail_count` / `_locked_until` | Int / Long | Rate limiting (FR-4.15) |
| `duress_passphrase_hash` / `_salt` | String | Optional (FR-4.17) |
| `block_websites` / `block_apps` / `block_keywords` | Boolean | Per-category switches |
| `protect_settings_screens` | Boolean | Settings shield (FR-5.1) |
| `lock_on_tamper` | Boolean | Screen lock on tamper (FR-4.9) |
| `alarm_enabled` | Boolean | Tamper alarm (FR-4.10) |
| `onboarding_complete` | Boolean | Gates FR-5.4 |
| `panic_pattern` | String | `BOX_4_4_4_4` \| `BREATH_4_7_8` \| `CALM_5_5` \| `CUSTOM` |
| `panic_pattern_custom` | String | e.g. `4,4,4,4` |
| `panic_minutes` | Int | 1…10 |
| `panic_cooldown_minutes` | Int | 0…60, default 15 |
| `panic_haptics` | Boolean | — |
| `breach_pause_until` | Long | Cool-down window during which blocks stay enforced |
| `stats_totals_*` | Long | Cached totals |
| `best_streak_days` | Int | Best clean streak (FR-6.4) |
| `current_streak_days` | Int | Cached current streak |
| `last_rollup_date` | String | Last processed local date |
| `theme_mode` | String | `SYSTEM` \| `LIGHT` \| `DARK` |

DataStore is written with `updateData` (atomic read-modify-write); reads are exposed as `Flow` so
the UI and the service observe the same source of truth.

### 5.3 Secrets

- The passphrase is **never stored in plain text**. Format: `pbkdf2$<iterations>$<saltHex>$<hashHex>`,
  PBKDF2-HMAC-SHA256, 200 000 iterations, 16-byte salt, 32-byte derived key, compared in
  constant time.
- The salt and the hash live in the app's private DataStore file; `allowBackup=false` prevents
  them from leaving the device via backup.
- The derived key is not cached in memory longer than a single verification.
- `Logging` redacts anything that looks like a passphrase and never logs hash material.

## 6. Matching engine (pure Kotlin)

```
input:  candidateUrlText : String
        candidateTitle   : String?
        pageText         : String?     (optional, only when a rule opts in)
        rules            : RuleSnapshot (immutable, in-memory)
output: MatchResult?  (ruleId, kind = SITE | KEYWORD, matchedText, normalisedHost)
```

### 6.1 Host normalisation (`HostNormalizer`)

1. Trim, collapse whitespace, drop a leading `search`/`?q=` prefix if the input is a search query.
2. Strip the scheme (`http://`, `https://`, `chrome://`, `about:` → ignore non-http schemes).
3. Strip userinfo (`user:pass@`).
4. Take the authority, drop the port and any path/query/fragment.
5. Strip a trailing `.`.
6. Strip a leading `www.`.
7. Lowercase (ASCII case folding).
8. Convert IDN to ASCII via `java.net.IDN.toASCII` (punycode), and convert `ß`/`ς` forms.

`HostNormalizer.normalise("HTTPS://WWW.Example.com:443/a/b?c=1")` → `example.com`.

### 6.2 Public suffix handling (`PublicSuffix`)

- `assets/public_suffix_list.dat` (Mozilla PSL, trimmed to the ICANN section) is loaded once at
  startup into an in-memory set; a small hard-coded fallback list (`com`, `net`, `org`, `co.uk`, …)
  covers the case where the asset fails to load.
- `registrableDomain(host)` → `example.co.uk` for `www.example.co.uk`.
- **Rule validation:** a rule whose domain is a public suffix (e.g. `it.com`, `co.uk`) is rejected
  at entry with an explanatory message — this is what makes FR-1.5 hold.
- **Matching:** host matches rule R if `host == R` or (`R.includeSubdomains` and
  `host.endsWith("." + R)`), where `R` is not a public suffix.
- Optional wildcard rules: `*.example.com` matches any subdomain but not the apex.

### 6.3 Keyword matching (`RuleMatcher`)

- Case-insensitive substring match by default.
- Page-text matching only when the rule sets `matchInPageText = true` (off by default: page text
  is large and expensive to read).
- Optional regex per rule, compiled once per snapshot, with a 1 MB input cap and a match-time guard
  to avoid pathological backtracking (input truncated to 2 000 characters for regex matching).
- The engine returns the **first** match, ordered: exact host → wildcard host → keyword.

### 6.4 Rule snapshot

`RuleSnapshot` is an immutable snapshot of all enabled rules, published through a `StateFlow` by
`BlocklistRepository` and rebuilt on every database change (or on `MY_PACKAGE_REPLACED`/boot).
The hot path reads the current snapshot from memory — no database I/O, no disk I/O.

## 7. Foreground app detection

```
AccessibilityEvent (TYPE_WINDOW_STATE_CHANGED)
        │  event.packageName, event.windowId
        ▼
┌───────────────────────────────────────────────────────────────────────┐
│ 1. package == self / allowlist / critical  → ignore                  │
│ 2. window type is TYPE_APPLICATION (via getWindows) → candidate       │
│ 3. candidate corroborated by UsageStats ACTIVITY_RESUMED within 5 s  │
│      └─ not corroborated → treat as a transient window (dialog/IME)   │
│ 4. candidate is on the blocked-app list → BLOCK                       │
└───────────────────────────────────────────────────────────────────────┘
        (parallel, slower) UsageStatsManager.queryEvents(≤10 s window)
        every 1500 ms, only while: PowerManager.isInteractive == true
                                   && KeyguardManager.isDeviceLocked == false
```

**Why both sources.** Accessibility events are instant but occasionally report a non-foreground
package (a dialog window, an IME, a notification shade). Usage stats are authoritative but lag by
up to the poll interval and can be revoked. Using Accessibility as the trigger and Usage stats as
the *corroborator* gives instant detection with few false positives, and the UsageStats poll then
catches anything the Accessibility service missed (e.g. an app that renders no accessible content).

**Allowlist construction**
- Dynamic: current launcher (`Intent.ACTION_MAIN` + `CATEGORY_HOME` → `resolveActivity`),
  current IME (`InputMethodManager.inputMethodList`).
- Dynamic: telecom/dialer — packages resolving `Intent.ACTION_DIAL` / `ACTION_CALL` and
  `TelecomManager`.
- Static critical list: `com.android.systemui`, `com.android.phone`, `com.android.server.telecom`,
  `com.android.providers.telephony`, `com.android.providers.downloads`, `com.android.packageinstaller`,
  `com.google.android.packageinstaller`, `com.android.vending` (Play, for system updates),
  `com.android.settings` (shielded, never blocked), `com.sec.android.*` variants, the alarm clock.
- The allowlist is **not editable** (FR-2.5) and is checked before the blocked list, always.

**Battery policy**
- No wakelock anywhere in the normal path.
- The poll stops within one interval of `ACTION_SCREEN_OFF`, and does not run at all while the
  keyguard is engaged.
- `queryEvents` uses a ≤ 10 s window, so the cost per tick is tiny.
- `PowerManager.isInteractive` is checked before each tick, so a missed broadcast cannot cause a
  runaway poll.
- The Accessibility service itself does not hold a wakelock; Android keeps the service process
  resident as long as the service is enabled and bound.

## 8. Block action pipeline

```
match ──► BlockDecision(kind, rule, target)
             │
             ├─► 1. EventRepository.insert(BLOCKED_SITE | BLOCKED_APP | BLOCKED_KEYWORD)
             ├─► 2. performGlobalAction(GLOBAL_ACTION_HOME)     (leaves the blocked app/browser)
             ├─► 3. startActivity(BlockedActivity, NEW_TASK | CLEAR_TOP)
             │        showWhenLocked / turnScreenOn (defensive, API 27+)
             ├─► 4. BlockedActivity renders: rule, target, streak, cooldown, panic button
             └─► 5. optional: DevicePolicyGateway.suspendPackage(target) (DO only)
```

Guards against loops:
- `BlockedActivity` runs in its own task affinity and is `noHistory`; when it is in the foreground
  the detector sees `com.selfguard` and ignores it.
- A re-entrancy flag (`AtomicBoolean blocking`) prevents two simultaneous block actions.
- A minimum 750 ms interval between two block actions prevents notification storms.

## 9. Protection state machine

```
                    ┌───────────────┐
                    │  UNINITIALISED│
                    └───────┬───────┘
                            │ onboarding complete + service enabled
                            ▼
   ┌───────────────┐   tamper attempt   ┌──────────────────────┐
   │    ARMED      │ ─────────────────► │  TAMPERED (cooldown) │
   │ blocking on   │ ◄───────────────── │ blocking STILL on    │
   └───────┬───────┘   cooldown expiry  └──────────┬───────────┘
           │                                      │
           │ passphrase supplied                  │ passphrase supplied
           │ (skips cooldown)                     ▼
           │                            ┌──────────────────────┐
           │                            │  RELEASE_PENDING     │
           │                            │ clearDeviceOwnerApp()│
           ▼                            └──────────┬───────────┘
   ┌───────────────┐                                ▼
   │   RELEASED    │ ──────────────────► ┌───────────────┐
   │  (no admin)   │   re-arm via pass-  │   UNARMED    │
   └───────────────┘   phrase or 24 h    └───────────────┘
```

State fields live in DataStore (§5.2) and are exposed as a `StateFlow<ProtectionState>`.

**Cooldown semantics (FR-4.6 … FR-4.13)**
- A cooldown starts on a tamper attempt and lasts `cooldownHours` (default 48).
- During a cooldown: blocking is unchanged; the Release action and protection-critical settings
  are locked; the ongoing notification shows a live countdown.
- Release is allowed when `now ≥ cooldownUntil` **and** `now ≥ lastProtectionChangeAt + minHoldHours`,
  or immediately with the passphrase.
- Changing `cooldownHours`, `minHoldHours`, protection strength, or the passphrase during a cooldown
  requires the passphrase.

**Countdown without a timer:** the cooldown notification uses
`setUsesChronometer(true)`, `setWhen(cooldownUntil)`, `setChronometerCountDown(true)`
(API 24+) so the system renders the countdown and SelfGuard does no periodic work.

## 10. Device Admin / Device Owner integration

`DevicePolicyGateway` wraps every DPM call the app uses:

| API | Used for | Requires |
|---|---|---|
| `isDeviceOwnerApp(pkg)` | Detecting DO mode | — |
| `isAdminActive(admin)` | Detecting DA mode | — |
| `setPackagesSuspended(pkg, list, true/false)` | Hiding blocked apps from the launcher | Device Owner |
| `setApplicationHidden(pkg, hidden)` | Optional launcher hiding | Device Owner, API 30+ |
| `lockNow()` | Locking the screen on a tamper attempt | Device Owner (`force-lock`) or DA (`lock-now` policy) |
| `clearDeviceOwnerApp(pkg)` | The release flow | Device Owner (self) |
| `getStorageEncryptionStatus()` | Read-only status display | — |

**Not present anywhere in the codebase:** `wipeData`, `wipeDataLocked`, `setCameraDisabled` for
non-admins, `resetPassword`, `setPasswordQuality` (which would let the app change the lock-screen
password). The device-admin XML declares only `<force-lock/>` and `<lock-now/>`.

**Granting `PACKAGE_USAGE_STATS`:** `DevicePolicyManager.setAppOpPolicy` can grant the
`GET_USAGE_STATS` app op, but only for apps the device owner manages — it cannot grant usage
access to a *system* permission that the user granted. In practice the owner grants Usage access
once in Settings → Special app access → Usage access. The onboarding flow detects the missing
grant, deep-links to that Settings page while the shield is relaxed, and treats later revocation
as a tamper event (FR-2.5 note in PRD).

## 11. Threading and concurrency

| Work | Context |
|---|---|
| Accessibility callbacks | Main thread → immediately `launch` a coroutine |
| Matching (pure CPU) | `Dispatchers.Default` |
| Room IO | `Dispatchers.IO` (Room's own executor) |
| DataStore reads/writes | `Dispatchers.IO` via the DataStore scope |
| UsageStats poll | `Dispatchers.Default`, one coroutine owned by the service |
| Notifications | `Dispatchers.Main` |
| Alarm playback | `Dispatchers.Default` + `MediaPlayer` callbacks on the main looper |

Concurrency rules:
- one mutex around `ProtectionState` writes;
- a single writer for the event log (Room handles it);
- `RuleSnapshot` published as an immutable object → readers never see a torn state;
- the service cancels its poll coroutine in `onDestroy` and restarts it in `onServiceConnected`.

## 12. Security model

| Threat | Mitigation |
|---|---|
| Someone reads the passphrase | Only a salted, 200k-iteration PBKDF2 hash is stored |
| Brute-force of the passphrase | Exponential back-off; 15-minute lockout after 5 failures; rate limiting is persisted |
| Someone edits the app data via ADB | Out of scope for a personal device, but `allowBackup=false` and app-private storage mean no backup channel; the passcode/lock screen is the real boundary |
| Someone uninstalls the app | Device Owner cannot be uninstalled while it is the owner; the release flow and the ADB fallback are the only exits |
| Someone disables the Accessibility service | Settings shield + tamper logging + cooldown + DO re-arming of our own components |
| Data exfiltration | No `INTERNET` permission; no analytics; nothing leaves the device |
| Screen recording of the blocklist | The app sets `FLAG_SECURE` on the block screen and the dashboard (optional, FR-2.13-style extras land in Phase 9) |

**Deliberate absence of destructive capability:** the product's value comes from friction, not from
fear of data loss. A wipe policy would make the owner passive and would risk a catastrophic
outcome for an accidental tap.

## 13. Failure modes and degradation

| Failure | Detection | Behaviour |
|---|---|---|
| Accessibility service killed by Samsung's task killer | `SelfGuardAccessibilityService.isRunning == false` while the system reports the service enabled | Dashboard banner + notification; the owner re-enables it; DO can re-enable the component |
| Usage access revoked | `AppOpsManager` check | Treated as tampering (FR-4.9) *and* the app falls back to Accessibility-only detection |
| Device Owner removed externally | `isDeviceOwnerApp == false` | Dashboard shows the weaker state; release button explains that no release is needed |
| Room database corrupt | DAO exception | Catch, log (redacted), fall back to an in-memory empty rule set and prompt for a re-import; never crash the service |
| Public suffix asset missing | Loader exception | Fall back to the built-in suffix list and warn in the diagnostics screen |
| Broadcast `SCREEN_OFF` missed | `PowerManager.isInteractive` check before each tick | Poll stops anyway |
| Clock changed (timezone/day boundary) | `Clock` abstraction + day-boundary detection | Daily rollup recomputes the affected dates |

## 14. Testing strategy

| Layer | Tool | Coverage |
|---|---|---|
| Pure domain | JUnit (JVM) | `HostNormalizer`, `PublicSuffix`, `RuleMatcher`, `StreakCalculator`, `ProtectionStateMachine`, `BreachDetector` |
| Data | Room in-memory DB tests | DAO behaviour, migrations |
| Storage | DataStore test harness | Preferences round-trips |
| UI | Compose UI tests | Blocklist editing, dashboard rendering, block screen states |
| Integration | Manual, on the Galaxy A20 | The matrix in [PHASES.md](./PHASES.md) § Phase 10 |

Target: the whole matching engine is covered by JVM tests that run in < 2 s (`./gradlew :app:test`).

## 15. Build, signing, and release

- Debug build: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Release build: a local keystore referenced by `keystore.properties` (git-ignored); the keystore
  file itself lives **outside** the repository.
- The debug and release variants share the `applicationId` `com.selfguard`, so the Device Owner
  command in [DEVICE_OWNER.md](./DEVICE_OWNER.md) is identical for both. (An `applicationIdSuffix`
  would change the Device Admin component name and break `dpm set-device-owner`.)
- Sideload: `adb install -r app/build/outputs/apk/release/app-release.apk`.
- Versioning: `versionCode` increments per release; the version name is `x.y.z`.

## 16. Known limitations (accepted)

1. The Accessibility service can always be disabled by someone with ADB; the goal is friction, not
   unbreakability.
2. Blocking is host-based; a direct IP address or a proxy bypasses it.
3. Chrome's incognito window is a different URL-bar node; Phase 3 verifies and, if it is
   undetectable, documents the limitation rather than pretending it is solved.
4. Website blocking reads what is rendered; pages that load content without a URL change (e.g. a
   redirect through a shortener) are matched on the final URL only.
5. The `UsageStatsManager` fallback can lag by up to ~1.5 s; the Accessibility path closes that gap
   in practice.
6. The streak counts days, not hours; a single late-night relapse breaks a day.
