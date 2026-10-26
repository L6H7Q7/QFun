package me.yxp.qfun.custom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Adds a real settings item to QFun's existing top-right dropdown. */
public final class CardClickSettings {
    private static final String TITLE = "卡片跳转设置";
    private static final String SPLASH_TITLE = "启动画面设置";
    private static final String PREFS = "QFun_CardClick";
    private static final String ENABLED = "enabled";

    private CardClickSettings() {}

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        return preferences(context).getBoolean(ENABLED, true);
    }

    public static String[] packages(Context context, String family) {
        SharedPreferences prefs = preferences(context);
        String key = "packages." + family;
        if (!prefs.contains(key)) return CardPackageRules.defaults(family);
        try {
            return CardPackageRules.parse(prefs.getString(key, ""));
        } catch (IllegalArgumentException corruptValue) {
            return CardPackageRules.defaults(family);
        }
    }

    public static void install(ClassLoader loader) {
        try {
            Class<?> topBar = XposedHelpers.findClass("me.yxp.qfun.ui.components.molecules.SearchTopBarKt", loader);
            Class<?> itemClass = XposedHelpers.findClass("me.yxp.qfun.ui.components.molecules.TopBarMenuItem", loader);
            Class<?> function0 = XposedHelpers.findClass("kotlin.jvm.functions.Function0", loader);
            Object unit = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("kotlin.Unit", loader), "INSTANCE");
            for (Method method : topBar.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!method.getName().equals("SearchTopBar") || types.length != 16 || types[8] != List.class) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                        Object current = hook.args[8];
                        if (!(current instanceof List)) return;
                        List<?> items = (List<?>) current;
                        boolean settingsMenu = false;
                        boolean hasCard = false, hasSplash = false;
                        int icon = 0;
                        for (Object item : items) {
                            String title = (String) XposedHelpers.callMethod(item, "getTitle");
                            if (TITLE.equals(title)) hasCard = true;
                            if (SPLASH_TITLE.equals(title)) hasSplash = true;
                            if ("导出配置".equals(title)) settingsMenu = true;
                            icon = (Integer) XposedHelpers.callMethod(item, "getIconRes");
                        }
                        if (!settingsMenu || (hasCard && hasSplash)) return;
                        List<Object> updated = new ArrayList<>(items);
                        for (String title : new String[]{TITLE, SPLASH_TITLE}) {
                            if ((TITLE.equals(title) && hasCard) || (SPLASH_TITLE.equals(title) && hasSplash)) continue;
                            Object callback = Proxy.newProxyInstance(function0.getClassLoader(), new Class<?>[]{function0}, (proxy, called, args) -> {
                            switch (called.getName()) {
                                case "invoke":
                                    Object env = XposedHelpers.getStaticObjectField(
                                        XposedHelpers.findClass("me.yxp.qfun.utils.qq.QQCurrentEnv", loader), "INSTANCE");
                                    Activity activity = (Activity) XposedHelpers.callMethod(env, "getActivity");
                                    if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                                        if (SPLASH_TITLE.equals(title)) CustomSplashFeature.show(activity); else show(activity);
                                    }
                                    return unit;
                                case "toString": return "QFunSettings:" + title;
                                case "hashCode": return System.identityHashCode(proxy);
                                case "equals": return args != null && args.length == 1 && proxy == args[0];
                                default: return null;
                            }
                            });
                            updated.add(itemClass.getConstructor(String.class, int.class, function0).newInstance(title, icon, callback));
                        }
                        hook.args[8] = updated;
                    }
                });
                XposedBridge.log("[QFunCardClick] Settings menu hook installed");
                return;
            }
            XposedBridge.log("[QFunCardClick] Settings menu signature was not found");
        } catch (Throwable failure) {
            XposedBridge.log("[QFunCardClick] Settings menu unavailable: " + failure);
        }
    }

    private static void show(Activity activity) {
        boolean dark = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        Context context = new ContextThemeWrapper(activity, dark ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density + 0.5f);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad / 2, pad, pad / 2);
        Switch master = new Switch(context);
        master.setText("启用卡片单击跳转");
        master.setChecked(enabled(activity));
        body.addView(master);
        TextView description = new TextView(context);
        description.setText("多个包名用逗号或换行分隔，优先尝试排在前面的应用。留空则不接管该类链接。保存后下次点击即生效。");
        description.setPadding(0, pad / 2, 0, pad / 2);
        body.addView(description);
        EditText[] editors = new EditText[CardPackageRules.FAMILIES.length];
        for (int i = 0; i < editors.length; i++) {
            TextView label = new TextView(context);
            label.setText(CardPackageRules.LABELS[i]);
            label.setPadding(0, pad / 2, 0, 0);
            body.addView(label);
            EditText editor = new EditText(context);
            editor.setTextSize(14);
            editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setHint("例如：com.example.piliplus");
            editor.setMinLines(2);
            editor.setMaxLines(5);
            editor.setText(CardPackageRules.join(packages(activity, CardPackageRules.FAMILIES[i])));
            editors[i] = editor;
            body.addView(editor, new LinearLayout.LayoutParams(-1, -2));
        }
        TextView help = new TextView(context);
        help.setText("B 站默认优先 PiliPlus；Google Play 版为 com.bilibili.app.in，海外 BiliBili/Bstation 为 com.bstar.intl。目标应用需已安装并支持相应链接。");
        help.setPadding(0, pad / 2, 0, 0);
        body.addView(help);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(TITLE).setView(scroll)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .setNeutralButton("恢复默认", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                master.setChecked(true);
                for (int i = 0; i < editors.length; i++) {
                    editors[i].setText(CardPackageRules.join(CardPackageRules.defaults(CardPackageRules.FAMILIES[i])));
                    editors[i].setError(null);
                }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String[][] values = new String[editors.length][];
                for (int i = 0; i < editors.length; i++) {
                    try {
                        values[i] = CardPackageRules.parse(editors[i].getText().toString());
                        editors[i].setError(null);
                    } catch (IllegalArgumentException invalid) {
                        editors[i].setError(invalid.getMessage());
                        editors[i].requestFocus();
                        return;
                    }
                }
                SharedPreferences.Editor save = preferences(activity).edit().putBoolean(ENABLED, master.isChecked());
                for (int i = 0; i < editors.length; i++) save.putString("packages." + CardPackageRules.FAMILIES[i], CardPackageRules.join(values[i]));
                save.apply();
                Toast.makeText(activity, "已保存，下次点击卡片即生效", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });
        dialog.show();
    }
}
