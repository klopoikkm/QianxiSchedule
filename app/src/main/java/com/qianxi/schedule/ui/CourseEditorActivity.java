package com.qianxi.schedule.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qianxi.schedule.data.AppSettings;
import com.qianxi.schedule.data.Course;
import com.qianxi.schedule.data.CourseDatabase;
import com.qianxi.schedule.data.ScheduleTime;
import com.qianxi.schedule.silence.AlarmScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Course editor modelled on WakeUp's AddCourseActivity: one course (name/teacher/location/colour)
 * carries a list of time segments, each with a weekday, a start/end period (node) pair, and a set of
 * weeks. Every segment is stored as its own {@link Course} row sharing the common fields, so the
 * period grid and silence alarm keep working unchanged.
 */
public final class CourseEditorActivity extends Activity {
    public static final String EXTRA_COURSE_ID = "course_id";
    public static final String EXTRA_DAY = "day";
    public static final String EXTRA_START_MINUTE = "start_minute";
    private static final int[] COLORS = {
            0xFF087F5B, 0xFF1971C2, 0xFF6741D9, 0xFFC2255C,
            0xFFE8590C, 0xFF5F6F52, 0xFF0B7285, 0xFF495057
    };

    /** One editable time slot: weekday + node range + selected weeks. */
    private static final class Segment {
        long sourceId;          // original row id when editing, 0 for a fresh slot
        int dayOfWeek = 1;
        int startNode = 1;
        int endNode = 2;
        final boolean[] weeks = new boolean[60];

        Segment() {
            for (int i = 0; i < 20; i++) weeks[i] = true; // default 1-20
        }
    }

    private AppSettings settings;
    private List<AppSettings.ClassTime> classTimes;
    private final List<Segment> segments = new ArrayList<>();
    private final List<Long> originalIds = new ArrayList<>();
    private String editKeyName = "";
    private String editKeyTeacher = "";
    private String editKeyLocation = "";
    private boolean isEditing;
    private int color = COLORS[0];

