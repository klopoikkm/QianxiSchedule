package com.qianxi.schedule.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
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
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.qianxi.schedule.data.Course;
import com.qianxi.schedule.data.CourseDatabase;
import com.qianxi.schedule.data.ScheduleTime;
import com.qianxi.schedule.silence.AlarmScheduler;

import java.util.List;

public final class CourseEditorActivity extends Activity {
    public static final String EXTRA_COURSE_ID = "course_id";
    public static final String EXTRA_DAY = "day";
    public static final String EXTRA_START_MINUTE = "start_minute";
    private static final int[] COLORS = {
            0xFF087F5B, 0xFF1971C2, 0xFF6741D9, 0xFFC2255C,
            0xFFE8590C, 0xFF5F6F52, 0xFF0B7285, 0xFF495057
    };

    private Course course;
    private EditText name;
    private EditText teacher;
    private EditText location;
    private EditText startWeek;
    private EditText endWeek;
    private Spinner day;
    private Spinner parity;
    private Button startTime;
    private Button endTime;
    private LinearLayout colorRow;
    private long originalWeekMask;
    private int originalStartWeek;
    private int originalEndWeek;
    private int originalParity;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        long id = getIntent().getLongExtra(EXTRA_COURSE_ID, 0);
        course = id > 0 ? CourseDatabase.get(this).find(id) : null;
        if (course == null) {
            course = new Course();
            course.dayOfWeek = getIntent().getIntExtra(EXTRA_DAY, 1);
            course.startMinute = getIntent().getIntExtra(EXTRA_START_MINUTE, 8 * 60);
            course.endMinute = Math.min(22 * 60, course.startMinute + 100);
        }
        originalWeekMask = course.weekMask;
        originalStartWeek = course.startWeek;
        originalEndWeek = course.endWeek;
        originalParity = course.parity;
        setContentView(buildContent());
        bindCourse();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        Button back = Ui.textButton(this, "‹");
        back.setTextSize(28);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));
        TextView title = Ui.text(this, course.id > 0 ? "编辑课程" : "添加课程", 21, Ui.INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        root.addView(toolbar);
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 28));

        name = field("课程名称", "例如：高等数学", form);
        teacher = field("教师", "可选", form);
        location = field("地点", "可选", form);

        form.addView(Ui.sectionTitle(this, "上课安排"));
        day = spinner(new String[]{"周一", "周二", "周三", "周四", "周五", "周六", "周日"});
        addLabeledRow(form, "星期", day);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        startTime = Ui.textButton(this, "08:00");
        endTime = Ui.textButton(this, "09:40");
        startTime.setBackground(Ui.rounded(Color.WHITE, 6, this));
        endTime.setBackground(Ui.rounded(Color.WHITE, 6, this));
        startTime.setOnClickListener(v -> pickTime(true));
        endTime.setOnClickListener(v -> pickTime(false));
        timeRow.addView(startTime, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        TextView dash = Ui.text(this, "—", 16, Ui.MUTED);
        dash.setGravity(Gravity.CENTER);
        timeRow.addView(dash, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 48)));
        timeRow.addView(endTime, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        addLabeledRow(form, "时间", timeRow);

        LinearLayout weeks = new LinearLayout(this);
        weeks.setOrientation(LinearLayout.HORIZONTAL);
        startWeek = numberField();
        endWeek = numberField();
        weeks.addView(startWeek, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        TextView weekDash = Ui.text(this, "至", 14, Ui.MUTED);
        weekDash.setGravity(Gravity.CENTER);
        weeks.addView(weekDash, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 48)));
        weeks.addView(endWeek, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        addLabeledRow(form, "周次", weeks);
        parity = spinner(new String[]{"每周", "仅单周", "仅双周"});
        addLabeledRow(form, "重复", parity);

        form.addView(Ui.sectionTitle(this, "课程颜色"));
        colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        for (int value : COLORS) {
            View swatch = new View(this);
            swatch.setContentDescription("选择课程颜色");
            swatch.setTag(value);
            swatch.setBackground(Ui.rounded(value, 18, this));
            swatch.setOnClickListener(v -> {
                course.color = (int) v.getTag();
                updateColorSelection();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
            params.setMargins(Ui.dp(this, 3), Ui.dp(this, 4), Ui.dp(this, 7), Ui.dp(this, 8));
            colorRow.addView(swatch, params);
        }
        form.addView(colorRow);

        Button save = Ui.primaryButton(this, "保存课程");
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        saveParams.topMargin = Ui.dp(this, 24);
        form.addView(save, saveParams);

        if (course.id > 0) {
            Button delete = Ui.textButton(this, "删除课程");
            delete.setTextColor(Color.rgb(194, 37, 92));
            delete.setOnClickListener(v -> confirmDelete());
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
            deleteParams.topMargin = Ui.dp(this, 8);
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
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));
        return edit;
    }

    private EditText numberField() {
        EditText edit = new EditText(this);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setGravity(Gravity.CENTER);
        edit.setTextColor(Ui.INK);
        edit.setBackground(Ui.rounded(Color.WHITE, 6, this));
        return edit;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
        spinner.setBackground(Ui.rounded(Color.WHITE, 6, this));
        spinner.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        return spinner;
    }

    private void addLabeledRow(LinearLayout form, String label, View value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = Ui.text(this, label, 14, Ui.MUTED);
        row.addView(text, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 56)));
        row.addView(value, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        form.addView(row);
    }

    private void bindCourse() {
        name.setText(course.name);
        teacher.setText(course.teacher);
        location.setText(course.location);
        day.setSelection(Math.max(0, course.dayOfWeek - 1));
        startWeek.setText(String.valueOf(course.startWeek));
        endWeek.setText(String.valueOf(course.endWeek));
        parity.setSelection(course.parity);
        updateTimeLabels();
        updateColorSelection();
    }

    private void pickTime(boolean start) {
        int value = start ? course.startMinute : course.endMinute;
        new TimePickerDialog(this, (view, hour, minute) -> {
            if (start) course.startMinute = hour * 60 + minute;
            else course.endMinute = hour * 60 + minute;
            updateTimeLabels();
        }, value / 60, value % 60, true).show();
    }

    private void updateTimeLabels() {
        startTime.setText(ScheduleTime.formatMinutes(course.startMinute));
        endTime.setText(ScheduleTime.formatMinutes(course.endMinute));
    }

    private void updateColorSelection() {
        for (int i = 0; i < colorRow.getChildCount(); i++) {
            View swatch = colorRow.getChildAt(i);
            int value = (int) swatch.getTag();
            android.graphics.drawable.GradientDrawable background = Ui.rounded(value, 18, this);
            if (value == course.color) background.setStroke(Ui.dp(this, 3), Color.WHITE);
            swatch.setBackground(background);
            swatch.setScaleX(value == course.color ? 1.12f : 1f);
            swatch.setScaleY(value == course.color ? 1.12f : 1f);
        }
    }

    private void save() {
        String courseName = name.getText().toString().trim();
        if (courseName.isEmpty()) {
            name.setError("请输入课程名称");
            return;
        }
        int firstWeek;
        int lastWeek;
        try {
            firstWeek = Integer.parseInt(startWeek.getText().toString());
            lastWeek = Integer.parseInt(endWeek.getText().toString());
        } catch (NumberFormatException exception) {
            Toast.makeText(this, "请输入有效周次", Toast.LENGTH_SHORT).show();
            return;
        }
        if (firstWeek < 1 || lastWeek < firstWeek || lastWeek > 60) {
            Toast.makeText(this, "周次范围应为 1—60", Toast.LENGTH_SHORT).show();
            return;
        }
        if (course.endMinute <= course.startMinute) {
            Toast.makeText(this, "下课时间必须晚于上课时间", Toast.LENGTH_SHORT).show();
            return;
        }
        course.name = courseName;
        course.teacher = teacher.getText().toString().trim();
        course.location = location.getText().toString().trim();
        course.dayOfWeek = day.getSelectedItemPosition() + 1;
        int selectedParity = parity.getSelectedItemPosition();
        boolean weekPatternChanged = firstWeek != originalStartWeek || lastWeek != originalEndWeek
                || selectedParity != originalParity;
        course.startWeek = firstWeek;
        course.endWeek = lastWeek;
        course.parity = selectedParity;
        course.weekMask = weekPatternChanged ? 0L : originalWeekMask;
        List<Course> conflicts = CourseDatabase.get(this).conflicts(course);
        if (!conflicts.isEmpty()) {
            showConflictWarning(conflicts);
            return;
        }
        persistCourse();
    }

    private void showConflictWarning(List<Course> conflicts) {
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
                .setPositiveButton("仍然保存", (dialog, which) -> persistCourse())
                .show();
    }

    private void persistCourse() {
        CourseDatabase.get(this).save(course);
        AlarmScheduler.reschedule(this);
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("删除课程")
                .setMessage("确定删除“" + course.name + "”吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    CourseDatabase.get(this).delete(course.id);
                    AlarmScheduler.reschedule(this);
                    finish();
                })
                .show();
    }
}
