package vn.medisense.app.utils;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

public class ImageProcessingUtils {
    
    /**
     * Tối ưu hóa ảnh chụp viên thuốc: Grayscale -> Histogram Equalization -> Canny Edge
     */
    public static Bitmap preprocessImageForPill(Bitmap srcBitmap) {
        Mat src = new Mat();
        Utils.bitmapToMat(srcBitmap, src);
        
        // 1. Chuyển sang Grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);
        
        // 2. Histogram Equalization (tăng cường độ tương phản làm nổi bật chữ/imprint)
        Mat equalized = new Mat();
        Imgproc.equalizeHist(gray, equalized);
        
        // 3. Phát hiện cạnh Canny
        Mat edges = new Mat();
        Imgproc.Canny(equalized, edges, 50, 150);
        
        // Trả về ảnh đã xử lý phục vụ debug hoặc model ML
        Bitmap resultBitmap = Bitmap.createBitmap(edges.cols(), edges.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(edges, resultBitmap);
        
        // Cleanup
        src.release(); gray.release(); equalized.release(); edges.release();
        return resultBitmap;
    }
    
    /**
     * Tìm Rect của vật thể lớn nhất trong toàn bộ ảnh.
     *
     * Trả về null khi không có contour hợp lệ (giảm nhiễu). Trong thử nghiệm
     * một vùng có diện tích rất nhỏ (ví dụ <100px²) thường là nhiễu, vì vậy
     * chúng ta bỏ qua.
     */
    public static Rect findLargestContour(Bitmap srcBitmap) {
        Mat src = new Mat();
        Utils.bitmapToMat(srcBitmap, src);
        
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);
        
        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 50, 150);
        
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        
        double maxArea = -1;
        Rect bestRect = null;
        final double MIN_AREA_THRESHOLD = 100.0; // px², theo kinh nghiệm để tránh các đốm nhiễu
        
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > maxArea && area > MIN_AREA_THRESHOLD) {
                maxArea = area;
                bestRect = Imgproc.boundingRect(contour);
            }
        }
        
        // nếu không tìm được contour lớn nào, trả về null để bên gọi xử lý
        src.release(); gray.release(); edges.release(); hierarchy.release();
        return bestRect; // có thể null
    }
}
