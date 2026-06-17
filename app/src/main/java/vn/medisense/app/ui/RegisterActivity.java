package vn.medisense.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import vn.medisense.app.R;
import vn.medisense.app.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText edtEmail, edtPassword;
    private Button btnRegister;
    private TextView tvLogin;
    private ProgressBar progressRegister;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);
        progressRegister = findViewById(R.id.progressRegister);

        btnRegister.setOnClickListener(v -> registerUser());
        
        tvLogin.setOnClickListener(v -> finish());
    }

    private void registerUser() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (email.isEmpty() || password.length() < 6) {
            Toast.makeText(this, "Email không hợp lệ hoặc mật khẩu dưới 6 ký tự", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Tất cả tài khoản tạo mới mặc định là Universal User (đại diện bằng role patient)
        String role = "patient";

        showProgress(true);
        final String finalRole = role;

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        saveUserToFirestore(user, email, finalRole);
                    } else {
                        showProgress(false);
                        String errMsg = task.getException() != null ? task.getException().getMessage() : "Lỗi không xác định";
                        
                        // Kiểm tra các lỗi cấu hình Firebase phổ biến
                        if (errMsg != null && errMsg.contains("CONFIGURATION_NOT_FOUND")) {
                            new com.google.android.material.dialog.MaterialAlertDialogBuilder(RegisterActivity.this)
                                .setTitle("Lỗi cấu hình Firebase")
                                .setMessage("Bạn chưa kích hoạt phương thức Đăng nhập bằng Email/Mật khẩu trên Firebase.\n\nHướng dẫn khắc phục:\n1. Truy cập Firebase Console\n2. Mở mục Authentication (Xác thực)\n3. Sang tab Sign-in method (Phương thức đăng nhập)\n4. Bật Email/Password\n5. Lưu lại và thử đăng ký lại.")
                                .setPositiveButton("Đã hiểu", null)
                                .show();
                            return;
                        }
                        
                        Toast.makeText(RegisterActivity.this, "Lỗi đăng ký: " + errMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(FirebaseUser user, String email, String role) {
        if (user == null) return;
        
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("role", role);
        userData.put("createdAt", System.currentTimeMillis());
        // Lần đầu tạo tài khoản bệnh nhân, hệ thống tự cấp 1 mã gia đình
        String generatedCode = null;
        if ("patient".equals(role)) {
            generatedCode = generateFamilyCode();
            userData.put("familyCode", generatedCode);
        }
        
        final String codeToBind = generatedCode;

        db.collection("users").document(user.getUid())
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    // Cập nhật Rule Binding Node trên Realtime Database
                    if (codeToBind != null) {
                        FirebaseDatabase.getInstance().getReference()
                                .child("userBindings").child(user.getUid()).child("familyCode")
                                .setValue(codeToBind);
                    }
                    
                    showProgress(false);
                    Toast.makeText(RegisterActivity.this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show();
                    // Điều hướng qua Splash để điều hướng tự động
                    Intent intent = new Intent(RegisterActivity.this, SplashActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(RegisterActivity.this, "Lỗi lưu dữ liệu phân quyền: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private String generateFamilyCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    private void showProgress(boolean show) {
        progressRegister.setVisibility(show ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!show);
    }
}
