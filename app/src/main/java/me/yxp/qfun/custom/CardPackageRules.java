package me.yxp.qfun.custom;

import java.util.LinkedHashSet;
import java.util.Set;

/** Validates package preferences without changing their requested priority. */
public final class CardPackageRules {
    public static final String[] FAMILIES = {"bilibili", "xiaohongshu", "coolapk", "zhihu"};
    public static final String[] LABELS = {"B 站 / PiliPlus / 国际版", "小红书", "酷安", "知乎"};

    private CardPackageRules() {}

    public static String[] defaults(String family) {
        switch (family) {
            case "bilibili": return new String[]{"com.example.piliplus", "tv.danmaku.bili", "com.bilibili.app.in", "com.bstar.intl", "tv.danmaku.bilibilihd", "com.bilibili.app.blue"};
            case "xiaohongshu": return new String[]{"com.xingin.xhs"};
            case "coolapk": return new String[]{"com.coolapk.market"};
            case "zhihu": return new String[]{"com.zhihu.android"};
            default: return new String[0];
        }
    }

    public static String[] parse(String input) {
        if (input == null || input.trim().isEmpty()) return new String[0];
        Set<String> names = new LinkedHashSet<>();
        for (String value : input.trim().split("[,，;；\\s]+")) {
            if (value.isEmpty()) continue;
            if (!value.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) {
                throw new IllegalArgumentException("无效包名：" + value);
            }
            names.add(value);
        }
        return names.toArray(new String[0]);
    }

    public static String join(String[] names) {
        StringBuilder text = new StringBuilder();
        for (String name : names) {
            if (text.length() != 0) text.append(", ");
            text.append(name);
        }
        return text.toString();
    }
}
