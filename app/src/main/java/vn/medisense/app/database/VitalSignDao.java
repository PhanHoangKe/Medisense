package vn.medisense.app.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface VitalSignDao {
    
    @Insert
    long insertVitalSign(VitalSign vitalSign);
    
    @Update
    void updateVitalSign(VitalSign vitalSign);
    
    @Query("DELETE FROM vital_signs WHERE id = :id")
    void deleteVitalSign(int id);
    
    /**
     * Lấy tất cả chỉ số sức khỏe theo loại
     */
    @Query("SELECT * FROM vital_signs WHERE type = :type ORDER BY timestamp DESC")
    LiveData<List<VitalSign>> getVitalSignsByType(String type);
    
    /**
     * Lấy chỉ số sức khỏe trong khoảng thời gian
     */
    @Query("SELECT * FROM vital_signs WHERE type = :type AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    List<VitalSign> getVitalSignsByTypeAndTimeRange(String type, long startTime, long endTime);
    
    /**
     * Lấy tất cả chỉ số trong khoảng thời gian (cho AI analysis)
     */
    @Query("SELECT * FROM vital_signs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    List<VitalSign> getAllVitalSignsInRange(long startTime, long endTime);
    
    /**
     * Lấy chỉ số mới nhất theo loại
     */
    @Query("SELECT * FROM vital_signs WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    VitalSign getLatestVitalSign(String type);
    
    /**
     * Đếm số lượng chỉ số theo loại
     */
    @Query("SELECT COUNT(*) FROM vital_signs WHERE type = :type")
    int getVitalSignCount(String type);
}
