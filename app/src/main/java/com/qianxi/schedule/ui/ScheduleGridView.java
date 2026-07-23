package com.qianxi.schedule.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.qianxi.schedule.data.AppSettings;
import com.qianxi.schedule.data.Course;
import com.qianxi.schedule.data.ScheduleTime;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Period-grid schedule view modelled on WakeUp: a fixed weekday header over a vertically scrolling
 * grid of fixed-height period rows. The left column is a time axis (period number + start/end time)
 * and the seven day columns hold course cards positioned by period (node) range.
 *
 * <p>When several courses fully share a slot only one card is drawn (with a count badge); tapping it
 * lets the user pick which one shows on top. Partially overlapping courses (staggered node ranges)
 * are offset slightly so both stay reachable.
 */
public final class ScheduleGridView extends LinearLayout {
    public interface Listener {
        void onCourseClick(Course course);
        void onEmptySlotClick(int dayOfWeek, int minute);
        /** Fired when a stacked slot with several courses is tapped, so the host can offer a chooser. */
        void onSlotStackClick(int dayOfWeek, int startNode, List<Course> courses);
    }

    private static final float DAY_WEIGHT = 1f;
    private static final int MAR_TOP_DP = 2;

    // WakeUp's nine-colour palette, assigned by course-name hash.
    private static final int[] PALETTE = {
            0xFFFF1744, // red
            0xFFFA6278, // pink
            0xFF2979FF, // blue
            0xFF1DE9B6, // green
            0xFFFFCA00, // yellow (darkened for white text legibility)
            0xFFFF9100, // orange
            0xFFFF3D00, // deepOrange
            0xFF2196F3, // lightBlue
            0xFF005CAF  // ruri
    };

    private final LinearLayout header;
    private final LinearLayout body;
    private Listener listener;
    private List<AppSettings.ClassTime> classTimes = new ArrayList<>();
    private LocalDate weekStart = LocalDate.now();
    private int itemHeightDp = 56;
    private AppSettings settings;

    public ScheduleGridView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        settings = new AppSettings(context);

        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(context);
        body.setOrientation(HORIZONTAL);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Sets the per-period row height in dp (user-adjustable via settings). */
    public void setItemHeightDp(int dp) {
        this.itemHeightDp = ScheduleGridMetrics.fromItemHeight(dp).itemHeightDp;
    }

    public void setData(List<Course> courses, LocalDate start,
                        List<AppSettings.ClassTime> times) {
        this.weekStart = start;
        this.classTimes = times == null ? new ArrayList<>() : times;
        buildHeader();
        buildBody(courses == null ? new ArrayList<>() : courses);
    }

