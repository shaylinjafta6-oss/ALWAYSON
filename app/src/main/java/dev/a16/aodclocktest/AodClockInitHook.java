package dev.a16.aodclocktest;

import android.content.Context;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Experimental scoped test for AODService 8.9.65.92 on Galaxy A16. */
public final class AodClockInitHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.samsung.android.app.aodservice";
    private static final String KEY = "a16_old_clockpack_flag";
    private static final String LOG = "A16AODClockTest: ";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET.equals(lpparam.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(
                "aod.qr0", lpparam.classLoader, "j", Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Class<?> flags = XposedHelpers.findClass("aod.ha6", lpparam.classLoader);
                            boolean original = XposedHelpers.getStaticBooleanField(flags, "w");
                            param.setObjectExtra(KEY, Boolean.valueOf(original));
                            XposedHelpers.setStaticBooleanField(flags, "w", false);
                            XposedBridge.log(LOG + "clock-list init: w " + original + " -> false (temporary)");
                        } catch (Throwable e) {
                            XposedBridge.log(LOG + "beforeHook failed: " + e);
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object saved = param.getObjectExtra(KEY);
                        if (!(saved instanceof Boolean)) return;
                        try {
                            Class<?> flags = XposedHelpers.findClass("aod.ha6", lpparam.classLoader);
                            XposedHelpers.setStaticBooleanField(flags, "w", ((Boolean) saved).booleanValue());
                            XposedBridge.log(LOG + "clock-list init: restored original flag " + saved);
                        } catch (Throwable e) {
                            XposedBridge.log(LOG + "afterHook restore failed: " + e);
                        }
                    }
                }
            );
            XposedBridge.log(LOG + "hook installed in " + lpparam.processName);
        } catch (Throwable e) {
            XposedBridge.log(LOG + "hook install failed: " + e);
        }
    }
}
