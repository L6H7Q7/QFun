package me.yxp.qfun.custom;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class RedPacketTrialRules {
    public static Set<Integer> defaults() { return new LinkedHashSet<>(Arrays.asList(1, 32)); }
    public static boolean scopeEnabled(int chatType, boolean group, boolean other) { return chatType == 2 ? group : other; }
    public static boolean supported(int channel) { return channel == 1 || channel == 32; }
    public static String scope(int chatType) { return chatType == 2 ? "group" : chatType == 1 ? "private" : "other"; }
    public static String channel(int type) { return type == 1 ? "normal" : type == 32 ? "command" : "unsupported"; }
    public static String keyword(String title, Iterable<?> keywords) {
        if (title == null || keywords == null) return null;
        for (Object key : keywords) if (key instanceof String && !((String) key).isEmpty() && title.contains((String) key)) return "keyword_match";
        return null;
    }
    private RedPacketTrialRules() {}
}
