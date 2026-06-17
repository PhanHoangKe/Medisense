package vn.medisense.app.ui;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.medisense.app.api.ParsedMedicationInfo;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.PrescriptionData;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.databinding.ActivityEditPrescriptionBinding;
import vn.medisense.app.repository.MedicationRepository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import vn.medisense.app.api.ParsedMedicationInfo;
import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.PrescriptionData;
import vn.medisense.app.database.Reminder;

public class EditPrescriptionActivity extends AppCompatActivity {

    private ActivityEditPrescriptionBinding binding;
    private PrescriptionEditAdapter adapter;
    private MedicationRepository medicationRepository;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private int prescriptionId = -1;
    private PrescriptionData prescriptionData;
    private List<Medication> originalMedications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityEditPrescriptionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        prescriptionId = getIntent().getIntExtra("prescription_id", -1);
        if (prescriptionId == -1) {
            Toast.makeText(this, "Không tìm thấy đơn thuốc", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        medicationRepository = new MedicationRepository(getApplicationContext());

        adapter = new PrescriptionEditAdapter();
        binding.recyclerEditMedicines.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerEditMedicines.setAdapter(adapter);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.buttonSave.setOnClickListener(v -> saveChanges());
        binding.buttonDelete.setOnClickListener(v -> confirmAndDelete());
        binding.buttonClone.setOnClickListener(v -> confirmAndClone());

        loadPrescriptionData();
    }

    private void loadPrescriptionData() {
        dbExecutor.execute(() -> {
            prescriptionData = medicationRepository.getPrescriptionDataByIdSync(prescriptionId);
            originalMedications = medicationRepository.getMedicationsByPrescriptionIdSync(prescriptionId);

            if (prescriptionData == null || originalMedications == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }

            List<ParsedMedicationInfo> parsedList = new ArrayList<>();
            for (Medication med : originalMedications) {
                ParsedMedicationInfo info = new ParsedMedicationInfo();
                info.id = med.id;
                info.name = med.name;
                info.dosage = med.dosage;
                info.totalQuantity = med.totalStock;
                info.notes = med.instructions;
                info.times = new ArrayList<>();
                // Trong Medisense thực tế, chúng ta có thể truy vấn các nhắc nhở sắp tới để hiển thị thời gian,
                // nhưng để đơn giản, chúng ta dựa vào cấu trúc DB (hoặc họ phải thêm lại thời gian)
                // Hãy truy vấn các thời gian riêng biệt của các nhắc nhở đang chờ cho thuốc này
                List<Reminder> pendingReminders = medicationRepository.getRemindersByMedicationIdSync(med.id);
                if (pendingReminders != null) {
                    for (Reminder r : pendingReminders) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(r.reminderTime);
                        String timeStr = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE));
                        if (!info.times.contains(timeStr)) {
                            info.times.add(timeStr);
                        }
                    }
                }
                parsedList.add(info);
            }

            runOnUiThread(() -> {
                adapter.setItems(parsedList);
            });
        });
    }

    private void saveChanges() {
        if (!adapter.validateAll(binding.recyclerEditMedicines)) {
            Toast.makeText(this, "Vui lòng kiểm tra lại thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        List<ParsedMedicationInfo> editedItems = adapter.getItems();

        dbExecutor.execute(() -> {
            for (ParsedMedicationInfo editedInfo : editedItems) {
                Medication originalTarget = null;
                for (Medication m : originalMedications) {
                    if (m.id == editedInfo.id) {
                        originalTarget = m;
                        break;
                    }
                }

                if (originalTarget != null) {
                    originalTarget.name = editedInfo.name;
                    originalTarget.dosage = editedInfo.dosage;
                    originalTarget.totalStock = editedInfo.totalQuantity;
                    originalTarget.instructions = editedInfo.notes;
                    medicationRepository.updateMedicationSync(originalTarget);

                    // Xây dựng lại nhắc nhở dựa trên thời gian đã cập nhật
                    medicationRepository.deletePendingRemindersByMedicationIdSync(originalTarget.id);

                    if (editedInfo.times != null && !editedInfo.times.isEmpty()) {
                        List<Reminder> newReminders = new ArrayList<>();
                        long currentTime = System.currentTimeMillis();
                        for (String timeStr : editedInfo.times) {
                            String[] parts = timeStr.split(":");
                            int h = Integer.parseInt(parts[0]);
                            int m = Integer.parseInt(parts[1]);

                            Calendar cal = Calendar.getInstance();
                            cal.set(Calendar.HOUR_OF_DAY, h);
                            cal.set(Calendar.MINUTE, m);
                            cal.set(Calendar.SECOND, 0);

                            // Schedule cho today
                            if (cal.getTimeInMillis() > currentTime) {
                                Reminder r = new Reminder(originalTarget.id, cal.getTimeInMillis());
                                newReminders.add(r);
                            }
                            // Lập lịch cho ngày mai để đảm bảo an toàn
                            cal.add(Calendar.DAY_OF_YEAR, 1);
                            Reminder rm = new Reminder(originalTarget.id, cal.getTimeInMillis());
                            newReminders.add(rm);
                        }
                        medicationRepository.insertRemindersSync(newReminders);
                    }
                }
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "Đã lưu thay đổi", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void confirmAndDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa đơn thuốc")
                .setMessage("Bạn có chắc chắn muốn xóa đơn thuốc này và tất cả các thuốc liên quan không? Hành động này không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    dbExecutor.execute(() -> {
                        medicationRepository.deletePrescriptionGroupSync(prescriptionId);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Đã xóa đơn thuốc thành công!", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void confirmAndClone() {
        new AlertDialog.Builder(this)
                .setTitle("📋 Nhân bản đơn thuốc")
                .setMessage("Bạn có chắc muốn nhân bản đơn thuốc này và bắt đầu lại lộ trình từ hôm nay không?")
                .setPositiveButton("Đồng ý", (dialog, which) -> dbExecutor.execute(this::clonePrescription))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void clonePrescription() {
        PrescriptionData parent = medicationRepository.getPrescriptionDataByIdSync(prescriptionId);
        java.util.List<vn.medisense.app.database.Medication> meds =
                medicationRepository.getMedicationsByPrescriptionIdSync(prescriptionId);

        if (parent == null || meds == null || meds.isEmpty()) {
            runOnUiThread(() -> Toast.makeText(this, "Không tìm thấy dữ liệu.", Toast.LENGTH_SHORT).show());
            return;
        }

        // Duplicate PrescriptionData
        PrescriptionData newParent = new PrescriptionData();
        newParent.title = parent.title + " (Bản sao)";
        newParent.diagnosis = parent.diagnosis;
        newParent.doctorAdvice = parent.doctorAdvice;
        newParent.dateCreated = System.currentTimeMillis();
        int newPrescriptionId = (int) vn.medisense.app.database.AppDatabase
                .getInstance(getApplicationContext())
                .medicationDao().insertPrescriptionData(newParent);

        // Nhân đôi mỗi Thuốc + tạo Reminders từ hôm nay
        for (vn.medisense.app.database.Medication oldMed : meds) {
            vn.medisense.app.database.Medication newMed = new vn.medisense.app.database.Medication();
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

            long newMedId = medicationRepository.insertMedicationSync(newMed);

            int freq = Math.max(1, newMed.frequency);
            long intervalMillis = (24L * 60L * 60L * 1000L) / freq;
            java.util.List<vn.medisense.app.database.Reminder> reminders = new java.util.ArrayList<>();
            for (int day = 0; day < newMed.durationDays; day++) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.add(java.util.Calendar.DAY_OF_YEAR, day);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 8);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                long start = cal.getTimeInMillis();
                for (int i = 0; i < freq; i++) {
                    reminders.add(new vn.medisense.app.database.Reminder((int) newMedId, start + i * intervalMillis));
                }
            }
            medicationRepository.insertRemindersSync(reminders);
        }

        runOnUiThread(() -> {
            Toast.makeText(this, "Đã nhân bản đơn thuốc thành công!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
    }
}
