package vn.medisense.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.utils.AppExecutors;
import vn.medisense.app.utils.SmartReminderManager;

/**
 * ReminderReceiver - Receives alarm triggers and shows appropriate notifications
 */
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int reminderId = intent.getIntExtra("reminder_id", -1);
        boolean isSnoozed = intent.getBooleanExtra("is_snoozed", false);
        
        if (reminderId == -1) {
            return;
        }

        // Lấy chi tiết nhắc nhở từ database
        AppExecutors.getInstance().diskIO().execute(() -> {
            Reminder reminder = AppDatabase.getInstance(context).reminderDao().getReminderById(reminderId);
            if (reminder == null) return;
            
            Medication medication = AppDatabase.getInstance(context).medicationDao()
                    .getMedicationById(reminder.medicationId);
            if (medication == null) return;
            
            String typeStr = intent.getStringExtra("type");
            SmartReminderManager.ReminderType type = SmartReminderManager.ReminderType.REGULAR_MEDICATION;
            if (typeStr != null) {
                try {
                    type = SmartReminderManager.ReminderType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    // Use default value
                }
            }
            final SmartReminderManager.ReminderType finalType = type;
            
            final int urgency = intent.getIntExtra("urgency", 2);
            
            // Hiển thị thông báo trên main thread
            final String medName = medication.name;
            final String medDosage = medication.dosage;
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                SmartReminderManager manager = new SmartReminderManager(context);
                manager.showReminderNotification(reminderId, finalType, urgency, medName, medDosage);
            });
        });
    }
}
