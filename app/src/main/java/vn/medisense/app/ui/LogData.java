package vn.medisense.app.ui;

import vn.medisense.app.database.Reminder;

/**
 * LogData — Model hiển thị log uống thuốc trong CaregiverDashboard.
 * Hỗ trợ cả dữ liệu cũ (chỉ có isTaken) và dữ liệu mới (có status đầy đủ).
 */
public class LogData {
    public String name;
    /** actionTime: thời điểm hành động xảy ra (uống/bỏ qua/...) */
    public long time;
    /** Giữ lại để tương thích ngược */
    public boolean taken;
    public boolean isLate;
    public String skipReason;
    /** Trạng thái đầy đủ: TAKEN/MISSED/SKIPPED/SNOOZED/PENDING */
    public String status;
    /** Tồn kho còn lại tại thời điểm log */
    public int stockRemaining;
    /** Giờ uống theo lịch */
    public long scheduledTime;

    // ─── Constructor tương thích ngược ───────────────────────────────────────

    public LogData(String n, Long t, boolean isTaken) {
        this.name           = n;
        this.time           = (t != null) ? t : 0;
        this.taken          = isTaken;
        this.isLate         = false;
        this.skipReason     = null;
        this.status         = isTaken ? Reminder.STATUS_TAKEN : Reminder.STATUS_PENDING;
        this.stockRemaining = -1;
        this.scheduledTime  = 0;
    }

    public LogData(String n, Long t, boolean isTaken, boolean late, String reason) {
        this.name           = n;
        this.time           = (t != null) ? t : 0;
        this.taken          = isTaken;
        this.isLate         = late;
        this.skipReason     = reason;
        this.status         = isTaken ? Reminder.STATUS_TAKEN : Reminder.STATUS_PENDING;
        this.stockRemaining = -1;
        this.scheduledTime  = 0;
    }

    // ─── Constructor đầy đủ (dữ liệu mới) ───────────────────────────────────

    public LogData(String name, long actionTime, String status,
                   boolean isLate, String skipReason,
                   int stockRemaining, long scheduledTime) {
        this.name           = name;
        this.time           = actionTime;
        this.status         = status != null ? status : Reminder.STATUS_PENDING;
        this.taken          = Reminder.STATUS_TAKEN.equals(this.status);
        this.isLate         = isLate;
        this.skipReason     = skipReason;
        this.stockRemaining = stockRemaining;
        this.scheduledTime  = scheduledTime;
    }

    // ─── Helper: resolve status từ dữ liệu cũ nếu status null ───────────────

    /**
     * Trả về status đã được resolve.
     * Nếu status null (dữ liệu cũ): dùng isTaken + scheduledTime để suy ra.
     */
    public String resolvedStatus() {
        if (status != null && !status.isEmpty()) return status;
        if (taken) return Reminder.STATUS_TAKEN;
        // Nếu scheduledTime đã qua 30 phút → MISSED, ngược lại → PENDING
        if (scheduledTime > 0
                && System.currentTimeMillis() > scheduledTime + Reminder.GRACE_PERIOD_MS) {
            return Reminder.STATUS_MISSED;
        }
        return Reminder.STATUS_PENDING;
    }
}
