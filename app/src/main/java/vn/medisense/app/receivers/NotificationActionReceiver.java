package vn.medisense.app.receivers;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.utils.AlarmHelper;
import vn.medisense.app.workers.NaggingWorker;

import vn.medisense.app.utils.AppExecutors;

public class NotificationActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        int reminderId = intent.getIntExtra("reminderId", -1);
        int notificationId = intent.getIntExtra("notification_id", -1);

        // Hủy thông báo nếu có ID
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && notificationId != -1) {
            manager.cancel(notificationId);
        }
        if (manager != null && reminderId != -1) {
            manager.cancel(reminderId);
        }

        // Luôn tắt chuông khẩn cấp nếu có tương tác với Notification Action
        vn.medisense.app.utils.AudioResourceHelper.stopAlarmSound();

        // Xử lý các hành động thông báo mới cho Dynamic Island
        if (action != null && action.startsWith("vn.medisense.app.")) {
            handleDynamicIslandActions(context, action, notificationId, intent);
            return;
        }

        if (reminderId == -1 || action == null)
            return;

        if (action.equals("ACTION_TAKEN") || action.equals("taken")) {
            // Dừng nagging
            NaggingWorker.stopNagging(context, reminderId);

            // Cập nhật trạng thái đầy đủ qua ReminderStatusHelper (trừ stock, caregiver log, SideEffect)
            AppExecutors.getInstance().diskIO().execute(() -> {
                vn.medisense.app.database.Reminder reminder =
                        AppDatabase.getInstance(context).reminderDao().getReminderByIdSync(reminderId);
                if (reminder == null) return;

                vn.medisense.app.database.Medication medication =
                        AppDatabase.getInstance(context).medicationDao().getMedicationByIdSync(reminder.medicationId);

                vn.medisense.app.database.ReminderWithMedication item =
                        new vn.medisense.app.database.ReminderWithMedication();
                item.reminder   = reminder;
                item.medication = medication;

                vn.medisense.app.utils.ReminderStatusHelper.markTaken(context, item);
            });

            Toast.makeText(context, "Đã đánh dấu uống thuốc", Toast.LENGTH_SHORT).show();

        } else if (action.equals("ACTION_SNOOZE") || action.equals("snooze")) {
            String medicationName = intent.getStringExtra("medicationName");
            String dosage         = intent.getStringExtra("dosage");
            boolean isCritical    = intent.getBooleanExtra("isCritical", false);

            // Lưu trạng thái SNOOZED vào DB và reschedule alarm
            AppExecutors.getInstance().diskIO().execute(() -> {
                vn.medisense.app.database.Reminder reminder =
                        AppDatabase.getInstance(context).reminderDao().getReminderByIdSync(reminderId);
                if (reminder == null) return;

                vn.medisense.app.database.Medication medication =
                        AppDatabase.getInstance(context).medicationDao().getMedicationByIdSync(reminder.medicationId);

                vn.medisense.app.database.ReminderWithMedication item =
                        new vn.medisense.app.database.ReminderWithMedication();
                item.reminder   = reminder;
                item.medication = medication;

                // Dừng nagging cũ, lưu SNOOZED, reschedule alarm
                NaggingWorker.stopNagging(context, reminderId);
                vn.medisense.app.utils.ReminderStatusHelper.markSnoozed(context, item, 10);
            });

            Toast.makeText(context, "Sẽ nhắc lại sau 10 phút nữa", Toast.LENGTH_SHORT).show();
        } else if (action.equals("measured")) {
            // Measurement recorded
            Toast.makeText(context, "Đã ghi nhận chỉ số", Toast.LENGTH_SHORT).show();
        } else if (action.equals("snooze_15")) {
            // 15 phút tạm dừng cho việc đo
            Toast.makeText(context, "Sẽ nhắc lại sau 15 phút", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handle Dynamic Island notification actions
     */
    private void handleDynamicIslandActions(Context context, String action, int notificationId, Intent intent) {
        String actionName = action.replace("vn.medisense.app.", "");
        
        switch (actionName) {
            case "taken":
                Toast.makeText(context, "✅ Đã uống thuốc", Toast.LENGTH_SHORT).show();
                // Đánh dấu là đã uống trong database
                break;
            case "snooze":
                Toast.makeText(context, "⏰ Nhắc lại sau 10 phút", Toast.LENGTH_SHORT).show();
                break;
            case "measured":
                Toast.makeText(context, "✅ Đã đo chỉ số", Toast.LENGTH_SHORT).show();
                break;
            case "snooze_15":
                Toast.makeText(context, "⏰ Nhắc lại sau 15 phút", Toast.LENGTH_SHORT).show();
                break;
        }
    }
}
