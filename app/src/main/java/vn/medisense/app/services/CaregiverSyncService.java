package vn.medisense.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import vn.medisense.app.R;
import vn.medisense.app.database.Reminder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * CaregiverSyncService — Foreground service lắng nghe Firebase Realtime DB.
 *
 * Gửi notification thông minh theo status:
 *   TAKEN   → "Đã uống [thuốc]"
 *   MISSED  → "Đã bỏ lỡ [thuốc]"
 *   SKIPPED → "Đã bỏ qua [thuốc] — [lý do]"
 *   SNOOZED → "Đã nhắc lại [thuốc]"
 *   PENDING → không gửi notification
 *
 * Chống spam: chỉ notify log có actionTime > lastProcessedTime.
 * Lưu lastProcessedTime vào SharedPreferences để tồn tại qua restart.
 */
public class CaregiverSyncService extends Service {

    private static final String CHANNEL_ID          = "CaregiverSyncChannel";
    private static final String PREF_LAST_PROCESSED = "caregiver_last_processed_time";

    private DatabaseReference familyRef;
    private ValueEventListener syncListener;
    /** Thời điểm service khởi động — dùng làm baseline lần đầu */
    private long lastLaunchTime;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        // Đọc lastProcessedTime từ prefs để tránh spam sau khi service restart
        SharedPreferences prefs = getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE);
        lastLaunchTime = prefs.getLong(PREF_LAST_PROCESSED, System.currentTimeMillis());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MediSense Caregiver")
                .setContentText("Đang theo dõi lịch uống thuốc của người thân...")
                .setSmallIcon(R.drawable.ic_medication)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(2, notification);
        startListeningToFirebase();
        return START_STICKY;
    }

    private void startListeningToFirebase() {
        SharedPreferences prefs = getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE);
        String familyCode = prefs.getString("monitoringCode", null);
        String userRole   = prefs.getString("userRole", null);

        if (familyCode == null || !"caregiver".equals(userRole)) {
            stopSelf();
            return;
        }

        familyRef = FirebaseDatabase.getInstance().getReference()
                .child("families").child(familyCode).child("logs");

        syncListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long latestActionTime = lastLaunchTime;

                for (DataSnapshot logSnapshot : snapshot.getChildren()) {
                    // ── Đọc actionTime (field mới) hoặc fallback timestamp (cũ) ──
                    Long actionTime = logSnapshot.child("actionTime").getValue(Long.class);
                    if (actionTime == null) {
                        actionTime = logSnapshot.child("timestamp").getValue(Long.class);
                    }
                    if (actionTime == null || actionTime <= lastLaunchTime) continue;

                    String medName   = logSnapshot.child("medicationName").getValue(String.class);
                    String status    = logSnapshot.child("status").getValue(String.class);
                    String skipReason = logSnapshot.child("skipReason").getValue(String.class);

                    // ── Fallback tương thích ngược ──────────────────────────
                    if (status == null || status.isEmpty()) {
                        Boolean isTaken = logSnapshot.child("isTaken").getValue(Boolean.class);
                        status = (isTaken != null && isTaken)
                                ? Reminder.STATUS_TAKEN
                                : Reminder.STATUS_PENDING;
                    }

                    // ── Không notify PENDING ─────────────────────────────────
                    if (Reminder.STATUS_PENDING.equals(status)) continue;

                    // ── Gửi notification theo status ─────────────────────────
                    sendStatusNotification(medName, status, skipReason);

                    // Cập nhật mốc thời gian mới nhất
                    if (actionTime > latestActionTime) {
                        latestActionTime = actionTime;
                    }
                }

                // Lưu lại để tránh spam sau restart
                if (latestActionTime > lastLaunchTime) {
                    lastLaunchTime = latestActionTime;
                    getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE)
                            .edit()
                            .putLong(PREF_LAST_PROCESSED, lastLaunchTime)
                            .apply();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Bỏ qua lỗi Firebase — service vẫn chạy
            }
        };

        familyRef.addValueEventListener(syncListener);
    }

    /**
     * Gửi notification với nội dung phù hợp theo status.
     */
    private void sendStatusNotification(String medicationName, String status, String skipReason) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        String safeName = medicationName != null ? medicationName : "thuốc";

        String title;
        String body;
        int priority;

        switch (status) {
            case Reminder.STATUS_TAKEN:
                title    = "Người thân đã uống thuốc ✓";
                body     = "Đã uống: " + safeName;
                priority = NotificationCompat.PRIORITY_DEFAULT;
                break;

            case Reminder.STATUS_MISSED:
                title    = "Người thân đã bỏ lỡ liều thuốc ✗";
                body     = "Bỏ lỡ: " + safeName;
                priority = NotificationCompat.PRIORITY_HIGH;
                break;

            case Reminder.STATUS_SKIPPED:
                title = "Người thân đã bỏ qua liều thuốc ⊘";
                body  = "Bỏ qua: " + safeName;
                if (skipReason != null && !skipReason.isEmpty()) {
                    body += " — " + skipReason;
                }
                priority = NotificationCompat.PRIORITY_DEFAULT;
                break;

            case Reminder.STATUS_SNOOZED:
                title    = "Người thân đã nhắc lại ⏰";
                body     = "Nhắc lại: " + safeName;
                priority = NotificationCompat.PRIORITY_LOW;
                break;

            default:
                return; // Không notify trạng thái khác
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(priority)
                .setAutoCancel(true);

        // Dùng hashCode của (medName + status + time) làm notificationId để tránh ghi đè
        int notifId = (safeName + status + System.currentTimeMillis()).hashCode();
        nm.notify(notifId, builder.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (familyRef != null && syncListener != null) {
            familyRef.removeEventListener(syncListener);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Caregiver Sync Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
