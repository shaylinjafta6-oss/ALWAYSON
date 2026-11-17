package dev.a16.aodclocktest;

import android.content.Context;
import android.content.ContentResolver;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Experimental fast start for the *already working* native Samsung AOD.
 *
 * Only when expressly opted in, it requests DOZE_AOD via Samsung's own
 * DozeService.onRequestShowDoze immediately after onDreamingStarted returns.
 * The plugin must already be connected and the machine must still be DOZE.
 * No display power, brightness, HWC, vendor, kernel or animation changes.
 */
public final class AodFastStartHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.android.systemui";
    private static final String OPT_IN = "a16_fast_aod_start";
    private static final String TAG = "A16AODFastStart";
    private static final String START = "a16_dream_start_uptime";

    private static void log(String message) { Log.i(TAG, message); }

    private static Object readField(Object obj, String name) throws Exception {
        for (Class<?> type = obj.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException ignored) {
                // Samsung code sometimes declares inherited fields in base classes.
            }
        }
        throw new NoSuchFieldException(name);
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName) || !TARGET.equals(lp.processName)) return;
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.doze.DozeService", lp.classLoader,
                "onDreamingStarted", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        p.setObjectExtra(START, Long.valueOf(SystemClock.uptimeMillis()));
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        if (p.hasThrowable()) return;
                        try {
                            final Object service = p.thisObject;
                            ContentResolver resolver =
                                ((Context) service).getContentResolver();
                            if (Settings.Secure.getInt(resolver, OPT_IN, 0) != 1) return;
                            if (Settings.System.getInt(resolver, "aod_mode", 0) != 1) {
                                log("Skipped: Samsung AOD disabled");
                                return;
                            }
                            if (Settings.Global.getInt(resolver, "low_power", 0) != 0) {
                                log("Skipped: battery saver enabled");
                                return;
                            }
                            Object plugin = readField(service, "mAODPlugin");
                            if (plugin == null) {
                                log("Skipped: native AOD plugin not connected yet");
                                return;
                            }
                            Object machine = readField(service, "mDozeMachine");
                            if (machine == null) {
                                log("Skipped: Doze machine missing");
                                return;
                            }
                            Method getState = machine.getClass().getMethod("getState");
                            String state = String.valueOf(getState.invoke(machine));
                            if (!"DOZE".equals(state)) {
                                log("No early request: machine already " + state);
                                return;
                            }

                            // The v0.7 experiment confirmed that this exact native
                            // Samsung method requests DozeMachine.State.DOZE_AOD.
                            // We do not change any display state or driver ourselves.
                            long now = SystemClock.uptimeMillis();
                            Object mark = p.getObjectExtra(START);
                            long elapsed = (mark instanceof Long)
                                ? now - ((Long) mark).longValue() : -1L;
                            log("Issuing early native DOZE_AOD request"
                                + " at onDreamingStarted+" + elapsed + "ms");
                            Method request = service.getClass()
                                .getMethod("onRequestShowDoze");
                            request.invoke(service);
                            log("State after early request=" +
                                String.valueOf(getState.invoke(machine)));
                        } catch (Throwable error) {
                            // If Samsung rejects early AOD, preserve its normal
                            // asynchronous plugin and doze transition behavior.
                            log("Early request failed, leaving stock AOD flow: " + error);
                        }
                    }
                });
            log("v0.13 fast AOD start hook installed (opt-in only)");
        } catch (Throwable error) {
            log("v0.13 hook installation failed: " + error);
        }
    }
}
