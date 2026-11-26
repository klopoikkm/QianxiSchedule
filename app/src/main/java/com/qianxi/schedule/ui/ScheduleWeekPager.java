package com.qianxi.schedule.ui;

import android.content.Context;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.qianxi.schedule.data.AppSettings;
import com.qianxi.schedule.data.Course;
import com.qianxi.schedule.data.ScheduleTime;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Recycles weekly grids while Android's pager handles dragging, flings and snapping. */
public final class ScheduleWeekPager extends FrameLayout {
    public interface Listener extends ScheduleGridView.Listener {
        void onWeekSelected(int week);
    }

    private static final int WEEK_COUNT = 60;
    private final Listener listener;
    private final ViewPager2 pager;
    private final WeekAdapter adapter = new WeekAdapter();
    private final SparseArray<WeekHolder> boundPages = new SparseArray<>();
    private List<Course> courses = new ArrayList<>();
    private List<AppSettings.ClassTime> classTimes = new ArrayList<>();
    private LocalDate semesterStart = LocalDate.now();
    private int itemHeightDp = 56;
    private int primaryWeek = 1;
    private int verticalScrollPosition;
    private boolean hasData;
    private boolean refreshing;

    public ScheduleWeekPager(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        pager = new ViewPager2(context);
        pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pager.setOffscreenPageLimit(1);
        // Weeks always advance to the left, matching the existing navigation buttons.
        pager.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        RecyclerView recycler = (RecyclerView) pager.getChildAt(0);
        recycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recycler.setItemAnimator(null);
        adapter.setHasStableIds(true);
        pager.setAdapter(adapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageScrollStateChanged(int state) {
                if (state != ViewPager2.SCROLL_STATE_IDLE) captureAndSyncScrollPosition();
            }

            @Override public void onPageSelected(int position) {
                primaryWeek = position + 1;
                if (!refreshing) listener.onWeekSelected(primaryWeek);
            }
        });
        addView(pager, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void setData(List<Course> courses, LocalDate semesterStart,
                        List<AppSettings.ClassTime> classTimes, int itemHeightDp, int week) {
        if (hasData) captureAndSyncScrollPosition();
        this.courses = new ArrayList<>(courses);
        this.semesterStart = semesterStart;
        this.classTimes = new ArrayList<>(classTimes);
        this.itemHeightDp = itemHeightDp;
        hasData = true;
        refreshing = true;
        adapter.notifyDataSetChanged();
        pager.setCurrentItem(clampWeek(week) - 1, false);
        primaryWeek = selectedWeek();
        refreshing = false;
    }

    public void showWeek(int week) {
        captureAndSyncScrollPosition();
        pager.setCurrentItem(clampWeek(week) - 1, isLaidOut());
    }

    public int selectedWeek() {
        return pager.getCurrentItem() + 1;
    }

    public int verticalScrollPosition() {
        WeekHolder current = boundPages.get(primaryWeek);
        return current == null ? verticalScrollPosition : current.grid.verticalScrollPosition();
    }

    public void restoreVerticalScrollPosition(int position) {
        verticalScrollPosition = Math.max(0, position);
        for (int i = 0; i < boundPages.size(); i++) {
            boundPages.valueAt(i).grid.restoreVerticalScrollPosition(verticalScrollPosition);
        }
    }

    private void captureAndSyncScrollPosition() {
        verticalScrollPosition = verticalScrollPosition();
        for (int i = 0; i < boundPages.size(); i++) {
            WeekHolder page = boundPages.valueAt(i);
            if (page.week != primaryWeek) {
                page.grid.restoreVerticalScrollPosition(verticalScrollPosition);
            }
        }
    }

    private static int clampWeek(int week) {
        return Math.max(1, Math.min(WEEK_COUNT, week));
    }

    private final class WeekAdapter extends RecyclerView.Adapter<WeekHolder> {
        @NonNull @Override public WeekHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            ScheduleGridView grid = new ScheduleGridView(parent.getContext());
            grid.setListener(listener);
            grid.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new WeekHolder(grid);
        }

        @Override public void onBindViewHolder(@NonNull WeekHolder holder, int position) {
            forgetPage(holder);
            holder.week = position + 1;
            boundPages.put(holder.week, holder);
            List<Course> weeklyCourses = new ArrayList<>();
            for (Course course : courses) {
                if (course.occursInWeek(holder.week)) weeklyCourses.add(course);
            }
            holder.grid.setItemHeightDp(itemHeightDp);
            holder.grid.setData(weeklyCourses, ScheduleTime.weekStart(semesterStart, holder.week), classTimes);
            holder.grid.restoreVerticalScrollPosition(verticalScrollPosition);
        }

        @Override public void onViewRecycled(@NonNull WeekHolder holder) {
            forgetPage(holder);
        }

        private void forgetPage(WeekHolder holder) {
            if (boundPages.get(holder.week) == holder) boundPages.remove(holder.week);
            holder.week = 0;
        }

        @Override public int getItemCount() { return WEEK_COUNT; }

        @Override public long getItemId(int position) { return position + 1L; }
    }

    private static final class WeekHolder extends RecyclerView.ViewHolder {
        final ScheduleGridView grid;
        int week;

        WeekHolder(ScheduleGridView grid) {
            super(grid);
            this.grid = grid;
        }
    }
}
