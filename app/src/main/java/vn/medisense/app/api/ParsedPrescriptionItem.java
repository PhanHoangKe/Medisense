package vn.medisense.app.api;

import androidx.annotation.Nullable;

/**
 * Model noi dung JSON tu Gemini cho ket qua quet don thuoc
 */
public class ParsedPrescriptionItem {
    @Nullable
    public String name;
    @Nullable
    public String dosage;
    @Nullable
    public String frequency;
    @Nullable
    public String time;
    @Nullable
    public String notes;
    @Nullable
    public String mealContext; // AFTER_MEAL, BEFORE_MEAL, NONE
    public int offsetMinutes; // 30, 0
    @Nullable
    public String specificShifts; // Morning,Afternoon,Evening,Night

    public int totalQuantity; // Tổng số viên thuốc/số lượng
    @Nullable
    public java.util.List<ParsedTimeDose> timeDoses; // Danh sách các giờ uống

    /** Số ngày uống (durationDays). 0 nếu không có. */
    public int durationDays;

    // ── Fields an toàn y tế (từ prompt mới) ──────────────────────────────────
    /** Độ tin cậy AI đọc được (0.0–1.0). Default 1.0 nếu Gemini không trả về. */
    public float confidence = 1.0f;
    /** true nếu AI không chắc và cần người dùng xem lại */
    public boolean needsReview = false;
    /** Danh sách field còn thiếu, vd ["dosage","frequency"] */
    @Nullable
    public java.util.List<String> missingFields;
}
