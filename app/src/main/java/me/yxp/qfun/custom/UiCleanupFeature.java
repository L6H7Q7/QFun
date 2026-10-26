package me.yxp.qfun.custom;

import android.content.Context;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/** QQ 9.2.27 binary-verified UI targets. No global View/TextView hooks. */
public final class UiCleanupFeature {
    private static final String EMO = "com.tencent.mobileqq.emoticonview.";
    private static final Set<String> WARNED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static Class<?> panelModel;
    private static final ThreadLocal<Boolean> APPLYING = new ThreadLocal<>();

    public static void install(ClassLoader hostLoader, ClassLoader moduleLoader) {
        UiCleanupSettings.init(moduleLoader);
        try { installSidebar(hostLoader); } catch (Throwable e) { warn("Sidebar hooks", e); }
        try { installEmoticons(hostLoader); } catch (Throwable e) { warn("Emoticon hooks", e); }
        try { installCompactProfile(hostLoader); } catch (Throwable e) { warn("Profile hooks", e); }
    }

    private static void installSidebar(ClassLoader loader) {
        Class<?> part = XposedHelpers.findClass("com.tencent.mobileqq.parts.QQSettingMeMenuPanelPartV3", loader);
        int count = 0;
        for (Method method : part.getDeclaredMethods()) {
            if (!method.getName().equals("N9") && !method.getName().equals("s9") &&
                    !method.getName().equals("t9") && !method.getName().equals("u9")) continue;
            if (method.getReturnType() != ArrayList.class || method.getParameterTypes().length != 1) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam hook) {
                    try {
                        if (!UiCleanupSettings.sidebar(UiCleanupSettings.hostContext()) || hook.hasThrowable()) return;
                        List<?> input = (List<?>) hook.getResult();
                        List<?> selected = sidebarRows(input);
                        if (selected != input) {
                            hook.setResult(new ArrayList<>(selected));
                            active("Sidebar reduced to requested entries");
                        }
                    } catch (Throwable e) { warn("Sidebar list", e); }
                }
            });
            count++;
        }
        // Final list cleanup also covers rows appended by cloud configuration after conversion.
        XposedHelpers.findAndHookMethod(part, "I9", ArrayList.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                try {
                    if (!UiCleanupSettings.sidebar(UiCleanupSettings.hostContext())) return;
                    @SuppressWarnings("unchecked") List<Object> input = (List<Object>) hook.args[0];
                    List<?> selected = sidebarRows(input);
                    if (selected != input) { input.clear(); input.addAll(selected); }
                } catch (Throwable e) { warn("Sidebar final list", e); }
            }
        });
        log("Sidebar list hooks installed: " + (count + 1));
    }

    private static List<?> sidebarRows(List<?> source) {
        return UiCleanupRules.sidebar(source, item -> {
            Object bean = XposedHelpers.callMethod(item, "a");
            return bean == null ? null : (String) XposedHelpers.callMethod(bean, "o");
        });
    }

    private static void installCompactProfile(ClassLoader loader) {
        Class<?> part = XposedHelpers.findClass("com.tencent.mobileqq.parts.aiavatar.QQSettingMeProfileBigCardPart", loader);
        XposedHelpers.findAndHookMethod(part, "na", boolean.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                if (UiCleanupSettings.sidebar(UiCleanupSettings.hostContext())) hook.args[0] = false;
            }
        });
        log("Compact sidebar profile hook installed");
    }

    private static Object controller(Object baseView) {
        return XposedHelpers.getObjectField(baseView, "mPanelController");
    }
    private static boolean enabled(Object controller) {
        Object baseView = XposedHelpers.callMethod(controller, "getBasePanelView");
        View panel = (View) XposedHelpers.callMethod(baseView, "getMainPanel");
        if (panel == null || !UiCleanupSettings.favoritesOnly(panel.getContext())) return false;
        Object sorter = XposedHelpers.getObjectField(controller, "mPanelTabSortHelper");
        return Boolean.TRUE.equals(XposedHelpers.callMethod(sorter, "isInAIOPanel"));
    }
    private static int favoriteIndex(Object controller) {
        return (Integer) XposedHelpers.callMethod(controller, "findIndexByPanelType", UiCleanupRules.FAVORITE_PANEL_TYPE);
    }

    private static void installEmoticons(ClassLoader loader) {
        panelModel = XposedHelpers.findClass(EMO + "BasePanelModel", loader);
        Class<?> sorter = XposedHelpers.findClass(EMO + "EmoticonPanelTabSortHelper", loader);
        XposedHelpers.findAndHookMethod(sorter, "getSortEmotionPanelInfoList", List.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                try {
                    Object ctrl = XposedHelpers.getObjectField(hook.thisObject, "mPanelController");
                    if (hook.hasThrowable() || !enabled(ctrl)) return;
                    List<?> source = (List<?>) hook.getResult();
                    List<?> filtered = UiCleanupRules.favorites(source, item -> XposedHelpers.getIntField(item, "type"));
                    if (source == filtered) return;
                    XposedHelpers.setObjectField(hook.thisObject, "mFrontDisSelectedDataList", new ArrayList<>());
                    XposedHelpers.setObjectField(hook.thisObject, "mBehindDisSelectedDataList", new ArrayList<>());
                    XposedHelpers.setObjectField(hook.thisObject, "mEmotionPanelInfos", filtered);
                    XposedHelpers.setBooleanField(hook.thisObject, "mPanelTabSortEnable", false);
                    XposedHelpers.setStaticIntField(panelModel, "sLastSelectedSecondTabIndex", 0);
                    XposedHelpers.setStaticIntField(panelModel, "sOldSelectedSecondTabIndex", 0);
                    hook.setResult(filtered);
                    active("Emoticon categories reduced to favorites");
                } catch (Throwable e) { warn("Favorite category list", e); }
            }
        });
        Class<?> baseView = XposedHelpers.findClass(EMO + "BasePanelView", loader);
        XposedHelpers.findAndHookMethod(baseView, "initEmoticonView", int.class, int.class, String.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                try {
                    if (enabled(controller(hook.thisObject))) {
                        hook.args[0] = UiCleanupRules.FAVORITE_PANEL_TYPE;
                        hook.args[1] = 0;
                        hook.args[2] = null;
                    }
                } catch (Throwable e) { warn("Favorite default page", e); }
            }
        });
        XposedHelpers.findAndHookMethod(baseView, "setViewPagerAdapter", long.class, int.class, boolean.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                try {
                    Object ctrl = controller(hook.thisObject);
                    if (enabled(ctrl)) {
                        int index = favoriteIndex(ctrl);
                        if (index >= 0) hook.args[1] = index;
                    }
                } catch (Throwable e) { warn("Favorite adapter selection", e); }
            }
            @Override protected void afterHookedMethod(MethodHookParam hook) { applyPanel(hook.thisObject); }
        });
        XposedHelpers.findAndHookMethod(baseView, "initQueryFinish", Integer.class, long.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                try {
                    Object ctrl = controller(hook.thisObject);
                    if (enabled(ctrl)) {
                        int index = favoriteIndex(ctrl);
                        if (index >= 0) hook.args[0] = index;
                    }
                } catch (Throwable e) { warn("Favorite query selection", e); }
            }
            @Override protected void afterHookedMethod(MethodHookParam hook) { applyPanel(hook.thisObject); }
        });
        for (String name : new String[]{"initView", "onShow", "showAllTabs"}) {
            XposedHelpers.findAndHookMethod(baseView, name, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam hook) { applyPanel(hook.thisObject); }
            });
        }
        log("Favorite-only emoticon hooks installed: 8");
    }

    private static void applyPanel(Object baseView) {
        if (Boolean.TRUE.equals(APPLYING.get())) return;
        try {
            APPLYING.set(Boolean.TRUE);
            Object ctrl = controller(baseView);
            if (!enabled(ctrl)) return;
            int index = favoriteIndex(ctrl);
            if (index < 0) return; // Unsupported panel keeps its normal navigation.
            XposedHelpers.setStaticIntField(panelModel, "sLastSelectedSecondTabIndex", index);
            XposedHelpers.callMethod(ctrl, "tryToRedirectToTab", UiCleanupRules.FAVORITE_PANEL_TYPE, false);
            XposedHelpers.callMethod(baseView, "hideAllTabs");
            XposedHelpers.callMethod(ctrl, "disableHorizontalScroll");
            active("Favorite page selected; category bar hidden");
        } catch (Throwable e) { warn("Favorite panel presentation", e); }
        finally { APPLYING.remove(); }
    }
    private static void log(String text) { XposedBridge.log("[QFunUiCleanup] " + text); }
    private static void active(String text) { if (WARNED.add("active:" + text)) log(text); }
    private static void warn(String stage, Throwable error) {
        if (WARNED.add(stage)) log(stage + " left unchanged: " + error);
    }
    private UiCleanupFeature() {}
}
