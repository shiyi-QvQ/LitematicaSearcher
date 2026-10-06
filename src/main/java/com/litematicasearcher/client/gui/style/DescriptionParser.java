package com.litematicasearcher.client.gui.style;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 蓝图描述解析：
 * - 版本号提取
 * - 警告关键词（不防雪、需铁砧墙…）
 * - 简介截取（前 N 字符）
 * - 版本分组标签
 */
public final class DescriptionParser {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(1\\.\\d{1,2}(?:\\.\\d{1,2})?)");

    private static final List<String> WARNING_KEYWORDS = Arrays.asList(
            "不防雪", "会冻", "不防刷", "需铁砧墙", "需要铁砧", "需要村民", "需村民", "需黑曜石",
            "不防苦力怕", "苦力怕", "TNT", "tnt", "不防岩浆", "岩浆", "不防末影龙", "末影龙",
            "not snow", "no snow", "no mob", "mob-proof"
    );

    private DescriptionParser() {
    }

    public static String shortSummary(String description, int max) {
        if (description == null) {
            return "";
        }
        String trimmed = description.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max).trim() + "…";
    }

    public static Set<String> warnings(String description) {
        Set<String> found = new LinkedHashSet<>();
        if (description == null) {
            return found;
        }
        String lower = description.toLowerCase();
        for (String w : WARNING_KEYWORDS) {
            if (lower.contains(w.toLowerCase())) {
                found.add(w);
            }
        }
        return found;
    }

    public static List<String> versions(String text) {
        Set<String> seen = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Matcher m = VERSION_PATTERN.matcher(text);
        while (m.find()) {
            String v = m.group(1);
            if (v.matches("1\\.\\d{1,2}")) {
                v = v + ".0";
            }
            seen.add(v);
        }
        return new ArrayList<>(seen);
    }

    /**
     * 把 1.14 / 1.20 等版本号归类为 1.14~1.19、1.20~1.21 等分组。
     * 最多返回 4 个分组标签。
     */
    public static List<VersionGroup> groupVersions(List<String> versions) {
        if (versions == null || versions.isEmpty()) {
            return List.of();
        }
        boolean legacy = false;
        boolean modern = false;
        boolean latest = false;
        for (String v : versions) {
            double n = parseMajorMinor(v);
            if (n <= 0) {
                continue;
            }
            if (n <= 19.0) {
                legacy = true;
            } else if (n < 20.5) {
                modern = true;
            } else {
                latest = true;
            }
        }
        List<VersionGroup> groups = new ArrayList<>();
        if (legacy) {
            groups.add(new VersionGroup("1.14~1.19", 0xFF8A95A5));
        }
        if (modern) {
            groups.add(new VersionGroup("1.20~1.20.6", 0xFFFFB347));
        }
        if (latest) {
            groups.add(new VersionGroup("1.21+", 0xFFFF8866));
        }
        return groups;
    }

    private static double parseMajorMinor(String v) {
        try {
            String[] parts = v.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major + minor / 100.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static final class VersionGroup {
        public final String label;
        public final int color;

        public VersionGroup(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }
}
