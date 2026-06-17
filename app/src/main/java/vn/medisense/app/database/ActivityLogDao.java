package vn.medisense.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;

import java.util.List;

/**
 * DAO cho ActivityLog
 */
@Dao
public interface ActivityLogDao {
    
    @Insert
    long insert(ActivityLog activityLog);
    
    @Delete
    void delete(ActivityLog activityLog);
    
    // Lấy tất cả hoạt động, sắp xếp theo thời gian mới nhất
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    List<ActivityLog> getAllSync();
    
    // Lấy hoạt động trong khoảng thời gian
    @Query("SELECT * FROM activity_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    List<ActivityLog> getActivityLogsBetweenSync(long startTime, long endTime);
    
    // Đếm số hoạt động trong ngày hôm nay
    @Query("SELECT COUNT(*) FROM activity_logs WHERE timestamp >= :startOfDay")
    int getCountToday(long startOfDay);
}
