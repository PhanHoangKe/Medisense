package vn.medisense.app.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import vn.medisense.app.BaseActivity;
import vn.medisense.app.R;
import vn.medisense.app.databinding.ActivitySettingsBinding;
import vn.medisense.app.services.MedicalIdService;
import vn.medisense.app.utils.BiometricAuthManager;
import vn.medisense.app.utils.PdfGenerator;
import vn.medisense.app.utils.ProfileManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;

/**
 * SettingsActivity - Màn hình cài đặt
 * Cho phép người dùng bật/tắt khóa sinh trắc học
 */
public class SettingsActivity extends BaseActivity {
    private ActivitySettingsBinding binding;
    private BiometricAuthManager biometricAuthManager;
    private ProfileManager profileManager;
    private static final int PERMISSION_REQUEST_CALL_PHONE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        biometricAuthManager = getBiometricAuthManager();
        profileManager = new ProfileManager(this);
        
        setupUI();
        setupSosSettings();

        binding.buttonExportPdf.setOnClickListener(v -> {
            Toast.makeText(this, "Đang tạo báo cáo PDF...", Toast.LENGTH_SHORT).show();
            PdfGenerator.generateAndShareReport(this);
        });



        binding.buttonShareQr.setOnClickListener(v -> showShareQr());

        binding.buttonFamilyConnect.setOnClickListener(v -> {
            startActivity(new Intent(this, CaregiverPairingActivity.class));
        });
        
