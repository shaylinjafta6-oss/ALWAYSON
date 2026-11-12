# ALWAYSON • Galaxy A16 AOD Diagnostics v0.2

Experimental **LSPosed module** for a Samsung Galaxy A16 SM-A165F (Android 17 / One UI 9).

## What's included

- **Original AODService hook (unchanged):** For donor AODService 8.9.65.92, temporarily changes `aod.ha6.w` during `aod.qr0.j(Context)` clock-list initialization, then restores its previous value. This prevented the previously observed startup crash, allowing AOD settings to open. Do not assume it enables actual display rendering.
- **New SystemUI diagnostic hook (read-only):** Observes `AmbientDisplayConfiguration.alwaysOnAvailable()` and `alwaysOnEnabled(int)`, logging returned values to Android logcat under tag `A16AODEligibility` and LSPosed logs. The two known framework class paths are tried. No return values, AOD settings, or display states are modified.

The device still entered `DOZE` rather than `DOZE_AOD` before this diagnostic build. This module seeks to determine why, not force the transition.

## Build

GitHub Actions automatically builds a debug APK on pushes to `main`. Download the `A16-AOD-Clock-Init-Test-APK` artifact from the latest successful **Build A16 AOD Clock Init LSPosed Test** run. Local build: `gradle :app:assembleDebug` (JDK 17, Gradle 8.10.2).

## Install and capture

1. Keep the known-working donor AODService installation and existing rollback method. Leave old `shalomp_aod_16` and `fullaodenable` disabled.
2. Install the updated debug APK over v0.1; the application ID is unchanged (`dev.a16.aodclocktest`).
3. In **LSPosed Manager**, enable this module and select **both** scopes: `com.samsung.android.app.aodservice` and **System UI** (`com.android.systemui`). Do not scope it broadly to other apps. LSPosed must inject into SystemUI to log its results.
4. Reload the scoped processes using the restart method already proven safe on this device. If SystemUI cannot be safely reloaded, reboot normally instead. The temporary AOD overlay and temp root may need re-establishment after reboot.
5. Lock the phone, unlock it, then run:

   ```sh
   su -c 'logcat -d -v time -s A16AODEligibility:I'
   ```

   Or check LSPosed logs for `A16AODEligibility`. Do not publish unrelated log lines that could contain private data.

6. Expected messages include `hooked ...alwaysOnEnabled(int)` and `alwaysOnEnabled(user=0) => true/false`. If only `hooked` appears, the method may not have been called after module load, or Samsung may use a different decision path. If `hookedMethods=0`, the targeted classes or method signatures differ on this firmware.

## Interpretation

- `alwaysOnAvailable() => false`: Resource availability or framework eligibility still fails in the SystemUI process.
- `alwaysOnEnabled(user=...) => false`: The Android-framework eligibility decision is blocking AOD.
- Both return `true` and DozeMachine still chooses `DOZE`: Investigate Samsung-specific DozeMachine logic; no proof yet.
- Missing results are inconclusive; check SystemUI scope and log entries first.

No live-device validation of the v0.2 build is claimed.

## Rollback

In LSPosed, deselect the **System UI** scope to remove diagnostic hooks; retain the AODService scope for the original crash fix. Alternatively disable the whole module, then restart affected processes. This APK does not modify SELinux, ClockPack, firmware partitions, or the display driver.
