package vn.medisense.app.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import vn.medisense.app.api.ParsedMedicationInfo;
import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.PrescriptionData;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.database.ReminderWithMedication;
import vn.medisense.app.repository.MedicationRepository;
import vn.medisense.app.utils.StatisticsManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainViewModel extends AndroidViewModel {

    private final MedicationRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>(System.currentTimeMillis());
    private final MutableLiveData<List<ReminderWithMedication>> todayReminders = new MutableLiveData<>();
    private final MutableLiveData<List<ReminderWithMedication>> strictlyPendingReminders = new MutableLiveData<>();
    private final MutableLiveData<ReminderWithMedication> nextDose = new MutableLiveData<>();
    private final MutableLiveData<StatisticsManager.ComplianceStats> complianceStats = new MutableLiveData<>();
    private final MutableLiveData<String> actionMessage = new MutableLiveData<>();

    private LiveData<List<ReminderWithMedication>> sourceDbReminders;
    private final MediatorLiveData<Void> triggerMediator = new MediatorLiveData<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = new MedicationRepository(application);
        setupObservers();
    }

    public void setSelectedDate(long dateMillis) {
        selectedDate.setValue(dateMillis);
    }

    private void setupObservers() {
        triggerMediator.addSource(selectedDate, dateMillis -> {
            if (sourceDbReminders != null) {
                triggerMediator.removeSource(sourceDbReminders);
            }
            long start = getStartOfDayMillis(dateMillis);
            long end = getEndOfDayMillis(dateMillis);
            sourceDbReminders = repository.getRemindersWithMedicationForDay(start, end);
            triggerMediator.addSource(sourceDbReminders, originalReminders -> {
                processReminders(originalReminders);
            });
        });
    }

    private long getStartOfDayMillis(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfDayMillis(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DATE, 1);
        calendar.add(Calendar.MILLISECOND, -1);
        return calendar.getTimeInMillis();
    }

    public LiveData<List<ReminderWithMedication>> getTodayReminders() {
        return todayReminders;
    }

    public LiveData<List<ReminderWithMedication>> getStrictlyPendingReminders() {
        return strictlyPendingReminders;
    }

    public LiveData<ReminderWithMedication> getNextDose() {
        return nextDose;
    }

    public LiveData<StatisticsManager.ComplianceStats> getComplianceStats() {
        return complianceStats;
    }

    public LiveData<String> getActionMessage() {
        return actionMessage;
    }
    
    // Khởi chạy để trigger luồng Mediator (dùng ở View)
    public LiveData<Void> getTrigger() {
        return triggerMediator;
    }

    private void processReminders(List<ReminderWithMedication> originalReminders) {
        executor.execute(() -> {
            if (originalReminders != null) {
                // 1. Phân loại theo status
                List<ReminderWithMedication> pending  = new ArrayList<>();
                List<ReminderWithMedication> snoozed  = new ArrayList<>();
                List<ReminderWithMedication> completed = new ArrayList<>(); // TAKEN
                List<ReminderWithMedication> missed   = new ArrayList<>();
                List<ReminderWithMedication> skipped  = new ArrayList<>();

                for (ReminderWithMedication r : originalReminders) {
                    if (r.reminder == null) continue;
                    String status = r.reminder.status != null
                            ? r.reminder.status : Reminder.STATUS_PENDING;
                    switch (status) {
                        case Reminder.STATUS_TAKEN:    completed.add(r); break;
                        case Reminder.STATUS_MISSED:   missed.add(r);    break;
                        case Reminder.STATUS_SKIPPED:  skipped.add(r);   break;
                        case Reminder.STATUS_SNOOZED:  snoozed.add(r);   break;
                        default:                       pending.add(r);   break;
                    }
                }

                // 2. Sắp xếp theo thời gian trong từng nhóm
                java.util.Comparator<ReminderWithMedication> byTime =
                        (r1, r2) -> Long.compare(r1.reminder.reminderTime, r2.reminder.reminderTime);
                pending.sort(byTime);
                snoozed.sort(byTime);
                completed.sort(byTime);
                missed.sort(byTime);
                skipped.sort(byTime);

                // 3. Ghép danh sách: chưa uống/snooze ở trên, xong/bỏ/lỡ ở dưới
                List<ReminderWithMedication> reminders = new ArrayList<>();
                reminders.addAll(pending);
                reminders.addAll(snoozed);
                reminders.addAll(missed);
                reminders.addAll(skipped);
                reminders.addAll(completed);

                // 4. Tìm liều uống tiếp theo từ PENDING và SNOOZED
                long currentTime = System.currentTimeMillis();
                ReminderWithMedication foundNextDose = null;
                for (ReminderWithMedication r : pending) {
                    // Cho phép uống muộn tối đa 1 giờ
                    if (r.reminder.reminderTime >= currentTime - 3_600_000L) {
                        foundNextDose = r;
                        break;
                    }
                }
                if (foundNextDose == null) {
                    for (ReminderWithMedication r : snoozed) {
                        if (r.reminder.snoozeUntil >= currentTime - 3_600_000L) {
                            foundNextDose = r;
                            break;
                        }
                    }
                }

                todayReminders.postValue(reminders);
                strictlyPendingReminders.postValue(pending);
                nextDose.postValue(foundNextDose);
            } else {
                todayReminders.postValue(new ArrayList<>());
                strictlyPendingReminders.postValue(new ArrayList<>());
                nextDose.postValue(null);
            }
            refreshStatistics();
        });
    }

    public void refreshStatistics() {
        executor.execute(() -> {
            StatisticsManager.ComplianceStats stats = StatisticsManager.getStatistics(getApplication());
            complianceStats.postValue(stats);
        });
    }

    public void markReminderTaken(ReminderWithMedication item, boolean isChecked) {
        executor.execute(() -> {
            if (isChecked) {
                // Dùng ReminderStatusHelper để đảm bảo không double-decrement stock
                vn.medisense.app.utils.ReminderStatusHelper.markTaken(getApplication(), item);
            } else {
                // Bỏ đánh dấu — cộng lại stock nếu trước đó là TAKEN
                vn.medisense.app.utils.ReminderStatusHelper.unmarkTaken(getApplication(), item);
            }
        });
    }

    /**
     * Bắt buộc chuyển trạng thái reminder về PENDING (Chưa uống).
     */
    public void markReminderPending(ReminderWithMedication item) {
        executor.execute(() ->
                vn.medisense.app.utils.ReminderStatusHelper.forcePending(getApplication(), item));
    }

    /**
     * Đánh dấu reminder là SKIPPED với lý do.
     * Không trừ stock. Ghi caregiver log.
     */
    public void markReminderSkipped(ReminderWithMedication item, String skipReason) {
        executor.execute(() ->
                vn.medisense.app.utils.ReminderStatusHelper.markSkipped(getApplication(), item, skipReason));
    }

    /**
     * Đánh dấu reminder là SNOOZED và reschedule alarm sau snoozeMinutes phút.
     */
    public void markReminderSnoozed(ReminderWithMedication item, int snoozeMinutes) {
        executor.execute(() ->
                vn.medisense.app.utils.ReminderStatusHelper.markSnoozed(getApplication(), item, snoozeMinutes));
    }

    /**
     * Quét và chuyển tất cả reminder quá hạn sang MISSED.
     * Gọi khi mở app hoặc sau BootReceiver.
     */
    public void markOverdueAsMissed() {
        executor.execute(() ->
                vn.medisense.app.utils.ReminderStatusHelper.markAllOverdueAsMissed(getApplication()));
    }

    public void cloneGroup(int prescriptionId) {
        executor.execute(() -> {
            PrescriptionData parent = repository.getPrescriptionDataByIdSync(prescriptionId);
            List<Medication> meds = repository.getMedicationsByPrescriptionIdSync(prescriptionId);

            if (parent == null || meds == null || meds.isEmpty()) {
                actionMessage.postValue("Không tìm thấy dữ liệu đơn thuốc này.");
                return;
            }

            PrescriptionData newParent = new PrescriptionData();
            newParent.title = parent.title + " (Bản sao)";
            newParent.diagnosis = parent.diagnosis;
            newParent.doctorAdvice = parent.doctorAdvice;
            newParent.dateCreated = System.currentTimeMillis();
            
            AppDatabase db = AppDatabase.getInstance(getApplication());
            int newPrescriptionId = (int) db.medicationDao().insertPrescriptionData(newParent);

            for (Medication oldMed : meds) {
                Medication newMed = new Medication();
                newMed.name = oldMed.name;
                newMed.dosage = oldMed.dosage;
                newMed.frequency = oldMed.frequency;
                newMed.durationDays = oldMed.durationDays;
                newMed.instructions = oldMed.instructions;
                newMed.isCritical = oldMed.isCritical;
                newMed.totalStock = oldMed.totalStock;
                newMed.currentStock = oldMed.currentStock;
                newMed.lowStockThreshold = oldMed.lowStockThreshold;
                newMed.dosagePerIntake = oldMed.dosagePerIntake;
                newMed.prescriptionId = newPrescriptionId;
                newMed.mealContext = oldMed.mealContext;
                newMed.offsetMinutes = oldMed.offsetMinutes;
                newMed.specificShifts = oldMed.specificShifts;

                long newMedId = repository.insertMedicationSync(newMed);

                List<Reminder> reminders = new ArrayList<>();
                for (int day = 0; day < newMed.durationDays; day++) {
                    long startMillis = buildTimeMillisForClone(8, 0, day);
                    long intervalMillis = (24L * 60L * 60L * 1000L) / newMed.frequency;
                    for (int i = 0; i < newMed.frequency; i++) {
                        reminders.add(new Reminder((int) newMedId, startMillis + i * intervalMillis));
                    }
                }
                repository.insertRemindersSync(reminders);

                // Schedule alarm cho từng reminder vừa tạo
                long[] reminderIds = repository.insertRemindersSync(reminders);
                for (int ri = 0; ri < reminderIds.length && ri < reminders.size(); ri++) {
                    long rid = reminderIds[ri];
                    if (rid <= 0) continue;
                    long alarmTime = reminders.get(ri).reminderTime;
                    if (alarmTime < System.currentTimeMillis() - 60_000L) continue;
                    vn.medisense.app.utils.AlarmHelper.scheduleAlarm(
                            getApplication(),
                            (int) rid,
                            alarmTime,
                            newMed.name,
                            newMed.dosage,
                            newMed.isCritical
                    );
                }
            }

            actionMessage.postValue("Đã mua lại đơn thuốc thành công!");
        });
    }

    public void deleteGroup(int prescriptionId) {
        executor.execute(() -> {
            repository.deletePrescriptionGroupSync(prescriptionId);
            actionMessage.postValue("Đã xóa đơn thuốc thành công!");
        });
    }

    public void archiveGroup(int prescriptionId) {
        executor.execute(() -> {
            repository.archivePrescriptionSync(prescriptionId);
            actionMessage.postValue("Đã lưu trữ đơn thuốc!");
        });
    }

    private long buildTimeMillisForClone(int hour, int minute, int offsetDays) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, offsetDays);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getStartOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DATE, 1);
        calendar.add(Calendar.MILLISECOND, -1);
        return calendar.getTimeInMillis();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
