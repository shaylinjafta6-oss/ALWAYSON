package dev.a16.aodclocktest;

import android.util.Log;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Read-only DozeMachine diagnostics. Strictly scoped to main SystemUI process.
 * Never modifies requested/returned states or AOD configuration.
 */
public final class AodDozeTraceHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.android.systemui";
    private static final String TAG = "A16AODTrace";
    private static final AtomicInteger EVENT_COUNT = new AtomicInteger();
    private static final int MAX_EVENTS = 90;

    private static void log(String message) {
        Log.i(TAG, message);
    }

    private static void event(String message) {
        int index = EVENT_COUNT.incrementAndGet();
        if (index <= MAX_EVENTS) log(message);
        if (index == MAX_EVENTS + 1) log("event limit reached; no further per-call logs");
    }

    private static String args(XC_MethodHook.MethodHookParam param) {
        if (param.args == null || param.args.length == 0) return "(none)";
        StringBuilder b = new StringBuilder();
        for (Object arg : param.args) {
            if (b.length() > 0) b.append(", ");
            b.append(String.valueOf(arg));
        }
        return b.toString();
    }

    private static int hook(final Class<?> clazz, final String name,
            final XC_MethodHook callback) {
        try {
            Set<XC_MethodHook.Unhook> installed = XposedBridge.hookAllMethods(clazz, name, callback);
            int count = installed == null ? 0 : installed.size();
            log("hooked " + clazz.getName() + "." + name + " count=" + count);
            return count;
        } catch (Throwable e) {
            log("hook failed for " + clazz.getName() + "." + name + ": " + e);
            return 0;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!TARGET.equals(param.packageName)
                || !TARGET.equals(param.processName)) return;
        log("starting read-only main SystemUI Doze diagnostics");
        int installed = 0;
        try {
            Class<?> machine = XposedHelpers.findClass(
                "com.android.systemui.doze.DozeMachine", param.classLoader);

            installed += hook(machine, "resolveIntermediateState", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    event("resolveIntermediateState entered args=" + args(p));
                }
            });

            installed += hook(machine, "transitionTo", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    event("transitionTo requested=" + args(p));
                }
            });

            installed += hook(machine, "transitionPolicy", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    event("transitionPolicy requested=" + args(p)
                        + " result=" + p.getResult());
                }
            });
        } catch (Throwable e) {
            log("DozeMachine unavailable: " + e);
        }

        String[] parameterClasses = {
            "com.android.systemui.statusbar.phone.DozeParameters",
            "com.android.systemui.doze.DozeParameters"
        };
        for (String name : parameterClasses) {
            try {
                Class<?> parameters = XposedHelpers.findClass(name, param.classLoader);
                installed += hook(parameters, "getAlwaysOn", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        event("DozeParameters.getAlwaysOn() result=" + p.getResult());
                    }
                });
            } catch (Throwable notOnThisFirmware) {
                log("DozeParameters candidate unavailable: " + name);
            }
        }
        log("diagnostic install complete hookedMethods=" + installed
            + " process=" + param.processName + " results unchanged");
    }
}
