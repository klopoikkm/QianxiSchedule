package com.qianxi.schedule.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.SeekBar;
import android.widget.TextView;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.qianxi.schedule.data.AppSettings;

import java.io.File;
import java.io.FileOutputStream;

/** Exercise touch dispatch, confirmation and reopening with Android's actual activity lifecycle. */
public final class BackgroundEditorActivityTest {
    private AppSettings settings;
    private String oldPath;
    private String oldSource;
    private float oldZoom;
    private float oldX;
    private float oldY;
    private File source;
    private File initialCrop;
    private BackgroundEditorActivity activity;
    private File invalidImage;

    private android.app.Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    @Before public void setUp() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        settings = new AppSettings(context);
        oldPath = settings.backgroundPath();
        oldSource = settings.backgroundSourcePath();
        oldZoom = settings.backgroundZoom();
        oldX = settings.backgroundFocusX();
        oldY = settings.backgroundFocusY();
        Bitmap image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.rgb(50, 150, 210));
        source = BackgroundImageStore.newSource(context);
        try (FileOutputStream output = new FileOutputStream(source)) {
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        initialCrop = BackgroundImageStore.writeCrop(context, image,
                new BackgroundCropTransform.State(1, 0.5, 0.5), 1080, 2400);
        image.recycle();
        assertTrue(settings.saveBackground(initialCrop.getPath(), source.getPath(), 1, 0.5f, 0.5f));
    }

    @Test public void testCancelKeepsSavedBackgroundAndCropState() throws Exception {
        openEditor();
        BackgroundCropView crop = awaitImage();
        getInstrumentation().runOnMainSync(() -> crop.setZoom(4));
        click("取消背景编辑并返回");
        getInstrumentation().waitForIdleSync();
        assertEquals(initialCrop.getPath(), settings.backgroundPath());
        assertEquals(1f, settings.backgroundZoom(), 0.001f);
        assertTrue(initialCrop.isFile());
        assertTrue(source.isFile());
    }

    @Test public void testPinchInSchedulePreviewSavesAndReopensTheSameCrop() throws Exception {
        openEditor();
        BackgroundCropView crop = awaitImage();
        getInstrumentation().runOnMainSync(() -> find(activity.getWindow().getDecorView(), Switch.class).setChecked(true));
        pinch(crop);
        getInstrumentation().waitForIdleSync();
        BackgroundCropTransform.State pinched = crop.cropState();
        assertTrue("Pinch must zoom the image while the schedule overlay is visible", pinched.zoom > 1.2);
        int[] position = new int[2];
        crop.getLocationOnScreen(position);
        float x = position[0] + crop.cropBounds().centerX();
        float y = position[1] + crop.cropBounds().centerY();
        tapOrDrag(x, y, x + 100, y - 50);
        getInstrumentation().waitForIdleSync();
        BackgroundCropTransform.State state = crop.cropState();
        assertTrue("Dragging in preview must move the image", Math.abs(state.focusX - pinched.focusX) > 0.001);
        assertEquals(initialCrop.getPath(), settings.backgroundPath());
        click("保存裁剪后的背景图片");
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (settings.backgroundPath().equals(initialCrop.getPath()) && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50);
        }
        getInstrumentation().waitForIdleSync();
        assertFalse(initialCrop.getPath().equals(settings.backgroundPath()));
        assertTrue(new File(settings.backgroundPath()).isFile());
        assertTrue(source.isFile());
        assertEquals(source.getPath(), settings.backgroundSourcePath());
        assertEquals(state.zoom, settings.backgroundZoom(), 0.001);
        assertFalse(initialCrop.exists());
        String savedPath = settings.backgroundPath();
        openEditor();
        BackgroundCropView reopened = awaitImage();
        assertEquals(state.zoom, reopened.cropState().zoom, 0.001);
        assertEquals(state.focusX, reopened.cropState().focusX, 0.001);
        assertEquals(state.focusY, reopened.cropState().focusY, 0.001);
        getInstrumentation().runOnMainSync(reopened::reset);
        click("取消背景编辑并返回");
        assertEquals(savedPath, settings.backgroundPath());
        assertEquals(state.zoom, settings.backgroundZoom(), 0.001);
    }

    @Test public void testZoomSliderAndResetKeepSettingsUntilConfirmation() {
        openEditor();
        BackgroundCropView crop = awaitImage();
        SeekBar slider = find(activity.getWindow().getDecorView(), SeekBar.class);
        int[] position = new int[2];
        slider.getLocationOnScreen(position);
        float x = position[0] + slider.getWidth() * 0.6f;
        float y = position[1] + slider.getHeight() / 2f;
        tapOrDrag(x, y, x, y);
        getInstrumentation().waitForIdleSync();
        assertTrue("Slider touches must update the visible zoom", crop.cropState().zoom > 2);
        assertEquals(initialCrop.getPath(), settings.backgroundPath());
        click("重置背景位置与缩放");
        assertEquals(1, crop.cropState().zoom, 0.001);
        assertEquals(0.5, crop.cropState().focusX, 0.001);
        assertEquals(0.5, crop.cropState().focusY, 0.001);
    }

    @Test public void testCancelledPickerPreservesTheImageAndPendingEdits() throws Exception {
        openEditor();
        BackgroundCropView crop = awaitImage();
        Bitmap image = crop.image();
        getInstrumentation().runOnMainSync(() -> crop.setZoom(2));
        Instrumentation.ActivityMonitor picker = monitorPicker(Activity.RESULT_CANCELED, null);
        try {
            click("重新选择背景图片");
            getInstrumentation().waitForIdleSync();
            assertEquals(1, picker.getHits());
            assertSame(image, crop.image());
            assertEquals(2, crop.cropState().zoom, 0.001);
            assertEquals(initialCrop.getPath(), settings.backgroundPath());
        } finally {
            getInstrumentation().removeMonitor(picker);
        }
    }

    @Test public void testSelectedImageIsCopiedAndOnlyAppliedOnConfirmation() throws Exception {
        openEditor();
        BackgroundCropView crop = awaitImage();
        Bitmap previous = crop.image();
        Instrumentation.ActivityMonitor picker = monitorPicker(Activity.RESULT_OK, Uri.fromFile(source));
        try {
            click("重新选择背景图片");
            long deadline = SystemClock.uptimeMillis() + 15000;
            while (crop.image() == previous && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50);
                getInstrumentation().waitForIdleSync();
            }
            assertEquals(1, picker.getHits());
            assertNotSame(previous, crop.image());
            assertEquals(initialCrop.getPath(), settings.backgroundPath());
            click("保存裁剪后的背景图片");
            while (settings.backgroundPath().equals(initialCrop.getPath()) && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50);
            }
            getInstrumentation().waitForIdleSync();
            assertFalse(initialCrop.getPath().equals(settings.backgroundPath()));
            assertFalse(source.getPath().equals(settings.backgroundSourcePath()));
            assertTrue(new File(settings.backgroundSourcePath()).isFile());
        } finally {
            getInstrumentation().removeMonitor(picker);
        }
    }

    @Test public void testInvalidSelectionKeepsTheSavedAndPreviewImages() throws Exception {
        openEditor();
        BackgroundCropView crop = awaitImage();
        Bitmap previous = crop.image();
        invalidImage = File.createTempFile("invalid-image-test-", ".img", source.getParentFile());
        try (FileOutputStream output = new FileOutputStream(invalidImage)) {
            output.write(new byte[]{1, 2, 3, 4});
        }
        Instrumentation.ActivityMonitor picker = monitorPicker(Activity.RESULT_OK, Uri.fromFile(invalidImage));
        try {
            click("重新选择背景图片");
            long deadline = SystemClock.uptimeMillis() + 15000;
            while (!hasText(activity.getWindow().getDecorView(), "无法读取图片，请重新选择")
                    && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50);
                getInstrumentation().waitForIdleSync();
            }
            assertEquals(1, picker.getHits());
            assertTrue(hasText(activity.getWindow().getDecorView(), "无法读取图片，请重新选择"));
            assertSame(previous, crop.image());
            assertEquals(initialCrop.getPath(), settings.backgroundPath());
            assertTrue(initialCrop.isFile());
        } finally {
            getInstrumentation().removeMonitor(picker);
        }
    }

    private Instrumentation.ActivityMonitor monitorPicker(int resultCode, Uri uri) throws Exception {
        IntentFilter filter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
        filter.addCategory(Intent.CATEGORY_OPENABLE);
        filter.addDataType("image/*");
        Intent result = uri == null ? null : new Intent().setData(uri);
        return getInstrumentation().addMonitor(filter, new Instrumentation.ActivityResult(resultCode, result), true);
    }

    private void openEditor() {
        Intent intent = new Intent(getInstrumentation().getTargetContext(), BackgroundEditorActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (BackgroundEditorActivity) getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
    }

    private BackgroundCropView awaitImage() {
        long deadline = SystemClock.uptimeMillis() + 15000;
        BackgroundCropView crop = find(activity.getWindow().getDecorView(), BackgroundCropView.class);
        while (crop.image() == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50);
            getInstrumentation().waitForIdleSync();
        }
        assertNotNull("Editor must load the retained source image", crop.image());
        assertTrue(crop.cropBounds().width() > 0);
        return crop;
    }

    private void click(String description) {
        getInstrumentation().runOnMainSync(() -> {
            Button button = findButton(activity.getWindow().getDecorView(), description);
            assertNotNull(description, button);
            assertTrue(description, button.isEnabled());
            button.performClick();
        });
    }

    private void pinch(BackgroundCropView crop) {
        int[] position = new int[2];
        crop.getLocationOnScreen(position);
        RectF bounds = crop.cropBounds();
        float x = position[0] + bounds.centerX();
        float y = position[1] + bounds.centerY();
        long down = SystemClock.uptimeMillis();
        pointer(down, MotionEvent.ACTION_DOWN, 1, x, y, 40);
        pointer(down, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, x, y, 40);
        for (int i = 1; i <= 8; i++) {
            SystemClock.sleep(30);
            pointer(down, MotionEvent.ACTION_MOVE, 2, x, y, 40 + i * 10);
        }
        pointer(down, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, x, y, 120);
        pointer(down, MotionEvent.ACTION_UP, 1, x, y, 120);
    }

    private void pointer(long down, int action, int count, float x, float y, float span) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = x + (i == 0 ? -span : span);
            coords[i].y = y;
            coords[i].pressure = 1;
            coords[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count, properties, coords,
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        getInstrumentation().sendPointerSync(event);
        event.recycle();
    }

    private void tapOrDrag(float startX, float startY, float endX, float endY) {
        long down = SystemClock.uptimeMillis();
        int[] actions = {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP};
        for (int action : actions) {
            SystemClock.sleep(30);
            boolean start = action == MotionEvent.ACTION_DOWN;
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    start ? startX : endX, start ? startY : endY, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            getInstrumentation().sendPointerSync(event);
            event.recycle();
        }
    }

    private static boolean hasText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasText(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T child = find(group.getChildAt(i), type);
                if (child != null) return child;
            }
        }
        return null;
    }

    private static Button findButton(View view, String description) {
        if (view instanceof Button && description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button child = findButton(group.getChildAt(i), description);
                if (child != null) return child;
            }
        }
        return null;
    }

    @After public void tearDown() {
        if (activity != null && !activity.isFinishing()) getInstrumentation().runOnMainSync(activity::finish);
        getInstrumentation().waitForIdleSync();
        String currentPath = settings.backgroundPath();
        String currentSource = settings.backgroundSourcePath();
        settings.saveBackground(oldPath, oldSource, oldZoom, oldX, oldY);
        BackgroundImageStore.deleteOwned(getInstrumentation().getTargetContext(), currentPath);
        BackgroundImageStore.deleteOwned(getInstrumentation().getTargetContext(), currentSource);
        BackgroundImageStore.deleteOwned(getInstrumentation().getTargetContext(), source.getPath());
        BackgroundImageStore.deleteOwned(getInstrumentation().getTargetContext(), initialCrop.getPath());
        if (invalidImage != null) invalidImage.delete();
    }
}
