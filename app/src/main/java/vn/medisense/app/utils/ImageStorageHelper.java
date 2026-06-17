package vn.medisense.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Hỗ trợ lưu, nén và resize hình ảnh vào bộ nhớ trong
 */
public class ImageStorageHelper {
    private static final String TAG = "ImageStorageHelper";
    private static final String DIRECTORY = "med_images";
    private static final int MAX_SIZE_PX = 1024;
    private static final int JPEG_QUALITY = 70;

    /**
     * Lưu ảnh thuốc vào internal storage và trả về đường dẫn file
     */
    public static String savePillImage(Context context, Bitmap bitmap, int medId) {
        if (bitmap == null) return null;

        // resize nếu quá lớn
        Bitmap resized = resizeBitmap(bitmap, MAX_SIZE_PX);

        File dir = new File(context.getFilesDir(), DIRECTORY);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String filename = String.format("pill_%d_%d.jpg", medId, System.currentTimeMillis());
        File outFile = new File(dir, filename);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(outFile);
            resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos);
            fos.flush();
            Log.d(TAG, "Saved pill image to " + outFile.getAbsolutePath());
            return outFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Failed to save image", e);
            return null;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
            }
            if (resized != bitmap) {
                resized.recycle();
            }
        }
    }

    /**
     * Phóng to hoặc thu nhỏ bitmap sao cho cạnh dài nhất <= maxSize
     */
    private static Bitmap resizeBitmap(Bitmap src, int maxSize) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxSize && h <= maxSize) {
            return src;
        }
        float scale = Math.min((float) maxSize / w, (float) maxSize / h);
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        Bitmap dst = Bitmap.createBitmap(src, 0, 0, w, h, m, true);
        return dst;
    }
}