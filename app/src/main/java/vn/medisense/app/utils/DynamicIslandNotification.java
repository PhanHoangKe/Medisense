package vn.medisense.app.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;

import vn.medisense.app.receivers.NotificationActionReceiver;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import vn.medisense.app.MainActivity;
import vn.medisense.app.R;

/**
 * DynamicIslandNotification - iOS-style Dynamic Island for Android
 * Pill-shaped expandable notification with smooth animations
 */
public class DynamicIslandNotification {

    private static final String CHANNEL_ID_MEDICATION = "medication_reminder";
    private static final String CHANNEL_ID_MEASUREMENT = "measurement_reminder";
    private static final String CHANNEL_ID_URGENT = "urgent_alerts";
    private static final String CHANNEL_ID_CELEBRATION = "celebration";

    public enum NotificationType {
        MEDICATION,     // Full-screen interruptive + sound
        MEASUREMENT,    // Banner notification
        URGENT,         // Urgent + vibration pattern
        CELEBRATION     // Thông báo nhỏ ăn mừng
    }

    public enum NotificationState {
        COMPACT,    // Small pill
        EXPANDED,   // Full info
        MINIMAL     // Progress only
    }

    private final Context context;
    private final NotificationManager notificationManager;
    private final Vibrator vibrator;
    private final Handler handler;
    private NotificationState currentState = NotificationState.COMPACT;
    private View islandView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams islandParams;

    private int notificationId = 1000;

    public DynamicIslandNotification(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
        createNotificationChannels();
    }

    /**
     * Create notification channels for Android O+
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Kênh thuốc - Quan trọng với âm thanh
            NotificationChannel medicationChannel = new NotificationChannel(
                    CHANNEL_ID_MEDICATION,
                    "Nhắc uống thuốc",
                    NotificationManager.IMPORTANCE_HIGH
            );
            medicationChannel.setDescription("Thông báo nhắc uống thuốc quan trọng");
            medicationChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
            medicationChannel.enableVibration(true);
            medicationChannel.setVibrationPattern(new long[]{0, 500, 200, 500});
            notificationManager.createNotificationChannel(medicationChannel);

            // Measurement channel - Medium importance
            NotificationChannel measurementChannel = new NotificationChannel(
                    CHANNEL_ID_MEASUREMENT,
                    "Nhắc đo chỉ số",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            measurementChannel.setDescription("Thông báo nhắc đo chỉ số sức khỏe");
            measurementChannel.enableVibration(true);
            measurementChannel.setVibrationPattern(new long[]{0, 300});
            notificationManager.createNotificationChannel(measurementChannel);

            // Kênh khẩn cấp - Quan trọng với rung đặc biệt
            NotificationChannel urgentChannel = new NotificationChannel(
                    CHANNEL_ID_URGENT,
                    "Cảnh báo khẩn cấp",
                    NotificationManager.IMPORTANCE_HIGH
            );
            urgentChannel.setDescription("Cảnh báo sức khỏe cần chú ý ngay");
            urgentChannel.enableVibration(true);
            urgentChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000, 500, 1000});
            urgentChannel.setLightColor(Color.RED);
            urgentChannel.enableLights(true);
            notificationManager.createNotificationChannel(urgentChannel);

            // Celebration channel - Low importance
            NotificationChannel celebrationChannel = new NotificationChannel(
                    CHANNEL_ID_CELEBRATION,
                    "Chúc mừng thành tích",
                    NotificationManager.IMPORTANCE_LOW
            );
            celebrationChannel.setDescription("Thông báo hoàn thành mục tiêu sức khỏe");
            notificationManager.createNotificationChannel(celebrationChannel);
        }
    }

    /**
     * Show Dynamic Island style notification
     */
    public void showIslandNotification(NotificationType type, String title, String message, int progress) {
        switch (type) {
            case MEDICATION:
                showMedicationIsland(title, message, progress);
                break;
            case MEASUREMENT:
                showMeasurementIsland(title, message);
                break;
            case URGENT:
                showUrgentIsland(title, message);
                break;
            case CELEBRATION:
                showCelebrationIsland(title, message);
                break;
        }
    }

