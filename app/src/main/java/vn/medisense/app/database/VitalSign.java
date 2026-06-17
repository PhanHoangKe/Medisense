package vn.medisense.app.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * VitalSign - Entity lưu trữ chỉ số sức khỏe
 */
@Entity(tableName = "vital_signs")
public class VitalSign {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String type;        // "blood_pressure", "heart_rate", "blood_sugar"
    public float value;        // Giá trị chỉ số
    public float value2;       // Giá trị thứ 2 (cho huyết áp: tâm thu/tâm trương)
    public long timestamp;     // Thời gian đo
    public String note;        // Ghi chú
    
    public VitalSign(String type, float value, float value2, long timestamp, String note) {
        this.type = type;
        this.value = value;
        this.value2 = value2;
        this.timestamp = timestamp;
        this.note = note;
    }
    
    /**
     * Lấy tên hiển thị của loại chỉ số
     */
    public String getDisplayName() {
        switch (type) {
            case "blood_pressure":
                return "Huyết áp";
            case "heart_rate":
                return "Nhịp tim";
            case "blood_sugar":
                return "Đường huyết";
            default:
                return type;
        }
    }
    
    /**
     * Lấy giá trị hiển thị
     */
    public String getDisplayValue() {
        switch (type) {
            case "blood_pressure":
                return String.format("%.0f/%.0f mmHg", value, value2);
            case "heart_rate":
                return String.format("%.0f bpm", value);
            case "blood_sugar":
                return String.format("%.1f mg/dL", value);
            default:
                return String.valueOf(value);
        }
    }
    
    /**
     * Kiểm tra xem chỉ số có bất thường không
     */
    public boolean isAbnormal() {
        switch (type) {
            case "blood_pressure":
                // Tâm thu > 140 hoặc < 90, Tâm trương > 90 hoặc < 60
                return value > 140 || value < 90 || value2 > 90 || value2 < 60;
            case "heart_rate":
                // Nhịp tim > 100 hoặc < 60
                return value > 100 || value < 60;
            case "blood_sugar":
                // Đường huyết > 140 hoặc < 70
                return value > 140 || value < 70;
            default:
                return false;
        }
    }
}
