package vn.medisense.app.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.List;

import vn.medisense.app.R;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.receivers.ReminderReceiver;

/**
 * SmartReminderManager - Hệ thống nhắc nhở thông minh
 * Tự động điều chỉnh kiểu thông báo dựa trên độ khẩn cấp và ngữ cảnh người dùng
 */
public class SmartReminderManager {

    private final Context context;
    private final DynamicIslandNotification notificationManager;
    private final AlarmManager alarmManager;

    public SmartReminderManager(Context context) {
        this.context = context;
        this.notificationManager = new DynamicIslandNotification(context);
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    /**
     * Lập lịch nhắc nhở thông minh dựa trên loại thuốc và độ khẩn cấp
     */
    public void scheduleReminder(Medication medication, Reminder reminder) {
        ReminderType type = determineReminderType(medication, reminder);
        int urgency = calculateUrgency(medication, reminder);

        // Lập lịch báo thức
        scheduleAlarm(reminder.id, reminder.reminderTime, type, urgency);

        // Hiển thị thông báo xem trước nếu khẩn cấp
        if (urgency >= 3) {
            showPreviewNotification(medication, type);
        }
    }

    /**
     * Determine reminder type based on medication properties
     */
    private ReminderType determineReminderType(Medication medication, Reminder reminder) {
        String medName = medication.name.toLowerCase();
        String dosage = medication.dosage.toLowerCase();

        // Kiểm tra thuốc huyết áp
        if (medName.contains("huyết áp") || medName.contains("amlodipine") || 
            medName.contains("lisinopril")) {
            return ReminderType.BLOOD_PRESSURE_MEDICATION;
        }

        // Kiểm tra thuốc tiểu đường
        if (medName.contains("đường huyết") || medName.contains("insulin") || 
            medName.contains("metformin")) {
            return ReminderType.DIABETES_MEDICATION;
        }

        // Kiểm tra thuốc quan trọng
        if (medication.isCritical) {
            return ReminderType.CRITICAL_MEDICATION;
        }

        return ReminderType.REGULAR_MEDICATION;
    }

    /**
     * Calculate urgency score (1-5)
     */
    private int calculateUrgency(Medication medication, Reminder reminder) {
        int urgency = 1;

        // Mức độ khẩn cấp cơ bản dựa trên loại thuốc
        ReminderType type = determineReminderType(medication, reminder);
        switch (type) {
            case CRITICAL_MEDICATION:
                urgency = 5;
                break;
            case BLOOD_PRESSURE_MEDICATION:
            case DIABETES_MEDICATION:
                urgency = 4;
                break;
            case REGULAR_MEDICATION:
                urgency = 2;
                break;
        }

        // Điều chỉnh dựa trên thời gian
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        
        // Tăng mức độ khẩn cấp cho thuốc buổi sáng (thói quen quan trọng)
        if (hour >= 6 && hour <= 9) {
            urgency = Math.min(urgency + 1, 5);
        }

        return urgency;
    }

    /**
     * Lập lịch báo thức với các cài đặt phù hợp
     */
    private void scheduleAlarm(int reminderId, long timeInMillis, ReminderType type, int urgency) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("reminder_id", reminderId);
        intent.putExtra("type", type.name());
        intent.putExtra("urgency", urgency);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);

