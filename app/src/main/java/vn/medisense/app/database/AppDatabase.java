package vn.medisense.app.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

@Database(entities = { Medication.class, Reminder.class, VitalSign.class,
        PrescriptionData.class, SideEffectLog.class, MeasurementTask.class, 
        ActivityLog.class, MoodLog.class }, version = 13, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;
    /** UID hiện tại đang mở database — dùng để phát hiện chuyển tài khoản */
    private static String currentDbUid = null;

    public abstract MedicationDao medicationDao();

    public abstract ReminderDao reminderDao();

    public abstract VitalSignDao vitalSignDao();
    
    public abstract SideEffectLogDao sideEffectLogDao();

    public abstract MeasurementTaskDao measurementTaskDao();
    
    public abstract ActivityLogDao activityLogDao(); // Thêm DAO mới
    
    public abstract MoodLogDao moodLogDao(); // Thêm DAO mới

    // Định nghĩa lộ trình migration an toàn để bảo toàn dữ liệu.
    // Nếu schema thực sự thay đổi từ 6 sang 7 trong tương lai, các lệnh SQL
    // để ALTER TABLE có thể được thêm vào bên trong migration này.
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Tạo bảng PrescriptionData mới
            database.execSQL("CREATE TABLE IF NOT EXISTS `prescription_data` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT, " +
                    "`diagnosis` TEXT, " +
                    "`doctorAdvice` TEXT, " +
                    "`dateCreated` INTEGER NOT NULL)");

            // Thay đổi bảng Medications để bao gồm các trường mới
            database.execSQL("ALTER TABLE `medications` ADD COLUMN `prescriptionId` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `medications` ADD COLUMN `mealContext` TEXT");
            database.execSQL("ALTER TABLE `medications` ADD COLUMN `offsetMinutes` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `medications` ADD COLUMN `specificShifts` TEXT");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Thêm isActive vào prescription_data
            database.execSQL("ALTER TABLE `prescription_data` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 1");
        }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Thêm các cột imagePath vào medications và reminders để lưu trữ đường dẫn ảnh
            database.execSQL("ALTER TABLE `medications` ADD COLUMN `imagePath` TEXT");
            database.execSQL("ALTER TABLE `reminders` ADD COLUMN `imagePath` TEXT");
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Tạo bảng side_effect_logs mới
            database.execSQL("CREATE TABLE IF NOT EXISTS `side_effect_logs` (" +
                    "`logId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`medicationId` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`moodRating` INTEGER NOT NULL, " +
                    "`symptoms` TEXT, " +
                    "`note` TEXT, " +
                    "FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_side_effect_logs_medicationId` ON `side_effect_logs` (`medicationId`)");
        }
    };

    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Thêm các cột trạng thái mới vào bảng reminders
            // DEFAULT 'PENDING' để dữ liệu cũ không bị null
            database.execSQL("ALTER TABLE `reminders` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'PENDING'");
            database.execSQL("ALTER TABLE `reminders` ADD COLUMN `skippedTime` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `reminders` ADD COLUMN `skipReason` TEXT");
            database.execSQL("ALTER TABLE `reminders` ADD COLUMN `snoozeUntil` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `reminders` ADD COLUMN `naggingCount` INTEGER NOT NULL DEFAULT 0");

            // Đồng bộ status cho dữ liệu cũ: nếu isTaken=1 thì status='TAKEN'
            database.execSQL("UPDATE `reminders` SET `status` = 'TAKEN' WHERE `isTaken` = 1");
        }
    };

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Tạo bảng activity_logs mới
            database.execSQL("CREATE TABLE IF NOT EXISTS `activity_logs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`activityName` TEXT, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`note` TEXT)");
            
            // Tạo bảng mood_logs mới
            database.execSQL("CREATE TABLE IF NOT EXISTS `mood_logs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`moodName` TEXT, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`note` TEXT)");
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Tạo bảng measurement_tasks mới
            database.execSQL("CREATE TABLE IF NOT EXISTS `measurement_tasks` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`type` TEXT, " +
                    "`title` TEXT, " +
                    "`timeOfDay` INTEGER NOT NULL, " +
                    "`isActive` INTEGER NOT NULL DEFAULT 1)");
        }
    };

    /**
     * Lấy tên database theo UID người dùng hiện tại.
     * - Đã đăng nhập: "medisense_<uid_8_ký_tự_đầu>.db" → mỗi tài khoản có file DB riêng
     * - Chưa đăng nhập (Guest): "medisense_db" → database chung cho guest
     *
     * Khi đăng xuất rồi đăng nhập nick khác, database cũ vẫn còn nguyên trên device,
     * nhưng nick mới sẽ mở file DB riêng → không bao giờ thấy dữ liệu lẫn nhau.
     */
    private static String getDatabaseName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getUid() != null) {
            // Lấy 8 ký tự đầu UID để tên file ngắn gọn mà vẫn đủ unique
            String shortUid = user.getUid().substring(0, Math.min(8, user.getUid().length()));
            return "medisense_" + shortUid;
        }
        return "medisense_db"; // Guest mode
    }

    /**
     * Lấy instance database phù hợp với tài khoản đang đăng nhập.
     * Nếu tài khoản thay đổi (so với lần gọi trước), tự đóng DB cũ và mở DB mới.
     */
    public static synchronized AppDatabase getInstance(Context context) {
        String uid = getCurrentUid();

        // Nếu tài khoản khác với DB đang mở → đóng DB cũ
        if (instance != null && !safeEquals(uid, currentDbUid)) {
            try {
                instance.close();
            } catch (Exception ignored) {}
            instance = null;
        }

        if (instance == null) {
            currentDbUid = uid;
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    AppDatabase.class, getDatabaseName())
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                    // ĐÃ LOẠI BỎ: .fallbackToDestructiveMigration() để bảo vệ dữ liệu người dùng
                    .build();
        }
        return instance;
    }

    /**
     * Đóng instance hiện tại — gọi khi đăng xuất.
     * Lần gọi getInstance() tiếp theo sẽ tạo DB mới theo UID mới.
     */
    public static synchronized void closeInstance() {
        if (instance != null) {
            try {
                instance.close();
            } catch (Exception ignored) {}
            instance = null;
            currentDbUid = null;
        }
    }

    private static String getCurrentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
