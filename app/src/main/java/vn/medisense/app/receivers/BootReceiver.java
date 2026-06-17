package vn.medisense.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.MedicationDao;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.database.ReminderWithMedication;
import vn.medisense.app.utils.AlarmHelper;
import vn.medisense.app.utils.AppExecutors;
import vn.medisense.app.utils.ProfileManager;
import vn.medisense.app.services.MedicalIdService;

import java.util.List;

/**
 * BootReceiver — Khôi phục alarm sau khi thiết bị khởi động lại.
 *
 * Chỉ schedule lại alarm cho reminder có status:
 *   PENDING  → schedule tại reminderTime (nếu còn trong tương lai)
 *   SNOOZED  → schedule tại snoozeUntil (nếu còn trong tương lai), fallback reminderTime
 *
 * Không schedule lại: TAKEN, MISSED, SKIPPED.
 * Tương thích ngược: nếu status null, dùng isTaken để quyết định.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        // 1. Restart MedicalIdService nếu SOS đang bật và user còn login
        ProfileManager profileManager = new ProfileManager(context);
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null && profileManager.isSosEnabled()) {
            Intent serviceIntent = new Intent(context, MedicalIdService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }

        // 2. Khôi phục alarm trên background thread
        AppExecutors.getInstance().diskIO().execute(() -> {
            long now = System.currentTimeMillis();
            MedicationDao dao = AppDatabase.getInstance(context).medicationDao();

            // Lấy tất cả reminder chưa uống trong tương lai (isTaken=0, reminderTime > now)
            // Query cũ vẫn dùng được vì isTaken được đồng bộ với status
            List<ReminderWithMedication> candidates =
                    dao.getPendingRemindersWithMedication(now);

            int scheduled = 0;
            int skipped   = 0;

            for (ReminderWithMedication rwm : candidates) {
                if (rwm.reminder == null || rwm.medication == null) continue;

                Reminder r = rwm.reminder;

                // ── Resolve status (tương thích ngược nếu status null) ──────
                String status = r.status;
                if (status == null || status.isEmpty()) {
                    status = r.isTaken ? Reminder.STATUS_TAKEN : Reminder.STATUS_PENDING;
                }

                // ── Bỏ qua TAKEN / MISSED / SKIPPED ─────────────────────────
                switch (status) {
                    case Reminder.STATUS_TAKEN:
                    case Reminder.STATUS_MISSED:
                    case Reminder.STATUS_SKIPPED:
                        skipped++;
                        continue;
                }

                // ── Xác định thời điểm alarm ─────────────────────────────────
                long alarmTime;
                if (Reminder.STATUS_SNOOZED.equals(status) && r.snoozeUntil > now) {
                    // SNOOZED: ưu tiên snoozeUntil
                    alarmTime = r.snoozeUntil;
                } else if (r.reminderTime > now) {
                    // PENDING hoặc SNOOZED với snoozeUntil đã qua → dùng reminderTime
                    alarmTime = r.reminderTime;
                } else {
                    // Reminder đã qua nhưng chưa được đánh dấu → bỏ qua, sẽ thành MISSED
                    skipped++;
                    continue;
                }

                // ── Schedule alarm ───────────────────────────────────────────
                AlarmHelper.scheduleAlarm(
                        context.getApplicationContext(),
                        r.id,
                        alarmTime,
                        rwm.medication.name,
                        rwm.medication.dosage,
                        rwm.medication.isCritical
                );
                scheduled++;
            }

            // 3. Khôi phục alarm đo chỉ số
            vn.medisense.app.database.MeasurementTaskDao measurementTaskDao = AppDatabase.getInstance(context).measurementTaskDao();
            List<vn.medisense.app.database.MeasurementTask> measurementTasks = measurementTaskDao.getActiveTasksSync();
            for (vn.medisense.app.database.MeasurementTask task : measurementTasks) {
                vn.medisense.app.utils.MeasurementAlarmHelper.scheduleAlarm(
                        context.getApplicationContext(),
                        task.id,
                        task.title,
                        task.type,
                        task.timeOfDay
                );
            }

            Log.d(TAG, "BootReceiver: medication scheduled=" + scheduled + ", skipped=" + skipped + ", measurement tasks scheduled=" + measurementTasks.size());
        });
    }
}
