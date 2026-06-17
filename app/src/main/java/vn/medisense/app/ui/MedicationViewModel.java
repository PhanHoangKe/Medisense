package vn.medisense.app.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import vn.medisense.app.api.ParsedMedicationInfo;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.repository.MedicationRepository;
import vn.medisense.app.utils.AlarmHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel xu ly luu danh sach thuoc vao Room
 */
public class MedicationViewModel extends AndroidViewModel {

    private final MedicationRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MedicationViewModel(@NonNull Application application) {
        super(application);
        this.repository = new MedicationRepository(application.getApplicationContext());
    }

    public void insertAll(vn.medisense.app.api.ParsedPrescriptionResult result, Runnable onComplete) {
        executor.execute(() -> {
            int prescriptionId = 0;
            if ((result.diagnosis != null && !result.diagnosis.trim().isEmpty()) ||
                    (result.doctorAdvice != null && !result.doctorAdvice.trim().isEmpty())) {
                vn.medisense.app.database.PrescriptionData pd = new vn.medisense.app.database.PrescriptionData();
                pd.title = "Đơn thuốc mới";
                pd.diagnosis = result.diagnosis != null ? result.diagnosis : "";
                pd.doctorAdvice = result.doctorAdvice != null ? result.doctorAdvice : "";
                pd.dateCreated = System.currentTimeMillis();
                prescriptionId = (int) repository.insertPrescriptionDataSync(pd);
            }

            if (result.medications != null) {
                for (ParsedMedicationInfo info : result.medications) {
                    if (info == null || info.name == null || info.name.trim().isEmpty()) {
                        continue;
                    }

                    Medication medication = new Medication();
                    medication.name = info.name;
                    // Bind new contexts
                    medication.prescriptionId = prescriptionId;
                    medication.mealContext = info.mealContext != null ? info.mealContext : "NONE";
                    medication.offsetMinutes = info.offsetMinutes;
                    medication.specificShifts = info.specificShifts != null ? info.specificShifts : "";

                    medication.dosage = info.dosage != null ? info.dosage : "";
                    medication.frequency = Math.max(1, info.frequency);
                    medication.durationDays = Math.max(1, info.durationDays);
                    medication.instructions = info.notes != null ? info.notes : "";
                    medication.isCritical = false;
                    medication.totalStock = 0;
                    medication.currentStock = 0;
                    medication.lowStockThreshold = 5;
                    medication.dosagePerIntake = info.dosagePerIntake > 0 ? info.dosagePerIntake : 1;

                    long medId = repository.insertMedicationSync(medication);
                    List<Reminder> reminders = buildReminders((int) medId, info);
                    long[] reminderIds = repository.insertRemindersSync(reminders);

                    for (int i = 0; i < reminderIds.length && i < reminders.size(); i++) {
                        long rid = reminderIds[i];
                        if (rid <= 0) continue;
                        long alarmTime = reminders.get(i).reminderTime;
                        if (alarmTime < System.currentTimeMillis() - 60_000L) continue;

                        AlarmHelper.scheduleAlarm(
                                getApplication().getApplicationContext(),
                                (int) rid,
                                alarmTime,
                                medication.name,
                                medication.dosage,
                                medication.isCritical
                        );
                    }
                }
            }

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private List<Reminder> buildReminders(int medId, ParsedMedicationInfo info) {
        List<Reminder> reminders = new ArrayList<>();
        List<String> times = info.times != null ? info.times : new ArrayList<>();
        int frequency = Math.max(1, info.frequency);
        int duration = Math.max(1, info.durationDays);

        for (int day = 0; day < duration; day++) {
            if (times.isEmpty()) {
                long startMillis = buildTimeMillis(8, 0, day);
                long intervalMillis = (24L * 60L * 60L * 1000L) / frequency;
                for (int i = 0; i < frequency; i++) {
                    reminders.add(new Reminder(medId, startMillis + i * intervalMillis));
                }
            } else {
                for (String time : times) {
                    int[] parsed = parseTime(time);
                    reminders.add(new Reminder(medId, buildTimeMillis(parsed[0], parsed[1], day)));
                }
            }
        }
        return reminders;
    }

    private int[] parseTime(String time) {
        int hour = 8;
        int minute = 0;
        if (time != null) {
            String[] parts = time.split(":");
            if (parts.length >= 2) {
                try {
                    hour = Integer.parseInt(parts[0].trim());
                    minute = Integer.parseInt(parts[1].trim());
                } catch (Exception ignored) {
                }
            }
        }
        return new int[] { hour, minute };
    }

    private long buildTimeMillis(int hour, int minute, int offsetDays) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, offsetDays);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
