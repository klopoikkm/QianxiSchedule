package com.qianxi.schedule.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.qianxi.schedule.data.AppSettings;
import com.qianxi.schedule.data.BackupManager;
import com.qianxi.schedule.data.Course;
import com.qianxi.schedule.data.CourseDatabase;
import com.qianxi.schedule.silence.AlarmScheduler;
import com.qianxi.schedule.silence.SilenceState;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class SettingsActivity extends Activity {
    private static final int REQUEST_EXPORT_BACKUP = 41;
    private static final int REQUEST_IMPORT_BACKUP = 42;
    private AppSettings settings;
    private Switch autoSilent;
    private TextView permissionStatus;
    private TextView semesterValue;
    private BackupManager.Backup pendingBackup;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        setContentView(buildContent());
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
        TextView title = Ui.text(this, "设置", 21, Ui.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        root.addView(toolbar);
        root.addView(Ui.divider(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        content.addView(Ui.sectionTitle(this, "上课模式"));
        LinearLayout silentRow = row();
        LinearLayout silentText = labels("上课自动静音", "课程重叠时会保持静音，最后一节结束后恢复原状态");
        silentRow.addView(silentText, new LinearLayout.LayoutParams(0, Ui.dp(this, 70), 1));
        autoSilent = new Switch(this);
        autoSilent.setChecked(settings.autoSilentEnabled());
        autoSilent.setContentDescription("开启上课自动静音");
        autoSilent.setOnCheckedChangeListener(this::onAutoSilentChanged);
        silentRow.addView(autoSilent, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 60)));
        content.addView(silentRow);

        permissionStatus = Ui.text(this, "", 13, Ui.MUTED);
        permissionStatus.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 8));
        content.addView(permissionStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout permissionActions = new LinearLayout(this);
        permissionActions.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 10));
        Button policy = Ui.textButton(this, "授予勿扰权限");
        policy.setOnClickListener(v -> requestPolicyAccess());
        Button exact = Ui.textButton(this, "授予精确闹钟权限");
        exact.setOnClickListener(v -> requestExactAlarmAccess());
        permissionActions.addView(policy, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        permissionActions.addView(exact, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        content.addView(permissionActions);
        content.addView(Ui.divider(this));

        content.addView(Ui.sectionTitle(this, "学期"));
        LinearLayout semesterRow = row();
        LinearLayout semesterText = labels("第一周开始日期", "选择第一周内任意日期，应用会自动对齐到周一");
        semesterRow.addView(semesterText, new LinearLayout.LayoutParams(0, Ui.dp(this, 70), 1));
        semesterValue = Ui.text(this, "", 14, Ui.PRIMARY);
        semesterValue.setGravity(Gravity.CENTER);
        semesterRow.addView(semesterValue, new LinearLayout.LayoutParams(Ui.dp(this, 110), Ui.dp(this, 60)));
        semesterRow.setOnClickListener(v -> pickSemesterDate());
        content.addView(semesterRow);
        content.addView(Ui.divider(this));

        content.addView(Ui.sectionTitle(this, "教务登录"));
        LinearLayout cookies = row();
        cookies.addView(labels("清除教务登录状态", "移除 WebView Cookie，下次导入时需重新登录"),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 70), 1));
        Button clear = Ui.textButton(this, "清除");
        clear.setOnClickListener(v -> {
            CookieManager.getInstance().removeAllCookies(value ->
                    Toast.makeText(this, "登录状态已清除", Toast.LENGTH_SHORT).show());
            CookieManager.getInstance().flush();
        });
        cookies.addView(clear, new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 52)));
        content.addView(cookies);
        content.addView(Ui.divider(this));

        content.addView(Ui.sectionTitle(this, "数据迁移"));
        LinearLayout backupActions = new LinearLayout(this);
        backupActions.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 10));
        Button export = Ui.textButton(this, "导出备份");
        export.setContentDescription("导出本地课表备份");
        export.setOnClickListener(v -> exportBackup());
        Button importBackup = Ui.textButton(this, "导入备份");
        importBackup.setContentDescription("导入本地课表备份");
        importBackup.setOnClickListener(v -> importBackup());
        backupActions.addView(export, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        backupActions.addView(importBackup, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        content.addView(backupActions);
        content.addView(Ui.divider(this));

        content.addView(Ui.sectionTitle(this, "关于"));
        LinearLayout about = labels("潜溪课表 1.2.1", "课程数据与教务页面只在本机处理 · MIT License");
        about.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        content.addView(about, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 70)));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 14), 0);
        row.setBackgroundColor(Color.WHITE);
        return row;
    }

    private LinearLayout labels(String title, String detail) {
        LinearLayout values = new LinearLayout(this);
        values.setOrientation(LinearLayout.VERTICAL);
        values.setGravity(Gravity.CENTER_VERTICAL);
        TextView primary = Ui.text(this, title, 15, Ui.INK);
        TextView secondary = Ui.text(this, detail, 12, Ui.MUTED);
        secondary.setMaxLines(2);
        values.addView(primary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));
        values.addView(secondary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 34)));
        return values;
    }

    private void onAutoSilentChanged(CompoundButton button, boolean enabled) {
        settings.setAutoSilentEnabled(enabled);
        if (enabled && !SilenceState.hasPolicyAccess(this)) requestPolicyAccess();
        else if (enabled && !AlarmScheduler.canScheduleExact(this)) requestExactAlarmAccess();
        AlarmScheduler.reschedule(this);
        updateStatus();
    }

    private void requestPolicyAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } catch (Exception exception) {
            Toast.makeText(this, "无法打开勿扰权限设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception exception) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void pickSemesterDate() {
        LocalDate date = settings.semesterStart();
        new DatePickerDialog(this, (view, year, month, day) -> {
            settings.setSemesterStart(LocalDate.of(year, month + 1, day));
            updateStatus();
            AlarmScheduler.reschedule(this);
        }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show();
    }

    private void exportBackup() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "qianxi-schedule-backup.json");
            startActivityForResult(intent, REQUEST_EXPORT_BACKUP);
        } catch (Exception exception) {
            Toast.makeText(this, "系统不支持导出文件", Toast.LENGTH_SHORT).show();
        }
    }

    private void importBackup() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/json");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQUEST_IMPORT_BACKUP);
        } catch (Exception exception) {
            Toast.makeText(this, "系统不支持导入文件", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_EXPORT_BACKUP) writeBackup(data.getData());
        else if (requestCode == REQUEST_IMPORT_BACKUP) readBackup(data.getData());
    }

    private void writeBackup(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new java.io.IOException("无法打开目标文件");
            String json = BackupManager.encode(settings, CourseDatabase.get(this).all());
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.flush();
            Toast.makeText(this, "备份已导出", Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(this, "导出失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void readBackup(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new java.io.IOException("无法读取备份文件");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(chunk)) != -1) {
                total += count;
                if (total > 2 * 1024 * 1024) throw new java.io.IOException("备份文件过大");
                buffer.write(chunk, 0, count);
            }
            pendingBackup = BackupManager.decode(buffer.toString(StandardCharsets.UTF_8.name()));
            String message = String.format(Locale.CHINA, "包含 %d 条课程和 %d 个教务入口。选择替换会同时恢复学期和静音设置。",
                    pendingBackup.courses.size(), pendingBackup.profiles.size());
            new AlertDialog.Builder(this)
                    .setTitle("导入本地备份")
                    .setMessage(message)
                    .setNegativeButton("取消", null)
                    .setNeutralButton("合并课程", (dialog, which) -> applyBackup(false))
                    .setPositiveButton("替换全部", (dialog, which) -> applyBackup(true))
                    .show();
        } catch (Exception exception) {
            pendingBackup = null;
            Toast.makeText(this, "备份无效：" + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void applyBackup(boolean replace) {
        if (pendingBackup == null) return;
        BackupManager.Backup backup = pendingBackup;
        pendingBackup = null;
        CourseDatabase.get(this).importCourses(backup.courses, replace);
        if (replace) {
            settings.setSemesterStart(backup.semesterStart);
            settings.setAutoSilentEnabled(backup.autoSilent);
            settings.setSchoolUrl(backup.schoolUrl);
            settings.setSelectedAdapterId(backup.adapterId);
            settings.replaceCustomSchoolProfiles(backup.profiles);
            settings.setSelectedSchoolProfileId(backup.selectedProfileId);
        }
        AlarmScheduler.reschedule(this);
        updateStatus();
        Toast.makeText(this, replace ? "备份已恢复" : "课程已合并", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        if (settings.autoSilentEnabled()) AlarmScheduler.reschedule(this);
    }

    private void updateStatus() {
        if (permissionStatus == null) return;
        boolean policy = SilenceState.hasPolicyAccess(this);
        boolean exact = AlarmScheduler.canScheduleExact(this);
        permissionStatus.setText(String.format(Locale.CHINA, "勿扰权限 %s   ·   精确闹钟 %s",
                policy ? "已授予" : "未授予", exact ? "已授予" : "未授予"));
        permissionStatus.setTextColor(policy && exact ? Ui.PRIMARY : Ui.ACCENT);
        semesterValue.setText(settings.semesterStart().format(
                DateTimeFormatter.ofPattern("yyyy.M.d", Locale.CHINA)));
    }
}
