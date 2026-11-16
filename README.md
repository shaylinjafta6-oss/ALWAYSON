# ALWAYSON v0.11 - one-shot donor DB schema test

The device's logs showed `PluginAODImpl.onCreate` raising `Index 0 out of bounds` and, in the **same SystemUI process**, `SQLiteException: no such table: table_settings`.

Inspection of donor AODService 8.9.65.92 confirms that `DBManager.init(Context,true)` and `AODSettingsDBItem.getCreateTableSQL()` can provision this table using `CREATE TABLE IF NOT EXISTS`. This version tries that path once, before native AOD plugin initialization. It does NOT blindly invent table columns, clear app data, or touch system partitions.

The secure setting `a16_repair_aod_schema_once` must be set to 1 first. The hook checks for the actual AODService plugin Context and unlocked user storage, then disarms the trigger BEFORE any DB operation. It uses the donor's DBManager and checks if the table exists. If still absent, it executes the donor's table-creation SQL in a transaction. No existing rows or tables are deleted. Created tables are persistent and rollback of the hook does not revert them.

The original AODService crash fix, v0.9 native plugin selector, v0.10 plugin clock fallback and v0.7 optional forced DOZE_AOD test remain intact. The SQLite failure may not be the only reason for blank AOD; successful compilation is not proof that AOD renders.

## Installation / single test
- Install the built GitHub Actions APK over v0.10.
- LSPosed scopes: System UI and Samsung AODService. Leave only the duplicate ClockPack FaceWidget provider disabled.
- Run:

```sh
su -c 'settings put secure a16_select_native_aod_plugin 1; settings put secure a16_force_doze_aod_once 0; settings put secure a16_repair_aod_schema_once 1; pid=$(pidof com.android.systemui); [ -n "$pid" ] && kill -TERM "$pid"'
```

- Lock for 15 seconds and unlock. Check once:

```sh
su -c 'logcat -d -b all -v brief | grep -E "A16AODSchema|PluginAODImpl.*onCreate:|onPluginConnected: PluginAOD|startAlwaysOnDisplay" | tail -n 40'
```

## Rollback

```sh
su -c 'settings put secure a16_repair_aod_schema_once 0; settings put secure a16_select_native_aod_plugin 0; settings put secure a16_force_doze_aod_once 0'
```

If SystemUI is unstable, disable its ALWAYSON scope in LSPosed and soft-restart SystemUI, or reinstall v0.10. A database schema created by this module remains until independently restored from a backup.
