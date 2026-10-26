package me.yxp.qfun.custom;
public final class DefaultFeatureRulesTest {
    public static void main(String[] args) {
        String[] selected = {"RepeatMsg", "AntiRevoke", "FlashPicDecrypt", "AntiAtAll", "RememberLastRead", "AllowViewBlockedTroop", "AutoGrabHb", "DisableSplitScreenLimit", "AutoRemarkApk", "DownloadRedirect", "DisableHotPatch", "SkipScanWaitTime", "RemoveQrCodeCheck", "LiquidGlassTabBar"};
        for (String name : selected) if (!DefaultFeatureRules.enabled(name, "")) throw new AssertionError(name);
        if (!DefaultFeatureRules.enabled("RemoveQQShow", "净化")) throw new AssertionError("Purification category");
        if (DefaultFeatureRules.enabled("TabletMode", "设备")) throw new AssertionError("Unrequested tablet mode");
        if (DefaultFeatureRules.enabled("NewOtherFeature", "其他")) throw new AssertionError("Unrequested future other feature");
        System.out.println("PASS: 17 default feature checks");
    }
}
