# ALWAYSON v0.12: Restore the native AOD clock list

Experimental LSPosed APK targeting the Samsung Galaxy A16 SM-A165F with AODService 8.9.65.92 and One UI 9. It has **not** been verified to produce visible AOD.

## Evidence from user's logs and actual donor DEX

- The AODService native PluginAODImpl is connected in SystemUI and startAlwaysOnDisplay() executes.
- v0.7 showed real DOZE_AOD transition; v0.9 made SystemUI select PluginAODImpl.
- The donor SQLite table_settings was missing; v0.11 created the missing table using Samsung's original schema (verified). The IndexOutOfBoundsException still occurred afterward.
- Inspecting the actual 8.9.65.92 donor DEX shows that aod.qr0.h(Category) iterates the category's clock list for the default and, when absent, **calls ArrayList.get(0) without an emptiness check**.
- aod.qr0.c(Category.AOD) copies aod.qr0.j, the AOD clock list.
- aod.qr0.t(Context, Category, int XML resource, ArrayList) parses clock definitions from that Context's resources.
- aod.nv5.clock_aod points to the built-in donor XML resource.
- Running the QR0 flag fallback by itself in v0.10/v0.11 didn't populate this list in SystemUI.

## New targeted intervention

The v0.12 hook installs through the real Samsung PluginFactory into the actual AODService plugin ClassLoader. Before PluginAODImpl.onCreate(Context systemUi, Context donorApp), it checks if the cached aod.qr0.j list is empty. Only then, using **donorApp context** (validated by its package name) rather than the SystemUI context, it verifies the donor clock_aod XML resource opens, invokes Samsung's own qr0.t(parser) into a temporary list, checks the parsed objects are genuine Samsung ClockInfo, and appends them to the cached list. The module does not fabricate ClockInfo, clear any app databases, replace resources, or modify kernel settings. If XML parsing fails or produces no clocks it makes no list changes, logging the reason.

We retain the v0.11 schema code but it is **one-shot and normally disarmed**, as well as the v0.10 temporary clock source workaround, v0.9 native plugin selector and v0.7 force-once test (normally disarmed).

## Install/test

1. Download the successful GitHub Actions artifact A16-AOD-Clock-Init-Test-APK and install over v0.11.
2. LSPosed scope: System UI and Samsung AODService. Keep ClockPack's duplicate FaceWidget provider **disabled**, but don't disable the whole ClockPack package.
3. No more database writes: clear any leftover schema trigger, keep native route on, and forced doze off:

```sh
su -c 'settings put secure a16_repair_aod_schema_once 0; settings put secure a16_force_doze_aod_once 0; settings put secure a16_select_native_aod_plugin 1; pid=$(pidof com.android.systemui); [ -n "$pid" ] && kill -TERM "$pid"'
```

4. Lock for 15 seconds and observe whether AOD is visible. Then use this **single check** if still black:

```sh
su -c 'logcat -d -b all -v brief | grep -E "A16AODClockList|PluginAODImpl.*onCreate:|onPluginConnected: PluginAOD|startAlwaysOnDisplay" | tail -n 30'
```

**Success:** report says 'Restored AOD clocks from donor XML' with a count >0, PluginAODImpl.onCreate no longer logs IndexOutOfBounds, and the actual clock renders on the panel. An unchanged error after restoring AOD clocks would indicate a different empty list or more than one incompatibility.

## Rollback

To turn off the native plugin routing and any one-shot hooks:
```sh
su -c 'settings put secure a16_select_native_aod_plugin 0; settings put secure a16_force_doze_aod_once 0; settings put secure a16_repair_aod_schema_once 0'
```
Deselect System UI in LSPosed and soft-restart SystemUI or reinstall v0.11 if the interface becomes unstable. The v0.12 clock-list edit is **in memory only** and disappears when SystemUI restarts. It does not undo the persistent table created by v0.11.
