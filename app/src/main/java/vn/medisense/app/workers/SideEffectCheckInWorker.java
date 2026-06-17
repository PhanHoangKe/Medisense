package vn.medisense.app.workers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import vn.medisense.app.MainActivity;
import vn.medisense.app.R;

import java.util.concurrent.TimeUnit;

/**
 * Worker gửi thông báo kiểm tra (Check-in) 30-60 phút sau khi uống thuốc
 */
public class SideEffectCheckInWorker extends Worker {

    public static final String KEY_MED_ID = "med_id";
    public static final String KEY_MED_NAME = "med_name";
    private static final String CHANNEL_ID = "side_effect_checkin";

    public SideEffectCheckInWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        int medId = getInputData().getInt(KEY_MED_ID, -1);
        String medName = getInputData().getString(KEY_MED_NAME);

        if (medId != -1 && medName != null) {
            sendNotification(medId, medName);
        }

        return Result.success();
    }

    private void sendNotification(int medId, String medName) {
        Context context = getApplicationContext();
        NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Tạo channel thông báo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Nhật ký phản ứng thuốc",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }

        // Hành động để mở Bottom Sheet thông qua MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("ACTION_OPEN_SIDE_EFFECT", true);
        intent.putExtra("MEDICATION_ID", medId);
        intent.putExtra("MEDICATION_NAME", medName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                medId, // Sử dụng medId làm mã yêu cầu để giữ các thông báo khác biệt
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_medication)
                .setContentTitle("Báo cáo sức khỏe")
                .setContentText("Bạn cảm thấy thế nào sau khi uống " + medName + "? Hãy dành 5 giây để cập nhật nhé.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(medId, builder.build());
    }

    /**
     * Hàm hỗ trợ để lập lịch kiểm tra
     * @param context Ngữ cảnh ứng dụng
     * @param medicationId ID của thuốc đã uống
     * @param medicationName Tên của thuốc
     * @param delayMinutes Thời gian chờ trước khi hỏi (ví dụ: 30)
     */
    public static void scheduleCheckIn(Context context, int medicationId, String medicationName, int delayMinutes) {
        Data inputData = new Data.Builder()
                .putInt(KEY_MED_ID, medicationId)
                .putString(KEY_MED_NAME, medicationName)
                .build();

        OneTimeWorkRequest checkInWork = new OneTimeWorkRequest.Builder(SideEffectCheckInWorker.class)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(inputData)
                .build();

        // Sắp xếp công việc duy nhất để tránh trùng lặp nếu người dùng đánh dấu đã uống nhiều lần nhanh chóng
        WorkManager.getInstance(context).enqueueUniqueWork(
                "CheckIn_" + medicationId,
                androidx.work.ExistingWorkPolicy.REPLACE,
                checkInWork
        );
    }
}
