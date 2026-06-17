package vn.medisense.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import vn.medisense.app.api.ParsedMedicationInfo;
import vn.medisense.app.camera.CameraManager;
import vn.medisense.app.databinding.ActivityScanBinding;
import vn.medisense.app.ocr.TextRecognitionManager;
import vn.medisense.app.repository.GeminiRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import vn.medisense.app.utils.NetworkUtils;

/**
 * Activity quản lý tính năng quét đơn thuốc bằng AI
 * Sử dụng CameraX để chụp ảnh, ML Kit để OCR, và Gemini AI để phân tích
 */
public class ScanActivity extends AppCompatActivity {
    private ActivityScanBinding binding;
    private static final int CAMERA_PERMISSION_CODE = 100;

    // Các manager xử lý logic riêng biệt
    private CameraManager cameraManager;
    private TextRecognitionManager textRecognitionManager;
    private GeminiRepository geminiRepository;

    // Launcher để chọn ảnh từ thư viện
    private final androidx.activity.result.ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        nhanDienVanBanTuUri(imageUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Khởi tạo các manager
        khoiTaoCacManager();

        // Kiểm tra quyền camera
        if (kiemTraQuyenCamera()) {
            batDauCamera();
        } else {
            yeuCauQuyenCamera();
        }

        // Thiết lập sự kiện cho các nút
        thietLapSuKien();
    }

    /**
     * Khởi tạo các manager xử lý logic
     */
    private void khoiTaoCacManager() {
        cameraManager = new CameraManager(this);
        textRecognitionManager = new TextRecognitionManager(this);
        geminiRepository = new GeminiRepository();
    }

    /**
     * Thiết lập sự kiện click cho các nút
     */
    private void thietLapSuKien() {
        binding.captureButton.setOnClickListener(v -> chupAnh());
        binding.galleryButton.setOnClickListener(v -> moThuVien());
    }

    /**
     * Mở thư viện ảnh để chọn ảnh
     */
    private void moThuVien() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    /**
     * Kiểm tra quyền camera đã được cấp chưa
     */
    private boolean kiemTraQuyenCamera() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Yêu cầu quyền camera từ người dùng
     */
    private void yeuCauQuyenCamera() {
        ActivityCompat.requestPermissions(this,
                new String[] { Manifest.permission.CAMERA },
                CAMERA_PERMISSION_CODE);
    }

    /**
     * Bắt đầu camera và hiển thị preview
     */
    private void batDauCamera() {
        cameraManager.startCamera(
                this,
                binding.previewView,
                new CameraManager.CameraInitCallback() {
                    @Override
                    public void onCameraInitSuccess() {
                        // Camera đã sẵn sàng
                    }

                    @Override
                    public void onCameraInitError(String errorMessage) {
                        hienThiThongBao(errorMessage);
                    }
                });
    }

    /**
     * Chụp ảnh và xử lý
     */
    private void chupAnh() {
        File photoFile = taoFileTam();

        cameraManager.capturePhoto(photoFile, new CameraManager.CaptureCallback() {
            @Override
            public void onCaptureSuccess(File imageFile) {
                nhanDienVanBanTuFile(imageFile);
            }

            @Override
            public void onCaptureError(String errorMessage) {
                hienThiThongBao(errorMessage);
            }
        });
    }

    /**
     * Tạo file tạm để lưu ảnh chụp
     */
    @NonNull
    private File taoFileTam() {
        return new File(getExternalFilesDir(null), "scan_temp_" + System.currentTimeMillis() + ".jpg");
    }

    /**
     * Nhận dạng văn bản từ file ảnh
     */
    private void nhanDienVanBanTuFile(@NonNull File imageFile) {
        textRecognitionManager.recognizeTextFromFile(imageFile, new TextRecognitionManager.RecognitionCallback() {
            @Override
            public void onRecognitionSuccess(@NonNull String extractedText) {
                phanTichDonThuoc(extractedText);
            }

            @Override
            public void onRecognitionError(@NonNull String errorMessage) {
                hienThiThongBao(errorMessage);
            }
        });
    }

    /**
     * Nhận dạng văn bản từ URI ảnh (từ thư viện)
     */
    private void nhanDienVanBanTuUri(@NonNull Uri imageUri) {
        textRecognitionManager.recognizeTextFromUri(imageUri, new TextRecognitionManager.RecognitionCallback() {
            @Override
            public void onRecognitionSuccess(@NonNull String extractedText) {
                phanTichDonThuoc(extractedText);
            }

            @Override
            public void onRecognitionError(@NonNull String errorMessage) {
                hienThiThongBao(errorMessage);
            }
        });
    }

