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
import com.qianxi.schedule.importer.AcademicWebPolicy;
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
    // Same-origin virtual path answered by the interceptor with the bundled jQuery. Injected
    // into EAMS main documents whose HTML carries no jQuery reference of its own.
    private static final String LOCAL_JQUERY_PATH = "/__qianxi__/jquery-1.7.2.min.js";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<SchoolProfile> profiles = new ArrayList<>();
    private final List<WebView> browserStack = new ArrayList<>();
    // Diagnostics: WebVPN can silently fail to load CSS/JS/AJAX subresources, leaving a bare
    // HTML skeleton. The WebView surfaces those failures only through onReceived*Error for
    // subframes and the JS console, both of which we record here so the user can report them.
    private static final int MAX_DIAGNOSTICS = 200;
    private final List<String> diagnostics = new ArrayList<>();
    // The WebVPN proxy base (https://vpn.neuq.edu.cn/http/<prefix>) for the page currently being
    // shown, used to re-route raw internal resource links that the proxy failed to rewrite.
    private volatile String webVpnBase;
    // URL of the page currently loading/shown, mirrored here on the UI thread because
    // shouldInterceptRequest runs on a background thread where calling any WebView method
    // (including getUrl()) throws and crashes the process.
    private volatile String currentPageUrl;
    // Scripts we have already served in this session. EAMS pages bundle multiple libraries into
    // one URL (jquery-form,jquery-history,jquery-colorbox,jquery-chosen.js), and every sub-page
    // references it again. History.js throws "Adapter has already been loaded" on the second
    // fetch and breaks all AJAX navigation. Cache script bodies in memory and return 304 for
    // subsequent requests so the browser reuses its cached copy without re-executing the script.
    private final java.util.Map<String, byte[]> servedScripts =
            new java.util.concurrent.ConcurrentHashMap<>();
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
        Button diagnose = Ui.textButton(this, "诊断");
        diagnose.setTextSize(15);
        diagnose.setContentDescription("查看加载诊断");
        diagnose.setOnClickListener(v -> showDiagnostics());
        toolbar.addView(diagnose, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 44)));
        Button reload = Ui.textButton(this, "↻");
        reload.setTextSize(23);
        reload.setContentDescription("刷新页面");
        reload.setOnClickListener(v -> {
            if (webView != null) webView.reload();
        });
        // Long-press the refresh button to inspect why a page rendered incompletely (WebVPN
        // subresource/AJAX failures and JS console errors are collected here).
        reload.setOnLongClickListener(v -> {
            showDiagnostics();
            return true;
        });
        toolbar.addView(reload, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 44)));
        root.addView(toolbar);
        root.addView(Ui.divider(this));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));

        // Profile + adapter selection are kept as off-screen state holders only: the visible UI is
        // now just the address bar, and the adapter is resolved automatically from the URL via
        // ImportAdapter.resolve(...) when the user taps 拉取课表. Removing the spinners from the
        // view tree — but keeping the objects — lets the profile-save / URL-persist code paths keep
        // working without confusing the user with a "选择教务" dropdown that never needed picking.
        profileSpinner = new Spinner(this);
        profileAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, profiles);
        profileSpinner.setAdapter(profileAdapter);

        adapterSpinner = new Spinner(this);
        adapterSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, ImportAdapter.labels()));
        adapterSpinner.setSelection(ImportAdapter.indexOf(settings.selectedAdapterId()));

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
        controls.addView(addressRow, addressParams);
        root.addView(controls);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 2)));

        webContainer = new FrameLayout(this);
        webView = createWebView();
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
        // The 1.12.0 redirect loop could poison the HTTP cache with truncated/failed script
        // responses; cached entries are then served without ever consulting the network or the
        // interceptor, keeping the portal broken forever. Start each import session clean.
        view.clearCache(true);
        WebSettings webSettings = view.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setSupportMultipleWindows(false);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(AcademicWebPolicy.loadWithOverview(settings.schoolUrl()));
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setBlockNetworkLoads(false);
        webSettings.setBlockNetworkImage(false);
        webSettings.setTextZoom(100);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setUserAgentString(AcademicWebPolicy.userAgent(settings.schoolUrl()));
        applyWebRenderProfile(view, settings.schoolUrl());

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(view, true);
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.INVISIBLE : View.VISIBLE);
            }

            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
                if (message != null && message.messageLevel() != null
                        && message.messageLevel() != android.webkit.ConsoleMessage.MessageLevel.LOG
                        && message.messageLevel() != android.webkit.ConsoleMessage.MessageLevel.TIP
                        && message.messageLevel() != android.webkit.ConsoleMessage.MessageLevel.DEBUG) {
                    String src = message.sourceId() == null ? "" : shorten(message.sourceId());
                    recordDiagnostic("JS " + message.messageLevel() + " @" + message.lineNumber()
                            + " " + src + ": " + message.message());
                }
                return super.onConsoleMessage(message);
            }
        });
        view.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                setActiveWebView(view);
                applyWebRenderProfile(view, url);
                currentPageUrl = url;
                String base = AcademicWebPolicy.parseWebVpnBase(url);
                if (base != null) webVpnBase = base;
                scanGeneration++;
                setImporting(false);
                status.setText("正在载入教务页面…");
                address.setText(url);
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view,
                                                                        WebResourceRequest request) {
                // Runs on a WebView background thread: an uncaught exception here kills the whole
                // process ("crash on load"). The interceptor is best-effort — on any failure fall
                // back to letting the WebView load the resource normally.
                try {
                    return interceptWebVpnResource(request);
                } catch (Throwable t) {
                    recordDiagnostic("INTERCEPT-CRASH " + t.getClass().getSimpleName()
                            + ": " + t.getMessage());
                    return null;
                }
            }

            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                // The responsive EAMS portal collapses its sidebar at phone width, hiding every
                // menu item. Forcing a desktop-width layout viewport reflows it back to the full
                // sidebar layout. Login/CAS pages keep their native narrow layout.
                if (AcademicWebPolicy.forceDesktopViewport(url)) {
                    view.evaluateJavascript(AcademicWebPolicy.FORCE_DESKTOP_VIEWPORT_JS, null);
                }
                ensureJQueryPresent(view, url);
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
                // Record every failed request, not just the main frame: on WebVPN the main page
                // loads fine but its stylesheets/scripts/AJAX often fail, and those are exactly
                // the failures that leave the bare-skeleton render seen on the portal.
                recordDiagnostic("LOAD " + error.getErrorCode() + " " + error.getDescription()
                        + (request.isForMainFrame() ? " [main] " : " [sub] ")
                        + shorten(request.getUrl().toString()));
                if (request.isForMainFrame()) {
                    showPageLoadError(view, request.getUrl().toString(),
                            webErrorMessage(error.getErrorCode(), error.getDescription()));
                }
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                       WebResourceResponse errorResponse) {
                recordDiagnostic("HTTP " + errorResponse.getStatusCode()
                        + (request.isForMainFrame() ? " [main] " : " [sub] ")
                        + shorten(request.getUrl().toString()));
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
        // Clear script cache on navigation: each page gets a fresh copy of bundled libraries.
        // History.js throws on re-execution within a single page session, but separate navigations
        // should start clean (the WebView itself discards JS state across navigations).
        servedScripts.clear();
        // Mirror before loading: shouldInterceptRequest can fire for the main resource before
        // onPageStarted updates the mirror, and it must never call webView.getUrl() itself.
        currentPageUrl = url;
        applyWebRenderProfile(webView, url);
        webView.loadUrl(url);
    }

    private boolean handleNavigation(WebView view, String url) {
        if (url == null || url.trim().isEmpty()) return true;
        String scheme = Uri.parse(url).getScheme();
        if (scheme == null) return false;
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            applyWebRenderProfile(view, url);
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

    /**
     * The WebVPN proxy fails to rewrite some absolute internal links (jQuery, CSS, avatar/AJAX
     * endpoints), so the WebView requests them straight from http://jwxt.neuq.edu.cn/... without
     * the WebVPN session and they fail — leaving the bare skeleton. When we detect such a request
     * while on a WebVPN page, we re-fetch it through the proxy base (which carries the session
     * cookie) and hand the bytes back to the WebView. Returns null to let normal loading proceed.
     */
    private WebResourceResponse interceptWebVpnResource(WebResourceRequest request) {
        if (request == null) return null;
        // Only rewrite plain GETs; POSTs and others must reach the proxy through the page itself.
        String method = request.getMethod();
        if (method != null && !method.equalsIgnoreCase("GET")) return null;
        String base = webVpnBase;
        String original = request.getUrl() == null ? null : request.getUrl().toString();
        // The virtual path referenced by our main-document HTML injection.
        if (original != null && original.contains(LOCAL_JQUERY_PATH)) {
            try {
                recordDiagnostic("LOCAL-JQUERY-VIRTUAL " + shorten(original));
                return new WebResourceResponse("text/javascript", "UTF-8",
                        getAssets().open("jquery-1.7.2.min.js"));
            } catch (Exception ignored) {
                recordDiagnostic("LOCAL-JQUERY-FAIL " + ignored.getClass().getSimpleName());
                return null;
            }
        }
        // EAMS main documents: fetch ourselves so jQuery can be injected into the HTML before
        // the WebView parses it (post-load injection is too late — inline scripts already ran).
        // Never intercept CAS ticket-bearing URLs: the ticket is single-use, and consuming it
        // in our fetch while the WebView retries the same URL would break the login handshake.
        if (request.isForMainFrame() && original != null && isAcademicHost(original)
                && !AcademicWebPolicy.useMobileAuthenticationLayout(original)
                && !original.contains("ticket=")) {
            return interceptMainDocument(original);
        }
        // Trace every script request: earlier diagnostics showed page JS failing with
        // "jQuery is not defined" while no jQuery request ever appeared in the log, which
        // makes it impossible to tell whether the request was never sent (memory-cached bad
        // response) or sent but mis-handled. This line settles that question.
        if (original != null && original.toLowerCase(Locale.ROOT).contains(".js")
                && isAcademicHost(original)) {
            recordDiagnostic("JS-REQ " + shorten(original));
        }
        // Some legacy EAMS pages reference jQuery with an absolute HTTP URL. On Android this
        // request can be rejected before WebView reports a useful network error, so the page's
        // inline bootstrap runs with `jQuery` undefined. jQuery 1.7.2 is MIT-licensed and keeps
        // the APIs used by these older portals; serve it locally for jQuery script requests.
        // Provide the local fallback for:
        // 1. WebVPN pages (where the proxy may have failed to rewrite the script link)
        // 2. Same-origin jQuery requests (old EAMS deployments serving jQuery from direct URLs)
        // 3. Any jQuery request: if the page is from any EAMS/NEUQ domain, jQuery is essential
        if (isJQueryResource(original) && (webVpnBase != null || isSamePageHost(original) || isAcademicHost(original))) {
            try {
                recordDiagnostic("LOCAL-JQUERY " + shorten(original));
                // Use text/javascript for better compatibility with older pages
                return new WebResourceResponse("text/javascript", "UTF-8",
                        getAssets().open("jquery-1.7.2.min.js"));
            } catch (Exception ignored) {
                recordDiagnostic("LOCAL-JQUERY-FAIL " + ignored.getClass().getSimpleName());
            }
        }
        String proxied = AcademicWebPolicy.rewriteThroughWebVpn(base, original);
        // Legacy EAMS servers gate their static scripts/CSS and endpoints like avatar/my.action
        // on the session cookie and Referer header. WebView sometimes drops or mangles these on
        // plain-HTTP subresource requests (diagnostics showed HTTP 403 on avatar and scripts
        // silently failing, leaving "jQuery is not defined"/"bg is not defined" and a bare
        // skeleton). Refetch every academic-host subresource through URLConnection with the
        // WebView cookie, Referer and a stable UA. Main-frame documents are left to the WebView
        // so navigation, redirects and history keep working.
        if (proxied == null && original != null && !request.isForMainFrame()
                && isAcademicHost(original)) {
            proxied = original;
        }
        if (proxied == null) return null;
        // NOTE: this method runs on a WebView background thread. Touching any WebView method
        // here (view.getUrl() etc.) throws "All WebView methods must be called on the same
        // thread" and kills the process, so only the mirrored currentPageUrl may be used.
        String pageUrl = currentPageUrl;
        try {
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) new java.net.URL(proxied).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", AcademicWebPolicy.userAgent(
                    pageUrl == null ? proxied : pageUrl));
            conn.setRequestProperty("Accept-Encoding", "identity");
            if (pageUrl != null) {
                conn.setRequestProperty("Referer", pageUrl);
            }
            String cookie = CookieManager.getInstance().getCookie(proxied);
            if (cookie != null) conn.setRequestProperty("Cookie", cookie);
            java.util.Map<String, String> headers = request.getRequestHeaders();
            if (headers != null) {
                String accept = headers.get("Accept");
                if (accept != null) conn.setRequestProperty("Accept", accept);
                String referer = headers.get("Referer");
                if (referer != null) conn.setRequestProperty("Referer", referer);
            }
            conn.connect();
            int code = conn.getResponseCode();
            // EAMS refreshes its session via Set-Cookie on subresource responses; feed them back
            // to the WebView's cookie store or the session silently dies. CookieManager is
            // thread-safe, so this is fine on the interceptor thread.
            java.util.List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
            if (setCookies != null) {
                for (String value : setCookies) {
                    if (value != null) CookieManager.getInstance().setCookie(proxied, value);
                }
            }
            if (code < 200 || code >= 300) {
                recordDiagnostic("REWRITE-SKIP " + code + " " + shorten(proxied));
                conn.disconnect();
                return null;
            }
            java.io.InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) stream = new java.io.ByteArrayInputStream(new byte[0]);
            String contentType = conn.getContentType();
            String mime = contentType;
            // Legacy EAMS serves GBK-encoded text without always declaring it. Only pass a
            // charset through when the server explicitly sent one; otherwise let the WebView
            // sniff the encoding itself (a wrong forced utf-8 corrupts GBK scripts and pages).
            String charset = null;
            if (contentType != null) {
                int semi = contentType.indexOf(';');
                if (semi >= 0) {
                    mime = contentType.substring(0, semi).trim();
                    int cs = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
                    if (cs >= 0) charset = contentType.substring(cs + 8).trim();
                }
            }
            if (mime == null || mime.isEmpty()) mime = guessMime(proxied);
            // Cache script bodies and return 304 for subsequent requests. EAMS bundles libraries
            // (jquery-form,jquery-history,...) into one URL, and every subpage re-references it.
            // History.js throws "Adapter has already been loaded" on the second fetch, breaking
            // all AJAX navigation. The browser's cache should handle this, but WebView sometimes
            // bypasses it when the interceptor is active, so we cache ourselves and return 304.
            // Key by normalized URL: strip cache-busting params like `?_=<timestamp>` so distinct
            // requests for the same logical script hit the cache.
            boolean isScript = mime != null && mime.contains("javascript");
            if (isScript) {
                String cacheKey = normalizeScriptUrl(proxied);
                byte[] cached = servedScripts.get(cacheKey);
                if (cached != null) {
                    recordDiagnostic("SCRIPT-CACHED-304 " + shorten(proxied));
                    WebResourceResponse resp = new WebResourceResponse(mime, charset,
                            new java.io.ByteArrayInputStream(new byte[0]));
                    try {
                        resp.setStatusCodeAndReasonPhrase(304, "Not Modified");
                    } catch (Exception ignored) {}
                    conn.disconnect();
                    return resp;
                }
                // First time: read, cache, and serve.
                byte[] body = readAll(stream);
                servedScripts.put(cacheKey, body);
                stream = new java.io.ByteArrayInputStream(body);
            }
            recordDiagnostic("REWRITE " + code + " " + mime + " " + shorten(proxied));
            WebResourceResponse response = new WebResourceResponse(mime, charset, stream);
            try {
                response.setStatusCodeAndReasonPhrase(code == 0 ? 200 : code,
                        reason(conn.getResponseMessage(), code));
                java.util.Map<String, String> out = new java.util.HashMap<>();
                out.put("Access-Control-Allow-Origin", "*");
                response.setResponseHeaders(out);
            } catch (Exception ignored) {
                // Older WebView may reject some status codes; the stream is still usable.
            }
            return response;
        } catch (Exception e) {
            recordDiagnostic("REWRITE-FAIL " + e.getClass().getSimpleName() + " " + shorten(proxied));
            return null;
        }
    }

    private static boolean isJQueryResource(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        int slash = lower.lastIndexOf('/');
        String file = slash >= 0 ? lower.substring(slash + 1) : lower;
        // CAS/EAMS propagate the session by URL rewriting, appending a ';JSESSIONID_AUTH=...'
        // or ';jsessionid=...' path parameter to every script src. Strip it, or the filename
        // never matches and the local jQuery fallback silently stops applying.
        int semi = file.indexOf(';');
        if (semi >= 0) file = file.substring(0, semi);
        // Do not replace jquery-ui, jquery.form, validation plugins, etc. with the core library.
        // Only the common core names (jquery.js, jquery.min.js, jquery-1.4.2.min.js, ...) match.
        return file.matches("jquery(?:[-_.][0-9]+(?:\\.[0-9]+)*)?(?:\\.min)?\\.js");
    }

    /**
     * Normalize a script URL for deduplication: strip cache-busting query params like `?_=timestamp`
     * and `&_=...`. EAMS appends `&_=<millis>` to every script request to defeat HTTP caching, but
     * that breaks our dedup cache (every request has a unique URL). Keep semantic params like
     * `?bg=3.4.3` that actually change the server's response.
     */
    private static String normalizeScriptUrl(String url) {
        if (url == null) return null;
        // Remove `?_=...` or `&_=...` (cache buster) but keep other query params.
        return url.replaceAll("[?&]_=[^&]*", "");
    }

    private boolean isSamePageHost(String resourceUrl) {
        // Runs on the WebView background thread — must not touch webView, only the mirror.
        String pageUrl = currentPageUrl;
        if (pageUrl == null || resourceUrl == null) return false;
        String pageHost = Uri.parse(pageUrl).getHost();
        String resourceHost = Uri.parse(resourceUrl).getHost();
        return pageHost != null && resourceHost != null && pageHost.equalsIgnoreCase(resourceHost);
    }

    /** True when the resource lives on a NEUQ academic host (direct EAMS, CAS, or WebVPN). */
    private static boolean isAcademicHost(String resourceUrl) {
        if (resourceUrl == null) return false;
        String host = Uri.parse(resourceUrl).getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("neuq.edu.cn") || host.endsWith(".neuq.edu.cn");
    }

    /**
     * Legacy EAMS pages break irrecoverably when jQuery is missing: every inline
     * `jQuery(...)` bootstrap throws and the page renders as a bare skeleton. The primary fix
     * is main-frame injection in the interceptor (a local jQuery script tag inserted into the
     * HTML before any inline script runs). This post-load check is the watchdog: it reloads
     * once so the injected HTML path takes effect, and otherwise reports clearly.
     */
    // URLs that already got one automatic reload after loading without jQuery. Bounded to a
    // single reload per URL so a still-broken page reports instead of looping.
    private final java.util.Set<String> jQueryReloadedUrls = new java.util.HashSet<>();

    private void ensureJQueryPresent(WebView view, String url) {
        if (view == null || url == null || !isAcademicHost(url)) return;
        // Login/CAS pages manage their own scripts; only the EAMS portal needs this.
        if (AcademicWebPolicy.useMobileAuthenticationLayout(url)) return;

        view.evaluateJavascript(
                "typeof jQuery !== 'undefined'",
                value -> {
                    if ("true".equals(value)) {
                        jQueryReloadedUrls.remove(url);
                        return;
                    }
                    if (jQueryReloadedUrls.add(url)) {
                        // First failure: reload so the main-frame injection path can kick in.
                        recordDiagnostic("JQUERY-MISSING-RELOAD " + shorten(url));
                        status.setText("页面脚本缺失，正在重新加载…");
                        view.reload();
                        return;
                    }
                    // Reload didn't help either — record it; the diagnostics (JS-REQ/HTML-INJECT
                    // markers) now show exactly what happened for follow-up.
                    recordDiagnostic("JQUERY-STILL-MISSING " + shorten(url));
                    status.setText("页面脚本仍未就绪，请点「诊断」查看详情");
                });
    }

    /**
     * Fetches an EAMS main document ourselves and, when its HTML does not reference jQuery at
     * all, inserts a script tag pointing at {@link #LOCAL_JQUERY_PATH} right after &lt;head&gt;.
     * Post-load injection cannot fix these pages — their inline scripts have already thrown by
     * then — so the tag must be in the HTML before the WebView parses it. The insert is pure
     * ASCII, which is valid in both GBK and UTF-8 documents, so the page charset is untouched.
     */
    private WebResourceResponse interceptMainDocument(String url) {
        try {
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            // CAS redirect hops must stay in the WebView (cookies, history, ticket handling).
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", AcademicWebPolicy.userAgent(url));
            conn.setRequestProperty("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Encoding", "identity");
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) conn.setRequestProperty("Cookie", cookie);
            conn.connect();
            int code = conn.getResponseCode();
            java.util.List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
            if (setCookies != null) {
                for (String value : setCookies) {
                    if (value != null) CookieManager.getInstance().setCookie(url, value);
                }
            }
            String contentType = conn.getContentType();
            if (code != 200 || contentType == null
                    || !contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
                // Redirect/error/non-HTML: let the WebView do the navigation itself.
                recordDiagnostic("MAIN-PASS " + code + " " + shorten(url));
                conn.disconnect();
                return null;
            }
            byte[] body = readAll(conn.getInputStream());
            conn.disconnect();
            if (indexOfAscii(body, "jquery") >= 0) {
                // Page references jQuery itself; the subresource fallback covers failures.
                recordDiagnostic("MAIN-HAS-JQUERY " + shorten(url));
            } else {
                int head = indexOfAscii(body, "<head");
                int insert = -1;
                if (head >= 0) {
                    for (int i = head; i < body.length; i++) {
                        if (body[i] == '>') { insert = i + 1; break; }
                    }
                }
                if (insert < 0) insert = indexOfAscii(body, "<script");
                if (insert >= 0) {
                    byte[] tag = ("<script src=\"" + LOCAL_JQUERY_PATH + "\"></script>")
                            .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                    byte[] merged = new byte[body.length + tag.length];
                    System.arraycopy(body, 0, merged, 0, insert);
                    System.arraycopy(tag, 0, merged, insert, tag.length);
                    System.arraycopy(body, insert, merged, insert + tag.length, body.length - insert);
                    body = merged;
                    recordDiagnostic("HTML-INJECT-JQUERY " + shorten(url));
                } else {
                    recordDiagnostic("HTML-INJECT-SKIP no-head " + shorten(url));
                }
            }
            String mime = "text/html";
            String charset = null;
            int cs = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
            if (cs >= 0) charset = contentType.substring(cs + 8).trim();
            WebResourceResponse response = new WebResourceResponse(mime, charset,
                    new java.io.ByteArrayInputStream(body));
            try {
                response.setStatusCodeAndReasonPhrase(200, "OK");
            } catch (Exception ignored) {
            }
            return response;
        } catch (Exception e) {
            recordDiagnostic("MAIN-FETCH-FAIL " + e.getClass().getSimpleName() + " " + shorten(url));
            return null;
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) > 0) out.write(buf, 0, read);
        return out.toByteArray();
    }

    /** Case-insensitive ASCII substring search over raw bytes (works for GBK and UTF-8 HTML). */
    private static int indexOfAscii(byte[] body, String needle) {
        byte[] target = needle.toLowerCase(Locale.ROOT)
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i + target.length <= body.length; i++) {
            for (int j = 0; j < target.length; j++) {
                byte b = body[i + j];
                if (b >= 'A' && b <= 'Z') b += 32;
                if (b != target[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static String reason(String message, int code) {
        if (message != null && !message.isEmpty()) return message;
        return code >= 400 ? "Error" : "OK";
    }

    private static String guessMime(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        if (u.endsWith(".js")) return "application/javascript";
        if (u.endsWith(".css")) return "text/css";
        if (u.endsWith(".json")) return "application/json";
        if (u.endsWith(".png")) return "image/png";
        if (u.endsWith(".jpg") || u.endsWith(".jpeg")) return "image/jpeg";
        if (u.endsWith(".gif")) return "image/gif";
        if (u.endsWith(".svg")) return "image/svg+xml";
        if (u.endsWith(".woff2")) return "font/woff2";
        if (u.endsWith(".woff")) return "font/woff";
        if (u.endsWith(".ttf")) return "font/ttf";
        return "text/html";
    }

    private void recordDiagnostic(String line) {
        if (line == null || line.isEmpty()) return;
        // Called from both the UI thread and the WebView background thread that runs
        // shouldInterceptRequest, so all access to the list is synchronized.
        synchronized (diagnostics) {
            // favicon.ico failures are harmless and, during a reload loop, arrive several times a
            // second — they would flush every useful entry out of the capped list. Keep one marker.
            if (line.contains("/favicon.ico")) {
                for (int i = diagnostics.size() - 1; i >= 0 && i >= diagnostics.size() - 5; i--) {
                    if (diagnostics.get(i).contains("/favicon.ico")) return;
                }
            }
            String stamped = String.format(Locale.CHINA, "%tT %s", new java.util.Date(), line);
            diagnostics.add(stamped);
            while (diagnostics.size() > MAX_DIAGNOSTICS) diagnostics.remove(0);
        }
    }

    private static String shorten(String url) {
        if (url == null) return "";
        return url.length() <= 120 ? url : url.substring(0, 117) + "...";
    }

    private void showDiagnostics() {
        List<String> snapshot;
        synchronized (diagnostics) {
            snapshot = new ArrayList<>(diagnostics);
        }
        String body;
        if (snapshot.isEmpty()) {
            body = "暂无加载错误记录。\n\n如果页面显示不全，请先在此页刷新一次，让错误被捕获后再查看。";
        } else {
            StringBuilder builder = new StringBuilder();
            for (int i = snapshot.size() - 1; i >= 0; i--) {
                builder.append(snapshot.get(i)).append('\n');
            }
            body = builder.toString();
        }
        final String report = body;
        TextView message = new TextView(this);
        message.setText(report);
        message.setTextSize(11);
        message.setTextIsSelectable(true);
        message.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(message);
        new AlertDialog.Builder(this)
                .setTitle("加载诊断（最近 " + snapshot.size() + " 条）")
                .setView(scroll)
                .setPositiveButton("复制", (ignored, which) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("diagnostics", report));
                        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("清空", (ignored, which) -> {
                    synchronized (diagnostics) {
                        diagnostics.clear();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void applyWebRenderProfile(WebView view, String url) {
        if (view == null) return;
        WebSettings webSettings = view.getSettings();
        // CAS advertises a responsive mobile page while EAMS advertises a legacy desktop page.
        // Keep the UA in sync with the destination so the portal returns the matching markup.
        // This is applied before loadUrl and on redirects; WebView only reloads when the value
        // actually changes, so an auth -> EAMS redirect settles after one transition.
        String desiredUserAgent = AcademicWebPolicy.userAgent(url);
        if (!desiredUserAgent.equals(webSettings.getUserAgentString())) {
            webSettings.setUserAgentString(desiredUserAgent);
        }
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(AcademicWebPolicy.loadWithOverview(url));
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
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
