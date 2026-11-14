# ALWAYSON • Galaxy A16 AOD Diagnostics v0.5

Experimental LSPosed module for Galaxy A16 SM-A165F, One UI 9 / Android 17.

## Current findings

- Donor AODService 8.9.65.92's clock-list crash is addressed by the **unchanged** AODService-only hook (first added in v0.1).
- SystemUI's `DozeParameters.getAlwaysOn()` changes to `true` when the fabricated `config_dozeAlwaysOnDisplayAvailable` overlay is active.
- **Even when this returns true**, main SystemUI still explicitly requests `DOZE` rather than `DOZE_AOD`.
- The firmware's `DozeMachine` has no hookable `resolveIntermediateState` or `transitionPolicy` methods with those names; its `mAODUi` and `mAODScreenBrightness` were null. This is not proof hardware cannot support AOD.

## What's new

**v0.5 adds a strictly read-only `A16AODOrigin` hook** in *main SystemUI*:
- Capture the SystemUI caller stack for the first two ordinary `DOZE` transition requests.
- List the classes of installed `DozeMachine.mParts`, if accessible.
- Does **not** change methods, return values, transition requests, system settings, display modes, ClockPack or SELinux.

Existing v0.4 `A16AODTrace` and v0.2 `A16AODEligibility` remain, as does the AODService crash workaround. The package ID is unchanged.

## Install / capture

1. Keep the working donor AODService 8.9.65.92 and its supporting modules mounted; keep old donor `shalomp_aod_16` and `fullaodenable` disabled.
2. Download the successful GitHub Actions artifact named `A16-AOD-Clock-Init-Test-APK` and install over v0.4.
3. In LSPosed Manager, enable this module for **Samsung AODService** and **System UI** only.
4. Before restarting SystemUI, confirm the overlay is active (`cmd overlay lookup android android:bool/config_dozeAlwaysOnDisplayAvailable` should return `true`). If root or the overlay does not survive restart, restore those separately; don't assume they persisted.
5. Reload the main SystemUI process via the recovery method already tested on the device, rather than performing an unnecessary full reboot. Then lock and unlock the phone once.
6. In Termux:

   ```sh
   su -c 'logcat -d -b all -v brief | grep A16AODOrigin | tail -n 55'
   ```

7. Expect the `DOZE caller[n]` entries and `part[n]` classes. No call stack means the hook failed, the process didn't reload or `DOZE` wasn't requested in the captured period.

## Rollback

Disable System UI in the LSPosed scope and reload that process to stop the diagnostic hooks while keeping the AODService crash fix enabled. Alternatively reinstall the previously working v0.4 APK. No partitions are modified.
