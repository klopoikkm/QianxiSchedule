package com.qianxi.schedule.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** A screen-shaped crop window; images always cover it, even at the minimum zoom. */
public final class BackgroundCropView extends View {
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint skyPaint = new Paint();
    private final RectF cropBounds = new RectF();
    private final Matrix matrix = new Matrix();
    private Bitmap image;
    private BackgroundCropTransform transform;
    private double aspectRatio = 0.5;
    private Runnable onChange;
    private boolean showGuides = true;
    private int activePointer = MotionEvent.INVALID_POINTER_ID;
    private float lastX;
    private float lastY;
    private float lastSpan;
    private float pinchX;
    private float pinchY;

    public BackgroundCropView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(27, 30, 33));
        setContentDescription("背景图片裁剪区域，可拖动图片或用双指缩放");
        framePaint.setStyle(Paint.Style.STROKE);
    }

    public void setOnChange(Runnable listener) {
        onChange = listener;
    }

    public void setAspectRatio(int width, int height) {
        aspectRatio = (double) Math.max(1, width) / Math.max(1, height);
        updateCropBounds();
    }

    public void setImage(Bitmap image, BackgroundCropTransform.State state) {
        this.image = image;
        transform = new BackgroundCropTransform(image.getWidth(), image.getHeight());
        if (!cropBounds.isEmpty()) transform.setViewport(cropBounds.width(), cropBounds.height());
        transform.restore(state);
        changed();
    }

    public Bitmap image() {
        return image;
    }

    public BackgroundCropTransform.State cropState() {
        return transform == null ? new BackgroundCropTransform.State(1, 0.5, 0.5) : transform.state();
    }

    public RectF cropBounds() {
        return new RectF(cropBounds);
    }

    public void setZoom(double zoom) {
        if (transform == null) return;
        transform.setZoom(zoom);
        changed();
    }

    public void reset() {
        if (transform == null) return;
        transform.reset();
        changed();
    }

    public void setShowGuides(boolean show) {
        showGuides = show;
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        updateCropBounds();
    }

    private void updateCropBounds() {
        float margin = Ui.dp(getContext(), 16);
        float availableWidth = Math.max(1, getWidth() - 2 * margin);
        float availableHeight = Math.max(1, getHeight() - 2 * margin);
        float width = (float) Math.min(availableWidth, availableHeight * aspectRatio);
        float height = (float) (width / aspectRatio);
        cropBounds.set((getWidth() - width) / 2, (getHeight() - height) / 2,
                (getWidth() + width) / 2, (getHeight() + height) / 2);
        skyPaint.setShader(new LinearGradient(0, cropBounds.top, 0, cropBounds.bottom,
                Color.rgb(135, 206, 235), Color.rgb(176, 224, 230), Shader.TileMode.CLAMP));
        if (transform != null) transform.setViewport(width, height);
        changed();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (image == null || cropBounds.isEmpty()) return;
        int save = canvas.save();
        canvas.clipRect(cropBounds);
        canvas.drawRect(cropBounds, skyPaint);
        matrix.setScale((float) transform.scale(), (float) transform.scale());
        matrix.postTranslate(cropBounds.left + (float) transform.translationX(),
                cropBounds.top + (float) transform.translationY());
        canvas.drawBitmap(image, matrix, imagePaint);
        canvas.restoreToCount(save);
        if (showGuides) {
            framePaint.setColor(Color.argb(100, 255, 255, 255));
            framePaint.setStrokeWidth(Ui.dp(getContext(), 1));
            for (int i = 1; i < 3; i++) {
                float x = cropBounds.left + cropBounds.width() * i / 3;
                float y = cropBounds.top + cropBounds.height() * i / 3;
                canvas.drawLine(x, cropBounds.top, x, cropBounds.bottom, framePaint);
                canvas.drawLine(cropBounds.left, y, cropBounds.right, y, framePaint);
            }
        }
        framePaint.setColor(Color.WHITE);
        framePaint.setStrokeWidth(Ui.dp(getContext(), 1));
        canvas.drawRect(cropBounds, framePaint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (transform == null || !isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointer = event.getPointerId(0);
                lastX = event.getX();
                lastY = event.getY();
                lastSpan = 0;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                recordPinch(event, 0, 1);
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) {
                    float focusX = (event.getX(0) + event.getX(1)) / 2;
                    float focusY = (event.getY(0) + event.getY(1)) / 2;
                    float span = span(event, 0, 1);
                    // Android's full-screen minimum span is too large for this smaller preview.
                    float minimumSpan = Ui.dp(getContext(), 12);
                    if (lastSpan >= minimumSpan && span >= minimumSpan) {
                        transform.pan(focusX - pinchX, focusY - pinchY);
                        transform.zoomTo(transform.state().zoom * span / lastSpan,
                                focusX - cropBounds.left, focusY - cropBounds.top);
                        changed();
                    }
                    recordPinch(event, 0, 1);
                    break;
                }
                int index = event.findPointerIndex(activePointer);
                if (index >= 0) {
                    float x = event.getX(index);
                    float y = event.getY(index);
                    transform.pan(x - lastX, y - lastY);
                    changed();
                    lastX = x;
                    lastY = y;
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                int lifted = event.getActionIndex();
                int remaining = lifted == 0 ? 1 : 0;
                activePointer = event.getPointerId(remaining);
                lastX = event.getX(remaining);
                lastY = event.getY(remaining);
                lastSpan = 0;
                if (event.getPointerCount() > 2) {
                    int first = lifted == 0 ? 1 : 0;
                    int second = lifted <= 1 ? 2 : 1;
                    recordPinch(event, first, second);
                }
                break;
            case MotionEvent.ACTION_UP:
                performClick();
                // fall through
            case MotionEvent.ACTION_CANCEL:
                activePointer = MotionEvent.INVALID_POINTER_ID;
                lastSpan = 0;
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            default:
                break;
        }
        return true;
    }

    private void recordPinch(MotionEvent event, int first, int second) {
        lastSpan = span(event, first, second);
        pinchX = (event.getX(first) + event.getX(second)) / 2;
        pinchY = (event.getY(first) + event.getY(second)) / 2;
    }

    private static float span(MotionEvent event, int first, int second) {
        return (float) Math.hypot(event.getX(first) - event.getX(second), event.getY(first) - event.getY(second));
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void changed() {
        invalidate();
        if (onChange != null) onChange.run();
    }
}
