package me.yxp.qfun.custom;

public final class SplashRulesTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(SplashRules.mainAsset("splash/splash_main.png"), "Default light asset selected");
        check(SplashRules.mainAsset("splash/splash_main_night.png"), "Default dark asset selected");
        check(SplashRules.mainAsset("assets/splash/splash_main.png"), "Optional assets prefix");
        check(!SplashRules.mainAsset("splash/splash_logo.png"), "Bottom QQ branding retained");
        check(!SplashRules.mainAsset("splash/splash_logo_night.png"), "Bottom dark branding retained");
        check(!SplashRules.mainAsset("icons/splash_main.png"), "Other image folders unaffected");
        check(SplashRules.caption("  hello\nworld  ").equals("hello\nworld"), "Caption trimming keeps lines");
        boolean emptyRejected = false;
        try { SplashRules.validateCustom(false, false, " "); } catch (IllegalArgumentException e) { emptyRejected = true; }
        check(emptyRejected, "Empty custom design rejected");
        boolean absentImageRejected = false;
        try { SplashRules.validateCustom(true, false, "text"); } catch (IllegalArgumentException e) { absentImageRejected = true; }
        check(absentImageRejected, "Missing selected image rejected");
        SplashRules.validateCustom(false, false, "Only text");
        check(true, "Text-only design supported");
        SplashRules.validateCustom(true, true, "");
        check(true, "Image-only design supported");
        boolean longRejected = false;
        try { SplashRules.caption(new String(new char[161]).replace('\0', 'a')); } catch (IllegalArgumentException e) { longRejected = true; }
        check(longRejected, "Excess caption length rejected");
        check(SplashRules.PICK_IMAGE != 1001 && SplashRules.PICK_IMAGE != 1002 && SplashRules.PICK_IMAGE != 1003, "Picker request does not collide with QFun");
        System.out.println("PASS: " + checks + " splash validation checks");
    }
}
