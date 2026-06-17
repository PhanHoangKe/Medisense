package vn.medisense.app.utils;

import android.content.Context;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.MedicationDao;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.database.ReminderWithMedication;
import vn.medisense.app.workers.NaggingWorker;
import vn.medisense.app.workers.SideEffectCheckInWorker;

/**
 * ReminderStatusHelper — Trung tâm xử lý thay đổi trạng thái reminder.
 *
 * Tất cả logic liên quan đến TAKEN / SKIPPED / SNOOZED / MISSED đều đi qua đây.
 * Đảm bảo:
 *   - Không double-decrement stock
 *   - Không double-increment stock
 *   - Caregiver log luôn được ghi
 *   - NaggingWorker luôn được dừng khi cần
 *   - SideEffectCheckIn luôn được lên lịch sau khi TAKEN
 *
 * Tất cả phương thức phải được gọi trên background thread (diskIO).
 */
public class ReminderStatusHelper {

    // ─── Đánh dấu ĐÃ UỐNG ────────────────────────────────────────────────────

    /**
     * Đánh dấu reminder là TAKEN.
     * - Chỉ trừ stock nếu trước đó chưa phải TAKEN (tránh double-decrement).
     * - Dừng NaggingWorker.
     * - Lên lịch SideEffectCheckIn sau 30 phút.
     * - Ghi caregiver log.
     *
     * @param context  Application context
     * @param item     ReminderWithMedication cần cập nhật
     */
    public static void markTaken(Context context, ReminderWithMedication item) {
        if (item == null || item.reminder == null) return;

        MedicationDao dao = AppDatabase.getInstance(context).medicationDao();
        long now = System.currentTimeMillis();

        // Kiểm tra trạng thái trước để tránh double-decrement
        String previousStatus = dao.getReminderStatus(item.reminder.id);
        boolean wasAlreadyTaken = Reminder.STATUS_TAKEN.equals(previousStatus);

        // Cập nhật DB
        dao.updateReminderStatusFull(
                item.reminder.id,
                Reminder.STATUS_TAKEN,
                true,
                now
        );



        // Trừ stock CHỈ nếu đây là lần đầu TAKEN
        if (!wasAlreadyTaken && item.medication != null) {
            StockManager.handleMedicationTaken(context, item.medication.id, true);
        }

        // Dừng nagging
        NaggingWorker.stopNagging(context, item.reminder.id);

        // Lên lịch hỏi tác dụng phụ sau 30 phút
        if (item.medication != null) {
            SideEffectCheckInWorker.scheduleCheckIn(
                    context, item.medication.id, item.medication.name, 30);
        }

        // Ghi log cho caregiver
        FamilyInteractionHelper.logMedicationTakeEvent(context, item, true);
    }

    // ─── Bỏ đánh dấu ĐÃ UỐNG ────────────────────────────────────────────────

    /**
     * Bỏ đánh dấu TAKEN — quay về PENDING hoặc MISSED tùy thời gian.
     * - Cộng lại stock CHỈ nếu trước đó là TAKEN.
     * - Ghi caregiver log.
     *
     * @param context Application context
     * @param item    ReminderWithMedication cần cập nhật
     */
    public static void unmarkTaken(Context context, ReminderWithMedication item) {
        if (item == null || item.reminder == null) return;

        MedicationDao dao = AppDatabase.getInstance(context).medicationDao();

        String previousStatus = dao.getReminderStatus(item.reminder.id);
        boolean wasTaken = Reminder.STATUS_TAKEN.equals(previousStatus);

        // Xác định trạng thái mới
        String newStatus = item.reminder.isOverdue()
                ? Reminder.STATUS_MISSED
                : Reminder.STATUS_PENDING;

        dao.updateReminderStatusFull(item.reminder.id, newStatus, false, 0L);



        // Cộng lại stock CHỈ nếu trước đó là TAKEN
        if (wasTaken && item.medication != null) {
            StockManager.handleMedicationTaken(context, item.medication.id, false);
        }

        // Ghi log cho caregiver (isTaken=false)
        FamilyInteractionHelper.logMedicationTakeEvent(context, item, false);
    }

    /**
     * Bắt buộc chuyển trạng thái reminder về PENDING (Chưa uống).
     * Cộng lại stock nếu trước đó là TAKEN.
     */
    public static void forcePending(Context context, ReminderWithMedication item) {
        if (item == null || item.reminder == null) return;

        MedicationDao dao = AppDatabase.getInstance(context).medicationDao();
        String previousStatus = dao.getReminderStatus(item.reminder.id);
        boolean wasTaken = Reminder.STATUS_TAKEN.equals(previousStatus);

        dao.updateReminderStatusFull(item.reminder.id, Reminder.STATUS_PENDING, false, 0L);



        // Cộng lại stock nếu trước đó là TAKEN
        if (wasTaken && item.medication != null) {
            StockManager.handleMedicationTaken(context, item.medication.id, false);
        }

        // Ghi log cho caregiver
        FamilyInteractionHelper.logMedicationTakeEvent(context, item, false);
    }

