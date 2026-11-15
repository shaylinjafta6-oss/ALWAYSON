# ALWAYSON v0.7 • one-shot experimental DOZE_AOD request

Galaxy A16 SM-A165F / One UI 9 / Android 17, donor AODService 8.9.65.92.

## Verified against user-uploaded SystemUI.apk

The device's own `com.android.systemui.doze.DozeService` has a public `onRequestShowDoze()` method. Its DEX code reads its `mDozeMachine` field (Samsung class `AODMachine` extending `DozeMachine`) and calls `DozeMachine.requestState(DozeMachine.State.DOZE_AOD)` when that field is not null. `DozeMachine.State.DOZE_AOD` and public `getState()` exist. Previous logs showed initial state `DOZE` even with availability overlay active.

This build adds the intentionally **one-shot, opt-in** `AodForceOnceHook`. It hooks **after** `DozeService.onDreamingStarted()` in the main SystemUI process. It does nothing until `settings put secure a16_force_doze_aod_once 1` is run manually. When the service reaches state `DOZE`, Samsung AOD is on, and battery saver is off, it **first resets that setting to 0** and **then invokes Samsung's real `onRequestShowDoze()`**. It logs the resulting DozeMachine state. Even if the requested state sticks, the hardware may still not render AOD; no rendering support is claimed.

**The speculative v0.6 delayed ClockPack registration hook was removed from `xposed_init`** to avoid confounding the experiment. Existing read-only diagnostics and the original working AODService clock-init crash workaround are unchanged.

## Install and run

1. Download the successful GitHub Actions artifact `A16-AOD-Clock-Init-Test-APK` and install the APK over v0.6 or v0.5.
2. LSPosed scope: `System UI` and `Samsung AODService`, as before.
3. Soft restart SystemUI (avoid a full reboot if temporary root matters):

   ```sh
   su -c 'pid=$(pidof com.android.systemui); [ -n "$pid" ] && kill -TERM "$pid"'
   ```

4. Once SystemUI recovers, arm the one-shot test:

   ```sh
   su -c 'settings put secure a16_force_doze_aod_once 1'
   ```

5. Lock for 15 seconds; unlock. Capture the result:

   ```sh
   su -c 'logcat -d -b all -v brief | grep -E "A16AODForce|DozeMachine.*transition" | tail -n 30'
   ```

**Do not repeat the trigger automatically.** If a crash, frozen lock screen or blank display follows, use the normal wake/unlock or normal restart recovery method. The trigger is disarmed before request and won't repeat on its own after restart.

## Rollback

To disarm explicitly: `su -c 'settings put secure a16_force_doze_aod_once 0'`. Deselect **System UI** in LSPosed and restart the SystemUI process to turn off the test while retaining the AODService crash fix. Alternatively reinstall v0.5. No kernel settings, firmware partitions, brightness values, SELinux, display drivers or persistent system files are changed.
