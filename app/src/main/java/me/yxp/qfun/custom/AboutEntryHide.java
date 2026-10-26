package me.yxp.qfun.custom;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

/**
 * Removes the "about QQ and help" group from the main settings list.
 *
 * The new settings page builds its groups in
 * com.tencent.mobileqq.setting.main.NewSettingConfigProvider#f(Context),
 * and AboutItemProcessor sits alone in its own group there. Dropping that
 * group is safer than mutating every item processor.
 */
public final class AboutEntryHide {
    private static final String PROVIDER = "com.tencent.mobileqq.setting.main.NewSettingConfigProvider";
    private static final String ABOUT_PROCESSOR = "AboutItemProcessor";
    private static boolean installed;

    public static void install(ClassLoader hostLoader, ClassLoader moduleLoader) {
        if (installed) return;
        installed = true;
        try {
            Class<?> provider = XposedHelpers.findClass(PROVIDER, hostLoader);
            int hooked = 0;
            for (Method method : provider.getDeclaredMethods()) {
                if (!method.getName().equals("f")) continue;
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 1 || parameters[0] != Context.class) continue;
                if (!List.class.isAssignableFrom(method.getReturnType())) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam hook) { strip(hook.getResult()); }
                });
                hooked++;
            }
            log("about settings hooks installed: " + hooked);
        } catch (Throwable error) {
            log("about settings hook failed: " + error);
        }
    }

    private static void strip(Object groups) {
        try {
            if (!(groups instanceof List)) return;
            Iterator<?> iterator = ((List<?>) groups).iterator();
            while (iterator.hasNext()) {
                Object group = iterator.next();
                if (group != null && containsAbout(group)) {
                    iterator.remove();
                    log("about settings group removed");
                }
            }
        } catch (Throwable error) {
            log("about settings strip failed: " + error);
        }
    }

    private static boolean containsAbout(Object group) {
        for (Field field : group.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(group);
                if (!(value instanceof Iterable)) continue;
                for (Object item : (Iterable<?>) value) {
                    if (item != null && item.getClass().getName().endsWith(ABOUT_PROCESSOR)) return true;
                }
            } catch (Throwable ignored) {
                // Field not readable: skip it.
            }
        }
        return false;
    }

    private static void log(String text) {
        try { XposedBridge.log("[QFunAbout] " + text); } catch (Throwable ignored) {}
    }

    private AboutEntryHide() {}
}
