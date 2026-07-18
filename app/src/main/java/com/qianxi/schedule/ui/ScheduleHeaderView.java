package com.qianxi.schedule.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Fixed weekday/date header paired with the vertically scrolling schedule body. */
public final class ScheduleHeaderView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LocalDate weekStart = LocalDate.now();

    public ScheduleHeaderView(Context context) {
        super(context);
        setBackgroundColor(Ui.PAPER);
    }

    public void setWeekStart(LocalDate start) {
        weekStart = start;
        setContentDescription(String.format(Locale.CHINA, "%s 至 %s的周课表",
                start, start.plusDays(6)));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(Ui.dp(getContext(), 360), widthMeasureSpec),
                resolveSize(Ui.dp(getContext(), 48), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float timeWidth = Ui.dp(getContext(), 34);
        float dayWidth = (getWidth() - timeWidth) / 7f;
        LocalDate today = LocalDate.now();
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d", Locale.CHINA);

        for (int day = 0; day < 7; day++) {
            if (weekStart.plusDays(day).equals(today)) {
                paint.setColor(Color.rgb(232, 247, 241));
                canvas.drawRect(timeWidth + day * dayWidth, 0,
                        timeWidth + (day + 1) * dayWidth, getHeight(), paint);
            }
        }
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, timeWidth, getHeight(), paint);

        paint.setColor(Ui.LINE);
        paint.setStrokeWidth(Ui.dp(getContext(), 1));
        for (int day = 0; day <= 7; day++) {
            float x = timeWidth + day * dayWidth;
            canvas.drawLine(x, 0, x, getHeight(), paint);
        }
        canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, paint);

        for (int day = 0; day < 7; day++) {
            float center = timeWidth + (day + 0.5f) * dayWidth;
            boolean isToday = weekStart.plusDays(day).equals(today);
            drawCenteredText(canvas, names[day], center, Ui.dp(getContext(), 19), 11,
                    isToday ? Ui.PRIMARY : Ui.INK, true);
            drawCenteredText(canvas, weekStart.plusDays(day).format(formatter), center,
                    Ui.dp(getContext(), 38), 9, isToday ? Ui.PRIMARY : Ui.MUTED, false);
        }
    }

    private void drawCenteredText(Canvas canvas, String value, float x, float baseline,
                                  float sp, int color, boolean bold) {
        paint.setColor(color);
        paint.setTextSize(Ui.sp(getContext(), sp));
        paint.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD
                : android.graphics.Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(value, x, baseline, paint);
    }
}
