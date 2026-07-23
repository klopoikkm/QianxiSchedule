package com.qianxi.schedule.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qianxi.schedule.data.AppSettings;
import com.qianxi.schedule.data.ScheduleTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClassTimesActivity extends Activity {
    private AppSettings settings;
    private List<AppSettings.ClassTime> classTimes;
    private LinearLayout timesContainer;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        classTimes = new ArrayList<>(settings.classTimes());
        View content = buildContent();
        Ui.applySystemBarInsets(content);
        setContentView(content);
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        Button back = Ui.textButton(this, "‹");
        back.setTextSize(28);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));
        TextView title = Ui.text(this, "上课时间", 21, Ui.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        Button save = Ui.textButton(this, "保存");
        save.setOnClickListener(v -> saveAndFinish());
        toolbar.addView(save, new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 48)));
        root.addView(toolbar);
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));

        TextView hint = Ui.text(this, "点击时间修改，长按删除节次", 13, Ui.MUTED);
        hint.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 10));
        content.addView(hint);

        timesContainer = new LinearLayout(this);
        timesContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(timesContainer);

        Button add = Ui.textButton(this, "+ 添加节次");
        add.setOnClickListener(v -> addPeriod());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        addParams.topMargin = Ui.dp(this, 8);
        content.addView(add, addParams);

        Button reset = Ui.textButton(this, "恢复默认作息");
        reset.setOnClickListener(v -> resetToDefaults());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        resetParams.topMargin = Ui.dp(this, 4);
        content.addView(reset, resetParams);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        refreshTimesList();
        return root;
    }

    private void refreshTimesList() {
        timesContainer.removeAllViews();
        for (int i = 0; i < classTimes.size(); i++) {
            final int index = i;
            AppSettings.ClassTime time = classTimes.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
            row.setBackground(Ui.rounded(Color.WHITE, 6, this));

            TextView period = Ui.text(this, String.format(Locale.CHINA, "第 %d 节", time.period), 15, Ui.INK);
            period.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            row.addView(period, new LinearLayout.LayoutParams(Ui.dp(this, 80), Ui.dp(this, 48)));

            TextView startTime = Ui.text(this, ScheduleTime.formatMinutes(time.startMinute), 14, Ui.PRIMARY);
            startTime.setGravity(Gravity.CENTER);
            startTime.setBackground(Ui.rounded(Color.rgb(240, 247, 255), 4, this));
            startTime.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            startTime.setOnClickListener(v -> editStartTime(index));
            row.addView(startTime, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));

            TextView separator = Ui.text(this, "—", 14, Ui.MUTED);
            separator.setGravity(Gravity.CENTER);
            row.addView(separator, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 40)));

            TextView endTime = Ui.text(this, ScheduleTime.formatMinutes(time.endMinute), 14, Ui.PRIMARY);
            endTime.setGravity(Gravity.CENTER);
            endTime.setBackground(Ui.rounded(Color.rgb(240, 247, 255), 4, this));
            endTime.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            endTime.setOnClickListener(v -> editEndTime(index));
            row.addView(endTime, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));

            row.setOnLongClickListener(v -> {
                deletePeriod(index);
                return true;
            });

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = Ui.dp(this, 6);
            timesContainer.addView(row, rowParams);
        }
    }

    private void editStartTime(int index) {
        AppSettings.ClassTime time = classTimes.get(index);
        int hour = time.startMinute / 60;
        int minute = time.startMinute % 60;
        new TimePickerDialog(this, (view, h, m) -> {
            int newStart = h * 60 + m;
            if (newStart >= time.endMinute) {
                Toast.makeText(this, "开始时间不能晚于结束时间", Toast.LENGTH_SHORT).show();
                return;
            }
            classTimes.set(index, new AppSettings.ClassTime(time.period, newStart, time.endMinute));
            refreshTimesList();
        }, hour, minute, true).show();
    }

    private void editEndTime(int index) {
        AppSettings.ClassTime time = classTimes.get(index);
        int hour = time.endMinute / 60;
        int minute = time.endMinute % 60;
        new TimePickerDialog(this, (view, h, m) -> {
            int newEnd = h * 60 + m;
            if (newEnd <= time.startMinute) {
                Toast.makeText(this, "结束时间不能早于开始时间", Toast.LENGTH_SHORT).show();
                return;
            }
            classTimes.set(index, new AppSettings.ClassTime(time.period, time.startMinute, newEnd));
            refreshTimesList();
        }, hour, minute, true).show();
    }

    private void addPeriod() {
        int nextPeriod = classTimes.isEmpty() ? 1 : classTimes.get(classTimes.size() - 1).period + 1;
        int lastEnd = classTimes.isEmpty() ? 8 * 60 : classTimes.get(classTimes.size() - 1).endMinute + 10;
        classTimes.add(new AppSettings.ClassTime(nextPeriod, lastEnd, lastEnd + 45));
        refreshTimesList();
    }

    private void deletePeriod(int index) {
        new AlertDialog.Builder(this)
                .setTitle("删除节次")
                .setMessage("确定删除第 " + classTimes.get(index).period + " 节？")
                .setPositiveButton("删除", (d, w) -> {
                    classTimes.remove(index);
                    refreshTimesList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void resetToDefaults() {
        new AlertDialog.Builder(this)
                .setTitle("恢复默认")
                .setMessage("将作息时间重置为默认配置（12 节，08:00-20:35）？")
                .setPositiveButton("恢复", (d, w) -> {
                    settings.setClassTimes(new ArrayList<>());
                    classTimes = new ArrayList<>(settings.classTimes());
                    refreshTimesList();
                    Toast.makeText(this, "已恢复默认作息", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveAndFinish() {
        if (classTimes.isEmpty()) {
            Toast.makeText(this, "至少保留一个节次", Toast.LENGTH_SHORT).show();
            return;
        }
        settings.setClassTimes(classTimes);
        Toast.makeText(this, "作息时间已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