        // Nếu thời gian đã qua, lập lịch cho ngày tiếp theo
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        // Lập lịch dựa trên mức độ khẩn cấp
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (urgency >= 4) {
                // Mức độ khẩn cấp cao - báo thức chính xác với đánh thức
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                // Mức độ khẩn cấp bình thường - cho phép tối ưu hóa hệ thống
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        }
    }

    /**
     * Show notification when reminder triggers
     */
    public void showReminderNotification(int reminderId, ReminderType type, int urgency, 
                                          String medicationName, String dosage) {
        DynamicIslandNotification.NotificationType notificationType;
        int progress = 0;

        // Ánh xạ loại nhắc nhở sang loại thông báo
        switch (type) {
            case CRITICAL_MEDICATION:
                notificationType = DynamicIslandNotification.NotificationType.URGENT;
                progress = 80;
                break;
            case BLOOD_PRESSURE_MEDICATION:
            case DIABETES_MEDICATION:
                notificationType = DynamicIslandNotification.NotificationType.MEDICATION;
                progress = 60;
                break;
            case REGULAR_MEDICATION:
                notificationType = DynamicIslandNotification.NotificationType.MEDICATION;
                progress = 40;
                break;
            default:
                notificationType = DynamicIslandNotification.NotificationType.MEDICATION;
        }

        // Điều chỉnh thông báo dựa trên mức độ khẩn cấp
        if (urgency >= 4) {
            // Gây gián interrupt toàn màn hình
            notificationManager.showIslandNotification(
                    notificationType,
                    medicationName,
                    dosage,
                    progress
            );
        } else {
            // Banner notification
            notificationManager.showIslandNotification(
                    DynamicIslandNotification.NotificationType.MEASUREMENT,
                    medicationName,
                    dosage,
                    progress
            );
        }
    }

    /**
     * Show measurement reminder (blood pressure, glucose, etc.)
     */
    public void showMeasurementReminder(String measurementType, String lastValue, String targetValue) {
        String title = "Đo " + measurementType;
        String message = lastValue != null ? 
                "Lần đo gần nhất: " + lastValue + " - Mục tiêu: " + targetValue :
                "Chưa có dữ liệu. Hãy đo " + measurementType + " ngay!";

        notificationManager.showIslandNotification(
                DynamicIslandNotification.NotificationType.MEASUREMENT,
                title,
                message,
                50
        );
    }

    /**
     * Show celebration for achievement
     */
    public void showAchievement(String title, String message) {
        notificationManager.showIslandNotification(
                DynamicIslandNotification.NotificationType.CELEBRATION,
                title,
                message,
                100
        );
    }

    /**
     * Show urgent alert
     */
    public void showUrgentAlert(String title, String message) {
        notificationManager.showIslandNotification(
                DynamicIslandNotification.NotificationType.URGENT,
                title,
                message,
                100
        );
    }

    /**
     * Show preview notification for upcoming reminder
     */
    private void showPreviewNotification(Medication medication, ReminderType type) {
        // Hiển thị thông báo nổi tinh tế cho nhắc nhở sắp tới
        int iconRes = R.drawable.ic_medication;
        if (type == ReminderType.BLOOD_PRESSURE_MEDICATION) {
            iconRes = R.drawable.ic_health;
        }

        notificationManager.showInAppIsland(
                "Sắp đến giờ",
                medication.name + " - " + medication.dosage,
                iconRes,
                30
        );
    }

    /**
     * Cancel reminder
     */
    public void cancelReminder(int reminderId) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(pendingIntent);
        notificationManager.cancelNotification(reminderId);
    }

    /**
     * Cancel all reminders
     */
    public void cancelAllReminders() {
        notificationManager.cancelAll();
    }

    /**
     * Snooze reminder for specified minutes
     */
    public void snoozeReminder(int reminderId, int minutes) {
        // Hủy báo thức hiện tại
        cancelReminder(reminderId);

        // Lập lịch lại sau thời gian tạm dừng
        long snoozeTime = System.currentTimeMillis() + (minutes * 60 * 1000);
        
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("reminder_id", reminderId);
        intent.putExtra("is_snoozed", true);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId + 10000, // ID khác cho báo thức đã tạm dừng
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTime,
                    pendingIntent
            );
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, snoozeTime, pendingIntent);
        }
    }

    /**
     * Kiểm tra xem người dùng có đang trong giờ yên tĩnh không
     */
    public boolean isQuietHours() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        // Quiet hours: 22:00 - 07:00
        return hour >= 22 || hour < 7;
    }

    /**
     * Get recommended notification delay based on quiet hours
     */
    public long getRecommendedDelay() {
        if (isQuietHours()) {
            // Delay until 7 AM
            Calendar morning = Calendar.getInstance();
            morning.set(Calendar.HOUR_OF_DAY, 7);
            morning.set(Calendar.MINUTE, 0);
            
            if (morning.getTimeInMillis() < System.currentTimeMillis()) {
                morning.add(Calendar.DAY_OF_YEAR, 1);
            }
            
            return morning.getTimeInMillis() - System.currentTimeMillis();
        }
        return 0;
    }

    public enum ReminderType {
        REGULAR_MEDICATION,
        BLOOD_PRESSURE_MEDICATION,
        DIABETES_MEDICATION,
        CRITICAL_MEDICATION,
        MEASUREMENT
    }
}
