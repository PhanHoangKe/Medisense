package vn.medisense.app.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Lưu trữ thông tin chung của một đơn thuốc
 * Bao gồm ngữ cảnh lớn (chẩn đoán, lời dặn bác sĩ...)
 */
@Entity(tableName = "prescription_data")
public class PrescriptionData {

    @PrimaryKey(autoGenerate = true)
    public int id;

    // Tên chung (nếu cần), có thể là "Đơn thuốc ngày 12/03/2026"
    public String title;

    // Chẩn đoán tổng quan (vd: "Tăng huyết áp vô căn, rối loạn lipid máu")
    public String diagnosis;

    // Lời dặn dò chung của bác sĩ (vd: "Kiêng ăn mặn, tập thể dục nhẹ")
    public String doctorAdvice;

    // Ngày giờ tạo đơn (Timestamp)
    public long dateCreated;

    // Trạng thái Lưu trữ (false) hoặc Hoạt động (true)
    public boolean isActive = true;
}
