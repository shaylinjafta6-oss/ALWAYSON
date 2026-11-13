# ALWAYSON • Galaxy A16 AOD Diagnostics v0.3

Experimental LSPosed module targeting **Samsung Galaxy A16 SM-A165F**, One UI 9 / Android 17.

## Features

- **v0.1 AODService clock-list initialization fix (unchanged):** Retains the working scoped hook for donor AODService 8.9.65.92.
- **v0.2 AOD eligibility diagnostics (unchanged):** Read-only tracing of `AmbientDisplayConfiguration.alwaysOnAvailable()` and `alwaysOnEnabled(int)` in SystemUI.
- **v0.3 DozeMachine diagnostics (new):** Read-only tracing of `DozeMachine.resolveIntermediateState`, `transitionTo`, `transitionPolicy`, and `DozeParameters.getAlwaysOn` (where available). Events are logged under `A16AODTrace` for the **main SystemUI process only**. Do not force or replace any return value or screen state.

The version 0.2 hooks loaded correctly into the main SystemUI process but produced no method-call results when the screen was locked. v0.3 observes the directly relevant DozeMachine transition path instead.

## Build

This repository builds on GitHub Actions on pushes to `main`. Download `A16-AOD-Clock-Init-Test-APK` from the latest successful run. The artifact name is unchanged for compatibility with the workflow.

## Install and test

1. Keep old `shalomp_aod_16` and `fullaodenable` disabled. Preserve rollback access.
2. Install v0.3 APK over v0.2; the application ID is unchanged.
3. In LSPosed Manager, enable **A16 AOD Clock Init Test** with scopes **com.samsung.android.app.aodservice** and **System UI** (`com.android.systemui`) only.
4. Reload main SystemUI using a method already proven safe on the device. The previous test restarted only the main SystemUI process via a TERM signal and it respawned without a full device reboot. Avoid doing this repeatedly if the interface becomes unstable.
5. Lock the phone for 10-15 seconds, then unlock.
6. Run:

   ```sh
   su -c 'logcat -d -b all -v brief | grep "A16AODTrace" | tail -n 60'
   ```

   The key values to inspect are `DozeParameters.getAlwaysOn() result=...`, `transitionTo requested=...`, and `transitionPolicy requested=... result=...`.

## Reading results

- `DozeParameters.getAlwaysOn() result=false`: An AOD gate returns false even if configuration flags are true.
- `transitionTo requested=DOZE` and no `DOZE_AOD` request: machine chooses ordinary Doze before displaying.
- `transitionPolicy requested=DOZE_AOD result=DOZE`: machine policy suppresses or rewrites AOD; investigate why.
- No `A16AODTrace` after the main process reload: confirm LSPosed scope and whether methods exist on this Samsung firmware.

The hook prints successful install counts and does not change any behavior. No live-device validation of v0.3 is claimed.

## Rollback

Deselect System UI scope and reload SystemUI to remove diagnostic hooks while preserving the AODService fix. Or disable the module entirely and reload both relevant processes. No firmware, display driver, ClockPack, or SELinux changes are made.
