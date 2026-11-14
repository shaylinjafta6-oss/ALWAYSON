package dev.a16.aodclocktest;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Read-only AOD state-selection diagnostic in the main SystemUI process.
 * No display states, fields, settings or method return values are changed.
 */
public final class AodDozeTraceHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.android.systemui";
    private static final String TAG = "A16AODTrace";
    private static final AtomicInteger ALWAYS_ON_CALLS = new AtomicInteger();
    private static volatile String lastAlwaysOn = null;

    private static void log(String message) {
        Log.i(TAG, message);
    }

    private static String args(XC_MethodHook.MethodHookParam param) {
        if (param.args == null || param.args.length == 0) return "(none)";
        StringBuilder out = new StringBuilder();
        for (Object arg : param.args) {
            if (out.length() > 0) out.append(", ");
            out.append(String.valueOf(arg));
        }
        return out.toString();
    }

    private static int hook(Class<?> clazz, String name, XC_MethodHook callback) {
        try {
            Set<XC_MethodHook.Unhook> installed =
                XposedBridge.hookAllMethods(clazz, name, callback);
            int count = installed == null ? 0 : installed.size();
            log("hooked " + clazz.getName() + "." + name + " count=" + count);
            return count;
        } catch (Throwable error) {
            log("hook failed " + clazz.getName() + "." + name + ": " + error);
            return 0;
        }
    }

    private static void dumpMachineGates(Object machine) {
        if (machine == null) {
            log("machine snapshot unavailable: null");
            return;
        }
        int inspected = 0;
        for (Class<?> type = machine.getClass();
             type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                String name = field.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!(lower.contains("config")
                    || lower.contains("ambient")
                    || lower.contains("always")
                    || lower.contains("aod")
                    || lower.contains("dock"))) continue;
                if (++inspected > 18) {
                    log("machine snapshot: field inspection limit reached");
                    return;
                }

                try {
                    field.setAccessible(true);
                    Object value = field.get(machine);
                    if (value == null) {
                        log("machine." + name + "=null");
                        continue;
                    }
                    if (value instanceof Boolean || value instanceof Number) {
                        log("machine." + name + "=" + value);
                        continue;
                    }

                    String className = value.getClass().getName();
                    log("machine." + name + " type=" + className);

                    // Getter invocation only; does not replace any eligibility result.
                    if (className.equals("android.hardware.display.AmbientDisplayConfiguration")
                        || className.endsWith(".AmbientDisplayConfiguration")) {
                        try {
                            Method getter = value.getClass()
                                .getMethod("alwaysOnEnabled", int.class);
                            Object result = getter.invoke(value, 0);
                            log("machine." + name
                                + ".alwaysOnEnabled(user=0) => " + result);
                        } catch (Throwable error) {
                            log("machine." + name
                                + ".alwaysOnEnabled read failed: "
                                + error.getClass().getSimpleName());
                        }
                    }

                    if (lower.contains("dock")) {
                        try {
                            Method getter = value.getClass().getMethod("isDocked");
                            Object result = getter.invoke(value);
                            log("machine." + name + ".isDocked() => " + result);
                        } catch (Throwable ignored) {
                            // Not every dock-related field is a DockManager.
                        }
                    }
                } catch (Throwable error) {
                    log("machine." + name
                        + " inspection failed: " + error.getClass().getSimpleName());
                }
            }
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!TARGET.equals(param.packageName)
                || !TARGET.equals(param.processName)) return;

        log("v0.4 read-only main SystemUI tracing started");
        int installed = 0;

        try {
            Class<?> machine = XposedHelpers.findClass(
                "com.android.systemui.doze.DozeMachine", param.classLoader);

            installed += hook(machine, "transitionTo", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    String requested = args(p);
                    log("transitionTo requested=" + requested);
                    if (p.args != null && p.args.length > 0) {
                        String state = String.valueOf(p.args[0]);
                        if ("INITIALIZED".equals(state) || "DOZE".equals(state)
                            || "DOZE_AOD".equals(state)) {
                            dumpMachineGates(p.thisObject);
                        }
                    }
                }
            });

            installed += hook(machine, "resolveIntermediateState", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    log("resolveIntermediateState entered args=" + args(p));
                }
            });

            installed += hook(machine, "transitionPolicy", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    log("transitionPolicy args=" + args(p)
                        + " result=" + p.getResult());
                }
            });
        } catch (Throwable error) {
            log("DozeMachine unavailable: " + error);
        }

        String[] candidates = {
            "com.android.systemui.statusbar.phone.DozeParameters",
            "com.android.systemui.doze.DozeParameters"
        };

        for (String name : candidates) {
            try {
                Class<?> parameters = XposedHelpers.findClass(name, param.classLoader);
                installed += hook(parameters, "getAlwaysOn", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        String result = String.valueOf(p.getResult());
                        int n = ALWAYS_ON_CALLS.incrementAndGet();
                        boolean changed = !result.equals(lastAlwaysOn);
                        if (changed) lastAlwaysOn = result;
                        if (n <= 3 || changed) {
                            log("DozeParameters.getAlwaysOn() result="
                                + result + " call=" + n);
                        }
                    }
                });
            } catch (Throwable missing) {
                log("DozeParameters candidate unavailable: " + name);
            }
        }

        log("v0.4 install complete hookedMethods=" + installed
            + " process=" + param.processName + " no values modified");
    }
}
