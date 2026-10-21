# SelfGuard — Product Requirements Document

| Field | Value |
|---|---|
| Product | SelfGuard (working name) |
| Version | 0.1.0 (skeleton) / PRD v1.0 |
| Platform | Native Android, Kotlin, Jetpack Compose |
| SDK | minSdk 26, targetSdk 30, compileSdk 36 |
| Device | Samsung Galaxy A20 (SM-A205F), Android 11 / One UI 3.1 |
| Distribution | Sideloaded APK only — never published to Google Play |
| Author | Single user (the developer). No other users, no test accounts. |

---

## 1. Problem statement

The device owner can browse adult websites, open apps that are easy to slip into, and quietly
re-enable any protection that gets in the way. A normal blocker app fails on three levels:

1. **It is easy to turn off.** Settings → Accessibility → toggle off, or long-press the icon → Uninstall.
2. **It does not remember.** Without a record of failures, the urge wins "because nothing happens".
3. **It has no friction.** There is no cost to giving in, so the decision stays a coin flip.

SelfGuard is a **personal, offline, single-device** self-control tool. It is designed around three
ideas:

- **Friction, not willpower.** Every bypass path is closed or made expensive.
- **Accountability, not secrecy.** Every attempt is logged, visible, and durable.
- **A deliberate exit, never a dead end.** Protection is strong but never irreversible: the owner
  always has a documented, non-destructive way out (cooldown or partner password).

## 2. Goal

> Make it structurally difficult to browse adult content or use self-defeating apps on this phone,
> while keeping the owner in control and able to release the device at any time without a factory
> reset.

### 2.1 Success criteria

| # | Criterion | Measure |
|---|---|---|
| G1 | Adult sites on the blocklist cannot be reached | 100 % blocked in the acceptance test matrix (docs/PHASES.md § Phase 10) |
| G2 | Blocklisted apps cannot be opened | 100 % blocked; blocked apps hidden/suspended when Device Owner is active |
| G3 | The Settings paths that disable protection are unreachable | Accessibility, App info, Force stop, Uninstall, Usage access, Battery optimisation all redirect |
| G4 | Disabling/uninstalling is not a 5-second operation | Requires cooldown (24–72 h) or the accountability partner's password |
| G5 | The owner can always exit safely | `clearDeviceOwnerApp()` release flow + ADB fallback, documented and tested |
| G6 | The phone stays usable | Dialer, emergency calls, IME, launcher, clock/alarm, app updates, and system settings never blocked |
| G7 | Battery impact is negligible | < 1 %/day extra drain when idle; polling only while the screen is on |
| G8 | Progress is visible | Daily stats, clean-day streak, best streak, tamper log |

### 2.2 Non-goals (explicitly out of scope)

- **Not a multi-device product.** No cloud sync, no cross-device enforcement.
- **Not a parental-control / MDM product.** It does not manage other users, does not do work profiles.
- **No network features.** No accounts, no analytics, no crash reporting, no remote kill switch.
  All data stays in the app's private storage; `allowBackup=false`.
- **No content filtering engine.** No AI classification, no image hashing, no TLS inspection.
  Matching is deterministic and local (exact hosts, subdomain rules, keyword lists).
- **No circumvention of OS security.** SelfGuard does not root, does not patch the framework,
  does not exploit any system vulnerability. It uses only documented APIs.
- **No self-harm / clinical features.** This is a habit tool, not a treatment. The panic screen
  includes a reminder to contact a person or a service if the urge is about self-harm.
