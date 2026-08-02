package com.qianxi.schedule.importer;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Repairs undecorated Beangle/EAMS HTML without binding the browser to a school hostname. */
public final class AcademicShellRepair {
    private static final String EAMS_SEGMENT = "/eams";
    private static final String FOUNDATION_MARKER = "/scripts/beangle/beangle.js";

    public static final class Result {
        public final byte[] body;
        public final boolean changed;

        Result(byte[] body, boolean changed) {
            this.body = body;
            this.changed = changed;
        }
    }

    private AcademicShellRepair() {}

    public static boolean shouldInspect(String url, String method, boolean mainFrame) {
        if (!mainFrame || method == null || !"GET".equalsIgnoreCase(method)) return false;
        URI uri = parseHttpUrl(url);
        if (uri == null) return false;
        String query = uri.getRawQuery();
        if (query != null && query.toLowerCase(Locale.ROOT).contains("ticket=")) return false;
        return eamsIndex(uri.getRawPath()) >= 0;
    }

    public static Result repair(String url, byte[] source) {
        if (source == null || source.length == 0 || hasFoundation(source)
                || !usesFoundation(source)) {
            return new Result(source, false);
        }
        URI uri = parseHttpUrl(url);
        if (uri == null) return new Result(source, false);
        String path = uri.getRawPath();
        int index = eamsIndex(path);
        if (index < 0) return new Result(source, false);

        String vendorRoot = path.substring(0, index + EAMS_SEGMENT.length());
        String markup = "<link rel=\"stylesheet\" href=\"" + vendorRoot
                + "/static/themes/default/beangle-ui.css\">"
                + "<script data-qianxi-shell-repair=\"1\" src=\"" + vendorRoot
                + "/static/scripts/jquery/jquery,/scripts/beangle/beangle.js\"></script>"
                + "<script>if(typeof window.openUrl!=='function'){window.openUrl=function(){"
                + "var u='';for(var i=0;i<arguments.length;i++){var v=arguments[i];"
                + "if(typeof v==='string'&&(v.indexOf('/')>=0||v.indexOf('.action')>=0)){u=v;break;}}"
                + "if(u){window.location.href=u;}return false;};}</script>";
        byte[] injection = markup.getBytes(StandardCharsets.US_ASCII);
        int insert = insertionPoint(source);
        byte[] merged = new byte[source.length + injection.length];
        System.arraycopy(source, 0, merged, 0, insert);
        System.arraycopy(injection, 0, merged, insert, injection.length);
        System.arraycopy(source, insert, merged, insert + injection.length,
                source.length - insert);
        return new Result(merged, true);
    }

    private static boolean hasFoundation(byte[] body) {
        return indexOfAscii(body, FOUNDATION_MARKER) >= 0
                || indexOfAscii(body, "data-qianxi-shell-repair") >= 0;
    }

    private static boolean usesFoundation(byte[] body) {
        return indexOfAscii(body, "jquery") >= 0
                || indexOfAscii(body, "bg.") >= 0
                || indexOfAscii(body, "openurl(") >= 0;
    }

    private static int insertionPoint(byte[] body) {
        int head = indexOfAscii(body, "<head");
        if (head >= 0) {
            for (int i = head; i < body.length; i++) {
                if (body[i] == '>') return i + 1;
            }
        }
        int script = indexOfAscii(body, "<script");
        if (script >= 0) return script;
        int html = indexOfAscii(body, "<html");
        if (html >= 0) {
            for (int i = html; i < body.length; i++) {
                if (body[i] == '>') return i + 1;
            }
        }
        return 0;
    }

    private static int eamsIndex(String path) {
        if (path == null) return -1;
        String lower = path.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from < lower.length()) {
            int index = lower.indexOf(EAMS_SEGMENT, from);
            if (index < 0) return -1;
            int end = index + EAMS_SEGMENT.length();
            if (end == lower.length() || lower.charAt(end) == '/') return index;
            from = index + 1;
        }
        return -1;
    }

    private static URI parseHttpUrl(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null || uri.getRawAuthority() == null
                    || (!("http".equalsIgnoreCase(scheme))
                    && !("https".equalsIgnoreCase(scheme)))) return null;
            return uri;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static int indexOfAscii(byte[] body, String needle) {
        if (body == null || needle == null) return -1;
        byte[] target = needle.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i + target.length <= body.length; i++) {
            for (int j = 0; j < target.length; j++) {
                byte value = body[i + j];
                if (value >= 'A' && value <= 'Z') value += 32;
                if (value != target[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