    /**
     * Medication notification - Full pill with progress
     */
    private void showMedicationIsland(String medicationName, String dosage, int progressPercent) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Tạo thông báo có thể mở rộng
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MEDICATION)
                .setSmallIcon(R.drawable.ic_medication)
                .setContentTitle("💊 Đến giờ uống thuốc")
                .setContentText(medicationName + " - " + dosage)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setProgress(100, progressPercent, false)
                .addAction(R.drawable.ic_check, "Đã uống", createActionIntent("taken", notificationId))
                .addAction(R.drawable.ic_notification, "Nhắc sau", createActionIntent("snooze", notificationId));

        // Kiểu văn bản lớn để mở rộng
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle("💊 " + medicationName);
        bigTextStyle.bigText("Liều dùng: " + dosage + "\n\nĐã uống thuốc chưa?");
        builder.setStyle(bigTextStyle);

        // Thông báo thuốc tùy chỉnh cho Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }

        notificationManager.notify(notificationId++, builder.build());

        // Kích hoạt rung cho khẩn cấp
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 500, 200, 500}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 500, 200, 500}, -1);
            }
        }
    }

    /**
     * Measurement notification - Banner style
     */
    private void showMeasurementIsland(String title, String message) {
        Intent intent = new Intent(context, vn.medisense.app.ui.HealthTrackerActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MEASUREMENT)
                .setSmallIcon(R.drawable.ic_health)
                .setContentTitle("📊 " + title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_check, "Đã đo", createActionIntent("measured", notificationId))
                .addAction(R.drawable.ic_notification, "Nhắc sau 15p", createActionIntent("snooze_15", notificationId));

        notificationManager.notify(notificationId++, builder.build());
    }

    /**
     * Urgent notification - Critical alert
     */
    private void showUrgentIsland(String title, String message) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_URGENT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⚠️ " + title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setColor(Color.RED)
                .setLights(Color.RED, 1000, 1000);

        // Ý định toàn màn hình cho cảnh báo quan trọng
        builder.setFullScreenIntent(pendingIntent, true);

        notificationManager.notify(notificationId++, builder.build());

        // Mẫu rung mạnh
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 1000, 500, 1000, 500, 1000}, 0));
            } else {
                vibrator.vibrate(new long[]{0, 1000, 500, 1000, 500, 1000}, 0);
            }
        }
    }

    /**
     * Celebration notification - Mini achievement
     */
    private void showCelebrationIsland(String title, String message) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_CELEBRATION)
                .setSmallIcon(R.drawable.ic_check)
                .setContentTitle("🎉 " + title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        // Thông báo nhỏ tự động đóng
        notificationManager.notify(notificationId++, builder.build());

        // Tự động đóng sau 5 giây
        handler.postDelayed(() -> {
            notificationManager.cancel(notificationId - 1);
        }, 5000);
    }

    /**
     * Create action pending intent
     */
    private PendingIntent createActionIntent(String action, int id) {
        Intent intent = new Intent(context, NotificationActionReceiver.class);
        intent.setAction("vn.medisense.app." + action);
        intent.putExtra("notification_id", id);
        return PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Hiển thị đảo nổi trong ứng dụng (kiểu lớp phủ)
     */
    public void showInAppIsland(String title, String subtitle, int iconRes, int progress) {
        // Xóa đảo hiện có
        hideInAppIsland();

        // Tạo view đảo
        LayoutInflater inflater = LayoutInflater.from(context);
        islandView = inflater.inflate(R.layout.dynamic_island_notification, null);

        // Setup views
        ImageView iconView = islandView.findViewById(R.id.islandIcon);
        TextView titleView = islandView.findViewById(R.id.islandTitle);
        TextView subtitleView = islandView.findViewById(R.id.islandSubtitle);
        ProgressBar progressBar = islandView.findViewById(R.id.islandProgress);
        LinearLayout expandedContent = islandView.findViewById(R.id.expandedContent);

        iconView.setImageResource(iconRes);
        titleView.setText(title);
        subtitleView.setText(subtitle);
        progressBar.setProgress(progress);

        // Nhấn để mở rộng/thu gọn
        islandView.setOnClickListener(v -> {
            if (currentState == NotificationState.COMPACT) {
                expandIsland(expandedContent);
            } else {
                collapseIsland(expandedContent);
            }
        });

        // Thêm vào cửa sổ
        if (windowManager == null) {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }

        islandParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        islandParams.gravity = android.view.Gravity.TOP;
        islandParams.y = 20;

        try {
            windowManager.addView(islandView, islandParams);

            // Animate in
            Animation slideDown = AnimationUtils.loadAnimation(context, R.anim.slide_in_right);
            islandView.startAnimation(slideDown);

            // Tự động thu gọn sau 3 giây
            handler.postDelayed(() -> {
                if (currentState == NotificationState.EXPANDED) {
                    collapseIsland(expandedContent);
                }
            }, 3000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Expand island to show full content
     */
    private void expandIsland(View expandedContent) {
        currentState = NotificationState.EXPANDED;
        expandedContent.setVisibility(View.VISIBLE);

        Animation expandAnim = AnimationUtils.loadAnimation(context, android.R.anim.fade_in);
        expandedContent.startAnimation(expandAnim);

        // Cập nhật chiều cao cửa sổ
        if (islandParams != null && windowManager != null) {
            islandParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            windowManager.updateViewLayout(islandView, islandParams);
        }
    }

    /**
     * Collapse island to compact form
     */
    private void collapseIsland(View expandedContent) {
        currentState = NotificationState.COMPACT;

        Animation collapseAnim = AnimationUtils.loadAnimation(context, android.R.anim.fade_out);
        collapseAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                expandedContent.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        expandedContent.startAnimation(collapseAnim);
    }

    /**
     * Hide in-app island
     */
    public void hideInAppIsland() {
        if (islandView != null && windowManager != null) {
            Animation slideUp = AnimationUtils.loadAnimation(context, R.anim.slide_out_left);
            slideUp.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    try {
                        windowManager.removeView(islandView);
                    } catch (Exception e) {
                        // View đã được xóa
                    }
                    islandView = null;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            islandView.startAnimation(slideUp);
        }
    }

    /**
     * Update progress of current notification
     */
    public void updateProgress(int id, int progress) {
        // Đối với thông báo hệ thống, chúng ta cần xây dựng lại
        // Đối với island trong ứng dụng, cập nhật trực tiếp
        if (islandView != null) {
            ProgressBar progressBar = islandView.findViewById(R.id.islandProgress);
            if (progressBar != null) {
                progressBar.setProgress(progress);
            }
        }
    }

    /**
     * Cancel notification by ID
     */
    public void cancelNotification(int id) {
        notificationManager.cancel(id);
        if (islandView != null) {
            hideInAppIsland();
        }
    }

    /**
     * Cancel all notifications
     */
    public void cancelAll() {
        notificationManager.cancelAll();
        hideInAppIsland();
    }

}
