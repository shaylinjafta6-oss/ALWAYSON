package dev.a16.aodclocktest;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * v0.12: populate an empty native AOD clock list by parsing the matching
 * donor APK's *own* clock_aod resource with the donor app Context.
 *
 * The AODService v8.9.65.92 dex confirms qr0.h(Category) does get(0)
 * if no default ClockInfo exists, qr0.c(AOD) copies qr0.j, and qr0.t
 * parses the XML clock resource into an ArrayList. Never synthesize a
 * ClockInfo object or modify the donor APK or SystemUI resources.
 */
public final class AodClockListRestoreHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.android.systemui";
    private static final String DONOR = "com.samsung.android.app.aodservice";
    private static final String PLUGIN = DONOR + ".plugin.PluginAODImpl";
    private static final String TAG = "A16AODClockList";
    private static final Set<ClassLoader> INSTALLED =
        Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>()));

    private static void log(String text) { Log.i(TAG, text); }

    private static Object getField(Class<?> cls, Object obj, String name)
            throws Exception {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(obj);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void repair(ClassLoader loader, Context donorCtx) throws Exception {
        if (donorCtx == null || !DONOR.equals(donorCtx.getPackageName())) {
            log("Not a donor AODService context; leaving clock list unchanged");
            return;
        }
        Class<?> managerClass = XposedHelpers.findClass("aod.qr0", loader);
        Object manager = getField(managerClass, null, "B");
        if (manager == null) {
            Method init = managerClass.getDeclaredMethod("j", Context.class);
            init.setAccessible(true);
            manager = init.invoke(null, donorCtx);
        }
        if (manager == null) {
            log("Clock manager absent; leaving stock behavior");
            return;
        }
        Object listObj = getField(managerClass, manager, "j");
        if (!(listObj instanceof ArrayList)) {
            log("AOD clock container is not an ArrayList");
            return;
        }
        ArrayList list = (ArrayList) listObj;
        if (!list.isEmpty()) {
            log("AOD clocks already loaded: count=" + list.size());
            return;
        }

        Class<?> resClass = XposedHelpers.findClass("aod.nv5", loader);
        Field resource = resClass.getDeclaredField("clock_aod");
        resource.setAccessible(true);
        int xmlId = resource.getInt(null);
        if (xmlId == 0) {
            log("Built-in clock_aod resource ID is zero; not modifying list");
            return;
        }

        // Prove the donor context can actually open this exact resource
        // before asking Samsung's original parser to handle it.
        try (XmlResourceParser xml = donorCtx.getResources().getXml(xmlId)) {
            if (xml == null) {
                log("Donor clock_aod XML missing; leaving stock list unchanged");
                return;
            }
        }

        Class<?> categoryClass = XposedHelpers.findClass(
            "com.samsung.android.clockpack.plugins.Category", loader);
        Object aodCategory = Enum.valueOf((Class<? extends Enum>) categoryClass, "AOD");
        Method parser = managerClass.getDeclaredMethod("t", Context.class,
            categoryClass, int.class, ArrayList.class);
        parser.setAccessible(true);

        ArrayList candidate = new ArrayList();
        parser.invoke(manager, donorCtx, aodCategory, xmlId, candidate);
        if (candidate.isEmpty()) {
            log("Donor XML parsed but produced zero clocks; no list changes");
            return;
        }
        Class<?> clockClass = XposedHelpers.findClass(
            "com.samsung.android.clockpack.plugins.clock.ClockInfo", loader);
        for (Object item : candidate) {
            if (!clockClass.isInstance(item)) {
                log("Unexpected clock item from donor parser; no list changes");
                return;
            }
        }

        // Do not replace the list reference; the donor code may hold it.
        list.addAll(candidate);
        log("Restored AOD clocks from donor XML: count=" + list.size());
    }

    private static void install(final ClassLoader loader) {
        if (loader == null || !INSTALLED.add(loader)) return;
        try {
            XposedHelpers.findAndHookMethod(PLUGIN, loader,
                "onCreate", Context.class, Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            repair(loader, (Context) p.args[1]);
                        } catch (Throwable failure) {
                            log("Donor clock XML repair failed: " + failure);
                        }
                    }
                });
            log("Clock XML restoration hook installed before PluginAODImpl.onCreate");
        } catch (Throwable failure) {
            log("Failed to install plugin onCreate hook: " + failure);
        }
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName) || !TARGET.equals(lp.processName)) return;
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
                        if (plugin == null
                            || !PLUGIN.equals(plugin.getClass().getName())) return;
                        install(plugin.getClass().getClassLoader());
                    }
                });
            log("v0.12 donor-clock parser hook ready");
        } catch (Throwable failure) {
            log("Plugin factory hook installation failed: " + failure);
        }
    }
}
