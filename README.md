ALWAYSON v0.10 - experimental SystemUI AOD plugin initialization fix.

The A16 SystemUI APK uses PluginInstance.loadPlugin, which invokes
PluginFactory.createPlugin before invoking the plugin onCreate callback.
When the donor AODService PluginAODImpl object is created, this version
hooks onCreate(Context,Context) and aod.qr0.j(Context) using its exact
plugin ClassLoader. Each hook temporarily sets aod.ha6.w=false and restores
its old value afterwards, as in the previously working AODService crash fix.

The SystemUI plugin onCreate error "Index 0 out of bounds for length 0" is
not necessarily the same clock-list exception. The missing SQLite table in
a separate AODService process is an additional unconfirmed issue.
No database, display power, firmware partitions, or vendor config is changed.

The v0.9 opt-in native AOD plugin selection and v0.7 one-shot AOD state test
are unchanged. Keep ClockPack's duplicate FaceWidget provider disabled.

Install the v0.10 APK from GitHub Actions over v0.9. LSPosed scope: System UI
and Samsung AODService. With root:

su -c 'settings put secure a16_select_native_aod_plugin 1; settings put secure a16_force_doze_aod_once 0; pid=$(pidof com.android.systemui); [ -n "$pid" ] && kill -TERM "$pid"'

Lock for 15s, unlock. Inspect the result:

su -c 'logcat -d -b all -v brief | grep -E "A16AODPluginInit|PluginAODImpl.*onCreate:|onPluginConnected: PluginAOD|startAlwaysOnDisplay" | tail -n 35'

A successful build is not proof of working AOD. If SystemUI misbehaves,
disable the System UI scope in LSPosed, soft restart SystemUI, or reinstall
v0.9. You can disarm plugin route via:

su -c 'settings put secure a16_select_native_aod_plugin 0; settings put secure a16_force_doze_aod_once 0'
