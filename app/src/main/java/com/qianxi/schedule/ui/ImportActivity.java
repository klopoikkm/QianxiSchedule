package com.qianxi.schedule.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SafeBrowsingResponse;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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
    private static final int MAX_POLL_ATTEMPTS = 160;
    private static final String DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<SchoolProfile> profiles = new ArrayList<>();
    private final List<WebView> browserStack = new ArrayList<>();
    private AppSettings settings;
    private WebView webView;
    private FrameLayout webContainer;
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
        View content = buildContent();
        Ui.applySystemBarInsets(content);
        setContentView(content);
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
        back.setOnClickListener(v -> navigateBack());
        toolbar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 44)));
        TextView title = Ui.text(this, "教务导入", 20, Ui.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        Button reload = Ui.textButton(this, "↻");
        reload.setTextSize(23);
        reload.setContentDescription("刷新页面");
        reload.setOnClickListener(v -> {
            if (webView != null) webView.reload();
        });
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
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setTextSize(14);
        address.setTextColor(Ui.INK);
        address.setHint("https://学校教务网址/");
        address.setText(settings.schoolUrl());
        address.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        address.setBackground(Ui.rounded(Color.WHITE, 6, this));
        address.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                openAddress();
                return true;
            }
            return false;
        });
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

        webContainer = new FrameLayout(this);
        webView = createWebView();
        webView.clearCache(true);
        browserStack.add(webView);
        webContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(Ui.divider(this));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 10), Ui.dp(this, 7));
        status = Ui.text(this, "输入网址，进入个人课表后拉取", 12, Ui.MUTED);
        status.setMaxLines(2);
        bottom.addView(status, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));
        importButton = Ui.primaryButton(this, "拉取课表");
        importButton.setOnClickListener(v -> scanPage());
        bottom.addView(importButton, new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 46)));
        root.addView(bottom);
        return root;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        WebView view = new WebView(this);
        view.setBackgroundColor(Color.WHITE);
        WebSettings webSettings = view.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setBlockNetworkLoads(false);
        webSettings.setBlockNetworkImage(false);
        webSettings.setTextZoom(100);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setUserAgentString(DESKTOP_USER_AGENT);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(view, true);
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.INVISIBLE : View.VISIBLE);
            }

            @Override public boolean onCreateWindow(WebView source, boolean isDialog,
                                                     boolean isUserGesture, Message resultMsg) {
                if (!(resultMsg.obj instanceof WebView.WebViewTransport) || webContainer == null) {
                    return false;
                }
                WebView popup = createWebView();
                browserStack.add(popup);
                webContainer.addView(popup, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                setActiveWebView(popup);
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }

            @Override public void onCloseWindow(WebView window) {
                closeWebView(window);
            }
        });
        view.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                setActiveWebView(view);
                applyCompatibility(view, url);
                scanGeneration++;
                setImporting(false);
                status.setText("正在载入教务页面…");
                address.setText(url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                String selected = ImportAdapter.idAt(adapterSpinner.getSelectedItemPosition());
                String resolved = ImportAdapter.resolve(selected, url);
                status.setText(String.format(Locale.CHINA, "已就绪 · %s", ImportAdapter.labelOf(resolved)));
                address.setText(url);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(view, request.getUrl().toString());
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(view, url);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                   WebResourceError error) {
                if (request.isForMainFrame()) {
                    showPageLoadError(view, request.getUrl().toString(),
                            webErrorMessage(error.getErrorCode(), error.getDescription()));
                }
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                       WebResourceResponse errorResponse) {
                if (request.isForMainFrame()) {
                    showPageLoadError(view, request.getUrl().toString(), String.format(Locale.CHINA,
                            "服务器返回 HTTP %d。请确认网址和登录状态。",
                            errorResponse.getStatusCode()));
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                                     SslError error) {
                AlertDialog dialog = new AlertDialog.Builder(ImportActivity.this)
                        .setTitle("网站证书验证失败")
                        .setMessage("无法验证该教务网站的 HTTPS 证书。仅在确认网址属于学校且证书问题是学校已知故障时继续。\n\n"
                                + error.getUrl())
                        .setNegativeButton("取消", (ignored, which) -> handler.cancel())
                        .setPositiveButton("仅本次继续", (ignored, which) -> handler.proceed())
                        .create();
                dialog.setOnCancelListener(ignored -> handler.cancel());
                dialog.show();
            }

            @Override public boolean onRenderProcessGone(WebView view,
                                                          RenderProcessGoneDetail detail) {
                recoverWebView(view, detail.didCrash());
                return true;
            }

            @SuppressLint("NewApi")
            @Override public void onSafeBrowsingHit(WebView view, WebResourceRequest request,
                                                     int threatType, SafeBrowsingResponse callback) {
                callback.backToSafety(true);
            }
        });
        return view;
    }

    private void reloadProfiles(String selectedId) {
        profiles.clear();
        profiles.add(SchoolProfile.customEntry());
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
        applyCompatibility(webView, url);
        webView.loadUrl(url);
    }

    private boolean handleNavigation(WebView view, String url) {
        if (url == null || url.trim().isEmpty()) return true;
        String scheme = Uri.parse(url).getScheme();
        if (scheme == null) return false;
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            applyCompatibility(view, url);
            return false;
        }
        // Legacy EAMS menus commonly use javascript: links; about/data/blob are also used by
        // authenticated pages for in-page navigation and generated timetable documents.
        if ("javascript".equalsIgnoreCase(scheme) || "about".equalsIgnoreCase(scheme)
                || "data".equalsIgnoreCase(scheme) || "blob".equalsIgnoreCase(scheme)) {
            return false;
        }
        if ("file".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme)) {
            Toast.makeText(this, "已阻止本地文件链接", Toast.LENGTH_SHORT).show();
            return true;
        }
        try {
            Intent intent = "intent".equalsIgnoreCase(scheme)
                    ? Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            intent.setSelector(null);
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, "没有应用可以打开该认证链接", Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(this, "认证链接无效", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void applyCompatibility(WebView view, String url) {
        if (view == null) return;
        view.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }

    private String webErrorMessage(int errorCode, CharSequence description) {
        switch (errorCode) {
            case WebViewClient.ERROR_TIMEOUT:
                return "连接超时。该教务站点可能仅限校园网或学校 VPN，也可能暂时停机。";
            case WebViewClient.ERROR_HOST_LOOKUP:
                return "无法解析网站域名。请检查网址、网络或学校 VPN。";
            case WebViewClient.ERROR_CONNECT:
                return "无法连接服务器。请确认站点是否要求校园网或学校 VPN。";
            case WebViewClient.ERROR_FAILED_SSL_HANDSHAKE:
                return "HTTPS 握手失败，网站证书或 TLS 配置可能不兼容。";
            case WebViewClient.ERROR_AUTHENTICATION:
                return "网站身份认证失败，请刷新后重新登录。";
            default:
                String detail = description == null ? "未知网络错误" : description.toString();
                return "网页加载失败：" + detail;
        }
    }

    private void showPageLoadError(WebView view, String url, String message) {
        if (isFinishing() || isDestroyed()) return;
        status.setText("网页无法打开");
        String engine = webViewEngine();
        new AlertDialog.Builder(this)
                .setTitle("网页无法打开")
                .setMessage(message + "\n\n网址：" + url + "\n浏览器内核：" + engine)
                .setNegativeButton("关闭", null)
                .setPositiveButton("重试", (ignored, which) -> {
                    if (view != null) view.reload();
                })
                .show();
    }

    private String webViewEngine() {
        PackageInfo info = WebView.getCurrentWebViewPackage();
        if (info == null) return "系统 WebView 不可用";
        return info.packageName + " " + info.versionName;
    }

    private void recoverWebView(WebView failedView, boolean crashed) {
        int index = browserStack.indexOf(failedView);
        if (index < 0) return;
        browserStack.remove(index);
        if (webContainer != null) webContainer.removeView(failedView);
        failedView.destroy();
        if (browserStack.isEmpty()) {
            WebView replacement = createWebView();
            browserStack.add(replacement);
            webContainer.addView(replacement, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        setActiveWebView(browserStack.get(browserStack.size() - 1));
        status.setText(crashed ? "浏览器内核已恢复，请重新打开网址" : "浏览器已重新载入");
    }

    private static boolean isLegacyEamsUrl(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        String path = uri.getPath();
        return (host != null && ("jwxt.neuq.edu.cn".equalsIgnoreCase(host)
                || host.toLowerCase(Locale.ROOT).endsWith(".jwxt.neuq.edu.cn")))
                || (path != null && path.toLowerCase(Locale.ROOT).contains("/eams/"));
    }

    private void setActiveWebView(WebView view) {
        if (view == null || !browserStack.contains(view)) return;
        webView = view;
        view.bringToFront();
    }

    private void closeWebView(WebView view) {
        if (view == null || browserStack.size() <= 1 || !browserStack.remove(view)) return;
        if (webContainer != null) webContainer.removeView(view);
        view.stopLoading();
        view.destroy();
        setActiveWebView(browserStack.get(browserStack.size() - 1));
        String url = webView.getUrl();
        if (url != null) address.setText(url);
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
                    showImportError(emptyImportMessage(outcome));
                    return;
                }
                showImportPreview(outcome);
            } catch (Exception exception) {
                showImportError("页面数据解析失败：" + exception.getMessage());
            }
        });
    }

    private String emptyImportMessage(ImportParser.ImportOutcome outcome) {
        StringBuilder message = new StringBuilder();
        message.append("当前页面没有识别到有效课程。请确认已进入当前学期的个人周课表页面。");
        message.append("\n\n识别器：").append(ImportAdapter.labelOf(outcome.adapterId));
        if (!outcome.pageTitle.isEmpty()) message.append("\n页面：").append(outcome.pageTitle);
        String host = Uri.parse(outcome.sourceUrl).getHost();
        if (host != null && !host.isEmpty()) message.append("\n站点：").append(host);
        message.append(String.format(Locale.CHINA,
                "\n诊断：%d 个表格 · %d 个内嵌页面 · %d 条候选 · %d 条无效",
                outcome.tables, outcome.frames, outcome.candidates, outcome.skippedItems));
        if (outcome.tables == 0 && outcome.candidates == 0) {
            message.append("\n\n该页面可能使用了尚未适配的画布或接口数据，请尝试切换到网页版课表。");
        }
        return message.toString();
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
        int conflicts = countExistingConflicts(outcome.courses);
        if (conflicts > 0) {
            if (preview.length() > 0 && preview.charAt(preview.length() - 1) != '\n') preview.append('\n');
            preview.append("与现有课表冲突 ").append(conflicts).append(" 条；选择替换可移除旧安排");
        }
        new AlertDialog.Builder(this)
                .setTitle(String.format(Locale.CHINA, "识别到 %d 条课程", outcome.courses.size()))
                .setMessage(preview.toString().trim())
                .setNegativeButton("取消", null)
                .setNeutralButton("合并", (dialog, which) -> commitImport(outcome.courses, false))
                .setPositiveButton("替换现有课表", (dialog, which) -> commitImport(outcome.courses, true))
                .show();
    }

    private int countExistingConflicts(List<Course> courses) {
        int conflicts = 0;
        CourseDatabase database = CourseDatabase.get(this);
        for (Course course : courses) {
            if (!database.conflicts(course).isEmpty()) conflicts++;
        }
        return conflicts;
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

    private void navigateBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else if (webView != null && browserStack.size() > 1) {
            closeWebView(webView);
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        navigateBack();
    }

    @Override
    protected void onDestroy() {
        scanGeneration++;
        handler.removeCallbacksAndMessages(null);
        for (WebView view : new ArrayList<>(browserStack)) {
            view.stopLoading();
            view.removeAllViews();
            view.destroy();
        }
        browserStack.clear();
        webView = null;
        super.onDestroy();
    }
}
