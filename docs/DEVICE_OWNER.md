# SelfGuard — Device Owner Setup & Release Guide

Everything in this document is **opt-in, reversible, and non-destructive**. Nothing here wipes the
phone. Read §7 (release) before you start.

- **What Device Owner gives SelfGuard:** the ability to suspend or hide blocked apps, lock the
  screen, and — crucially — to be *un-installable by normal means*. The system refuses to uninstall
  a device owner, so the "5-second uninstall" hole disappears.
- **What it costs:** one ADB command, once, plus removing your Google account for a minute.

---

## 1. Prerequisites

| Requirement | How to check |
|---|---|
| `adb` on the computer | `adb version` |
| USB debugging enabled on the phone | Settings → About phone → tap "Build number" 7× → Developer options → USB debugging |
| The phone is connected and authorised | `adb devices` → the device shows as `device`, not `unauthorized` |
| **No Google/other accounts on the phone** | Settings → Accounts → remove them temporarily (§4.1) |
| **Only one user on the device** | Settings → Accounts → "Add account" is different; check for a "Second screen"/"Secure folder" user — `adb shell pm list users` |
| No other Device Owner / MDM already installed | `adb shell dpm list-owners` |

> **Do this before you add accounts to the phone.** Android refuses to promote an app to Device
> Owner while any account exists on the device. Removing and re-adding a Google account is
> harmless, but on some Samsung builds the account re-add triggers a re-sync you will have to
> wait for.

## 2. Order of operations (important)

```
1. Install the APK
2. Enable the Accessibility service          ← manual, in Settings
3. Grant Usage access                        ← manual, in Settings
4. Set Device Owner via ADB                  ← terminal
5. Verify with adb                           ← terminal
6. Arm protection inside SelfGuard           ← in the app
```

Steps 2 and 3 must happen **before** step 6, because arming protection blocks the Settings
screens that would let you change your mind. The onboarding flow enforces this order and relaxes
the Settings shield until the Accessibility service reports that it is connected.

## 3. Step-by-step

### Step 1 — Build and install

```bash
cd ~/projects/SelfGuard
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Confirm the install:

```bash
adb shell pm list packages | grep selfguard
# package:com.selfguard
```

### Step 2 — Enable the Accessibility service (manual)

On the phone: **Settings → Accessibility → Installed apps (or Downloaded apps) → SelfGuard
Protection → enable.**

SelfGuard shows a system dialog; accept it. The service name is *SelfGuard Protection*.

Verify:

```bash
adb shell settings get secure enabled_accessibility_services
# com.selfguard/com.selfguard.service.accessibility.SelfGuardAccessibilityService
```

### Step 3 — Grant Usage access (manual)

On the phone: **Settings → Apps → Special app access → Usage access → SelfGuard → allow.**

SelfGuard's onboarding screen links straight to this page.

Verify:

```bash
adb shell appops get com.selfguard GET_USAGE_STATS
# ... allow
```

### Step 4 — Set Device Owner (ADB)

```bash
adb shell dpm set-device-owner com.selfguard/com.selfguard.service.deviceadmin.SelfGuardDeviceAdminReceiver
```

Expected output:

```
Success: set-device-owner com.selfguard/com.selfguard.service.deviceadmin.SelfGuardDeviceAdminReceiver
```

The short form works too:

```bash
adb shell dpm set-device-owner com.selfguard/.service.deviceadmin.SelfGuardDeviceAdminReceiver
```

> The component name **must** match exactly: `com.selfguard` is the application id and
> `.service.deviceadmin.SelfGuardDeviceAdminReceiver` is the receiver class. The debug and release
> builds deliberately share the same application id so this command is identical for both.

### Step 5 — Verify

```bash
adb shell dpm list-owners
# Device owner: com.selfguard (test)

