package me.yxp.qfun.custom;

/** Small, platform-independent validation rules for the splash feature. */
public final class SplashRules {
    public static final int PICK_IMAGE = 0x7b41;
    public static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;
    public static final int MAX_CAPTION_LENGTH = 160;

    private SplashRules() {}

    public static boolean mainAsset(String name) {
        if (name == null) return false;
        if (name.startsWith("assets/")) name = name.substring(7);
        switch (name) {
            case "splash.jpg": case "splash.png": case "splash_big.jpg":
            case "splash_main.png": case "splash_main_night.png":
            case "splash/splash_simple.png": case "splash/splash_big_simple.png":
            case "splash/splash_main.png": case "splash/splash_main_night.png": return true;
            default: return false;
        }
    }

    public static String caption(String text) {
        String result = text == null ? "" : text.trim();
        if (result.length() > MAX_CAPTION_LENGTH) throw new IllegalArgumentException("文字最多 160 个字符");
        return result;
    }

    public static void validateCustom(boolean useImage, boolean hasImage, String text) {
        String value = caption(text);
        if (useImage && !hasImage) throw new IllegalArgumentException("请先选择图片");
        if (!useImage && value.isEmpty()) throw new IllegalArgumentException("请选择图片或填写自定义文字");
    }
}
