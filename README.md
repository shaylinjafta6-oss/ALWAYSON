# ALWAYSON v0.13: optional faster transition from lock screen to AOD

**Experimental.** This module preserves the Galaxy A16 SM-A165F One UI 9's already-working native Always On Display configuration from v0.12.

## Problem

Native AOD is visible with v0.12, but the user reports about 1 to 3 seconds of black screen after pressing lock. Earlier on-device testing with v0.7 proved that a call to Samsung's genuine `DozeService.onRequestShowDoze()` causes the machine to enter `DOZE_AOD`; v0.12 subsequently fixed the native clock list and plugin registration. The plugin's normal asynchronous AOD request may arrive later than the start of the doze dream. We have not yet proven this is the cause of the 1–3s delay.

## New opt-in test

Add `AodFastStartHook` to the main SystemUI process. When secure setting `a16_fast_aod_start=1`, it observes `DozeService.onDreamingStarted()`. **After** the normal method has executed and started the real `PluginAODImpl`, it checks:
- Samsung AOD setting is enabled and battery saver is off;
- Samsung native `mAODPlugin` has connected;
- the real Samsung Doze machine is currently in `DOZE` and not already in `DOZE_AOD`.

Only then it calls Samsung's existing `onRequestShowDoze()` immediately, instead of waiting for the plugin to request it asynchronously. It logs the call and resulting state. This does not turn off the screen manually, force panel brightness or kernel states, bypass hardware safeguards, or alter v0.12 clock-list repair.

This may reduce perceived lock-to-AOD delay, have no noticeable effect, or cause a transient display issue on an unsupported device. **Zero delay is not guaranteed** due to display hardware state changes and compositor/clock rendering. Leave this mode OFF unless explicitly testing.

## Single test

1. Keep your working v0.12 APK available as rollback. Install the v0.13 GitHub Actions debug APK over v0.12 (same package signature and ID), with the same LSPosed scopes: System UI and Samsung AODService. Keep the ClockPack duplicate FaceWidget provider disabled.
2. Arm the opt-in early start, disarm the obsolete one-shot forcing and database repair, keep the native plugin route enabled, and soft-restart SystemUI:

```sh
su -c 'settings put secure a16_fast_aod_start 1; settings put secure a16_select_native_aod_plugin 1; settings put secure a16_force_doze_aod_once 0; settings put secure a16_repair_aod_schema_once 0; pid=$(pidof com.android.systemui); [ -n "$pid" ] && kill -TERM "$pid"'
```

3. Lock and unlock normally. Assess whether AOD appears faster or the lock screen behaves worse. Check the new log line only if necessary:

```sh
su -c 'logcat -d -b all -v brief | grep A16AODFastStart | tail -n 12'
```

## Disable the fast experiment instantly

```sh
su -c 'settings put secure a16_fast_aod_start 0'
```

This returns to native v0.12 transition behavior at the next lock (the module reads this setting on every dream). No SystemUI restart is needed to disable fast start. For full rollback, reinstall the previous v0.12 APK and soft-restart SystemUI. Avoid a full device reboot with temporary DirtyFrag root.

Do not run if AOD is currently malfunctioning. If screen-off locking becomes unreliable, turn the setting off and use the working v0.12 configuration.