- **No Play Store compliance.** `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, Device Admin and
  Device Owner are used without Play-policy justification — acceptable because the app is never
  published.

## 3. Users

**Primary user — the owner.** Uses the phone daily, mostly alone, in the evening when the urge is
strongest. Has tried and failed to quit using willpower alone. Comfortable with ADB (has already
run `adb devices` at least once) and with copying a command from a document into a terminal.

**Secondary actor — the accountability partner.** Does not use the app and never sees the device.
Holds a passphrase that can (a) skip a cooldown, (b) unlock protection-critical settings during a
cooldown, and (c) authorise release of Device Owner ownership. The app stores only a salted,
iterated hash of the passphrase — the partner never needs to visit the device.

**Tertiary actor — the person in the next room.** May pick up the phone. SelfGuard must not be
bypassable by a curious person who does not know the passphrase, but must not create a scenario
where a family member is locked out of the phone entirely.

## 4. Personas and key journeys

### J1 — Evening scroll (the main scenario)

Owner opens Chrome → types a blocked domain → the page never loads. The address bar is emptied,
the screen is sent Home, and SelfGuard shows a calm screen explaining which rule fired and how
long the current clean streak is. From there one tap opens the breathing exercise.

### J2 — App slip

Owner taps a blocked app (e.g. a feed app) → the app does not come up; SelfGuard shows the
blocked screen. If the app was hidden via Device Owner, the icon is simply not in the launcher.

### J3 — Attempt to disable protection

Owner goes to Settings → Accessibility to switch the service off. SelfGuard redirects to Home and
shows "Protection is on". If the owner instead goes to App info → Uninstall, or revokes Usage
access, the device locks, an alarm sounds for up to 30 seconds, a notification is posted, the
attempt is written to the tamper log, and a 48-hour cooldown begins.

### J4 — Legitimate exit

After the cooldown has expired (or immediately, with the partner passphrase), the owner opens
SelfGuard → Settings → "Release device protection", confirms, and SelfGuard calls
`clearDeviceOwnerApp()`. Device Admin is removed, the phone behaves normally again, and the
release is recorded in the statistics with its reason.

### J5 — Urge spike

Owner opens the SelfGuard dashboard and taps the panic button. A 4-7-8 or box breathing animation
runs for the configured duration (default 3 minutes) with haptic cues. Afterwards a short cool-down
window is applied during which blocked items stay blocked. The session is recorded.

## 5. Feature scope

Priority: **P0** = required for the product to work, **P1** = strongly desired, **P2** = nice to have.

| # | Feature | Priority | Summary |
|---|---|---|---|
| F1 | Website blocking | P0 | AccessibilityService reads the browser address bar / screen text, matches against host and keyword rules, then redirects Home and shows the block screen |
| F2 | App blocking | P0 | Foreground app detection (Accessibility events primary, UsageStats backup) blocks apps on the list; Device Owner additionally hides/suspends them |
| F3 | Custom blocklists | P0 | Editable lists of apps, websites (host rules) and keywords, with import/export |
| F4 | Protection durability | P0 | Device Admin + Device Owner, cooldown (24–72 h), accountability-partner passphrase, loud tamper alarm, safe release flow, no data wipe anywhere |
| F5 | Settings-screen blocking | P0 | Redirects the Settings pages that would let the owner disable the Accessibility service, uninstall SelfGuard, or revoke Usage access |
| F6 | Streak & statistics | P1 | Daily clean-day streak, best streak, block counts, tamper log, 30-day history, export |
| F7 | Panic button | P1 | Breathing exercise animation + timer, cool-down window, session logging |

### 5.1 Feature notes and decisions (agreed with the owner)

**F1 Website blocking.** Browsers in scope: Google Chrome, Samsung Internet, Firefox, and any
browser that exposes an editable address bar node. Matching is on the normalised host, plus
optional keyword matching against the URL and page title. The block action is:
empty/stop the page load → `GLOBAL_ACTION_HOME` → launch the SelfGuard block screen.

**F2 App blocking.** Two detection sources, in priority order: (1) `TYPE_WINDOW_STATE_CHANGED`
Accessibility events for instant detection; (2) a bounded `UsageStatsManager.queryEvents` poll
every ~1.5 s, running **only while the screen is on and the device is unlocked**. A hard allowlist
(launcher, dialer, emergency/telecom, IME, clock/alarm, SelfGuard, SystemUI) can never be blocked.

**F3 Custom blocklists.** Three editable lists, all stored in Room. Import/export as JSON and
plain text (one host or keyword per line) so a backup can be re-imported after a reinstall.

**F4 Protection durability.** Device Owner is the primary mechanism (set once via ADB), Device
Admin as the fallback layer. **No `wipe-data` policy is ever declared or called** — the decision
is final and is enforced by the device-admin XML containing only `<force-lock/>` and `<lock-now/>`.
Tamper response: lock the screen, play a loud alarm for at most 30 seconds (auto-stopping,
dismissible, never looping indefinitely), post a notification, log the attempt, start a
48-hour cooldown (configurable 24–72 h; changing that value is itself gated by the cooldown).
The cooldown only *restricts changes* — blocking stays fully active throughout.

**F5 Settings-screen blocking.** Redirect (Home + block screen) on the Settings screens related to
Accessibility, Installed apps, App info, Force stop, Uninstall, Usage access, Battery optimisation,
and Device admin apps. The redirect is **relaxed during onboarding** so the owner can enable the
Accessibility service the first time, and it re-arms only once the service is confirmed running.

**F6 Streak & statistics.** A *clean day* is a local calendar day with zero bypass events
(tamper attempt, override used, or abandoned panic session). Blocked attempts do not break a
streak — being blocked is a win. Today is always shown as *pending* until the day ends.

**F7 Panic button.** Box (4-4-4-4), 4-7-8, or 5-5 breathing, 1–10 minutes, animated circle with
haptics, reachable from the dashboard and from the block screen. After a session, a cool-down
window (default 15 min) applies during which blocked items stay blocked.

## 6. Constraints

| ID | Constraint | Consequence |
|---|---|---|
| C1 | Single device, Android 11 (API 30) | No Android 12+ APIs; `setApplicationHidden` is API 30+ so it *is* available; no `POST_NOTIFICATIONS` runtime permission needed |
| C2 | Sideloaded, not on Play | `QUERY_ALL_PACKAGES` and `PACKAGE_USAGE_STATS` are usable without Play justification |
| C3 | Device Owner can only be set with ADB | One-time setup step; documented in docs/DEVICE_OWNER.md |
| C4 | AccessibilityService can be disabled by the OS owner | Mitigated by F5 + cooldown + tamper logging; OS-level "cannot be disabled" does not exist |
| C5 | No network | Partner notification by SMS/e-mail is out of scope; accountability is passphrase-based |
| C6 | Battery | No wakelock; polling gated on screen state; the alarm is the only wake-up source and is capped at 30 s |
| C7 | Privacy | Everything local; `allowBackup=false`; no analytics; the app's own data is excluded from cloud backup |
| C8 | The owner must never be permanently locked out | Release flow (§ F4) + ADB fallback are mandatory, tested, and documented |

## 7. Risks and mitigations

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Samsung's "Sleeping apps"/task killer stops the AccessibilityService | Medium | High | Detect a dead service, show a persistent warning on the dashboard and a notification; `BootReceiver` re-arms state; DO can re-enable our own components |
| R2 | Owner removes the Google account, breaking `set-device-owner` | Medium | Medium | Document the exact error text and the remove → set → re-add sequence in docs/DEVICE_OWNER.md |
| R3 | Browser UI changes break URL extraction | Medium | Medium | Extraction is rule-based and configurable; the "scan for URL-shaped text" fallback is version-independent; a per-browser diagnostic screen dumps the last-seen node tree |
| R4 | Owner accidentally blocks the dialer and cannot call for help | Low | Critical | Hard allowlist that cannot be edited (launcher, telecom/dialer, emergency, IME, clock, SystemUI); a single "unblock everything" gesture requiring the partner passphrase |
| R5 | Cooldown makes the phone unusable | Low | High | The cooldown only gates *changes*; blocking behaviour never softens; the owner can always wait it out or use the passphrase |
| R6 | Protection causes data loss | Low | Critical | No wipe policy, no factory reset, no `wipe-data` capability, and no code path that calls any destructive DPM API |
| R7 | Blocking apps drains the battery | Low | Medium | Screen-gated polling, no wakelocks, hard cap on the alarm, documented power measurement in Phase 9 |
| R8 | The owner disables USB debugging and then genuinely needs ADB recovery | Low | Medium | The in-app release path is the primary exit; ADB is the fallback, not the only exit |
| R9 | SelfGuard blocks an app needed for a legitimate task (banking, maps) | Medium | Low | Per-rule enable/disable, and a "trusted for 30 minutes" option that does not touch protection-critical settings |

## 8. Release and success review

- **v0.1 (Phase 6)** — blocking engines + protection hardening, usable daily.
- **v0.2 (Phase 8)** — statistics, streaks, panic button; daily driver.
- **v1.0 (Phase 10)** — accepted on the Galaxy A20 after the full manual test matrix passes.

Success is judged by the streak, not by the block count: if the clean streak keeps growing, the
tool is doing its job.

## 9. Open questions

| # | Question | Default assumption until answered |
|---|---|---|
| Q1 | Should the accountability partner be able to see a *live* status, or only break a cooldown? | Passphrase-only (no network) |
| Q2 | Should the "why I started this" message be shown on the block screen, or only in the notification? | Both |
| Q3 | Should blocked apps be hidden (invisible in the launcher) or visible-but-blocked? | Hidden when Device Owner is active, visible-but-blocked otherwise |
| Q4 | Is a 48-hour default cooldown right, or should the first-tamper cooldown be longer? | 48 h, adjustable 24–72 h |
| Q5 | Should the app ever allow itself to be paused without the passphrase (e.g. for a trusted family member to use the phone)? | No pause in v1.0 |
