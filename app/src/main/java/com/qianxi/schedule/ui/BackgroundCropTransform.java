package com.qianxi.schedule.ui;

/** Image-space focus and zoom shared by the preview and the saved crop. */
public final class BackgroundCropTransform {
    public static final double MAX_ZOOM = 5.0;

    public static final class State {
        public final double zoom;
        public final double focusX;
        public final double focusY;

        public State(double zoom, double focusX, double focusY) {
            this.zoom = zoom;
            this.focusX = focusX;
            this.focusY = focusY;
        }
    }

    private final int imageWidth;
    private final int imageHeight;
    private double width;
    private double height;
    private double zoom = 1.0;
    private double focusX = 0.5;
    private double focusY = 0.5;

    public BackgroundCropTransform(int imageWidth, int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) throw new IllegalArgumentException("Empty image");
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        width = imageWidth;
        height = imageHeight;
    }

    public void setViewport(double width, double height) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Empty viewport");
        }
        this.width = width;
        this.height = height;
        clampFocus();
    }

    public State state() {
        return new State(zoom, focusX, focusY);
    }

    public void restore(State state) {
        zoom = finiteClamp(state.zoom, 1.0, MAX_ZOOM, 1.0);
        focusX = finiteClamp(state.focusX, 0.0, 1.0, 0.5);
        focusY = finiteClamp(state.focusY, 0.0, 1.0, 0.5);
        clampFocus();
    }

    public double scale() {
        return Math.max(width / imageWidth, height / imageHeight) * zoom;
    }

    public double translationX() {
        return width / 2.0 - imageWidth * focusX * scale();
    }

    public double translationY() {
        return height / 2.0 - imageHeight * focusY * scale();
    }

    public void pan(double dx, double dy) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return;
        focusX -= dx / (imageWidth * scale());
        focusY -= dy / (imageHeight * scale());
        clampFocus();
    }

    /** Keeps the image point under the fingers stationary, until an image edge is reached. */
    public void zoomTo(double value, double anchorX, double anchorY) {
        if (!Double.isFinite(value) || !Double.isFinite(anchorX) || !Double.isFinite(anchorY)) return;
        double oldScale = scale();
        zoom = finiteClamp(value, 1.0, MAX_ZOOM, 1.0);
        double delta = 1.0 / oldScale - 1.0 / scale();
        focusX += (anchorX - width / 2.0) * delta / imageWidth;
        focusY += (anchorY - height / 2.0) * delta / imageHeight;
        clampFocus();
    }

    public void setZoom(double value) {
        zoomTo(value, width / 2.0, height / 2.0);
    }

    public void reset() {
        restore(new State(1.0, 0.5, 0.5));
    }

    private void clampFocus() {
        double halfX = Math.min(0.5, width / (2.0 * imageWidth * scale()));
        double halfY = Math.min(0.5, height / (2.0 * imageHeight * scale()));
        focusX = Math.max(halfX, Math.min(1.0 - halfX, focusX));
        focusY = Math.max(halfY, Math.min(1.0 - halfY, focusY));
    }

    private static double finiteClamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
