package com.qianxi.schedule.importer;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ImportAdapter {
    public static final String AUTO = "auto";
    public static final String NEU = "neu";
    public static final String NEUQ_EAMS = "neuq-eams";
    public static final String ZHENGFANG = "zhengfang";
    public static final String QIANGZHI = "qiangzhi";
    public static final String KINGOSOFT = "kingosoft";
    public static final String GENERIC = "generic";

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
            new Definition(NEU, "东北大学教务"),
            new Definition(ZHENGFANG, "正方教务"),
            new Definition(QIANGZHI, "强智教务"),
            new Definition(KINGOSOFT, "青果教务"),
            new Definition(GENERIC, "通用网页表格")
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
        // Older installs stored the former Qinhuangdao-only adapter id. It now maps to the
        // single Northeast University choice while retaining host-specific parsing internally.
        if (NEUQ_EAMS.equals(id)) id = NEU;
        for (int i = 0; i < DEFINITIONS.size(); i++) {
            if (DEFINITIONS.get(i).id.equals(id)) return i;
        }
        return 0;
    }

    public static String labelOf(String id) {
        if (NEUQ_EAMS.equals(id)) return "东北大学教务";
        return DEFINITIONS.get(indexOf(id)).label;
    }

    public static String resolve(String selectedId, String url) {
        String detected = detect(url);
        if (AUTO.equals(selectedId)) return detected;
        if ((NEU.equals(selectedId) || NEUQ_EAMS.equals(selectedId))
                && NEUQ_EAMS.equals(detected)) {
            return NEUQ_EAMS;
        }
        return NEUQ_EAMS.equals(selectedId) ? NEU : selectedId;
    }

    public static String detect(String url) {
        try {
            String host = URI.create(url).getHost();
            host = host == null ? "" : host.toLowerCase(java.util.Locale.ROOT);
            if (host.equals("jwxt.neu.edu.cn") || host.endsWith(".jwxt.neu.edu.cn")) return NEU;
            if (host.equals("jwxt.neuq.edu.cn") || host.endsWith(".jwxt.neuq.edu.cn")) {
                return NEUQ_EAMS;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return GENERIC;
    }
}
