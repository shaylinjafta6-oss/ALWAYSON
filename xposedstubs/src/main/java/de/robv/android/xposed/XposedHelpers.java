package de.robv.android.xposed;
public final class XposedHelpers {
    private XposedHelpers() {}
    public static XC_MethodHook.Unhook findAndHookMethod(String name, ClassLoader loader, String methodName, Object... args) { return null; }
    public static Class<?> findClass(String name, ClassLoader loader) { return null; }
    public static boolean getStaticBooleanField(Class<?> clazz, String name) { return false; }
    public static void setStaticBooleanField(Class<?> clazz, String name, boolean value) {}
}
