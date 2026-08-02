package com.qianxi.schedule.importer;

import java.util.Locale;

public final class AcademicWebPolicy {
    public static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";
    private AcademicWebPolicy() {}

    /**
     * Authentication pages are usually responsive, while timetable pages often depend on their
     * original desktop layout. This rule is based only on common authentication paths and never on
     * a school or domain.
     */
    public static boolean useMobileAuthenticationLayout(String url) {
        String path = path(url).toLowerCase(Locale.ROOT);
        return path.equals("/login") || path.startsWith("/login/")
                || path.equals("/cas") || path.contains("/cas/login")
                || path.equals("/authserver") || path.contains("/authserver/")
                || path.equals("/tpass") || path.contains("/tpass/login");
    }

    public static boolean loadWithOverview(String url) {
        return useMobileAuthenticationLayout(url);
    }

    /**
     * Legacy academic portals often return a reduced, incomplete shell to Android/WebView user
     * agents even though their timetable is a desktop-only page. Authentication pages keep the
     * system mobile user agent so that CAS and verification controls remain usable; all other
     * HTTP(S) pages use a desktop browser identity. The decision is path based, not school based.
     */
    public static boolean useDesktopContentUserAgent(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        return !useMobileAuthenticationLayout(url);
    }

    /**
     * Keep one identity for the entire CAS -> portal session. Some authentication servers bind
     * their session to the User-Agent and reject a ticket if the identity changes on redirect.
     */
    public static String userAgent(String url) {
        return DESKTOP_USER_AGENT;
    }

    private static String path(String url) {
        if (url == null) return "";
        int scheme = url.indexOf("://");
        int start = scheme < 0 ? 0 : scheme + 3;
        int slash = url.indexOf('/', start);
        if (slash < 0) return "";
        int end = url.length();
        for (int i = slash; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        return url.substring(slash, end);
    }
}
