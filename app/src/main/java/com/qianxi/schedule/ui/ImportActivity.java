package com.qianxi.schedule.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SafeBrowsingResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.qianxi.schedule.data.AppSettings;
import com.qianxi.schedule.data.Course;
import com.qianxi.schedule.data.CourseDatabase;
import com.qianxi.schedule.importer.ImportAdapter;
import com.qianxi.schedule.importer.ImportParser;
import com.qianxi.schedule.importer.ImportScript;
import com.qianxi.schedule.importer.SchoolProfile;
import com.qianxi.schedule.silence.AlarmScheduler;

import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ImportActivity extends Activity {
    private static final int MAX_POLL_ATTEMPTS = 80;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<SchoolProfile> profiles = new ArrayList<>();
    private AppSettings settings;
    private WebView webView;
    private EditText address;
    private Spinner profileSpinner;
    private Spinner adapterSpinner;
    private ArrayAdapter<SchoolProfile> profileAdapter;
    private ProgressBar progress;
    private TextView status;
    private Button importButton;
    private boolean applyingProfile;
    private int scanGeneration;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        setContentView(buildContent());
        reloadProfiles(settings.selectedSchoolProfileId());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        Button back = Ui.textButton(this, "‹");
        back.setTextSize(28);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 44)));
        TextView title = Ui.text(this, "教务导入", 20, Ui.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        Button reload = Ui.textButton(this, "↻");
        reload.setTextSize(23);
        reload.setContentDescription("刷新页面");
        reload.setOnClickListener(v -> webView.reload());
        toolbar.addView(reload, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 44)));
        root.addView(toolbar);
        root.addView(Ui.divider(this));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));

        profileSpinner = new Spinner(this);
        profileAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, profiles);
        profileSpinner.setAdapter(profileAdapter);
        profileSpinner.setBackground(Ui.rounded(Color.WHITE, 6, this));
        profileSpinner.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!applyingProfile && position >= 0 && position < profiles.size()) {
                    applyProfile(profiles.get(position));
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        controls.addView(profileSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        LinearLayout adapterRow = new LinearLayout(this);
        adapterRow.setGravity(Gravity.CENTER_VERTICAL);
        adapterSpinner = new Spinner(this);
        adapterSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, ImportAdapter.labels()));
        adapterSpinner.setBackground(Ui.rounded(Color.WHITE, 6, this));
        adapterSpinner.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        adapterSpinner.setSelection(ImportAdapter.indexOf(settings.selectedAdapterId()));
        adapterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!applyingProfile) settings.setSelectedAdapterId(ImportAdapter.idAt(position));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        Button manage = Ui.textButton(this, "管理入口");
        manage.setOnClickListener(this::showProfileMenu);
        adapterRow.addView(adapterSpinner, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        adapterRow.addView(manage, new LinearLayout.LayoutParams(Ui.dp(this, 88), Ui.dp(this, 44)));
        LinearLayout.LayoutParams adapterParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        adapterParams.topMargin = Ui.dp(this, 6);
        controls.addView(adapterRow, adapterParams);

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(14);
        address.setTextColor(Ui.INK);
        address.setHint("https://学校教务网址/");
        address.setText(settings.schoolUrl());
        address.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        address.setBackground(Ui.rounded(Color.WHITE, 6, this));
        Button open = Ui.textButton(this, "打开");
        open.setOnClickListener(v -> openAddress());
        addressRow.addView(address, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        addressRow.addView(open, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 44)));
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        addressParams.topMargin = Ui.dp(this, 6);
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
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webView.getSettings().setUserAgentString(
                webView.getSettings().getUserAgentString() + " QianxiSchedule/1.2");
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
                scanGeneration++;
                setImporting(false);
                status.setText("正在载入教务页面…");
                address.setText(url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                String selected = ImportAdapter.idAt(adapterSpinner.getSelectedItemPosition());
                String resolved = ImportAdapter.resolve(selected, url);
                status.setText(String.format(Locale.CHINA, "已就绪 · %s", ImportAdapter.labelOf(resolved)));
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
        bottom.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 10), Ui.dp(this, 7));
        status = Ui.text(this, "选择入口并登录教务系统", 12, Ui.MUTED);
        status.setMaxLines(2);
        bottom.addView(status, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));
        importButton = Ui.primaryButton(this, "拉取课表");
        importButton.setOnClickListener(v -> scanPage());
        bottom.addView(importButton, new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 46)));
        root.addView(bottom);
        return root;
    }

    private void reloadProfiles(String selectedId) {
        profiles.clear();
        profiles.add(SchoolProfile.customEntry());
        profiles.add(SchoolProfile.northeasternUniversity());
        profiles.addAll(settings.customSchoolProfiles());
        profileAdapter.notifyDataSetChanged();
        int selected = 0;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(selectedId)) selected = i;
        }
        applyingProfile = true;
        profileSpinner.setSelection(selected);
        applyingProfile = false;
        applyProfile(profiles.get(selected));
    }

    private void applyProfile(SchoolProfile profile) {
        applyingProfile = true;
        settings.setSelectedSchoolProfileId(profile.id);
        if (!SchoolProfile.CUSTOM_ENTRY_ID.equals(profile.id)) {
            address.setText(profile.url);
            adapterSpinner.setSelection(ImportAdapter.indexOf(profile.adapterId));
            settings.setSchoolUrl(profile.url);
            settings.setSelectedAdapterId(profile.adapterId);
        } else {
            address.setText(settings.schoolUrl());
            adapterSpinner.setSelection(ImportAdapter.indexOf(settings.selectedAdapterId()));
        }
        applyingProfile = false;
    }

    private void showProfileMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("保存当前入口");
        SchoolProfile selected = selectedProfile();
        if (selected != null && selected.custom) menu.getMenu().add("删除当前入口");
        menu.setOnMenuItemClickListener(item -> {
            if ("保存当前入口".contentEquals(item.getTitle())) saveCurrentProfile();
            else deleteCurrentProfile();
            return true;
        });
        menu.show();
    }

    private void saveCurrentProfile() {
        String url = normalizedAddress();
        if (url == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("例如：学校本科教务");
        SchoolProfile current = selectedProfile();
        if (current != null && current.custom) input.setText(current.name);
        input.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        new AlertDialog.Builder(this)
                .setTitle("保存教务入口")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = Uri.parse(url).getHost();
                    SchoolProfile saved = settings.saveCustomSchoolProfile(name, url,
                            ImportAdapter.idAt(adapterSpinner.getSelectedItemPosition()));
                    reloadProfiles(saved.id);
                    Toast.makeText(this, "入口已保存", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void deleteCurrentProfile() {
        SchoolProfile profile = selectedProfile();
        if (profile == null || !profile.custom) return;
        new AlertDialog.Builder(this)
                .setTitle("删除入口")
                .setMessage("确定删除“" + profile.name + "”吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    settings.deleteCustomSchoolProfile(profile.id);
                    reloadProfiles(SchoolProfile.CUSTOM_ENTRY_ID);
                })
                .show();
    }

    private SchoolProfile selectedProfile() {
        int position = profileSpinner.getSelectedItemPosition();
        return position >= 0 && position < profiles.size() ? profiles.get(position) : null;
    }

    private void openAddress() {
        String url = normalizedAddress();
        if (url == null) return;
        if (url.startsWith("http://")) {
            new AlertDialog.Builder(this)
                    .setTitle("该教务网站未使用 HTTPS")
                    .setMessage("登录信息可能以明文传输。仅在确认这是学校官方地址且没有 HTTPS 入口时继续。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("仍然打开", (dialog, which) -> loadUrl(url))
                    .show();
            return;
        }
        loadUrl(url);
    }

    private String normalizedAddress() {
        String url = address.getText().toString().trim();
        if (url.isEmpty()) {
            address.setError("请输入教务系统网址");
            return null;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        Uri uri = Uri.parse(url);
        if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            address.setError("网址格式不正确");
            return null;
        }
        return url;
    }

    private void loadUrl(String url) {
        settings.setSchoolUrl(url);
        settings.setSelectedAdapterId(ImportAdapter.idAt(adapterSpinner.getSelectedItemPosition()));
        address.setText(url);
        webView.loadUrl(url);
    }

    private void scanPage() {
        String url = webView.getUrl();
        if (url == null) {
            Toast.makeText(this, "请先打开并登录教务系统", Toast.LENGTH_SHORT).show();
            return;
        }
        String selected = ImportAdapter.idAt(adapterSpinner.getSelectedItemPosition());
        String adapter = ImportAdapter.resolve(selected, url);
        int generation = ++scanGeneration;
        setImporting(true);
        status.setText(String.format(Locale.CHINA, "正在通过%s拉取…", ImportAdapter.labelOf(adapter)));
        webView.evaluateJavascript(ImportScript.forAdapter(adapter), ignored ->
                handler.postDelayed(() -> pollImport(generation, adapter, 0), 200));
    }

    private void pollImport(int generation, String adapter, int attempt) {
        if (generation != scanGeneration || webView == null) return;
        if (attempt >= MAX_POLL_ATTEMPTS) {
            setImporting(false);
            showImportError("拉取超时，请检查网络和登录状态后重试");
            return;
        }
        webView.evaluateJavascript("window.__qianxiImportState || ''", value -> {
            String state = decodeJavascriptString(value);
            if ("done".equals(state)) {
                readImportPayload(generation, adapter);
            } else if ("error".equals(state)) {
                webView.evaluateJavascript("window.__qianxiImportError || ''", error -> {
                    setImporting(false);
                    showImportError(decodeJavascriptString(error));
                });
            } else {
                handler.postDelayed(() -> pollImport(generation, adapter, attempt + 1), 250);
            }
        });
    }

    private void readImportPayload(int generation, String adapter) {
        if (generation != scanGeneration) return;
        webView.evaluateJavascript("JSON.stringify(window.__qianxiImportPayload || null)", value -> {
            if (generation != scanGeneration) return;
            setImporting(false);
            try {
                ImportParser.ImportOutcome outcome = ImportParser.parseOutcome(value, adapter);
                if (outcome.courses.isEmpty()) {
                    showImportError("当前页面没有识别到课程。请确认已进入当前学期的个人课表页面。");
                    return;
                }
                showImportPreview(outcome);
            } catch (Exception exception) {
                showImportError("页面数据解析失败：" + exception.getMessage());
            }
        });
    }

    private static String decodeJavascriptString(String value) {
        try {
            Object decoded = new JSONTokener(value).nextValue();
            return decoded == null ? "" : decoded.toString();
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }

    private void showImportError(String message) {
        status.setText("拉取失败");
        new AlertDialog.Builder(this)
                .setTitle("无法导入课表")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showImportPreview(ImportParser.ImportOutcome outcome) {
        StringBuilder preview = new StringBuilder();
        preview.append(ImportAdapter.labelOf(outcome.adapterId));
        if (!outcome.term.isEmpty()) preview.append("  ·  ").append(outcome.term);
        if (!outcome.campus.isEmpty()) preview.append("\n").append(outcome.campus);
        preview.append("\n\n");
        int limit = Math.min(6, outcome.courses.size());
        for (int i = 0; i < limit; i++) {
            Course course = outcome.courses.get(i);
            preview.append(course.name).append("  ·  周").append(chineseDay(course.dayOfWeek))
                    .append(' ').append(com.qianxi.schedule.data.ScheduleTime.formatMinutes(course.startMinute));
            if (!course.location.isEmpty()) preview.append("  ·  ").append(course.location);
            preview.append('\n');
        }
        if (outcome.courses.size() > limit) {
            preview.append("另有 ").append(outcome.courses.size() - limit).append(" 条课程\n");
        }
        if (outcome.skippedItems > 0) {
            preview.append("已忽略 ").append(outcome.skippedItems).append(" 条无效记录");
        }
        new AlertDialog.Builder(this)
                .setTitle(String.format(Locale.CHINA, "识别到 %d 条课程", outcome.courses.size()))
                .setMessage(preview.toString().trim())
                .setNegativeButton("取消", null)
                .setNeutralButton("合并", (dialog, which) -> commitImport(outcome.courses, false))
                .setPositiveButton("替换现有课表", (dialog, which) -> commitImport(outcome.courses, true))
                .show();
    }

    private void commitImport(List<Course> courses, boolean replace) {
        CourseDatabase.get(this).importCourses(courses, replace);
        AlarmScheduler.reschedule(this);
        status.setText(String.format(Locale.CHINA, "已导入 %d 条课程", courses.size()));
        Toast.makeText(this, "课表导入完成", Toast.LENGTH_SHORT).show();
    }

    private void setImporting(boolean importing) {
        if (importButton != null) {
            importButton.setEnabled(!importing);
            importButton.setText(importing ? "拉取中…" : "拉取课表");
            importButton.setAlpha(importing ? 0.65f : 1f);
        }
    }

    private static String chineseDay(int day) {
        return new String[]{"一", "二", "三", "四", "五", "六", "日"}
                [Math.max(1, Math.min(7, day)) - 1];
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        scanGeneration++;
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
