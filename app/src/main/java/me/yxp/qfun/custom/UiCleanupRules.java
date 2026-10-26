package me.yxp.qfun.custom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure selection rules. Unknown host data is returned unchanged. */
public final class UiCleanupRules {
    public static final int FAVORITE_PANEL_TYPE = 4;
    private static final String[] SIDEBAR_ORDER = {"d_favorite", "d_qqwallet", "d_album", "d_setting"};
    public interface Id<T> { String get(T item); }
    public interface Type<T> { int get(T item); }

    public static <T> List<T> sidebar(List<T> source, Id<T> id) {
        if (source == null) return null;
        Map<String, T> selected = new LinkedHashMap<>();
        for (T item : source) {
            if (item == null) continue;
            String key = id.get(item);
            for (String allowed : SIDEBAR_ORDER) {
                if (allowed.equals(key) && !selected.containsKey(key)) selected.put(key, item);
            }
        }
        // Never replace an unrecognized list with an empty list or just a settings row.
        if (!selected.containsKey("d_favorite") && !selected.containsKey("d_qqwallet") && !selected.containsKey("d_album")) return source;
        List<T> result = new ArrayList<>();
        for (String key : SIDEBAR_ORDER) if (selected.containsKey(key)) result.add(selected.get(key));
        return result;
    }

    public static <T> List<T> favorites(List<T> source, Type<T> type) {
        if (source == null) return null;
        for (T item : source) {
            if (item != null && type.get(item) == FAVORITE_PANEL_TYPE) {
                List<T> result = new ArrayList<>();
                result.add(item);
                return result;
            }
        }
        return source;
    }
    private UiCleanupRules() {}
}
