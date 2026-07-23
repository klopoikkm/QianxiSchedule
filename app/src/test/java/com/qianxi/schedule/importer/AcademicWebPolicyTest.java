package com.qianxi.schedule.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AcademicWebPolicyTest {
    @Test
    public void eamsUsesDesktopLayoutAndForcedViewport() {
        String url = "http://jwxt.neuq.edu.cn/eams/homeExt.action";
        assertFalse(AcademicWebPolicy.useMobileAuthenticationLayout(url));
        assertFalse(AcademicWebPolicy.loadWithOverview(url));
        assertTrue(AcademicWebPolicy.forceDesktopViewport(url));
        assertSame(AcademicWebPolicy.DESKTOP_USER_AGENT, AcademicWebPolicy.userAgent(url));
    }

    @Test
    public void webVpnEamsForcesDesktopViewport() {
        String url = "https://vpn.neuq.edu.cn/http/encoded/eams/homeExt.action";
        assertFalse(AcademicWebPolicy.useMobileAuthenticationLayout(url));
        assertTrue(AcademicWebPolicy.forceDesktopViewport(url));
    }

    @Test
    public void neuqAuthenticationUsesMobileResponsiveLayout() {
        String url = "http://ids.neuq.edu.cn/authserver/login?service=test";
        assertTrue(AcademicWebPolicy.useMobileAuthenticationLayout(url));
        assertTrue(AcademicWebPolicy.loadWithOverview(url));
        assertFalse(AcademicWebPolicy.forceDesktopViewport(url));
        // CAS binds its session to the User-Agent header; switching UA between the login page
        // and EAMS invalidates the ticket and causes an infinite CAS↔EAMS redirect loop.
        // The UA must therefore stay constant across the whole authentication chain.
        assertSame(AcademicWebPolicy.DESKTOP_USER_AGENT, AcademicWebPolicy.userAgent(url));
    }

    @Test
    public void userAgentIsStableAcrossAuthenticationChain() {
        // Every hop of the CAS chain must observe the same UA, or the session breaks.
        String[] chain = {
                "http://jwxt.neuq.edu.cn/eams/homeExt.action",
                "http://ids.neuq.edu.cn/authserver/login?service=x",
                "https://vpn.neuq.edu.cn/login",
                "https://vpn.neuq.edu.cn/http/777264767/eams/homeExt.action",
        };
        for (String url : chain) {
            assertSame(AcademicWebPolicy.DESKTOP_USER_AGENT, AcademicWebPolicy.userAgent(url));
        }
    }

    @Test
    public void webVpnLoginKeepsNativeResponsiveLayout() {
        String url = "https://vpn.neuq.edu.cn/login";
        assertTrue(AcademicWebPolicy.useMobileAuthenticationLayout(url));
        assertFalse(AcademicWebPolicy.forceDesktopViewport(url));
    }

    @Test
    public void parsesWebVpnProxyBaseFromPortalUrl() {
        String url = "https://vpn.neuq.edu.cn/http/777264767/eams/homeExt.action";
        assertEquals("https://vpn.neuq.edu.cn/http/777264767",
                AcademicWebPolicy.parseWebVpnBase(url));
    }

    @Test
    public void parsesWebVpnProxyBaseForHttpsSegment() {
        String url = "https://vpn.neuq.edu.cn/https/abc123/eams/homeExt.action?x=1";
        assertEquals("https://vpn.neuq.edu.cn/https/abc123",
                AcademicWebPolicy.parseWebVpnBase(url));
    }

    @Test
    public void directHostHasNoWebVpnBase() {
        assertNull(AcademicWebPolicy.parseWebVpnBase("http://jwxt.neuq.edu.cn/eams/homeExt.action"));
    }

    @Test
    public void rewritesRawInternalResourceThroughProxy() {
        String base = "https://vpn.neuq.edu.cn/http/777264767";
        assertEquals("https://vpn.neuq.edu.cn/http/777264767/eams/static/jquery.js",
                AcademicWebPolicy.rewriteThroughWebVpn(base,
                        "http://jwxt.neuq.edu.cn/eams/static/jquery.js"));
    }

    @Test
    public void rewritePreservesQueryAndStripsFragment() {
        String base = "https://vpn.neuq.edu.cn/http/777264767";
        assertEquals("https://vpn.neuq.edu.cn/http/777264767/eams/avatar/my.action?t=9",
                AcademicWebPolicy.rewriteThroughWebVpn(base,
                        "http://jwxt.neuq.edu.cn/eams/avatar/my.action?t=9#top"));
    }

    @Test
    public void alreadyProxiedResourceIsNotRewritten() {
        String base = "https://vpn.neuq.edu.cn/http/777264767";
        assertNull(AcademicWebPolicy.rewriteThroughWebVpn(base,
                "https://vpn.neuq.edu.cn/http/777264767/eams/static/jquery.js"));
    }

    @Test
    public void externalResourceIsNotRewritten() {
        String base = "https://vpn.neuq.edu.cn/http/777264767";
        assertNull(AcademicWebPolicy.rewriteThroughWebVpn(base,
                "https://cdn.jsdelivr.net/npm/jquery/dist/jquery.min.js"));
    }
}
