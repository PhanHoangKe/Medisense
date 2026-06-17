package vn.medisense.app.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import vn.medisense.app.R;
import vn.medisense.app.api.ParsedMedicationInfo;
import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.MedicationDao;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.databinding.BottomSheetMedicationConfirmBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MedicationConfirmBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetMedicationConfirmBinding binding;
    private ParsedMedicationInfo parsedInfo;
    private MedicationDao medicationDao;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    public static MedicationConfirmBottomSheet newInstance(ParsedMedicationInfo info) {
        MedicationConfirmBottomSheet fragment = new MedicationConfirmBottomSheet();
        fragment.parsedInfo = info;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = BottomSheetMedicationConfirmBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        medicationDao = AppDatabase.getInstance(requireContext().getApplicationContext()).medicationDao();

        if (parsedInfo != null) {
            binding.inputName.setText(parsedInfo.name);
            binding.inputDosage.setText(parsedInfo.dosage);
            binding.inputFrequency.setText(String.valueOf(parsedInfo.frequency));
            binding.inputDuration.setText(String.valueOf(parsedInfo.durationDays));
            binding.inputNotes.setText(parsedInfo.notes);

            if (parsedInfo.times != null && !parsedInfo.times.isEmpty()) {
                binding.inputTimes.setText(TextUtils.join(", ", parsedInfo.times));
            }

            // Hiển thị trạng thái AI nếu cần kiểm tra
            StringBuilder status = new StringBuilder();
            if (parsedInfo.needsReview) {
                status.append(getString(R.string.ai_needs_review));
            }
            if (parsedInfo.missingFields != null && !parsedInfo.missingFields.isEmpty()) {
                if (status.length() > 0) status.append("\n");
                status.append(getString(R.string.ai_missing_fields_prefix))
                        .append(" ")
                        .append(android.text.TextUtils.join(", ", parsedInfo.missingFields));
            }
            if (binding.textAiStatus != null) {
                if (status.length() > 0) {
                    binding.textAiStatus.setText(status.toString());
                    binding.textAiStatus.setVisibility(View.VISIBLE);
                } else {
                    binding.textAiStatus.setVisibility(View.GONE);
                }
            }
        }

        binding.buttonSave.setOnClickListener(v -> saveMedicationAndReminders());
    }

    private void saveMedicationAndReminders() {
        String name = binding.inputName.getText() != null ? binding.inputName.getText().toString().trim() : "";
        String dosage = binding.inputDosage.getText() != null ? binding.inputDosage.getText().toString().trim() : "";
        String frequencyText = binding.inputFrequency.getText() != null
                ? binding.inputFrequency.getText().toString().trim()
                : "1";
        String durationText = binding.inputDuration.getText() != null
                ? binding.inputDuration.getText().toString().trim()
                : "1";
        String notes = binding.inputNotes.getText() != null ? binding.inputNotes.getText().toString().trim() : "";
        String timesText = binding.inputTimes.getText() != null ? binding.inputTimes.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            binding.inputLayoutName.setError("Tên thuốc không được để trống");
            return;
        }

        int frequency = 1;
        try {
            frequency = Integer.parseInt(frequencyText);
        } catch (Exception ignored) {
        }

        int durationDays = 1;
        try {
            durationDays = Integer.parseInt(durationText);
        } catch (Exception ignored) {
        }

        List<String> timesList = new ArrayList<>();
        if (!TextUtils.isEmpty(timesText)) {
            String[] split = timesText.split(",");
            for (String t : split) {
                timesList.add(t.trim());
            }
        }

        Medication medication = new Medication();
        medication.name = name;
        medication.dosage = dosage;
        medication.frequency = frequency;
        medication.durationDays = durationDays;
        medication.instructions = notes;
        medication.isCritical = false;

        // Khởi tạo thông tin kho thuốc từ AI
        if (parsedInfo != null && parsedInfo.totalQuantity > 0) {
            medication.totalStock = parsedInfo.totalQuantity;
            medication.currentStock = parsedInfo.totalQuantity;
        } else {
            // Mặc định nếu AI không trả về
            medication.totalStock = 0;
            medication.currentStock = 0;
        }

        if (parsedInfo != null && parsedInfo.dosagePerIntake > 0) {
            medication.dosagePerIntake = parsedInfo.dosagePerIntake;
        } else {
            medication.dosagePerIntake = 1;
        }

        medication.lowStockThreshold = 5; // Mặc định cảnh báo khi còn 5 viên

        int finalDurationDays = durationDays;
        int finalFrequency = frequency;

        dbExecutor.execute(() -> {
            long medicationId = medicationDao.insertMedication(medication);

            List<Reminder> remindersToSave = new ArrayList<>();

            // Tạo nhắc nhở cho mỗi ngày trong thời lượng
            for (int day = 0; day < finalDurationDays; day++) {

                if (timesList.isEmpty()) {
                    // Dự phòng theo khoảng thời gian tần số bắt đầu từ 08:00
                    long startMillis = buildTimeMillis(8, 0, day);
                    long intervalMillis = (24L * 60L * 60L * 1000L) / Math.max(1, finalFrequency);
                    for (int i = 0; i < finalFrequency; i++) {
                        long timeMillis = startMillis + (i * intervalMillis);
                        remindersToSave.add(new Reminder((int) medicationId, timeMillis));
                    }
                } else {
                    // Sử dụng danh sách thời gian cụ thể
                    for (String timeStr : timesList) {
                        try {
                            String[] parts = timeStr.split(":");
                            if (parts.length >= 2) {
                                int hour = Integer.parseInt(parts[0]);
                                int minute = Integer.parseInt(parts[1]);
                                long timeMillis = buildTimeMillis(hour, minute, day);
                                remindersToSave.add(new Reminder((int) medicationId, timeMillis));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            long[] insertedIds = medicationDao.insertReminders(remindersToSave);

            for (int i = 0; i < insertedIds.length; i++) {
                vn.medisense.app.utils.AlarmHelper.scheduleAlarm(
                        requireContext().getApplicationContext(),
                        (int) insertedIds[i],
                        remindersToSave.get(i).reminderTime,
                        name,
                        dosage,
                        false); // Mặc định không phải khẩn cấp khi thêm từ bot
            }

            requireActivity().runOnUiThread(() -> {
                if (getActivity() == null || getContext() == null) return;
                Toast.makeText(getContext(), "Đã lưu thuốc vào lịch.", Toast.LENGTH_SHORT).show();
                dismiss();
                // Quay tro ve man hinh chinh
                getActivity().finish();
            });
        });
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
    }
}
