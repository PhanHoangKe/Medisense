package vn.medisense.app.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import vn.medisense.app.MainActivity;
import vn.medisense.app.R;
import vn.medisense.app.ui.CriticalAlertActivity;
import vn.medisense.app.utils.AudioResourceHelper;
import vn.medisense.app.workers.NaggingWorker;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "MEDISENSE_ALARM_CHANNEL";
    private static final String CRITICAL_CHANNEL_ID = "MEDISENSE_CRITICAL_CHANNEL";

    @Override
    public void onReceive(Context context, Intent intent) {
        int reminderId = intent.getIntExtra("reminderId", -1);
        String medicationName = intent.getStringExtra("medicationName");
        String dosage = intent.getStringExtra("dosage");
        boolean isCritical = intent.getBooleanExtra("isCritical", false);

        if (reminderId == -1 || medicationName == null)
            return;

        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        // Tạo kênh cho Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Kênh thông tin bình thường
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Nhắc nhở uống thuốc",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Kênh thông báo nhắc nhở giờ uống thuốc");

            // Kênh thông tin Quan trọng (Bỏ qua DND)
            NotificationChannel criticalChannel = new NotificationChannel(
                    CRITICAL_CHANNEL_ID,
                    "Cảnh Báo Thuốc Khẩn Cấp",
                    NotificationManager.IMPORTANCE_HIGH);
            criticalChannel.setDescription("Kênh vượt Do Not Disturb cho thuốc quan trọng");
            criticalChannel.setBypassDnd(true);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                notificationManager.createNotificationChannel(criticalChannel);
            }
        }

        // Action "Đã uống"
        Intent takenIntent = new Intent(context, NotificationActionReceiver.class);
        takenIntent.setAction("ACTION_TAKEN");
        takenIntent.putExtra("reminderId", reminderId);
        PendingIntent takenPendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId * 10,
                takenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Action "Nhắc lại sau 10 phút"
        Intent snoozeIntent = new Intent(context, NotificationActionReceiver.class);
        snoozeIntent.setAction("ACTION_SNOOZE");
        snoozeIntent.putExtra("reminderId", reminderId);
        snoozeIntent.putExtra("medicationName", medicationName);
        snoozeIntent.putExtra("dosage", dosage);
        snoozeIntent.putExtra("isCritical", isCritical); // Chuyển tiếp trạng thái
        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId * 10 + 1,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (isCritical) {
            // Thiết lập Intent hiển thị toàn màn hình
            Intent fullScreenIntent = new Intent(context, CriticalAlertActivity.class);
            fullScreenIntent.putExtra("reminderId", reminderId);
            fullScreenIntent.putExtra("medicationName", medicationName);
            fullScreenIntent.putExtra("dosage", dosage);
            fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                    context,
                    reminderId,
                    fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CRITICAL_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("⚠ CẢNH BÁO KHẨN CẤP")
                    .setContentText(medicationName + " - Liều lượng: " + dosage)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setAutoCancel(true);

            if (notificationManager != null) {
                notificationManager.notify(reminderId, builder.build());
            }

            // Bắt đầu âm thanh báo động lớn
            AudioResourceHelper.startAlarmSound(context);

        } else {
            // Intent để mở ứng dụng khi bấm vào thông báo
            Intent mainIntent = new Intent(context, MainActivity.class);
            PendingIntent mainPendingIntent = PendingIntent.getActivity(
                    context,
                    reminderId,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Đến giờ uống thuốc!")
                    .setContentText(medicationName + " - Liều lượng: " + dosage)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(mainPendingIntent)
                    .addAction(android.R.drawable.ic_menu_add, "Đã uống", takenPendingIntent)
                    .addAction(android.R.drawable.ic_popup_reminder, "Nhắc lại sau  10p", snoozePendingIntent);

            if (notificationManager != null) {
                notificationManager.notify(reminderId, builder.build());
            }

            // Lập lịch cho worker nhắc nhở bắt đầu sau 5 phút nếu chưa uống thuốc
            NaggingWorker.scheduleNagging(context, reminderId, 5);
        }
    }
}
