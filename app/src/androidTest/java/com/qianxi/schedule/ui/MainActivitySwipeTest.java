package com.qianxi.schedule.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.viewpager2.widget.ViewPager2;

import com.qianxi.schedule.data.AppSettings;
import com.qianxi.schedule.data.Course;
import com.qianxi.schedule.data.CourseDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/** Verify real touch dispatch through course cards, the vertical grid and the weekly pager. */
public final class MainActivitySwipeTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<Long> fixtureIds = new ArrayList<>();
    private CourseDatabase database;
    private SharedPreferences preferences;
    private Map<String, ?> oldPreferences;
    private MainActivity activity;
    private Activity editor;
    private Instrumentation.ActivityMonitor editorMonitor;
    private Course currentCourse;
    private long gestureStart;
    private float lastX, lastY;
    private boolean pointerDown;

    @Before public void setUp() {
        Context context = instrumentation.getTargetContext();
        database = CourseDatabase.get(context);
        preferences = context.getSharedPreferences("qianxi_settings", Context.MODE_PRIVATE);
        oldPreferences = preferences.getAll();
        AppSettings settings = new AppSettings(context);
        settings.setSemesterStart(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(2));
        settings.setItemHeightDp(80);
        settings.setAutoSilentEnabled(false);
        currentCourse = addCourse("滑动测试·本周", 6, 1, 2, 3, 3);
        addCourse("滑动测试·下周", 2, 1, 2, 4, 4);
        addCourse("滑动测试·第30节", 7, 30, 1, 1, 60);
        editorMonitor = instrumentation.addMonitor(CourseEditorActivity.class.getName(), null, true);
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) instrumentation.startActivitySync(intent);
        awaitSettled(3);
    }

    @After public void tearDown() {
        if (pointerDown) endGesture(MotionEvent.ACTION_CANCEL);
        instrumentation.runOnMainSync(() -> {
            if (editor != null && !editor.isFinishing()) editor.finish();
            if (activity != null && !activity.isFinishing()) activity.finish();
        });
        instrumentation.waitForIdleSync();
        if (editorMonitor != null) instrumentation.removeMonitor(editorMonitor);
        for (long id : fixtureIds) database.delete(id);
        SharedPreferences.Editor restore = preferences.edit();
        for (String key : new String[]{"semester_start", "item_height_dp", "auto_silent"}) {
            Object value = oldPreferences.get(key);
            if (value == null) restore.remove(key);
            else if (value instanceof Long) restore.putLong(key, (Long) value);
            else if (value instanceof Integer) restore.putInt(key, (Integer) value);
            else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
        }
        assertTrue(restore.commit());
    }

    @Test public void dragMovesBothWeeksBeforeReleaseAndCancelsCourseTap() {
        ScheduleGridView oldGrid = read(this::currentGrid);
        Rect card = read(() -> bounds(findText(oldGrid, "滑动测试·本周")));
        Rect viewport = read(() -> bounds(weekPager()));
        beginGesture(card.exactCenterX(), card.exactCenterY());
        moveGesture(card.exactCenterX() - viewport.width() * 0.45f, card.exactCenterY(), 500);
        read(() -> {
            assertEquals(ViewPager2.SCROLL_STATE_DRAGGING, nativePager().getScrollState());
            Rect oldBounds = new Rect();
            assertTrue(oldGrid.getGlobalVisibleRect(oldBounds));
            assertTrue(oldBounds.width() > viewport.width() * 0.3f);
            assertTrue(oldBounds.width() < viewport.width() * 0.8f);
            TextView incoming = findText(weekPager(), "滑动测试·下周");
            assertNotNull(incoming);
            assertTrue(incoming.getGlobalVisibleRect(new Rect()));
            return null;
        });
        moveGesture(viewport.left + viewport.width() * 0.05f, card.exactCenterY(), 160);
        endGesture(MotionEvent.ACTION_UP);
        awaitSettled(4);
        assertEquals(0, editorMonitor.getHits());
    }

    @Test public void leftAndRightSwipesSelectTheCorrectCoursesAndKeepTheBackgroundFixed() {
        Rect background = read(() -> bounds(find(activity.getWindow().getDecorView(), ImageView.class)));
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/M/d", Locale.CHINA));
        swipe(-0.68f, 300);
        awaitSettled(4);
        read(() -> {
            assertNotNull(findText(currentGrid(), "滑动测试·下周"));
            assertNull(findText(currentGrid(), "滑动测试·本周"));
            assertNotNull(findText(activity.getWindow().getDecorView(), today));
            assertEquals(background, bounds(find(activity.getWindow().getDecorView(), ImageView.class)));
            return null;
        });
        swipe(0.68f, 300);
        awaitSettled(3);
        assertNotNull(read(() -> findText(currentGrid(), "滑动测试·本周")));
    }

    @Test public void slowShortDragReturnsToTheOriginalWeek() {
        swipe(-0.12f, 600);
        awaitSettled(3);
        assertEquals(0, editorMonitor.getHits());
    }

    @Test public void fastShortFlickAdvancesOneWeek() {
        swipe(-0.24f, 80);
        awaitSettled(4);
    }

    @Test public void verticalScrollKeepsTheWeekAndSurvivesHorizontalPaging() {
        Rect viewport = read(() -> bounds(weekPager()));
        Rect header = read(() -> bounds((View) currentGrid().getChildAt(0)));
        float x = viewport.left + viewport.width() * 0.5f;
        beginGesture(x, viewport.top + viewport.height() * 0.75f);
        moveGesture(x + 2, viewport.top + viewport.height() * 0.35f, 550);
        SystemClock.sleep(100);
        endGesture(MotionEvent.ACTION_UP);
        awaitSettled(3);
        int offset = read(() -> currentGrid().verticalScrollPosition());
        assertTrue("The vertical grid must scroll", offset > 100);
        assertEquals(header, read(() -> bounds((View) currentGrid().getChildAt(0))));
        swipe(-0.68f, 300);
        awaitSettled(4);
        assertEquals(offset, (int) read(() -> currentGrid().verticalScrollPosition()), 2);
        swipe(0.68f, 300);
        awaitSettled(3);
        assertEquals(offset, (int) read(() -> currentGrid().verticalScrollPosition()), 2);
    }

    @Test public void navigationButtonsAnimateAndTodayReturnsToTheCurrentWeek() {
        clickAndAssertAnimation("下一周");
        awaitSettled(4);
        clickAndAssertAnimation("上一周");
        awaitSettled(3);
        read(() -> { weekPager().showWeek(8); return null; });
        awaitSettled(8);
        clickAndAssertAnimation("回到本周");
        awaitSettled(3);
    }

    @Test public void rapidButtonPressesDoNotDropWeekChanges() {
        read(() -> {
            Button next = button("下一周");
            for (int i = 0; i < 6; i++) next.performClick();
            return null;
        });
        awaitSettled(9);
        clickAndAssertAnimation("上一周");
        awaitSettled(8);
    }

    @Test public void firstAndLastWeeksStayWithinTheBrowsingBounds() {
        read(() -> { weekPager().showWeek(1); return null; });
        awaitSettled(1);
        swipe(0.65f, 250);
        awaitSettled(1);
        read(() -> { button("上一周").performClick(); weekPager().showWeek(60); return null; });
        awaitSettled(60);
        swipe(-0.65f, 250);
        awaitSettled(60);
        read(() -> { button("下一周").performClick(); return null; });
        awaitSettled(60);
    }

    @Test public void cancelledShortDragSettlesWithoutOpeningACourse() {
        Rect viewport = read(() -> bounds(weekPager()));
        float x = viewport.left + viewport.width() * 0.85f;
        float y = viewport.top + viewport.height() * 0.3f;
        beginGesture(x, y);
        moveGesture(x - viewport.width() * 0.24f, y, 350);
        endGesture(MotionEvent.ACTION_CANCEL);
        awaitSettled(3);
        assertEquals(0, editorMonitor.getHits());
    }

    @Test public void activityRecreationRestoresTheViewedWeekAndScrollPosition() {
        read(() -> { weekPager().showWeek(7); return null; });
        awaitSettled(7);
        read(() -> { find(currentGrid(), ScrollView.class).scrollTo(0, 260); return null; });
        assertEquals(260, (int) read(() -> currentGrid().verticalScrollPosition()));
        MainActivity previous = activity;
        instrumentation.runOnMainSync(previous::recreate);
        activity = awaitReplacement(previous);
        awaitSettled(7);
        assertEquals(260, (int) read(() -> currentGrid().verticalScrollPosition()), 2);
    }

    @Test public void normalCourseTapStillOpensTheMatchingEditor() {
        instrumentation.removeMonitor(editorMonitor);
        editorMonitor = instrumentation.addMonitor(CourseEditorActivity.class.getName(), null, false);
        Rect card = read(() -> bounds(findText(currentGrid(), "滑动测试·本周")));
        beginGesture(card.exactCenterX(), card.exactCenterY());
        endGesture(MotionEvent.ACTION_UP);
        editor = instrumentation.waitForMonitorWithTimeout(editorMonitor, 5000);
        assertNotNull("A tap must still open the course editor", editor);
        assertEquals(currentCourse.id, editor.getIntent().getLongExtra(CourseEditorActivity.EXTRA_COURSE_ID, 0));
    }

    @Test public void resumingAfterAnEditRefreshesCoursesWithoutResettingTheViewedWeek() {
        clickAndAssertAnimation("下一周");
        awaitSettled(4);
        instrumentation.removeMonitor(editorMonitor);
        editorMonitor = instrumentation.addMonitor(CourseEditorActivity.class.getName(), null, false);
        Rect card = read(() -> bounds(findText(currentGrid(), "滑动测试·下周")));
        beginGesture(card.exactCenterX(), card.exactCenterY());
        endGesture(MotionEvent.ACTION_UP);
        editor = instrumentation.waitForMonitorWithTimeout(editorMonitor, 5000);
        assertNotNull(editor);
        addCourse("滑动测试·更新后", 4, 3, 2, 4, 4);
        instrumentation.runOnMainSync(editor::finish);
        instrumentation.waitForIdleSync();
        awaitSettled(4);
        assertNotNull(read(() -> findText(currentGrid(), "滑动测试·更新后")));
    }

    private Course addCourse(String name, int day, int node, int step, int firstWeek, int lastWeek) {
        Course course = new Course();
        course.name = name;
        course.location = "模拟教室";
        course.dayOfWeek = day;
        course.startNode = node;
        course.step = step;
        course.startWeek = firstWeek;
        course.endWeek = lastWeek;
        fixtureIds.add(database.save(course));
        return course;
    }

    private void clickAndAssertAnimation(String description) {
        read(() -> {
            button(description).performClick();
            assertEquals("Buttons must start a smooth transition", ViewPager2.SCROLL_STATE_SETTLING,
                    nativePager().getScrollState());
            return null;
        });
    }

    private Button button(String description) {
        View view = findDescription(activity.getWindow().getDecorView(), description);
        assertTrue(view instanceof Button);
        return (Button) view;
    }

    private ScheduleWeekPager weekPager() {
        return find(activity.getWindow().getDecorView(), ScheduleWeekPager.class);
    }

    private ViewPager2 nativePager() {
        return find(weekPager(), ViewPager2.class);
    }

    private ScheduleGridView currentGrid() {
        ViewPager2 pager = nativePager();
        RecyclerView recycler = (RecyclerView) pager.getChildAt(0);
        return (ScheduleGridView) recycler.getLayoutManager().findViewByPosition(pager.getCurrentItem());
    }

    private void awaitSettled(int week) {
        long deadline = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync();
            if (read(() -> nativePager().getScrollState() == ViewPager2.SCROLL_STATE_IDLE
                    && weekPager().selectedWeek() == week && currentGrid() != null
                    && currentGrid().getWidth() > 0 && !currentGrid().isLayoutRequested())) {
                SystemClock.sleep(60);
                assertNotNull(read(() -> findText(activity.getWindow().getDecorView(), "第 " + week + " 周")));
                return;
            }
            SystemClock.sleep(25);
        }
        fail("Expected a settled week " + week + "; current week is " + read(() -> weekPager().selectedWeek()));
    }

    private MainActivity awaitReplacement(MainActivity previous) {
        long deadline = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < deadline) {
            MainActivity replacement = read(() -> {
                for (Activity candidate : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (candidate instanceof MainActivity && candidate != previous) return (MainActivity) candidate;
                }
                return null;
            });
            if (replacement != null) return replacement;
            SystemClock.sleep(25);
        }
        throw new AssertionError("MainActivity did not resume after recreation");
    }

    private void swipe(float fraction, int durationMillis) {
        Rect viewport = read(() -> bounds(weekPager()));
        float x = viewport.left + viewport.width() * (fraction < 0 ? 0.85f : 0.15f);
        float y = viewport.top + viewport.height() * 0.3f;
        beginGesture(x, y);
        moveGesture(x + viewport.width() * fraction, y, durationMillis);
        if (durationMillis >= 500) SystemClock.sleep(100);
        endGesture(MotionEvent.ACTION_UP);
    }

    private void beginGesture(float x, float y) {
        gestureStart = SystemClock.uptimeMillis();
        lastX = x;
        lastY = y;
        pointerDown = true;
        send(MotionEvent.ACTION_DOWN, x, y);
    }

    private void moveGesture(float x, float y, int durationMillis) {
        float fromX = lastX, fromY = lastY;
        int frames = Math.max(4, durationMillis / 20);
        for (int i = 1; i <= frames; i++) {
            SystemClock.sleep(Math.max(1, durationMillis / frames));
            send(MotionEvent.ACTION_MOVE, fromX + (x - fromX) * i / frames,
                    fromY + (y - fromY) * i / frames);
        }
        lastX = x;
        lastY = y;
    }

    private void endGesture(int action) {
        send(action, lastX, lastY);
        pointerDown = false;
    }

    private void send(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(gestureStart, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        instrumentation.sendPointerSync(event);
        event.recycle();
    }

    private <T> T read(Supplier<T> supplier) {
        AtomicReference<T> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(supplier.get()));
        return result.get();
    }

    private static Rect bounds(View view) {
        assertNotNull(view);
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
    }

    private static TextView findText(View view, String prefix) {
        if (view instanceof TextView && ((TextView) view).getText().toString().startsWith(prefix)) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = find(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
