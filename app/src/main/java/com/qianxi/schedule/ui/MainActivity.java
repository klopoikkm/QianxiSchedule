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
    private ScheduleHeaderView scheduleHeader;
    private ScheduleView scheduleView;
    private TextView weekLabel;
    private TextView silentStatus;
    private TextView emptyState;
    private TextView nextSummary;
    private Course summaryCourse;
    private int selectedWeek;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        database = CourseDatabase.get(this);
        selectedWeek = ScheduleTime.weekOf(settings.semesterStart(), LocalDate.now());
        View content = buildContent();
        Ui.applySystemBarInsets(content);
        setContentView(content);
        AlarmScheduler.ensureDailyRefresh(this);
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 5));
        TextView title = Ui.text(this, "潜溪课表", 22, Ui.INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        silentStatus = Ui.text(this, "", 12, Ui.PRIMARY);
        silentStatus.setGravity(Gravity.CENTER);
        silentStatus.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 5));
        silentStatus.setBackground(Ui.rounded(Color.rgb(229, 245, 238), 6, this));
        silentStatus.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        brand.addView(silentStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(brand);

        LinearLayout weekBar = new LinearLayout(this);
        weekBar.setGravity(Gravity.CENTER_VERTICAL);
        weekBar.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), Ui.dp(this, 5));
        Button previous = Ui.textButton(this, "‹");
        previous.setTextSize(28);
        previous.setContentDescription("上一周");
        previous.setOnClickListener(v -> changeWeek(-1));
        weekBar.addView(previous, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 44)));

        weekLabel = Ui.text(this, "", 13, Ui.INK);
        weekLabel.setGravity(Gravity.CENTER);
        weekLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        weekLabel.setMaxLines(2);
        weekBar.addView(weekLabel, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));

        Button today = Ui.textButton(this, "本周");
        today.setOnClickListener(v -> {
            selectedWeek = ScheduleTime.weekOf(settings.semesterStart(), LocalDate.now());
            refresh();
        });
        weekBar.addView(today, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 44)));
        Button next = Ui.textButton(this, "›");
        next.setTextSize(28);
        next.setContentDescription("下一周");
        next.setOnClickListener(v -> changeWeek(1));
        weekBar.addView(next, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 44)));
        root.addView(weekBar);

        nextSummary = Ui.text(this, "", 13, Ui.INK);
        nextSummary.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        nextSummary.setMaxLines(2);
        nextSummary.setBackground(Ui.rounded(Color.WHITE, 8, this));
        nextSummary.setOnClickListener(v -> {
            if (summaryCourse != null) onCourseClick(summaryCourse);
        });
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54));
        summaryParams.setMargins(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 8));
        root.addView(nextSummary, summaryParams);
        root.addView(Ui.divider(this));

        FrameLayout scheduleContainer = new FrameLayout(this);
        LinearLayout scheduleColumn = new LinearLayout(this);
        scheduleColumn.setOrientation(LinearLayout.VERTICAL);
        scheduleHeader = new ScheduleHeaderView(this);
        scheduleColumn.addView(scheduleHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        ScrollView vertical = new ScrollView(this);
        vertical.setFillViewport(false);
        vertical.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scheduleView = new ScheduleView(this);
        scheduleView.setListener(this);
        vertical.addView(scheduleView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 712)));
        scheduleColumn.addView(vertical, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        scheduleContainer.addView(scheduleColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyState = Ui.text(this, "暂无课程，点击空白时段添加", 14, Ui.MUTED);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        emptyState.setBackground(Ui.rounded(Color.WHITE, 6, this));
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        emptyParams.topMargin = Ui.dp(this, 58);
        scheduleContainer.addView(emptyState, emptyParams);

        TextView addCourse = Ui.text(this, "+", 30, Color.WHITE);
        addCourse.setGravity(Gravity.CENTER);
        addCourse.setContentDescription("添加课程");
        addCourse.setBackground(Ui.circle(Ui.ACCENT));
        addCourse.setElevation(Ui.dp(this, 8));
        addCourse.setOnClickListener(v -> openNewCourse(1, 8 * 60));
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(
                Ui.dp(this, 52), Ui.dp(this, 52), Gravity.END | Gravity.BOTTOM);
        addParams.setMargins(0, 0, Ui.dp(this, 14), Ui.dp(this, 14));
        scheduleContainer.addView(addCourse, addParams);
        root.addView(scheduleContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(Ui.divider(this));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 7));
        actions.setGravity(Gravity.CENTER);
        Button importButton = Ui.textButton(this, "导入课表");
        importButton.setOnClickListener(v -> startActivity(new Intent(this, ImportActivity.class)));
        Button settingsButton = Ui.textButton(this, "设置");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M.d", Locale.CHINA);
        weekLabel.setText(String.format(Locale.CHINA, "第 %d 周\n%s—%s", selectedWeek,
                start.format(formatter), start.plusDays(6).format(formatter)));
        silentStatus.setText(settings.autoSilentEnabled() ? "静音 已开" : "静音 未开");
        silentStatus.setTextColor(settings.autoSilentEnabled() ? Ui.PRIMARY : Ui.MUTED);
        scheduleHeader.setWeekStart(start);
        scheduleView.setData(courses, start);
        emptyState.setVisibility(courses.isEmpty() ? View.VISIBLE : View.GONE);
        updateSummary(courses, start);
    }

    private void updateSummary(List<Course> courses, LocalDate weekStart) {
        summaryCourse = null;
        if (courses.isEmpty()) {
            nextSummary.setText("本周暂无课程 · 点击空白时段即可添加");
            nextSummary.setTextColor(Ui.MUTED);
            nextSummary.setClickable(false);
            return;
        }

        LocalDate today = LocalDate.now();
        int currentWeek = ScheduleTime.weekOf(settings.semesterStart(), today);
        int now = java.time.LocalTime.now().getHour() * 60 + java.time.LocalTime.now().getMinute();
        Course ongoing = null;
        Course next = null;
        if (selectedWeek == currentWeek && !today.isBefore(weekStart) && !today.isAfter(weekStart.plusDays(6))) {
            int todayIndex = today.getDayOfWeek().getValue();
            for (Course course : courses) {
                if (course.dayOfWeek != todayIndex) continue;
                if (course.startMinute <= now && now < course.endMinute) {
                    ongoing = course;
                    break;
                }
                if (course.startMinute > now && next == null) next = course;
            }
        }
        if (ongoing != null) {
            summaryCourse = ongoing;
            nextSummary.setText(String.format(Locale.CHINA, "正在上课 · 周%s %s\n%s%s",
                    chineseDay(ongoing.dayOfWeek), ScheduleTime.formatMinutes(ongoing.startMinute),
                    ongoing.name, ongoing.location.isEmpty() ? "" : " · " + ongoing.location));
            nextSummary.setTextColor(Ui.PRIMARY);
        } else {
            if (next == null && selectedWeek == currentWeek) {
                int todayIndex = today.getDayOfWeek().getValue();
                for (Course course : courses) {
                    if (course.dayOfWeek > todayIndex) {
                        next = course;
                        break;
                    }
                }
            }
            if (next == null) next = courses.get(0);
            summaryCourse = next;
            LocalDate date = weekStart.plusDays(next.dayOfWeek - 1L);
            nextSummary.setText(String.format(Locale.CHINA, "下一节 · 周%s %s %s\n%s%s",
                    chineseDay(next.dayOfWeek), date.getMonthValue() + "." + date.getDayOfMonth(),
                    ScheduleTime.formatMinutes(next.startMinute), next.name,
                    next.location.isEmpty() ? "" : " · " + next.location));
            nextSummary.setTextColor(Ui.INK);
        }
        nextSummary.setClickable(true);
    }

    private static String chineseDay(int day) {
        return new String[]{"一", "二", "三", "四", "五", "六", "日"}
                [Math.max(1, Math.min(7, day)) - 1];
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
