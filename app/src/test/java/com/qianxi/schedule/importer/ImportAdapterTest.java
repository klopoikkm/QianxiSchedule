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
    public void detectsNeuqWebVpnAsNortheastUniversity() {
        assertEquals(ImportAdapter.NEUQ_EAMS,
                ImportAdapter.detect("https://vpn.neuq.edu.cn/http/77726476706e69737468656265737421"));
    }

    @Test
    public void hostDetectionOverridesMismatchedManualPick() {
        // A NEUQ EAMS URL must resolve to the EAMS adapter even if the user left the dropdown on a
        // different system (e.g. 青果/kingosoft) — otherwise the generic DOM scan runs on a portal
        // page that never finished rendering.
        assertEquals(ImportAdapter.NEUQ_EAMS, ImportAdapter.resolve(ImportAdapter.KINGOSOFT,
                "https://jwxt.neuq.edu.cn/eams/homeExt.action"));
        assertEquals(ImportAdapter.NEU, ImportAdapter.resolve(ImportAdapter.ZHENGFANG,
                "https://jwxt.neu.edu.cn/jwapp/sys/homeapp/index.do"));
    }

    @Test
    public void neuqAdapterUsesEamsApiScript() {
        // NEUQ must run the EAMS API script (fetches courseTableForStd + parses TaskActivity weeks),
        // not the generic DOM scanner — the portal page renders as a skeleton without jQuery.
        String neuq = ImportScript.forAdapter(ImportAdapter.NEUQ_EAMS);
        assertTrue(neuq.contains("courseTableForStd"));
        assertTrue(neuq.contains("TaskActivity"));
    }

    @Test
    public void adaptersRouteToDistinctScripts() {
        String generic = ImportScript.forAdapter(ImportAdapter.GENERIC);
        String neuq = ImportScript.forAdapter(ImportAdapter.NEUQ_EAMS);
        String neu = ImportScript.forAdapter(ImportAdapter.NEU);
        assertFalse(generic.equals(neuq));
        assertFalse(generic.equals(neu));
        assertTrue(generic.contains("document.querySelectorAll"));
    }
}
