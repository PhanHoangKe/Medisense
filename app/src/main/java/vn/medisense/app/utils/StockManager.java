package vn.medisense.app.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import vn.medisense.app.MainActivity;
import vn.medisense.app.R;
import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.MedicationDao;

/**
 * StockManager - Quản lý kho thuốc và cảnh báo sắp hết
 * 
 * Chức năng:
 * - Tự động trừ kho khi người dùng đánh dấu "Đã uống"
 * - Kiểm tra ngưỡng cảnh báo
 * - Gửi thông báo khi thuốc sắp hết
 */
public class StockManager {
    private static final String CHANNEL_ID = "low_stock_channel";
    private static final String CHANNEL_NAME = "Cảnh báo thuốc sắp hết";
    private static final int NOTIFICATION_ID_BASE = 2000;

    /**
     * Xử lý khi người dùng đánh dấu đã uống thuốc.
     *
     * QUAN TRỌNG: Phương thức này chỉ nên được gọi từ ReminderStatusHelper
     * để đảm bảo không bị double-decrement. Caller phải đảm bảo:
     *   - isTaken=true  → chỉ gọi nếu trước đó chưa phải TAKEN
     *   - isTaken=false → chỉ gọi nếu trước đó là TAKEN
     */
    public static void handleMedicationTaken(Context context, int medicationId, boolean isTaken) {
        MedicationDao dao = AppDatabase.getInstance(context).medicationDao();
        
        AppExecutors.getInstance().diskIO().execute(() -> {
            Medication medication = dao.getMedicationByIdSync(medicationId);
            if (medication == null) return;
            
            if (isTaken) {
                // Trừ kho — đảm bảo không âm
                int dosageAmount = medication.dosagePerIntake > 0 ? medication.dosagePerIntake : 1;
                int newStock = Math.max(0, medication.currentStock - dosageAmount);
                medication.currentStock = newStock;
                dao.updateMedication(medication);
                checkLowStock(context, medication);
            } else {
                // Cộng lại kho — đảm bảo không vượt totalStock
                int dosageAmount = medication.dosagePerIntake > 0 ? medication.dosagePerIntake : 1;
                int newStock = Math.min(medication.totalStock, medication.currentStock + dosageAmount);
                medication.currentStock = newStock;
                dao.updateMedication(medication);
            }
        });
    }

    /**
     * Tính số ngày thuốc còn đủ dùng.
     * Công thức: daysLeft = currentStock / (frequency * dosagePerIntake)
     *
     * @return số ngày còn lại, hoặc -1 nếu không tính được (frequency=0)
     */
    public static int calculateDaysLeft(Medication medication) {
        if (medication == null) return 0;
        int dailyConsumption = medication.frequency * Math.max(1, medication.dosagePerIntake);
        if (dailyConsumption <= 0) return -1;
        return medication.currentStock / dailyConsumption;
    }

    /**
     * Trả về chuỗi mô tả tồn kho để hiển thị trong UI.
     * VD: "Còn khoảng 7 ngày" hoặc "Sắp hết thuốc — còn khoảng 2 ngày" hoặc "Đã hết thuốc"
     */
    public static String getStockSummary(Medication medication) {
        if (medication == null) return "";
        if (medication.currentStock <= 0) return "Đã hết thuốc";
        int days = calculateDaysLeft(medication);
        if (days < 0) return medication.currentStock + " " + "đơn vị còn lại";
        int threshold = medication.lowStockThreshold > 0 ? medication.lowStockThreshold : 5;
        if (medication.currentStock <= threshold) {
            return "Sắp hết thuốc — còn khoảng " + days + " ngày";
        }
        return "Còn khoảng " + days + " ngày";
    }

    /**
     * Kiểm tra ngưỡng cảnh báo và gửi thông báo nếu cần
     */
    private static void checkLowStock(Context context, Medication medication) {
        int threshold = medication.lowStockThreshold > 0 ? medication.lowStockThreshold : 5;
        
        if (medication.currentStock <= threshold && medication.currentStock > 0) {
            sendLowStockNotification(context, medication);
        } else if (medication.currentStock == 0) {
            sendOutOfStockNotification(context, medication);
        }
    }

    /**
     * Gửi thông báo thuốc sắp hết
     */
    private static void sendLowStockNotification(Context context, Medication medication) {
        createNotificationChannel(context);
        
        NotificationManager notificationManager = 
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager == null) {
            return;
        }
        
        // Intent để mở app khi nhấn vào notification
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String title = "⚠️ Thuốc sắp hết!";
        String message = String.format(
            "Thuốc %s của bạn sắp hết, chỉ còn %d viên. Hãy mua thêm ngay!",
            medication.name,
            medication.currentStock
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFFF57C00); // Orange color
        
        notificationManager.notify(NOTIFICATION_ID_BASE + medication.id, builder.build());
    }

    /**
     * Gửi thông báo thuốc đã hết
     */
    private static void sendOutOfStockNotification(Context context, Medication medication) {
        createNotificationChannel(context);
        
        NotificationManager notificationManager = 
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager == null) {
            return;
        }
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String title = "🚨 Thuốc đã hết!";
        String message = String.format(
            "Thuốc %s đã hết! Vui lòng mua thêm ngay để không bỏ lỡ liều uống.",
            medication.name
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFFD32F2F); // Red color
        
        notificationManager.notify(NOTIFICATION_ID_BASE + medication.id, builder.build());
    }

    /**
     * Tạo notification channel (Android 8.0+)
     */
    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Thông báo khi thuốc sắp hết hoặc đã hết");
            
            NotificationManager notificationManager = 
                context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Nhập thêm thuốc vào kho
     */
    public static void restockMedication(Context context, int medicationId, int additionalQuantity, RestockCallback callback) {
        MedicationDao dao = AppDatabase.getInstance(context).medicationDao();
        
        AppExecutors.getInstance().diskIO().execute(() -> {
            Medication medication = dao.getMedicationByIdSync(medicationId);
            
            if (medication == null) {
                if (callback != null) {
                    AppExecutors.getInstance().mainThread().execute(() -> 
                        callback.onError("Không tìm thấy thuốc"));
                }
                return;
            }
            
            medication.currentStock += additionalQuantity;
            medication.totalStock += additionalQuantity;
            dao.updateMedication(medication);
            
            if (callback != null) {
                AppExecutors.getInstance().mainThread().execute(() -> 
                    callback.onSuccess(medication));
            }
        });
    }

    /**
     * Callback cho việc nhập kho
     */
    public interface RestockCallback {
        void onSuccess(Medication medication);
        void onError(String errorMessage);
    }
}
