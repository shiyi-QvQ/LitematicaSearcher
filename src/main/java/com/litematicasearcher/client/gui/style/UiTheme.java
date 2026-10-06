package com.litematicasearcher.client.gui.style;

import com.litematicasearcher.client.api.RedenMachine;

/**
 * UI 工具：分类判定 + 颜色工具。
 * <p>不做任何自定义绘制 — 屏幕布局全部交给原生 Minecraft Widget 体系。</p>
 */
public final class UiTheme {

    private UiTheme() {
    }

    // ===================== 分类 =====================
    public enum Category {
        FARM(0xFF6BCB77, "litematicasearcher.category.farm"),
        BUILD(0xFFE0B341, "litematicasearcher.category.build"),
        DECOR(0xFFB080FF, "litematicasearcher.category.decor"),
        REDSTONE(0xFFFF6B6B, "litematicasearcher.category.redstone"),
        GENERATOR(0xFFFFA040, "litematicasearcher.category.generator"),
        MISC(0xFF8A95A5, "litematicasearcher.category.misc");

        public final int color;
        public final String translatableKey;

        Category(int color, String translatableKey) {
            this.color = color;
            this.translatableKey = translatableKey;
        }
    }

    public static Category classify(RedenMachine machine) {
        if (machine == null) {
            return Category.MISC;
        }
        if (machine.isGenerationType()) {
            return Category.GENERATOR;
        }
        String lower = ((machine.name() == null ? "" : machine.name()) + " "
                + (machine.description() == null ? "" : machine.description())
                + " " + (machine.summary() == null ? "" : machine.summary())).toLowerCase();
        if (containsAny(lower, "farm", "wheat", "carrot", "potato", "beet", "sugar cane", "sugarcane",
                "pumpkin", "melon", "mushroom", "cocoa", "bamboo", "cactus", "kelp",
                "作物", "农场", "小麦", "萝卜", "土豆", "甜菜", "甘蔗", "南瓜", "西瓜", "蘑菇", "竹子", "仙人掌", "海带")) {
            return Category.FARM;
        }
        if (containsAny(lower, "build", "house", "castle", "tower", "bridge", "wall", "ship",
                "建筑", "房屋", "城堡", "塔", "桥", "城墙", "船")) {
            return Category.BUILD;
        }
        if (containsAny(lower, "decor", "fountain", "garden", "statue", "lamp", "装饰", "喷泉", "花园", "雕像", "路灯")) {
            return Category.DECOR;
        }
        if (containsAny(lower, "redstone", "piston", "tnt", "trap", "door", "机关", "红石", "活塞", "陷阱", "门")) {
            return Category.REDSTONE;
        }
        return Category.MISC;
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String t : tokens) {
            if (!t.isEmpty() && text.contains(t)) {
                return true;
            }
        }
        return false;
    }

    // ===================== 颜色 =====================
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    public static int darken(int color, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int a = (color >>> 24) & 0xFF;
        int r = (int) (((color >>> 16) & 0xFF) * (1f - amount));
        int g = (int) (((color >>> 8) & 0xFF) * (1f - amount));
        int b = (int) ((color & 0xFF) * (1f - amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int lighten(int color, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int a = (color >>> 24) & 0xFF;
        int r = (int) (((color >>> 16) & 0xFF) + (255 - ((color >>> 16) & 0xFF)) * amount);
        int g = (int) (((color >>> 8) & 0xFF) + (255 - ((color >>> 8) & 0xFF)) * amount);
        int b = (int) ((color & 0xFF) + (255 - (color & 0xFF)) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
