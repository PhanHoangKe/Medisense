package vn.medisense.app;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import vn.medisense.app.utils.BiometricAuthManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * BaseActivity - Activity cơ sở với xác thực sinh trắc học
 * 
 * Tất cả các Activity khác nên extend từ BaseActivity này
 * để tự động có tính năng khóa bảo mật
 */
public abstract class BaseActivity extends AppCompatActivity {
    
    private BiometricAuthManager biometricAuthManager;
    private boolean isAuthenticating = false;
    
    // Đã chuyển thành static để trạng thái được duy trì khi chuyển đổi giữa các màn hình (Activity)
    private static boolean isAuthenticated = false;
    private static long lastBackgroundTime = 0;
    private static final long GRACE_PERIOD_MS = 60_000; // 60 giây

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        biometricAuthManager = new BiometricAuthManager(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Nếu đã quá thời gian grace period kể từ khi app vào background, yêu cầu đăng nhập lại
        if (lastBackgroundTime != 0 && (System.currentTimeMillis() - lastBackgroundTime) > GRACE_PERIOD_MS) {
            isAuthenticated = false;
            biometricAuthManager.clearAuthTime();
        }
        
        // Kiểm tra xem có cần xác thực không
        if (biometricAuthManager.isBiometricEnabled() && !isAuthenticated && !isAuthenticating) {
            authenticateUser();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Cập nhật thời điểm bắt đầu vào background (hoặc chuyển màn hình)
        lastBackgroundTime = System.currentTimeMillis();
    }

    /**
     * Xác thực người dùng bằng sinh trắc học
     */
    private void authenticateUser() {
        isAuthenticating = true;
        
        biometricAuthManager.authenticate(this, new BiometricAuthManager.BiometricAuthCallback() {
            @Override
            public void onAuthenticationSucceeded() {
                isAuthenticating = false;
                isAuthenticated = true;
                onAuthenticationSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                // Không làm gì, cho phép thử lại
                Toast.makeText(BaseActivity.this, 
                        "Xác thực thất bại. Vui lòng thử lại.", 
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationError(String errorMessage) {
                isAuthenticating = false;
                showErrorDialog(errorMessage);
            }

            @Override
            public void onAuthenticationCancelled() {
                isAuthenticating = false;
                // Người dùng hủy - thoát app
                showCancelDialog();
            }

            @Override
            public void onAuthenticationLockout(String message) {
                isAuthenticating = false;
                showLockoutDialog(message);
            }
        });
    }

    /**
     * Hiển thị dialog lỗi
     */
    private void showErrorDialog(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Lỗi xác thực")
                .setMessage(message)
                .setPositiveButton("Thử lại", (dialog, which) -> authenticateUser())
                .setNegativeButton("Thoát", (dialog, which) -> finishAffinity())
                .setCancelable(false)
                .show();
    }

    /**
     * Hiển thị dialog khi người dùng hủy
     */
    private void showCancelDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Xác thực bị hủy")
                .setMessage("Bạn cần xác thực để sử dụng ứng dụng")
                .setPositiveButton("Thử lại", (dialog, which) -> authenticateUser())
                .setNegativeButton("Thoát", (dialog, which) -> finishAffinity())
                .setCancelable(false)
                .show();
    }

    /**
     * Hiển thị dialog khi bị khóa do thử quá nhiều lần
     */
    private void showLockoutDialog(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Tạm thời bị khóa")
                .setMessage(message + "\n\nVui lòng thử lại sau hoặc sử dụng mật khẩu thiết bị.")
                .setPositiveButton("Thử lại", (dialog, which) -> authenticateUser())
                .setNegativeButton("Thoát", (dialog, which) -> finishAffinity())
                .setCancelable(false)
                .show();
    }

    /**
     * Callback khi xác thực thành công
     * Override trong subclass nếu cần xử lý thêm
     */
    protected void onAuthenticationSuccess() {
        // Subclass có thể override
    }

    /**
     * Lấy BiometricAuthManager
     */
    protected BiometricAuthManager getBiometricAuthManager() {
        return biometricAuthManager;
    }
}
