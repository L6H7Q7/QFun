package me.yxp.qfun.custom;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;

/** Local presentation preferences for this QFun 1.3.4 custom build. */
public final class QFunUiCleanup {
    private QFunUiCleanup() {}

    public static void install(ClassLoader loader) {
        int count = 0;
        try {
            Class<?> vm = XposedHelpers.findClass("me.yxp.qfun.ui.viewmodel.SettingViewModel", loader);
            // Prevent creation of the automatic update coroutine and its network request.
            count += constant(vm, "checkUpdate", void.class, null);
            count += constant(vm, "getShowUpdateDialog", boolean.class, Boolean.FALSE);
            count += constant(vm, "getUpdateInfo", null, null);
            // Suppress both the action and dialog state, including existing saved UI state.
            count += constant(vm, "showDonate", void.class, null);
            count += constant(vm, "getShowDonateDialog", boolean.class, Boolean.FALSE);
        } catch (Throwable failure) {
            log("ViewModel hooks unavailable: " + failure);
        }
        try {
            Class<?> manager = XposedHelpers.findClass("me.yxp.qfun.utils.net.UpdateManager", loader);
            for (Method method : manager.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (method.getName().equals("checkUpdateSuspend") && method.getReturnType() == Object.class
                    && args.length == 1 && args[0].getName().equals("kotlin.coroutines.Continuation")) {
                    // A nullable suspend result may return null synchronously.
                    XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(null));
                    count++;
                }
            }
        } catch (Throwable failure) {
            log("Update manager hook unavailable: " + failure);
        }
        try {
            Class<?> buttons = XposedHelpers.findClass("me.yxp.qfun.ui.components.atoms.ButtonsKt", loader);
            for (Method method : buttons.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (!method.getName().equals("SocialButton") || method.getReturnType() != void.class
                    || args.length < 3 || args[0] != int.class || args[1] != String.class) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam hook) {
                        String label = (String) hook.args[1];
                        if ("Donate".equalsIgnoreCase(label) || "打赏".equals(label)
                            || "赞助".equals(label) || "支持作者".equals(label)) {
                            // Skip before any Compose group or layout node is emitted.
                            hook.setResult(null);
                        }
                    }
                });
                count++;
            }
        } catch (Throwable failure) {
            log("Donation button hook unavailable: " + failure);
        }
        log("Update/donation presentation hooks installed: " + count);
    }

    private static int constant(Class<?> type, String name, Class<?> resultType, Object result) {
        try {
            Method method = type.getDeclaredMethod(name);
            if (resultType != null && method.getReturnType() != resultType) {
                throw new NoSuchMethodException("Unexpected return type: " + name);
            }
            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(result));
            return 1;
        } catch (Throwable failure) {
            log("Cannot suppress " + name + ": " + failure);
            return 0;
        }
    }

    private static void log(String message) {
        XposedBridge.log("[QFunUiCleanup] " + message);
    }
}
