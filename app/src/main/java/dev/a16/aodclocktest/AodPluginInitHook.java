package dev.a16.aodclocktest;

import android.content.Context;
import android.util.Log;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Install the donor clock-list fallback in the plugin's own ClassLoader.
 * SystemUI's PluginInstance.loadPlugin creates the plugin first, then
 * calls Plugin.onCreate, so the installation happens before initialization.
 */
public final class AodPluginInitHook implements IXposedHookLoadPackage {
    private static final String PROCESS = "com.android.systemui";
    private static final String PLUGIN = "com.samsung.android.app.aodservice.plugin.PluginAODImpl";
    private static final String TAG = "A16AODPluginInit";
    private static final String EXTRA = "oldAodClockSource";
    private static final Set<ClassLoader> installed =
        Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>()));

    private static void log(String message) { Log.i(TAG, message); }

    private static XC_MethodHook fallback(final ClassLoader loader, final String from) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    Class<?> flags = XposedHelpers.findClass("aod.ha6", loader);
                    boolean previous = XposedHelpers.getStaticBooleanField(flags, "w");
                    p.setObjectExtra(EXTRA, Boolean.valueOf(previous));
                    XposedHelpers.setStaticBooleanField(flags, "w", false);
                    log(from + ": fallback activated; old=" + previous);
                } catch (Throwable t) {
                    log(from + ": fallback failed: " + t);
                }
            }
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                Object old = p.getObjectExtra(EXTRA);
                if (!(old instanceof Boolean)) return;
                try {
                    Class<?> flags = XposedHelpers.findClass("aod.ha6", loader);
                    XposedHelpers.setStaticBooleanField(flags, "w", ((Boolean) old).booleanValue());
                    log(from + ": fallback restored=" + old);
                } catch (Throwable t) {
                    log(from + ": restore failed: " + t);
                }
            }
        };
    }

    private static void install(final ClassLoader loader) {
        if (loader == null || !installed.add(loader)) return;
        try {
            XposedHelpers.findAndHookMethod(PLUGIN, loader,
                "onCreate", Context.class, Context.class,
                fallback(loader, "PluginAODImpl.onCreate"));
            log("Installed donor plugin onCreate fallback");
        } catch (Throwable t) {
            log("PluginAODImpl.onCreate hook failed: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod("aod.qr0", loader,
                "j", Context.class, fallback(loader, "aod.qr0.j"));
            log("Installed donor clock-list initializer fallback");
        } catch (Throwable t) {
            log("aod.qr0.j hook failed: " + t);
        }
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!PROCESS.equals(lp.packageName) || !PROCESS.equals(lp.processName)) return;
        try {
            Class<?> instanceClass = XposedHelpers.findClass(
                "com.android.systemui.shared.plugins.PluginInstance", lp.classLoader);
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.shared.plugins.PluginInstance$PluginFactory",
                lp.classLoader, "createPlugin", instanceClass,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        Object plugin = p.getResult();
                        if (plugin == null || !PLUGIN.equals(plugin.getClass().getName())) return;
                        log("PluginAODImpl constructed; installing hooks before onCreate");
                        install(plugin.getClass().getClassLoader());
                    }
                });
            log("PluginFactory.createPlugin hook installed");
        } catch (Throwable t) {
            log("PluginFactory.createPlugin hook failed: " + t);
        }
    }
}