    private void buildHeader() {
        header.removeAllViews();
        Context context = getContext();
        ScheduleGridMetrics metrics = ScheduleGridMetrics.fromItemHeight(itemHeightDp);
        LocalDate today = LocalDate.now();
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("d", Locale.CHINA);

        // Left cell: current month.
        TextView month = new TextView(context);
        month.setText(weekStart.getMonthValue() + "\n月");
        month.setTextSize(10);
        month.setTypeface(Typeface.DEFAULT_BOLD);
        month.setTextColor(Ui.INK);
        month.setGravity(Gravity.CENTER);
        month.setLineSpacing(Ui.dp(context, 1), 1f);
        month.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 6));
        header.addView(month, new LinearLayout.LayoutParams(
                Ui.dp(context, metrics.timeColumnWidthDp), ViewGroup.LayoutParams.WRAP_CONTENT));

        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        for (int day = 0; day < 7; day++) {
            LocalDate date = weekStart.plusDays(day);
            boolean isToday = date.equals(today);
            TextView cell = new TextView(context);
            cell.setText(names[day] + "\n" + date.format(dayFmt));
            cell.setTextSize(11);
            cell.setTypeface(Typeface.DEFAULT_BOLD);
            cell.setTextColor(isToday ? Ui.PRIMARY : Ui.INK);
            cell.setGravity(Gravity.CENTER);
            cell.setLineSpacing(Ui.dp(context, 1), 1f);
            cell.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 6));
            header.addView(cell, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, DAY_WEIGHT));
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void buildBody(List<Course> courses) {
        body.removeAllViews();
        Context context = getContext();
        ScheduleGridMetrics metrics = ScheduleGridMetrics.fromItemHeight(itemHeightDp);
        int itemHeight = Ui.dp(context, metrics.itemHeightDp);
        int marTop = Ui.dp(context, MAR_TOP_DP);
        int periods = Math.max(1, classTimes.size());
        int gridHeight = periods * (itemHeight + marTop) + marTop;

        // Left time-axis column: one cell per period. WakeUp applies the exact same item height to
        // its node labels and course cards; do that here too, then derive the number/time typography
        // from that shared height instead of keeping a fixed 8sp three-line label.
        LinearLayout axis = new LinearLayout(context);
        axis.setOrientation(VERTICAL);
        for (int i = 0; i < periods; i++) {
            AppSettings.ClassTime slot = classTimes.get(i);
            TextView cell = buildTimeAxisCell(context, slot, metrics);
            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, itemHeight);
            cellParams.topMargin = marTop;
            axis.addView(cell, cellParams);
        }
        body.addView(axis, new LinearLayout.LayoutParams(
                Ui.dp(context, metrics.timeColumnWidthDp), gridHeight));

        LocalDate today = LocalDate.now();
        for (int day = 1; day <= 7; day++) {
            FrameLayout column = new FrameLayout(context);
            if (weekStart.plusDays(day - 1L).equals(today)) {
                column.setBackgroundColor(Color.argb(40, 8, 127, 91));
            }
            final int dayOfWeek = day;
            column.setOnClickListener(v -> {
                if (listener != null && !classTimes.isEmpty()) {
                    listener.onEmptySlotClick(dayOfWeek, classTimes.get(0).startMinute);
                }
            });
            addDayCourses(column, courses, day, itemHeight, marTop);
            LinearLayout.LayoutParams columnParams =
                    new LinearLayout.LayoutParams(0, gridHeight, DAY_WEIGHT);
            columnParams.leftMargin = Ui.dp(context, 1);
            columnParams.rightMargin = Ui.dp(context, 1);
            body.addView(column, columnParams);
        }
    }

    private TextView buildTimeAxisCell(Context context, AppSettings.ClassTime slot,
                                       ScheduleGridMetrics metrics) {
        TextView cell = new TextView(context);
        String period = String.valueOf(slot.period);
        String value = period + "\n" + ScheduleTime.formatMinutes(slot.startMinute) + "\n"
                + ScheduleTime.formatMinutes(slot.endMinute);
        SpannableString label = new SpannableString(value);
        label.setSpan(new StyleSpan(Typeface.BOLD), 0, period.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new ForegroundColorSpan(Ui.INK), 0, period.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new RelativeSizeSpan(metrics.periodTextSp / metrics.timeTextSp),
                0, period.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        cell.setText(label);
        cell.setTextColor(Ui.MUTED);
        cell.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        cell.setGravity(Gravity.CENTER);
        cell.setIncludeFontPadding(false);
        cell.setMaxLines(3);
        cell.setLineSpacing(0, metrics.axisLineSpacingMultiplier);
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.timeTextSp);
        int minTextSp = Math.max(5, Math.round(metrics.timeTextSp - 2f));
        int maxTextSp = Math.max(minTextSp, Math.round(metrics.timeTextSp));
        cell.setAutoSizeTextTypeUniformWithConfiguration(minTextSp, maxTextSp, 1,
                TypedValue.COMPLEX_UNIT_SP);
        int verticalPadding = Ui.dp(context, metrics.axisVerticalPaddingDp);
        cell.setPadding(0, verticalPadding, 0, verticalPadding);
        cell.setContentDescription("第" + slot.period + "节，"
                + ScheduleTime.formatMinutes(slot.startMinute) + "至"
                + ScheduleTime.formatMinutes(slot.endMinute));
        cell.setFontFeatureSettings("tnum");
        return cell;
    }

    private void addDayCourses(FrameLayout column, List<Course> courses, int day,
                               int itemHeight, int marTop) {
        List<Course> dayCourses = new ArrayList<>();
        for (Course c : courses) {
            if (c.dayOfWeek == day) dayCourses.add(c);
        }
        int[] startNodes = new int[dayCourses.size()];
        int[] endNodes = new int[dayCourses.size()];
        for (int i = 0; i < dayCourses.size(); i++) {
            Course c = dayCourses.get(i);
            if (c.startNode >= 1 && c.step >= 1) {
                startNodes[i] = c.startNode;
                endNodes[i] = c.endNode();
            } else {
                int[] range = ScheduleTime.nodeRange(classTimes, c.startMinute, c.endMinute);
                startNodes[i] = range[0];
                endNodes[i] = range[1];
            }
        }

        // Group courses that share the exact same node range into one stack; a stack draws a single
        // card (user's preferred one on top) plus a count badge. Different-but-overlapping ranges are
        // handled separately with a small horizontal offset.
        List<List<Integer>> stacks = new ArrayList<>();
        boolean[] taken = new boolean[dayCourses.size()];
        for (int i = 0; i < dayCourses.size(); i++) {
            if (taken[i]) continue;
            List<Integer> group = new ArrayList<>();
            group.add(i);
            taken[i] = true;
            for (int j = i + 1; j < dayCourses.size(); j++) {
                if (!taken[j] && startNodes[j] == startNodes[i] && endNodes[j] == endNodes[i]) {
                    group.add(j);
                    taken[j] = true;
                }
            }
            stacks.add(group);
        }

        // Order stacks by start node so earlier ones are added first and stay visually stable.
        stacks.sort(Comparator.comparingInt((List<Integer> g) -> startNodes[g.get(0)])
                .thenComparingInt(g -> endNodes[g.get(0)]));

        Context context = getContext();
        int shiftStep = Ui.dp(context, 6);
        List<int[]> placed = new ArrayList<>(); // {startNode, endNode}
        for (List<Integer> group : stacks) {
            int first = group.get(0);
            int startNode = startNodes[first];
            int endNode = endNodes[first];
            int step = Math.max(1, endNode - startNode + 1);
            int top = (startNode - 1) * (itemHeight + marTop) + marTop;
            int height = step * itemHeight + (step - 1) * marTop;

            // Partial overlap with an already-placed *different* range: nudge right so both are seen.
            int overlap = 0;
            for (int[] p : placed) {
                boolean sameRange = p[0] == startNode && p[1] == endNode;
                if (!sameRange && startNode <= p[1] && endNode >= p[0]) overlap++;
            }
            placed.add(new int[]{startNode, endNode});

            // Choose which course in the stack shows on top: the user's slot preference if set.
            Course shown = dayCourses.get(first);
            long pref = settings.preferredCourseForSlot(day, startNode);
            if (pref != 0L) {
                for (int idx : group) {
                    if (dayCourses.get(idx).id == pref) {
                        shown = dayCourses.get(idx);
                        break;
                    }
                }
            }
            final Course shownCourse = shown;
            final List<Course> stackCourses = new ArrayList<>();
            for (int idx : group) stackCourses.add(dayCourses.get(idx));
            final int slotStartNode = startNode;

            FrameLayout cardWrap = new FrameLayout(context);
            TextView card = new TextView(context);
            card.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            card.setTextColor(Color.WHITE);
            card.setTextSize(step == 1 ? 11 : 10);
            card.setTypeface(Typeface.DEFAULT_BOLD);
            card.setLineSpacing(0, 1.05f);
            card.setPadding(Ui.dp(context, 3), Ui.dp(context, 4),
                    Ui.dp(context, 3), Ui.dp(context, 4));
            card.setText(cardText(shownCourse, step));
            card.setBackground(cardBackground(context, courseColor(shownCourse)));
            cardWrap.addView(card, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            if (stackCourses.size() > 1) {
                TextView badge = new TextView(context);
                badge.setText(String.valueOf(stackCourses.size()));
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(9);
                badge.setTypeface(Typeface.DEFAULT_BOLD);
                badge.setGravity(Gravity.CENTER);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(0xCC000000);
                badge.setBackground(bg);
                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                        Ui.dp(context, 15), Ui.dp(context, 15), Gravity.TOP | Gravity.END);
                badgeParams.setMargins(0, Ui.dp(context, 2), Ui.dp(context, 2), 0);
                cardWrap.addView(badge, badgeParams);
            }

            cardWrap.setOnClickListener(v -> {
                if (listener == null) return;
                if (stackCourses.size() > 1) {
                    listener.onSlotStackClick(day, slotStartNode, stackCourses);
                } else {
                    listener.onCourseClick(shownCourse);
                }
            });

            FrameLayout.LayoutParams params =
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
            params.topMargin = top;
            params.leftMargin = overlap * shiftStep;
            column.addView(cardWrap, params);
        }
    }

    private String cardText(Course c, int step) {
        StringBuilder text = new StringBuilder(c.name);
        if (!c.location.isEmpty()) text.append("\n@").append(c.location);
        // Only show the teacher when the card is tall enough to fit an extra line.
        if (step >= 2 && !c.teacher.isEmpty()) text.append("\n").append(c.teacher);
        return text.toString();
    }

    private int courseColor(Course c) {
        if (c.color != 0 && c.color != 0xFF2F9E6F) return c.color;
        return PALETTE[Math.floorMod(c.name.hashCode(), PALETTE.length)];
    }

    /** WakeUp's course_item_bg: rounded 4dp, 2dp translucent white stroke, semi-transparent fill. */
    private GradientDrawable cardBackground(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        int alphaColor = (color & 0x00FFFFFF) | (0xA9 << 24);
        drawable.setColor(alphaColor);
        drawable.setCornerRadius(Ui.dp(context, 4));
        drawable.setStroke(Ui.dp(context, 2), 0x80FFFFFF);
        return drawable;
    }
}
