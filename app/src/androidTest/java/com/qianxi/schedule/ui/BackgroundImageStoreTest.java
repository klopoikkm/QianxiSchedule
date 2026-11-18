package com.qianxi.schedule.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import androidx.exifinterface.media.ExifInterface;

import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Pixel and file checks that need Android's real codecs and Canvas. */
public final class BackgroundImageStoreTest {
    private final List<File> files = new ArrayList<>();

    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test public void testPannedZoomedCropSavesExpectedPixels() throws Exception {
        Bitmap source = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(source);
        Paint paint = new Paint();
        int[] colors = {Color.RED, Color.GREEN, Color.BLUE};
        for (int i = 0; i < colors.length; i++) {
            paint.setColor(colors[i]);
            canvas.drawRect(i * 400, 0, (i + 1) * 400, 800, paint);
        }
        File crop = BackgroundImageStore.writeCrop(context(), source,
                new BackgroundCropTransform.State(2, 0.8, 0.5), 300, 600);
        files.add(crop);
        Bitmap output = BackgroundImageStore.decode(crop);
        assertEquals(300, output.getWidth());
        assertEquals(600, output.getHeight());
        assertColor(Color.BLUE, output.getPixel(150, 300));
        assertColor(Color.BLUE, output.getPixel(20, 20));
        source.recycle();
        output.recycle();
    }

    @Test public void testTransparentImageMatchesDefaultGradient() throws Exception {
        Bitmap source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        File crop = BackgroundImageStore.writeCrop(context(), source,
                new BackgroundCropTransform.State(1, 0.5, 0.5), 300, 600);
        files.add(crop);
        Bitmap output = BackgroundImageStore.decode(crop);
        assertColor(Color.rgb(135, 206, 235), output.getPixel(150, 1));
        assertColor(Color.rgb(176, 224, 230), output.getPixel(150, 598));
        source.recycle();
        output.recycle();
    }

    @Test public void testOutputDimensionsRemainBounded() throws Exception {
        Bitmap source = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.GREEN);
        File crop = BackgroundImageStore.writeCrop(context(), source,
                new BackgroundCropTransform.State(1, 0.5, 0.5), 8000, 12000);
        files.add(crop);
        Bitmap output = BackgroundImageStore.decode(crop);
        assertTrue(output.getWidth() <= BackgroundImageStore.MAX_OUTPUT_EDGE);
        assertTrue(output.getHeight() <= BackgroundImageStore.MAX_OUTPUT_EDGE);
        assertTrue((long) output.getWidth() * output.getHeight() <= BackgroundImageStore.MAX_OUTPUT_PIXELS);
        assertEquals(2.0 / 3.0, (double) output.getWidth() / output.getHeight(), 0.001);
        assertColor(Color.GREEN, output.getPixel(output.getWidth() / 2, output.getHeight() / 2));
        source.recycle();
        output.recycle();
    }

    @Test public void testModernDecoderHonorsAllExifOrientations() throws Exception {
        checkExifOrientations(false);
    }

    @Test public void testLegacyDecoderHonorsAllExifOrientations() throws Exception {
        checkExifOrientations(true);
    }

    private void checkExifOrientations(boolean legacy) throws Exception {
        Bitmap source = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(source);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        canvas.drawRect(0, 0, 60, 80, paint);
        paint.setColor(Color.BLUE);
        canvas.drawRect(60, 0, 120, 80, paint);
        for (int orientation = 1; orientation <= 8; orientation++) {
            File file = newFile(".jpg");
            try (FileOutputStream output = new FileOutputStream(file)) {
                source.compress(Bitmap.CompressFormat.JPEG, 100, output);
            }
            ExifInterface exif = new ExifInterface(file.getPath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(orientation));
            exif.saveAttributes();
            Bitmap decoded = legacy ? BackgroundImageStore.decodeLegacy(file) : BackgroundImageStore.decode(file);
            boolean swapsAxes = orientation >= 5;
            assertEquals(swapsAxes ? 80 : 120, decoded.getWidth());
            assertEquals(swapsAxes ? 120 : 80, decoded.getHeight());
            int first = swapsAxes ? decoded.getPixel(40, 25) : decoded.getPixel(25, 40);
            int last = swapsAxes ? decoded.getPixel(40, 95) : decoded.getPixel(95, 40);
            boolean redFirst = orientation == 1 || orientation == 4 || orientation == 5 || orientation == 6;
            assertColor(redFirst ? Color.RED : Color.BLUE, first);
            assertColor(redFirst ? Color.BLUE : Color.RED, last);
            decoded.recycle();
        }
        source.recycle();
    }

    @Test public void testPanoramaDecodeIsBounded() throws Exception {
        Bitmap source = Bitmap.createBitmap(12000, 200, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.BLUE);
        File file = newFile(".png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            source.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        Bitmap decoded = BackgroundImageStore.decode(file);
        assertTrue(decoded.getWidth() <= BackgroundImageStore.MAX_DECODE_EDGE);
        assertTrue((long) decoded.getWidth() * decoded.getHeight() <= BackgroundImageStore.MAX_DECODE_PIXELS);
        assertColor(Color.BLUE, decoded.getPixel(decoded.getWidth() / 2, decoded.getHeight() / 2));
        Bitmap legacy = BackgroundImageStore.decodeLegacy(file);
        assertTrue(legacy.getWidth() <= BackgroundImageStore.MAX_DECODE_EDGE);
        assertColor(Color.BLUE, legacy.getPixel(legacy.getWidth() / 2, legacy.getHeight() / 2));
        source.recycle();
        decoded.recycle();
        legacy.recycle();
    }

    @Test public void testInvalidImageFailsWithoutChangingCurrentFiles() throws Exception {
        File current = newFile(".img");
        byte[] original = "existing background".getBytes(StandardCharsets.UTF_8);
        Files.write(current.toPath(), original);
        try {
            BackgroundImageStore.decode(current);
            fail("Invalid image should fail to decode");
        } catch (IOException expected) {
            assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(current.toPath())));
        }
    }

    @Test public void testOversizedCopyLeavesTargetIntactAndRemovesPartialFile() throws Exception {
        File source = newFile(".img");
        byte[] megabyte = new byte[1024 * 1024];
        try (FileOutputStream output = new FileOutputStream(source)) {
            for (int i = 0; i < 33; i++) output.write(megabyte);
        }
        File target = BackgroundImageStore.newSource(context());
        files.add(target);
        byte[] original = "existing background".getBytes(StandardCharsets.UTF_8);
        Files.write(target.toPath(), original);
        try {
            BackgroundImageStore.copySource(context().getContentResolver(), Uri.fromFile(source), target);
            fail("Oversized image should fail to copy");
        } catch (IOException expected) {
            assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(target.toPath())));
            assertFalse(new File(target.getPath() + ".pending").exists());
        }
    }

    private File newFile(String extension) throws IOException {
        File file = File.createTempFile("background-source-test-", extension, context().getFilesDir());
        files.add(file);
        return file;
    }

    private static void assertColor(int expected, int actual) {
        assertTrue("red: " + Color.red(actual), Math.abs(Color.red(expected) - Color.red(actual)) < 10);
        assertTrue("green: " + Color.green(actual), Math.abs(Color.green(expected) - Color.green(actual)) < 10);
        assertTrue("blue: " + Color.blue(actual), Math.abs(Color.blue(expected) - Color.blue(actual)) < 10);
    }

    @After public void tearDown() {
        for (File file : files) file.delete();
    }
}
