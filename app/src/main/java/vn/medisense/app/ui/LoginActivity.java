package vn.medisense.app.ui;

import android.content.Intent;
import android.content.SharedPreferences;
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

public class LoginActivity extends AppCompatActivity {

    private EditText edtEmail, edtPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private ProgressBar progressLogin;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        progressLogin = findViewById(R.id.progressLogin);

        btnLogin.setOnClickListener(v -> loginUser());
        
        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void loginUser() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập Email và Mật khẩu", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgress(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showProgress(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show();
                        syncUserProfileAndContinue();
                    } else {
                        String errMsg = task.getException() != null ? task.getException().getMessage() : "Lỗi không xác định";
                        
                        // Kiểm tra các lỗi cấu hình Firebase
                        if (errMsg != null && errMsg.contains("CONFIGURATION_NOT_FOUND")) {
                            new com.google.android.material.dialog.MaterialAlertDialogBuilder(LoginActivity.this)
                                .setTitle("Lỗi cấu hình Firebase")
                                .setMessage("Bạn chưa kích hoạt phương thức Đăng nhập bằng Email/Mật khẩu trên Firebase.\n\nHướng dẫn khắc phục:\n1. Truy cập Firebase Console\n2. Mở mục Authentication (Xác thực)\n3. Sang tab Sign-in method (Phương thức đăng nhập)\n4. Bật Email/Password\n5. Lưu lại và thử đăng nhập lại ứng dụng.")
                                .setPositiveButton("Đã hiểu", null)
                                .show();
                            return;
                        }
                        
                        Toast.makeText(LoginActivity.this, "Đăng nhập thất bại: " + errMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void syncUserProfileAndContinue() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(LoginActivity.this, SplashActivity.class));
            finish();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    SharedPreferences prefs = getSharedPreferences("MediSensePrefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String role = documentSnapshot.getString("role");
                        String monitoringCode = documentSnapshot.getString("monitoringCode");
                        String familyCode = documentSnapshot.getString("familyCode");

                        if (role != null && !role.trim().isEmpty()) {
                            editor.putString("userRole", role.trim());
                        } else {
                            editor.remove("userRole");
                        }

                        if (monitoringCode != null && !monitoringCode.trim().isEmpty()) {
                            editor.putString("monitoringCode", monitoringCode.trim());
                        } else {
                            editor.remove("monitoringCode");
                        }

                        if (familyCode != null && !familyCode.trim().isEmpty()) {
                            editor.putString("familyCode", familyCode.trim());
                        } else {
                            editor.remove("familyCode");
                        }
                    } else {
                        editor.remove("userRole");
                        editor.remove("monitoringCode");
                        editor.remove("familyCode");
                    }

                    editor.apply();
                    startActivity(new Intent(LoginActivity.this, SplashActivity.class));
                    finish();
                })
                .addOnFailureListener(error -> {
                    startActivity(new Intent(LoginActivity.this, SplashActivity.class));
                    finish();
                });
    }

    private void showProgress(boolean show) {
        progressLogin.setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
    }
}
