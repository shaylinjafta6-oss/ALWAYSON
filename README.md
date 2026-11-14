# ALWAYSON • Galaxy A16 AOD Diagnostics v0.4

Experimental LSPosed module targeting Samsung Galaxy A16 SM-A165F with One UI 9 / Android 17.

## Modules

- **AODService clock-list crash workaround:** Existing v0.1 hook remains unchanged and only runs in `com.samsung.android.app.aodservice`, for donor AODService 8.9.65.92.
- **Android AOD eligibility hook:** Existing v0.2 read-only hooks remain, scoped to SystemUI.
- **DozeMachine decision tracing (v0.4):** Logs `transitionTo`, `resolveIntermediateState` and `transitionPolicy` calls, plus an inspection of selected DozeMachine fields whenever INITIALIZED, DOZE or DOZE_AOD is requested. When a relevant field is an `AmbientDisplayConfiguration`, the diagnostic also reads its `alwaysOnEnabled(user=0)` result; it does not replace the result. Repetitive `DozeParameters.getAlwaysOn()` logging is now sampled to avoid hiding transition events.

The prior v0.3 logs showed `DozeParameters.getAlwaysOn() result=true`, but `DozeMachine` still requested `DOZE` instead of `DOZE_AOD`. AOSP's DozeMachine examines the AmbientDisplayConfiguration object directly on some Android versions. Samsung might use an additional firmware-specific decision. This diagnostic seeks to distinguish those possibilities.

## Build

GitHub Actions builds on pushes to `main`. Download the artifact `A16-AOD-Clock-Init-Test-APK` from the latest successful workflow run. Local: Gradle 8.10.2, Android SDK 35, JDK 17.

## Installation & usage

1. Keep the correctly mounted donor AODService 8.9.65.92 active and the conflicting old AODService / fullaodenable modules disabled. Preserve rollback access.
2. Install the updated APK over v0.3 (same Android application ID `dev.a16.aodclocktest`).
3. In LSPosed Manager, enable the module only for **Samsung AODService** and **System UI**.
4. Reload the main SystemUI process using the previously working method; do not reboot if temporary root needs preserving.
5. Lock the phone for 10-15 seconds; unlock and run:

   ```sh
   su -c 'logcat -d -b all -v brief | grep "A16AODTrace" | tail -n 80'
   ```

6. Check `hooked ... count=`, `machine.<field>.alwaysOnEnabled(user=0) =>`, and `transitionTo requested=...`.

## Interpreting the logs

- Machine's own configuration says `alwaysOnEnabled(user=0) => false`: that is a candidate explanation for `DOZE` despite DozeParameters reporting true.
- Machine configuration says true but transition is still DOZE: likely a different Samsung-specific gate; further investigation needed.
- No machine configuration field found: Samsung uses a different code structure; inspect installed method counts.
- No DOZE_AOD request: no evidence yet that display hardware can render AOD.

### Safety & rollback

The hooks never change state arguments, return values, settings, SELinux, ClockPack or display-driver controls. Inspection uses read-only reflection and invokes only known getter methods. Deselect System UI in LSPosed and reload it to remove diagnostic hooks while preserving the AODService crash fix. No on-device validation of v0.4 is claimed.
