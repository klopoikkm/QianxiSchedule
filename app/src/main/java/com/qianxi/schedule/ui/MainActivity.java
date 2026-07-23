package com.qianxi.schedule.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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

public final class MainActivity extends Activity implements ScheduleGridView.Listener {
    private AppSettings settings;
    private CourseDatabase database;
    private ScheduleGridView scheduleGrid;
    private ImageView backgroundImage;
    private TextView weekLabel;
    private TextView weekSubLabel;
    private TextView silentStatus;
    private TextView emptyState;
    private TextView nextSummary;
    private Course summaryCourse;
    private int selectedWeek;

    private View insetTarget;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new AppSettings(this);
        database = CourseDatabase.get(this);
        selectedWeek = ScheduleTime.weekOf(settings.semesterStart(), LocalDate.now());
        // Draw edge-to-edge so the background image (or gradient) fills behind the status and
        // navigation bars instead of leaving a paper-coloured strip under the phone's info bar.
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        View content = buildContent();
        // Inset only the content layer, not the root: the background ImageView must stay full-bleed
        // (reaching under both system bars) while the toolbar/grid keep clear of them.
        Ui.applySystemBarInsets(insetTarget);
        setContentView(content);
        AlarmScheduler.ensureDailyRefresh(this);
    }

    private View buildContent() {
        FrameLayout root = new FrameLayout(this);

        // Background layer: a user-picked image if set, otherwise the default sky gradient.
        backgroundImage = new ImageView(this);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int[] gradientColors = {Color.rgb(135, 206, 235), Color.rgb(176, 224, 230)};
        android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, gradientColors);
        backgroundImage.setBackground(gradient);
        root.addView(backgroundImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Content layer.
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        insetTarget = content;

        // Top bar: big date + week info + silence tag on the left; week nav + "+" menu on the right.
        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 4));

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        weekLabel = Ui.text(this, "", 18, Ui.INK);
        weekLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        weekLabel.setMaxLines(1);
        titleColumn.addView(weekLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout subRow = new LinearLayout(this);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        weekSubLabel = Ui.text(this, "", 13, Ui.MUTED);
        subRow.addView(weekSubLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        silentStatus = Ui.text(this, "", 11, Ui.PRIMARY);
        silentStatus.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 4), 0);
        silentStatus.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        subRow.addView(silentStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams subRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subRowParams.topMargin = Ui.dp(this, 2);
        titleColumn.addView(subRow, subRowParams);
        topBar.addView(titleColumn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Week navigation: ‹ 今 › then a "+" menu that holds add/import/settings so the bottom bar
        // and the floating action button can both be removed, freeing the whole screen for the grid.
        Button prevWeek = smallIconButton("‹", "上一周", 22);
        prevWeek.setOnClickListener(v -> changeWeek(-1));
        topBar.addView(prevWeek, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 40)));
        Button todayWeek = smallIconButton("今天", "回到本周", 12);
        todayWeek.setTextColor(Color.WHITE);
        todayWeek.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable todayBg = new GradientDrawable();
        todayBg.setColor(Ui.PRIMARY);
        todayBg.setCornerRadius(Ui.dp(this, 12));
        todayWeek.setBackground(todayBg);
        todayWeek.setPadding(Ui.dp(this, 9), 0, Ui.dp(this, 9), 0);
        todayWeek.setOnClickListener(v -> {
            selectedWeek = ScheduleTime.weekOf(settings.semesterStart(), LocalDate.now());
            refresh();
        });
        LinearLayout.LayoutParams todayParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 26));
        todayParams.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
        topBar.addView(todayWeek, todayParams);
        Button nextWeek = smallIconButton("›", "下一周", 22);
        nextWeek.setOnClickListener(v -> changeWeek(1));
        topBar.addView(nextWeek, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 40)));
        Button menu = smallIconButton("+", "更多操作", 26);
        menu.setOnClickListener(this::showMenu);
        topBar.addView(menu, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        content.addView(topBar);

        nextSummary = Ui.text(this, "", 12, Ui.INK);
        nextSummary.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        nextSummary.setMaxLines(2);
        nextSummary.setBackground(Ui.rounded(Color.argb(240, 255, 255, 255), 10, this));
        nextSummary.setElevation(Ui.dp(this, 2));
        nextSummary.setOnClickListener(v -> {
            if (summaryCourse != null) onCourseClick(summaryCourse);
        });
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
        summaryParams.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 4));
        content.addView(nextSummary, summaryParams);

        // The schedule now spans the full width (no side margins, no card chrome) so each of the
        // seven day columns is as wide as possible and course names stop wrapping to one character.
        // Wrap it in a horizontal-swipe container so a left/right fling changes the week (WakeUp
        // parity). The inner ScheduleGridView owns vertical scrolling — we only intercept when the
        // gesture is clearly sideways, so the vertical scroll keeps working untouched.
        FrameLayout scheduleContainer = buildSwipeContainer();
        scheduleGrid = new ScheduleGridView(this);
        scheduleGrid.setListener(this);
        scheduleContainer.addView(scheduleGrid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyState = Ui.text(this, "暂无课程，点击 + 添加或导入", 14, Ui.MUTED);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        emptyState.setBackground(Ui.rounded(Color.argb(200, 255, 255, 255), 8, this));
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        emptyParams.topMargin = Ui.dp(this, 68);
        scheduleContainer.addView(emptyState, emptyParams);
        content.addView(scheduleContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "添加课程");
        popup.getMenu().add(0, 2, 1, "导入课表");
        popup.getMenu().add(0, 3, 2, "设置");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    openNewCourse(1, 8 * 60);
                    return true;
                case 2:
                    startActivity(new Intent(this, ImportActivity.class));
                    return true;
                case 3:
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyBackground();
        refresh();
    }

    /** Loads the user's background image (sampled to screen size) or falls back to the gradient. */
    private void applyBackground() {
        String path = settings.backgroundPath();
        if (path == null || path.isEmpty()) {
            backgroundImage.setImageDrawable(null);
            return;
        }
        try {
            Drawable drawable = decodeSampledBackground(path);
            backgroundImage.setImageDrawable(drawable);
        } catch (Throwable t) {
            backgroundImage.setImageDrawable(null);
        }
    }

    private Drawable decodeSampledBackground(String path) {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(path, bounds);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= screenW && bounds.outHeight / (sample * 2) >= screenH
                && sample < 8) {
            sample *= 2;
        }
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = sample;
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path, opts);
        return bitmap == null ? null : new BitmapDrawable(getResources(), bitmap);
    }

    private void changeWeek(int amount) {
        selectedWeek = Math.max(1, Math.min(60, selectedWeek + amount));
        refresh();
    }

    private void refresh() {
        LocalDate start = ScheduleTime.weekStart(settings.semesterStart(), selectedWeek);
        List<Course> courses = database.forWeek(selectedWeek);
        boolean scheduleIsEmpty = database.all().isEmpty();
        // Big title (WakeUp parity): the top date is unconditionally today's real date, regardless
        // of which week is being viewed. WakeUp's CourseUtils.getTodayDate() never looks at the
        // selected week, and neither should we — the previous "today if selectedWeek==currentWeek
        // else week's Monday" logic was the actual cause of the top date drifting.
        weekLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/M/d", Locale.CHINA)));
        DateTimeFormatter range = DateTimeFormatter.ofPattern("M.d", Locale.CHINA);
        weekSubLabel.setText(String.format(Locale.CHINA, "第 %d 周  %s–%s", selectedWeek,
                start.format(range), start.plusDays(6).format(range)));
        silentStatus.setText(settings.autoSilentEnabled() ? "· 静音已开" : "· 静音未开");
        silentStatus.setTextColor(settings.autoSilentEnabled() ? Ui.PRIMARY : Ui.MUTED);
        scheduleGrid.setItemHeightDp(settings.itemHeightDp());
        scheduleGrid.setData(courses, start, settings.classTimes());
        // A blank teaching week is valid (odd/even weeks, holidays, late semester). Only show the
        // add/import prompt when the entire timetable is empty; otherwise leave the week grid clean.
        emptyState.setVisibility(scheduleIsEmpty ? View.VISIBLE : View.GONE);
        updateSummary(scheduleIsEmpty);
    }

    /**
     * FrameLayout that treats a decisive horizontal swipe as a week change (like WakeUp): swipe
     * right → previous week, swipe left → next week. Small movements and mostly-vertical drags are
     * left to the schedule grid's own vertical ScrollView. Once a horizontal swipe is claimed the
     * gesture is consumed for the rest of the gesture, so a swipe never doubles as a course tap.
     */
    private FrameLayout buildSwipeContainer() {
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        final int minSwipePx = Ui.dp(this, 48);
        return new FrameLayout(this) {
            private float downX, downY;
            private boolean claimed;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = ev.getX();
                        downY = ev.getY();
                        claimed = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        float dx = ev.getX() - downX;
                        float dy = ev.getY() - downY;
                        if (!claimed && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                            claimed = true;
                            return true;
                        }
                        return false;
                    default:
                        return false;
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_UP:
                        if (claimed) {
                            float dx = ev.getX() - downX;
                            if (Math.abs(dx) >= minSwipePx) changeWeek(dx > 0 ? -1 : 1);
                            claimed = false;
                            return true;
                        }
                        return false;
                    case MotionEvent.ACTION_CANCEL:
                        claimed = false;
                        return false;
                    default:
                        return claimed;
                }
            }
        };
    }

    /** Small transparent icon/label button used by the top-right week navigation and "+" menu. */
    private Button smallIconButton(String label, String description, float textSize) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(textSize);
        button.setTextColor(Ui.INK);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(description);
        return button;
    }

    /**
     * The "next class" card always reflects the actual current moment, never the week the user
     * happens to be viewing (it used to be gated by selectedWeek == currentWeek, so scrolling to
     * another week or drifting past that comparison silently fell back to "courses.get(0)" of the
     * VIEWED week's list, which had nothing to do with real time). Fetches the current week's own
     * courses directly instead of reusing the possibly-different displayed week's list.
     */
    private void updateSummary(boolean scheduleIsEmpty) {
        summaryCourse = null;
        LocalDate today = LocalDate.now();
        int currentWeek = ScheduleTime.weekOf(settings.semesterStart(), today);
        int totalWeeks = settings.totalWeeks();
        if (currentWeek > totalWeeks) {
            nextSummary.setText("本学期已结束");
            nextSummary.setTextColor(Ui.MUTED);
            nextSummary.setClickable(false);
            return;
        }

        List<Course> courses = database.forWeek(currentWeek);
        if (courses.isEmpty()) {
            nextSummary.setText(scheduleIsEmpty ? "暂无课程 · 点击 + 添加或导入" : "本周无课");
            nextSummary.setTextColor(Ui.MUTED);
            nextSummary.setClickable(false);
            return;
        }

        LocalDate weekStart = ScheduleTime.weekStart(settings.semesterStart(), currentWeek);
        int now = java.time.LocalTime.now().getHour() * 60 + java.time.LocalTime.now().getMinute();
        int todayIndex = today.getDayOfWeek().getValue();
        Course ongoing = null;
        Course next = null;
        for (Course course : courses) {
            if (course.dayOfWeek != todayIndex) continue;
            if (course.startMinute <= now && now < course.endMinute) {
                ongoing = course;
                break;
            }
            if (course.startMinute > now && next == null) next = course;
        }
        if (ongoing != null) {
            summaryCourse = ongoing;
            nextSummary.setText(String.format(Locale.CHINA, "正在上课 · 周%s %s\n%s%s",
                    chineseDay(today.getDayOfWeek().getValue()), ScheduleTime.formatMinutes(ongoing.startMinute),
                    ongoing.name, ongoing.location.isEmpty() ? "" : " · " + ongoing.location));
            nextSummary.setTextColor(Ui.PRIMARY);
        } else {
            if (next == null) {
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

    @Override
    public void onSlotStackClick(int dayOfWeek, int startNode, List<Course> courses) {
        // Several courses share this slot: let the user pick which shows on top, or open one.
        CharSequence[] labels = new CharSequence[courses.size()];
        for (int i = 0; i < courses.size(); i++) {
            Course c = courses.get(i);
            String extra = c.location.isEmpty() ? "" : " @" + c.location;
            labels[i] = c.name + extra;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("该时段有 " + courses.size() + " 门课")
                .setItems(labels, (dialog, which) -> {
                    Course chosen = courses.get(which);
                    new android.app.AlertDialog.Builder(this)
                            .setTitle(chosen.name)
                            .setNegativeButton("编辑", (d, w) -> onCourseClick(chosen))
                            .setPositiveButton("置顶显示", (d, w) -> {
                                settings.setPreferredCourseForSlot(dayOfWeek, startNode, chosen.id);
                                refresh();
                            })
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openNewCourse(int day, int minute) {
        Intent intent = new Intent(this, CourseEditorActivity.class);
        intent.putExtra(CourseEditorActivity.EXTRA_DAY, day);
        intent.putExtra(CourseEditorActivity.EXTRA_START_MINUTE, minute);
        startActivity(intent);
    }
}
