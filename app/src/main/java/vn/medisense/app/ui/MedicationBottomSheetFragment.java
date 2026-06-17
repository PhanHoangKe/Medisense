package vn.medisense.app.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.medisense.app.api.ParsedMedicationInfo;
import vn.medisense.app.databinding.BottomSheetPrescriptionListBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bottom sheet hien thi danh sach thuoc tu AI va cho phep chinh sua
 */
public class MedicationBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_JSON = "medication_json";
    private static final String ARG_OCR_TEXT = "ocr_text";
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private BottomSheetPrescriptionListBinding binding;
    private PrescriptionEditAdapter adapter;
    private MedicationViewModel viewModel;

    private vn.medisense.app.api.ParsedPrescriptionResult prescriptionResult;
    private String ocrText = "";

    public static MedicationBottomSheetFragment newInstance(vn.medisense.app.api.ParsedPrescriptionResult result) {
        MedicationBottomSheetFragment fragment = new MedicationBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_JSON, new Gson().toJson(result));
        fragment.setArguments(args);
        return fragment;
    }

    public static MedicationBottomSheetFragment newInstance(vn.medisense.app.api.ParsedPrescriptionResult result,
            String ocrText) {
        MedicationBottomSheetFragment fragment = new MedicationBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_JSON, new Gson().toJson(result));
        args.putString(ARG_OCR_TEXT, ocrText);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = BottomSheetPrescriptionListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(MedicationViewModel.class);
        adapter = new PrescriptionEditAdapter();

        binding.recyclerPrescription.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerPrescription.setAdapter(adapter);

        prescriptionResult = readResult();
        ocrText = getArguments() != null ? getArguments().getString(ARG_OCR_TEXT, "") : "";
        List<ParsedMedicationInfo> items = prescriptionResult.medications != null ? prescriptionResult.medications
                : new ArrayList<>();
        adapter.setItems(items);
        updateVisibility(items.isEmpty());

        adapter.setOnEditItemListener(this::openManualEditForItem);

        binding.buttonViewOcr.setOnClickListener(v -> showOcrTextDialog());
        binding.buttonManualEdit.setOnClickListener(v -> {
            List<ParsedMedicationInfo> current = adapter.getItems();
            if (current != null && !current.isEmpty()) {
                openManualEditForItem(current.get(0));
            } else {
                startActivity(new android.content.Intent(requireContext(), AddMedicationActivity.class));
            }
        });

        if (prescriptionResult.diagnosis != null && !prescriptionResult.diagnosis.isEmpty()) {
            binding.inputDiagnosis.setText(prescriptionResult.diagnosis);
        }
        if (prescriptionResult.doctorAdvice != null && !prescriptionResult.doctorAdvice.isEmpty()) {
            binding.inputDoctorAdvice.setText(prescriptionResult.doctorAdvice);
        }

        binding.buttonSaveAll.setOnClickListener(v -> saveAll());
    }

    private vn.medisense.app.api.ParsedPrescriptionResult readResult() {
        if (getArguments() == null) {
            return new vn.medisense.app.api.ParsedPrescriptionResult();
        }
        String json = getArguments().getString(ARG_JSON);
        if (TextUtils.isEmpty(json)) {
            return new vn.medisense.app.api.ParsedPrescriptionResult();
        }
        vn.medisense.app.api.ParsedPrescriptionResult result = new Gson().fromJson(json,
                vn.medisense.app.api.ParsedPrescriptionResult.class);
        return result != null ? result : new vn.medisense.app.api.ParsedPrescriptionResult();
    }

    private void updateVisibility(boolean isEmpty) {
        binding.recyclerPrescription.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.lottieWarning.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.textEmptyMessage.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void saveAll() {
        List<ParsedMedicationInfo> items = adapter.getItems();
        if (items.isEmpty()) {
            showErrorSnackbar();
            return;
        }

        ValidationResult validation = validateItems(items);
        if (!validation.invalidItems.isEmpty()) {
            showInvalidItemsDialog(validation);
            return;
        }

        // Cập nhật đối tượng kết quả với các giá trị UI có thể đã được sửa đổi
        prescriptionResult.diagnosis = binding.inputDiagnosis.getText() != null
                ? binding.inputDiagnosis.getText().toString().trim()
                : "";
        prescriptionResult.doctorAdvice = binding.inputDoctorAdvice.getText() != null
                ? binding.inputDoctorAdvice.getText().toString().trim()
                : "";
        prescriptionResult.medications = items;

        viewModel.insertAll(prescriptionResult, () -> {
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "Da luu tat ca", Toast.LENGTH_SHORT).show();
                dismiss();
            });
        });
    }

    private void showErrorSnackbar() {
        Snackbar.make(binding.getRoot(),
                "Don thuoc mo hoac khong hop le, vui long chup lai hoac nhap tay!",
                Snackbar.LENGTH_LONG)
                .setBackgroundTint(androidx.core.content.ContextCompat.getColor(requireContext(), vn.medisense.app.R.color.status_error_dark))
                .show();
    }

    private void showOcrTextDialog() {
        android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
        android.widget.TextView textView = new android.widget.TextView(requireContext());
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        textView.setPadding(padding, padding, padding, padding);
        textView.setText(ocrText == null || ocrText.isEmpty() ? "Không có văn bản OCR" : ocrText);
        scrollView.addView(textView);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(vn.medisense.app.R.string.ocr_view_raw_text))
                .setView(scrollView)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void showInvalidItemsDialog(ValidationResult validation) {
        int invalidCount = validation.invalidItems.size();
        String message = "Có " + invalidCount + " thuốc cần kiểm tra trước khi lưu.";

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Chưa thể lưu tất cả")
                .setMessage(message)
                .setPositiveButton("Sửa từng thuốc", (d, w) -> {
                    if (!validation.invalidItems.isEmpty()) {
                        openManualEditForItem(validation.invalidItems.get(0).info);
                    }
                })
                .setNeutralButton("Chỉ lưu thuốc hợp lệ", (d, w) -> saveOnlyValid(validation))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void saveOnlyValid(ValidationResult validation) {
        if (validation.validItems.isEmpty()) {
            Toast.makeText(requireContext(), "Không có thuốc hợp lệ để lưu", Toast.LENGTH_SHORT).show();
            return;
        }

        vn.medisense.app.api.ParsedPrescriptionResult filtered = new vn.medisense.app.api.ParsedPrescriptionResult();
        filtered.diagnosis = prescriptionResult.diagnosis;
        filtered.doctorAdvice = prescriptionResult.doctorAdvice;
        filtered.medications = validation.validItems;

        viewModel.insertAll(filtered, () -> {
            requireActivity().runOnUiThread(() -> showSkippedItemsDialog(validation.invalidItems));
        });
    }

    private void showSkippedItemsDialog(List<InvalidItem> invalidItems) {
        StringBuilder message = new StringBuilder();
        for (InvalidItem item : invalidItems) {
            message.append("- ")
                    .append(item.name)
                    .append(": ")
                    .append(item.reason)
                    .append("\n");
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Đã lưu thuốc hợp lệ")
                .setMessage(message.toString().trim())
                .setPositiveButton("Đóng", (d, w) -> dismiss())
                .show();
    }

    private void openManualEditForItem(ParsedMedicationInfo info) {
        if (info == null) return;
        android.content.Intent intent = new android.content.Intent(requireContext(), AddMedicationActivity.class);
        if (info.name != null && !info.name.trim().isEmpty()) {
            intent.putExtra(AddMedicationActivity.EXTRA_MED_NAME, info.name);
        }
        intent.putExtra(AddMedicationActivity.EXTRA_DOSAGE, info.dosage);
        intent.putExtra(AddMedicationActivity.EXTRA_FREQUENCY, info.frequency);
        intent.putExtra(AddMedicationActivity.EXTRA_DURATION_DAYS, info.durationDays);
        intent.putExtra(AddMedicationActivity.EXTRA_TOTAL_QUANTITY, info.totalQuantity);
        intent.putExtra(AddMedicationActivity.EXTRA_INSTRUCTIONS, info.notes);
        intent.putExtra(AddMedicationActivity.EXTRA_MEAL_CONTEXT, info.mealContext);
        intent.putExtra(AddMedicationActivity.EXTRA_OFFSET_MINUTES, info.offsetMinutes);
        intent.putExtra(AddMedicationActivity.EXTRA_SPECIFIC_SHIFTS, info.specificShifts);
        if (info.times != null && !info.times.isEmpty()) {
            intent.putStringArrayListExtra(AddMedicationActivity.EXTRA_TIMES,
                    new java.util.ArrayList<>(info.times));
        }
        intent.putExtra(AddMedicationActivity.EXTRA_CONFIDENCE, info.confidence);
        intent.putExtra(AddMedicationActivity.EXTRA_NEEDS_REVIEW, info.needsReview);
        if (info.missingFields != null && !info.missingFields.isEmpty()) {
            intent.putStringArrayListExtra(AddMedicationActivity.EXTRA_MISSING_FIELDS,
                    new java.util.ArrayList<>(info.missingFields));
        }
        intent.putExtra(AddMedicationActivity.EXTRA_OCR_DIAGNOSIS, prescriptionResult.diagnosis);
        intent.putExtra(AddMedicationActivity.EXTRA_OCR_DOCTOR_ADVICE, prescriptionResult.doctorAdvice);
        intent.putExtra(AddMedicationActivity.EXTRA_OCR_TEXT, ocrText);
        intent.putExtra(AddMedicationActivity.EXTRA_SOURCE, "OCR_AI");
        startActivity(intent);
    }

    private ValidationResult validateItems(List<ParsedMedicationInfo> items) {
        ValidationResult result = new ValidationResult();
        for (ParsedMedicationInfo info : items) {
            if (info == null) continue;
            String reason = getInvalidReason(info);
            if (reason == null) {
                result.validItems.add(info);
            } else {
                result.invalidItems.add(new InvalidItem(info, reason));
            }
        }
        return result;
    }

    private String getInvalidReason(ParsedMedicationInfo info) {
        if (info.name == null || info.name.trim().isEmpty()) {
            return "Thiếu tên thuốc";
        }
        if (info.dosage == null || info.dosage.trim().isEmpty()) {
            return "Thiếu liều dùng";
        }
        if (info.needsReview) {
            return "Cần kiểm tra";
        }
        if (info.frequency <= 0) {
            return "Thiếu tần suất";
        }
        if (info.durationDays <= 0) {
            return "Thiếu số ngày dùng";
        }
        if (!hasValidTimes(info.times)) {
            return "Thiếu giờ uống";
        }
        return null;
    }

    private boolean hasValidTimes(List<String> times) {
        if (times == null || times.isEmpty()) return false;
        for (String t : times) {
            if (t != null && TIME_PATTERN.matcher(t.trim()).matches()) {
                return true;
            }
        }
        return false;
    }

    private static class ValidationResult {
        final List<ParsedMedicationInfo> validItems = new ArrayList<>();
        final List<InvalidItem> invalidItems = new ArrayList<>();
    }

    private static class InvalidItem {
        final ParsedMedicationInfo info;
        final String name;
        final String reason;

        InvalidItem(ParsedMedicationInfo info, String reason) {
            this.info = info;
            this.name = (info.name == null || info.name.trim().isEmpty()) ? "(Không rõ tên)" : info.name;
            this.reason = reason;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
