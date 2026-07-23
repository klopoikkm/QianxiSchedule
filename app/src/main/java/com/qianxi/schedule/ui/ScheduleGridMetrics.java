package com.qianxi.schedule.ui;

/**
 * Size values shared by the period axis and course grid.
 *
 * <p>WakeUp keeps one item height as the source of truth for both sides of the timetable. We keep
 * that rule, then derive the narrow axis typography from the same value so a compact grid does not
 * clip three fixed-size lines and a tall grid does not leave tiny time labels floating in the row.
 */
final class ScheduleGridMetrics {
    static final int MIN_ITEM_HEIGHT_DP = 40;
    static final int MAX_ITEM_HEIGHT_DP = 80;

    final int itemHeightDp;
    final int timeColumnWidthDp;
    final float periodTextSp;
    final float timeTextSp;
    final float axisLineSpacingMultiplier;
    final int axisVerticalPaddingDp;

    private ScheduleGridMetrics(int itemHeightDp, int timeColumnWidthDp,
                                float periodTextSp, float timeTextSp,
                                float axisLineSpacingMultiplier,
                                int axisVerticalPaddingDp) {
        this.itemHeightDp = itemHeightDp;
        this.timeColumnWidthDp = timeColumnWidthDp;
        this.periodTextSp = periodTextSp;
        this.timeTextSp = timeTextSp;
        this.axisLineSpacingMultiplier = axisLineSpacingMultiplier;
        this.axisVerticalPaddingDp = axisVerticalPaddingDp;
    }

    static ScheduleGridMetrics fromItemHeight(int requestedDp) {
        int height = Math.max(MIN_ITEM_HEIGHT_DP, Math.min(MAX_ITEM_HEIGHT_DP, requestedDp));
        float progress = (height - MIN_ITEM_HEIGHT_DP)
                / (float) (MAX_ITEM_HEIGHT_DP - MIN_ITEM_HEIGHT_DP);
        return new ScheduleGridMetrics(
                height,
                Math.round(28f + 2f * progress),
                lerp(10f, 14f, progress),
                lerp(6.5f, 9f, progress),
                lerp(0.82f, 0.94f, progress),
                Math.round(1f + 2f * progress));
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }
}
