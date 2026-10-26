package me.yxp.qfun.custom;

public final class CardLinkRouterTest {
    private static int checks;
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
    private static String card(String url) {
        return "{\"app\":\"com.tencent.miniapp_01\",\"meta\":{\"detail_1\":{\"qqdocurl\":\"" + url + "\"}}}";
    }
    public static void main(String[] args) {
        CardLinkRouter.Target bili = CardLinkRouter.find("mqqapi://miniapp/open", card("https:\\/\\/b23.tv\\/ABC123"));
        check(bili != null && bili.family.equals("bilibili") && bili.shortLink, "Escaped Bilibili short link");
        check(bili.url.equals("https://b23.tv/ABC123"), "JSON slash decoding");
        check(bili.packages[0].equals("com.example.piliplus"), "PiliPlus preferred");
        check(CardLinkRouter.find(null, card("https://www.bilibili.com/video/BV123?x=1&y=2")).url.endsWith("x=1&y=2"), "Preserve query data");
        check(CardLinkRouter.find(null, card("https://www.xiaohongshu.com/discovery/item/123")).family.equals("xiaohongshu"), "Xiaohongshu card");
        check(CardLinkRouter.find(null, card("https://xhslink.com/a/123")).shortLink, "XHS short link");
        check(CardLinkRouter.find(null, "{\"meta\":{\"news\":{\"jumpUrl\":\"https://www.coolapk.com/feed/123\"}}}").family.equals("coolapk"), "Coolapk jumpUrl");
        check(CardLinkRouter.find(null, card("https://www.zhihu.com/question/123")).family.equals("zhihu"), "Zhihu card");
        check(CardLinkRouter.find("https://www.zhihu.com/question/456", card("https://b23.tv/123")).family.equals("zhihu"), "Clicked destination wins");
        check(CardLinkRouter.find(null, card("https://bilibili.com.evil.example/video/1")) == null, "Domain suffix spoof ignored");
        check(CardLinkRouter.find(null, card("https://bilibili.com@evil.example/")) == null, "User-info URL ignored");
        check(CardLinkRouter.find(null, card("https://www.example.com/")) == null, "Unknown service preserves QQ behavior");
        check(CardLinkRouter.find(null, "{\"meta\":{\"preview\":{\"url\":\"https://www.zhihu.com/a.png\"}}}") == null, "Preview is not a destination");
        check(CardLinkRouter.find(null, "{\"title\":\"https://b23.tv/123\"}") == null, "Title text is not a destination");
        check(CardLinkRouter.find(null, "{\"app\":\"com.tencent.multimsg\",\"meta\":{\"url\":\"https://b23.tv/123\"}}") == null, "Forwarded chats retain QQ behavior");
        check(CardLinkRouter.find("https://b23.tv/123", "{invalid") == null, "Malformed payload retains QQ behavior");
        check(CardLinkRouter.find("mqqapi://miniapp/open?url=https%3A%2F%2Fb23.tv%2FABC", null).family.equals("bilibili"), "QQ-wrapped destination");
        check(CardLinkRouter.fromUrl("https://b23.tv/ABC").shortLink, "Short URL recognized after redirect");
        check(CardLinkRouter.find(null, card("https://www.bilibili.com/video/BV123?from=qq&amp;foo=bar")).url.endsWith("from=qq&foo=bar"), "HTML entity decoding");
        System.out.println("PASS: " + checks + " routing checks");
    }
}
