package vn.medisense.app.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import vn.medisense.app.MainActivity;
import vn.medisense.app.R;
import vn.medisense.app.receivers.NotificationActionReceiver;

/**
 * NotificationUtils - Utility class for creating various notification types
 * Simplified helper methods for common notification patterns
 */
public class NotificationUtils {

    private static final String CHANNEL_GENERAL = "general";
    private static final String CHANNEL_REMINDERS = "reminders";
    private static final String CHANNEL_ACHIEVEMENTS = "achievements";
    private static final String CHANNEL_SYSTEM = "system";

    /**
     * Create all notification channels (call once at app startup)
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);

            // General notifications
            NotificationChannel general = new NotificationChannel(
                    CHANNEL_GENERAL,
                    "Thông báo chung",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            general.setDescription("Thông báo chung của ứng dụng");

            // Reminders
            NotificationChannel reminders = new NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Nhắc nhở uống thuốc",
                    NotificationManager.IMPORTANCE_HIGH
            );
            reminders.setDescription("Nhắc nhở quan trọng về lịch uống thuốc");
            reminders.enableVibration(true);
            reminders.setVibrationPattern(new long[]{0, 500, 200, 500});

            // Achievements
            NotificationChannel achievements = new NotificationChannel(
                    CHANNEL_ACHIEVEMENTS,
                    "Thành tích",
                    NotificationManager.IMPORTANCE_LOW
            );
            achievements.setDescription("Thông báo khi đạt được mục tiêu sức khỏe");

            // System
            NotificationChannel system = new NotificationChannel(
                    CHANNEL_SYSTEM,
                    "Hệ thống",
                    NotificationManager.IMPORTANCE_MIN
            );
            system.setDescription("Thông báo nội bộ của hệ thống");

            manager.createNotificationChannels(java.util.Arrays.asList(
                    general, reminders, achievements, system
            ));
        }
    }

    /**
     * Show simple medication reminder
     */
    public static void showMedicationReminder(Context context, String medicationName, 
                                               String dosage, int notificationId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_medication)
                .setContentTitle("💊 Đến giờ uống thuốc")
                .setContentText(medicationName + " - " + dosage)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_check, "Đã uống", createActionIntent(context, "taken", notificationId))
                .addAction(R.drawable.ic_notification, "Nhắc sau", createActionIntent(context, "snooze", notificationId));

        // Kiểu văn bản lớn
        NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
        style.setBigContentTitle("💊 " + medicationName);
        style.bigText("Liều dùng: " + dosage + "\n\nĐã uống thuốc chưa? Hãy đánh dấu để theo dõi tiến độ.");
        builder.setStyle(style);

        notify(context, notificationId, builder.build());

        // Vibrate
        vibrate(context, new long[]{0, 500, 200, 500});
    }

    /**
     * Show measurement reminder
     */
    public static void showMeasurementReminder(Context context, String measurementType, 
                                                int notificationId) {
        Intent intent = new Intent(context, vn.medisense.app.ui.HealthTrackerActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = "📊 Đến giờ đo " + measurementType;
        String message = "Hãy đo và ghi lại chỉ số " + measurementType + " của bạn";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_health)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_check, "Đã đo", createActionIntent(context, "measured", notificationId))
                .addAction(R.drawable.ic_notification, "Nhắc sau", createActionIntent(context, "snooze", notificationId));

        notify(context, notificationId, builder.build());
    }

    /**
     * Show urgent alert
     */
    public static void showUrgentAlert(Context context, String title, String message, int notificationId) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⚠️ " + title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setColor(Color.RED)
                .setLights(Color.RED, 1000, 1000)
                .setFullScreenIntent(pendingIntent, true);

        notify(context, notificationId, builder.build());

        // Strong vibration
        vibrate(context, new long[]{0, 1000, 500, 1000, 500, 1000});
    }

    /**
     * Show achievement notification
     */
    public static void showAchievement(Context context, String title, String message, int notificationId) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ACHIEVEMENTS)
                .setSmallIcon(R.drawable.ic_check)
                .setContentTitle("🎉 " + title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notify(context, notificationId, builder.build());
    }

    /**
     * Show progress notification
     */
    public static void showProgressNotification(Context context, String title, int progress, 
                                                 int max, int notificationId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SYSTEM)
                .setSmallIcon(R.drawable.ic_sync)
                .setContentTitle(title)
                .setProgress(max, progress, false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        notify(context, notificationId, builder.build());
    }

    /**
     * Update progress of existing notification
     */
    public static void updateProgress(Context context, String title, int progress, int max, int notificationId) {
        showProgressNotification(context, title, progress, max, notificationId);
    }

    /**
     * Cancel notification
     */
    public static void cancelNotification(Context context, int notificationId) {
        NotificationManagerCompat.from(context).cancel(notificationId);
    }

    /**
     * Cancel all notifications
     */
    public static void cancelAll(Context context) {
        NotificationManagerCompat.from(context).cancelAll();
    }

    /**
     * Check if notifications are enabled
     */
    public static boolean areNotificationsEnabled(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Create action pending intent
     */
    private static PendingIntent createActionIntent(Context context, String action, int id) {
        Intent intent = new Intent(context, NotificationActionReceiver.class);
        intent.setAction("vn.medisense.app." + action);
        intent.putExtra("notification_id", id);
        return PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Vibrate with pattern
     */
    private static void vibrate(Context context, long[] pattern) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }
    }

    /**
     * Send notification
     */
    private static void notify(Context context, int id, Notification notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification);
        } catch (SecurityException e) {
            // Quyền thông báo không được cấp (Android 13+)
            e.printStackTrace();
        }
    }

    /**
     * Show medication taken confirmation
     */
    public static void showMedicationTaken(Context context, String medicationName, int notificationId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ACHIEVEMENTS)
                .setSmallIcon(R.drawable.ic_check)
                .setContentTitle("✅ Đã uống thuốc")
                .setContentText(medicationName + " - Tuyệt vời!")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setTimeoutAfter(3000);

        notify(context, notificationId, builder.build());
    }

    /**
     * Show snooze confirmation
     */
    public static void showSnoozeConfirmation(Context context, int minutes, int notificationId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_GENERAL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⏰ Đã nhắc lại")
                .setContentText("Sẽ nhắc lại sau " + minutes + " phút")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setTimeoutAfter(2000);

        notify(context, notificationId, builder.build());
    }
}
