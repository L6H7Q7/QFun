package me.yxp.qfun.custom;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 点击链接时改用系统默认浏览器打开，不再进入 QQ 内置浏览器。
 *
 * 原理：QQ 打开内置浏览器时会 startActivity 一个 component 名包含
 * QQBrowserActivity、extra 中带 "url" 的 Intent。这里 hook
 * Activity / ContextWrapper 的 startActivity 系列方法，命中后改用
 * ACTION_VIEW 拉起系统默认浏览器，并阻止原 Intent 启动，用户留在原界面。
 *
 * 思路参考 QAuxiliary 的「去你大爷的QQ浏览器」。
 */
public final class SystemBrowserFeature {
    private static final String TAG = "QFunSysBrowser";
    private static final String EXTRA_BYPASS = "me.yxp.qfun.custom.SystemBrowserFeature.BYPASS";

    /** 只放行看起来像网址的字符串，避免误把 QQ 其他用途的 "url" extra 当链接。 */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$|^www\\.[^.]+\\.[^.]+$|^[^.]+\\.[^.]+$",
            Pattern.CASE_INSENSITIVE);

    /** QQ 自身业务（登录/支付/会议等）仍保留内置浏览器，避免流程被破坏。 */
    private static final String[] INTERNAL_HOSTS = {
            "qq.com",
            "tenpay.com",
            "meeting.tencent.com",
            "qq-web.cdn-go.cn"
    };

    private static boolean installed;

    public static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        int hooked = 0;
        try {
            hooked += hookStartActivity(Activity.class);
        } catch (Throwable error) {
            log("Activity hook unavailable: " + error);
        }
        try {
            hooked += hookStartActivity(ContextWrapper.class);
        } catch (Throwable error) {
            log("ContextWrapper hook unavailable: " + error);
        }
        log("System browser hooks installed: " + hooked);
    }

    private static int hookStartActivity(Class<?> clazz) {
        int count = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (!"startActivity".equals(method.getName())
                    && !"startActivityForResult".equals(method.getName())) continue;
            if (java.lang.reflect.Modifier.isNative(method.getModifiers())
                    || java.lang.reflect.Modifier.isAbstract(method.getModifiers())) continue;
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam hook) {
                        try {
                            intercept(hook);
                        } catch (Throwable failure) {
                            log("Intercept failed: " + failure);
                        }
                    }
                });
                count++;
            } catch (Throwable error) {
                log("Skip " + clazz.getSimpleName() + "#" + method.getName() + ": " + error);
            }
        }
        return count;
    }

    private static void intercept(XC_MethodHook.MethodHookParam hook) {
        Intent intent = findIntent(hook.args);
        if (intent == null) return;
        if (intent.getBooleanExtra(EXTRA_BYPASS, false)) return;

        ComponentName component = intent.getComponent();
        if (component == null) return;
        if (!component.getShortClassName().contains("QQBrowserActivity")) return;

        String url = intent.getStringExtra("url");
        if (url == null) return;
        url = url.trim();
        if (url.isEmpty() || !URL_PATTERN.matcher(url.toLowerCase(Locale.ROOT)).matches()) return;
        if (isInternalUrl(url)) return;
        if (!(hook.thisObject instanceof Context)) return;

        Context context = (Context) hook.thisObject;
        try {
            String target = url.startsWith("http://") || url.startsWith("https://")
                    ? url : "http://" + url;
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            open.putExtra(EXTRA_BYPASS, true);
            context.startActivity(open);
            // 阻止打开 QQ 内置浏览器，让用户留在原界面。
            hook.setResult(null);
            log("Opened in system browser: " + url);
        } catch (Throwable failure) {
            // 失败时放行原逻辑，退回 QQ 内置浏览器，不打断用户。
            log("Fallback to internal browser: " + failure);
        }
    }

    private static Intent findIntent(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Intent) return (Intent) arg;
        }
        return null;
    }

    private static boolean isInternalUrl(String url) {
        String body = url.contains("://") ? url.substring(url.indexOf("://") + 3) : url;
        int slash = body.indexOf('/');
        String host = (slash >= 0 ? body.substring(0, slash) : body).toLowerCase(Locale.ROOT);
        for (String internal : INTERNAL_HOSTS) {
            if (host.endsWith(internal)) return true;
        }
        return false;
    }

    private static void log(String message) {
        Log.i(TAG, message);
        XposedBridge.log("[" + TAG + "] " + message);
    }

    private SystemBrowserFeature() {}
}
