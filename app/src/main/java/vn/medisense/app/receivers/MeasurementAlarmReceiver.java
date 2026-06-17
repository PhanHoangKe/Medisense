package vn.medisense.app.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import vn.medisense.app.R;
import vn.medisense.app.ui.MeasurementInputDialogActivity;

public class MeasurementAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "MEDISENSE_MEASUREMENT_CHANNEL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"vn.medisense.app.MEASUREMENT_ALARM".equals(intent.getAction())) {
            return;
        }

        int taskId = intent.getIntExtra("taskId", -1);
        String title = intent.getStringExtra("title");
        String type = intent.getStringExtra("type");

        if (taskId == -1 || type == null) return;

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Nhắc nhở đo sinh hiệu",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Kênh thông báo lịch đo huyết áp, đường huyết, v.v.");
            
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Hành động: Mở hộp thoại nhập trực tiếp
        Intent inputIntent = new Intent(context, MeasurementInputDialogActivity.class);
        inputIntent.putExtra("type", type);
        inputIntent.putExtra("title", title);
        inputIntent.putExtra("taskId", taskId);
        // Các flag quan trọng để hiển thị như tác vụ mới/trên màn hình khóa nếu cần
        inputIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent inputPendingIntent = PendingIntent.getActivity(
                context,
                taskId + 200000,
                inputIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText("Đã đến giờ đo " + getDisplayType(type) + "! Chạm vào đây để nhập nhanh.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(inputPendingIntent)
                .setFullScreenIntent(inputPendingIntent, true) // Actively interrupts, shows over lockscreen 
                .addAction(android.R.drawable.ic_menu_edit, "Nhập Ngay", inputPendingIntent);

        if (notificationManager != null) {
            notificationManager.notify(taskId + 100000, builder.build());
        }
    }

    private String getDisplayType(String type) {
        switch (type) {
            case "blood_pressure": return "Huyết áp";
            case "heart_rate": return "Nhịp tim";
            case "blood_sugar": return "Đường huyết";
            case "weight": return "Cân nặng";
            default: return type;
        }
    }
}
