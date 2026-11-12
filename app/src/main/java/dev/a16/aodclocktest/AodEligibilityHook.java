package dev.a16.aodclocktest;

import android.util.Log;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Read-only diagnostic for Samsung SystemUI's Always On Display eligibility.
 * Does not replace method results or request any display state transitions.
 */
public final class AodEligibilityHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.android.systemui";
    private static final String TAG = "A16AODEligibility";
    private static final AtomicInteger CALL_COUNT = new AtomicInteger();
    private static final int MAX_CALL_LOGS = 80;

    private static void log(String message) {
        Log.i(TAG, message);
        XposedBridge.log(TAG + ": " + message);
    }

    private static void logCall(String message) {
        int count = CALL_COUNT.incrementAndGet();
        if (count <= MAX_CALL_LOGS) {
            log(message);
        } else if (count == MAX_CALL_LOGS + 1) {
            log("Call log limit reached; continuing silently");
        }
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET.equals(lpparam.packageName)) return;

        String[] candidates = {
            "android.hardware.display.AmbientDisplayConfiguration",
            "com.android.internal.hardware.AmbientDisplayConfiguration"
        };

        int installed = 0;
        for (final String className : candidates) {
            try {
                XposedHelpers.findClass(className, lpparam.classLoader);
            } catch (Throwable missingClass) {
                log("class unavailable: " + className);
                continue;
            }

            try {
                XposedHelpers.findAndHookMethod(
                    className, lpparam.classLoader, "alwaysOnAvailable",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            logCall(className + ".alwaysOnAvailable() => " + param.getResult());
                        }
                    }
                );
                installed++;
                log("hooked " + className + ".alwaysOnAvailable()");
            } catch (Throwable e) {
                log("could not hook " + className + ".alwaysOnAvailable(): " + e);
            }

            try {
                XposedHelpers.findAndHookMethod(
                    className, lpparam.classLoader, "alwaysOnEnabled", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object userId = (param.args != null && param.args.length > 0)
                                ? param.args[0] : "?";
                            logCall(className + ".alwaysOnEnabled(user=" + userId
                                + ") => " + param.getResult());
                        }
                    }
                );
                installed++;
                log("hooked " + className + ".alwaysOnEnabled(int)");
            } catch (Throwable e) {
                log("could not hook " + className + ".alwaysOnEnabled(int): " + e);
            }
        }

        log("SystemUI diagnostic initialized, hookedMethods=" + installed
            + ", process=" + lpparam.processName + ", results unchanged");
    }
}
