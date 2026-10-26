package me.yxp.qfun.custom;

import java.util.Arrays;

public final class CardPackageRulesTest {
    private static int checks;
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        String[] defaults = CardPackageRules.defaults("bilibili");
        check(defaults[0].equals("com.example.piliplus"), "PiliPlus must be preferred");
        check(Arrays.asList(defaults).contains("com.bilibili.app.in"), "Google Play Bilibili included");
        check(Arrays.asList(defaults).contains("com.bstar.intl"), "Overseas Bilibili/Bstation included");
        String[] custom = CardPackageRules.parse("com.custom.client，com.bilibili.app.in\ncom.custom.client; tv.danmaku.bili");
        check(Arrays.equals(custom, new String[]{"com.custom.client", "com.bilibili.app.in", "tv.danmaku.bili"}), "Priority and deduplication");
        check(CardPackageRules.parse(" \n ").length == 0, "Empty field disables a family");
        check(Arrays.equals(custom, CardPackageRules.parse(CardPackageRules.join(custom))), "Stored settings round trip");
        for (String invalid : new String[]{"https://example.com", "com.bad-name.app", "com.example/Activity", "com..bad"}) {
            boolean rejected = false;
            try { CardPackageRules.parse(invalid); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Invalid input rejected: " + invalid);
        }
        check(CardPackageRules.defaults("unknown").length == 0, "Unknown family leaves QQ unchanged");
        System.out.println("PASS: " + checks + " package preference checks");
    }
}
