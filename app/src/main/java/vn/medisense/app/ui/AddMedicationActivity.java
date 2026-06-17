package vn.medisense.app.ui;

import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import vn.medisense.app.R;
import vn.medisense.app.api.DrugInteractionResponse;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.databinding.ActivityAddMedicationBinding;
import vn.medisense.app.utils.AlarmHelper;
import vn.medisense.app.utils.SafetyChecker;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import vn.medisense.app.databinding.DialogDrugWarningBinding;
import vn.medisense.app.repository.MedicationRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddMedicationActivity extends AppCompatActivity {

    // Intent extras cho AI auto-fill
    public static final String EXTRA_MED_NAME = "extra_med_name";
    public static final String EXTRA_DOSAGE = "extra_dosage";
    public static final String EXTRA_FREQUENCY = "extra_frequency";
    public static final String EXTRA_INSTRUCTIONS = "extra_instructions";
    public static final String EXTRA_DOSAGE_PER_INTAKE = "extra_dosage_per_intake";
    public static final String EXTRA_TOTAL_QUANTITY = "extra_total_quantity";
    public static final String EXTRA_DURATION_DAYS = "extra_duration_days";
    public static final String EXTRA_MEAL_CONTEXT = "extra_meal_context";
    public static final String EXTRA_OFFSET_MINUTES = "extra_offset_minutes";
    public static final String EXTRA_SPECIFIC_SHIFTS = "extra_specific_shifts";
    public static final String EXTRA_TIMES = "extra_times";
    public static final String EXTRA_CONFIDENCE = "extra_confidence";
    public static final String EXTRA_NEEDS_REVIEW = "extra_needs_review";
    public static final String EXTRA_MISSING_FIELDS = "extra_missing_fields";
    public static final String EXTRA_OCR_DIAGNOSIS = "extra_ocr_diagnosis";
    public static final String EXTRA_OCR_DOCTOR_ADVICE = "extra_ocr_doctor_advice";
    public static final String EXTRA_OCR_TEXT = "extra_ocr_text";
    public static final String EXTRA_SOURCE = "extra_source";
    public static final String EXTRA_EDIT_MEDICATION_ID = "extra_edit_medication_id";

    /** Danh sách đơn vị thuốc phổ biến */
    private static final String[] DOSAGE_UNITS = {
        "Viên", "ml", "Gói", "Giọt", "Muỗng", "Ống", "Miếng dán", "Viên sủi", "Viên nang"
    };

    private ActivityAddMedicationBinding binding;
    private MedicationRepository medicationRepository;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private SafetyChecker safetyChecker;
    private ProgressDialog progressDialog;

    private String ocrRawText = "";

    /** Đơn vị thuốc hiện tại (từ dropdown) */
    private String currentUnit = "Viên";
    /** Đánh dấu chip "Liên tục" đang được chọn */
    private boolean isContinuousMode = false;
    /** Đang cập nhật stock từ chip → tránh vòng lặp listener */
    private boolean isUpdatingFromChip = false;

    private static class TimeItem {
        int hour;
        int minute;
        TimeItem(int hour, int minute) {
            this.hour = hour;
            this.minute = minute;
        }
    }
    private List<TimeItem> selectedTimes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityAddMedicationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        medicationRepository = new MedicationRepository(getApplicationContext());
        safetyChecker = new SafetyChecker(this);

        setupDosageUnitDropdown();
        setupFrequencyListener();
        setupSmartEstimateListeners();
        setupDurationChips();
        updateTimePickers(1); // Default 1 time
        setupSteppedForm();
        syncDosageUnitChips("Viên");

        binding.buttonSaveMedication.setOnClickListener(v -> saveMedication());

        // Mở Camera khi nhấn card Quét
        binding.cardScan.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanActivity.class);
            startActivity(intent);
        });
        binding.buttonBack.setOnClickListener(v -> finish());
        // Kiểm tra AI auto-fill từ ScanActivity
        handleAutoFillIntent();
        // Kiểm tra edit mode
        handleEditModeIntent();
    }

    private Medication existingMedication = null;
    private void handleEditModeIntent() {
        Intent intent = getIntent();
        int editId = intent.getIntExtra(EXTRA_EDIT_MEDICATION_ID, -1);
        if (editId == -1) return;
        // Tải existing medication
        dbExecutor.execute(() -> {
            existingMedication = medicationRepository.getMedicationByIdSync(editId);
            if (existingMedication != null) {
                runOnUiThread(() -> {
                    // Prefill UI
                    binding.inputMedicationName.setText(existingMedication.name);
                    // Parse dosage + unit
                    parseDosageIntoFields(existingMedication.dosage);
                    binding.inputInstructions.setText(existingMedication.instructions);
                    binding.inputFrequency.setText(String.valueOf(existingMedication.frequency));
                    binding.inputTotalStock.setText(String.valueOf(existingMedication.totalStock));
                    // Cập nhật selectedTimes
                    selectedTimes.clear();
                    if (existingMedication.specificShifts != null && !existingMedication.specificShifts.isEmpty()) {
                        applyTimesFromList(new ArrayList<>(java.util.Arrays.asList(existingMedication.specificShifts.split(","))));
                    }
                    // Cập nhật title đến "Edit Medication"
                    setTitle("Chỉnh sửa thuốc");
                });
            }
        });
    }

    // ═══════════════════════════════════════════════════════
    // DOSAGE UNIT DROPDOWN
    // ═══════════════════════════════════════════════════════

    /**
     * Thiết lập dropdown đơn vị thuốc (Viên, ml, Gói, Giọt, Muỗng...)
     * Khi đổi đơn vị → cập nhật hint ô "Tổng số thuốc" và recalc estimate.
     */
    private void setupDosageUnitDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, DOSAGE_UNITS);
        binding.dropdownDosageUnit.setAdapter(adapter);

        binding.dropdownDosageUnit.setOnItemClickListener((parent, view, position, id) -> {
            currentUnit = DOSAGE_UNITS[position];
            updateTotalStockHint();
            updateSmartEstimate();
        });

        // Set hint ban đầu
        updateTotalStockHint();
    }

    /**
     * Cập nhật hint ô "Tổng số thuốc" theo đơn vị đã chọn.
     * VD: "Tổng số thuốc đang có (Viên)" hoặc "Tổng số thuốc đang có (ml)"
     */
    private void updateTotalStockHint() {
        String hint = getString(R.string.total_stock_hint, currentUnit);
        binding.inputLayoutTotalStock.setHint(hint);
    }

    // ═══════════════════════════════════════════════════════
    // AI AUTO-FILL
    // ═══════════════════════════════════════════════════════

    /**
     * Nhận dữ liệu auto-fill từ ScanActivity thông qua Intent extras.
     * Khi AI quét được thông tin thuốc, form sẽ được điền sẵn.
     */
    private void handleAutoFillIntent() {
        Intent intent = getIntent();
        if (intent == null) return;

        String source = intent.getStringExtra(EXTRA_SOURCE);
        boolean fromOcr = source != null && source.equalsIgnoreCase("OCR_AI");

        String name = intent.getStringExtra(EXTRA_MED_NAME);
        if (TextUtils.isEmpty(name) && !fromOcr) return;

        // Điền sẵn các trường nếu có
        if (!TextUtils.isEmpty(name)) {
            binding.inputMedicationName.setText(name);
        }

        // Liều lượng: thử parse số + đơn vị từ chuỗi
        String dosage = intent.getStringExtra(EXTRA_DOSAGE);
        if (!TextUtils.isEmpty(dosage)) {
            parseDosageIntoFields(dosage);
        }

        // Nếu có dosagePerIntake riêng từ AI → dùng trực tiếp
        int dosagePerIntake = intent.getIntExtra(EXTRA_DOSAGE_PER_INTAKE, 0);
        if (dosagePerIntake > 0) {
            binding.inputDosageAmount.setText(String.valueOf(dosagePerIntake));
        }

        String instructions = intent.getStringExtra(EXTRA_INSTRUCTIONS);
        if (!TextUtils.isEmpty(instructions)) {
            binding.inputInstructions.setText(instructions);
        }

        int frequency = intent.getIntExtra(EXTRA_FREQUENCY, 0);
        if (frequency > 0) {
            binding.inputFrequency.setText(String.valueOf(frequency));
        }

        int totalQuantity = intent.getIntExtra(EXTRA_TOTAL_QUANTITY, 0);
        if (totalQuantity > 0) {
            binding.inputTotalStock.setText(String.valueOf(totalQuantity));
        }

        // Time doses / specific shifts
        java.util.ArrayList<String> times = intent.getStringArrayListExtra(EXTRA_TIMES);
        String shifts = intent.getStringExtra(EXTRA_SPECIFIC_SHIFTS);
        java.util.List<String> finalTimes = buildTimesFromOcr(times, shifts, frequency);
        if (!finalTimes.isEmpty()) {
            applyTimesFromList(new java.util.ArrayList<>(finalTimes));
        }

        // OCR banner & missing fields
        boolean needsReview = intent.getBooleanExtra(EXTRA_NEEDS_REVIEW, false);
        java.util.ArrayList<String> missingFields = intent.getStringArrayListExtra(EXTRA_MISSING_FIELDS);
        if (missingFields == null) missingFields = new java.util.ArrayList<>();
        float confidence = intent.getFloatExtra(EXTRA_CONFIDENCE, 0f);
        if (confidence <= 0f && needsReview) {
            confidence = 0.5f;
        }

        if (fromOcr) {
            binding.layoutOcrBanner.setVisibility(View.VISIBLE);
            binding.textOcrBanner.setText(getString(R.string.ocr_needs_review_banner));
            if (!missingFields.isEmpty()) {
                binding.textOcrMissingFields.setText(getString(R.string.ocr_missing_fields_label)
                        + " " + android.text.TextUtils.join(", ", missingFields));
                binding.textOcrMissingFields.setVisibility(View.VISIBLE);
            } else {
                binding.textOcrMissingFields.setVisibility(View.GONE);
            }
            binding.textOcrSource.setVisibility(View.VISIBLE);
            binding.buttonViewOcrText.setOnClickListener(v -> showOcrTextDialog());

            String diagnosis = intent.getStringExtra(EXTRA_OCR_DIAGNOSIS);
            String doctorAdvice = intent.getStringExtra(EXTRA_OCR_DOCTOR_ADVICE);
            showOcrInfoBlock(diagnosis, doctorAdvice);
        }

        // Highlight missing fields trên UI
        if (missingFields.contains("name")) {
            binding.inputLayoutMedicationName.setError(getString(R.string.error_required));
        }
        if (missingFields.contains("dosage")) {
            binding.inputLayoutDosageAmount.setError(getString(R.string.error_required));
        }
        if (missingFields.contains("frequency")) {
            binding.inputLayoutFrequency.setError(getString(R.string.error_required));
        }
        if (missingFields.contains("durationDays")) {
            binding.inputLayoutTotalStock.setError(getString(R.string.error_required));
        }

        // Đổi tiêu đề thành "AI đã điền sẵn"
        binding.textFormTitle.setText(R.string.ai_autofill_title);

        // OCR raw text
        ocrRawText = intent.getStringExtra(EXTRA_OCR_TEXT);
    }

    private void showOcrInfoBlock(String diagnosis, String doctorAdvice) {
        boolean hasDiagnosis = diagnosis != null && !diagnosis.trim().isEmpty();
        boolean hasAdvice = doctorAdvice != null && !doctorAdvice.trim().isEmpty();

        if (!hasDiagnosis && !hasAdvice) {
            binding.layoutOcrInfo.setVisibility(View.GONE);
            return;
        }

        binding.layoutOcrInfo.setVisibility(View.VISIBLE);
        if (hasDiagnosis) {
            binding.textOcrDiagnosis.setText("Chẩn đoán: " + diagnosis.trim());
            binding.textOcrDiagnosis.setVisibility(View.VISIBLE);
        } else {
            binding.textOcrDiagnosis.setVisibility(View.GONE);
        }

        if (hasAdvice) {
            binding.textOcrDoctorAdvice.setText("Lời dặn: " + doctorAdvice.trim());
            binding.textOcrDoctorAdvice.setVisibility(View.VISIBLE);
        } else {
            binding.textOcrDoctorAdvice.setVisibility(View.GONE);
        }
    }

    private void showOcrTextDialog() {
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.TextView textView = new android.widget.TextView(this);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        textView.setPadding(padding, padding, padding, padding);
        textView.setText(ocrRawText == null || ocrRawText.isEmpty()
                ? "Không có văn bản OCR"
                : ocrRawText);
        scrollView.addView(textView);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.ocr_view_raw_text))
                .setView(scrollView)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void applyTimesFromList(java.util.ArrayList<String> times) {
        java.util.List<TimeItem> parsed = new java.util.ArrayList<>();
        for (String t : times) {
            int[] hm = parseTimeSafe(t);
            if (hm != null) {
                parsed.add(new TimeItem(hm[0], hm[1]));
            }
        }
        if (parsed.isEmpty()) {
            return;
        }
        selectedTimes = parsed;
        updateTimePickers(parsed.size());
        binding.inputFrequency.setText(String.valueOf(parsed.size()));
    }

    private int[] parseTimeSafe(String time) {
        if (time == null) return null;
        String[] parts = time.trim().split(":");
        if (parts.length < 2) return null;
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
            return new int[] { hour, minute };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private java.util.List<String> mapShiftsToTimes(String shifts) {
        java.util.List<String> times = new java.util.ArrayList<>();
        if (shifts == null || shifts.trim().isEmpty()) return times;
        String lower = shifts.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("morning")) times.add("08:00");
        if (lower.contains("noon")) times.add("12:00");
        if (lower.contains("afternoon")) times.add("14:00");
        if (lower.contains("evening")) times.add("18:00");
        if (lower.contains("night")) times.add("21:00");
        return times;
    }

    private java.util.List<String> buildTimesFromOcr(
            java.util.ArrayList<String> timeDoses,
            String specificShifts,
            int frequency) {
        java.util.List<String> finalTimes = new java.util.ArrayList<>();

        if (timeDoses != null) {
            for (String t : timeDoses) {
                if (parseTimeSafe(t) != null && !finalTimes.contains(t.trim())) {
                    finalTimes.add(t.trim());
                }
            }
        }

        if (finalTimes.isEmpty()) {
            java.util.List<String> mapped = mapShiftsToTimes(specificShifts);
            finalTimes.addAll(mapped);
        }

        int targetCount = Math.max(0, frequency);
        if (targetCount > 0) {
            if (finalTimes.size() > targetCount) {
                finalTimes = new java.util.ArrayList<>(finalTimes.subList(0, targetCount));
            } else if (finalTimes.size() < targetCount) {
                java.util.List<String> fallback = buildDefaultTimes(targetCount);
                for (String t : fallback) {
                    if (finalTimes.size() >= targetCount) break;
                    if (!finalTimes.contains(t)) {
                        finalTimes.add(t);
                    }
                }
            }
        }

        return finalTimes;
    }

    private java.util.List<String> buildDefaultTimes(int count) {
        java.util.List<String> times = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            int hour = 8 + (i * 4);
            if (hour >= 24) hour = hour % 24;
            times.add(String.format(java.util.Locale.getDefault(), "%02d:00", hour));
        }
        return times;
    }

    /**
     * Cố gắng bóc tách số và đơn vị từ chuỗi liều lượng (VD: "500mg", "2 viên", "5ml")
     */
    private void parseDosageIntoFields(String dosage) {
        dosage = dosage.trim();

        // Thử tách số + chữ
        StringBuilder numberPart = new StringBuilder();
        StringBuilder unitPart = new StringBuilder();
        boolean foundDigit = false;
        for (char c : dosage.toCharArray()) {
            if (Character.isDigit(c) || c == '.' || c == ',') {
                numberPart.append(c);
                foundDigit = true;
            } else if (foundDigit) {
                unitPart.append(c);
            } else {
                unitPart.append(c);
            }
        }

        if (numberPart.length() > 0) {
            binding.inputDosageAmount.setText(numberPart.toString().replace(",", "."));
        }

        String unitStr = unitPart.toString().trim().toLowerCase(Locale.ROOT);
        if (!unitStr.isEmpty()) {
            // Map các đơn vị thường gặp
            for (String unit : DOSAGE_UNITS) {
                if (unit.toLowerCase(Locale.ROOT).contains(unitStr)
                        || unitStr.contains(unit.toLowerCase(Locale.ROOT))) {
                    binding.dropdownDosageUnit.setText(unit, false);
                    syncDosageUnitChips(unit);
                    return;
                }
            }
        } else {
            syncDosageUnitChips("Viên");
        }
    }

    private void syncDosageUnitChips(String unit) {
        if (unit == null) return;
        String lower = unit.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("viên") || lower.contains("vien")) {
            binding.chipGroupDosageUnit.check(R.id.chipUnitVien);
            binding.inputLayoutDosageUnit.setVisibility(View.GONE);
            currentUnit = "Viên";
        } else if (lower.contains("ml")) {
            binding.chipGroupDosageUnit.check(R.id.chipUnitMl);
            binding.inputLayoutDosageUnit.setVisibility(View.GONE);
            currentUnit = "ml";
        } else if (lower.contains("gói") || lower.contains("goi")) {
            binding.chipGroupDosageUnit.check(R.id.chipUnitGoi);
            binding.inputLayoutDosageUnit.setVisibility(View.GONE);
            currentUnit = "Gói";
        } else if (lower.contains("giọt") || lower.contains("giot")) {
            binding.chipGroupDosageUnit.check(R.id.chipUnitGiot);
            binding.inputLayoutDosageUnit.setVisibility(View.GONE);
            currentUnit = "Giọt";
        } else {
            binding.chipGroupDosageUnit.check(R.id.chipUnitKhac);
            binding.inputLayoutDosageUnit.setVisibility(View.VISIBLE);
            binding.dropdownDosageUnit.setText(unit, false);
            currentUnit = unit;
        }
        updateTotalStockHint();
    }

    private void setupSteppedForm() {
        // Step navigation
        binding.buttonStep1Next.setOnClickListener(v -> {
            String name = binding.inputMedicationName.getText() != null 
                    ? binding.inputMedicationName.getText().toString().trim() 
                    : "";
            if (TextUtils.isEmpty(name)) {
                binding.inputLayoutMedicationName.setError(getString(R.string.error_required));
                return;
            }
            binding.inputLayoutMedicationName.setError(null);
            goToStep(2);
        });

        binding.buttonStep2Back.setOnClickListener(v -> goToStep(1));
        binding.buttonStep2Next.setOnClickListener(v -> {
            String frequencyText = binding.inputFrequency.getText() != null 
                    ? binding.inputFrequency.getText().toString().trim() 
                    : "";
            if (TextUtils.isEmpty(frequencyText)) {
                binding.inputLayoutFrequency.setError(getString(R.string.error_required));
                return;
            }
            binding.inputLayoutFrequency.setError(null);
            goToStep(3);
        });

        binding.buttonStep3Back.setOnClickListener(v -> goToStep(2));

        // Quick-select dosage unit chips
        binding.chipGroupDosageUnit.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipUnitVien) {
                currentUnit = "Viên";
                binding.inputLayoutDosageUnit.setVisibility(View.GONE);
            } else if (checkedId == R.id.chipUnitMl) {
                currentUnit = "ml";
                binding.inputLayoutDosageUnit.setVisibility(View.GONE);
            } else if (checkedId == R.id.chipUnitGoi) {
                currentUnit = "Gói";
                binding.inputLayoutDosageUnit.setVisibility(View.GONE);
            } else if (checkedId == R.id.chipUnitGiot) {
                currentUnit = "Giọt";
                binding.inputLayoutDosageUnit.setVisibility(View.GONE);
            } else if (checkedId == R.id.chipUnitKhac) {
                binding.inputLayoutDosageUnit.setVisibility(View.VISIBLE);
                currentUnit = binding.dropdownDosageUnit.getText().toString();
            }
            updateTotalStockHint();
            updateSmartEstimate();
        });

        // Duration Slider
        binding.sliderDuration.addOnChangeListener((slider, value, fromUser) -> {
            int days = (int) value;
            binding.textDurationSliderLabel.setText("Liệu trình dùng thuốc: " + days + " ngày");
            
            if (fromUser) {
                int dosageAmount = parseIntSafe(binding.inputDosageAmount.getText());
                int frequency = parseIntSafe(binding.inputFrequency.getText());
                if (dosageAmount <= 0) dosageAmount = 1;
                if (frequency <= 0) frequency = 1;

                isContinuousMode = false;
                int neededStock = dosageAmount * frequency * days;
                isUpdatingFromChip = true;
                binding.inputTotalStock.setText(String.valueOf(neededStock));
                isUpdatingFromChip = false;

                // Sync với chip group nếu there's a matching chip
                binding.chipGroupDuration.clearCheck();
                if (days == 3) binding.chipGroupDuration.check(R.id.chip3days);
                else if (days == 5) binding.chipGroupDuration.check(R.id.chip5days);
                else if (days == 7) binding.chipGroupDuration.check(R.id.chip7days);
                else if (days == 14) binding.chipGroupDuration.check(R.id.chip14days);
                else if (days == 30) binding.chipGroupDuration.check(R.id.chip30days);
                
                updateSmartEstimate();
            }
        });
    }

    private void goToStep(int step) {
        if (step == 1) {
            binding.layoutStep1Container.setVisibility(View.VISIBLE);
            binding.layoutStep2Container.setVisibility(View.GONE);
            binding.layoutStep3Container.setVisibility(View.GONE);

            binding.txtStep1Number.setBackgroundResource(R.drawable.step_active_background);
            binding.txtStep1Number.setTextColor(getColor(R.color.white));
            binding.txtStep1Label.setTextColor(getColor(R.color.brand_primary));
            binding.txtStep1Label.setTypeface(null, android.graphics.Typeface.BOLD);

            binding.txtStep2Number.setBackgroundResource(R.drawable.step_inactive_background);
            binding.txtStep2Number.setTextColor(getColor(R.color.gray_500));
            binding.txtStep2Label.setTextColor(getColor(R.color.gray_500));
            binding.txtStep2Label.setTypeface(null, android.graphics.Typeface.NORMAL);

            binding.txtStep3Number.setBackgroundResource(R.drawable.step_inactive_background);
            binding.txtStep3Number.setTextColor(getColor(R.color.gray_500));
            binding.txtStep3Label.setTextColor(getColor(R.color.gray_500));
            binding.txtStep3Label.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else if (step == 2) {
            binding.layoutStep1Container.setVisibility(View.GONE);
            binding.layoutStep2Container.setVisibility(View.VISIBLE);
            binding.layoutStep3Container.setVisibility(View.GONE);

            binding.txtStep1Number.setBackgroundResource(R.drawable.step_active_background);
            binding.txtStep1Number.setTextColor(getColor(R.color.white));
            binding.txtStep1Label.setTextColor(getColor(R.color.brand_primary));
            binding.txtStep1Label.setTypeface(null, android.graphics.Typeface.BOLD);

            binding.txtStep2Number.setBackgroundResource(R.drawable.step_active_background);
            binding.txtStep2Number.setTextColor(getColor(R.color.white));
            binding.txtStep2Label.setTextColor(getColor(R.color.brand_primary));
            binding.txtStep2Label.setTypeface(null, android.graphics.Typeface.BOLD);

            binding.txtStep3Number.setBackgroundResource(R.drawable.step_inactive_background);
            binding.txtStep3Number.setTextColor(getColor(R.color.gray_500));
            binding.txtStep3Label.setTextColor(getColor(R.color.gray_500));
            binding.txtStep3Label.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else if (step == 3) {
            binding.layoutStep1Container.setVisibility(View.GONE);
            binding.layoutStep2Container.setVisibility(View.GONE);
            binding.layoutStep3Container.setVisibility(View.VISIBLE);

            binding.txtStep1Number.setBackgroundResource(R.drawable.step_active_background);
            binding.txtStep1Number.setTextColor(getColor(R.color.white));
            binding.txtStep1Label.setTextColor(getColor(R.color.brand_primary));
            binding.txtStep1Label.setTypeface(null, android.graphics.Typeface.BOLD);

            binding.txtStep2Number.setBackgroundResource(R.drawable.step_active_background);
            binding.txtStep2Number.setTextColor(getColor(R.color.white));
            binding.txtStep2Label.setTextColor(getColor(R.color.brand_primary));
            binding.txtStep2Label.setTypeface(null, android.graphics.Typeface.BOLD);

            binding.txtStep3Number.setBackgroundResource(R.drawable.step_active_background);
            binding.txtStep3Number.setTextColor(getColor(R.color.white));
            binding.txtStep3Label.setTextColor(getColor(R.color.brand_primary));
            binding.txtStep3Label.setTypeface(null, android.graphics.Typeface.BOLD);
        }
    }

    // ═══════════════════════════════════════════════════════
    // SMART ESTIMATION (Tính toán thông minh)
    // ═══════════════════════════════════════════════════════

    /**
     * Lắng nghe thay đổi trên: dosageAmount, frequency, totalStock
     * để tự động tính toán và hiển thị dòng ước tính ngày dùng.
     *
     * Công thức: số_ngày = tổng_thuốc / (liều_mỗi_lần × số_lần_ngày)
     */
    private void setupSmartEstimateListeners() {
        SimpleTextWatcher estimateWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdatingFromChip) {
                    binding.chipGroupDuration.clearCheck();
                    isContinuousMode = false;
                }
                updateSmartEstimate();
            }
        };

        binding.inputDosageAmount.addTextChangedListener(estimateWatcher);
        binding.inputTotalStock.addTextChangedListener(estimateWatcher);
        binding.inputFrequency.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdatingFromChip) {
                    binding.chipGroupDuration.clearCheck();
                    isContinuousMode = false;
                }
                updateSmartEstimate();
            }
        });
    }

    /**
     * Tính toán và hiển thị ước tính số ngày dùng thuốc.
     * Dùng dosageAmount (liều mỗi lần) từ ô liều lượng ở trên.
     */
    private void updateSmartEstimate() {
        int dosageAmount = parseIntSafe(binding.inputDosageAmount.getText());
        int totalStock = parseIntSafe(binding.inputTotalStock.getText());
        int frequency = parseIntSafe(binding.inputFrequency.getText());

        if (dosageAmount <= 0 || frequency <= 0) {
            binding.textSmartEstimate.setText(R.string.smart_estimate_placeholder);
            binding.textSmartEstimate.setTextColor(getColor(R.color.gray_500));
            return;
        }

        if (totalStock <= 0) {
            binding.textSmartEstimate.setText(R.string.smart_estimate_placeholder);
            binding.textSmartEstimate.setTextColor(getColor(R.color.gray_500));
            return;
        }

        int dailyConsumption = dosageAmount * frequency;
        int estimatedDays = totalStock / dailyConsumption;

        if (estimatedDays <= 0) {
            binding.textSmartEstimate.setText(R.string.smart_estimate_insufficient);
            binding.textSmartEstimate.setTextColor(getColor(R.color.status_error_dark));
            return;
        }

        if (isContinuousMode) {
            String text = getString(R.string.smart_estimate_continuous, estimatedDays);
            binding.textSmartEstimate.setText(text);
        } else {
            Calendar endDate = Calendar.getInstance();
            endDate.add(Calendar.DAY_OF_YEAR, estimatedDays);
            String dateStr = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(endDate.getTime());
            String text = getString(R.string.smart_estimate_format, estimatedDays, dateStr);
            binding.textSmartEstimate.setText(text);
        }
        binding.textSmartEstimate.setTextColor(getColor(R.color.brand_primary));
    }

    // ═══════════════════════════════════════════════════════
    // DURATION CHIPS (Liệu trình nhanh)
    // ═══════════════════════════════════════════════════════

    /**
     * Thiết lập chip chọn nhanh liệu trình.
     * Khi chọn chip → tự tính ngược totalStock = dosageAmount × frequency × days
     */
    private void setupDurationChips() {
        int[] chipIds = {
            R.id.chip3days, R.id.chip5days, R.id.chip7days,
            R.id.chip14days, R.id.chip30days, R.id.chipContinuous
        };
        int[] dayValues = { 3, 5, 7, 14, 30, 0 };

        for (int i = 0; i < chipIds.length; i++) {
            Chip chip = findViewById(chipIds[i]);
            final int days = dayValues[i];

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) return;

                int dosageAmount = parseIntSafe(binding.inputDosageAmount.getText());
                int frequency = parseIntSafe(binding.inputFrequency.getText());

                if (dosageAmount <= 0) dosageAmount = 1;
                if (frequency <= 0) frequency = 1;

                isContinuousMode = (days == 0);

                if (days > 0) {
                    int neededStock = dosageAmount * frequency * days;
                    isUpdatingFromChip = true;
                    binding.inputTotalStock.setText(String.valueOf(neededStock));
                    isUpdatingFromChip = false;

                    if (days >= 1 && days <= 90) {
                        binding.sliderDuration.setValue((float) days);
                    }
                }

                updateSmartEstimate();
            });
        }
    }

    // ═══════════════════════════════════════════════════════
    // FREQUENCY & TIME PICKERS
    // ═══════════════════════════════════════════════════════

    private void setupFrequencyListener() {
        binding.inputFrequency.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                int frequency = 1;
                if (!TextUtils.isEmpty(s)) {
                    try {
                        frequency = Math.max(1, Integer.parseInt(s.toString()));
                    } catch (NumberFormatException ignored) {}
                }
                updateTimePickers(frequency);
            }
        });
    }

    private void updateTimePickers(int count) {
        binding.layoutTimePickers.removeAllViews();

        List<TimeItem> newSelectedTimes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i < selectedTimes.size()) {
                newSelectedTimes.add(selectedTimes.get(i));
            } else {
                int startHour = 8 + (i * 4);
                if (startHour >= 24) startHour = startHour % 24;
                newSelectedTimes.add(new TimeItem(startHour, 0));
            }
        }
        selectedTimes = newSelectedTimes;

        for (int i = 0; i < count; i++) {
            final int index = i;
            TimeItem timeItem = selectedTimes.get(i);

            com.google.android.material.button.MaterialButton button = new com.google.android.material.button.MaterialButton(this);
            button.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            button.setText(String.format(Locale.getDefault(), "Giờ lần %d: %02d:%02d", (i + 1), timeItem.hour, timeItem.minute));
            button.setIconResource(android.R.drawable.ic_menu_recent_history);

            button.setOnClickListener(v -> {
                TimePickerDialog dialog = new TimePickerDialog(
                        this,
                        (view, hourOfDay, minute) -> {
                            selectedTimes.get(index).hour = hourOfDay;
                            selectedTimes.get(index).minute = minute;
                            button.setText(String.format(Locale.getDefault(), "Giờ lần %d: %02d:%02d", (index + 1), hourOfDay, minute));
                        },
                        selectedTimes.get(index).hour,
                        selectedTimes.get(index).minute,
                        true);
                dialog.show();
            });

            binding.layoutTimePickers.addView(button);
        }
    }

    // ═══════════════════════════════════════════════════════
    // Lưu MEDICATION
    // ═══════════════════════════════════════════════════════

    private void saveMedication() {
        String name = binding.inputMedicationName.getText() != null
                ? binding.inputMedicationName.getText().toString().trim()
                : "";
        String instructions = binding.inputInstructions.getText() != null
                ? binding.inputInstructions.getText().toString().trim()
                : "";
        String frequencyText = binding.inputFrequency.getText() != null
                ? binding.inputFrequency.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(name)) {
            binding.inputLayoutMedicationName.setError(getString(R.string.error_required));
            return;
        }
        binding.inputLayoutMedicationName.setError(null);

        int frequency = 1;
        if (!TextUtils.isEmpty(frequencyText)) {
            try {
                frequency = Math.max(1, Integer.parseInt(frequencyText));
            } catch (NumberFormatException ignored) {
                frequency = 1;
            }
        }

        // Liều lượng = số + đơn vị (VD: "2 Viên", "5 ml")
        int dosageAmount = parseIntSafe(binding.inputDosageAmount.getText());
        if (dosageAmount <= 0) dosageAmount = 1;
        String dosageStr = dosageAmount + " " + currentUnit;

        // Tổng số thuốc
        int totalStock = parseIntSafe(binding.inputTotalStock.getText());
        if (totalStock <= 0) totalStock = 0;

        // Tính số ngày uống
        int durationDays = 0;
        if (!isContinuousMode && totalStock > 0 && dosageAmount > 0 && frequency > 0) {
            durationDays = totalStock / (dosageAmount * frequency);
        }

        Medication medication;
        if (existingMedication != null) {
            medication = existingMedication;
            // Cập nhật existing fields
            medication.name = name;
            medication.dosage = dosageStr;
            medication.instructions = instructions;
            medication.frequency = frequency;
            medication.isCritical = binding.switchCritical.isChecked();
            medication.totalStock = totalStock;
            medication.lowStockThreshold = existingMedication != null ? existingMedication.lowStockThreshold : 5;
            medication.dosagePerIntake = dosageAmount;
            medication.durationDays = durationDays;
            medication.specificShifts = selectedTimes.stream()
                    .map(t -> String.format("%02d:%02d", t.hour, t.minute))
                    .collect(java.util.stream.Collectors.joining(","));
            // Also Cập nhật currentStock nếu needed (keep original currentStock nếu totalStock is same, otherwise adjust)
            if (medication.totalStock != existingMedication.totalStock) {
                int diff = medication.totalStock - existingMedication.totalStock;
                medication.currentStock = Math.max(0, existingMedication.currentStock + diff);
            }
            // cho edit mode, skip AI interaction Kiểm tra nếu we want, but let's keep it cho consistency
            checkDrugInteractionBeforeSaving(medication);
        } else {
            medication = new Medication();
            medication.name = name;
            medication.dosage = dosageStr; // "2 Viên" thay vì gõ tự do
            medication.instructions = instructions;
            medication.frequency = frequency;
            medication.isCritical = binding.switchCritical.isChecked();

            // Quản lý kho thuốc — đọc từ input
            medication.currentStock = totalStock;
            medication.totalStock = totalStock;
            medication.lowStockThreshold = 5;
            medication.dosagePerIntake = dosageAmount; // dosageAmount = số viên/ml mỗi lần
            medication.durationDays = durationDays;
            medication.specificShifts = selectedTimes.stream()
                    .map(t -> String.format("%02d:%02d", t.hour, t.minute))
                    .collect(java.util.stream.Collectors.joining(","));

            checkDrugInteractionBeforeSaving(medication);
        }
    }

    // ═══════════════════════════════════════════════════════
    // DRUG INTERACTION CHECK (Giữ nguyên)
    // ═══════════════════════════════════════════════════════

    /**
     * Kiểm tra tương tác thuốc sử dụng Gemini AI.
     * Xử lý 3 trường hợp: onSuccess (có/không warning), onOffline, onError.
     */
    private void checkDrugInteractionBeforeSaving(Medication newMedication) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.checking_drug_interaction));
        progressDialog.setCancelable(false);
        progressDialog.show();

        dbExecutor.execute(() -> {
            List<Medication> existingMedications = medicationRepository.getAllMedicationsSync();

            safetyChecker.checkDrugInteraction(
                    existingMedications,
                    newMedication.name,
                    new SafetyChecker.SafetyCheckCallback() {
                        @Override
                        public void onSuccess(DrugInteractionResponse result) {
                            runOnUiThread(() -> {
                                dismissProgressDialog();
                                if (result.hasWarning) {
                                    showDrugWarningDialog(result, newMedication);
                                } else {
                                    saveMedicationToDatabase(newMedication);
                                }
                            });
                        }

                        @Override
                        public void onOffline(DrugInteractionResponse result) {
                            // Offline hoặc API key thiếu — cho phép lưu nhưng cảnh báo chưa kiểm tra
                            runOnUiThread(() -> {
                                dismissProgressDialog();
                                new MaterialAlertDialogBuilder(AddMedicationActivity.this)
                                        .setTitle("⚠️ Chưa kiểm tra tương tác")
                                        .setMessage(getString(R.string.drug_interaction_not_checked))
                                        .setPositiveButton("Vẫn lưu", (dialog, which) ->
                                                saveMedicationToDatabase(newMedication))
                                        .setNegativeButton("Hủy", null)
                                        .show();
                            });
                        }

                        @Override
                        public void onError(String errorMessage) {
                            runOnUiThread(() -> {
                                dismissProgressDialog();
                                new MaterialAlertDialogBuilder(AddMedicationActivity.this)
                                        .setTitle("Lỗi kiểm tra tương tác")
                                        .setMessage(errorMessage + "\n\n" +
                                                getString(R.string.drug_interaction_not_checked))
                                        .setPositiveButton("Vẫn lưu", (dialog, which) ->
                                                saveMedicationToDatabase(newMedication))
                                        .setNegativeButton("Hủy", null)
                                        .show();
                            });
                        }
                    });
        });
    }

    /**
     * Hiển thị dialog cảnh báo tương tác thuốc nâng cấp.
     * HIGH severity: mặc định không lưu, cần xác nhận rõ ràng.
     * Tất cả severity: hiển thị disclaimer y tế.
     */
    private void showDrugWarningDialog(DrugInteractionResponse result, Medication medication) {
        DialogDrugWarningBinding dialogBinding = DialogDrugWarningBinding.inflate(LayoutInflater.from(this));
        View dialogView = dialogBinding.getRoot();

        // ── Severity badge + tone màu ─────────────────────────────────────────
        String severityText;
        int severityColor;
        String severityTitle;
        if (result.isHighSeverity()) {
            severityText  = getString(R.string.severity_high);
            severityColor = 0xFFD32F2F; // đỏ đậm
            severityTitle = getString(R.string.drug_interaction_high_title);
        } else if (result.isMediumSeverity()) {
            severityText  = getString(R.string.severity_medium);
            severityColor = 0xFFF57C00; // cam
            severityTitle = getString(R.string.drug_interaction_medium_title);
        } else {
            severityText  = getString(R.string.severity_low);
            severityColor = 0xFFFBC02D; // vàng
            severityTitle = getString(R.string.drug_interaction_low_title);
        }
        dialogBinding.textSeverity.setText(severityText);
        
        // Dynamically style badge với rounded corners
        android.graphics.drawable.GradientDrawable severityBadge = new android.graphics.drawable.GradientDrawable();
        severityBadge.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        severityBadge.setCornerRadius(100f); // Fully rounded pill shape
        severityBadge.setColor(severityColor);
        dialogBinding.textSeverity.setBackground(severityBadge);

        // Adjust warning container icon background
        if (dialogBinding.cardWarningIcon != null) {
            int alphaColor = (severityColor & 0x00FFFFFF) | 0x1A000000; // 10% alpha
            dialogBinding.cardWarningIcon.setCardBackgroundColor(alphaColor);
        }

        // Adjust title color
        if (dialogBinding.textWarningTitle != null) {
            dialogBinding.textWarningTitle.setText(severityTitle);
            dialogBinding.textWarningTitle.setTextColor(severityColor);
        }
        if (dialogBinding.iconWarning != null) {
            dialogBinding.iconWarning.setColorFilter(severityColor);
        }
        if (dialogBinding.buttonToggleExpand != null) {
            dialogBinding.buttonToggleExpand.setTextColor(severityColor);
        }

        // ── Nội dung cảnh báo ─────────────────────────────────────────────────
        String mainMsg = result.getMainMessage();
        String warningMessage = mainMsg.isEmpty() ? severityTitle : mainMsg;
        if (result.isHighSeverity()) {
            warningMessage = warningMessage + "\n\n" + getString(R.string.drug_interaction_high_advice);
        }
        dialogBinding.textWarningMessage.setText(warningMessage);

        // ── Nút mở rộng/thu gọn nội dung cảnh báo ─────────────────────────────
        final boolean[] isExpanded = {false};
        dialogBinding.buttonToggleExpand.setOnClickListener(v -> {
            if (isExpanded[0]) {
                dialogBinding.textWarningMessage.setMaxLines(4);
                dialogBinding.textWarningMessage.setEllipsize(android.text.TextUtils.TruncateAt.END);
                dialogBinding.buttonToggleExpand.setText("Xem thêm");
                isExpanded[0] = false;
            } else {
                dialogBinding.textWarningMessage.setMaxLines(Integer.MAX_VALUE);
                dialogBinding.textWarningMessage.setEllipsize(null);
                dialogBinding.buttonToggleExpand.setText("Thu gọn");
                isExpanded[0] = true;
            }
        });

        // ── Cảnh báo thực phẩm ────────────────────────────────────────────────
        if (result.foodWarnings != null && !result.foodWarnings.isEmpty()) {
            dialogBinding.layoutFoodWarnings.setVisibility(View.VISIBLE);
            dialogBinding.textFoodWarnings.setText(result.foodWarnings);
        } else {
            dialogBinding.layoutFoodWarnings.setVisibility(View.GONE);
        }

        // ── Disclaimer y tế ───────────────────────────────────────────────────
        if (dialogBinding.textAiDisclaimer != null) {
            dialogBinding.textAiDisclaimer.setText(getString(R.string.ai_medical_disclaimer));
            dialogBinding.textAiDisclaimer.setVisibility(View.VISIBLE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // ── Nút Hủy ──────────────────────────────────────────────────────────
        dialogBinding.buttonCancel.setText(getString(R.string.cancel));
        dialogBinding.buttonCancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(severityColor));
        dialogBinding.buttonCancel.setOnClickListener(v -> dialog.dismiss());

        // ── Nút Sửa thông tin thuốc ───────────────────────────────────────────
        if (dialogBinding.buttonEditInfo != null) {
            dialogBinding.buttonEditInfo.setVisibility(View.VISIBLE);
            dialogBinding.buttonEditInfo.setText(getString(R.string.drug_interaction_edit_info));
            dialogBinding.buttonEditInfo.setOnClickListener(v -> {
                dialog.dismiss();
                // Không lưu — người dùng quay lại form để sửa
            });
        }

        // ── Nút Đánh dấu hỏi bác sĩ ──────────────────────────────────────────
        if (dialogBinding.buttonAskDoctor != null) {
            dialogBinding.buttonAskDoctor.setVisibility(View.VISIBLE);
            dialogBinding.buttonAskDoctor.setText(getString(R.string.drug_interaction_ask_doctor));
            dialogBinding.buttonAskDoctor.setOnClickListener(v -> {
                result.markedForDoctorConsult = true;
                applyAiWarningToMedication(result, medication, true);
                dialog.dismiss();
                saveMedicationToDatabase(medication);
            });
        }

        // ── Nút Vẫn lưu ──────────────────────────────────────────────────────
        // HIGH severity: label rõ ràng hơn
        String saveLabel = result.isHighSeverity()
                ? getString(R.string.drug_interaction_save_anyway)
                : getString(R.string.understood_continue_save);
        dialogBinding.buttonContinue.setText(saveLabel);
        dialogBinding.buttonContinue.setOnClickListener(v -> {
            dialog.dismiss();
            applyAiWarningToMedication(result, medication, false);
            saveMedicationToDatabase(medication);
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int maxHeight = (int) (metrics.heightPixels * 0.90);
            int width     = (int) (metrics.widthPixels  * 0.92);
            dialog.getWindow().setLayout(width,
                    Math.min(maxHeight, android.view.WindowManager.LayoutParams.WRAP_CONTENT));
        }
    }

    /**
     * Lưu thuốc vào cơ sở dữ liệu, tạo reminder và lên lịch alarm cho từng giờ uống.
     *
     * Luồng:
     *   1. Insert Medication → lấy medicationId
     *   2. Build Reminder list từ selectedTimes
     *   3. Insert Reminders → lấy reminderId[] từ Room
     *   4. Schedule alarm cho từng reminderId với AlarmHelper
     *   5. Nếu bất kỳ bước nào thất bại → không schedule alarm rác
     */
    private void saveMedicationToDatabase(Medication medication) {
        boolean isCritical = medication.isCritical;
        String  medName    = medication.name;
        String  dosage     = medication.dosage;

        dbExecutor.execute(() -> {
            if (existingMedication != null) {
                // Edit mode: Cập nhật medication
                medicationRepository.updateMedicationSync(medication);
                // Hủy old reminders
                List<Reminder> oldReminders = medicationRepository.getRemindersByMedicationIdSync(existingMedication.id);
                for (Reminder r : oldReminders) {
                    vn.medisense.app.utils.AlarmHelper.cancelAlarm(getApplicationContext(), r.id);
                    medicationRepository.deleteReminderSync(r.id);
                }
                // Build và insert new reminders
                List<Reminder> reminders = buildTodayReminders(existingMedication.id, medication.frequency);
                if (!reminders.isEmpty()) {
                    long[] reminderIds = medicationRepository.insertRemindersSync(reminders);
                    for (int i = 0; i < reminderIds.length && i < reminders.size(); i++) {
                        long rid = reminderIds[i];
                        if (rid <= 0) continue;

                        long alarmTime = reminders.get(i).reminderTime;
                        if (alarmTime < System.currentTimeMillis() - 60_000L) continue;

                        vn.medisense.app.utils.AlarmHelper.scheduleAlarm(
                                getApplicationContext(),
                                (int) rid,
                                alarmTime,
                                medName,
                                dosage,
                                isCritical
                        );
                    }
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, "Đã cập nhật thuốc", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } else {
                // Add mode: insert medication
                long medicationId = medicationRepository.insertMedicationSync(medication);
                if (medicationId <= 0) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Lỗi lưu thuốc. Vui lòng thử lại.", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Bước 2: Build reminder list
                List<Reminder> reminders = buildTodayReminders((int) medicationId, medication.frequency);
                if (reminders.isEmpty()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.medication_saved, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                // Bước 3: Insert reminders → lấy IDs
                long[] reminderIds = medicationRepository.insertRemindersSync(reminders);

                // Bước 4: Schedule alarm cho từng reminder
                // reminderIds[i] tương ứng với reminders.get(i)
                for (int i = 0; i < reminderIds.length && i < reminders.size(); i++) {
                    long rid = reminderIds[i];
                    if (rid <= 0) continue; // Insert thất bại → bỏ qua

                    long alarmTime = reminders.get(i).reminderTime;
                    // Chỉ schedule alarm cho giờ trong tương lai (hoặc trong vòng 1 phút qua)
                    if (alarmTime < System.currentTimeMillis() - 60_000L) continue;

                    vn.medisense.app.utils.AlarmHelper.scheduleAlarm(
                            getApplicationContext(),
                            (int) rid,
                            alarmTime,
                            medName,
                            dosage,
                            isCritical
                    );
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.medication_saved, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    /**
     * Ghi chú cảnh báo AI vào instructions để người dùng dễ theo dõi (không sửa DB sâu).
     */
    private void applyAiWarningToMedication(DrugInteractionResponse result,
            Medication medication,
            boolean markedForDoctor) {
        if (medication == null || result == null) return;

        StringBuilder note = new StringBuilder();
        note.append("\n\n[AI cảnh báo tương tác]");
        note.append("\nMức độ: ").append(result.severity != null ? result.severity : "N/A");
        String mainMsg = result.getMainMessage();
        if (mainMsg != null && !mainMsg.trim().isEmpty()) {
            note.append("\nTóm tắt: ").append(mainMsg.trim());
        }
        if (result.foodWarnings != null && !result.foodWarnings.trim().isEmpty()) {
            note.append("\nThực phẩm cần lưu ý: ").append(result.foodWarnings.trim());
        }
        if (markedForDoctor) {
            note.append("\nĐánh dấu: cần hỏi bác sĩ/dược sĩ.");
        }
        note.append("\nThời điểm kiểm tra: ")
                .append(new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                        .format(new java.util.Date()));

        if (medication.instructions == null || medication.instructions.trim().isEmpty()) {
            medication.instructions = note.toString().trim();
        } else {
            medication.instructions = medication.instructions + note;
        }
    }

    private List<Reminder> buildTodayReminders(int medicationId, int frequency) {
        List<Reminder> reminders = new ArrayList<>();
        for (TimeItem timeItem : selectedTimes) {
            long timeMillis = buildTodayTimeMillis(timeItem.hour, timeItem.minute);
            reminders.add(new Reminder(medicationId, timeMillis));
        }
        return reminders;
    }

    private long buildTodayTimeMillis(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    // ═══════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════

    private int parseIntSafe(CharSequence text) {
        if (text == null || TextUtils.isEmpty(text.toString().trim())) return 0;
        try {
            return (int) Double.parseDouble(text.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
        dismissProgressDialog();
    }
}
