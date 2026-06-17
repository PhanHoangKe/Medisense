package vn.medisense.app.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import vn.medisense.app.receivers.MeasurementAlarmReceiver;

import java.util.Calendar;

public class MeasurementAlarmHelper {
    public static final String ACTION_MEASUREMENT_ALARM = "vn.medisense.app.MEASUREMENT_ALARM";

    public static void scheduleAlarm(Context context, int taskId, String title, String type, long timeOfDayMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("MeasurementAlarmHelper", "Chưa cấp quyền SCHEDULE_EXACT_ALARM");
            }
        }

        Intent intent = new Intent(context, MeasurementAlarmReceiver.class);
        intent.setAction(ACTION_MEASUREMENT_ALARM);
        intent.putExtra("taskId", taskId);
        intent.putExtra("title", title);
        intent.putExtra("type", type);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId + 100000, // Bù đắp để tránh xung đột với nhắc nhở thuốc
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Tính toán lần xuất hiện tiếp theo
        Calendar now = Calendar.getInstance();
        Calendar alarmTime = Calendar.getInstance();
        
        // Cần lấy giờ/phút từ timeOfDayMillis
        int hours = (int) (timeOfDayMillis / (60 * 60 * 1000));
        int minutes = (int) ((timeOfDayMillis % (60 * 60 * 1000)) / (60 * 1000));
        
        alarmTime.set(Calendar.HOUR_OF_DAY, hours);
        alarmTime.set(Calendar.MINUTE, minutes);
        alarmTime.set(Calendar.SECOND, 0);
        alarmTime.set(Calendar.MILLISECOND, 0);

        if (alarmTime.before(now)) {
            // Nếu đã qua giờ hôm nay, thì đặt cho ngày mai
            alarmTime.add(Calendar.DAY_OF_YEAR, 1);
        }

        try {
            // Đặt báo thức lặp lại mỗi ngày
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
            );
            Log.d("MeasurementAlarmHelper", "Đã đặt lịch đo: " + title + " lúc " + alarmTime.getTime());
        } catch (SecurityException e) {
            e.printStackTrace();
            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
            );
        }
    }

    public static void cancelAlarm(Context context, int taskId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, MeasurementAlarmReceiver.class);
        intent.setAction(ACTION_MEASUREMENT_ALARM);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId + 100000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.cancel(pendingIntent);
        Log.d("MeasurementAlarmHelper", "Đã hủy lịch đo ID: " + taskId);
    }
}
