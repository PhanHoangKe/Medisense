package vn.medisense.app.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity lưu nhật ký tâm trạng và triệu chứng
 */
@Entity(tableName = "mood_logs")
public class MoodLog {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String moodName; // Tên tâm trạng: "Rất tốt 😊", "Bình thường 😐", v.v.
    
    public long timestamp; // Thời gian ghi nhận
    
    public String note; // Ghi chú (optional)

    public MoodLog(String moodName, long timestamp, String note) {
        this.moodName = moodName;
        this.timestamp = timestamp;
        this.note = note;
    }
}
