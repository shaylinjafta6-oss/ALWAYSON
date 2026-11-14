# ALWAYSON • Samsung Galaxy A16 v0.6

Experimental LSPosed module targeting Galaxy A16 SM-A165F, Android 17 / One UI 9. Preserve your working AODService 8.9.65.92 and rollback access.

## Finding from the user's original SystemUI APK

The **actual Samsung DozeService** contains two branches in `onCreate()`:

- If `PluginFaceWidgetManager.mIsConnected` is true, it directly registers a plugin listener with the Samsung `PluginManager` for either `PluginClockPack` or `PluginAOD`, according to `LsRune.LOCKUI_AOD_PACKAGE_AVAILABLE`.
- If FaceWidget is not connected, it enqueues `mPluginConnectionRunnable` in `PluginAODManager.mConnectionRunnableList`. This runnable registers that same listener later.
- `onDreamingStarted()` calls `startClockPack()` when `LOCKUI_AOD_PACKAGE_AVAILABLE` is true. It logs `mClockPackPlugin=null` when no plugin has connected, so the plugin's `onDreamingStarted` is skipped.
- `onRequestShowDoze()` explicitly requests `DozeMachine.State.DOZE_AOD`, but the user's logs only show `DOZE`.

The user has independently confirmed the ClockPack service action can be discovered, but has repeatedly observed `startClockPack: mClockPackPlugin=null`.

## Targeted trial in v0.6

**New:** `AodClockPackRegistrationHook` installs an after-hook for `DozeService.onCreate()` in the main SystemUI process.

After onCreate finishes, **only if** the exact stock `mPluginConnectionRunnable` is sitting in Samsung's deferred `mConnectionRunnableList` and the Doze machine/plugin manager exist, it executes Samsung's own registration runnable. On success it removes that callback from the deferred list to avoid a duplicate listener later. If any precondition fails, it leaves stock behavior unchanged.

This tests whether waiting for FaceWidget is the blocking dependency. It does not forge plugin objects, manipulate `DOZE_AOD` or screen states, change settings, SELinux, partitions, hardware or the AODService crash hook. It is experimental and could expose ClockPack/plugin compatibility errors.

All v0.1-v0.5 hooks are retained unchanged.

## Test

1. Install the new GitHub Actions build artifact `A16-AOD-Clock-Init-Test-APK` over v0.5.
2. In LSPosed, scope it to **Samsung AODService** and **System UI**.
3. Soft-restart the main SystemUI process (not a full reboot if root is temporary):

   ```sh
   su -c 'pid=$(pidof com.android.systemui); [ -n "$pid" ] && kill -TERM "$pid"'
   ```

4. Lock and unlock the phone, then run **one** check:

   ```sh
   su -c 'logcat -d -b all -v brief | grep -E "A16AODRegistration|startClockPack:|onPluginConnected: PluginClockPack" | tail -n 30'
   ```

Success for this test means a real ClockPack connection is logged or `startClockPack` reports a non-null plugin. A working AOD screen is a separate test, not guaranteed. The previously fabricated `config_dozeAlwaysOnDisplayAvailable` overlay may be false after reboot, but that is independent of this specific plugin registration test.

## Rollback

Disable the module's **System UI** scope in LSPosed and reload SystemUI to stop this experiment while preserving the **Samsung AODService** scope and crash fix. Alternatively install the previous v0.5 APK. No partitions are changed.
