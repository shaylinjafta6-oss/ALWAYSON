package dev.a16.aodclocktest;

import android.content.Context;
import android.content.ContentResolver;
import android.provider.Settings;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * One-shot, opt-in AOD transition experiment using Samsung's own
 * DozeService.onRequestShowDoze() implementation.
 *
 * The uploaded Galaxy A16 SystemUI APK confirms that method requests
 * DozeMachine.State.DOZE_AOD through AODMachine / DozeMachine.requestState.
 * Never forces brightness, display power, or kernel/vendor values directly.
 */
public final class AodForceOnceHook implements IXposedHookLoadPackage {
    private static final String PROCESS = "com.android.systemui";
    private static final String TAG = "A16AODForce";
    private static final String TRIGGER = "a16_force_doze_aod_once";
    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean(false);

    private static void log(String msg) {
        Log.i(TAG, msg);
    }

    private static Object dozeMachine(Object service) throws Exception {
        Field field = service.getClass().getDeclaredField("mDozeMachine");
        field.setAccessible(true);
        return field.get(service);
    }

    private static String currentState(Object machine) throws Exception {
        Method getter = machine.getClass().getMethod("getState");
        Object state = getter.invoke(machine);
        return String.valueOf(state);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!PROCESS.equals(lp.packageName) || !PROCESS.equals(lp.processName)) return;
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.doze.DozeService",
                lp.classLoader,
                "onDreamingStarted",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        if (p.hasThrowable()) return;
                        if (ATTEMPTED.get()) return;

                        try {
                            Object service = p.thisObject;
                            ContentResolver resolver =
                                ((Context) service).getContentResolver();
                            if (Settings.Secure.getInt(resolver, TRIGGER, 0) != 1) return;
                            if (Settings.System.getInt(resolver, "aod_mode", 0) != 1) {
                                log("Skipped: Samsung AOD setting is off");
                                return;
                            }
                            if (Settings.Global.getInt(resolver, "low_power", 0) != 0) {
                                log("Skipped: battery saver is on");
                                return;
                            }
                            Object machine = dozeMachine(service);
                            if (machine == null) {
                                log("Skipped: no AOD machine");
                                return;
                            }
                            String before = currentState(machine);
                            if (!"DOZE".equals(before)) {
                                log("Not attempting from state=" + before);
                                return;
                            }
                            if (!ATTEMPTED.compareAndSet(false, true)) return;

                            // Disarm BEFORE requesting DOZE_AOD. If SystemUI crashes
                            // during the request, the next restart will not repeat it.
                            if (!Settings.Secure.putInt(resolver, TRIGGER, 0)) {
                                log("Aborted: could not disarm the one-shot trigger");
                                return;
                            }

                            log("One-shot request: DOZE -> DOZE_AOD via Samsung DozeService");
                            try {
                                Method request = service.getClass().getMethod("onRequestShowDoze");
                                request.invoke(service);
                                log("State after request=" + currentState(machine));
                            } catch (Throwable error) {
                                log("Request failed: " + error.toString());
                            }
                        } catch (Throwable error) {
                            log("One-shot preflight failed: " + error.toString());
                        }
                    }
                });
            log("v0.7 hook installed; inert until secure setting " + TRIGGER + "=1");
        } catch (Throwable error) {
            log("v0.7 installation failed: " + error.toString());
        }
    }
}
