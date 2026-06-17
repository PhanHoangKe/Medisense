package vn.medisense.app.utils;

import android.content.Context;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.MedicationDao;
import com.github.mikephil.charting.data.BarEntry;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * StatisticsManager — Tính toán thống kê tuân thủ uống thuốc.
 *
 * Công thức adherenceRate (tỷ lệ tuân thủ):
 *   adherenceRate = TAKEN / (TAKEN + MISSED + SKIPPED)
 *
 * Lý do:
 *   - PENDING chưa đến giờ → chưa tính vào mẫu số (không phạt người dùng)
 *   - SNOOZED vẫn đang chờ → chưa tính
 *   - SKIPPED hiển thị riêng, không gộp với MISSED
 *   - MISSED = quá giờ ân hạn mà không uống → tính vào mẫu số
 *
 * Streak chỉ tính ngày đạt 100% TAKEN (không có MISSED, không có SKIPPED).
 */
public class StatisticsManager {

    // ─── ComplianceStats ──────────────────────────────────────────────────────

    public static class ComplianceStats {
        /** % tuân thủ hôm nay = TAKEN / (TAKEN + MISSED + SKIPPED) * 100 */
        public int todayPercentage;

        /** Số liều PENDING còn lại hôm nay */
        public int remainingDosesToday;

        /** Số liều TAKEN hôm nay */
        public int takenToday;

        /** Số liều MISSED hôm nay */
        public int missedToday;

        /** Số liều SKIPPED hôm nay */
        public int skippedToday;

        /** Chuỗi ngày liên tiếp đạt 100% TAKEN */
        public int currentStreak;

        /** Dữ liệu biểu đồ cột 7 ngày */
        public List<BarEntry> chartEntries;

        /** Nhãn trục X biểu đồ */
        public List<String> xAxisLabels;

        /** Huy hiệu đã đạt: 7, 30, 90 ngày */
        public List<Integer> earnedBadges;

        /** Mốc tiếp theo cần đạt */
        public int nextMilestone;

        /** Tiến trình đến mốc tiếp theo */
        public int progressToNextMilestone;

        public ComplianceStats() {
            chartEntries  = new ArrayList<>();
            xAxisLabels   = new ArrayList<>();
            earnedBadges  = new ArrayList<>();
        }
    }

    // ─── DayStats (dùng nội bộ) ───────────────────────────────────────────────

    /** Thống kê chi tiết cho một ngày */
    public static class DayStats {
        public int total;
        public int taken;
        public int missed;
        public int skipped;
        public int pending;

        /** adherenceRate = taken / (taken + missed + skipped), 0 nếu mẫu số = 0 */
        public float adherenceRate() {
            int denominator = taken + missed + skipped;
            if (denominator == 0) return 0f;
            return (float) taken / denominator;
        }

        /** % tuân thủ (0–100) */
        public int adherencePercent() {
            return (int) (adherenceRate() * 100f);
        }

        /** true nếu ngày này đạt 100% (không có MISSED, không có SKIPPED) */
        public boolean isPerfect() {
            return (missed == 0 && skipped == 0 && taken > 0);
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Lấy thống kê đầy đủ. Phải gọi trên background thread.
     */
    public static ComplianceStats getStatistics(Context context) {
        MedicationDao dao = AppDatabase.getInstance(context).medicationDao();
        ComplianceStats stats = new ComplianceStats();

        // 1. Biểu đồ 7 ngày
        for (int i = 6; i >= 0; i--) {
            Calendar dayCal = Calendar.getInstance();
            dayCal.add(Calendar.DAY_OF_YEAR, -i);

            long start = getStartOfDay(dayCal);
            long end   = getEndOfDay(dayCal);

            DayStats day = getDayStats(dao, start, end);

            // Biểu đồ dùng adherencePercent (không tính PENDING)
            float pct = day.adherencePercent();
            stats.chartEntries.add(new BarEntry(6 - i, pct));

            String label = dayCal.get(Calendar.DAY_OF_MONTH)
                    + "/" + (dayCal.get(Calendar.MONTH) + 1);
            stats.xAxisLabels.add(label);

            if (i == 0) {
                // Hôm nay
                stats.todayPercentage    = day.adherencePercent();
                stats.remainingDosesToday = day.pending;
                stats.takenToday         = day.taken;
                stats.missedToday        = day.missed;
                stats.skippedToday       = day.skipped;
            }
        }

        // 2. Streak
        stats.currentStreak = calculateStreakFromDb(dao);

        // 3. Huy hiệu & milestone
        calculateBadgesAndMilestones(stats);

        return stats;
    }

    /**
     * Lấy DayStats cho một khoảng thời gian cụ thể.
     * Dùng được cho PdfGenerator và các nơi khác cần thống kê chi tiết.
     */
    public static DayStats getDayStats(MedicationDao dao, long startTime, long endTime) {
        DayStats day = new DayStats();
        day.total   = dao.getTotalRemindersCountSync(startTime, endTime);
        day.taken   = dao.getTakenCountSync(startTime, endTime);
        day.missed  = dao.getMissedCountSync(startTime, endTime);
        day.skipped = dao.getSkippedCountSync(startTime, endTime);
        day.pending = dao.getPendingCountSync(startTime, endTime);
        return day;
    }

    // ─── Streak ───────────────────────────────────────────────────────────────

    private static int calculateStreakFromDb(MedicationDao dao) {
        int streak = 0;
        Calendar cal = Calendar.getInstance();

        // Hôm nay: chỉ tính nếu đã hoàn thành 100% (không MISSED, không SKIPPED)
        long startToday = getStartOfDay(cal);
        long endToday   = getEndOfDay(cal);
        DayStats today  = getDayStats(dao, startToday, endToday);
        if (today.isPerfect()) {
            streak++;
        }

        // Các ngày trước (tối đa 365 ngày)
        cal.add(Calendar.DAY_OF_YEAR, -1);
        for (int i = 0; i < 365; i++) {
            long start = getStartOfDay(cal);
            long end   = getEndOfDay(cal);
            DayStats day = getDayStats(dao, start, end);

            if (day.total == 0) {
                // Ngày không có thuốc → bỏ qua, không phá streak
                cal.add(Calendar.DAY_OF_YEAR, -1);
                continue;
            }

            if (day.isPerfect()) {
                streak++;
            } else {
                break; // Phá streak
            }
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    // ─── Badges & Milestones ──────────────────────────────────────────────────

    private static void calculateBadgesAndMilestones(ComplianceStats stats) {
        int streak = stats.currentStreak;

        if (streak >= 7)  stats.earnedBadges.add(7);
        if (streak >= 30) stats.earnedBadges.add(30);
        if (streak >= 90) stats.earnedBadges.add(90);

        if (streak < 7) {
            stats.nextMilestone          = 7;
            stats.progressToNextMilestone = streak;
        } else if (streak < 30) {
            stats.nextMilestone          = 30;
            stats.progressToNextMilestone = streak;
        } else if (streak < 90) {
            stats.nextMilestone          = 90;
            stats.progressToNextMilestone = streak;
        } else {
            stats.nextMilestone          = 365;
            stats.progressToNextMilestone = streak;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public static long getStartOfDay(Calendar calendar) {
        Calendar cal = (Calendar) calendar.clone();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public static long getEndOfDay(Calendar calendar) {
        Calendar cal = (Calendar) calendar.clone();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.add(Calendar.MILLISECOND, -1);
        return cal.getTimeInMillis();
    }
}
