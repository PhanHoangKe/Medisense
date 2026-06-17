package vn.medisense.app.ui;

import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.databinding.ActivityCriticalAlertBinding;
import vn.medisense.app.utils.AlarmHelper;
import vn.medisense.app.utils.AudioResourceHelper;

import vn.medisense.app.utils.AppExecutors;

public class CriticalAlertActivity extends AppCompatActivity {

    private ActivityCriticalAlertBinding binding;
    private int reminderId;
    private String medicationName;
    private String dosage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Thiết lập Window Flags để Bật Màn hình và Hiển thị Trên Màn hình Khóa
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        // Giữ màn hình bật khi chuông reo
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding = ActivityCriticalAlertBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Extract Intent Data
        reminderId = getIntent().getIntExtra("reminderId", -1);
        medicationName = getIntent().getStringExtra("medicationName");
        dosage = getIntent().getStringExtra("dosage");

        if (medicationName != null) {
            binding.textMedicationName.setText(medicationName);
            binding.textDosage.setText(dosage);
        }

        binding.buttonTaken.setOnClickListener(v -> markAsTaken());
        binding.buttonSnooze.setOnClickListener(v -> snoozeAlarm());
    }

    private void markAsTaken() {
        stopRingingAndClearNotification();

        if (reminderId != -1) {
            AppExecutors.getInstance().diskIO().execute(() -> {
                AppDatabase.getInstance(this).medicationDao().updateReminderStatus(reminderId, true);
            });
            Toast.makeText(this, "Đã đánh dấu uống thuốc", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void snoozeAlarm() {
        stopRingingAndClearNotification();

        if (reminderId != -1) {
            long snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000L;
            AlarmHelper.scheduleAlarm(this, reminderId, snoozeTime, medicationName, dosage, true);
            Toast.makeText(this, "Sẽ nhắc lại cảnh báo sau 10 phút", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void stopRingingAndClearNotification() {
        // Dừng chuông báo thức lặp to
        AudioResourceHelper.stopAlarmSound();

        // Xóa thông báo heads-up thực tế từ thanh thông báo
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && reminderId != -1) {
            manager.cancel(reminderId);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Dự phòng: Đảm bảo báo thức dừng nếu activity bị vuốt/hủy mà không có
        // tương tác
        AudioResourceHelper.stopAlarmSound();
    }
}
