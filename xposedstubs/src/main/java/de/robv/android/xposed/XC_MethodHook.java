package de.robv.android.xposed;
import java.util.HashMap;
import java.util.Map;
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
    public static class MethodHookParam {
        private final Map<String, Object> extras = new HashMap<>();
        public void setObjectExtra(String key, Object value) { extras.put(key, value); }
        public Object getObjectExtra(String key) { return extras.get(key); }
    }
    public static class Unhook {}
}
