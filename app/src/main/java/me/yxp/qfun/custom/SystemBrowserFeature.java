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
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 点击链接时改用系统默认浏览器打开，不再进入 QQ 内置浏览器。
 *
 * 原理：QQ 打开内置浏览器时会启动 component 名包含 QQBrowser / *BrowserActivity /
 * *WebViewActivity 的页面，并带上网址（url extra 或 Intent data）。这里 hook：
 *   - Activity / ContextWrapper 的 startActivity 系列方法（常规路径）
 *   - Instrumentation.execStartActivity（Fragment、扫码等绕过 startActivity 的路径）
 * 命中后改用 ACTION_VIEW 拉起系统默认浏览器，并阻止原页面启动。
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

    /** 诊断用：记录出现过的浏览器入口，方便定位漏网场景。 */
    private static final Set<String> SEEN_CANDIDATES = Collections.synchronizedSet(new HashSet<String>());

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
        try {
            hooked += hookInstrumentation(loader);
        } catch (Throwable error) {
            log("Instrumentation hook unavailable: " + error);
        }
        log("System browser hooks installed: " + hooked);
    }

    /** 兜底：Fragment 等路径会绕过 startActivity 直接走 Instrumentation.execStartActivity。 */
    private static int hookInstrumentation(ClassLoader loader) {
        int count = 0;
        Class<?> instrumentation = XposedHelpers.findClass("android.app.Instrumentation", loader);
        for (Method method : instrumentation.getDeclaredMethods()) {
            if (!"execStartActivity".equals(method.getName())) continue;
            if (java.lang.reflect.Modifier.isNative(method.getModifiers())
                    || java.lang.reflect.Modifier.isAbstract(method.getModifiers())) continue;
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam hook) {
                        try {
                            intercept(hook);
                        } catch (Throwable failure) {
                            log("execStartActivity intercept failed: " + failure);
                        }
                    }
                });
                count++;
            } catch (Throwable error) {
                log("Skip Instrumentation#" + method.getName() + ": " + error);
            }
        }
        return count;
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
        if (!isBrowserActivity(component.getShortClassName())) return;

        String url = extractUrl(intent);
        if (url == null || url.isEmpty()) return;
        if (!URL_PATTERN.matcher(url.toLowerCase(Locale.ROOT)).matches()) return;

        // 诊断：记录所有浏览器入口，即使后续不拦截也能看到真实组件。
        String key = component.getShortClassName() + "|" + (url.length() > 80 ? url.substring(0, 80) : url);
        if (SEEN_CANDIDATES.add(key)) {
            log("browser candidate: " + key);
        }

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
            log("Opened in system browser (" + component.getShortClassName() + "): " + url);
        } catch (Throwable failure) {
            // 失败时放行原逻辑，退回 QQ 内置浏览器，不打断用户。
            log("Fallback to internal browser: " + failure);
        }
    }

    /**
     * 内置浏览器入口的类名，覆盖 QQ 的各种网页容器：
     * QQBrowserActivity、QQBrowserDelegationActivity、PublicAccountBrowserImpl（扫码/公众号）、
     * 以及其它 *BrowserActivity / *WebViewActivity。
     */
    private static boolean isBrowserActivity(String shortClassName) {
        if (shortClassName == null) return false;
        return shortClassName.contains("Browser")
                || shortClassName.contains("WebViewActivity");
    }

    private static String extractUrl(Intent intent) {
        String url = intent.getStringExtra("url");
        if (url == null || url.isEmpty()) url = intent.getStringExtra("arg_url");
        if (url == null || url.isEmpty()) url = intent.getStringExtra("url_str");
        if ((url == null || url.isEmpty()) && intent.getData() != null) {
            url = intent.getData().toString();
        }
        return url == null ? null : url.trim();
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
