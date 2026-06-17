package vn.medisense.app.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.MedicationDao;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.database.ReminderWithMedication;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository quản lý thao tác dữ liệu thuốc và nhắc thuốc
 * Tất cả thao tác ghi (insert/update/delete) phải đi qua lớp này
 */
public class MedicationRepository {

    public interface ResultCallback<T> {
        void onResult(T result);
    }

    private final MedicationDao dao;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public MedicationRepository(Context context) {
        this.dao = AppDatabase.getInstance(context.getApplicationContext()).medicationDao();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public LiveData<List<ReminderWithMedication>> getRemindersWithMedicationForDay(long startTime, long endTime) {
        return dao.getRemindersWithMedicationForDay(startTime, endTime);
    }

    public List<ReminderWithMedication> getRemindersWithMedicationForDaySync(long startTime, long endTime) {
        return dao.getRemindersWithMedicationForDaySync(startTime, endTime);
    }

    public List<Medication> getAllMedicationsSync() {
        return dao.getAllMedicationsSync();
    }

    /**
     * Lấy danh sách thuốc dùng gần nhất để giới hạn kích thước prompt AI.
     * Nếu không có dữ liệu, trả về toàn bộ.
     */
    public List<Medication> getTopMedicationsForPromptSync(int limit) {
        List<Medication> recent = dao.getMostRecentlyTakenMedicationsSync(limit);
        if (recent == null || recent.isEmpty()) {
            return getAllMedicationsSync();
        }
        return recent;
    }

    public Medication getMedicationByIdSync(int medicationId) {
        return dao.getMedicationByIdSync(medicationId);
    }

    public vn.medisense.app.database.PrescriptionData getPrescriptionDataByIdSync(int id) {
        return dao.getPrescriptionDataByIdSync(id);
    }

    public long insertPrescriptionDataSync(vn.medisense.app.database.PrescriptionData prescriptionData) {
        return dao.insertPrescriptionData(prescriptionData);
    }

    public List<Medication> getMedicationsByPrescriptionIdSync(int prescriptionId) {
        return dao.getMedicationsByPrescriptionIdSync(prescriptionId);
    }

    public void deletePrescriptionGroupSync(int prescriptionId) {
        dao.deleteMedicationsByPrescriptionIdSync(prescriptionId);
        dao.deletePrescriptionDataSync(prescriptionId);
    }

    public void archivePrescriptionSync(int prescriptionId) {
        dao.archivePrescriptionDataSync(prescriptionId);
        dao.deletePendingRemindersByPrescriptionIdSync(prescriptionId);
    }

    public long insertMedicationSync(Medication medication) {
        return dao.insertMedication(medication);
    }

    public long[] insertRemindersSync(List<Reminder> reminders) {
        return dao.insertReminders(reminders);
    }

    public void updateReminderSync(Reminder reminder) {
        dao.updateReminder(reminder);
    }

    public void deletePendingRemindersByMedicationIdSync(int medId) {
        dao.deletePendingRemindersByMedicationIdSync(medId);
    }

    public List<Reminder> getRemindersByMedicationIdSync(int medId) {
        return dao.getRemindersByMedicationIdSync(medId);
    }

    public void deleteReminderSync(int reminderId) {
        dao.deleteReminderSync(reminderId);
    }

    public void updateMedicationSync(Medication medication) {
        dao.updateMedication(medication);
    }

    public void deleteMedicationSync(int medId) {
        dao.deleteMedication(medId);
    }

    public void insertMedication(Medication medication, ResultCallback<Long> callback) {
        executor.execute(() -> {
            long id = dao.insertMedication(medication);
            postResult(callback, id);
        });
    }

    public void insertReminders(List<Reminder> reminders, ResultCallback<long[]> callback) {
        executor.execute(() -> {
            long[] ids = dao.insertReminders(reminders);
            postResult(callback, ids);
        });
    }

    public void updateReminder(Reminder reminder) {
        executor.execute(() -> dao.updateReminder(reminder));
    }

    public void updateMedication(Medication medication) {
        executor.execute(() -> dao.updateMedication(medication));
    }

    /**
     * Lưu đường dẫn ảnh vào loại thuốc
     */
    public void updateMedicationImagePath(int medId, String path, ResultCallback<Void> callback) {
        executor.execute(() -> {
            dao.updateMedicationImagePath(medId, path);
            postResult(callback, null);
        });
    }

    /**
     * Lưu đường dẫn ảnh đơn thuốc vào reminder
     */
    public void updateReminderImagePath(int reminderId, String path, ResultCallback<Void> callback) {
        executor.execute(() -> {
            dao.updateReminderImagePath(reminderId, path);
            postResult(callback, null);
        });
    }

    public void deleteMedication(int medId) {
        executor.execute(() -> dao.deleteMedication(medId));
    }

    public void updateReminderTakenTime(int reminderId, long takenTime) {
        executor.execute(() -> dao.updateReminderTakenTime(reminderId, takenTime));
    }

    public void updateReminderTakenTime(int reminderId, long takenTime, ResultCallback<Void> callback) {
        executor.execute(() -> {
            dao.updateReminderTakenTime(reminderId, takenTime);
            postResult(callback, null);
        });
    }

    /**
     * Tim reminder gan nhat chua uong trong ngay theo ten thuoc gan dung
     */
    public ReminderWithMedication findClosestPendingReminderForNameSync(String identifiedName, long nowMillis) {
        if (identifiedName == null || identifiedName.trim().isEmpty()) {
            return null;
        }

        List<Medication> meds = dao.getAllMedicationsSync();
        if (meds == null || meds.isEmpty()) {
            return null;
        }

        Medication bestMed = null;
        double bestScore = 0.0;
        for (Medication med : meds) {
            double score = similarityScore(identifiedName, med.name);
            if (score > bestScore) {
                bestScore = score;
                bestMed = med;
            }
        }

        if (bestMed == null || bestScore < 0.6) {
            return null;
        }

        long start = getStartOfTodayMillis(nowMillis);
        long end = getEndOfTodayMillis(nowMillis);
        List<Reminder> pending = dao.getPendingRemindersForMedicationBetweenSync(bestMed.id, start, end);
        if (pending == null || pending.isEmpty()) {
            return null;
        }

        Reminder closest = null;
        long minDiff = Long.MAX_VALUE;
        for (Reminder reminder : pending) {
            long diff = Math.abs(reminder.reminderTime - nowMillis);
            if (diff < minDiff) {
                minDiff = diff;
                closest = reminder;
            }
        }

        if (closest == null) {
            return null;
        }

        ReminderWithMedication result = new ReminderWithMedication();
        result.reminder = closest;
        result.medication = bestMed;
        return result;
    }

    private double similarityScore(String a, String b) {
        String left = normalizeText(a);
        String right = normalizeText(b);
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int dist = levenshteinDistance(left, right);
        int maxLen = Math.max(left.length(), right.length());
        return 1.0 - (double) dist / (double) maxLen;
    }

    private String normalizeText(String input) {
        String text = input == null ? "" : input.trim().toLowerCase();
        java.text.Normalizer.Form form = java.text.Normalizer.Form.NFD;
        text = java.text.Normalizer.normalize(text, form);
        return text.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private long getStartOfTodayMillis(long nowMillis) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(nowMillis);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long getEndOfTodayMillis(long nowMillis) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(nowMillis);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        cal.add(java.util.Calendar.DATE, 1);
        cal.add(java.util.Calendar.MILLISECOND, -1);
        return cal.getTimeInMillis();
    }

    private <T> void postResult(ResultCallback<T> callback, T result) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onResult(result));
    }
}
