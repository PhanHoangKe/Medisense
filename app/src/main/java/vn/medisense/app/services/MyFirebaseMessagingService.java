package vn.medisense.app.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Mã đăng ký FCM mới: " + token);
        saveTokenToFirestore(token);
    }

    private void saveTokenToFirestore(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "Chưa xác thực người dùng. Bỏ qua lưu mã đăng ký FCM.");
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> data = new HashMap<>();
        data.put("fcmToken", token);
        db.collection("users").document(user.getUid()).set(data, SetOptions.merge())
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Lưu mã đăng ký FCM thành công"))
            .addOnFailureListener(e -> Log.e(TAG, "Lỗi khi lưu mã đăng ký FCM", e));
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Nhận tin nhắn từ: " + remoteMessage.getFrom());
        
        // Nếu ứng dụng đang ở foreground, chúng ta có thể xử lý thông báo thủ công ở đây nếu muốn.
        // Hiện tại, để Firebase SDK tự động xử lý các payload Data/Notification cơ bản.
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Nội dung thông báo: " + remoteMessage.getNotification().getBody());
            // Hiển thị thông báo tùy chỉnh sử dụng NotificationCompat nếu cần thiết
        }
    }
}
