package com.qianxi.schedule.importer;

import static org.junit.Assert.assertEquals;
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
    public void neuqAdapterUsesAuthenticatedCourseTableEndpoint() {
        String script = ImportScript.forAdapter(ImportAdapter.NEUQ_EAMS);
        assertTrue(script.contains("/eams/courseTableForStd.action"));
        assertTrue(script.contains("/eams/courseTableForStd!courseTable.action"));
        assertTrue(script.contains("semester.id"));
        assertTrue(script.contains("credentials: 'include'"));
    }
}
