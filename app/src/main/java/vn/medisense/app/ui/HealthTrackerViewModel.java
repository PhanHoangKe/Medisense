package vn.medisense.app.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import vn.medisense.app.database.MeasurementTask;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.database.VitalSign;
import vn.medisense.app.database.Medication;
import vn.medisense.app.models.RiskReport;
import vn.medisense.app.repository.HealthTrackerRepository;

public class HealthTrackerViewModel extends AndroidViewModel {

    private final HealthTrackerRepository repository;
    private final ExecutorService executor;

    private final MutableLiveData<VitalSign[]> latestVitalSigns = new MutableLiveData<>();
    private final MutableLiveData<RiskReport> riskReport = new MutableLiveData<>();
    private final MutableLiveData<ChartData> chartData = new MutableLiveData<>();

    public HealthTrackerViewModel(@NonNull Application application) {
        super(application);
        repository = new HealthTrackerRepository(application);
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<MeasurementTask>> getAllMeasurementTasks() {
        return repository.getAllTasks();
    }

    public void insertMeasurementTask(MeasurementTask task) {
        executor.execute(() -> {
            long taskId = repository.insertMeasurementTask(task);
            if (taskId > 0) {
                // Schedule các alarm using MeasurementAlarmHelper
                vn.medisense.app.utils.MeasurementAlarmHelper.scheduleAlarm(
                        getApplication(),
                        (int) taskId,
                        task.title,
                        task.type,
                        task.timeOfDay
                );
            }
        });
    }

    public void deleteMeasurementTask(MeasurementTask task) {
        executor.execute(() -> {
            // Hủy các alarm first
            vn.medisense.app.utils.MeasurementAlarmHelper.cancelAlarm(
                    getApplication(),
                    task.id
            );
            // thì Xóa từ database
            repository.deleteMeasurementTask(task);
        });
    }

    public void insertVitalSign(VitalSign vitalSign, String type) {
        executor.execute(() -> {
            repository.insertVitalSign(vitalSign);
            vn.medisense.app.utils.FirestoreSyncHelper.syncVitalSign(getApplication(), vitalSign);
            loadLatestVitalSigns();
            loadChartData(type);
            loadRiskAssessment();
        });
    }

    public LiveData<VitalSign[]> getLatestVitalSigns() {
        return latestVitalSigns;
    }

    public void loadLatestVitalSigns() {
        executor.execute(() -> {
            VitalSign bp = repository.getLatestVitalSign("blood_pressure");
            VitalSign hr = repository.getLatestVitalSign("heart_rate");
            VitalSign bs = repository.getLatestVitalSign("blood_sugar");
            VitalSign weight = repository.getLatestVitalSign("weight"); // Thêm cân nặng
            latestVitalSigns.postValue(new VitalSign[]{bp, hr, bs, weight});
        });
    }

    public LiveData<RiskReport> getRiskReport() {
        return riskReport;
    }

    public void loadRiskAssessment() {
        executor.execute(() -> {
            RiskReport report = repository.assessRiskSync();
            riskReport.postValue(report);
        });
    }

    public LiveData<ChartData> getChartData() {
        return chartData;
    }

    public void loadChartData(String type) {
        executor.execute(() -> {
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (7L * 24 * 60 * 60 * 1000);

            List<VitalSign> vitalSigns = repository.getVitalSignsByTypeAndTimeRange(type, startTime, endTime);
            List<Reminder> takenReminders = repository.getRemindersBetweenSync(startTime, endTime);

            List<Long> medicationTimes = new java.util.ArrayList<>();
            for (Reminder reminder : takenReminders) {
                if (reminder.isTaken) {
                    medicationTimes.add(reminder.reminderTime);
                }
            }

            chartData.postValue(new ChartData(vitalSigns, medicationTimes, type));
        });
    }

    public void getAIAnalysisData(AIAnalysisCallback callback) {
        executor.execute(() -> {
            long endTime = System.currentTimeMillis();
            long startTime = endTime - (7L * 24 * 60 * 60 * 1000);
            List<VitalSign> vitalSigns = repository.getAllVitalSignsInRange(startTime, endTime);
            List<Reminder> reminders = repository.getRemindersBetweenSync(startTime, endTime);
            callback.onDataReady(vitalSigns, reminders);
        });
    }

    public void getAllMedications(MedicationListCallback callback) {
        executor.execute(() -> {
            List<Medication> medications = repository.getAllMedicationsSync();
            callback.onMedicationsLoaded(medications);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }

    public static class ChartData {
        public List<VitalSign> vitalSigns;
        public List<Long> medicationTimes;
        public String type;

        public ChartData(List<VitalSign> vitalSigns, List<Long> medicationTimes, String type) {
            this.vitalSigns = vitalSigns;
            this.medicationTimes = medicationTimes;
            this.type = type;
        }
    }

    public interface AIAnalysisCallback {
        void onDataReady(List<VitalSign> vitalSigns, List<Reminder> reminders);
    }

    public interface MedicationListCallback {
        void onMedicationsLoaded(List<Medication> medications);
    }
}
