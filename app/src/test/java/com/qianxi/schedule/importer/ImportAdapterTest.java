package com.qianxi.schedule.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ImportAdapterTest {
    @Test
    public void schoolHostsDoNotSelectSchoolSpecificAdapters() {
        assertEquals(ImportAdapter.GENERIC,
                ImportAdapter.detect("https://jwxt.neu.edu.cn/jwapp/sys/homeapp/index.do"));
        assertEquals(ImportAdapter.GENERIC,
                ImportAdapter.detect("https://jwxt.neuq.edu.cn/eams/homeExt.action"));
        assertEquals(ImportAdapter.GENERIC,
                ImportAdapter.detect("https://webvpn.neu.edu.cn/https/encoded"));
        assertEquals(ImportAdapter.GENERIC,
                ImportAdapter.detect("https://vpn.neuq.edu.cn/http/encoded/eams/homeExt.action"));
    }

    @Test
    public void legacySchoolIdsMigrateToAutomaticDetection() {
        assertEquals(ImportAdapter.AUTO, ImportAdapter.migrateLegacyId(ImportAdapter.NEU));
        assertEquals(ImportAdapter.AUTO, ImportAdapter.migrateLegacyId(ImportAdapter.NEUQ_EAMS));
        assertEquals(ImportAdapter.indexOf(ImportAdapter.AUTO),
                ImportAdapter.indexOf(ImportAdapter.NEU));
        assertEquals(ImportAdapter.GENERIC, ImportAdapter.resolve(ImportAdapter.NEU,
                "https://jw.example.edu.cn/schedule"));
        for (ImportAdapter.Definition definition : ImportAdapter.definitions()) {
            assertFalse(ImportAdapter.NEU.equals(definition.id));
            assertFalse(ImportAdapter.NEUQ_EAMS.equals(definition.id));
        }
    }

    @Test
    public void detectsOnlyPortableVendorPathsBeforePageScanning() {
        assertEquals(ImportAdapter.ZHENGFANG,
                ImportAdapter.detect("https://zdbk.zju.edu.cn/jwglxt/xtgl/index_initMenu.html"));
        assertEquals(ImportAdapter.QIANGZHI,
                ImportAdapter.detect("https://jw.example.edu.cn/jsxsd/xskb/xskb_list.do"));
        assertEquals(ImportAdapter.KINGOSOFT,
                ImportAdapter.detect("https://jw.example.edu.cn/urp/student/course"));
        assertEquals(ImportAdapter.GENERIC,
                ImportAdapter.detect("https://jw.example.edu.cn/eams/homeExt.action"));
        assertEquals(ImportAdapter.GENERIC,
                ImportAdapter.detect("https://jw.example.edu.cn/jwapp/sys/homeapp/index.do"));
    }

    @Test
    public void everyAdapterUsesTheSameCurrentPageScanner() {
        String generic = ImportScript.forAdapter(ImportAdapter.GENERIC);
        String[] ids = {
                ImportAdapter.AUTO, ImportAdapter.ZHENGFANG, ImportAdapter.QIANGZHI,
                ImportAdapter.KINGOSOFT, ImportAdapter.BEANGLE_EAMS, ImportAdapter.EMAP,
                ImportAdapter.NEU, ImportAdapter.NEUQ_EAMS
        };
        for (String id : ids) {
            assertEquals(generic, ImportScript.forAdapter(id));
            assertEquals(generic, ImportScript.forAdapter(id, "https://school.example/kb"));
        }
        assertTrue(generic.contains("collectDocuments"));
        assertTrue(generic.contains("collectQueryRoots"));
        assertTrue(generic.contains("scanVisualTimetable"));
        assertTrue(generic.contains("scanBeangleRuntime"));
        assertTrue(generic.contains("binaryWeeks"));
        assertTrue(generic.contains("runtimeActivities"));
        assertFalse(generic.contains("currentUser.do"));
        assertFalse(generic.contains("fetch("));
    }

    @Test
    public void genericScannerMapsParityColumnsAndStructureFingerprints() {
        String script = ImportScript.forAdapter(ImportAdapter.GENERIC);
        assertTrue(script.contains("columnDays"));
        assertTrue(script.contains("columnParity"));
        assertTrue(script.contains("originColumn"));
        assertTrue(script.contains("firstDayColumn"));
        assertTrue(script.contains("__INITIAL_STATE__"));
        assertTrue(script.contains("'KCMC'"));
        assertTrue(script.contains("'KSJC'"));
        assertTrue(script.contains("node.shadowRoot"));
        assertTrue(script.contains("fingerprintMap"));
        assertTrue(script.contains("fingerprint('emap'"));
        assertTrue(script.contains("fingerprint('eams'"));
        assertTrue(script.contains("fingerprint('eams', 12, '运行时活动对象')"));
        assertTrue(script.contains("layout = strongest.id"));
        assertTrue(script.contains("['zhengfang', 'qiangzhi', 'kingosoft', 'eams', 'emap']"));
        assertFalse(script.contains("fingerprint('neu'"));
        assertFalse(script.contains("const dayColumns = {}"));
    }

    @Test
    public void genericScannerIncludesWakeupCompatibleLayoutPlugins() {
        String script = ImportScript.forAdapter(ImportAdapter.GENERIC);
        assertTrue(script.contains("'[title=\"教师\"] font'"));
        assertTrue(script.contains("'[title=\"周次(节次)\"]'"));
        assertTrue(script.contains("mode === 'qiangzhi'"));
        assertTrue(script.contains("groupedRow * 2 - 1"));
        assertTrue(script.contains("property.test(tokens[nameIndex])"));
        assertTrue(script.contains("timeIndexes.length"));
        assertTrue(script.contains("String(part == null ? '' : part).replace"));
        assertTrue(script.contains("scanQiangzhiWakeup"));
        assertTrue(script.contains("qiangzhiFallback"));
        assertTrue(script.contains("row.cells || []"));
        assertTrue(script.contains("scanVueRoot"));
        assertTrue(script.contains("__vueParentComponent"));
        assertTrue(script.contains("structuredCandidates"));
        assertTrue(script.contains("selectedSource = vueRoots > 0 ? 'vue-runtime' : 'structured-data'"));
        assertFalse(script.contains("part.replace(/<[^>]+>/g"));
        assertTrue(script.contains("typeof node.getAttribute === 'function'"));
        assertTrue(script.contains("typeof node.querySelector !== 'function'"));
        assertTrue(script.contains("weeksAndTeachers"));
        assertTrue(script.contains("titleDetail"));
        assertTrue(script.contains("startTime"));
        assertTrue(script.contains("runtimeActivities"));
        assertTrue(script.contains("scanRecordTable"));
        assertTrue(script.contains("recordRole"));
        assertTrue(script.contains("recordRows"));
        assertTrue(script.contains("Nsb_table"));
    }
}
