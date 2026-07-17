package com.qianxi.schedule.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.qianxi.schedule.data.AppSettings;
import com.qianxi.schedule.data.Course;
import com.qianxi.schedule.data.CourseDatabase;
import com.qianxi.schedule.data.ScheduleTime;
import com.qianxi.schedule.silence.AlarmScheduler;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity implements ScheduleView.Listener {
    private AppSettings settings;
    private CourseDatabase database;
    private ScheduleView scheduleView;
    private TextView weekLabel;
    private TextView silentStatus;
    private TextView emptyState;
    private int selectedWeek;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        database = CourseDatabase.get(this);
        selectedWeek = ScheduleTime.weekOf(settings.semesterStart(), LocalDate.now());
        setContentView(buildContent());
        AlarmScheduler.ensureDailyRefresh(this);
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 8));
        TextView title = Ui.text(this, "潜溪课表", 26, Ui.INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        silentStatus = Ui.text(this, "", 12, Ui.PRIMARY);
        silentStatus.setGravity(Gravity.CENTER);
        silentStatus.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        silentStatus.setBackground(Ui.rounded(Color.rgb(229, 245, 238), 6, this));
        silentStatus.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        brand.addView(silentStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(brand);

        LinearLayout weekBar = new LinearLayout(this);
        weekBar.setGravity(Gravity.CENTER_VERTICAL);
        weekBar.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 7));
        Button previous = Ui.textButton(this, "‹");
        previous.setTextSize(28);
        previous.setContentDescription("上一周");
        previous.setOnClickListener(v -> changeWeek(-1));
        weekBar.addView(previous, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 44)));

        weekLabel = Ui.text(this, "", 15, Ui.INK);
        weekLabel.setGravity(Gravity.CENTER);
        weekLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        weekBar.addView(weekLabel, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));

        Button today = Ui.textButton(this, "本周");
        today.setOnClickListener(v -> {
            selectedWeek = ScheduleTime.weekOf(settings.semesterStart(), LocalDate.now());
            refresh();
        });
        weekBar.addView(today, new LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 44)));
        Button next = Ui.textButton(this, "›");
        next.setTextSize(28);
        next.setContentDescription("下一周");
        next.setOnClickListener(v -> changeWeek(1));
        weekBar.addView(next, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 44)));
        root.addView(weekBar);
        root.addView(Ui.divider(this));

        FrameLayout scheduleContainer = new FrameLayout(this);
        ScrollView vertical = new ScrollView(this);
        vertical.setFillViewport(false);
        vertical.setOverScrollMode(View.OVER_SCROLL_NEVER);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(true);
        horizontal.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scheduleView = new ScheduleView(this);
        scheduleView.setListener(this);
        horizontal.addView(scheduleView, new HorizontalScrollView.LayoutParams(
                Ui.dp(this, 720), Ui.dp(this, 980)));
        vertical.addView(horizontal, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 980)));
        scheduleContainer.addView(vertical, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyState = Ui.text(this, "暂无课程，点击空白时段添加", 14, Ui.MUTED);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        emptyState.setBackground(Ui.rounded(Color.WHITE, 6, this));
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        emptyParams.topMargin = Ui.dp(this, 70);
        scheduleContainer.addView(emptyState, emptyParams);
        root.addView(scheduleContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(Ui.divider(this));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 7));
        actions.setGravity(Gravity.CENTER);
        Button add = Ui.textButton(this, "＋ 课程");
        add.setOnClickListener(v -> openNewCourse(1, 8 * 60));
        Button importButton = Ui.textButton(this, "⇩ 导入");
        importButton.setOnClickListener(v -> startActivity(new Intent(this, ImportActivity.class)));
        Button settingsButton = Ui.textButton(this, "设置");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        actions.addView(add, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        actions.addView(importButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        actions.addView(settingsButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        root.addView(actions);
        return root;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void changeWeek(int amount) {
        selectedWeek = Math.max(1, Math.min(60, selectedWeek + amount));
        refresh();
    }

    private void refresh() {
        LocalDate start = ScheduleTime.weekStart(settings.semesterStart(), selectedWeek);
        List<Course> courses = database.forWeek(selectedWeek);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);
        weekLabel.setText(String.format(Locale.CHINA, "第 %d 周  ·  %s—%s", selectedWeek,
                start.format(formatter), start.plusDays(6).format(formatter)));
        silentStatus.setText(settings.autoSilentEnabled() ? "自动静音 已开启" : "自动静音 未开启");
        silentStatus.setTextColor(settings.autoSilentEnabled() ? Ui.PRIMARY : Ui.MUTED);
        scheduleView.setData(courses, start);
        emptyState.setVisibility(courses.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onCourseClick(Course course) {
        Intent intent = new Intent(this, CourseEditorActivity.class);
        intent.putExtra(CourseEditorActivity.EXTRA_COURSE_ID, course.id);
        startActivity(intent);
    }

    @Override
    public void onEmptySlotClick(int dayOfWeek, int minute) {
        openNewCourse(dayOfWeek, minute);
    }

    private void openNewCourse(int day, int minute) {
        Intent intent = new Intent(this, CourseEditorActivity.class);
        intent.putExtra(CourseEditorActivity.EXTRA_DAY, day);
        intent.putExtra(CourseEditorActivity.EXTRA_START_MINUTE, minute);
        startActivity(intent);
    }
}