    private EditText name;
    private EditText teacher;
    private EditText location;
    private LinearLayout segmentsContainer;
    private LinearLayout colorRow;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        classTimes = settings.classTimes();
        loadInitialState();
        View content = buildContent();
        Ui.applySystemBarInsets(content);
        setContentView(content);
        bindHeader();
        refreshSegments();
    }

    /** Seeds the editor from an existing course group, or a blank one at the tapped slot. */
    private void loadInitialState() {
        long id = getIntent().getLongExtra(EXTRA_COURSE_ID, 0);
        Course seed = id > 0 ? CourseDatabase.get(this).find(id) : null;
        if (seed != null) {
            isEditing = true;
            editKeyName = seed.name;
            editKeyTeacher = seed.teacher;
            editKeyLocation = seed.location;
            color = seed.color;
            List<Course> group = CourseDatabase.get(this)
                    .courseGroup(seed.name, seed.teacher, seed.location);
            if (group.isEmpty()) group.add(seed);
            for (Course c : group) {
                originalIds.add(c.id);
                segments.add(segmentOf(c));
            }
        } else {
            isEditing = false;
            Segment segment = new Segment();
            segment.dayOfWeek = getIntent().getIntExtra(EXTRA_DAY, 1);
            int startMinute = getIntent().getIntExtra(EXTRA_START_MINUTE, 8 * 60);
            int[] range = ScheduleTime.nodeRange(classTimes, startMinute, startMinute + 90);
            segment.startNode = range[0];
            segment.endNode = Math.max(range[0], range[1]);
            segments.add(segment);
        }
    }

    private Segment segmentOf(Course c) {
        Segment segment = new Segment();
        segment.sourceId = c.id;
        segment.dayOfWeek = c.dayOfWeek;
        if (c.startNode >= 1 && c.step >= 1) {
            segment.startNode = c.startNode;
            segment.endNode = c.endNode();
        } else {
            int[] range = ScheduleTime.nodeRange(classTimes, c.startMinute, c.endMinute);
            segment.startNode = range[0];
            segment.endNode = range[1];
        }
        for (int i = 0; i < 60; i++) segment.weeks[i] = false;
        if (c.weekMask != 0L) {
            for (int w = 1; w <= 60; w++) {
                if ((c.weekMask & (1L << (w - 1))) != 0) segment.weeks[w - 1] = true;
            }
        } else {
            for (int w = c.startWeek; w <= c.endWeek && w <= 60; w++) {
                if (c.parity == 1 && w % 2 == 0) continue;
                if (c.parity == 2 && w % 2 == 1) continue;
                if (w >= 1) segment.weeks[w - 1] = true;
            }
        }
        return segment;
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        Button back = Ui.textButton(this, "‹");
        back.setTextSize(26);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 44)));
        TextView title = Ui.text(this, isEditing ? "编辑课程" : "添加课程", 19, Ui.INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        Button save = Ui.textButton(this, "保存");
        save.setTextSize(15);
        save.setOnClickListener(v -> save());
        toolbar.addView(save, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 44)));
        root.addView(toolbar);
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 24));

        name = field("课程名称", "例如：高等数学", form);
        teacher = field("教师", "可选", form);
        location = field("地点", "可选", form);

        form.addView(Ui.sectionTitle(this, "上课时间"));
        segmentsContainer = new LinearLayout(this);
        segmentsContainer.setOrientation(LinearLayout.VERTICAL);
        form.addView(segmentsContainer);

        Button addSegment = Ui.textButton(this, "+ 添加时间段");
        addSegment.setOnClickListener(v -> {
            Segment segment = new Segment();
            if (!segments.isEmpty()) {
                Segment last = segments.get(segments.size() - 1);
                segment.dayOfWeek = last.dayOfWeek;
                System.arraycopy(last.weeks, 0, segment.weeks, 0, 60);
            }
            segments.add(segment);
            refreshSegments();
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        addParams.topMargin = Ui.dp(this, 6);
        form.addView(addSegment, addParams);

        form.addView(Ui.sectionTitle(this, "课程颜色"));
        colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        for (int value : COLORS) {
            View swatch = new View(this);
            swatch.setContentDescription("选择课程颜色");
            swatch.setTag(value);
            swatch.setOnClickListener(v -> {
                color = (int) v.getTag();
                updateColorSelection();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32));
            params.setMargins(Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 6));
            colorRow.addView(swatch, params);
        }
        form.addView(colorRow);

        if (isEditing) {
            Button delete = Ui.textButton(this, "删除整门课程");
            delete.setTextColor(Color.rgb(194, 37, 92));
            delete.setOnClickListener(v -> confirmDelete());
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
            deleteParams.topMargin = Ui.dp(this, 16);
            form.addView(delete, deleteParams);
        }

        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private EditText field(String label, String hint, LinearLayout parent) {
        parent.addView(Ui.sectionTitle(this, label));
        EditText edit = new EditText(this);
        edit.setTextSize(16);
        edit.setTextColor(Ui.INK);
        edit.setHintTextColor(Color.rgb(160, 164, 160));
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        edit.setBackground(Ui.rounded(Color.WHITE, 6, this));
        parent.addView(edit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
        return edit;
    }

    private void bindHeader() {
        name.setText(editKeyName);
        teacher.setText(editKeyTeacher);
        location.setText(editKeyLocation);
        updateColorSelection();
    }

    private void updateColorSelection() {
        for (int i = 0; i < colorRow.getChildCount(); i++) {
            View swatch = colorRow.getChildAt(i);
            int value = (int) swatch.getTag();
            android.graphics.drawable.GradientDrawable background = Ui.rounded(value, 16, this);
            if (value == color) background.setStroke(Ui.dp(this, 3), Color.WHITE);
            swatch.setBackground(background);
            swatch.setScaleX(value == color ? 1.12f : 1f);
            swatch.setScaleY(value == color ? 1.12f : 1f);
        }
    }

    private void refreshSegments() {
        segmentsContainer.removeAllViews();
        String[] dayNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int i = 0; i < segments.size(); i++) {
            final int index = i;
            Segment segment = segments.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.rounded(Color.WHITE, 8, this));
            card.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 10));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = Ui.dp(this, 8);

            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = Ui.text(this, "时间段 " + (i + 1), 13, Ui.MUTED);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            headerRow.addView(label, new LinearLayout.LayoutParams(0, Ui.dp(this, 30), 1));
            if (segments.size() > 1) {
                Button remove = Ui.textButton(this, "移除");
                remove.setTextColor(Color.rgb(194, 37, 92));
                remove.setTextSize(13);
                remove.setOnClickListener(v -> {
                    segments.remove(index);
                    refreshSegments();
                });
                headerRow.addView(remove, new LinearLayout.LayoutParams(
                        Ui.dp(this, 56), Ui.dp(this, 30)));
            }
            card.addView(headerRow);

            TextView timeValue = Ui.text(this, timeSummary(segment, dayNames), 15, Ui.INK);
            timeValue.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
            timeValue.setBackground(Ui.rounded(Color.rgb(240, 247, 255), 6, this));
            timeValue.setOnClickListener(v -> pickTime(index));
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            timeParams.topMargin = Ui.dp(this, 4);
            card.addView(timeValue, timeParams);

            TextView weekValue = Ui.text(this, weekSummary(segment), 14, Ui.PRIMARY);
            weekValue.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
            weekValue.setBackground(Ui.rounded(Color.rgb(240, 247, 255), 6, this));
            weekValue.setOnClickListener(v -> pickWeeks(index));
            LinearLayout.LayoutParams weekParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            weekParams.topMargin = Ui.dp(this, 6);
            card.addView(weekValue, weekParams);

            segmentsContainer.addView(card, cardParams);
        }
    }

    private String timeSummary(Segment segment, String[] dayNames) {
        int day = Math.max(1, Math.min(7, segment.dayOfWeek));
        int start = ScheduleTime.startMinuteForNode(classTimes, segment.startNode);
        int end = ScheduleTime.endMinuteForNode(classTimes, segment.endNode);
        return String.format(Locale.CHINA, "%s   第 %d–%d 节   %s–%s",
                dayNames[day - 1], segment.startNode, segment.endNode,
                ScheduleTime.formatMinutes(start), ScheduleTime.formatMinutes(end));
    }

    private String weekSummary(Segment segment) {
        List<int[]> ranges = new ArrayList<>();
        int runStart = -1;
        for (int w = 1; w <= 60; w++) {
            boolean on = segment.weeks[w - 1];
            if (on && runStart < 0) runStart = w;
            if ((!on || w == 60) && runStart >= 0) {
                int runEnd = on ? w : w - 1;
                ranges.add(new int[]{runStart, runEnd});
                runStart = -1;
            }
        }
        if (ranges.isEmpty()) return "未选择周次";
        StringBuilder builder = new StringBuilder();
        for (int[] r : ranges) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(r[0] == r[1] ? String.valueOf(r[0]) : r[0] + "-" + r[1]);
        }
        builder.append(" 周");
        return builder.toString();
    }

    /** Node-based day + start/end period picker, mirroring WakeUp's SelectTimeFragment. */
    private void pickTime(int index) {
        Segment segment = segments.get(index);
        int periods = Math.max(1, classTimes.size());
        String[] dayNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        String[] nodeNames = new String[periods];
        for (int i = 0; i < periods; i++) nodeNames[i] = "第 " + (i + 1) + " 节";

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        NumberPicker dayPicker = picker(dayNames, segment.dayOfWeek - 1);
        NumberPicker startPicker = picker(nodeNames, Math.min(periods, segment.startNode) - 1);
        NumberPicker endPicker = picker(nodeNames, Math.min(periods, segment.endNode) - 1);
        row.addView(dayPicker, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        row.addView(startPicker, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(endPicker, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        new AlertDialog.Builder(this)
                .setTitle("选择上课时间")
                .setView(row)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    segment.dayOfWeek = dayPicker.getValue() + 1;
                    int start = startPicker.getValue() + 1;
                    int end = endPicker.getValue() + 1;
                    if (end < start) end = start;
                    segment.startNode = start;
                    segment.endNode = end;
                    refreshSegments();
                })
                .show();
    }

    private NumberPicker picker(String[] values, int selected) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(0);
        picker.setMaxValue(values.length - 1);
        picker.setDisplayedValues(values);
        picker.setWrapSelectorWheel(false);
        picker.setValue(Math.max(0, Math.min(values.length - 1, selected)));
        return picker;
    }

    /** Week grid with 全选/单周/双周 shortcuts, mirroring WakeUp's SelectWeekFragment. */
    private void pickWeeks(int index) {
        Segment segment = segments.get(index);
        final boolean[] working = new boolean[60];
        System.arraycopy(segment.weeks, 0, working, 0, 60);
        int maxWeek = 20;

        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));

        LinearLayout shortcuts = new LinearLayout(this);
        shortcuts.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout[] gridHolder = new LinearLayout[1];
        Button all = Ui.textButton(this, "全选");
        Button odd = Ui.textButton(this, "单周");
        Button even = Ui.textButton(this, "双周");
        all.setOnClickListener(v -> {
            for (int i = 0; i < maxWeek; i++) working[i] = true;
            rebuildWeekGrid(gridHolder[0], working, maxWeek);
        });
        odd.setOnClickListener(v -> {
            for (int i = 0; i < 60; i++) working[i] = (i % 2 == 0) && i < maxWeek;
            rebuildWeekGrid(gridHolder[0], working, maxWeek);
        });
        even.setOnClickListener(v -> {
            for (int i = 0; i < 60; i++) working[i] = (i % 2 == 1) && i < maxWeek;
            rebuildWeekGrid(gridHolder[0], working, maxWeek);
        });
        shortcuts.addView(all, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        shortcuts.addView(odd, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        shortcuts.addView(even, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        container.addView(shortcuts);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        gridHolder[0] = grid;
        rebuildWeekGrid(grid, working, maxWeek);
        container.addView(grid);
        scroll.addView(container);

        new AlertDialog.Builder(this)
                .setTitle("选择周次")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    System.arraycopy(working, 0, segment.weeks, 0, 60);
                    refreshSegments();
                })
                .show();
    }

    private void rebuildWeekGrid(LinearLayout grid, boolean[] working, int maxWeek) {
        grid.removeAllViews();
        int perRow = 6;
        LinearLayout currentRow = null;
        for (int w = 1; w <= maxWeek; w++) {
            if ((w - 1) % perRow == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = Ui.dp(this, 4);
                grid.addView(currentRow, rowParams);
            }
            final int week = w;
            boolean on = working[w - 1];
            TextView cell = new TextView(this);
            cell.setText(String.valueOf(w));
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(14);
            cell.setTextColor(on ? Color.WHITE : Ui.INK);
            cell.setBackground(Ui.rounded(on ? Ui.PRIMARY : Color.rgb(238, 240, 238), 18, this));
            cell.setOnClickListener(v -> {
                working[week - 1] = !working[week - 1];
                rebuildWeekGrid(grid, working, maxWeek);
            });
            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1);
            cellParams.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            currentRow.addView(cell, cellParams);
        }
    }

    private void save() {
        String courseName = name.getText().toString().trim();
        if (courseName.isEmpty()) {
            name.setError("请输入课程名称");
            return;
        }
        String courseTeacher = teacher.getText().toString().trim();
        String courseLocation = location.getText().toString().trim();

        List<Course> built = new ArrayList<>();
        for (Segment segment : segments) {
            long mask = 0L;
            for (int w = 1; w <= 60; w++) {
                if (segment.weeks[w - 1]) mask |= 1L << (w - 1);
            }
            if (mask == 0L) {
                Toast.makeText(this, "每个时间段至少选择一周", Toast.LENGTH_SHORT).show();
                return;
            }
            int start = Math.min(segment.startNode, segment.endNode);
            int end = Math.max(segment.startNode, segment.endNode);
            Course course = new Course();
            course.name = courseName;
            course.teacher = courseTeacher;
            course.location = courseLocation;
            course.color = color;
            course.dayOfWeek = Math.max(1, Math.min(7, segment.dayOfWeek));
            course.startNode = start;
            course.step = end - start + 1;
            course.startMinute = ScheduleTime.startMinuteForNode(classTimes, start);
            course.endMinute = ScheduleTime.endMinuteForNode(classTimes, end);
            course.weekMask = mask;
            course.startWeek = Long.numberOfTrailingZeros(mask) + 1;
            course.endWeek = 64 - Long.numberOfLeadingZeros(mask);
            course.parity = 0;
            built.add(course);
        }

        List<Course> conflicts = externalConflicts(built);
        if (!conflicts.isEmpty()) {
            showConflictWarning(built, conflicts);
            return;
        }
        persist(built);
    }

    /** Conflicts with courses outside this group (the group's own rows are being replaced). */
    private List<Course> externalConflicts(List<Course> built) {
        CourseDatabase database = CourseDatabase.get(this);
        List<Course> conflicts = new ArrayList<>();
        java.util.Set<Long> exclude = new java.util.HashSet<>(originalIds);
        for (Course candidate : built) {
            for (Course other : database.conflictsExcluding(candidate, exclude)) {
                if (!conflicts.contains(other)) conflicts.add(other);
            }
        }
        return conflicts;
    }

    private void showConflictWarning(List<Course> built, List<Course> conflicts) {
        StringBuilder message = new StringBuilder("该课程与以下安排时间重叠：\n\n");
        int limit = Math.min(3, conflicts.size());
        for (int i = 0; i < limit; i++) {
            Course conflict = conflicts.get(i);
            message.append("- ").append(conflict.name).append("  ")
                    .append(ScheduleTime.formatMinutes(conflict.startMinute)).append("—")
                    .append(ScheduleTime.formatMinutes(conflict.endMinute)).append('\n');
        }
        if (conflicts.size() > limit) {
            message.append("另有 ").append(conflicts.size() - limit).append(" 条冲突\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("发现课程冲突")
                .setMessage(message.toString().trim())
                .setNegativeButton("返回修改", null)
                .setPositiveButton("仍然保存", (dialog, which) -> persist(built))
                .show();
    }

    private void persist(List<Course> built) {
        CourseDatabase.get(this).replaceGroup(originalIds, built);
        AlarmScheduler.reschedule(this);
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("删除课程")
                .setMessage("确定删除“" + editKeyName + "”的全部时间段吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    CourseDatabase.get(this).replaceGroup(originalIds, new ArrayList<>());
                    AlarmScheduler.reschedule(this);
                    finish();
                })
                .show();
    }
}
