package vn.medisense.app.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "medications")
public class Medication {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name; // Tên thuốc
    public String dosage; // Liều lượng (vd: 2 viên)
    public String instructions; // Tóm tắt từ AI (kiêng kị, cách uống)
    public int frequency; // Số lần uống/ngày
    public int durationDays; // Số ngày uống thuốc
    public boolean isCritical; // Đánh dấu thuốc quan trọng

    // Liên kết Đơn thuốc và Ngữ cảnh
    public int prescriptionId; // Khóa ngoại liên kết tới PrescriptionData (nếu có, 0 nếu không có)
    public String mealContext; // Vd: BEFORE_MEAL, AFTER_MEAL, NONE
    public int offsetMinutes; // Thời gian lệch (vd: 30 cho "trước ăn 30 phút")
    public String specificShifts; // Vd: "Morning,Night"

    // Quản lý kho thuốc
    public int currentStock; // Số viên hiện có
    public int totalStock; // Số viên ban đầu
    public int lowStockThreshold; // Ngưỡng báo động (ví dụ: 5 viên)
    public int dosagePerIntake; // Số viên mỗi lần uống (mặc định: 1)

    // Đường dẫn ảnh của loại thuốc (thuốc đã chụp khi nhận diện)
    public String imagePath;
}
