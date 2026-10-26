package me.yxp.qfun.custom;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;

/**
 * Hides the "new QQ version available" banner from the message list.
 *
 * QQ 9.2.27 builds that banner in
 * com.tencent.mobileqq.upgrade.ui.banner.UpgradeBannerProcessor:
 * initBanner() creates the QUI notice bar, updateBanner() fills it and
 * onMessage() asks BannerManager to show it. Suppressing all three keeps the
 * banner from being created or refreshed. The upgrade dialog stays untouched,
 * that is handled separately if needed.
 */
public final class UpgradeBannerHide {
    private static final String[] PROCESSORS = {
        "com.tencent.mobileqq.upgrade.ui.banner.UpgradeBannerProcessor",
        "com.tencent.mobileqq.upgrade.ui.banner.InstallUpgradeBannerProcessor"
    };

    public static void install(ClassLoader hostLoader) {
        int hooked = 0;
        for (String name : PROCESSORS) {
            try {
                Class<?> processor = XposedHelpers.findClass(name, hostLoader);
                for (Method method : processor.getDeclaredMethods()) {
                    String methodName = method.getName();
                    if (methodName.equals("initBanner")) {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam hook) { hook.setResult(null); }
                        });
                        hooked++;
                    } else if (methodName.equals("updateBanner") || methodName.equals("onMessage")
                            || methodName.equals("onAccountChanged")) {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam hook) { hook.setResult(null); }
                        });
                        hooked++;
                    }
                }
            } catch (Throwable error) {
                log("banner hook failed for " + name + ": " + error);
            }
        }
        log("upgrade banner hooks installed: " + hooked);
    }

    private static void log(String text) {
        try { XposedBridge.log("[QFunBanner] " + text); } catch (Throwable ignored) {}
    }

    private UpgradeBannerHide() {}
}
