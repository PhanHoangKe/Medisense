package vn.medisense.app.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.utils.AudioResourceHelper;

import java.util.concurrent.TimeUnit;

/**
 * NaggingWorker: Phát ra tiếng bíp ngắn sau mỗi 2 phút nếu người dùng chưa tương tác với thông báo.
 */
public class NaggingWorker extends Worker {

    public static final String KEY_REMINDER_ID = "reminder_id";

    public NaggingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        int reminderId = getInputData().getInt(KEY_REMINDER_ID, -1);
        
        if (reminderId == -1) {
            return Result.failure();
        }

        // Kiểm tra xem thuốc đã được uống chưa (dùng status mới)
        String status = AppDatabase.getInstance(getApplicationContext())
                                   .medicationDao()
                                   .getReminderStatus(reminderId);

        if (Reminder.STATUS_TAKEN.equals(status) || Reminder.STATUS_SKIPPED.equals(status)) {
            Log.d("NaggingWorker", "Reminder " + reminderId + " đã " + status + ". Dừng nagging.");
            return Result.success();
        }

        // Tăng naggingCount
        AppDatabase.getInstance(getApplicationContext())
                   .medicationDao()
                   .incrementNaggingCount(reminderId);

        // Phát tiếng bíp ngắn
        Log.d("NaggingWorker", "Reminder " + reminderId + " chưa uống. Phát tiếng bíp!");
        AudioResourceHelper.playShortBeep(getApplicationContext());

        // Lập lịch lại sau 2 phút
        scheduleNagging(getApplicationContext(), reminderId, 2);

        return Result.success();
    }

    /**
     * Hàm hỗ trợ để bắt đầu vòng lặp nhắc nhở
     */
    public static void scheduleNagging(Context context, int reminderId, int delayMinutes) {
        Data inputData = new Data.Builder()
                .putInt(KEY_REMINDER_ID, reminderId)
                .build();

        OneTimeWorkRequest naggingWork = new OneTimeWorkRequest.Builder(NaggingWorker.class)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(inputData)
                .build();

        // Sắp xếp công việc duy nhất để tránh nhiều vòng lặp nhắc nhở cho cùng một lần nhắc thuốc
        WorkManager.getInstance(context).enqueueUniqueWork(
                "Nag_" + reminderId,
                androidx.work.ExistingWorkPolicy.REPLACE,
                naggingWork
        );
    }

    /**
     * Dừng vòng lặp nhắc nhở
     */
    public static void stopNagging(Context context, int reminderId) {
        WorkManager.getInstance(context).cancelUniqueWork("Nag_" + reminderId);
    }
}
