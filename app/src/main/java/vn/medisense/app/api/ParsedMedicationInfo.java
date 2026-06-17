package vn.medisense.app.api;

import androidx.annotation.Nullable;
import java.util.List;

/**
 * Model chứa thông tin thuốc được phân tích từ đơn thuốc
 * Được parse từ JSON response của Gemini AI
 */
public class ParsedMedicationInfo {
    /** Theo dõi Khóa chính khi mapper từ DB cục bộ */
    public int id = 0;

    /** Tên loại thuốc */
    @Nullable
    public String name;

    /** Liều lượng mỗi lần uống (ví dụ: "500mg", "1 viên") */
    @Nullable
    public String dosage;

    /** Số lần uống trong ngày */
    public int frequency;

    /** Danh sách giờ uống thuốc (định dạng "HH:mm") */
    @Nullable
    public List<String> times;

    /** Tổng số ngày uống */
    public int durationDays;

    /** Các ghi chú khác (cách uống, kiêng cữ, tác dụng phụ) */
    @Nullable
    public String notes;

    /** Tổng số lượng thuốc có trong đơn (số viên/vỉ) */
    public int totalQuantity;

    /** Số viên mỗi lần uống */
    public int dosagePerIntake;

    /** Liên kết ngữ cảnh */
    public int prescriptionId;
    @Nullable
    public String mealContext; // AFTER_MEAL, BEFORE_MEAL, NONE
    public int offsetMinutes;
    @Nullable
    public String specificShifts; // Morning,Afternoon,Evening,Night

    // ─── Fields mới cho an toàn y tế ─────────────────────────────────────────

    /**
     * Độ tin cậy AI đọc được thông tin này (0.0 – 1.0).
     * Mặc định 1.0 nếu AI không trả về.
     */
    public float confidence = 1.0f;

    /**
     * true nếu AI không chắc chắn và cần người dùng xem lại.
     * Khi true: không cho "lưu nhanh", phải qua AddMedicationActivity.
     */
    public boolean needsReview = false;

    /**
     * Danh sách field còn thiếu/không rõ, ví dụ: ["dosage", "frequency"].
     * Dùng để hiển thị cảnh báo trong UI.
     */
    @Nullable
    public java.util.List<String> missingFields;
}