    // ─── Bỏ qua liều thuốc ───────────────────────────────────────────────────

    /**
     * Đánh dấu reminder là SKIPPED.
     * - KHÔNG trừ stock.
     * - Ghi skipReason vào DB.
     * - Ghi caregiver log với skipReason.
     * - Nếu lý do là "Có tác dụng phụ", caller nên mở SideEffectBottomSheet.
     *
     * @param context    Application context
     * @param item       ReminderWithMedication cần cập nhật
     * @param skipReason Lý do bỏ qua (từ danh sách gợi ý hoặc nhập tự do)
     */
    public static void markSkipped(Context context, ReminderWithMedication item, String skipReason) {
        if (item == null || item.reminder == null) return;

        MedicationDao dao = AppDatabase.getInstance(context).medicationDao();
        long now = System.currentTimeMillis();

        dao.updateReminderSkipped(
                item.reminder.id,
                Reminder.STATUS_SKIPPED,
                now,
                skipReason
        );



        // Dừng nagging nếu đang chạy
        NaggingWorker.stopNagging(context, item.reminder.id);

        // Ghi log cho caregiver với skipReason
        FamilyInteractionHelper.logMedicationTakeEvent(context, item, false, skipReason);
    }

    // ─── Nhắc lại (Snooze) ───────────────────────────────────────────────────

    /**
     * Đánh dấu reminder là SNOOZED và reschedule alarm.
     * - KHÔNG trừ stock.
     * - Lưu snoozeUntil vào DB.
     *
     * @param context        Application context
     * @param item           ReminderWithMedication cần cập nhật
     * @param snoozeMinutes  Số phút nhắc lại (thường là 10)
     */
    public static void markSnoozed(Context context, ReminderWithMedication item, int snoozeMinutes) {
        if (item == null || item.reminder == null) return;

        MedicationDao dao = AppDatabase.getInstance(context).medicationDao();
        long snoozeUntil = System.currentTimeMillis() + (long) snoozeMinutes * 60 * 1000;

        dao.updateReminderSnoozed(
                item.reminder.id,
                Reminder.STATUS_SNOOZED,
                snoozeUntil
        );



        // Dừng nagging cũ trước khi reschedule
        NaggingWorker.stopNagging(context, item.reminder.id);

        // Reschedule alarm
        if (item.medication != null) {
            AlarmHelper.scheduleAlarm(
                    context,
                    item.reminder.id,
                    snoozeUntil,
                    item.medication.name,
                    item.medication.dosage,
                    item.medication.isCritical
            );
        }
    }

    // ─── Chuyển sang MISSED ───────────────────────────────────────────────────

    /**
     * Chuyển reminder sang MISSED nếu đã quá giờ ân hạn.
     * Được gọi bởi một background job hoặc khi mở app.
     * - KHÔNG trừ stock.
     *
     * @param context    Application context
     * @param reminderId ID của reminder cần kiểm tra
     * @return true nếu đã chuyển sang MISSED
     */
    public static boolean checkAndMarkMissedIfOverdue(Context context, int reminderId) {
        MedicationDao dao = AppDatabase.getInstance(context).medicationDao();
        Reminder reminder = dao.getReminderByIdSync(reminderId);
        if (reminder == null) return false;

        // Chỉ chuyển MISSED nếu đang PENDING hoặc SNOOZED và đã quá giờ ân hạn
        boolean isPendingOrSnoozed = Reminder.STATUS_PENDING.equals(reminder.status)
                || Reminder.STATUS_SNOOZED.equals(reminder.status);

        if (isPendingOrSnoozed && reminder.isOverdue()) {
            dao.updateReminderMissed(reminderId);
            return true;
        }
        return false;
    }

    /**
     * Quét tất cả reminder PENDING/SNOOZED đã quá giờ ân hạn và chuyển sang MISSED.
     * Nên gọi khi mở app hoặc sau khi BootReceiver chạy.
     *
     * @param context Application context
     */
    public static void markAllOverdueAsMissed(Context context) {
        MedicationDao dao = AppDatabase.getInstance(context).medicationDao();
        long now = System.currentTimeMillis();
        java.util.List<Reminder> overdueList = dao.getOverdueRemindersSync(now);
        if (overdueList == null || overdueList.isEmpty()) return;

        for (Reminder r : overdueList) {
            dao.updateReminderMissed(r.id);
        }
    }
}
