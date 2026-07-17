package com.qianxi.schedule.importer;

import static org.junit.Assert.assertEquals;

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
}
