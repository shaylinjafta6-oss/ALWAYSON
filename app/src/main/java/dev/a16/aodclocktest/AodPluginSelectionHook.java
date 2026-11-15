package dev.a16.aodclocktest;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Opt-in, reversible Samsung AOD plugin-path experiment for the A16.
 *
 * The user's SystemUI.apk confirms that DozeService.onCreate and
 * DozeService.onDreamingStarted read LsRune.LOCKUI_AOD_PACKAGE_AVAILABLE
 * to choose the AODService plugin path rather than the ClockPack path.
 * Only make that boolean true around those two original method calls.
 */
public final class AodPluginSelectionHook implements IXposedHookLoadPackage {
    private static final String PROCESS = "com.android.systemui";
    private static final String FLAG_OWNER = "com.android.systemui.LsRune";
    private static final String FIELD = "LOCKUI_AOD_PACKAGE_AVAILABLE";
    private static final String OPT_IN = "a16_select_native_aod_plugin";
    private static final String TAG = "A16AODPluginPath";
    private static final String SAVED = "a16_plugin_path_old_flag";

    private static void log(String s) { Log.i(TAG, s); }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!PROCESS.equals(lp.packageName) || !PROCESS.equals(lp.processName)) return;
        final Class<?> rune;
        try {
            rune = XposedHelpers.findClass(FLAG_OWNER, lp.classLoader);
        } catch (Throwable e) {
            log("LsRune unavailable: " + e);
            return;
        }
        for (final String method : new String[]{"onCreate", "onDreamingStarted"}) {
            try {
                XposedHelpers.findAndHookMethod("com.android.systemui.doze.DozeService",
                    lp.classLoader, method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                Context ctx = (Context) p.thisObject;
                                if (Settings.Secure.getInt(ctx.getContentResolver(), OPT_IN, 0) != 1)
                                    return;
                                boolean was = XposedHelpers.getStaticBooleanField(rune, FIELD);
                                if (!was) {
                                    XposedHelpers.setStaticBooleanField(rune, FIELD, true);
                                    if (!XposedHelpers.getStaticBooleanField(rune, FIELD)) {
                                        log(method + ": could not activate plugin flag");
                                        return;
                                    }
                                }
                                p.setObjectExtra(SAVED, Boolean.valueOf(was));
                                log(method + ": temporary AOD plugin route (original=" + was + ")");
                            } catch (Throwable e) {
                                log(method + ": pre-hook failed, stock behavior: " + e);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            Object saved = p.getObjectExtra(SAVED);
                            if (!(saved instanceof Boolean)) return;
                            try {
                                // The companion registration after-hook executes before
                                // this restoration, so Samsung's queued callback also
                                // reads true when choosing the native AOD listener.
                                XposedHelpers.setStaticBooleanField(rune, FIELD,
                                    ((Boolean) saved).booleanValue());
                                log(method + ": restored original plugin route flag=" + saved);
                            } catch (Throwable e) {
                                log(method + ": could not restore original flag: " + e);
                            }
                        }
                    });
                log("Hooked " + method + " (opt-in via " + OPT_IN + "=1)");
            } catch (Throwable e) {
                log("Could not hook " + method + ": " + e);
            }
        }
    }
}