    /**
     * Phân tích đơn thuốc bằng Gemini AI.
     * Lưu OCR text gốc để hiển thị cho người dùng kiểm tra.
     */
    private void phanTichDonThuoc(@NonNull String extractedText) {
        // Lưu OCR text để hiển thị nếu cần
        this.lastOcrText = extractedText;

        if (!NetworkUtils.isNetworkAvailable(this)) {
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                    .setTitle("Không có kết nối mạng")
                    .setMessage(getString(vn.medisense.app.R.string.ai_offline_message)
                            + "\n\nBạn có thể nhập thủ công.")
                    .setPositiveButton("Nhập thủ công", (d, w) -> {
                        startActivity(new Intent(this, AddMedicationActivity.class));
                        finish();
                    })
                    .setNegativeButton("Hủy", null)
                    .show());
            return;
        }

        // Hiển thị loading (có thể thêm ProgressBar vào layout sau)
        geminiRepository.scanPrescription(extractedText, new GeminiRepository.PrescriptionCallback() {
            @Override
            public void onAnalysisSuccess(@NonNull vn.medisense.app.api.ParsedPrescriptionResult result) {
                if (result == null || result.medications == null || result.medications.isEmpty()) {
                    hienThiLoiDuLieu();
                    return;
                }
                hienThiDanhSachThuoc(result);
            }

            @Override
            public void onAnalysisError(@NonNull String errorMessage) {
                runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(ScanActivity.this)
                            .setTitle("Không thể phân tích")
                            .setMessage(getString(vn.medisense.app.R.string.ai_error_parse)
                                    + "\n\nBạn có thể nhập thủ công.")
                            .setPositiveButton("Nhập thủ công", (d, w) -> {
                                startActivity(new Intent(ScanActivity.this, AddMedicationActivity.class));
                                finish();
                            })
                            .setNeutralButton("Xem OCR gốc", (d, w) -> showOcrTextDialog())
                            .setNegativeButton("Hủy", null)
                            .show();
                });
            }
        });
    }

    /** OCR text gốc lưu lại để hiển thị khi cần */
    private String lastOcrText = "";

    /** Hiển thị dialog xem OCR text gốc */
    private void showOcrTextDialog() {
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.TextView textView = new android.widget.TextView(this);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        textView.setPadding(padding, padding, padding, padding);
        textView.setText(lastOcrText.isEmpty() ? "Không có văn bản OCR" : lastOcrText);
        scrollView.addView(textView);

        new MaterialAlertDialogBuilder(this)
            .setTitle(getString(vn.medisense.app.R.string.ocr_view_raw_text))
            .setView(scrollView)
            .setPositiveButton("Đóng", null)
            .show();
    }

    /**
     * Hiển thị danh sách thuốc từ kết quả AI.
     * Chặn "lưu nhanh" nếu tên rỗng hoặc needsReview=true.
     * Luôn hiển thị disclaimer y tế.
     */
    private void hienThiDanhSachThuoc(@NonNull vn.medisense.app.api.ParsedPrescriptionResult result) {
        if (result.medications != null && result.medications.size() == 1) {
            vn.medisense.app.api.ParsedMedicationInfo singleMed = result.medications.get(0);

            boolean nameEmpty   = singleMed.name == null || singleMed.name.trim().isEmpty();
            boolean needsReview = singleMed.needsReview || nameEmpty;

            // Xây dựng thông tin hiển thị
            String medDisplay = nameEmpty ? "⚠️ Tên thuốc chưa rõ" : singleMed.name;
            String dosageDisplay = (singleMed.dosage == null || singleMed.dosage.isEmpty())
                    ? "Chưa rõ liều" : singleMed.dosage;

            // Cảnh báo thiếu thông tin
            StringBuilder warningMsg = new StringBuilder();
            warningMsg.append("🤖 AI gợi ý:\n");
            warningMsg.append("Thuốc: ").append(medDisplay).append("\n");
            warningMsg.append("Liều: ").append(dosageDisplay).append("\n");
            if (needsReview) {
                warningMsg.append("\n⚠️ ").append(getString(vn.medisense.app.R.string.ocr_incomplete_warning));
            }
            warningMsg.append("\n\n").append(getString(vn.medisense.app.R.string.ai_medical_disclaimer));

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(vn.medisense.app.R.string.ai_suggestion_label))
                    .setMessage(warningMsg.toString())
                    .setPositiveButton("Chỉnh sửa & Lưu", (dialog, which) -> {
                        Intent fillIntent = new Intent(this, AddMedicationActivity.class);
                        if (!nameEmpty) fillIntent.putExtra(AddMedicationActivity.EXTRA_MED_NAME, singleMed.name);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_DOSAGE, singleMed.dosage);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_INSTRUCTIONS, singleMed.notes);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_FREQUENCY, singleMed.frequency);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_DOSAGE_PER_INTAKE, singleMed.dosagePerIntake);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_TOTAL_QUANTITY, singleMed.totalQuantity);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_DURATION_DAYS, singleMed.durationDays);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_MEAL_CONTEXT, singleMed.mealContext);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_OFFSET_MINUTES, singleMed.offsetMinutes);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_SPECIFIC_SHIFTS, singleMed.specificShifts);
                        if (singleMed.times != null && !singleMed.times.isEmpty()) {
                            fillIntent.putStringArrayListExtra(AddMedicationActivity.EXTRA_TIMES,
                                    new java.util.ArrayList<>(singleMed.times));
                        }
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_CONFIDENCE, singleMed.confidence);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_NEEDS_REVIEW, singleMed.needsReview);
                        if (singleMed.missingFields != null && !singleMed.missingFields.isEmpty()) {
                            fillIntent.putStringArrayListExtra(AddMedicationActivity.EXTRA_MISSING_FIELDS,
                                    new java.util.ArrayList<>(singleMed.missingFields));
                        }
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_OCR_DIAGNOSIS, result.diagnosis);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_OCR_DOCTOR_ADVICE, result.doctorAdvice);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_OCR_TEXT, lastOcrText);
                        fillIntent.putExtra(AddMedicationActivity.EXTRA_SOURCE, "OCR_AI");
                        startActivity(fillIntent);
                        finish();
                    })
                    .setNeutralButton(getString(vn.medisense.app.R.string.ocr_view_raw_text),
                            (d, w) -> showOcrTextDialog());

            // Chỉ cho "Lưu nhanh" nếu tên rõ và không cần review
            if (!needsReview) {
                builder.setNegativeButton("Lưu nhanh", (dialog, which) -> {
                    MedicationBottomSheetFragment bottomSheet =
                            MedicationBottomSheetFragment.newInstance(result, lastOcrText);
                    bottomSheet.show(getSupportFragmentManager(), "MedicationBottomSheetFragment");
                });
            } else {
                // Tên rỗng → chỉ cho chỉnh sửa
                builder.setNegativeButton("Hủy", null);
            }

            builder.show();
        } else {
            // Nhiều thuốc → hiện bottom sheet
                MedicationBottomSheetFragment bottomSheet =
                    MedicationBottomSheetFragment.newInstance(result, lastOcrText);
            bottomSheet.show(getSupportFragmentManager(), "MedicationBottomSheetFragment");
        }
    }

    private void hienThiLoiDuLieu() {
        Snackbar.make(binding.getRoot(),
                "Đơn thuốc mờ hoặc không hợp lệ, vui lòng chụp lại hoặc nhập tay!",
                Snackbar.LENGTH_LONG)
                .setBackgroundTint(androidx.core.content.ContextCompat.getColor(this, vn.medisense.app.R.color.status_error_dark))
                .show();
    }

    /**
     * Hiển thị kết quả phân tích trong Bottom Sheet
     */
    private void hienThiKetQuaPhanTich(@NonNull ParsedMedicationInfo medicationInfo) {
        MedicationConfirmBottomSheet bottomSheet = MedicationConfirmBottomSheet.newInstance(medicationInfo);
        bottomSheet.show(getSupportFragmentManager(), "MedicationConfirmBottomSheet");
    }

    /**
     * Hiển thị thông báo Toast
     */
    private void hienThiThongBao(@NonNull String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Giải phóng tài nguyên
        if (cameraManager != null) {
            cameraManager.shutdown();
        }
        if (textRecognitionManager != null) {
            textRecognitionManager.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (kiemTraQuyenCamera()) {
                batDauCamera();
            } else {
                hienThiThongBao("Bạn phải cấp quyền camera để dùng tính năng này!");
                finish();
            }
        }
    }
}
