package me.yxp.qfun.custom;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public final class UiCleanupRulesTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        List<String> sidebar = new ArrayList<>(Arrays.asList("d_album", "d_favorite", "d_document", null,
                "d_qqwallet", "d_vip_identity", "d_decoration", "d_vip_card"));
        List<String> selected = UiCleanupRules.sidebar(sidebar, value -> value);
        check(selected.equals(Arrays.asList("d_favorite", "d_qqwallet", "d_album")), "Only requested rows, in requested order");
        check(sidebar.size() == 8, "Original list is not mutated");
        check(UiCleanupRules.sidebar(selected, value -> value).equals(selected), "Repeated refresh is stable");
        check(UiCleanupRules.sidebar(Arrays.asList("d_favorite", "d_favorite", "d_qqwallet"), value -> value).size() == 2, "Duplicate row removed");
        List<String> missing = Arrays.asList("unknown", "d_setting", null);
        check(UiCleanupRules.sidebar(missing, value -> value) == missing, "Unknown dataset keeps normal UI");
        check(UiCleanupRules.sidebar(Arrays.asList("d_album", "d_setting"), value -> value).contains("d_setting"), "Alternate layout retains its only settings route");
        List<String> empty = new ArrayList<>();
        check(UiCleanupRules.sidebar(empty, value -> value) == empty, "Empty sidebar safe");
        check(UiCleanupRules.<String>sidebar(null, value -> value) == null, "Null sidebar safe");
        List<Integer> types = new ArrayList<>(Arrays.asList(1, 21, 4, 17, 22));
        List<Integer> favorite = UiCleanupRules.favorites(types, value -> value);
        check(favorite.equals(Arrays.asList(4)), "Favorite is selected by type, not old index");
        check(types.size() == 5, "Original panel list is not mutated");
        check(UiCleanupRules.favorites(Arrays.asList(4, 4), value -> value).size() == 1, "Only one favorite page");
        List<Integer> noFavorite = Arrays.asList(1, 17);
        check(UiCleanupRules.favorites(noFavorite, value -> value) == noFavorite, "Unsupported panel retains navigation");
        check(UiCleanupRules.favorites(Arrays.asList(null, 4), value -> value).equals(Arrays.asList(4)), "Null panel entry safe");
        check(UiCleanupRules.favorites(null, value -> (Integer) value) == null, "Null panel safe");
        List<Integer> noPanels = new ArrayList<>();
        check(UiCleanupRules.favorites(noPanels, value -> value) == noPanels, "Empty panel list safe");
        System.out.println("PASS: " + checks + " UI cleanup selection checks");
    }
}
