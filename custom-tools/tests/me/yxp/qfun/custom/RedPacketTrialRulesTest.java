package me.yxp.qfun.custom;
import java.util.Arrays;
public final class RedPacketTrialRulesTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(RedPacketTrialRules.defaults().equals(new java.util.LinkedHashSet<>(Arrays.asList(1, 32))), "Default normal and command");
        check(!RedPacketTrialRules.defaults().contains(65536), "Voice not offered");
        check(RedPacketTrialRules.supported(1), "Normal supported");
        check(RedPacketTrialRules.supported(32), "Command supported");
        check(!RedPacketTrialRules.supported(65536), "Voice rejected even from old saved settings");
        check(!RedPacketTrialRules.supported(0), "Unknown channel rejected");
        check(RedPacketTrialRules.scopeEnabled(2, true, false), "Group can be on alone");
        check(!RedPacketTrialRules.scopeEnabled(1, true, false), "Private remains off independently");
        check(!RedPacketTrialRules.scopeEnabled(2, false, true), "Group remains off independently");
        check(RedPacketTrialRules.scopeEnabled(1, false, true), "Private can be on alone");
        check(RedPacketTrialRules.scopeEnabled(100, false, true), "Other packet messages share non-group trial scope");
        check(!RedPacketTrialRules.scopeEnabled(2, false, false), "Both off excludes group");
        check(!RedPacketTrialRules.scopeEnabled(1, false, false), "Both off excludes private");
        check("group".equals(RedPacketTrialRules.scope(2)), "Group label");
        check("private".equals(RedPacketTrialRules.scope(1)), "Private label");
        check("other".equals(RedPacketTrialRules.scope(100)), "Other label");
        check(RedPacketTrialRules.keyword("红包", Arrays.asList("")) == null, "Empty keyword does not exclude everything");
        check(RedPacketTrialRules.keyword("不领取红包", Arrays.asList("不领取")) != null, "Keyword exclusion preserved");
        check(RedPacketTrialRules.keyword("红包", Arrays.asList("其他")) == null, "Nonmatching title proceeds");
        check(RedPacketTrialRules.keyword(null, null) == null, "Missing title and keywords safe");
        check(!RedPacketTrialRules.channel(65536).equals("normal"), "Voice cannot be mislabeled ordinary");
        System.out.println("PASS: " + checks + " red packet trial rules");
    }
}
