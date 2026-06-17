package vn.medisense.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

/**
 * BiometricAuthManager - Quản lý xác thực sinh trắc học
 * 
 * Tính năng:
 * - Kiểm tra khả năng hỗ trợ sinh trắc học
 * - Hiển thị BiometricPrompt
 * - Lưu trạng thái bật/tắt khóa
 * - Xử lý xác thực thành công/thất bại
 * - Bảo vệ dữ liệu người dùng
 */
public class BiometricAuthManager {
    
    private static final String TAG = "BiometricAuthManager";
    private static final String PREFS_NAME = "BiometricPrefs";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_LAST_AUTH_TIME = "last_auth_time";
    private static final long AUTH_TIMEOUT = 30000; // 30 giây
    
    private final Context context;
    private final SharedPreferences prefs;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    public BiometricAuthManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Kiểm tra xem thiết bị có hỗ trợ sinh trắc học không
     */
    public BiometricStatus checkBiometricSupport() {
        BiometricManager biometricManager = BiometricManager.from(context);
        
        int canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG | 
                BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        
        switch (canAuthenticate) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                Log.d(TAG, "Biometric authentication available");
                return BiometricStatus.SUCCESS;
                
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                Log.e(TAG, "No biometric hardware");
                return BiometricStatus.NO_HARDWARE;
                
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                Log.e(TAG, "Biometric hardware unavailable");
                return BiometricStatus.HARDWARE_UNAVAILABLE;
                
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                Log.e(TAG, "No biometric enrolled");
                return BiometricStatus.NONE_ENROLLED;
                
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
                Log.e(TAG, "Security update required");
                return BiometricStatus.SECURITY_UPDATE_REQUIRED;
                
            case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:
                Log.e(TAG, "Biometric unsupported");
                return BiometricStatus.UNSUPPORTED;
                
            case BiometricManager.BIOMETRIC_STATUS_UNKNOWN:
                Log.e(TAG, "Biometric status unknown");
                return BiometricStatus.UNKNOWN;
                
            default:
                return BiometricStatus.UNKNOWN;
        }
    }

    /**
     * Hiển thị BiometricPrompt để xác thực
     */
    public void authenticate(FragmentActivity activity, BiometricAuthCallback callback) {
        // Kiểm tra xem có bật khóa không
        if (!isBiometricEnabled()) {
            callback.onAuthenticationSucceeded();
            return;
        }
        
        // Kiểm tra xem vừa xác thực gần đây không (trong 30s)
        if (isRecentlyAuthenticated()) {
            callback.onAuthenticationSucceeded();
            return;
        }
        
        // Kiểm tra hỗ trợ
        BiometricStatus status = checkBiometricSupport();
        if (status != BiometricStatus.SUCCESS) {
            callback.onAuthenticationError(getErrorMessage(status));
            return;
        }
        
        // Tạo executor
        Executor executor = ContextCompat.getMainExecutor(context);
        
        // Tạo BiometricPrompt
        biometricPrompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Log.e(TAG, "Authentication error: " + errString);
                        
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                            // Người dùng hủy - thoát app
                            callback.onAuthenticationCancelled();
                        } else if (errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                                   errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                            // Bị khóa do thử quá nhiều lần
                            callback.onAuthenticationLockout(errString.toString());
                        } else {
                            callback.onAuthenticationError(errString.toString());
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Log.d(TAG, "Authentication succeeded");
                        
                        // Lưu thời gian xác thực
                        saveLastAuthTime();
                        
                        callback.onAuthenticationSucceeded();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Log.w(TAG, "Authentication failed");
                        callback.onAuthenticationFailed();
                    }
                });
        
        // Tạo PromptInfo
        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Xác thực để vào MediSense")
                .setSubtitle("Sử dụng vân tay hoặc khuôn mặt của bạn")
                .setDescription("Bảo vệ dữ liệu y tế của bạn")
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG | 
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        
        // Hiển thị prompt
        biometricPrompt.authenticate(promptInfo);
    }

    /**
     * Bật/tắt khóa sinh trắc học
     */
    public void setBiometricEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply();
        Log.d(TAG, "Biometric lock " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Kiểm tra xem khóa sinh trắc học có được bật không
     */
    public boolean isBiometricEnabled() {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
    }

    /**
     * Lưu thời gian xác thực cuối cùng
     */
    private void saveLastAuthTime() {
        prefs.edit().putLong(KEY_LAST_AUTH_TIME, System.currentTimeMillis()).apply();
    }

    /**
     * Kiểm tra xem có vừa xác thực gần đây không
     */
    private boolean isRecentlyAuthenticated() {
        long lastAuthTime = prefs.getLong(KEY_LAST_AUTH_TIME, 0);
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastAuthTime) < AUTH_TIMEOUT;
    }

    /**
     * Xóa thời gian xác thực (khi app vào background)
     */
    public void clearAuthTime() {
        prefs.edit().remove(KEY_LAST_AUTH_TIME).apply();
    }

    /**
     * Lấy thông báo lỗi từ BiometricStatus
     */
    private String getErrorMessage(BiometricStatus status) {
        switch (status) {
            case NO_HARDWARE:
                return "Thiết bị không hỗ trợ sinh trắc học";
            case HARDWARE_UNAVAILABLE:
                return "Phần cứng sinh trắc học không khả dụng";
            case NONE_ENROLLED:
                return "Chưa đăng ký vân tay hoặc khuôn mặt. Vui lòng đăng ký trong Cài đặt hệ thống.";
            case SECURITY_UPDATE_REQUIRED:
                return "Cần cập nhật bảo mật hệ thống";
            case UNSUPPORTED:
                return "Sinh trắc học không được hỗ trợ";
            default:
                return "Lỗi không xác định";
        }
    }

    /**
     * Enum cho trạng thái sinh trắc học
     */
    public enum BiometricStatus {
        SUCCESS,
        NO_HARDWARE,
        HARDWARE_UNAVAILABLE,
        NONE_ENROLLED,
        SECURITY_UPDATE_REQUIRED,
        UNSUPPORTED,
        UNKNOWN
    }

    /**
     * Callback interface
     */
    public interface BiometricAuthCallback {
        void onAuthenticationSucceeded();
        void onAuthenticationFailed();
        void onAuthenticationError(String errorMessage);
        void onAuthenticationCancelled();
        void onAuthenticationLockout(String message);
    }
}
