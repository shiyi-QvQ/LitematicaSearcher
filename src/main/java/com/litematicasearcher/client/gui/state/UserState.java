package com.litematicasearcher.client.gui.state;

import com.litematicasearcher.client.api.RedenMachine;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 全局轻量状态：收藏列表 + 对比选择。两个屏幕共享。
 */
public final class UserState {
    private static final Map<String, RedenMachine> FAVORITES = new LinkedHashMap<>();
    private static final Set<String> COMPARE_KEYS = new LinkedHashSet<>();

    private UserState() {
    }

    public static boolean isFavorite(RedenMachine machine) {
        return FAVORITES.containsKey(machine.key());
    }

    public static void toggleFavorite(RedenMachine machine) {
        if (!FAVORITES.remove(machine.key(), machine)) {
            FAVORITES.put(machine.key(), machine);
        }
    }

    public static Map<String, RedenMachine> favorites() {
        return FAVORITES;
    }

    public static boolean isInCompare(RedenMachine machine) {
        return COMPARE_KEYS.contains(machine.key());
    }

    public static boolean toggleCompare(RedenMachine machine) {
        if (COMPARE_KEYS.contains(machine.key())) {
            COMPARE_KEYS.remove(machine.key());
            return false;
        }
        if (COMPARE_KEYS.size() >= 2) {
            return false; // 最多 2 个
        }
        COMPARE_KEYS.add(machine.key());
        return true;
    }

    public static void clearCompare() {
        COMPARE_KEYS.clear();
    }

    public static Set<String> compareKeys() {
        return COMPARE_KEYS;
    }
}
