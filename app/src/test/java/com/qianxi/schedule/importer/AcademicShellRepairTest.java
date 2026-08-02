package com.qianxi.schedule.importer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public final class AcademicShellRepairTest {
    @Test
    public void inspectsOnlyOrdinaryMainFrameEamsGets() {
        assertTrue(AcademicShellRepair.shouldInspect(
                "https://jw.example.edu.cn/eams/homeExt.action", "GET", true));
        assertTrue(AcademicShellRepair.shouldInspect(
                "https://vpn.example.edu.cn/https/token/eams/homeExt.action", "GET", true));
        assertFalse(AcademicShellRepair.shouldInspect(
                "https://jw.example.edu.cn/eams/homeExt.action?ticket=ST-1", "GET", true));
        assertFalse(AcademicShellRepair.shouldInspect(
                "https://jw.example.edu.cn/eams/homeExt.action", "POST", true));
        assertFalse(AcademicShellRepair.shouldInspect(
                "https://jw.example.edu.cn/eams/homeExt.action", "GET", false));
        assertFalse(AcademicShellRepair.shouldInspect(
                "https://jw.example.edu.cn/jwglxt/homeExt.action", "GET", true));
    }

    @Test
    public void injectsOfficialFoundationBeforeInlineScripts() {
        byte[] html = ("<html><head><script>jQuery(function(){bg.Go('/eams/a.action')})"
                + "</script></head></html>").getBytes(StandardCharsets.UTF_8);
        AcademicShellRepair.Result result = AcademicShellRepair.repair(
                "https://jw.example.edu.cn/eams/homeExt.action", html);
        String repaired = new String(result.body, StandardCharsets.UTF_8);
        assertTrue(result.changed);
        assertTrue(repaired.contains("/eams/static/scripts/jquery/jquery,/scripts/beangle/beangle.js"));
        assertTrue(repaired.contains("/eams/static/themes/default/beangle-ui.css"));
        assertTrue(repaired.contains("data-qianxi-shell-repair"));
        assertTrue(repaired.indexOf("data-qianxi-shell-repair")
                < repaired.indexOf("jQuery(function"));
    }

    @Test
    public void preservesWebVpnPrefixInFoundationUrls() {
        byte[] html = "<html><script>bg.Go('/x')</script></html>"
                .getBytes(StandardCharsets.UTF_8);
        String repaired = new String(AcademicShellRepair.repair(
                "https://vpn.example.edu.cn/https/token/eams/homeExt.action", html).body,
                StandardCharsets.UTF_8);
        assertTrue(repaired.contains(
                "/https/token/eams/static/scripts/jquery/jquery,/scripts/beangle/beangle.js"));
    }

    @Test
    public void leavesAlreadyDecoratedAndUnrelatedHtmlUntouched() {
        byte[] decorated = ("<script src='/eams/static/scripts/jquery/jquery,"
                + "/scripts/beangle/beangle.js'></script><script>bg.Go('/x')</script>")
                .getBytes(StandardCharsets.UTF_8);
        byte[] unrelated = "<html><p>plain page</p></html>".getBytes(StandardCharsets.UTF_8);
        AcademicShellRepair.Result first = AcademicShellRepair.repair(
                "https://jw.example.edu.cn/eams/homeExt.action", decorated);
        AcademicShellRepair.Result second = AcademicShellRepair.repair(
                "https://jw.example.edu.cn/eams/homeExt.action", unrelated);
        assertFalse(first.changed);
        assertFalse(second.changed);
        assertSame(decorated, first.body);
        assertSame(unrelated, second.body);
    }
}
