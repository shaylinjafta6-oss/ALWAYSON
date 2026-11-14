package dev.a16.aodclocktest;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Galaxy A16 / Samsung SystemUI: trial of Samsung's own deferred ClockPack
 * listener registration callback. Only runs when DozeService.onCreate actually
 * queued that callback waiting for FaceWidget to connect.
 *
 * Does not modify Doze state, display controls, settings or vendor feature flags.
 */
public final class AodClockPackRegistrationHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.android.systemui";
    private static final String TAG = "A16AODRegistration";

    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }

    private static Object lazyGet(Object lazy) throws Exception {
        Method method = lazy.getClass().getMethod("get");
        return method.invoke(lazy);
    }

    private static void log(String message) {
        Log.i(TAG, message);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET.equals(lpparam.packageName)
                || !TARGET.equals(lpparam.processName)) return;

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.doze.DozeService",
                lpparam.classLoader,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object service = param.thisObject;
                            if (field(service, "mDozeMachine") == null
                                    || field(service, "mPluginManager") == null) {
                                log("DozeService not fully initialized; leave stock behavior");
                                return;
                            }

                            Object pending = field(service, "mPluginConnectionRunnable");
                            if (!(pending instanceof Runnable)) {
                                log("No Samsung connection callback; leave stock behavior");
                                return;
                            }

                            Object lazy = field(service, "mPluginAODManagerLazy");
                            if (lazy == null) {
                                log("AOD plugin manager unavailable; leave stock behavior");
                                return;
                            }
                            Object manager = lazyGet(lazy);
                            if (manager == null) {
                                log("AOD plugin manager not initialized; leave stock behavior");
                                return;
                            }

                            Object callbacks = field(manager, "mConnectionRunnableList");
                            if (!(callbacks instanceof List)
                                    || !((List<?>) callbacks).contains(pending)) {
                                log("ClockPack listener not deferred; stock behavior retained");
                                return;
                            }

                            // Do exactly what Samsung's queued callback would do once
                            // FaceWidget connects: register DozeService as plugin listener.
                            // The callback itself respects LOCKUI_AOD_PACKAGE_AVAILABLE.
                            ((Runnable) pending).run();

                            // Avoid a duplicate listener if FaceWidget connects later.
                            boolean removed = ((List<?>) callbacks).remove(pending);
                            log("Executed Samsung's queued plugin registration callback"
                                    + "; removed duplicate callback=" + removed);
                        } catch (Throwable problem) {
                            // Failure leaves the original deferred callback installed.
                            log("Registration test failed; stock fallback retained: "
                                    + problem.getClass().getSimpleName()
                                    + ": " + problem.getMessage());
                        }
                    }
                });
            log("v0.6 installed: guarded deferred ClockPack listener registration");
        } catch (Throwable error) {
            log("v0.6 setup failed: " + error);
        }
    }
}
