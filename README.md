# ALWAYSON v0.8 • ClockPack registration + opt-in DOZE_AOD

Experimental LSPosed module for Galaxy A16 SM-A165F, One UI 9 / Android 17 with AODService 8.9.65.92.

## What was learned from logs

The **one-shot v0.7** experiment succeeded:
- `DozeMachine: old=DOZE req=DOZE_AOD new=DOZE_AOD`
- `DozeScreenState: applyScreenState(4)`
- `DreamService: mDozeScreenState=4 mDozeScreenBrightness=-1.0`

Android's display state **4 is STATE_DOZE_SUSPEND**. This is a display-suspending doze state, which preserves existing on-screen content but may prevent fresh display updates. Brightness -1 means system default, *not* zero. The phone stayed visually black. Earlier Samsung logs said `startClockPack: mClockPackPlugin=null`.

## What changes in v0.8

- **Re-enable** `AodClockPackRegistrationHook` from v0.6 alongside the proven v0.7 one-shot AOD-state request.
- The registration hook does *not* forge ClockPack or force a screen state. After Samsung's `DozeService.onCreate()`, it only executes Samsung's **own queued connection-listener registration callback**, if that callback really exists in the vendor's deferred `PluginAODManager.mConnectionRunnableList`. It removes that callback from the queue afterward to avoid double-registration. If conditions are unmet, it takes no action.
- Keep the v0.7 `AodForceOnceHook` unchanged. It only requests `DOZE_AOD` when the explicit secure trigger is set, and clears that trigger before trying.
- Keep the original AODService crash-fix hook and existing read-only diagnostics unchanged.
- Does **not** modify brightness, change kernel/vendor settings, force display state 3, or write to system partitions.

This is an **experimental combined test**, not proof of a native working AOD. Plugin initialization may still fail or the display may stay blank. Stop if UI stability worsens.

## Install

1. Download the GitHub Actions artifact `A16-AOD-Clock-Init-Test-APK`; extract and install over the existing ALWAYSON app.
2. Keep LSPosed scopes **System UI** and **Samsung AODService** enabled.
3. Soft-restart SystemUI, not the whole phone if root is temporary:

   ```sh
   su -c 'pid=$(pidof com.android.systemui); [ -n "$pid" ] && kill -TERM "$pid"'
   ```

4. Lock/unlock once to check ClockPack registration; then arm the explicit one-shot AOD test if SystemUI remains stable:

   ```sh
   su -c 'settings put secure a16_force_doze_aod_once 1'
   ```

5. Lock for 15 seconds, unlock. Inspect the **one** filtered log:

   ```sh
   su -c 'logcat -d -b all -v brief | grep -E "A16AODRegistration|A16AODForce|startClockPack:|onPluginConnected.*ClockPack|DozeScreenState.*applyScreenState" | tail -n 50'
   ```

The user already set two ClockPack components back to `default`. **Do not disable ClockPack again** for this experiment.

## Rollback / safety

Disarm the one-shot trigger: `su -c 'settings put secure a16_force_doze_aod_once 0'`. If lock screen or UI acts strangely, turn off **System UI** scope for this module and soft-restart SystemUI; the separate Samsung AODService hook can remain enabled. Or reinstall v0.7. Never keep forcing DOZE_AOD if it destabilizes the display.