adb shell dpm list-active-admins
# Active admin: com.selfguard/com.selfguard.service.deviceadmin.SelfGuardDeviceAdminReceiver
```

In the app: **Settings → Protection** should now show *Device Owner: active* and offer the
Device-Owner-only actions (suspend / hide blocked apps, release protection).

### Step 6 — Arm protection

In the app: complete onboarding → set your accountability passphrase → add your first blocklists →
turn on **Protection**.

From this point:

- blocked apps can be suspended/hidden,
- Settings → Accessibility, App info, Force stop, Uninstall, Usage access and Device admin apps
  redirect to the SelfGuard block screen,
- tampering locks the screen, sounds the alarm, and starts the cooldown.

## 4. When `set-device-owner` fails

### 4.1 "Not allowed to set the device owner because there are already some accounts on the device"

The account restriction. Fix:

1. On the phone: Settings → Accounts → remove the Google account (and any other account).
2. Run the command again.
3. Re-add the accounts afterwards (this works on Android 11 while a device owner is set).

### 4.2 "Not allowed to set the device owner because there are already several users on the device"

A second user or a Samsung "Secure Folder"/"Second screen" exists.

```bash
adb shell pm list users
```

Remove the extra user in Settings, or remove a work profile, then retry. On a Secure Folder, delete
the folder (Settings → Biometrics and Security → Secure Folder → Delete).

### 4.3 "Trying to set the device owner but device owner is already set"

Something else is the owner (or a previous SelfGuard install is).

```bash
adb shell dpm list-owners
adb shell dpm clear-device-owner com.selfguard     # if SelfGuard is the owner
```

If a *different* app is the owner, uninstall it first (or factory-reset; on a personal device that
is the honest answer).

### 4.4 "Cannot set the device owner if the device is already provisioned"

Android 11 sometimes refuses after the device has been fully set up. Workarounds, in order of
preference:

1. Remove all accounts (§4.1) and retry.
2. Remove all secondary users (§4.2) and retry.
3. Factory-reset the device and set Device Owner **before** adding accounts. This destroys local
   data — it is a last resort, and it is the one path in this document that is destructive, which
   is why it is not part of the normal flow.

### 4.5 "Class com.selfguard... is not a valid device admin" / "Invalid component"

The receiver is not declared correctly. Check:

```bash
adb shell dumpsys package com.selfguard | grep -i -A4 "Device Admin\|admin"
```

Requirements: the receiver must be exported, carry the `android.permission.BIND_DEVICE_ADMIN`
permission, declare the `android.app.action.DEVICE_ADMIN_ENABLED` intent filter, and point
`android.app.device_admin` at `res/xml/device_admin.xml`. The skeleton in this repository already
does all of that.

### 4.6 "java.lang.SecurityException: Neither user 2000 nor current process has android.permission.USE_DEBUG_POLICY"

The phone's screen must be unlocked and on the Home screen when you run the command, and USB
debugging must be on. Unlock the phone and retry.

### 4.7 The command hangs or the phone restarts the adb server

Some Samsung builds are picky. Retry with the device plugged in, screen on, and:

```bash
adb kill-server && adb start-server && adb devices
```

## 5. Device-Owner capabilities used by SelfGuard

| Capability | API | Effect |
|---|---|---|
| `setUninstallBlocked` | 21 | The system refuses `adb uninstall` and long-press uninstall while SelfGuard is the Device Owner; toggled automatically when protection is armed/released |
| `setPackagesSuspended` | 23 | Blocked apps cannot launch and disappear from the launcher while suspended (Phase 5) |
| `setApplicationHidden` | 30 | Optional: hide a blocked app from the launcher and app drawer (Phase 5) |
| `lockNow()` | 21 | Lock the screen on a tamper attempt (Phase 5) |
| `clearDeviceOwnerApp()` | 21 | The release flow (§7) |
| `isDeviceOwnerApp()` | 21 | Detect DO mode |

**Plain Device Admin vs Device Owner:** activating SelfGuard as a Device Admin in Settings only
adds `lockNow()`. Every DO-only call above — including **uninstall blocking** — checks
`isDeviceOwnerApp()` first and is skipped when the role is missing, so the Settings screen can
show the honest state (*Device Owner: No*) instead of pretending. The in-app Settings → Protection
card shows the `set-device-owner` command with a copy button for exactly this reason.

**Explicitly not used, ever:** `wipeData`, `wipeDataLocked`, `resetPassword`,
`setPasswordQuality`, `setStorageEncryptionStatus`. The device-admin policy file
(`app/src/main/res/xml/device_admin.xml`) declares only `<force-lock/>` and `<lock-now/>`; there is
no `<wipe-data/>` element, and the app contains no code that could call a destructive DPM method.

## 6. Recovering if the Accessibility service is disabled or the app misbehaves

While protection is **armed**, SelfGuard runs a small watchdog service (a persistent low-priority
notification). It watches the secure accessibility-services setting and calls
`AccessibilityManager.enableSelf()` when it sees the service switched off — on stock Android the
service comes back within a second or two. This is hidden API used by reflection and is
best-effort: some OEM skins ignore it. The manual paths below always work.

| Situation | Fix |
|---|---|
| Accessibility service stopped (unarmed) | Settings → Accessibility → Installed apps → SelfGuard Protection → enable |
| Accessibility service stopped *and* protection armed | Wait for the watchdog; or Developer options → Accessibility services; or ADB: `adb shell settings put secure enabled_accessibility_services com.selfguard/com.selfguard.service.accessibility.SelfGuardAccessibilityService && adb shell settings put secure accessibility_enabled 1` |
| Usage access revoked | Settings → Apps → Special app access → Usage access → SelfGuard → allow |
| The phone is locked by `lockNow()` | Unlock with your normal PIN/pattern — SelfGuard never changes the lock-screen password |
| The app is stuck in a crash loop | `adb shell am force-stop com.selfguard`, then launch it from the launcher |
| Everything is broken and the app will not start | §7.2 (ADB release) — it works even if the app cannot run |

**Note on recovery by design:** the Developer-options screen is *not* shielded, and USB debugging is
never disabled by SelfGuard (FR-2.11, FR-4.18). A full recovery path always exists.

## 7. Release flow — how to get your phone back

There are two ways out. Both are permanent and both are tested in Phase 10.

### 7.1 In-app release (primary)

**SelfGuard → Settings → Protection → switch "Settings shield armed" off.**

Release is never instant (FR-4.7 / FR-4.8):

- **Cooldown path** — a 24-hour cooldown starts at the moment you request the release. The
  shield stays armed for the whole cooldown. When the 24 hours are over, tapping release again
  completes it.
- **Passphrase path** — if an accountability passphrase is set, entering it releases immediately
  and skips the remaining cooldown. Five wrong attempts lock the passphrase for 15 minutes.

After the release completes, SelfGuard:

1. turns the settings shield off and stops the watchdog,
2. clears `setUninstallBlocked` and calls `DevicePolicyManager.clearDeviceOwnerApp("com.selfguard")`
   when the Device Owner role is held (nothing to clear otherwise),
3. logs the release (timestamp and whether the passphrase was used),
4. leaves the app installed and fully usable in "unprotected" mode.

Afterwards the app can be uninstalled normally:

```bash
adb uninstall com.selfguard
```

or by long-pressing its launcher icon.

### 7.2 ADB release (fallback — works even if the app is broken)

```bash
adb shell dpm clear-device-owner com.selfguard
adb shell dpm remove-active-admin --user current com.selfguard/com.selfguard.service.deviceadmin.SelfGuardDeviceAdminReceiver
adb uninstall com.selfguard
```

Verify:

```bash
adb shell dpm list-owners     # empty
adb shell dpm list-active-admins
```

### 7.3 Resetting for testing

To re-run the whole flow (e.g. after changing the admin receiver):

```bash
adb shell dpm clear-device-owner com.selfguard
adb shell dpm remove-active-admin --user current com.selfguard/com.selfguard.service.deviceadmin.SelfGuardDeviceAdminReceiver
adb uninstall com.selfguard
adb shell dpm list-owners      # must be empty before the next set-device-owner
```

## 8. Quick reference

```bash
# status
adb shell dpm list-owners
adb shell dpm list-active-admins
adb shell settings get secure enabled_accessibility_services
adb shell appops get com.selfguard GET_USAGE_STATS

