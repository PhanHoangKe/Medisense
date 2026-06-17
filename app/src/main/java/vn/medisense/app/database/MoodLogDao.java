package vn.medisense.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;

import java.util.List;

/**
 * DAO cho MoodLog
 */
@Dao
public interface MoodLogDao {
    
    @Insert
    long insert(MoodLog moodLog);
    
    @Delete
    void delete(MoodLog moodLog);
    
    // Lấy tất cả tâm trạng, sắp xếp theo thời gian mới nhất
    @Query("SELECT * FROM mood_logs ORDER BY timestamp DESC")
    List<MoodLog> getAllSync();
    
    // Lấy tâm trạng trong khoảng thời gian
    @Query("SELECT * FROM mood_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    List<MoodLog> getMoodLogsBetweenSync(long startTime, long endTime);
    
    // Đếm số ghi nhận trong ngày hôm nay
    @Query("SELECT COUNT(*) FROM mood_logs WHERE timestamp >= :startOfDay")
    int getCountToday(long startOfDay);
}
