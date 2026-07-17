package com.qianxi.schedule.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SafeBrowsingResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.qianxi.schedule.data.AppSettings;
import com.qianxi.schedule.data.Course;
import com.qianxi.schedule.data.CourseDatabase;
import com.qianxi.schedule.importer.ImportParser;
import com.qianxi.schedule.importer.ImportScript;
import com.qianxi.schedule.silence.AlarmScheduler;

import java.util.List;

public final class ImportActivity extends Activity {
    private static final String[] SYSTEMS = {"自动识别", "正方教务", "强智教务", "青果教务", "通用表格"};
    private AppSettings settings;
    private WebView webView;
    private EditText address;
    private Spinner system;
    private ProgressBar progress;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        setContentView(buildContent());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        Button back = Ui.textButton(this, "‹");
        back.setTextSize(28);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));
        TextView title = Ui.text(this, "从教务系统导入", 21, Ui.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        root.addView(toolbar);
        root.addView(Ui.divider(this));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        system = new Spinner(this);
        system.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SYSTEMS));
        system.setBackground(Ui.rounded(Color.WHITE, 6, this));
        controls.addView(system, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(14);
        address.setTextColor(Ui.INK);
        address.setHint("学校教务系统网址");
        address.setText(settings.schoolUrl());
        address.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        address.setBackground(Ui.rounded(Color.WHITE, 6, this));
        Button open = Ui.textButton(this, "打开");
        open.setOnClickListener(v -> openAddress());
        addressRow.addView(address, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));
        addressRow.addView(open, new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 46)));
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        addressParams.topMargin = Ui.dp(this, 8);
        controls.addView(addressRow, addressParams);
        root.addView(controls);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 2)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.INVISIBLE : View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                status.setText("正在载入教务页面…");
                address.setText(url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                status.setText("进入个人课表页面后，点击右侧导入按钮");
                address.setText(url);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
                Toast.makeText(ImportActivity.this, "已阻止非网页链接", Toast.LENGTH_SHORT).show();
                return true;
            }

            @SuppressLint("NewApi")
            @Override public void onSafeBrowsingHit(WebView view, WebResourceRequest request,
                                                     int threatType, SafeBrowsingResponse callback) {
                callback.backToSafety(true);
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(Ui.divider(this));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        status = Ui.text(this, settings.schoolUrl().isEmpty() ? "输入教务网址并登录" : "打开教务系统并登录", 12, Ui.MUTED);
        status.setMaxLines(2);
        bottom.addView(status, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        Button importButton = Ui.primaryButton(this, "导入当前课表");
        importButton.setOnClickListener(v -> scanPage());
        bottom.addView(importButton, new LinearLayout.LayoutParams(Ui.dp(this, 138), Ui.dp(this, 48)));
        root.addView(bottom);

        if (!settings.schoolUrl().isEmpty()) openAddress();
        return root;
    }

    private void openAddress() {
        String url = address.getText().toString().trim();
        if (url.isEmpty()) {
            address.setError("请输入教务系统网址");
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        if (url.startsWith("http://")) {
            final String cleartextUrl = url;
            new AlertDialog.Builder(this)
                    .setTitle("该教务网站未使用 HTTPS")
                    .setMessage("登录信息可能在网络中以明文传输。仅在确认这是学校官方地址且没有 HTTPS 入口时继续。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("仍然打开", (dialog, which) -> loadUrl(cleartextUrl))
                    .show();
            return;
        }
        loadUrl(url);
    }

    private void loadUrl(String url) {
        settings.setSchoolUrl(url);
        address.setText(url);
        webView.loadUrl(url);
    }

    private void scanPage() {
        if (webView.getUrl() == null) {
            Toast.makeText(this, "请先打开教务系统", Toast.LENGTH_SHORT).show();
            return;
        }
        status.setText("正在识别课程…");
        webView.evaluateJavascript(ImportScript.SCAN_PAGE, value -> {
            try {
                List<Course> courses = ImportParser.parseJavascriptResult(value,
                        SYSTEMS[system.getSelectedItemPosition()]);
                if (courses.isEmpty()) {
                    status.setText("当前页面没有识别到课程");
                    new AlertDialog.Builder(this)
                            .setTitle("未识别到课程")
                            .setMessage("请确认已经进入按周显示的个人课表页面，再重新导入。部分教务系统需先选择当前学期。")
                            .setPositiveButton("知道了", null)
                            .show();
                    return;
                }
                showImportPreview(courses);
            } catch (Exception exception) {
                status.setText("解析失败");
                new AlertDialog.Builder(this)
                        .setTitle("无法解析当前页面")
                        .setMessage("页面结构暂不受支持。可切换教务类型后重试。\n\n" + exception.getMessage())
                        .setPositiveButton("知道了", null)
                        .show();
            }
        });
    }

    private void showImportPreview(List<Course> courses) {
        StringBuilder preview = new StringBuilder();
        int limit = Math.min(6, courses.size());
        for (int i = 0; i < limit; i++) {
            Course course = courses.get(i);
            preview.append(course.name).append("  ·  周").append(chineseDay(course.dayOfWeek))
                    .append(' ').append(com.qianxi.schedule.data.ScheduleTime.formatMinutes(course.startMinute));
            if (!course.location.isEmpty()) preview.append("  ·  ").append(course.location);
            preview.append('\n');
        }
        if (courses.size() > limit) preview.append("另有 ").append(courses.size() - limit).append(" 条课程");
        new AlertDialog.Builder(this)
                .setTitle("识别到 " + courses.size() + " 条课程")
                .setMessage(preview.toString().trim())
                .setNegativeButton("取消", null)
                .setNeutralButton("合并", (dialog, which) -> commitImport(courses, false))
                .setPositiveButton("替换现有课表", (dialog, which) -> commitImport(courses, true))
                .show();
    }

    private void commitImport(List<Course> courses, boolean replace) {
        CourseDatabase.get(this).importCourses(courses, replace);
        AlarmScheduler.reschedule(this);
        status.setText(String.format(java.util.Locale.CHINA, "已导入 %d 条课程", courses.size()));
        Toast.makeText(this, "课表导入完成", Toast.LENGTH_SHORT).show();
    }

    private static String chineseDay(int day) {
        return new String[]{"一", "二", "三", "四", "五", "六", "日"}[Math.max(1, Math.min(7, day)) - 1];
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }
}
