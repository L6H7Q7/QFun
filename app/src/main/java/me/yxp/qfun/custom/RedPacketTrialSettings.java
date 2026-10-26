package me.yxp.qfun.custom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Replaces the original automatic-red-packet dialog; no whitelist or voice option. */
public final class RedPacketTrialSettings {
    private static AlertDialog active;
    public static String account() {
        try {
            ClassLoader loader = RedPacketTrialSettings.class.getClassLoader();
            Object env = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("me.yxp.qfun.utils.qq.QQCurrentEnv", loader), "INSTANCE");
            return String.valueOf(XposedHelpers.callMethod(env, "getCurrentUin"));
        } catch (Throwable ignored) { return "unknown"; }
    }
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("QFun_RedPacketTrial_" + account(), Context.MODE_PRIVATE);
    }
    public static boolean group() {
        Context c = UiCleanupSettings.hostContext();
        return c != null && prefs(c).getBoolean("group", true);
    }
    public static boolean other() {
        Context c = UiCleanupSettings.hostContext();
        return c != null && prefs(c).getBoolean("other", true);
    }
    public static void show(Activity activity, Object auto) {
        if (active != null && active.isShowing()) return;
        Object config = XposedHelpers.callMethod(auto, "getConfig");
        int padding = Math.round(activity.getResources().getDisplayMetrics().density * 18);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(padding, padding / 2, padding, padding / 2);
        TextView hint = new TextView(activity);
        hint.setText("开发试验版：群聊、私聊及其他非群聊共用现有领取流程。总开关关闭时都不领取。私聊领取效果需测试确认。\n领取成功后，群聊和私聊共用下方自动回复内容。");
        body.addView(hint);
        Switch group = toggle(activity, body, "自动领取群聊红包", group());
        Switch other = toggle(activity, body, "自动领取私聊／其他非群聊红包", other());
        Object types = XposedHelpers.callMethod(config, "getTargetTypes");
        Set<?> selected = types instanceof Set ? (Set<?>) types : RedPacketTrialRules.defaults();
        Switch normal = toggle(activity, body, "普通／拼手气红包", selected.contains(1));
        Switch command = toggle(activity, body, "口令红包", selected.contains(32));
        EditText delay = input(activity, body, "延迟时间（毫秒）", String.valueOf(XposedHelpers.callMethod(config, "getDelay")), true);
        EditText average = input(activity, body, "最低平均金额（分，0 为不限）", String.valueOf(XposedHelpers.callMethod(config, "getMinAverage")), true);
        EditText keywords = input(activity, body, "过滤关键词（逗号分隔，命中不领取）", join(XposedHelpers.callMethod(config, "getKeywords")), false);
        EditText reply = input(activity, body, "自动回复（逗号分隔随机选一条，留空不回复）", join(XposedHelpers.callMethod(config, "getAutoReply")), false);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("自动领取红包 · 试验版")
                .setView(scroll).setPositiveButton("保存", null).setNegativeButton("取消", null)
                .setNeutralButton("查看日志", (d, which) -> showLog(activity)).create();
        active = dialog;
        dialog.setOnDismissListener(ignored -> { if (active == dialog) active = null; });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                long wait = Long.parseLong(delay.getText().toString().trim());
                int minimum = Integer.parseInt(average.getText().toString().trim());
                if (wait < 0 || minimum < 0) throw new IllegalArgumentException();
                Set<Integer> targets = new LinkedHashSet<>();
                if (normal.isChecked()) targets.add(1);
                if (command.isChecked()) targets.add(32);
                Object updated = XposedHelpers.callMethod(config, "copy", wait, minimum,
                        new LinkedHashSet<>(split(keywords.getText().toString())), split(reply.getText().toString()),
                        new LinkedHashSet<String>(), targets);
                XposedHelpers.callMethod(auto, "updateConfig", updated);
                prefs(activity).edit().putBoolean("group", group.isChecked()).putBoolean("other", other.isChecked()).apply();
                RedPacketTrialLog.write("settings", "SAVED", "group=" + group.isChecked() + " other=" + other.isChecked()
                        + " normal=" + normal.isChecked() + " command=" + command.isChecked() + " whitelist_removed");
                Toast.makeText(activity, "已保存，下一个红包开始生效", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (NumberFormatException error) {
                Toast.makeText(activity, "延迟和金额请填写非负整数", Toast.LENGTH_LONG).show();
            } catch (IllegalArgumentException error) {
                Toast.makeText(activity, "延迟和金额不能小于 0", Toast.LENGTH_LONG).show();
            } catch (Throwable error) {
                RedPacketTrialLog.write("settings", "SAVE_FAILED", RedPacketTrialLog.error(error));
                Toast.makeText(activity, "保存失败，请查看日志", Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }
    private static Switch toggle(Context context, LinearLayout body, String label, boolean checked) {
        Switch value = new Switch(context);
        value.setText(label); value.setChecked(checked); body.addView(value);
        return value;
    }
    private static EditText input(Context context, LinearLayout body, String label, String current, boolean number) {
        TextView title = new TextView(context); title.setText(label); body.addView(title);
        EditText value = new EditText(context); value.setText(current);
        value.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        body.addView(value); return value;
    }
    private static String join(Object value) {
        List<String> parts = new ArrayList<>();
        if (value instanceof Iterable) for (Object part : (Iterable<?>) value) if (part != null) parts.add(String.valueOf(part));
        return android.text.TextUtils.join(",", parts);
    }
    private static List<String> split(String text) {
        List<String> result = new ArrayList<>();
        for (String part : text.split("[,，\\r\\n]")) if (!part.trim().isEmpty()) result.add(part.trim());
        return result;
    }
    public static void showLog(Activity activity) {
        File file = RedPacketTrialLog.file(activity);
        String content = "尚无日志。启用自动领取后，收到红包消息时会开始记录。";
        try {
            if (file.isFile()) try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
                long start = Math.max(0, input.length() - 48 * 1024);
                input.seek(start);
                byte[] data = new byte[(int) (input.length() - start)]; input.readFully(data);
                content = new String(data, StandardCharsets.UTF_8);
                if (start > 0 && content.contains("\n")) content = content.substring(content.indexOf('\n') + 1);
            }
        } catch (Throwable error) { content = "无法读取日志：" + RedPacketTrialLog.error(error); }
        String text = file.getAbsolutePath() + "\n\n" + content;
        TextView display = new TextView(activity);
        display.setText(text); display.setTextIsSelectable(true); display.setTextSize(11); display.setTypeface(Typeface.MONOSPACE);
        int pad = Math.round(activity.getResources().getDisplayMetrics().density * 12); display.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(activity); scroll.addView(display);
        new AlertDialog.Builder(activity).setTitle("红包试验日志").setView(scroll).setNegativeButton("关闭", null)
                .setPositiveButton("复制", (dialog, which) -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QFun 红包日志", text));
                    Toast.makeText(activity, "已复制日志", Toast.LENGTH_SHORT).show();
                }).show();
    }
    private RedPacketTrialSettings() {}
}
