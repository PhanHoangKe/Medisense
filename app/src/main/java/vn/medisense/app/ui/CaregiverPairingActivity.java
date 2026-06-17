package vn.medisense.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.database.FirebaseDatabase;

import vn.medisense.app.databinding.ActivityCaregiverPairingBinding;

import java.util.Random;

public class CaregiverPairingActivity extends AppCompatActivity {
    private ActivityCaregiverPairingBinding binding;
    private SharedPreferences prefs;

    private final androidx.activity.result.ActivityResultLauncher<com.journeyapps.barcodescanner.ScanOptions> barcodeLauncher = registerForActivityResult(
            new com.journeyapps.barcodescanner.ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    String scannedContent = result.getContents().trim();
                    String code = extractPairingCode(scannedContent);
                    if (code != null && code.length() == 6) {
                        binding.inputCaregiverCode.setText(code);
                        Toast.makeText(this, "Đã nhận dạng mã kết nối: " + code, Toast.LENGTH_SHORT).show();
                        connectAsCaregiver(code);
                    } else {
                        Toast.makeText(this, "Mã QR không đúng định dạng kết nối MediSense!", Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCaregiverPairingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE);

        binding.btnSelectPatient.setOnClickListener(v -> selectRole("patient"));
        binding.btnSelectCaregiver.setOnClickListener(v -> selectRole("caregiver"));
        binding.btnGenerateCode.setOnClickListener(v -> generateFamilyCode());
        binding.btnConnectCaregiver.setOnClickListener(v -> attemptCaregiverConnect());
        binding.btnDisconnect.setOnClickListener(v -> showDisconnectDialog());
        binding.btnBackToRoleFromPatient.setOnClickListener(v -> resetRoleSelection());
        binding.btnBackToRoleFromCaregiver.setOnClickListener(v -> resetRoleSelection());

        // Setup endIcon click listener for QR Code scanning
        binding.layoutInputCaregiverCode.setEndIconOnClickListener(v -> startQrScanner());

        handleIntent(getIntent());
        refreshUi();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(android.content.Intent intent) {
        if (intent != null && android.content.Intent.ACTION_VIEW.equals(intent.getAction())) {
            android.net.Uri data = intent.getData();
            if (data != null) {
                String code = data.getQueryParameter("code");
                if (code != null && code.trim().length() == 6) {
                    code = code.trim().toUpperCase();
                    prefs.edit().putString("userRole", "caregiver").apply();
                    binding.inputCaregiverCode.setText(code);
                    Toast.makeText(this, "Đã nhận mã kết nối từ liên kết: " + code, Toast.LENGTH_SHORT).show();
                    connectAsCaregiver(code);
                }
            }
        }
    }

    private String extractPairingCode(String content) {
        if (content == null) return null;
        content = content.trim();
        if (content.startsWith("http://") || content.startsWith("https://") || content.startsWith("medisense://")) {
            android.net.Uri uri = android.net.Uri.parse(content);
            String code = uri.getQueryParameter("code");
            if (code != null) return code.trim().toUpperCase();
        }
        if (content.length() == 6) {
            return content.toUpperCase();
        }
        return null;
    }

    private void startQrScanner() {
        com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions();
        options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE);
        options.setPrompt("Quét mã QR kết nối của người bệnh");
        options.setCameraId(0);
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(false);
        options.setOrientationLocked(false);
        barcodeLauncher.launch(options);
    }

    private void refreshUi() {
        String monitoringCode = prefs.getString("monitoringCode", null);
        String familyCode = prefs.getString("familyCode", null);
        String role = prefs.getString("userRole", null);

        if ("caregiver".equals(role) && monitoringCode != null && !monitoringCode.isEmpty()) {
            showConnectedState("caregiver", monitoringCode);
            return;
        }

        if ("patient".equals(role) && familyCode != null && !familyCode.isEmpty()) {
            showConnectedState("patient", familyCode);
            return;
        }

        binding.layoutConnected.setVisibility(View.GONE);

        if (role == null || role.trim().isEmpty()) {
            binding.layoutRoleSelection.setVisibility(View.VISIBLE);
            binding.layoutPatientFlow.setVisibility(View.GONE);
            binding.layoutCaregiverFlow.setVisibility(View.GONE);
            return;
        }

        if ("patient".equals(role)) {
            binding.layoutRoleSelection.setVisibility(View.GONE);
            binding.layoutPatientFlow.setVisibility(View.VISIBLE);
            binding.layoutCaregiverFlow.setVisibility(View.GONE);

            if (familyCode != null && !familyCode.isEmpty()) {
                binding.tvGeneratedCode.setText(familyCode);
            }
        } else if ("caregiver".equals(role)) {
            binding.layoutRoleSelection.setVisibility(View.GONE);
            binding.layoutPatientFlow.setVisibility(View.GONE);
            binding.layoutCaregiverFlow.setVisibility(View.VISIBLE);
        } else {
            binding.layoutRoleSelection.setVisibility(View.VISIBLE);
            binding.layoutPatientFlow.setVisibility(View.GONE);
            binding.layoutCaregiverFlow.setVisibility(View.GONE);
        }
    }

    private void showConnectedState(String role, String code) {
        binding.layoutConnected.setVisibility(View.VISIBLE);
        if ("patient".equals(role)) {
            binding.tvConnectedStatus.setText("Mã kết nối của bạn: " + code);
        } else {
            binding.tvConnectedStatus.setText("Đang giám sát người thân");
            loadMonitoredPatientName(code);
        }
        binding.layoutRoleSelection.setVisibility(View.GONE);
        binding.layoutPatientFlow.setVisibility(View.GONE);
        binding.layoutCaregiverFlow.setVisibility(View.GONE);
    }

    private void selectRole(String role) {
        prefs.edit().putString("userRole", role).apply();
        refreshUi();
    }

    private void resetRoleSelection() {
        prefs.edit().remove("userRole").apply();
        if (binding.inputCaregiverCode.getText() != null) {
            binding.inputCaregiverCode.getText().clear();
        }
        refreshUi();
    }

    private void attemptCaregiverConnect() {
        String code = binding.inputCaregiverCode.getText() != null
                ? binding.inputCaregiverCode.getText().toString().trim().toUpperCase()
                : "";

        if (code.length() != 6) {
            Toast.makeText(this, "Mã kết nối phải bao gồm 6 ký tự!", Toast.LENGTH_SHORT).show();
            return;
        }

        connectAsCaregiver(code);
    }

    private void generateFamilyCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        while (sb.length() < 6) {
            int index = (int) (rnd.nextFloat() * chars.length());
            sb.append(chars.charAt(index));
        }
        String newCode = sb.toString();

        binding.tvGeneratedCode.setText(newCode);

        // Lưu vào máy
        prefs.edit().putString("familyCode", newCode)
            .putString("userRole", "patient")
            .apply();
                
        // Thêm lưu lên Firebase Auth (Firestore & RTDB)
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(user.getUid())
                    .update("familyCode", newCode, "role", "patient");
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference()
                    .child("userBindings").child(user.getUid()).child("familyCode")
                    .setValue(newCode);
        }

