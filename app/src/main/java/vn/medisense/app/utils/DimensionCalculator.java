package vn.medisense.app.utils;

public class DimensionCalculator {
    private static final double CARD_WIDTH_MM = 85.6; // Kích thước thẻ ATM/CCCD tiêu chuẩn

    /**
     * Tính toán kích thước thật dựa vào tỉ lệ pixel.
     * @param cardPixelWidth Chiều rộng của thẻ trên ảnh (tính bằng pixel)
     * @param pixelDistance Kích thước vật thể (pixel) cần quy đổi
     * @return Kích thước thực tế (mm)
     */
    public static double calculateRealDimension(double cardPixelWidth, double pixelDistance) {
        if (cardPixelWidth <= 0) return 0;
        
        double pixelsPerMm = cardPixelWidth / CARD_WIDTH_MM;
        return pixelDistance / pixelsPerMm;
    }
    
    public static double calculateRealArea(double cardPixelWidth, double pixelArea) {
        if (cardPixelWidth <= 0) return 0;
        
        double pixelsPerMm = cardPixelWidth / CARD_WIDTH_MM;
        return pixelArea / (pixelsPerMm * pixelsPerMm);
    }
}
