package com.qianxi.schedule.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ScheduleGridMetricsTest {
    @Test
    public void clampsTheSharedRowHeight() {
        assertEquals(40, ScheduleGridMetrics.fromItemHeight(20).itemHeightDp);
        assertEquals(80, ScheduleGridMetrics.fromItemHeight(120).itemHeightDp);
    }

    @Test
    public void axisTypographyGrowsWithCourseRows() {
        ScheduleGridMetrics compact = ScheduleGridMetrics.fromItemHeight(40);
        ScheduleGridMetrics defaultSize = ScheduleGridMetrics.fromItemHeight(56);
        ScheduleGridMetrics tall = ScheduleGridMetrics.fromItemHeight(80);

        assertTrue(compact.periodTextSp < defaultSize.periodTextSp);
        assertTrue(defaultSize.periodTextSp < tall.periodTextSp);
        assertTrue(compact.timeTextSp < defaultSize.timeTextSp);
        assertTrue(defaultSize.timeTextSp < tall.timeTextSp);
        assertTrue(compact.axisLineSpacingMultiplier < tall.axisLineSpacingMultiplier);
        assertTrue(compact.timeColumnWidthDp <= tall.timeColumnWidthDp);
    }
}
