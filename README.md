# ALWAYSON • Galaxy A16 AOD Clock Initialization Test

An **experimental LSPosed module APK**, targeting **Samsung Galaxy A16 SM-A165F** with **AODService 8.9.65.92**, One UI 9 and Android 17 (API 37). It is **not** a complete native Always On Display port.

## Hypothesis

The donor AODService detects `clockpackversion=7` and skips initialization of its built-in AOD clock definitions. At startup the AOD clock list can be empty; a default-clock lookup then attempts to access item 0, causing `IndexOutOfBoundsException`. This app hooks `aod.qr0.j(Context)` and temporarily sets the internal `aod.ha6.w` flag to `false` **only during the clock-list initialization call**. It restores the original flag afterward, including when the hooked method throws.

The hook is **experimental** and relies on obfuscated names from the supplied AODService **8.9.65.92** APK. Its success and any improvement to native AOD rendering have **not** been verified on a Galaxy A16.

## Building

GitHub Actions builds the debug APK on pushes to `main` and can also be started using the **Actions** tab. Open the `Build A16 AOD Clock Init LSPosed Test` workflow and download its `A16-AOD-Clock-Init-Test-APK` artifact from a successful run.

For a local build with the Android SDK and JDK 17: `gradle :app:assembleDebug` (Gradle 8.10.2 / Android SDK 35).

The `xposedstubs` module supplies *compile-only* API signatures; the actual LSPosed API is supplied at runtime. These stub classes are not included in the installable APK.

## Phone testing

1. Preserve an existing working recovery/rollback method. The target is experimental and may destabilize the lock screen.
2. Keep old `shalomp_aod_16` and `fullaodenable` KernelSU modules **disabled**.
3. Enable only the donor `aodservice_896592_test` module and verify Android recognizes its APK before using the hook.
4. Install the built APK normally (not as a KernelSU ZIP). In LSPosed Manager, enable **A16 AOD Clock Init Test** and scope it **only** to `com.samsung.android.app.aodservice`.
5. Use the already-established restart/soft-reboot approach to reload the target process.
6. Open the AOD setting once and inspect **LSPosed logs** for `A16AODClockTest:` and Android's crash logs for the previous `IndexOutOfBoundsException`.

If the crash disappears, that supports the clock-list hypothesis but **does not prove actual AOD is working**. If it persists, the parsing path may have failed or the hook may have loaded too late. Do not force SELinux changes, privileges, or display modes as a workaround.

## Rollback

Disable the APK module in LSPosed and restart its target process. For the prior known-working baseline, disable the separate donor AODService KernelSU module. This test does not modify ClockPack, Samsung's signed AOD APK, SELinux, or system partitions.

**No on-device testing has been performed for this build.**