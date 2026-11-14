package dev.a16.aodclocktest;

import android.util.Log;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Read-only identification of who requests ordinary DOZE on Galaxy A16. */
public final class AodTransitionOriginHook implements IXposedHookLoadPackage {
    private static final String PROCESS = "com.android.systemui";
    private static final String TAG = "A16AODOrigin";
    private static final AtomicInteger DOZE_COUNT = new AtomicInteger();

    private static void log(String msg) {
        Log.i(TAG, msg);
    }

    private static void logParts(Object machine) {
        if (machine == null) return;
        for (Class<?> clazz = machine.getClass(); clazz != null
                && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!"mParts".equals(field.getName())) continue;
                try {
                    field.setAccessible(true);
                    Object parts = field.get(machine);
                    if (parts == null) {
                        log("mParts=null");
                    } else if (parts.getClass().isArray()) {
                        int count = Array.getLength(parts);
                        log("DozeMachine parts=" + count);
                        for (int n = 0; n < count && n < 20; n++) {
                            Object part = Array.get(parts, n);
                            log("part[" + n + "]="
                                + (part == null ? "null" : part.getClass().getName()));
                        }
                    } else {
                        log("mParts type=" + parts.getClass().getName());
                    }
                } catch (Throwable e) {
                    log("Could not read mParts: " + e.getClass().getSimpleName());
                }
                return;
            }
        }
        log("mParts field not found");
    }

    private static void logCallers() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int n = 0;
        for (StackTraceElement frame : stack) {
            String clazz = frame.getClassName();
            if (!clazz.startsWith("com.android.systemui.")) continue;
            log("DOZE caller[" + n + "]=" + frame.toString());
            if (++n == 12) break;
        }
        if (n == 0) log("No direct SystemUI stack frames visible");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!PROCESS.equals(param.packageName)
                || !PROCESS.equals(param.processName)) return;
        try {
            Class<?> machine = XposedHelpers.findClass(
                "com.android.systemui.doze.DozeMachine", param.classLoader);
            Set<XC_MethodHook.Unhook> hooked = XposedBridge.hookAllMethods(
                machine, "transitionTo", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        if (p.args == null || p.args.length == 0
                                || !"DOZE".equals(String.valueOf(p.args[0]))) return;
                        if (DOZE_COUNT.incrementAndGet() > 2) return;
                        try {
                            log("DOZE requested: capturing caller and assembled machine parts");
                            logCallers();
                            logParts(p.thisObject);
                        } catch (Throwable error) {
                            log("Read-only trace error: " + error);
                        }
                    }
                });
            log("v0.5 installed read-only transition origin hook count="
                + (hooked == null ? 0 : hooked.size()));
        } catch (Throwable error) {
            log("v0.5 hook install failed: " + error);
        }
    }
}
