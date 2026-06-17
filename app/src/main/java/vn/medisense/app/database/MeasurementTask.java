package vn.medisense.app.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * MeasurementTask - Entity lưu trữ lịch nhắc đo chỉ số sức khỏe
 */
@Entity(tableName = "measurement_tasks")
public class MeasurementTask {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String type;        // "blood_pressure", "heart_rate", "blood_sugar", "weight"
    public String title;       // Tiêu đề hiển thị (vd: "Đo Huyết áp sáng")
    public long timeOfDay;     // Giờ đo tính bằng milliseconds tính từ đầu ngày (vd: 8*60*60*1000 cho 8h sáng)
    public boolean isActive;   // Bật/tắt lịch nhắc

    public MeasurementTask(String type, String title, long timeOfDay) {
        this.type = type;
        this.title = title;
        this.timeOfDay = timeOfDay;
        this.isActive = true;
    }
}
