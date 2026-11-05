package me.yxp.qfun.custom;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;

/**
 * 屏蔽 QQ 9.2.27 的升级弹窗与更新提示（横幅由 UpgradeBannerHide 负责）。
 *
 * QQ 9.2.27 的升级控制混淆在：
 *   - com.tencent.mobileqq.upgrade.control.d  （升级控制单例）
 *       p()/s() 负责弹"发现新版本"弹窗
 *       q()/t() 负责弹升级提示
 *   - com.tencent.mobileqq.upgrade.config.united.a#e(UpgradeDetailWrapper)
 *       统一配置的升级入口，决定是否提示
 * 把这两个点拦截掉，QQ 就不会再弹更新提示；横幅仍由 UpgradeBannerHide 处理。
 */
public final class UpgradeDialogHide {
    private static final String CONTROL_CLASS = "com.tencent.mobileqq.upgrade.control.d";
    private static final String UNITED_CLASS = "com.tencent.mobileqq.upgrade.config.united.a";
    private static final String WRAPPER_CLASS = "com.tencent.mobileqq.upgrade.control.UpgradeDetailWrapper";

    private static final XC_MethodHook NOOP = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam hook) {
            hook.setResult(null);
        }
    };

    public static void install(ClassLoader hostLoader) {
        int hooked = 0;

        // 升级统一配置入口
        try {
            Class<?> united = XposedHelpers.findClass(UNITED_CLASS, hostLoader);
            for (Method method : united.getDeclaredMethods()) {
                if (!method.getName().equals("e")) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 1 && types[0].getName().equals(WRAPPER_CLASS)
                        && method.getReturnType() == void.class) {
                    XposedBridge.hookMethod(method, NOOP);
                    hooked++;
                }
            }
        } catch (Throwable error) {
            log("united.a hook failed: " + error);
        }

        // 升级控制单例：p/q/s/t 全部拦截
        try {
            Class<?> control = XposedHelpers.findClass(CONTROL_CLASS, hostLoader);
            for (Method method : control.getDeclaredMethods()) {
                if (method.getParameterTypes().length != 0 || method.getReturnType() != void.class) continue;
                String name = method.getName();
                if (name.equals("p") || name.equals("q") || name.equals("s") || name.equals("t")) {
                    XposedBridge.hookMethod(method, NOOP);
                    hooked++;
                }
            }
        } catch (Throwable error) {
            log("control.d hook failed: " + error);
        }

        log("upgrade dialog/tip hooks installed: " + hooked);
    }

    private static void log(String text) {
        try { XposedBridge.log("[QFunUpgrade] " + text); } catch (Throwable ignored) {}
    }

    private UpgradeDialogHide() {}
}