        setupAuthButton();
    }
    
    private void setupAuthButton() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            // Đã đăng nhập
            binding.textUserTitle.setText(currentUser.getEmail());
            binding.textUserSubtitle.setText("Tài khoản của bạn đã được liên kết trực tuyến.");
            binding.viewBadgeDot.setVisibility(android.view.View.GONE);
            
            binding.buttonAuth.setText("Đăng xuất");
            binding.buttonAuth.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Đăng xuất")
                        .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản này?")
                        .setPositiveButton("Đăng xuất", (dialog, which) -> {
                            vn.medisense.app.utils.LogoutHelper.logout(this);
                            finish();
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            });
        } else {
            // Chưa đăng nhập (Guest Mode)
            binding.textUserTitle.setText("Khách truy cập");
            binding.textUserSubtitle.setText("Hoàn thiện hồ sơ để lưu trữ và đồng bộ hóa dữ liệu trực tuyến.");
            binding.viewBadgeDot.setVisibility(android.view.View.VISIBLE);
            
            binding.buttonAuth.setText("Hoàn thành hồ sơ");
            binding.buttonAuth.setOnClickListener(v -> {
                startActivity(new Intent(this, LoginActivity.class));
            });
        }
    }

    private void setupUI() {
        // Toolbar Navigation
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Cài đặt");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        
        // Collapsible items
        binding.rowBiometric.setOnClickListener(v -> toggleCollapsible(binding.layoutBiometricSettings, binding.chevronBiometric));
        binding.rowSos.setOnClickListener(v -> toggleCollapsible(binding.layoutSosSettings, binding.chevronSos));
        
        // Mock items
        binding.rowRemoveAds.setOnClickListener(v -> Toast.makeText(this, "Tính năng Premium: Gỡ quảng cáo đang phát triển", Toast.LENGTH_SHORT).show());
        binding.rowRecommend.setOnClickListener(v -> Toast.makeText(this, "Cảm ơn bạn đã giới thiệu MediSense!", Toast.LENGTH_SHORT).show());

        // Kiểm tra trạng thái hiện tại
        boolean isEnabled = biometricAuthManager.isBiometricEnabled();
        binding.switchBiometric.setChecked(isEnabled);
        updateBiometricStatus(isEnabled);
        
        // Kiểm tra hỗ trợ
        BiometricAuthManager.BiometricStatus status = biometricAuthManager.checkBiometricSupport();
        if (status != BiometricAuthManager.BiometricStatus.SUCCESS) {
            binding.switchBiometric.setEnabled(false);
            binding.textBiometricStatus.setText(getStatusMessage(status));
            binding.textBiometricStatus.setTextColor(0xFFD32F2F); // Red
            
            if (status == BiometricAuthManager.BiometricStatus.NONE_ENROLLED) {
                binding.buttonEnrollBiometric.setVisibility(android.view.View.VISIBLE);
                binding.buttonEnrollBiometric.setOnClickListener(v -> openBiometricSettings());
            }
        }
        
        // Switch listener
        binding.switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Kiểm tra lại hỗ trợ
                BiometricAuthManager.BiometricStatus currentStatus = 
                        biometricAuthManager.checkBiometricSupport();
                
                if (currentStatus == BiometricAuthManager.BiometricStatus.SUCCESS) {
                    // Test xác thực trước khi bật
                    testBiometricAuthentication(isChecked);
                } else {
                    binding.switchBiometric.setChecked(false);
                    showErrorDialog(getStatusMessage(currentStatus));
                }
            } else {
                // Tắt khóa
                biometricAuthManager.setBiometricEnabled(false);
                updateBiometricStatus(false);
                Toast.makeText(this, "Đã tắt khóa sinh trắc học", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleCollapsible(android.view.View layout, android.widget.ImageView chevron) {
        if (layout.getVisibility() == android.view.View.VISIBLE) {
            layout.setVisibility(android.view.View.GONE);
            chevron.animate().rotation(-90).setDuration(200).start();
        } else {
            layout.setVisibility(android.view.View.VISIBLE);
            chevron.animate().rotation(90).setDuration(200).start();
        }
    }

    /**
     * Test xác thực trước khi bật khóa
     */
    private void testBiometricAuthentication(boolean shouldEnable) {
        biometricAuthManager.authenticate(this, new BiometricAuthManager.BiometricAuthCallback() {
            @Override
            public void onAuthenticationSucceeded() {
                biometricAuthManager.setBiometricEnabled(shouldEnable);
                updateBiometricStatus(shouldEnable);
                Toast.makeText(SettingsActivity.this, 
                        "Đã bật khóa sinh trắc học", 
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationFailed() {
                binding.switchBiometric.setChecked(false);
                Toast.makeText(SettingsActivity.this, 
                        "Xác thực thất bại", 
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationError(String errorMessage) {
                binding.switchBiometric.setChecked(false);
                showErrorDialog(errorMessage);
            }

            @Override
            public void onAuthenticationCancelled() {
                binding.switchBiometric.setChecked(false);
            }

            @Override
            public void onAuthenticationLockout(String message) {
                binding.switchBiometric.setChecked(false);
                showErrorDialog(message);
            }
        });
    }

    /**
     * Cập nhật hiển thị trạng thái
     */
    private void updateBiometricStatus(boolean isEnabled) {
        if (isEnabled) {
            binding.textBiometricStatus.setText("✓ Khóa đang bật - Ứng dụng được bảo vệ");
            binding.textBiometricStatus.setTextColor(0xFF4CAF50); // Green
        } else {
            binding.textBiometricStatus.setText("Khóa đang tắt - Ứng dụng không được bảo vệ");
            binding.textBiometricStatus.setTextColor(0xFF757575); // Gray
        }
    }

    /**
     * Lấy thông báo trạng thái
     */
    private String getStatusMessage(BiometricAuthManager.BiometricStatus status) {
        switch (status) {
            case NO_HARDWARE:
                return "⚠️ Thiết bị không hỗ trợ sinh trắc học";
            case HARDWARE_UNAVAILABLE:
                return "⚠️ Phần cứng sinh trắc học không khả dụng";
            case NONE_ENROLLED:
                return "⚠️ Chưa đăng ký vân tay hoặc khuôn mặt";
            case SECURITY_UPDATE_REQUIRED:
                return "⚠️ Cần cập nhật bảo mật hệ thống";
            case UNSUPPORTED:
                return "⚠️ Sinh trắc học không được hỗ trợ";
            default:
                return "⚠️ Lỗi không xác định";
        }
    }

    /**
     * Mở cài đặt sinh trắc học của hệ thống
     */
    private void openBiometricSettings() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Đăng ký sinh trắc học")
                .setMessage("Bạn cần đăng ký vân tay hoặc khuôn mặt trong Cài đặt hệ thống trước.")
                .setPositiveButton("Mở Cài đặt", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Hiển thị dialog lỗi
     */
    private void showErrorDialog(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Lỗi")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
    private void setupSosSettings() {
        // Load existing data
        binding.editBloodType.setText(profileManager.getBloodType());
        binding.editAllergies.setText(profileManager.getAllergies());
        binding.editMedications.setText(profileManager.getMedications());
        binding.editIceContact.setText(profileManager.getIceContact());
        boolean isSosEnabled = profileManager.isSosEnabled();
        binding.switchSos.setChecked(isSosEnabled);

        // Lưu dữ liệu khi văn bản thay đổi
        TextWatcher saveWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                profileManager.setBloodType(binding.editBloodType.getText().toString());
                profileManager.setAllergies(binding.editAllergies.getText().toString());
                profileManager.setMedications(binding.editMedications.getText().toString());
                profileManager.setIceContact(binding.editIceContact.getText().toString());

                // Khởi động lại dịch vụ nếu đang chạy để cập nhật thông báo
                if (profileManager.isSosEnabled()) {
                    startMedicalIdService();
                }
            }
        };

        binding.editBloodType.addTextChangedListener(saveWatcher);
        binding.editAllergies.addTextChangedListener(saveWatcher);
        binding.editMedications.addTextChangedListener(saveWatcher);
        binding.editIceContact.addTextChangedListener(saveWatcher);

        // Toggle SOS Service
        binding.switchSos.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                enableSosFeature();
            } else {
                profileManager.setSosEnabled(false);
                stopMedicalIdService();
            }
        });
    }

    private void showShareQr() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, getString(R.string.share_qr_missing_auth), Toast.LENGTH_SHORT).show();
            return;
        }

        android.content.SharedPreferences prefs = getSharedPreferences("MediSensePrefs", MODE_PRIVATE);
        String role = prefs.getString("userRole", "patient");
        if (!"patient".equals(role)) {
            Toast.makeText(this, getString(R.string.share_qr_patient_only), Toast.LENGTH_SHORT).show();
            return;
        }

        String familyCode = prefs.getString("familyCode", null);
        if (familyCode == null || familyCode.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Chưa tạo mã liên kết")
                    .setMessage("Bạn chưa tạo mã liên kết người thân. Vui lòng vào mục 'Liên kết người thân' để tạo mã trước khi chia sẻ QR.")
                    .setPositiveButton("Mở liên kết", (dialog, which) -> {
                        startActivity(new Intent(this, CaregiverPairingActivity.class));
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
            return;
        }

        String payload = "medisense://pair?code=" + familyCode;
        Bitmap bitmap = generateQrBitmap(payload, 720);
        if (bitmap == null) {
            Toast.makeText(this, "Không thể tạo QR", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        imageView.setAdjustViewBounds(true);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        imageView.setPadding(padding, padding, padding, padding);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.share_qr_dialog_title)
                .setView(imageView)
                .setPositiveButton(R.string.share_qr_button, (dialog, which) -> shareQrBitmap(bitmap))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private Bitmap generateQrBitmap(String content, int size) {
        if (content == null || content.isEmpty()) return null;
        QRCodeWriter writer = new QRCodeWriter();
        try {
            com.google.zxing.common.BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            return null;
        }
    }

    private void shareQrBitmap(Bitmap bitmap) {
        File reportsDir = new File(getCacheDir(), "reports");
        if (!reportsDir.exists() && !reportsDir.mkdirs()) {
            Toast.makeText(this, "Không thể tạo file tạm", Toast.LENGTH_SHORT).show();
            return;
        }

        File outFile = new File(reportsDir, "qr_share.png");
        try (FileOutputStream out = new FileOutputStream(outFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            Toast.makeText(this, "Không thể lưu QR", Toast.LENGTH_SHORT).show();
            return;
        }

        String authority = getPackageName() + ".fileprovider";
        android.net.Uri uri = FileProvider.getUriForFile(this, authority, outFile);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/png");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_qr_title)));
    }

    private void enableSosFeature() {
        profileManager.setSosEnabled(true);
        startMedicalIdService();
        Toast.makeText(this, "SOS Medical ID đã được bật", Toast.LENGTH_SHORT).show();
    }

    private void startMedicalIdService() {
        Intent serviceIntent = new Intent(this, MedicalIdService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopMedicalIdService() {
        Intent serviceIntent = new Intent(this, MedicalIdService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "SOS Medical ID đã bị tắt", Toast.LENGTH_SHORT).show();
    }
}
