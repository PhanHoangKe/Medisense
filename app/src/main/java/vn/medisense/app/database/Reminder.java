package vn.medisense.app.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "reminders",
        foreignKeys = @ForeignKey(
                entity = Medication.class,
                parentColumns = "id",
                childColumns = "medicationId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("medicationId")}
)
public class Reminder {

    // ─── Hằng số trạng thái ───────────────────────────────────────────────────
    /** Chưa đến giờ hoặc đang chờ uống */
    public static final String STATUS_PENDING  = "PENDING";
    /** Đã uống */
    public static final String STATUS_TAKEN    = "TAKEN";
    /** Quá giờ nhưng chưa uống (tự động sau gracePeriod) */
    public static final String STATUS_MISSED   = "MISSED";
    /** Người dùng bấm "Nhắc lại" — đang chờ alarm mới */
    public static final String STATUS_SNOOZED  = "SNOOZED";
    /** Người dùng chủ động bỏ qua liều này */
    public static final String STATUS_SKIPPED  = "SKIPPED";

    /** Thời gian ân hạn trước khi chuyển PENDING → MISSED (30 phút) */
    public static final long GRACE_PERIOD_MS = 30L * 60L * 1000L;

    // ─── Fields ───────────────────────────────────────────────────────────────
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int medicationId;

    /** Timestamp giờ uống theo lịch (epoch ms) */
    public long reminderTime;

    /**
     * Giữ lại để tương thích ngược với code cũ và BootReceiver.
     * Luôn đồng bộ với status: isTaken = STATUS_TAKEN.equals(status)
     */
    public boolean isTaken;

    /** Thời điểm người dùng đánh dấu đã uống */
    public long takenTime;

    // ─── Fields mới (migration 11 → 12) ──────────────────────────────────────
    /**
     * Trạng thái rõ ràng của liều thuốc.
     * Giá trị: PENDING | TAKEN | MISSED | SNOOZED | SKIPPED
     * Default: "PENDING"
     */
    @androidx.annotation.NonNull
    @androidx.room.ColumnInfo(name = "status", defaultValue = "'PENDING'")
    public String status;

    /** Thời điểm người dùng bấm Bỏ qua */
    @androidx.room.ColumnInfo(name = "skippedTime", defaultValue = "0")
    public long skippedTime;

    /** Lý do bỏ qua (do người dùng chọn hoặc nhập) */
    @androidx.room.ColumnInfo(name = "skipReason")
    public String skipReason;

    /** Alarm sẽ kêu lại lúc nào (epoch ms), 0 nếu không đang snooze */
    @androidx.room.ColumnInfo(name = "snoozeUntil", defaultValue = "0")
    public long snoozeUntil;

    /** Số lần nagging đã phát (để debug / thống kê) */
    @androidx.room.ColumnInfo(name = "naggingCount", defaultValue = "0")
    public int naggingCount;

    /** Đường dẫn hình ảnh đơn thuốc liên quan (nếu có) */
    public String imagePath;

    // ─── Constructor ──────────────────────────────────────────────────────────
    public Reminder(int medicationId, long reminderTime) {
        this.medicationId  = medicationId;
        this.reminderTime  = reminderTime;
        this.isTaken       = false;
        this.takenTime     = 0L;
        this.status        = STATUS_PENDING;
        this.skippedTime   = 0L;
        this.skipReason    = null;
        this.snoozeUntil   = 0L;
        this.naggingCount  = 0;
        this.imagePath     = null;
    }

    // ─── Helper methods ───────────────────────────────────────────────────────

    /** Kiểm tra liều này đã quá giờ ân hạn chưa */
    public boolean isOverdue() {
        return System.currentTimeMillis() > (reminderTime + GRACE_PERIOD_MS);
    }

    /** Trả về true nếu trạng thái là TAKEN */
    public boolean isTakenStatus() {
        return STATUS_TAKEN.equals(status);
    }

    /** Trả về true nếu trạng thái là MISSED */
    public boolean isMissed() {
        return STATUS_MISSED.equals(status);
    }

    /** Trả về true nếu trạng thái là SKIPPED */
    public boolean isSkipped() {
        return STATUS_SKIPPED.equals(status);
    }

    /** Trả về true nếu trạng thái là SNOOZED */
    public boolean isSnoozed() {
        return STATUS_SNOOZED.equals(status);
    }

    /**
     * Đặt trạng thái TAKEN và đồng bộ isTaken.
     * Chỉ trừ stock nếu trước đó chưa phải TAKEN (tránh double-decrement).
     * @return true nếu đây là lần đầu đánh dấu TAKEN (cần trừ stock)
     */
    public boolean markTaken(long now) {
        boolean wasAlreadyTaken = STATUS_TAKEN.equals(this.status);
        this.status   = STATUS_TAKEN;
        this.isTaken  = true;
        this.takenTime = now;
        return !wasAlreadyTaken; // true = cần trừ stock
    }

    /**
     * Bỏ đánh dấu TAKEN — quay về PENDING hoặc MISSED tùy thời gian.
     * @return true nếu trước đó là TAKEN (cần cộng lại stock)
     */
    public boolean unmarkTaken() {
        boolean wasTaken = STATUS_TAKEN.equals(this.status);
        this.isTaken   = false;
        this.takenTime = 0L;
        // Nếu đã quá giờ ân hạn → MISSED, ngược lại → PENDING
        this.status = isOverdue() ? STATUS_MISSED : STATUS_PENDING;
        return wasTaken; // true = cần cộng lại stock
    }

    /** Đặt trạng thái SKIPPED */
    public void markSkipped(long now, String reason) {
        this.status      = STATUS_SKIPPED;
        this.isTaken     = false;
        this.skippedTime = now;
        this.skipReason  = reason;
    }

    /** Đặt trạng thái SNOOZED */
    public void markSnoozed(long snoozeUntilMs) {
        this.status      = STATUS_SNOOZED;
        this.isTaken     = false;
        this.snoozeUntil = snoozeUntilMs;
    }

    /** Đặt trạng thái MISSED */
    public void markMissed() {
        this.status  = STATUS_MISSED;
        this.isTaken = false;
    }
}