# promote
adb shell dpm set-device-owner com.selfguard/com.selfguard.service.deviceadmin.SelfGuardDeviceAdminReceiver

# demote
adb shell dpm clear-device-owner com.selfguard

# re-enable the accessibility service remotely
adb shell settings put secure enabled_accessibility_services com.selfguard/com.selfguard.service.accessibility.SelfGuardAccessibilityService
adb shell settings put secure accessibility_enabled 1

# watch what the service sees
adb logcat -s SelfGuard:V
```

## 9. Android 11 and OEM limits (honest list)

The target device is a Galaxy A20 on Android 11 / One UI 3; development also happens on Android 14
skins. What does and does not work is stated here so nothing is a surprise:

1. **A plain Device Admin cannot block uninstall.** `setUninstallBlocked` requires the Device
   Owner role. Without it SelfGuard still arms the settings shield, but `adb uninstall` (or
   long-press uninstall) succeeds. This is an Android rule, not an app limitation.
2. **`dpm set-device-owner` needs a "clean" device:** no accounts, no secondary users, no other
   owner (§4). On an already-provisioned Android 11 phone it may refuse outright; the only
   guaranteed path then is a factory reset with DO set first (§4.4), which is why this guide
   exists.
3. **The settings shield is best-effort.** It matches AOSP activity class names
   (`AccessibilitySettingsActivity`, `DeviceAdminSettingsActivity`, `InstalledAppDetailsActivity`)
   plus an exact visible-heading fallback. OEMs (One UI, ColorOS, Funtouch …) sometimes rename or
   nest these activities; an unrecognised screen is not blocked. The ADB paths in §6/§7 always
   remain as the recovery net.
4. **`enableSelf()` self-recovery is hidden API** (reflection on the greylist). It works on stock
   Android; aggressive OEM battery managers may still kill or ignore it. The boot receiver
   restarts the watchdog after every reboot regardless.
5. **Force-stop cannot be self-healed.** If the user force-stops SelfGuard from app info (possible
   while the shield is off), only a manual launch or a reboot restarts it. The shield's job is to
   make reaching that button take a cooldown's worth of friction.
6. **Background-start restrictions:** Android 12+ limits starting foreground services from the
   background; SelfGuard only starts its watchdog from `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`
   (both exempt) and from the arming action (user-present). Android 11 has no such restriction.
7. **`LOCK_NOW`/`FORCE-LOCK` only** — SelfGuard never changes or removes the lock-screen
   credential, and there is no wipe path anywhere in the app.
