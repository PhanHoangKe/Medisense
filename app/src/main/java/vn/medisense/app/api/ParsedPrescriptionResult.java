package vn.medisense.app.api;

import androidx.annotation.Nullable;
import java.util.List;

/**
 * Model bao bọc dữ liệu trả về từ Gemini AI khi web/scan toàn bộ một đơn thuốc
 * (có chẩn đoán)
 */
public class ParsedPrescriptionResult {
    @Nullable
    public String diagnosis;

    @Nullable
    public String doctorAdvice;

    @Nullable
    public List<ParsedMedicationInfo> medications;
}
