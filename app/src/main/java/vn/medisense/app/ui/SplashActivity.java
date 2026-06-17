package vn.medisense.app.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import vn.medisense.app.MainActivity;
import vn.medisense.app.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                // Đã đăng nhập -> Lấy Role từ Firestore
                FirebaseFirestore.getInstance().collection("users").document(currentUser.getUid())
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                String role = documentSnapshot.getString("role");
                                String familyCode = documentSnapshot.getString("familyCode");
                                String monitoringCode = documentSnapshot.getString("monitoringCode");
                                
                                // Lưu đè SharedPreferences để tương thích với các module cũ
                                SharedPreferences prefs = getSharedPreferences("MediSensePrefs", MODE_PRIVATE);
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("userRole", role);
                                if (familyCode != null && !familyCode.isEmpty()) {
                                    editor.putString("familyCode", familyCode);
                                } else {
                                    editor.remove("familyCode");
                                }
                                if (monitoringCode != null && !monitoringCode.isEmpty()) {
                                    editor.putString("monitoringCode", monitoringCode);
                                } else {
                                    editor.remove("monitoringCode");
                                }
                                editor.apply();
                                        
                                navigateBasedOnRole(role);
                            } else {
                                navigateBasedOnRole("patient");
                            }
                        })
                        .addOnFailureListener(e -> {
                            // Fallback khi offline
                            SharedPreferences prefs = getSharedPreferences("MediSensePrefs", MODE_PRIVATE);
                            String role = prefs.getString("userRole", "patient");
                            navigateBasedOnRole(role);
                        });
            } else {
                // Chưa đăng nhập -> Check first launch
                SharedPreferences prefs = getSharedPreferences("MediSensePrefs", MODE_PRIVATE);
                boolean onboarded = prefs.getBoolean("onboarded", false);
                
                if (!onboarded) {
                    // First time -> Show onboarding
                    startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
                } else {
                    // Already onboarded -> Guest Mode (MainActivity)
                    prefs.edit().putString("userRole", "patient").apply();
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                }
                finish();
            }
        }, 1500); // Hiển thị Splash 1.5 giây
    }

    private void navigateBasedOnRole(String role) {
        // Universal Architecture: Tất cả người dùng đều vào MainActivity
        // Các tính năng mở rộng (Doctor/Caregiver) sẽ hiển thị dựa trên role trong SharedPreferences
        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
    }
}
