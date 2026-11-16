package dev.a16.aodclocktest;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** One-shot optional donor DB schema repair. Never clears app data. */
public final class AodSchemaRepairHook implements IXposedHookLoadPackage {
    private static final String SYSUI = "com.android.systemui";
    private static final String PKG = "com.samsung.android.app.aodservice";
    private static final String PLUGIN = PKG + ".plugin.PluginAODImpl";
    private static final String KEY = "a16_repair_aod_schema_once";
    private static final String TAG = "A16AODSchema";
    private static final AtomicBoolean attempted = new AtomicBoolean(false);
    private static void log(String s) { Log.i(TAG, s); }

    private static boolean hasTable(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='table_settings'",
                null)) {
            return c.moveToFirst();
        }
    }

    private static void repair(ClassLoader loader, Context context) throws Exception {
        Class<?> managerClass = XposedHelpers.findClass(
            PKG + ".common.provider.DB.DBManager", loader);
        Object manager = managerClass.getMethod("getInstance").invoke(null);
        managerClass.getMethod("init", Context.class, boolean.class)
            .invoke(manager, context, true);
        Object adapter = managerClass.getMethod("getDBAdapter").invoke(manager);
        if (adapter == null ||
                !adapter.getClass().getName().endsWith(".SQLiteOpenHelperAdapter")) {
            log("Unexpected DB adapter; no manual changes");
            return;
        }
        Field helperField = adapter.getClass().getDeclaredField("mDBHelper");
        helperField.setAccessible(true);
        Object helper = helperField.get(adapter);
        if (!(helper instanceof SQLiteOpenHelper)) {
            log("DBHelper not available; no manual changes");
            return;
        }
        SQLiteDatabase db = ((SQLiteOpenHelper) helper).getWritableDatabase();
        if (hasTable(db)) {
            log("table_settings verified present; no manual schema change");
            return;
        }
        Class<?> itemClass = XposedHelpers.findClass(
            PKG + ".common.provider.DB.AODSettingsDBItem", loader);
        Object item = itemClass.getConstructor().newInstance();
        String sql = (String) itemClass.getMethod("getCreateTableSQL").invoke(item);
        if (!sql.trim().toLowerCase(Locale.ROOT)
                .startsWith("create table if not exists table_settings ")) {
            log("Donor SQL validation failed; no changes");
            return;
        }
        db.beginTransaction();
        try {
            db.execSQL(sql);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        log("Created table using donor SQL; verified=" + hasTable(db));
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!SYSUI.equals(lp.packageName) || !SYSUI.equals(lp.processName)) return;
        try {
            Class<?> instance = XposedHelpers.findClass(
                "com.android.systemui.shared.plugins.PluginInstance", lp.classLoader);
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.shared.plugins.PluginInstance$PluginFactory",
                lp.classLoader, "createPlugin", instance, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        Object plugin = p.getResult();
                        if (plugin == null || !PLUGIN.equals(plugin.getClass().getName())) return;
                        final ClassLoader loader = plugin.getClass().getClassLoader();
                        try {
                            XposedHelpers.findAndHookMethod(
                                PLUGIN, loader, "onCreate", Context.class, Context.class,
                                new XC_MethodHook() {
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam call) {
                                        try {
                                            if (attempted.get()) return;
                                            Context sysui = (Context) call.args[0];
                                            if (Settings.Secure.getInt(
                                                    sysui.getContentResolver(), KEY, 0) != 1)
                                                return;
                                            Context app = (Context) call.args[1];
                                            if (app == null || !PKG.equals(app.getPackageName())) {
                                                log("AOD plugin Context not verified; skipping");
                                                return;
                                            }
                                            UserManager user = app.getSystemService(UserManager.class);
                                            if (user == null || !user.isUserUnlocked()) {
                                                log("User storage locked; skipping");
                                                return;
                                            }
                                            if (!attempted.compareAndSet(false, true)) return;
                                            if (!Settings.Secure.putInt(
                                                    sysui.getContentResolver(), KEY, 0)) {
                                                log("Unable to disarm trigger; skipping");
                                                return;
                                            }
                                            log("Checking donor database schema");
                                            repair(loader, app);
                                        } catch (Throwable e) {
                                            log("Schema repair failed: " + e);
                                        }
                                    }
                                });
                            log("Plugin schema hook installed; awaiting one-shot opt-in");
                        } catch (Throwable e) {
                            log("Plugin onCreate hook failed: " + e);
                        }
                    }
                });
            log("v0.11 donor schema hook ready (inactive by default)");
        } catch (Throwable e) {
            log("Hook installation failed: " + e);
        }
    }
}
