package me.yxp.qfun.custom;

import android.content.SharedPreferences;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.Collections;

/** Override missing defaults only; saved user choices retain precedence. */
public final class QFunDefaults {
    private static boolean installed;
    public static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        try {
            Class<?> base = XposedHelpers.findClass("me.yxp.qfun.hook.base.BaseSwitchHookItem", loader);
            Object companion = XposedHelpers.getStaticObjectField(base, "Companion");
            XposedHelpers.findAndHookMethod(base, "isEnable", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    String category = (String) XposedHelpers.callMethod(hook.thisObject, "getCategory");
                    if (!DefaultFeatureRules.enabled(hook.thisObject.getClass().getSimpleName(), category)) return;
                    SharedPreferences prefs = (SharedPreferences) XposedHelpers.callMethod(companion, "getPrefs");
                    String key = (String) XposedHelpers.callMethod(hook.thisObject, "getName");
                    if (!prefs.contains(key)) hook.setResult(Boolean.TRUE);
                }
            });
            XposedBridge.log("[QFunDefaults] Missing feature preferences use requested defaults; saved choices preserved");
        } catch (Throwable error) {
            XposedBridge.log("[QFunDefaults] Feature defaults hook unavailable: " + error);
        }
    }
    public static void installRedPacketDefaults(ClassLoader loader) {
        try {
            Class<?> auto = XposedHelpers.findClass("me.yxp.qfun.hook.redpacket.AutoGrabHb", loader);
            for (Method method : auto.getDeclaredMethods()) {
                if (!method.getName().equals("getDefaultConfig") || method.getParameterTypes().length != 0
                        || !method.getReturnType().getName().equals("me.yxp.qfun.conf.RedPacketConfig")) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam hook) {
                        Object config = hook.getResult();
                        if (config == null) return;
                        hook.setResult(XposedHelpers.callMethod(config, "copy",
                                XposedHelpers.callMethod(config, "getDelay"),
                                XposedHelpers.callMethod(config, "getMinAverage"),
                                XposedHelpers.callMethod(config, "getKeywords"),
                                Collections.singletonList("xxlb"),
                                XposedHelpers.callMethod(config, "getWhiteList"),
                                RedPacketTrialRules.defaults()));
                    }
                });
            }
            XposedBridge.log("[QFunDefaults] Default red packet reply: xxlb");
        } catch (Throwable error) {
            XposedBridge.log("[QFunDefaults] Reply default hook unavailable: " + error);
        }
    }
    private QFunDefaults() {}
}
