package com.qianxi.schedule.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import com.qianxi.schedule.data.Course;
import com.qianxi.schedule.data.ScheduleTime;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ScheduleView extends View {
    public interface Listener {
        void onCourseClick(Course course);
        void onEmptySlotClick(int dayOfWeek, int minute);
    }

    private static final int START_MINUTE = 7 * 60;
    private static final int END_MINUTE = 22 * 60;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final List<Course> courses = new ArrayList<>();
    private final Map<Course, RectF> hitAreas = new IdentityHashMap<>();
    private LocalDate weekStart = LocalDate.now();
    private Listener listener;
    private float downX;
    private float downY;

    public ScheduleView(Context context) {
        super(context);
        setBackgroundColor(Ui.PAPER);
        setFocusable(true);
        setContentDescription("周课表");
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<Course> values, LocalDate start) {
        courses.clear();
        courses.addAll(values);
        courses.sort(Comparator.comparingInt((Course c) -> c.dayOfWeek)
                .thenComparingInt(c -> c.startMinute));
        weekStart = start;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int wantedWidth = Ui.dp(getContext(), 360);
        int wantedHeight = Ui.dp(getContext(), 760);
        setMeasuredDimension(resolveSize(wantedWidth, widthMeasureSpec),
                resolveSize(wantedHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        hitAreas.clear();
        float timeWidth = Ui.dp(getContext(), 34);
        float headerHeight = Ui.dp(getContext(), 48);
        float dayWidth = (getWidth() - timeWidth) / 7f;
        float minuteHeight = (getHeight() - headerHeight) / (float) (END_MINUTE - START_MINUTE);

        drawTodayBand(canvas, timeWidth, headerHeight, dayWidth);
        drawGrid(canvas, timeWidth, headerHeight, dayWidth, minuteHeight);
        drawCourses(canvas, timeWidth, headerHeight, dayWidth, minuteHeight);
        drawNowLine(canvas, timeWidth, headerHeight, dayWidth, minuteHeight);
    }

    private void drawTodayBand(Canvas canvas, float timeWidth, float headerHeight, float dayWidth) {
        LocalDate today = LocalDate.now();
        for (int day = 0; day < 7; day++) {
            if (weekStart.plusDays(day).equals(today)) {
                paint.setColor(Color.rgb(232, 247, 241));
                canvas.drawRect(timeWidth + day * dayWidth, 0,
                        timeWidth + (day + 1) * dayWidth, getHeight(), paint);
            }
        }
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, timeWidth, headerHeight, paint);
    }

    private void drawGrid(Canvas canvas, float timeWidth, float headerHeight,
                          float dayWidth, float minuteHeight) {
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d", Locale.CHINA);
        paint.setStrokeWidth(Ui.dp(getContext(), 1));
        paint.setColor(Ui.LINE);

        for (int day = 0; day <= 7; day++) {
            float x = timeWidth + day * dayWidth;
            canvas.drawLine(x, 0, x, getHeight(), paint);
        }
        canvas.drawLine(0, headerHeight, getWidth(), headerHeight, paint);

        for (int hour = 7; hour <= 22; hour++) {
            float y = headerHeight + (hour * 60 - START_MINUTE) * minuteHeight;
            paint.setColor(hour == 7 ? Ui.LINE : Color.rgb(235, 237, 234));
            canvas.drawLine(timeWidth, y, getWidth(), y, paint);
            drawCenteredText(canvas, String.format(Locale.CHINA, "%02d", hour),
                    timeWidth / 2f, y + Ui.dp(getContext(), 10), 9, Ui.MUTED, false);
        }

        for (int day = 0; day < 7; day++) {
            float center = timeWidth + (day + 0.5f) * dayWidth;
            boolean today = weekStart.plusDays(day).equals(LocalDate.now());
            drawCenteredText(canvas, names[day], center, Ui.dp(getContext(), 19), 11,
                    today ? Ui.PRIMARY : Ui.INK, true);
            drawCenteredText(canvas, weekStart.plusDays(day).format(formatter), center,
                    Ui.dp(getContext(), 38), 9, today ? Ui.PRIMARY : Ui.MUTED, false);
        }
    }

    private void drawCourses(Canvas canvas, float timeWidth, float headerHeight,
                             float dayWidth, float minuteHeight) {
        Map<Course, Integer> lanes = new IdentityHashMap<>();
        Map<Integer, Integer> laneCounts = assignLanes(lanes);
        for (Course course : courses) {
            int laneCount = Math.max(1, laneCounts.getOrDefault(course.dayOfWeek, 1));
            int lane = lanes.getOrDefault(course, 0);
            float available = dayWidth - Ui.dp(getContext(), 2);
            float blockWidth = available / laneCount;
            float left = timeWidth + (course.dayOfWeek - 1) * dayWidth
                    + Ui.dp(getContext(), 1) + lane * blockWidth;
            float right = left + blockWidth - Ui.dp(getContext(), 1);
            float top = headerHeight + (Math.max(START_MINUTE, course.startMinute) - START_MINUTE) * minuteHeight + 1;
            float bottom = headerHeight + (Math.min(END_MINUTE, course.endMinute) - START_MINUTE) * minuteHeight - 1;
            if (bottom <= top) continue;

            RectF rect = new RectF(left, top, right, bottom);
            hitAreas.put(course, rect);
            paint.setColor(course.color);
            canvas.drawRoundRect(rect, Ui.dp(getContext(), 5), Ui.dp(getContext(), 5), paint);

            StringBuilder label = new StringBuilder(course.name);
            if (!course.location.isEmpty() && bottom - top > Ui.dp(getContext(), 58)) {
                label.append("\n").append(course.location);
            }
            drawCourseText(canvas, label.toString(), rect);
        }
    }

    private Map<Integer, Integer> assignLanes(Map<Course, Integer> lanes) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int day = 1; day <= 7; day++) {
            List<Integer> laneEnds = new ArrayList<>();
            for (Course course : courses) {
                if (course.dayOfWeek != day) continue;
                int lane = 0;
                while (lane < laneEnds.size() && laneEnds.get(lane) > course.startMinute) lane++;
                if (lane == laneEnds.size()) laneEnds.add(course.endMinute);
                else laneEnds.set(lane, course.endMinute);
                lanes.put(course, lane);
            }
            counts.put(day, Math.max(1, laneEnds.size()));
        }
        return counts;
    }

    private void drawCourseText(Canvas canvas, String value, RectF rect) {
        int padding = Ui.dp(getContext(), 2);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(Ui.sp(getContext(), 9));
        textPaint.setFakeBoldText(true);
        int width = Math.max(1, (int) rect.width() - padding * 2);
        canvas.save();
        canvas.clipRect(rect);
        canvas.translate(rect.left + padding, rect.top + padding);
        int maxLines = Math.max(1, (int) ((rect.height() - padding * 2) / Ui.sp(getContext(), 11)));
        StaticLayout layout = StaticLayout.Builder.obtain(value, 0, value.length(), textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0, 1.0f)
                .setIncludePad(false)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setMaxLines(maxLines)
                .build();
        layout.draw(canvas);
        canvas.restore();
    }

    private void drawNowLine(Canvas canvas, float timeWidth, float headerHeight,
                             float dayWidth, float minuteHeight) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(weekStart) || today.isAfter(weekStart.plusDays(6))) return;
        java.time.LocalTime now = java.time.LocalTime.now();
        int minute = now.getHour() * 60 + now.getMinute();
        if (minute < START_MINUTE || minute > END_MINUTE) return;
        int day = today.getDayOfWeek().getValue() - 1;
        float y = headerHeight + (minute - START_MINUTE) * minuteHeight;
        float left = timeWidth + day * dayWidth;
        paint.setColor(Ui.ACCENT);
        paint.setStrokeWidth(Ui.dp(getContext(), 2));
        canvas.drawLine(left, y, left + dayWidth, y, paint);
        canvas.drawCircle(left + Ui.dp(getContext(), 3), y, Ui.dp(getContext(), 3), paint);
    }

    private void drawCenteredText(Canvas canvas, String value, float x, float baseline,
                                  float sp, int color, boolean bold) {
        paint.setColor(color);
        paint.setTextSize(Ui.sp(getContext(), sp));
        paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(value, x, baseline, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (Math.abs(event.getX() - downX) > Ui.dp(getContext(), 8)
                    || Math.abs(event.getY() - downY) > Ui.dp(getContext(), 8)) return true;
            performClick();
            for (Map.Entry<Course, RectF> entry : hitAreas.entrySet()) {
                if (entry.getValue().contains(event.getX(), event.getY())) {
                    if (listener != null) listener.onCourseClick(entry.getKey());
                    return true;
                }
            }
            float timeWidth = Ui.dp(getContext(), 34);
            float headerHeight = Ui.dp(getContext(), 48);
            if (event.getX() > timeWidth && event.getY() > headerHeight && listener != null) {
                int day = Math.max(1, Math.min(7,
                        (int) ((event.getX() - timeWidth) / ((getWidth() - timeWidth) / 7f)) + 1));
                int minute = START_MINUTE + Math.round((event.getY() - headerHeight)
                        / (getHeight() - headerHeight) * (END_MINUTE - START_MINUTE));
                minute = Math.max(START_MINUTE, Math.min(END_MINUTE - 30, minute / 5 * 5));
                listener.onEmptySlotClick(day, minute);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
