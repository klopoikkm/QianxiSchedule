package com.qianxi.schedule.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.qianxi.schedule.data.AppSettings;
import com.qianxi.schedule.data.Course;
import com.qianxi.schedule.data.CourseDatabase;
import com.qianxi.schedule.data.NextClass;
import com.qianxi.schedule.data.ScheduleTime;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Select, adjust and preview a background without changing the saved image until confirmation. */
public final class BackgroundEditorActivity extends Activity {
    public static final String EXTRA_VIEWPORT_WIDTH = "background_viewport_width";
    public static final String EXTRA_VIEWPORT_HEIGHT = "background_viewport_height";
    private static final int REQUEST_IMAGE = 51;
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    private AppSettings settings;
    private BackgroundCropView cropView;
    private FrameLayout previewClip;
    private FrameLayout previewContent;
    private SeekBar zoomBar;
    private TextView zoomLabel;
    private TextView status;
    private Button choose;
    private Button save;
    private Button reset;
    private Button back;
    private Switch preview;
    private File sourceFile;
    private Uri sourceUri;
    private boolean ownsDraft;
    private boolean loading;
    private boolean saving;
    private boolean saved;
    private boolean pickerOpen;
    private int targetWidth;
    private int targetHeight;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        updateTargetSize();
        int width = getIntent().getIntExtra(EXTRA_VIEWPORT_WIDTH, 0);
        int height = getIntent().getIntExtra(EXTRA_VIEWPORT_HEIGHT, 0);
        if (width > 0 && height > 0 && (width > height) == (targetWidth > targetHeight)) {
            targetWidth = width;
            targetHeight = height;
        }
        View content = buildContent();
        Ui.applySystemBarInsets(content);
        setContentView(content);
        BackgroundCropTransform.State crop = new BackgroundCropTransform.State(
                settings.backgroundZoom(), settings.backgroundFocusX(), settings.backgroundFocusY());
        String path = settings.backgroundSourcePath();
        boolean draft = false;
        Uri uri = getIntent().getData();
        if (state != null) {
            path = state.getString("source_path", path);
            draft = state.getBoolean("draft");
            String uriString = state.getString("source_uri");
            uri = uriString == null ? null : Uri.parse(uriString);
            pickerOpen = state.getBoolean("picker_open");
            crop = new BackgroundCropTransform.State(state.getDouble("zoom", 1),
                    state.getDouble("focus_x", 0.5), state.getDouble("focus_y", 0.5));
            preview.setChecked(state.getBoolean("preview"));
        }
        if (uri != null) {
            File target = draft && !path.isEmpty() ? new File(path) : BackgroundImageStore.newSource(this);
            load(target, uri, true, crop);
        } else if (!path.isEmpty()) {
            File original = new File(path);
            if (!original.isFile() && !settings.backgroundPath().isEmpty()) {
                original = new File(settings.backgroundPath());
                crop = new BackgroundCropTransform.State(1, 0.5, 0.5);
                draft = false;
            }
            load(original, null, draft, crop);
        } else if (!pickerOpen) {
            cropView.post(this::pickImage);
        }
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        back = Ui.textButton(this, "‹");
        back.setTextSize(28);
        back.setContentDescription("取消背景编辑并返回");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 52)));
        TextView title = Ui.text(this, "编辑背景", 20, Ui.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
        choose = Ui.textButton(this, "换图");
        choose.setContentDescription("重新选择背景图片");
        choose.setOnClickListener(v -> pickImage());
        toolbar.addView(choose, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 52)));
        save = Ui.textButton(this, "完成");
        save.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        save.setContentDescription("保存裁剪后的背景图片");
        save.setEnabled(false);
        save.setOnClickListener(v -> saveBackground());
        toolbar.addView(save, new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 52)));
        root.addView(toolbar);

        status = Ui.text(this, "拖动调整位置 · 双指或滑条缩放", 12, Ui.MUTED);
        status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 8));
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout stage = new FrameLayout(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                // The live schedule is an overlay; gestures still adjust the picture underneath.
                return cropView.dispatchTouchEvent(event);
            }
        };
        cropView = new BackgroundCropView(this);
        cropView.setAspectRatio(targetWidth, targetHeight);
        stage.addView(cropView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        previewClip = new FrameLayout(this);
        previewClip.setVisibility(View.GONE);
        previewClip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        stage.addView(previewClip);
        root.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 8));
        LinearLayout controlRow = new LinearLayout(this);
        controlRow.setGravity(Gravity.CENTER_VERTICAL);
        zoomLabel = Ui.text(this, "缩放  100%", 14, Ui.INK);
        controlRow.addView(zoomLabel, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        preview = new Switch(this);
        preview.setText("课表预览  ");
        preview.setTextSize(13);
        preview.setContentDescription("显示本地课表预览，仍可拖动和缩放背景");
        preview.setOnCheckedChangeListener((button, checked) -> {
            previewClip.setVisibility(checked ? View.VISIBLE : View.GONE);
            cropView.setShowGuides(!checked);
        });
        controlRow.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44)));
        controls.addView(controlRow);
        zoomBar = new SeekBar(this);
        zoomBar.setMax(400);
        zoomBar.setEnabled(false);
        zoomBar.setContentDescription("背景缩放，100% 到 500%");
        zoomBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) cropView.setZoom(1 + progress / 100.0);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        controls.addView(zoomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));
        reset = Ui.textButton(this, "重置位置与缩放");
        reset.setContentDescription("重置背景位置与缩放");
        reset.setEnabled(false);
        reset.setOnClickListener(v -> cropView.reset());
        controls.addView(reset, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));
        root.addView(controls);
        cropView.setOnChange(this::updateControls);
        return root;
    }

    private void pickImage() {
        if (loading || saving || pickerOpen) return;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQUEST_IMAGE);
            pickerOpen = true;
        } catch (Exception exception) {
            Toast.makeText(this, "系统不支持选择图片", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_IMAGE) return;
        pickerOpen = false;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            if (cropView.image() == null && !loading) finish();
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        load(BackgroundImageStore.newSource(this), uri, true, new BackgroundCropTransform.State(1, 0.5, 0.5));
    }

    private void load(File file, Uri uri, boolean draft, BackgroundCropTransform.State crop) {
        File previous = sourceFile;
        boolean previousDraft = ownsDraft;
        sourceFile = file;
        sourceUri = uri;
        ownsDraft = draft;
        loading = true;
        status.setText("正在读取图片…");
        updateControls();
        images.execute(() -> {
            try {
                if (uri != null && !file.isFile()) BackgroundImageStore.copySource(getContentResolver(), uri, file);
                Bitmap image = BackgroundImageStore.decode(file);
                List<Course> courses = CourseDatabase.get(this).all();
                runOnUiThread(() -> {
                    releaseUri(uri);
                    if (isDestroyed() || isFinishing()) {
                        image.recycle();
                        if (isFinishing() && draft) BackgroundImageStore.deleteOwned(this, file.getPath());
                        if (isFinishing() && previousDraft && previous != null) {
                            BackgroundImageStore.deleteOwned(this, previous.getPath());
                        }
                        return;
                    }
                    sourceUri = null;
                    loading = false;
                    cropView.setImage(image, crop);
                    buildSchedulePreview(courses);
                    if (previousDraft && previous != null && !previous.equals(file)) {
                        BackgroundImageStore.deleteOwned(this, previous.getPath());
                    }
                    status.setText("拖动调整位置 · 双指或滑条缩放");
                    updateControls();
                });
            } catch (IOException | RuntimeException | OutOfMemoryError exception) {
                runOnUiThread(() -> {
                    releaseUri(uri);
                    if (draft) BackgroundImageStore.deleteOwned(this, file.getPath());
                    if (isDestroyed() || isFinishing()) {
                        if (isFinishing() && previousDraft && previous != null) {
                            BackgroundImageStore.deleteOwned(this, previous.getPath());
                        }
                        return;
                    }
                    sourceFile = previous;
                    ownsDraft = previousDraft;
                    sourceUri = null;
                    loading = false;
                    status.setText("无法读取图片，请重新选择");
                    updateControls();
                    Toast.makeText(this, "读取图片失败：" + errorMessage(exception), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void saveBackground() {
        if (loading || saving || cropView.image() == null || sourceFile == null) return;
        Bitmap image = cropView.image();
        BackgroundCropTransform.State crop = cropView.cropState();
        File original = sourceFile;
        int width = targetWidth;
        int height = targetHeight;
        saving = true;
        status.setText("正在保存背景…");
        updateControls();
        images.execute(() -> {
            try {
                File result = BackgroundImageStore.writeCrop(this, image, crop, width, height);
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) {
                        BackgroundImageStore.deleteOwned(this, result.getPath());
                        return;
                    }
                    String previous = settings.backgroundPath();
                    String previousSource = settings.backgroundSourcePath();
                    float oldZoom = settings.backgroundZoom();
                    float oldX = settings.backgroundFocusX();
                    float oldY = settings.backgroundFocusY();
                    if (!settings.saveBackground(result.getPath(), original.getPath(),
                            (float) crop.zoom, (float) crop.focusX, (float) crop.focusY)) {
                        if (settings.saveBackground(previous, previousSource, oldZoom, oldX, oldY)) {
                            BackgroundImageStore.deleteOwned(this, result.getPath());
                        }
                        saving = false;
                        status.setText("保存失败，请重试");
                        updateControls();
                        return;
                    }
                    saved = true;
                    if (!previous.equals(original.getPath())) BackgroundImageStore.deleteOwned(this, previous);
                    if (!previousSource.equals(original.getPath())) BackgroundImageStore.deleteOwned(this, previousSource);
                    Toast.makeText(this, "背景已设置", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (IOException | RuntimeException | OutOfMemoryError exception) {
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    saving = false;
                    status.setText("保存失败，请重试");
                    updateControls();
                    Toast.makeText(this, "保存背景失败：" + errorMessage(exception), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateControls() {
        if (zoomBar == null) return;
        boolean ready = cropView.image() != null && !loading && !saving;
        choose.setEnabled(!loading && !saving);
        save.setEnabled(ready);
        save.setText(saving ? "保存中" : "完成");
        back.setEnabled(!saving);
        reset.setEnabled(ready);
        zoomBar.setEnabled(ready);
        cropView.setEnabled(ready);
        int percent = (int) Math.round(cropView.cropState().zoom * 100);
        zoomLabel.setText("缩放  " + percent + "%");
        zoomBar.setProgress(percent - 100);
        updatePreviewBounds();
    }

    private void updatePreviewBounds() {
        if (previewContent == null) return;
        RectF bounds = cropView.cropBounds();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) previewClip.getLayoutParams();
        int width = Math.round(bounds.width());
        int height = Math.round(bounds.height());
        int left = Math.round(bounds.left);
        int top = Math.round(bounds.top);
        if (params.width != width || params.height != height || params.leftMargin != left || params.topMargin != top) {
            params.width = width;
            params.height = height;
            params.leftMargin = left;
            params.topMargin = top;
            previewClip.setLayoutParams(params);
        }
        ViewGroup.LayoutParams contentParams = previewContent.getLayoutParams();
        if (contentParams.width != targetWidth || contentParams.height != targetHeight) {
            previewContent.setLayoutParams(new FrameLayout.LayoutParams(targetWidth, targetHeight));
        }
        previewContent.setPivotX(0);
        previewContent.setPivotY(0);
        previewContent.setScaleX(bounds.width() / targetWidth);
        previewContent.setScaleY(bounds.height() / targetHeight);
    }

    private void buildSchedulePreview(List<Course> courses) {
        previewClip.removeAllViews();
        previewContent = new FrameLayout(this);
        View mask = new View(this);
        int mode = settings.textEnhancementMode();
        mask.setBackgroundColor(mode == 1 ? Color.argb(128, 255, 255, 255)
                : mode == 2 ? Color.argb(100, 0, 0, 0) : Color.TRANSPARENT);
        previewContent.addView(mask, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                content.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                content.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
        }
        LocalDate today = LocalDate.now();
        int week = ScheduleTime.weekOf(settings.semesterStart(), today);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 4));
        TextView date = Ui.text(this, today.getMonthValue() + "月" + today.getDayOfMonth() + "日", 18, Ui.INK);
        date.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(date);
        header.addView(Ui.text(this, "第 " + week + " 周", 13, Ui.MUTED));
        content.addView(header);
        LocalTime now = LocalTime.now();
        NextClass next = NextClass.find(courses, settings.semesterStart(), settings.totalWeeks(), today,
                now.getHour() * 60 + now.getMinute());
        String summary = next == null ? "暂无课程 · 点击 + 添加或导入"
                : (next.kind == NextClass.Kind.ONGOING ? "正在上课" : "下一节") + " · "
                + ScheduleTime.formatMinutes(next.course.startMinute) + "\n" + next.course.name;
        TextView nextText = Ui.text(this, summary, 12, Ui.INK);
        nextText.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        nextText.setBackground(Ui.rounded(Color.argb(240, 255, 255, 255), 10, this));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
        summaryParams.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 4));
        content.addView(nextText, summaryParams);
        List<Course> visible = new ArrayList<>();
        for (Course course : courses) if (course.occursInWeek(week)) visible.add(course);
        ScheduleGridView grid = new ScheduleGridView(this);
        grid.setItemHeightDp(settings.itemHeightDp());
        grid.setData(visible, settings.semesterStart().plusWeeks(week - 1L), settings.classTimes());
        content.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        previewContent.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        previewClip.addView(previewContent);
        updatePreviewBounds();
    }

    private void updateTargetSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            targetWidth = bounds.width();
            targetHeight = bounds.height();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            targetWidth = metrics.widthPixels;
            targetHeight = metrics.heightPixels;
        }
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        updateTargetSize();
        cropView.setAspectRatio(targetWidth, targetHeight);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (sourceFile != null) state.putString("source_path", sourceFile.getPath());
        if (sourceUri != null) state.putString("source_uri", sourceUri.toString());
        state.putBoolean("draft", ownsDraft);
        state.putBoolean("picker_open", pickerOpen);
        state.putBoolean("preview", preview.isChecked());
        BackgroundCropTransform.State crop = cropView.cropState();
        state.putDouble("zoom", crop.zoom);
        state.putDouble("focus_x", crop.focusX);
        state.putDouble("focus_y", crop.focusY);
    }

    @Override public void onBackPressed() {
        if (!saving) super.onBackPressed();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        images.shutdown();
        if (isFinishing() && ownsDraft && !saved && sourceFile != null) {
            BackgroundImageStore.deleteOwned(this, sourceFile.getPath());
        }
    }

    private void releaseUri(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
    }

    private static String errorMessage(Throwable error) {
        if (error instanceof OutOfMemoryError) return "图片过大，请选择较小的图片";
        String message = error.getMessage();
        return message == null ? "不支持或已损坏的图片" : message;
    }
}
