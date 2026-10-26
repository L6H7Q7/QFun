package me.yxp.qfun.custom;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Link selection shared by the native hook and desktop regression checks. */
public final class CardLinkRouter {
    private static final Set<String> URL_KEYS = new HashSet<>(Arrays.asList(
        "qqdocurl", "jumpurl", "targeturl", "shareurl", "contentjumpurl", "detailurl", "url", "link"));
    private static final Set<String> IMAGE_KEYS = new HashSet<>(Arrays.asList(
        "icon", "preview", "cover", "image", "avatar", "thumb", "thumbnail", "logo"));

    public static final class Target {
        public final String family;
        public final String url;
        public final String[] packages;
        public final boolean shortLink;

        Target(String family, String url, String[] packages, boolean shortLink) {
            this.family = family;
            this.url = url;
            this.packages = packages;
            this.shortLink = shortLink;
        }
    }

    private CardLinkRouter() {}

    public static Target find(String clickedUrl, String cardJson) {
        JSONObject card = null;
        if (cardJson != null && !cardJson.trim().isEmpty()) {
            if (cardJson.length() > 1024 * 1024) return null;
            try {
                card = new JSONObject(cardJson);
                String app = card.optString("app");
                if ("com.tencent.multimsg".equals(app) || "com.tencent.picture.lua".equals(app)) return null;
            } catch (Exception invalidCard) {
                // Do not intercept malformed card payloads.
                return null;
            }
        }
        Target clicked = fromUrl(clickedUrl);
        if (clicked != null) return clicked;
        return card == null ? null : fromJson(card, 0);
    }

    private static Target fromJson(Object node, int depth) {
        if (depth > 12) return null;
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            // Prefer explicit destination fields over generic URLs.
            for (String key : new String[]{"qqdocurl", "jumpUrl", "targetUrl", "shareUrl", "contentJumpUrl", "detailUrl", "url", "link"}) {
                Target found = fromUrl(obj.optString(key, ""));
                if (found != null) return found;
            }
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (IMAGE_KEYS.contains(key.toLowerCase(Locale.ROOT))) continue;
                Object value = obj.opt(key);
                if (value instanceof JSONObject || value instanceof JSONArray) {
                    Target found = fromJson(value, depth + 1);
                    if (found != null) return found;
                } else if (value instanceof String && URL_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                    Target found = fromUrl((String) value);
                    if (found != null) return found;
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                Target found = fromJson(array.opt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static Target fromUrl(String raw) {
        return fromUrl(raw, 0);
    }

    private static Target fromUrl(String raw, int depth) {
        if (raw == null || raw.length() > 16384 || depth > 2) return null;
        String value = raw.trim().replace("\\/", "/").replace("&amp;", "&");
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null) return null;
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (uri.getRawUserInfo() != null) return null;
            String host = uri.getHost();
            host = host == null ? "" : host.toLowerCase(Locale.ROOT);
            String path = uri.getPath();
            if (path != null && path.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp|gif|svg|ico)$")) return null;
            if ("http".equals(scheme) || "https".equals(scheme)) {
                if (domain(host, "bilibili.com") || domain(host, "bilibili.tv") || domain(host, "bilibili.cn") || domain(host, "b23.tv")) {
                    return new Target("bilibili", value, CardPackageRules.defaults("bilibili"), domain(host, "b23.tv"));
                }
                if (domain(host, "xiaohongshu.com") || domain(host, "xhslink.com")) {
                    return new Target("xiaohongshu", value, CardPackageRules.defaults("xiaohongshu"), domain(host, "xhslink.com"));
                }
                if (domain(host, "coolapk.com")) return new Target("coolapk", value, CardPackageRules.defaults("coolapk"), false);
                if (domain(host, "zhihu.com")) return new Target("zhihu", value, CardPackageRules.defaults("zhihu"), false);
            }
            // QQ sometimes wraps a share URL in a miniapp/browser scheme.
            if ("mqqapi".equals(scheme) || "mqq".equals(scheme) || domain(host, "qq.com")) {
                String query = uri.getRawQuery();
                if (query != null) for (String part : query.split("&")) {
                    int split = part.indexOf('=');
                    if (split < 0) continue;
                    String key = URLDecoder.decode(part.substring(0, split), "UTF-8").toLowerCase(Locale.ROOT);
                    if (!URL_KEYS.contains(key) && !"target".equals(key)) continue;
                    Target target = fromUrl(URLDecoder.decode(part.substring(split + 1), "UTF-8"), depth + 1);
                    if (target != null) return target;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static boolean domain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }
}
