package com.qianxi.schedule.importer;

import java.util.Locale;

public final class AcademicWebPolicy {
    public static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0";
    public static final String MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Mobile Safari/537.36";

    private AcademicWebPolicy() {}

    public static boolean useMobileAuthenticationLayout(String url) {
        // Parse leniently by hand. Academic/WebVPN URLs frequently contain unescaped characters
        // (spaces, '|', '{}', CJK query params) that make java.net.URI.create throw, which would
        // silently fall back to the desktop profile for a login page that needs the mobile one.
        String host = lower(host(url));
        String path = lower(path(url));
        if (host.equals("vpn.neuq.edu.cn") && path.startsWith("/login")) return true;
        if (host.equals("ids.neuq.edu.cn") || host.startsWith("ids.")) return true;
        return path.contains("/authserver/") || path.equals("/authserver");
    }

    private static String host(String url) {
        if (url == null) return "";
        int scheme = url.indexOf("://");
        int start = scheme < 0 ? 0 : scheme + 3;
        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') { end = i; break; }
        }
        String authority = url.substring(start, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) authority = authority.substring(at + 1);
        int colon = authority.indexOf(':');
        if (colon >= 0) authority = authority.substring(0, colon);
        return authority;
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
            if (c == '?' || c == '#') { end = i; break; }
        }
        return url.substring(slash, end);
    }

    /**
     * One stable desktop user agent for the whole session, regardless of URL. CAS binds the
     * server session to the User-Agent header: authenticating with the mobile UA and then
     * loading EAMS with the desktop UA invalidates the session, which bounces the browser back
     * to the login page and produces an infinite CAS↔EAMS redirect loop (seen as dozens of
     * page loads per minute; 1.12.0 regressed this, 1.9.0 had originally fixed it). The mobile
     * login layout is instead approximated with viewport scaling, which needs no UA change.
     */
    public static String userAgent(String url) {
        return DESKTOP_USER_AGENT;
    }

    public static boolean loadWithOverview(String url) {
        // EAMS desktop pages use fixed-width legacy CSS. Overview mode makes Chromium lay out
        // that page against the device width first, which hides the sidebar and can prevent its
        // legacy scripts from initialising. CAS/login pages are responsive and should be fitted.
        return useMobileAuthenticationLayout(url);
    }

    /**
     * The modern EAMS portal uses responsive CSS: on a phone-width layout viewport it collapses
     * the side navigation into a drawer that often will not open inside a WebView, leaving the
     * home page with no reachable menu. Forcing a desktop-width layout viewport keeps the full
     * sidebar visible (matching the desktop browser view). Login/CAS pages are left in their
     * native responsive layout because they are designed for narrow screens.
     */
    public static boolean forceDesktopViewport(String url) {
        return !useMobileAuthenticationLayout(url);
    }

    public static final int DESKTOP_VIEWPORT_WIDTH = 1280;

    public static final String FORCE_DESKTOP_VIEWPORT_JS =
            "(function(){try{var w=" + DESKTOP_VIEWPORT_WIDTH + ";"
                    + "var m=document.querySelector('meta[name=viewport]');"
                    + "if(!m){m=document.createElement('meta');m.setAttribute('name','viewport');"
                    + "(document.head||document.documentElement).appendChild(m);}"
                    + "var c='width='+w;"
                    + "if(m.getAttribute('content')!==c){m.setAttribute('content',c);"
                    // Nudge frameworks that gate the sidebar on a JS resize listener rather than
                    // pure CSS media queries, so they re-evaluate against the new desktop width.
                    + "try{window.dispatchEvent(new Event('resize'));}catch(e){}}"
                    + "}catch(e){}})();";

    public static final String WEBVPN_HOST = "vpn.neuq.edu.cn";
    private static final String INTERNAL_DOMAIN = "neuq.edu.cn";

    /**
     * When a page is served through the NEUQ WebVPN the URL looks like
     * {@code https://vpn.neuq.edu.cn/http/<prefix>/eams/homeExt.action}, where {@code <prefix>}
     * encodes the internal host+port. The proxy is supposed to rewrite every resource link to the
     * same form, but it misses absolute internal links and JS-built URLs, so scripts such as
     * jQuery get requested straight from {@code http://jwxt.neuq.edu.cn/...} — without the WebVPN
     * session cookie — and fail, leaving a bare skeleton. Returns the proxy base
     * {@code https://vpn.neuq.edu.cn/http/<prefix>} to reuse for those resources, or null when the
     * URL is not a WebVPN page.
     */
    public static String parseWebVpnBase(String url) {
        String host = lower(host(url));
        if (!host.equals(WEBVPN_HOST) && !host.endsWith("." + WEBVPN_HOST)) return null;
        String path = path(url);
        if (path.isEmpty()) return null;
        String[] seg = path.split("/", -1);
        // seg[0] is empty (leading slash); seg[1] is the scheme segment; seg[2] is the prefix.
        if (seg.length < 3) return null;
        String schemeSeg = lower(seg[1]);
        if (!schemeSeg.equals("http") && !schemeSeg.equals("https")) return null;
        if (seg[2].isEmpty()) return null;
        String proto = scheme(url);
        if (proto.isEmpty()) proto = "https";
        return proto + "://" + host + "/" + seg[1] + "/" + seg[2];
    }

    /**
     * If {@code resourceUrl} points at a raw internal academic host while we are operating through
     * the WebVPN, returns the equivalent URL routed through the proxy base so it inherits the
     * session; otherwise null (leave the request untouched).
     */
    public static String rewriteThroughWebVpn(String base, String resourceUrl) {
        if (base == null || resourceUrl == null) return null;
        String scheme = lower(scheme(resourceUrl));
        if (!scheme.equals("http") && !scheme.equals("https")) return null;
        String host = lower(host(resourceUrl));
        if (host.isEmpty()) return null;
        // Already proxied, or not an internal NEUQ host: leave it alone.
        if (host.equals(WEBVPN_HOST) || host.endsWith("." + WEBVPN_HOST)) return null;
        if (!host.equals(INTERNAL_DOMAIN) && !host.endsWith("." + INTERNAL_DOMAIN)) return null;
        String pathQuery = pathAndQuery(resourceUrl);
        if (pathQuery.isEmpty()) pathQuery = "/";
        return base + pathQuery;
    }

    private static String scheme(String url) {
        if (url == null) return "";
        int i = url.indexOf("://");
        return i < 0 ? "" : url.substring(0, i);
    }

    private static String pathAndQuery(String url) {
        if (url == null) return "";
        int scheme = url.indexOf("://");
        int start = scheme < 0 ? 0 : scheme + 3;
        int slash = url.indexOf('/', start);
        if (slash < 0) return "/";
        int hash = url.indexOf('#', slash);
        return hash < 0 ? url.substring(slash) : url.substring(slash, hash);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
