package com.qianxi.schedule.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Bounded image decoding and private, complete files; the active background is never overwritten. */
public final class BackgroundImageStore {
    static final int MAX_SOURCE_BYTES = 32 * 1024 * 1024;
    static final int MAX_DECODE_EDGE = 4096;
    static final int MAX_DECODE_PIXELS = 8 * 1024 * 1024;
    static final int MAX_OUTPUT_EDGE = 2560;
    static final int MAX_OUTPUT_PIXELS = 4 * 1024 * 1024;

    private BackgroundImageStore() {}

    public static File newSource(Context context) {
        return new File(context.getFilesDir(), "background-source-" + UUID.randomUUID() + ".img");
    }

    public static void copySource(ContentResolver resolver, Uri uri, File target) throws IOException {
        File pending = new File(target.getPath() + ".pending");
        try {
            try (InputStream input = resolver.openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(pending)) {
                if (input == null) throw new IOException("无法读取所选图片");
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_SOURCE_BYTES) throw new IOException("图片过大（超过 32MB）");
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (!pending.renameTo(target)) throw new IOException("无法保存原图");
        } finally {
            if (pending.exists()) pending.delete();
        }
    }

    public static Bitmap decode(File file) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file), (decoder, info, source) -> {
                int width = info.getSize().getWidth();
                int height = info.getSize().getHeight();
                double factor = sizeFactor(width, height, MAX_DECODE_EDGE, MAX_DECODE_PIXELS);
                decoder.setTargetSize(Math.max(1, (int) (width * factor)), Math.max(1, (int) (height * factor)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB));
            });
        }
        return decodeLegacy(file);
    }

    static Bitmap decodeLegacy(File file) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("不支持或已损坏的图片");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while ((long) Math.ceil((double) bounds.outWidth / options.inSampleSize)
                * (long) Math.ceil((double) bounds.outHeight / options.inSampleSize) > MAX_DECODE_PIXELS
                || Math.ceil((double) Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize) > MAX_DECODE_EDGE) {
            options.inSampleSize *= 2;
        }
        Bitmap image = BitmapFactory.decodeFile(file.getPath(), options);
        if (image == null) throw new IOException("不支持或已损坏的图片");
        // BitmapFactory on Android 8 does not apply the phone camera's EXIF orientation.
        Matrix orientation = new Matrix();
        try {
            ExifInterface exif = new ExifInterface(file.getPath());
            switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: orientation.setScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: orientation.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: orientation.setScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    orientation.setRotate(90); orientation.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: orientation.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    orientation.setRotate(-90); orientation.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: orientation.setRotate(-90); break;
                default: break;
            }
        } catch (IOException ignored) {
            // Formats without EXIF still have a valid decoded bitmap.
        }
        if (orientation.isIdentity()) return image;
        Bitmap rotated = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), orientation, true);
        if (rotated != image) image.recycle();
        return rotated;
    }

    public static File writeCrop(Context context, Bitmap image, BackgroundCropTransform.State state,
                                 int width, int height) throws IOException {
        if (width <= 0 || height <= 0) throw new IOException("无法确定课表尺寸");
        double factor = sizeFactor(width, height, MAX_OUTPUT_EDGE, MAX_OUTPUT_PIXELS);
        int outputWidth = Math.max(1, (int) (width * factor));
        int outputHeight = Math.max(1, (int) (height * factor));
        BackgroundCropTransform transform = new BackgroundCropTransform(image.getWidth(), image.getHeight());
        transform.setViewport(outputWidth, outputHeight);
        transform.restore(state);
        Bitmap result = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
        File target = File.createTempFile("background-crop-", ".jpg", context.getFilesDir());
        boolean complete = false;
        try {
            Canvas canvas = new Canvas(result);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setShader(new LinearGradient(0, 0, 0, outputHeight,
                    Color.rgb(135, 206, 235), Color.rgb(176, 224, 230), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, outputWidth, outputHeight, paint);
            paint.setShader(null);
            Matrix matrix = new Matrix();
            matrix.setScale((float) transform.scale(), (float) transform.scale());
            matrix.postTranslate((float) transform.translationX(), (float) transform.translationY());
            canvas.drawBitmap(image, matrix, paint);
            try (FileOutputStream output = new FileOutputStream(target)) {
                if (!result.compress(Bitmap.CompressFormat.JPEG, 94, output)) throw new IOException("裁剪保存失败");
                output.getFD().sync();
            }
            complete = true;
            return target;
        } finally {
            result.recycle();
            if (!complete) target.delete();
        }
    }

    private static double sizeFactor(int width, int height, int edge, int pixels) {
        return Math.min(1.0, Math.min((double) edge / Math.max(width, height),
                Math.sqrt((double) pixels / ((double) width * height))));
    }

    /** Only remove images owned by this feature, never an arbitrary path from preferences. */
    public static void deleteOwned(Context context, String path) {
        if (path == null || path.isEmpty()) return;
        File file = new File(path);
        try {
            String name = file.getName();
            if (context.getFilesDir().getCanonicalFile().equals(file.getParentFile().getCanonicalFile())
                    && (name.equals("background.img") || name.startsWith("background-source-")
                    || name.startsWith("background-crop-"))) {
                file.delete();
                new File(file.getPath() + ".pending").delete();
            }
        } catch (IOException | NullPointerException ignored) {
        }
    }
}
