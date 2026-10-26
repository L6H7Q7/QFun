package me.yxp.qfun.custom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;
import de.robv.android.xposed.XposedHelpers;

public final class UiCleanupSettings {
    private static final String PREFS = "QFun_InterfaceCleanup";
    private static volatile Context host;
    private static volatile Boolean supported;
    private static ClassLoader moduleLoader;
    public static void init(ClassLoader loader) { moduleLoader = loader; }
    static Context hostContext() {
        if (host != null) return host;
        try {
            Object info = XposedHelpers.getStaticObjectField(
                    XposedHelpers.findClass("me.yxp.qfun.utils.qq.HostInfo", moduleLoader), "INSTANCE");
            Context value = (Context) XposedHelpers.callMethod(info, "getHostContext");
            if (value != null) host = value.getApplicationContext();
        } catch (Throwable ignored) {}
        return host;
    }
    static boolean target(Context context) {
        if (context == null) return false;
        if (supported != null) return supported;
        try {
            supported = "com.tencent.mobileqq".equals(context.getPackageName()) &&
                    "9.2.27".equals(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
            return supported;
        } catch (Throwable ignored) { return false; }
    }
    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    public static boolean favoritesOnly(Context context) {
        return target(context) && preferences(context).getBoolean("favorites_only", true);
    }
    public static boolean sidebar(Context context) {
        return target(context) && preferences(context).getBoolean("sidebar_only_three", true);
    }
    public static void show(Activity activity) {
        SharedPreferences prefs = preferences(activity);
        boolean[] checked = {prefs.getBoolean("favorites_only", true), prefs.getBoolean("sidebar_only_three", true)};
        new AlertDialog.Builder(activity)
                .setTitle("界面精简设置")
                .setMultiChoiceItems(new String[]{"表情按钮直接进入收藏，隐藏分类栏", "侧边栏只留收藏、钱包、相册；精简 QQ 秀资料卡"}, checked,
                        (dialog, which, value) -> checked[which] = value)
                .setPositiveButton("保存", (dialog, which) -> {
                    prefs.edit().putBoolean("favorites_only", checked[0]).putBoolean("sidebar_only_three", checked[1]).apply();
                    Toast.makeText(activity, "已保存，请结束 QQ 进程后重新打开", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    private UiCleanupSettings() {}
}
