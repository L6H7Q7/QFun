package me.yxp.qfun.custom;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Defaults for the explicitly requested QFun 1.3.4 features. */
public final class DefaultFeatureRules {
    private static final Set<String> ENABLED = new HashSet<>(Arrays.asList(
            "RepeatMsg", "AntiRevoke", "FlashPicDecrypt", "AntiAtAll",
            "RememberLastRead", "AllowViewBlockedTroop", "AutoGrabHb",
            "DisableSplitScreenLimit", "AutoRemarkApk", "DownloadRedirect",
            "DisableHotPatch", "SkipScanWaitTime", "RemoveQrCodeCheck", "LiquidGlassTabBar"));
    public static boolean enabled(String simpleName, String category) {
        return ENABLED.contains(simpleName) || "净化".equals(category);
    }
    private DefaultFeatureRules() {}
}
