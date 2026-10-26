package me.yxp.qfun.custom;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;

/** Minimal shared-flow experiment: original request builders are deliberately unchanged. */
public final class RedPacketTrial {
    private static final String PREFIX = "me.yxp.qfun.hook.redpacket.";
    private static final String TRACE = "QFun.RedPacketTrace";
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Map<String, Long> SEEN = new ConcurrentHashMap<>();
    private static ClassLoader moduleLoader;
    private static Class<?> dataClass, contextClass;
    private static Object helper;
    private static boolean installationAttempted;
    static final class Trace {
        final String id;
        final int chatType;
        final AtomicBoolean finished = new AtomicBoolean();
        Trace(int type) { chatType = type; id = "rp-" + SEQUENCE.incrementAndGet(); }
        void log(String event, String safeDetail) { RedPacketTrialLog.write(id, event, safeDetail); }
    }

    public static void schedule(ClassLoader loader) {
        try {
            Class<?> module = XposedHelpers.findClass("me.yxp.qfun.common.ModuleLoader", loader);
            XposedHelpers.findAndHookMethod(module, "injectClassLoader", ClassLoader.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam hook) {
                    if (!hook.hasThrowable()) install(loader, (ClassLoader) hook.args[0]);
                }
            });
            RedPacketTrialLog.write("setup", "BOOTSTRAP_REGISTERED", "waiting_for_QFun_host_classloader");
        } catch (Throwable error) { RedPacketTrialLog.write("setup", "BOOTSTRAP_FAILED", RedPacketTrialLog.error(error)); }
    }
    private static synchronized void install(ClassLoader loader, ClassLoader hostLoader) {
        if (installationAttempted) return;
        installationAttempted = true;
        moduleLoader = loader;
        String stage = "defaults";
        try {
            QFunDefaults.installRedPacketDefaults(loader);
            stage = "helper_class";
            Class<?> helperClass = XposedHelpers.findClass(PREFIX + "RedPacketHelper", loader);
            helper = XposedHelpers.getStaticObjectField(helperClass, "INSTANCE");
            stage = "context_types";
            contextClass = XposedHelpers.findClass(PREFIX + "RedPacketContext", loader);
            dataClass = XposedHelpers.findClass("me.yxp.qfun.plugin.bean.MsgData", loader);
            stage = "stage_hooks";
            installStageLogs(helperClass);
            stage = "notification_prefix";
            installPrivatePrefix(helperClass);
            stage = "coroutine_completion";
            installCompletion(loader);
            stage = "receiver_hook";
            Class<?> auto = XposedHelpers.findClass(PREFIX + "AutoGrabHb", loader);
            Class<?> record = XposedHelpers.findClass("com.tencent.qqnt.kernel.nativeinterface.MsgRecord", hostLoader);
            XposedHelpers.findAndHookMethod(auto, "onReceive", record, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    // Replace only this module's receiver; do not mutate the host message or contact.
                    hook.setResult(null);
                    receive(hook.thisObject, hook.args[0]);
                }
            });
            stage = "settings_hook";
            installSettings(auto);
            RedPacketTrialLog.write("setup", "READY", "group_and_other_scopes; whitelist_removed; types=1,32; request_builders=unchanged");
            new Handler(Looper.getMainLooper()).postDelayed(() -> RedPacketTrialLog.write("setup", "SESSION_READY", "awaiting_packet_messages"), 2000);
        } catch (Throwable error) {
            RedPacketTrialLog.write("setup", "INSTALL_FAILED", "stage=" + stage + " " + RedPacketTrialLog.error(error));
        }
    }

    private static void receive(Object auto, Object record) {
        Trace trace = null;
        try {
            if (record == null || XposedHelpers.getIntField(record, "msgType") != 10) return;
            int chatType = XposedHelpers.getIntField(record, "chatType");
            trace = new Trace(chatType);
            trace.log("RECEIVED", "scope=" + RedPacketTrialRules.scope(chatType) + " chatType=" + chatType);
            if (!Boolean.TRUE.equals(XposedHelpers.callMethod(auto, "isEnable"))) { trace.log("SKIP", "master_off"); return; }
            if (!UiCleanupSettings.target(UiCleanupSettings.hostContext())) { trace.log("SKIP", "unsupported_host"); return; }
            if (!RedPacketTrialRules.scopeEnabled(chatType, RedPacketTrialSettings.group(), RedPacketTrialSettings.other())) {
                trace.log("SKIP", "scope_off"); return;
            }
            Object wallet = null;
            Object elements = XposedHelpers.getObjectField(record, "elements");
            if (elements instanceof Iterable) for (Object element : (Iterable<?>) elements) {
                if (element == null) continue;
                Object candidate = XposedHelpers.getObjectField(element, "walletElement");
                if (candidate != null) { wallet = candidate; break; }
            }
            if (wallet == null) { trace.log("SKIP", "wallet_element_missing"); return; }
            int channel = XposedHelpers.getIntField(wallet, "redChannel");
            if (!RedPacketTrialRules.supported(channel)) { trace.log("SKIP", "unsupported_channel=" + channel); return; }
            Object config = XposedHelpers.callMethod(auto, "getConfig");
            Object selected = XposedHelpers.callMethod(config, "getTargetTypes");
            if (!(selected instanceof java.util.Set) || !((java.util.Set<?>) selected).contains(channel)) {
                trace.log("SKIP", "channel_not_selected=" + channel); return;
            }
            Object receiver = XposedHelpers.getObjectField(wallet, "receiver");
            String title = receiver == null ? "" : (String) XposedHelpers.getObjectField(receiver, "title");
            Object keywords = XposedHelpers.callMethod(config, "getKeywords");
            if (RedPacketTrialRules.keyword(title, keywords instanceof Iterable ? (Iterable<?>) keywords : null) != null) {
                trace.log("SKIP", "keyword_match"); return;
            }
            String bill = (String) XposedHelpers.getObjectField(wallet, "billNo");
            String auth = (String) XposedHelpers.getObjectField(wallet, "authkey");
            if (bill == null || bill.isEmpty() || auth == null || auth.isEmpty()) { trace.log("SKIP", "required_fields_missing"); return; }
            // In-memory dedup key is never printed or written to disk.
            String account = RedPacketTrialSettings.account();
            String key = account + ":" + bill;
            long now = android.os.SystemClock.elapsedRealtime();
            if (SEEN.size() > 512) for (Map.Entry<String, Long> entry : SEEN.entrySet()) {
                if (now - entry.getValue() > 10 * 60 * 1000L) SEEN.remove(entry.getKey(), entry.getValue());
            }
            Long old = SEEN.putIfAbsent(key, now);
            if (old != null) { trace.log("SKIP", "duplicate_delivery"); return; }
            Object data = XposedHelpers.newInstance(dataClass, record);
            Object context = XposedHelpers.newInstance(contextClass, data, bill, auth, channel, title == null ? "" : title, true, config);
            XposedHelpers.setAdditionalInstanceField(context, TRACE, trace);
            trace.log("DISPATCH", "channel=" + RedPacketTrialRules.channel(channel) + "; original_request_flow; whitelist_ignored");
            if (chatType != 2) trace.log("TRIAL_PARAMETERS", "request_group_type_still_1; actual_contact_preserved");
            final Trace pending = trace;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!pending.finished.get()) pending.log("NO_SUCCESS_WITHIN_90S", "inspect_stage_logs; no_automatic_retry");
            }, 90_000);
            XposedHelpers.callMethod(helper, "startGrab", context);
        } catch (Throwable error) {
            if (trace != null) { trace.finished.set(true); trace.log("RECEIVER_FAILED", RedPacketTrialLog.error(error)); }
            else RedPacketTrialLog.write("receiver", "FAILED", RedPacketTrialLog.error(error));
        }
    }

    private static Trace trace(Object context) {
        return context == null ? null : (Trace) XposedHelpers.getAdditionalInstanceField(context, TRACE);
    }
    private static Object findContext(Object[] args) {
        for (Object arg : args) if (arg != null && contextClass.isInstance(arg)) return arg;
        return null;
    }
    private static boolean suspended(Object result) {
        return result instanceof Enum && "COROUTINE_SUSPENDED".equals(((Enum<?>) result).name());
    }
    private static void installStageLogs(Class<?> helperClass) {
        for (Method method : helperClass.getDeclaredMethods()) {
            String name = method.getName();
            if (!java.util.Arrays.asList("startGrab", "preGrabOld", "preGrabNew", "checkRedPacketDetails",
                    "handleLuckyRedPacket", "handleCommandRedPacket", "sendCommandMsgAndAwait", "finalGrab", "onSuccess").contains(name)) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    Trace trace = trace(findContext(hook.args));
                    if (trace != null) trace.log("STAGE_ENTER", name);
                }
                @Override protected void afterHookedMethod(MethodHookParam hook) {
                    Trace trace = trace(findContext(hook.args));
                    if (trace == null) return;
                    if (hook.hasThrowable()) { trace.log("STAGE_THROWN", name + " " + RedPacketTrialLog.error(hook.getThrowable())); return; }
                    if (name.equals("onSuccess")) {
                        trace.finished.set(true);
                        trace.log("SUCCESS", "helper_confirmed; receipt_and_reply_follow_original_config");
                        return;
                    }
                    Object result = hook.getResult();
                    if (suspended(result)) { trace.log("STAGE_WAIT", name); return; }
                    String detail = name;
                    if (result instanceof JSONObject) for (String key : new String[]{"retcode", "ret_code", "re_code", "code"}) {
                        Object code = ((JSONObject) result).opt(key);
                        if (code instanceof Number) detail += " " + key + "=" + code;
                    }
                    trace.log("STAGE_RETURN", detail);
                }
            });
        }
    }
    private static void installPrivatePrefix(Class<?> helperClass) {
        for (Method method : helperClass.getDeclaredMethods()) {
            if (!method.getName().equals("getPrefix")) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    Trace trace = trace(findContext(hook.args));
                    if (trace != null && trace.chatType != 2) {
                        hook.setResult("[QFun]自动领取红包（试验版）\n会话：非群聊（type=" + trace.chatType + "）\n");
                    }
                }
            });
        }
    }
    private static void installCompletion(ClassLoader loader) {
        Class<?> main = XposedHelpers.findClass(PREFIX + "RedPacketHelper$startGrab$1", loader);
        XposedHelpers.findAndHookMethod(main, "invokeSuspend", Object.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                Trace trace = trace(XposedHelpers.getObjectField(hook.thisObject, "$ctx"));
                if (trace == null || suspended(hook.getResult()) || trace.finished.get()) return;
                trace.finished.set(true);
                trace.log(hook.hasThrowable() ? "MAIN_FAILED" : "FINISHED_WITHOUT_SUCCESS",
                        hook.hasThrowable() ? RedPacketTrialLog.error(hook.getThrowable()) : "no_success_callback; inspect_previous_stages");
            }
        });
    }
    private static void installSettings(Class<?> auto) {
        for (Method method : auto.getDeclaredMethods()) {
            if (!method.getName().equals("ConfigContent") || method.getParameterTypes().length != 3) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    hook.setResult(null);
                    Object dismiss = hook.args[0];
                    new Handler(Looper.getMainLooper()).post(() -> {
                        try {
                            XposedHelpers.callMethod(dismiss, "invoke");
                            Object env = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("me.yxp.qfun.utils.qq.QQCurrentEnv", moduleLoader), "INSTANCE");
                            Activity activity = (Activity) XposedHelpers.callMethod(env, "getActivity");
                            if (activity != null && !activity.isFinishing()) RedPacketTrialSettings.show(activity, hook.thisObject);
                        } catch (Throwable error) { RedPacketTrialLog.write("settings", "FAILED", RedPacketTrialLog.error(error)); }
                    });
                }
            });
        }
    }
    private RedPacketTrial() {}
}
