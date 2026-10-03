package com.example.chatapp.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Compresses images before upload.
 * Longest edge is capped at MAX_DIMENSION (1280px), JPEG quality 80.
 */
public class ImageCompressor {

    private static final int MAX_DIMENSION = 1280;
    private static final int JPEG_QUALITY = 80;

    /**
     * Compresses the image at the given URI and returns a File pointing to
     * the compressed JPEG in the app's cache directory.
     *
     * @param context Application context
     * @param sourceUri URI of the source image (from gallery picker)
     * @return Compressed file, or null on failure
     */
    public static File compress(Context context, Uri sourceUri) throws IOException {
        // Step 1: Decode bounds only (no pixel allocation)
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(sourceUri)) {
            BitmapFactory.decodeStream(is, null, boundsOptions);
        }

        int originalWidth = boundsOptions.outWidth;
        int originalHeight = boundsOptions.outHeight;

        if (originalWidth <= 0 || originalHeight <= 0) {
            throw new IOException("Could not determine image dimensions");
        }

        // Step 2: Calculate inSampleSize for efficient memory use
        int inSampleSize = 1;
        while ((originalWidth / inSampleSize) > MAX_DIMENSION * 2 ||
               (originalHeight / inSampleSize) > MAX_DIMENSION * 2) {
            inSampleSize *= 2;
        }

        // Step 3: Decode with sample size
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = inSampleSize;
        Bitmap sampled;
        try (InputStream is = context.getContentResolver().openInputStream(sourceUri)) {
            sampled = BitmapFactory.decodeStream(is, null, decodeOptions);
        }

        if (sampled == null) {
            throw new IOException("Failed to decode image");
        }

        // Step 4: Scale to fit MAX_DIMENSION on the longest edge
        int w = sampled.getWidth();
        int h = sampled.getHeight();
        float scale = 1f;
        if (w > MAX_DIMENSION || h > MAX_DIMENSION) {
            scale = (float) MAX_DIMENSION / Math.max(w, h);
        }

        Bitmap scaled;
        if (scale < 1f) {
            int newW = Math.round(w * scale);
            int newH = Math.round(h * scale);
            scaled = Bitmap.createScaledBitmap(sampled, newW, newH, true);
            if (scaled != sampled) {
                sampled.recycle();
            }
        } else {
            scaled = sampled;
        }

        // Step 5: Write compressed JPEG to cache
        File cacheDir = new File(context.getCacheDir(), "compressed_images");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        File outputFile = new File(cacheDir, "img_" + System.currentTimeMillis() + ".jpg");

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos);
            fos.flush();
        } finally {
            scaled.recycle();
        }

        return outputFile;
    }
}
