package com.qianxi.schedule.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BackgroundCropTransformTest {
    private static final double EPSILON = 0.00001;

    @Test public void landscapePhotoStartsWithACenteredPortraitCrop() {
        BackgroundCropTransform crop = crop(4000, 2000, 1000, 2000);
        assertEquals(1, crop.scale(), EPSILON);
        assertEquals(-1500, crop.translationX(), EPSILON);
        assertEquals(0, crop.translationY(), EPSILON);
    }

    @Test public void portraitPhotoCoversALandscapeWindow() {
        BackgroundCropTransform crop = crop(2000, 4000, 2000, 1000);
        assertEquals(1, crop.scale(), EPSILON);
        assertEquals(0, crop.translationX(), EPSILON);
        assertEquals(-1500, crop.translationY(), EPSILON);
    }

    @Test public void panningStopsAtEveryImageEdge() {
        BackgroundCropTransform crop = crop(4000, 2000, 1000, 2000);
        crop.setZoom(2);
        crop.pan(1e9, 1e9);
        assertEquals(0, crop.translationX(), EPSILON);
        assertEquals(0, crop.translationY(), EPSILON);
        crop.pan(-1e9, -1e9);
        assertEquals(1000 - 4000 * crop.scale(), crop.translationX(), EPSILON);
        assertEquals(2000 - 2000 * crop.scale(), crop.translationY(), EPSILON);
    }

    @Test public void pinchKeepsTheImagePointUnderTheFingers() {
        BackgroundCropTransform crop = crop(4000, 2000, 1000, 2000);
        double x = (300 - crop.translationX()) / crop.scale();
        double y = (800 - crop.translationY()) / crop.scale();
        crop.zoomTo(2, 300, 800);
        assertEquals(x, (300 - crop.translationX()) / crop.scale(), EPSILON);
        assertEquals(y, (800 - crop.translationY()) / crop.scale(), EPSILON);
    }

    @Test public void zoomIsBoundedAndCannotRevealEmptySpace() {
        BackgroundCropTransform crop = crop(4000, 2000, 1000, 2000);
        crop.setZoom(100);
        assertEquals(5, crop.state().zoom, EPSILON);
        crop.pan(-1e9, 1e9);
        crop.setZoom(0.1);
        assertEquals(1, crop.state().zoom, EPSILON);
        assertCoverage(crop, 4000, 2000, 1000, 2000);
    }

    @Test public void sliderZoomKeepsTheVisibleCenter() {
        BackgroundCropTransform crop = crop(4000, 2000, 1000, 2000);
        crop.setZoom(2);
        crop.pan(-300, -400);
        BackgroundCropTransform.State before = crop.state();
        crop.setZoom(3);
        assertEquals(before.focusX, crop.state().focusX, EPSILON);
        assertEquals(before.focusY, crop.state().focusY, EPSILON);
    }

    @Test public void savingAtScreenResolutionKeepsTheSameSourceRegion() {
        BackgroundCropTransform preview = crop(4000, 2000, 180, 360);
        preview.setZoom(2.7);
        preview.pan(-45, 20);
        BackgroundCropTransform output = crop(4000, 2000, 1080, 2160);
        output.restore(preview.state());
        assertEquals(-preview.translationX() / preview.scale(),
                -output.translationX() / output.scale(), EPSILON);
        assertEquals(-preview.translationY() / preview.scale(),
                -output.translationY() / output.scale(), EPSILON);
        assertEquals(180 / preview.scale(), 1080 / output.scale(), EPSILON);
        assertEquals(360 / preview.scale(), 2160 / output.scale(), EPSILON);
    }

    @Test public void reeditingADifferentDecodeSizeRestoresTheNormalizedRegion() {
        BackgroundCropTransform original = crop(4000, 2000, 180, 360);
        original.setZoom(2.7);
        original.pan(-45, 20);
        BackgroundCropTransform smaller = crop(2000, 1000, 180, 360);
        smaller.restore(original.state());
        assertEquals(original.state().focusX, smaller.state().focusX, EPSILON);
        assertEquals(original.state().focusY, smaller.state().focusY, EPSILON);
        assertEquals(original.translationX(), smaller.translationX(), EPSILON);
        assertEquals(original.translationY(), smaller.translationY(), EPSILON);
    }

    @Test public void rotationRetainsZoomAndClampsTheNewAspectRatio() {
        BackgroundCropTransform crop = crop(4000, 2000, 1000, 2000);
        crop.setZoom(3);
        crop.pan(-1e9, 1e9);
        crop.setViewport(2000, 1000);
        assertEquals(3, crop.state().zoom, EPSILON);
        assertCoverage(crop, 4000, 2000, 2000, 1000);
    }

    @Test public void resetReturnsToTheCenteredMinimumCrop() {
        BackgroundCropTransform crop = crop(4000, 2000, 1000, 2000);
        crop.setZoom(4);
        crop.pan(-1e9, 1e9);
        crop.reset();
        assertEquals(1, crop.state().zoom, EPSILON);
        assertEquals(0.5, crop.state().focusX, EPSILON);
        assertEquals(0.5, crop.state().focusY, EPSILON);
    }

    @Test public void invalidSavedValuesFallBackToACenteredCrop() {
        BackgroundCropTransform crop = crop(4000, 2000, 1000, 2000);
        crop.restore(new BackgroundCropTransform.State(Double.NaN, Double.POSITIVE_INFINITY, Double.NaN));
        assertEquals(1, crop.state().zoom, EPSILON);
        assertEquals(0.5, crop.state().focusX, EPSILON);
        assertEquals(0.5, crop.state().focusY, EPSILON);
        crop.zoomTo(Double.POSITIVE_INFINITY, 0, 0);
        crop.pan(Double.NaN, 10);
        assertCoverage(crop, 4000, 2000, 1000, 2000);
    }

    @Test public void arbitraryPhotoShapesAndGesturesAlwaysCoverTheCropWindow() {
        int[][] images = {{6000, 100}, {100, 6000}, {1, 1}, {4000, 3000}, {3000, 4000}};
        int[][] windows = {{1080, 2400}, {2400, 1080}, {500, 500}};
        for (int[] image : images) {
            for (int[] window : windows) {
                BackgroundCropTransform crop = crop(image[0], image[1], window[0], window[1]);
                for (int i = 0; i < 100; i++) {
                    crop.zoomTo(0.5 + i % 7, window[0] * (i % 4) / 3.0, window[1] * (i % 5) / 4.0);
                    crop.pan((i % 2 == 0 ? 1 : -1) * 5000, (i % 3 == 0 ? 1 : -1) * 3000);
                    assertCoverage(crop, image[0], image[1], window[0], window[1]);
                }
            }
        }
    }

    private static BackgroundCropTransform crop(int imageWidth, int imageHeight, int width, int height) {
        BackgroundCropTransform crop = new BackgroundCropTransform(imageWidth, imageHeight);
        crop.setViewport(width, height);
        return crop;
    }

    private static void assertCoverage(BackgroundCropTransform crop, int imageWidth, int imageHeight,
                                       int width, int height) {
        assertTrue(crop.translationX() <= EPSILON);
        assertTrue(crop.translationY() <= EPSILON);
        assertTrue(crop.translationX() + imageWidth * crop.scale() >= width - EPSILON);
        assertTrue(crop.translationY() + imageHeight * crop.scale() >= height - EPSILON);
    }
}
