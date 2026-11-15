# ALWAYSON v0.9: Native AODService plugin selection

For Galaxy A16 SM-A165F One UI 9 / Android 17 (SystemUI.apk inspected). Experimental LSPosed module.

## Verified in user's actual SystemUI.dex

`DozeService.onCreate()` reads `com.android.systemui.LsRune.LOCKUI_AOD_PACKAGE_AVAILABLE` to select the native AODService plugin vs ClockPack while setting up its plugin listener. `DozeService.onDreamingStarted()` reads that exact field again to select the corresponding wake/doze path.

- AODService exports `com.samsung.android.app.aodservice/.plugin.PluginAODImpl` for **`com.android.systemui.action.PLUGIN_AOD`**, confirmed by `pm query-services`.
- After disabling ClockPack's duplicate FaceWidget component with `pm disable --user 0`, SystemUI finds only AODService's FaceWidget provider, and connects it.
- Yet `DozeService` was still connecting `PluginClockPackImpl`, consistent with the stock device taking the ClockPack branch.

## v0.9 targeted test (opt-in)

New `AodPluginSelectionHook` only activates if `settings get secure a16_select_native_aod_plugin` is 1. It temporarily sets the static `LsRune.LOCKUI_AOD_PACKAGE_AVAILABLE` boolean to true around Samsung's own `DozeService.onCreate()` and `onDreamingStarted()`, and restores its previous value in after-hook. This should make Samsung register the native `PluginAODImpl` listener and use the AOD path during dreaming.

The existing guarded listener-registration callback hook from v0.8 is preserved. Xposed init entry order intentionally installs the new selector hook **before** that callback hook, so the latter's `afterHookedMethod` observes the temporarily selected native AOD path. Existing AODService clock-list crash workaround is unchanged. The old v0.7 force-DOZE_AOD hook remains present but **inactive unless separately armed**.

This does **not** flash firmware, modify vendor display or brightness state, write overlay files, or globally retain the flag. Reflection may reject mutation of the final flag on some builds; in that case it logs an error and stock behavior should remain. Native AOD rendering is **not guaranteed**.

## Test

1. Keep the already confirmed conflict isolated: ClockPack's `com.samsung.android.app.facewidget.plugin.PluginKeyguardStatusViewImpl` component is **disabled** (not the ClockPack app).
2. Install this build's `A16-AOD-Clock-Init-Test-APK` over v0.8; keep LSPosed scopes **System UI** and **Samsung AODService**.
3. Arm the **plugin path** switch only. Disarm the older one-shot force test:

   ```sh
   su -c 'settings put secure a16_force_doze_aod_once 0; settings put secure a16_select_native_aod_plugin 1'
   ```

4. Soft-restart SystemUI to make it register the native plugin:

   ```sh
   su -c 'pid=$(pidof com.android.systemui); [ -n "$pid" ] && kill -TERM "$pid"'
   ```

5. Lock the screen and observe for about 15 seconds. Unlock and inspect one filtered log:

   ```sh
   su -c 'logcat -d -b all -v brief | grep -E "A16AODPluginPath|A16AODRegistration|PluginAODImpl|onPluginConnected: PluginAOD|DozeMachine.*transition|DozeScreenState.*applyScreenState" | tail -n 50'
   ```

Success for the plugin-selection test is SystemUI connecting the actual native `PluginAODImpl`, not merely Package Manager listing it. Actual visible AOD must be separately confirmed.

## Rollback

```sh
su -c 'settings put secure a16_select_native_aod_plugin 0; settings put secure a16_force_doze_aod_once 0'
su -c 'pid=$(pidof com.android.systemui); [ -n "$pid" ] && kill -TERM "$pid"'
```

If SystemUI becomes unstable, deselect **System UI** for ALWAYSON in LSPosed before soft restart, and keep AODService scope if desired. You can reinstall v0.8. This project avoids reboot since temporary root may disappear on reboot.
