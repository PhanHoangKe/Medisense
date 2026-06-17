package vn.medisense.app.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    Reminder getReminderById(int reminderId);

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    Reminder getReminderByIdSync(int reminderId);

    @Query("SELECT * FROM reminders WHERE medicationId = :medicationId")
    List<Reminder> getRemindersByMedicationIdSync(int medicationId);

    @Insert
    long[] insertReminders(List<Reminder> reminders);

    @Update
    void updateReminder(Reminder reminder);

    @Query("SELECT * FROM reminders WHERE reminderTime BETWEEN :startTime AND :endTime ORDER BY reminderTime ASC")
    LiveData<List<Reminder>> getRemindersForDay(long startTime, long endTime);

    @Query("UPDATE reminders SET isTaken = :isTaken WHERE id = :reminderId")
    void updateReminderStatus(int reminderId, boolean isTaken);

    @Query("UPDATE reminders SET isTaken = 1, takenTime = :takenTime WHERE id = :reminderId")
    void updateReminderTakenTime(int reminderId, long takenTime);

    @Query("SELECT isTaken FROM reminders WHERE id = :reminderId")
    boolean isReminderTaken(int reminderId);

    @Query("DELETE FROM reminders WHERE isTaken = 0 AND medicationId = :medicationId")
    void deletePendingRemindersByMedicationIdSync(int medicationId);

    @Query("UPDATE reminders SET imagePath = :path WHERE id = :reminderId")
    void updateReminderImagePath(int reminderId, String path);
}
