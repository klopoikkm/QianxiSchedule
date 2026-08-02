package com.qianxi.schedule.importer;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ImportAdapter {
    public static final String AUTO = "auto";
    public static final String ZHENGFANG = "zhengfang";
    public static final String QIANGZHI = "qiangzhi";
    public static final String KINGOSOFT = "kingosoft";
    public static final String BEANGLE_EAMS = "eams";
    public static final String EMAP = "emap";
    public static final String GENERIC = "generic";

    // Stored by older releases. These IDs are migration aliases only and never select a
    // school-specific loading or parsing path.
    public static final String NEU = "neu";
    public static final String NEUQ_EAMS = "neuq-eams";

    public static final class Definition {
        public final String id;
        public final String label;

        Definition(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final List<Definition> DEFINITIONS = Collections.unmodifiableList(Arrays.asList(
            new Definition(AUTO, "自动识别"),
            new Definition(ZHENGFANG, "正方教务"),
            new Definition(QIANGZHI, "强智教务"),
            new Definition(KINGOSOFT, "青果教务"),
            new Definition(BEANGLE_EAMS, "Beangle / EAMS"),
            new Definition(EMAP, "EMAP 教务"),
            new Definition(GENERIC, "通用网页课表")
    ));

    private ImportAdapter() {}

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static String[] labels() {
        String[] labels = new String[DEFINITIONS.size()];
        for (int i = 0; i < DEFINITIONS.size(); i++) labels[i] = DEFINITIONS.get(i).label;
        return labels;
    }

    public static String idAt(int index) {
        if (index < 0 || index >= DEFINITIONS.size()) return AUTO;
        return DEFINITIONS.get(index).id;
    }

    public static int indexOf(String id) {
        id = migrateLegacyId(id);
        for (int i = 0; i < DEFINITIONS.size(); i++) {
            if (DEFINITIONS.get(i).id.equals(id)) return i;
        }
        return 0;
    }

    public static String labelOf(String id) {
        return DEFINITIONS.get(indexOf(id)).label;
    }

    public static String resolve(String selectedId, String url) {
        String detected = detect(url);
        if (!GENERIC.equals(detected)) return detected;
        String migrated = migrateLegacyId(selectedId);
        if (migrated == null || AUTO.equals(migrated)) return GENERIC;
        return migrated;
    }

    public static String detect(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            path = path == null ? "" : path.toLowerCase(java.util.Locale.ROOT);
            if (path.equals("/jwglxt") || path.startsWith("/jwglxt/")
                    || path.contains("/jwglxt/")) {
                return ZHENGFANG;
            }
            if (path.equals("/jsxsd") || path.startsWith("/jsxsd/")
                    || path.contains("/jsxsd/")) {
                return QIANGZHI;
            }
            if (path.equals("/urp") || path.startsWith("/urp/") || path.contains("/urp/")) {
                return KINGOSOFT;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return GENERIC;
    }

    public static String migrateLegacyId(String id) {
        if (NEU.equals(id) || NEUQ_EAMS.equals(id)) return AUTO;
        return id;
    }
}
