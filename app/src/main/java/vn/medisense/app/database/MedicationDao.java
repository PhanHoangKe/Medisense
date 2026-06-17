package vn.medisense.app.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MedicationDao {
    @Insert
    long insertMedication(Medication medication);

    @Insert
    long insertPrescriptionData(PrescriptionData prescriptionData);

    @Query("SELECT * FROM prescription_data WHERE id = :id")
    PrescriptionData getPrescriptionDataByIdSync(int id);

    @Update
    void updatePrescriptionDataSync(PrescriptionData data);

    @Query("UPDATE prescription_data SET isActive = 0 WHERE id = :prescriptionId")
    void archivePrescriptionDataSync(int prescriptionId);

    @Query("DELETE FROM reminders WHERE isTaken = 0 AND medicationId IN (SELECT id FROM medications WHERE prescriptionId = :prescriptionId)")
    void deletePendingRemindersByPrescriptionIdSync(int prescriptionId);

    @Query("DELETE FROM reminders WHERE isTaken = 0 AND medicationId = :medicationId")
    void deletePendingRemindersByMedicationIdSync(int medicationId);

    @Query("SELECT * FROM reminders WHERE medicationId = :medicationId")
    List<Reminder> getRemindersByMedicationIdSync(int medicationId);

    @Insert
    long[] insertReminders(List<Reminder> reminders);

    @Update
    void updateReminder(Reminder reminder);

    // Lấy tất cả liều thuốc cần uống trong một khoảng thời gian (ví dụ: ngày hôm nay)
    @Query("SELECT * FROM reminders WHERE reminderTime BETWEEN :startTime AND :endTime ORDER BY reminderTime ASC")
    LiveData<List<Reminder>> getRemindersForDay(long startTime, long endTime);

    @Transaction
    @Query("SELECT * FROM reminders WHERE reminderTime BETWEEN :startTime AND :endTime ORDER BY reminderTime ASC")
    LiveData<List<ReminderWithMedication>> getRemindersWithMedicationForDay(long startTime, long endTime);

    @Transaction
    @Query("SELECT * FROM reminders WHERE reminderTime BETWEEN :startTime AND :endTime ORDER BY reminderTime ASC")
    List<ReminderWithMedication> getRemindersWithMedicationForDaySync(long startTime, long endTime);

    @Query("UPDATE reminders SET isTaken = :isTaken WHERE id = :reminderId")
    void updateReminderStatus(int reminderId, boolean isTaken);

    @Query("UPDATE reminders SET isTaken = 1, takenTime = :takenTime WHERE id = :reminderId")
    void updateReminderTakenTime(int reminderId, long takenTime);

    @Query("SELECT isTaken FROM reminders WHERE id = :reminderId")
    boolean isReminderTaken(int reminderId);

    @Transaction
    @Query("SELECT * FROM reminders WHERE isTaken = 0 AND reminderTime > :currentTime")
    List<ReminderWithMedication> getPendingRemindersWithMedication(long currentTime);

    @Query("SELECT * FROM reminders WHERE medicationId = :medId AND reminderTime BETWEEN :startTime AND :endTime AND isTaken = 0")
    List<Reminder> getPendingRemindersForMedicationBetweenSync(int medId, long startTime, long endTime);

    @Query("SELECT COUNT(*) FROM reminders WHERE reminderTime BETWEEN :startTime AND :endTime")
    int getTotalRemindersCountSync(long startTime, long endTime);

    @Query("SELECT COUNT(*) FROM reminders WHERE isTaken = 1 AND reminderTime BETWEEN :startTime AND :endTime")
    int getTakenRemindersCountSync(long startTime, long endTime);

    @Query("SELECT * FROM reminders WHERE reminderTime BETWEEN :startTime AND :endTime")
    List<Reminder> getRemindersBetweenSync(long startTime, long endTime);

    @Query("SELECT * FROM medications")
    List<Medication> getAllMedicationsSync();

    // sử dụng join để lấy thuốc có lời đánh dấu dùng gần nhất, giảm N lên 5,6,...
    @Query("SELECT m.* FROM medications m " +
           "INNER JOIN reminders r ON m.id = r.medicationId " +
           "WHERE r.takenTime > 0 " +
           "GROUP BY m.id " +
           "ORDER BY MAX(r.takenTime) DESC " +
           "LIMIT :limit")
    List<Medication> getMostRecentlyTakenMedicationsSync(int limit);

    @Query("SELECT COUNT(*) FROM reminders WHERE medicationId = :medId AND reminderTime BETWEEN :startTime AND :endTime")
    int getTotalRemindersForMedicationSync(int medId, long startTime, long endTime);



    // Xóa thuốc (sẽ tự động xóa các Reminder liên quan nhờ CASCADE)
    @Query("DELETE FROM medications WHERE id = :medId")
    void deleteMedication(int medId);

    // Quan ly kho thuoc
    @Update
    void updateMedication(Medication medication);

    @Query("SELECT * FROM medications WHERE id = :medId")
    Medication getMedicationById(int medId);

    @Query("SELECT * FROM medications WHERE id = :medId")
    Medication getMedicationByIdSync(int medId);

    @Query("UPDATE medications SET imagePath = :path WHERE id = :medId")
    void updateMedicationImagePath(int medId, String path);

    @Query("SELECT * FROM medications WHERE prescriptionId = :prescriptionId")
    List<Medication> getMedicationsByPrescriptionIdSync(int prescriptionId);

    @Query("DELETE FROM medications WHERE prescriptionId = :prescriptionId")
    void deleteMedicationsByPrescriptionIdSync(int prescriptionId);

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    Reminder getReminderByIdSync(int reminderId);

    @Query("DELETE FROM reminders WHERE id = :reminderId")
    void deleteReminderSync(int reminderId);

    @Query("UPDATE reminders SET imagePath = :path WHERE id = :reminderId")
    void updateReminderImagePath(int reminderId, String path);

    @Query("DELETE FROM prescription_data WHERE id = :prescriptionId")
    void deletePrescriptionDataSync(int prescriptionId);

    @Query("UPDATE medications SET currentStock = currentStock - :amount WHERE id = :medId")
    void decreaseStock(int medId, int amount);

    @Query("UPDATE medications SET currentStock = currentStock + :amount WHERE id = :medId")
    void increaseStock(int medId, int amount);

    // ─── Queries cho hệ thống status mới ─────────────────────────────────────

    /** Cập nhật status + isTaken + takenTime trong một lần gọi */
    @Query("UPDATE reminders SET status = :status, isTaken = :isTaken, takenTime = :takenTime WHERE id = :reminderId")
    void updateReminderStatusFull(int reminderId, String status, boolean isTaken, long takenTime);

    /** Cập nhật status SKIPPED + skippedTime + skipReason */
    @Query("UPDATE reminders SET status = :status, isTaken = 0, skippedTime = :skippedTime, skipReason = :skipReason WHERE id = :reminderId")
    void updateReminderSkipped(int reminderId, String status, long skippedTime, String skipReason);

    /** Cập nhật status SNOOZED + snoozeUntil */
    @Query("UPDATE reminders SET status = :status, isTaken = 0, snoozeUntil = :snoozeUntil WHERE id = :reminderId")
    void updateReminderSnoozed(int reminderId, String status, long snoozeUntil);

    /** Cập nhật status MISSED */
    @Query("UPDATE reminders SET status = 'MISSED', isTaken = 0 WHERE id = :reminderId")
    void updateReminderMissed(int reminderId);

    /** Tăng naggingCount lên 1 */
    @Query("UPDATE reminders SET naggingCount = naggingCount + 1 WHERE id = :reminderId")
    void incrementNaggingCount(int reminderId);

    /** Lấy status hiện tại của reminder */
    @Query("SELECT status FROM reminders WHERE id = :reminderId")
    String getReminderStatus(int reminderId);

    /** Đếm số liều TAKEN trong khoảng thời gian */
    @Query("SELECT COUNT(*) FROM reminders WHERE status = 'TAKEN' AND reminderTime BETWEEN :startTime AND :endTime")
    int getTakenCountSync(long startTime, long endTime);

    /** Đếm số liều MISSED trong khoảng thời gian */
    @Query("SELECT COUNT(*) FROM reminders WHERE status = 'MISSED' AND reminderTime BETWEEN :startTime AND :endTime")
    int getMissedCountSync(long startTime, long endTime);

    /** Đếm số liều SKIPPED trong khoảng thời gian */
    @Query("SELECT COUNT(*) FROM reminders WHERE status = 'SKIPPED' AND reminderTime BETWEEN :startTime AND :endTime")
    int getSkippedCountSync(long startTime, long endTime);

    /** Đếm số liều PENDING trong khoảng thời gian */
    @Query("SELECT COUNT(*) FROM reminders WHERE status = 'PENDING' AND reminderTime BETWEEN :startTime AND :endTime")
    int getPendingCountSync(long startTime, long endTime);

    /** Lấy tất cả reminder PENDING/SNOOZED đã quá giờ ân hạn (để chuyển sang MISSED) */
    @Query("SELECT * FROM reminders WHERE (status = 'PENDING' OR status = 'SNOOZED') AND reminderTime + 1800000 < :currentTime")
    List<Reminder> getOverdueRemindersSync(long currentTime);

    /** Đếm TAKEN theo medicationId trong khoảng thời gian */
    @Query("SELECT COUNT(*) FROM reminders WHERE medicationId = :medId AND status = 'TAKEN' AND reminderTime BETWEEN :startTime AND :endTime")
    int getTakenRemindersForMedicationSync(int medId, long startTime, long endTime);

    /** Đếm MISSED theo medicationId trong khoảng thời gian */
    @Query("SELECT COUNT(*) FROM reminders WHERE medicationId = :medId AND status = 'MISSED' AND reminderTime BETWEEN :startTime AND :endTime")
    int getMissedRemindersForMedicationSync(int medId, long startTime, long endTime);

    /** Đếm SKIPPED theo medicationId trong khoảng thời gian */
    @Query("SELECT COUNT(*) FROM reminders WHERE medicationId = :medId AND status = 'SKIPPED' AND reminderTime BETWEEN :startTime AND :endTime")
    int getSkippedRemindersForMedicationSync(int medId, long startTime, long endTime);
}
