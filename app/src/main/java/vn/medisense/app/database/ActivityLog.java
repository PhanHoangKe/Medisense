package vn.medisense.app.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity lưu nhật ký hoạt động thể chất
 */
@Entity(tableName = "activity_logs")
public class ActivityLog {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String activityName; // Tên hoạt động: "Đi bộ", "Chạy bộ", "Tập Yoga", "Đạp xe", "Thiền"
    
    public long timestamp; // Thời gian ghi nhận
    
    public String note; // Ghi chú (optional)

    public ActivityLog(String activityName, long timestamp, String note) {
        this.activityName = activityName;
        this.timestamp = timestamp;
        this.note = note;
    }
}
