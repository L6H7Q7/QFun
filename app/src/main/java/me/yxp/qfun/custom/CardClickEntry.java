package me.yxp.qfun.custom;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Extra native entry inside QFun 1.3.4, targeted to QQ 9.2.27 / 12160. */
public final class CardClickEntry implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String TAG = "QFunCardClick";
    private static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<>();
    private static final ConcurrentHashMap<String, Boolean> PENDING = new ConcurrentHashMap<>();
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "QFun-card-link");
        thread.setDaemon(true);
        return thread;
    });
    private static boolean initialized;
    private String moduleApkPath;

    @Override public void initZygote(StartupParam param) {
        moduleApkPath = param.modulePath;
        QFunDefaults.install(CardClickEntry.class.getClassLoader());
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!"com.tencent.mobileqq".equals(param.packageName) || !param.packageName.equals(param.processName) || initialized) return;
        initialized = true;
        CustomSplashFeature.install(param.classLoader, CardClickEntry.class.getClassLoader(), moduleApkPath);
        CardClickSettings.install(CardClickEntry.class.getClassLoader());
        QFunUiCleanup.install(CardClickEntry.class.getClassLoader());
        UiCleanupFeature.install(param.classLoader, CardClickEntry.class.getClassLoader());
        int hooked = 0;
        try {
            Class<?> clickClass = XposedHelpers.findClass(
                "com.tencent.mobileqq.aio.msglist.holder.component.template.jump.c", param.classLoader);
            Method click = clickClass.getDeclaredMethod("onClick", View.class, String.class, String.class);
            XposedBridge.hookMethod(click, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (Boolean.TRUE.equals(REPLAYING.get())) return;
                    try {
                        Activity activity = activityOf(((View) hook.args[0]).getContext());
                        Object wrapper = XposedHelpers.getObjectField(hook.thisObject, "a");
                        Object record = XposedHelpers.callMethod(wrapper, "f");
                        intercept(hook, activity, record, (String) hook.args[2], null);
                    } catch (Throwable failure) {
                        log("Template click left unchanged: " + failure.getClass().getSimpleName());
                    }
                }
            });
            hooked++;
        } catch (Throwable failure) {
            log("Template hook unavailable: " + failure);
        }
        // Also cover Ark cards that QQ renders through its older card path.
        try {
            Class<?> api = XposedHelpers.findClass("com.tencent.qqnt.aio.adapter.api.impl.AIOArkApiImpl", param.classLoader);
            for (Method method : api.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!method.getName().equals("jumpUrl") || method.getReturnType() != boolean.class || types.length != 4
                    || types[0] != String.class || !types[1].getName().equals("com.tencent.qqnt.kernel.nativeinterface.MsgRecord")
                    || types[2] != Activity.class) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam hook) {
                        if (Boolean.TRUE.equals(REPLAYING.get())) return;
                        try {
                            intercept(hook, (Activity) hook.args[2], hook.args[1], (String) hook.args[0], Boolean.TRUE);
                        } catch (Throwable failure) {
                            log("Ark click left unchanged: " + failure.getClass().getSimpleName());
                        }
                    }
                });
                hooked++;
            }
        } catch (Throwable failure) {
            log("Ark fallback hook unavailable: " + failure);
        }
        log("QQ 9.2.27 native card click hooks installed: " + hooked);
    }

    private static void intercept(XC_MethodHook.MethodHookParam hook, Activity activity,
                                  Object record, String clicked, Object consumedResult) throws Exception {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || record == null) return;
        if (!CardClickSettings.enabled(activity)) return;
        if (!"9.2.27".equals(activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName)) return;
        CardLinkRouter.Target target = CardLinkRouter.find(clicked, cardJson(record));
        if (target == null) return;
        List<String> installed = installedPackages(activity, CardClickSettings.packages(activity, target.family));
        if (installed.isEmpty()) return;
        if (!target.shortLink) {
            if (launch(activity, target.url, installed)) hook.setResult(consumedResult);
            return;
        }
        String key = System.identityHashCode(record) + ":" + target.url;
        if (PENDING.putIfAbsent(key, Boolean.TRUE) != null) {
            hook.setResult(consumedResult);
            return;
        }
        Member original = hook.method;
        Object receiver = hook.thisObject;
        Object[] arguments = hook.args.clone();
        try {
            NETWORK.execute(() -> {
                String resolved = resolveShortLink(target);
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        boolean enabled = CardClickSettings.enabled(activity);
                        boolean opened = enabled && launch(activity, resolved, installed);
                        if (enabled && !opened && !target.url.equals(resolved)) opened = launch(activity, target.url, installed);
                        if (!opened) {
                            log("App could not open link; replaying QQ click");
                            REPLAYING.set(Boolean.TRUE);
                            try {
                                XposedBridge.invokeOriginalMethod(original, receiver, arguments);
                            } finally {
                                REPLAYING.remove();
                            }
                        }
                    } catch (Throwable failure) {
                        log("Original click replay failed: " + failure);
                    } finally {
                        PENDING.remove(key);
                    }
                });
            });
            // Only consume after asynchronous work was successfully submitted.
            hook.setResult(consumedResult);
        } catch (RuntimeException failure) {
            PENDING.remove(key);
            throw failure;
        }
    }

    private static String cardJson(Object record) {
        try {
            Object elements = property(record, "getElements", "elements");
            if (!(elements instanceof Iterable)) return null;
            for (Object element : (Iterable<?>) elements) {
                if (element == null) continue;
                Object ark = property(element, "getArkElement", "arkElement");
                if (ark == null) continue;
                Object bytes = property(ark, "getBytesData", "bytesData");
                if (bytes instanceof String && !((String) bytes).trim().isEmpty()) return (String) bytes;
            }
        } catch (Throwable ignored) {
            // An unrecognized message representation must retain its original behavior.
        }
        return null;
    }

    private static Object property(Object object, String getter, String field) {
        try {
            return XposedHelpers.callMethod(object, getter);
        } catch (Throwable noGetter) {
            try {
                return XposedHelpers.getObjectField(object, field);
            } catch (Throwable noField) {
                return null;
            }
        }
    }

    private static List<String> installedPackages(Activity activity, String[] packages) {
        List<String> installed = new ArrayList<>();
        for (String name : packages) try {
            ApplicationInfo info = activity.getPackageManager().getApplicationInfo(name, 0);
            if (info.enabled) installed.add(name);
        } catch (Exception ignored) {}
        return installed;
    }

    private static boolean launch(Activity activity, String url, List<String> packages) {
        for (String name : packages) try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(name));
            log("Opened card in " + name);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Try the next supported installed application.
        } catch (RuntimeException failure) {
            log("External launch failed: " + failure.getClass().getSimpleName());
        }
        return false;
    }

    private static String resolveShortLink(CardLinkRouter.Target target) {
        String current = target.url;
        long deadline = System.nanoTime() + 6_000_000_000L;
        for (int hop = 0; hop < 3; hop++) {
            HttpURLConnection connection = null;
            try {
                int timeout = (int) Math.min(1800, (deadline - System.nanoTime()) / 1_000_000L);
                if (timeout <= 0) break;
                connection = (HttpURLConnection) new URL(current).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36");
                int response = connection.getResponseCode();
                if (response != 301 && response != 302 && response != 303 && response != 307 && response != 308) break;
                String location = connection.getHeaderField("Location");
                if (location == null) break;
                String next = new URI(current).resolve(location).toASCIIString();
                CardLinkRouter.Target nextTarget = CardLinkRouter.fromUrl(next);
                if (nextTarget == null || !nextTarget.family.equals(target.family)) break;
                current = next;
                if (!nextTarget.shortLink) break;
            } catch (Exception failure) {
                log("Short link resolution failed: " + failure.getClass().getSimpleName());
                break;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return current;
    }

    private static Activity activityOf(Context context) {
        for (int i = 0; i < 12; i++) {
            if (context instanceof Activity) return (Activity) context;
            if (!(context instanceof ContextWrapper)) return null;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) return null;
            context = next;
        }
        return null;
    }

    private static void log(String message) {
        Log.i(TAG, message);
        XposedBridge.log("[" + TAG + "] " + message);
    }
}
