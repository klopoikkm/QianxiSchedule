package com.qianxi.schedule.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ImportAdapterTest {
    @Test
    public void detectsNortheasternUniversityHost() {
        assertEquals(ImportAdapter.NEU,
                ImportAdapter.detect("https://jwxt.neu.edu.cn/jwapp/sys/homeapp/index.do"));
    }

    @Test
    public void keepsUnknownCustomUrlGeneric() {
        assertEquals(ImportAdapter.GENERIC,
                ImportAdapter.detect("https://jw.example.edu.cn/schedule"));
    }

    @Test
    public void detectsNortheasternUniversityQinhuangdaoEamsHost() {
        assertEquals(ImportAdapter.NEUQ_EAMS,
                ImportAdapter.detect("https://jwxt.neuq.edu.cn/eams/homeExt.action"));
    }

    @Test
    public void exposesOnlyOneNortheasternUniversityChoice() {
        assertEquals(ImportAdapter.indexOf(ImportAdapter.NEU),
                ImportAdapter.indexOf(ImportAdapter.NEUQ_EAMS));
        for (ImportAdapter.Definition definition : ImportAdapter.definitions()) {
            assertFalse(ImportAdapter.NEUQ_EAMS.equals(definition.id));
        }
    }

    @Test
    public void unifiedNortheasternUniversityChoiceUsesEamsInternallyForNeuq() {
        assertEquals(ImportAdapter.NEUQ_EAMS, ImportAdapter.resolve(ImportAdapter.NEU,
                "https://jwxt.neuq.edu.cn/eams/homeExt.action"));
        assertEquals(ImportAdapter.NEU, ImportAdapter.resolve(ImportAdapter.NEU,
                "https://jwxt.neu.edu.cn/"));
    }

    @Test
    public void detectsCompatibleSystemsFromCustomUrlPaths() {
        assertEquals(ImportAdapter.NEUQ_EAMS,
                ImportAdapter.detect("https://jw.example.edu.cn/eams/homeExt.action"));
        assertEquals(ImportAdapter.NEU,
                ImportAdapter.detect("https://jw.example.edu.cn/jwapp/sys/homeapp/index.do"));
    }

    @Test
    public void specialAdaptersDoNotRejectCustomHosts() {
        assertFalse(ImportScript.forAdapter(ImportAdapter.NEU).contains("jwxt\\.neu\\.edu\\.cn$"));
        assertFalse(ImportScript.forAdapter(ImportAdapter.NEUQ_EAMS)
                .contains("jwxt\\.neuq\\.edu\\.cn$"));
        assertFalse(ImportScript.forAdapter(ImportAdapter.NEUQ_EAMS).contains("courseTableForStd"));
    }

    @Test
    public void allAdaptersScanOnlyTheCurrentPage() {
        String generic = ImportScript.forAdapter(ImportAdapter.GENERIC);
        assertEquals(generic, ImportScript.forAdapter(ImportAdapter.NEU));
        assertEquals(generic, ImportScript.forAdapter(ImportAdapter.NEUQ_EAMS));
        String script = ImportScript.forAdapter(ImportAdapter.NEUQ_EAMS);
        assertTrue(script.contains("document.querySelectorAll"));
        assertFalse(script.contains("fetch("));
    }
}
