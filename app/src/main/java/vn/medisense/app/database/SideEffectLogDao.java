package vn.medisense.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface SideEffectLogDao {

    @Insert
    void insert(SideEffectLog log);

    @Query("SELECT * FROM side_effect_logs ORDER BY timestamp DESC")
    List<SideEffectLog> getAllLogs();

    @Query("SELECT * FROM side_effect_logs WHERE medicationId = :medId ORDER BY timestamp DESC")
    List<SideEffectLog> getLogsForMedication(int medId);

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :medId")
    MedicationWithSideEffects getMedicationWithSideEffects(int medId);
    
    // Cho insights/reporting: lấy số lượng của các triệu chứng cụ thể
    @Query("SELECT COUNT(*) FROM side_effect_logs WHERE medicationId = :medId AND symptoms LIKE '%' || :symptom || '%'")
    int getSymptomCountForMedication(int medId, String symptom);
}
