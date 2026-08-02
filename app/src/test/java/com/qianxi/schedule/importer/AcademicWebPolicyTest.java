package com.qianxi.schedule.importer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AcademicWebPolicyTest {
    @Test
    public void timetablePagesKeepTheirNativeLayoutOnEveryDomain() {
        assertFalse(AcademicWebPolicy.loadWithOverview(
                "http://jwxt.neuq.edu.cn/eams/homeExt.action"));
        assertFalse(AcademicWebPolicy.loadWithOverview(
                "https://zdbk.zju.edu.cn/jwglxt/xtgl/index_initMenu.html"));
        assertFalse(AcademicWebPolicy.loadWithOverview(
                "https://jwxt.neu.edu.cn/jwapp/sys/homeapp/index.do"));
    }

    @Test
    public void commonAuthenticationPathsUseResponsiveOverview() {
        assertTrue(AcademicWebPolicy.loadWithOverview(
                "https://ids.example.edu.cn/authserver/login?service=test"));
        assertTrue(AcademicWebPolicy.loadWithOverview(
                "https://sso.example.edu.cn/cas/login?service=test"));
        assertTrue(AcademicWebPolicy.loadWithOverview(
                "https://pass.example.edu.cn/tpass/login"));
        assertTrue(AcademicWebPolicy.loadWithOverview(
                "https://vpn.example.edu.cn/login"));
    }

    @Test
    public void authenticationKeepsMobileUserAgentAndContentUsesDesktopIdentity() {
        assertFalse(AcademicWebPolicy.useDesktopContentUserAgent(
                "https://ids.example.edu.cn/authserver/login?service=test"));
        assertFalse(AcademicWebPolicy.useDesktopContentUserAgent(
                "https://sso.example.edu.cn/cas/login?service=test"));
        assertTrue(AcademicWebPolicy.useDesktopContentUserAgent(
                "https://jw.example.edu.cn/eams/homeExt.action"));
        assertTrue(AcademicWebPolicy.useDesktopContentUserAgent(
                "https://jw.example.edu.cn/jwglxt/xskb/xskb_cxXsKb.html"));
        assertFalse(AcademicWebPolicy.useDesktopContentUserAgent("about:blank"));
    }

    @Test
    public void browserIdentityDoesNotChangeAcrossAuthenticationRedirect() {
        String login = AcademicWebPolicy.userAgent(
                "https://ids.example.edu.cn/authserver/login?service=test");
        String portal = AcademicWebPolicy.userAgent(
                "https://jw.example.edu.cn/eams/homeExt.action");
        assertTrue(login.contains("Windows NT"));
        assertTrue(login.equals(portal));
    }

    @Test
    public void layoutPolicyDependsOnPathRatherThanSchoolHost() {
        String[] hosts = {"neu.edu.cn", "neuq.edu.cn", "zju.edu.cn", "example.edu.cn"};
        for (String host : hosts) {
            assertTrue(AcademicWebPolicy.useMobileAuthenticationLayout(
                    "https://" + host + "/cas/login"));
            assertFalse(AcademicWebPolicy.useMobileAuthenticationLayout(
                    "https://" + host + "/eams/homeExt.action"));
        }
    }
}