        Toast.makeText(this, "Đã tạo mã kết nối. Hãy đưa mã này cho người giám sát!", Toast.LENGTH_LONG).show();
    }

    private void connectAsCaregiver(String inputCode) {
        prefs.edit()
                .putString("monitoringCode", inputCode)
                .putString("userRole", "caregiver")
                .apply();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance()
                    .collection("users").document(user.getUid())
                    .update("monitoringCode", inputCode, "role", "caregiver");
            FirebaseDatabase.getInstance().getReference()
                    .child("userBindings").child(user.getUid()).child("monitoringCode")
                    .setValue(inputCode);
            FirebaseDatabase.getInstance().getReference()
                    .child("families").child(inputCode).child("caregivers").child(user.getUid())
                    .setValue(true);
        }

        Toast.makeText(this, "Kết nối thành công!", Toast.LENGTH_LONG).show();
        refreshUi();
    }

    private void showDisconnectDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Ngắt kết nối giám sát?")
                .setMessage("Bạn sẽ không nhận được thông báo nhắc nhở và lịch sử uống thuốc của người thân nữa. Bạn có chắc chắn muốn ngắt kết nối?")
                .setPositiveButton("Ngắt kết nối", (dialog, which) -> disconnectCaregiver())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void disconnectCaregiver() {
        String role = prefs.getString("userRole", null);
        String monitoringCode = prefs.getString("monitoringCode", null);
        String familyCode = prefs.getString("familyCode", null);

        prefs.edit()
                .remove("monitoringCode")
            .remove("familyCode")
                .remove("userRole")
                .apply();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            if ("caregiver".equals(role) && monitoringCode != null) {
            FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .update("monitoringCode", null, "role", "patient");
            FirebaseDatabase.getInstance().getReference()
                .child("userBindings").child(user.getUid()).child("monitoringCode")
                .setValue(null);
            FirebaseDatabase.getInstance().getReference()
                .child("families").child(monitoringCode).child("caregivers").child(user.getUid())
                .setValue(null);
            }

            if ("patient".equals(role) && familyCode != null) {
            FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .update("familyCode", null, "role", "patient");
            FirebaseDatabase.getInstance().getReference()
                .child("userBindings").child(user.getUid()).child("familyCode")
                .setValue(null);
            }
        }

        Toast.makeText(this, "Đã ngắt kết nối.", Toast.LENGTH_SHORT).show();
        refreshUi();
    }

    private void loadMonitoredPatientName(String monitoringCode) {
        if (monitoringCode == null || monitoringCode.isEmpty()) {
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("familyCode", monitoringCode)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        String fullName = querySnapshot.getDocuments().get(0).getString("fullName");
                        if (fullName == null || fullName.trim().isEmpty()) {
                            fullName = querySnapshot.getDocuments().get(0).getString("name");
                        }

                        if (fullName != null && !fullName.trim().isEmpty()) {
                            binding.tvConnectedStatus.setText("Đang kết nối với: " + fullName.trim());
                            return;
                        }
                    }

                    binding.tvConnectedStatus.setText("Đang kết nối với: " + monitoringCode);
                })
                .addOnFailureListener(error -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    binding.tvConnectedStatus.setText("Đang kết nối với: " + monitoringCode);
                });
    }
}
