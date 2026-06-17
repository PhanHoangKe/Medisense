package vn.medisense.app.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import vn.medisense.app.receivers.AlarmReceiver;

public class AlarmHelper {
    public static void scheduleAlarm(Context context, int reminderId, long timeInMillis, String medicationName,
            String dosage, boolean isCritical) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null)
            return;

        // Check if we can schedule exact alarms (Android 12+)
        boolean canScheduleExact = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canScheduleExact = PermissionHelper.canScheduleExactAlarms(context);
            if (!canScheduleExact) {
                Log.w("AlarmHelper", "Chưa cấp quyền SCHEDULE_EXACT_ALARM - dùng fallback");
            }
        }

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("reminderId", reminderId);
        intent.putExtra("medicationName", medicationName);
        intent.putExtra("dosage", dosage);
        intent.putExtra("isCritical", isCritical);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
                Log.d("AlarmHelper", "Đã đặt báo thức chính xác cho: " + medicationName);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
                Log.d("AlarmHelper", "Đã đặt báo thức fallback cho: " + medicationName);
            }
        } catch (SecurityException e) {
            Log.e("AlarmHelper", "Lỗi đặt báo thức, dùng fallback", e);
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
        }
    }

    public static void cancelAlarm(Context context, int reminderId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null)
            return;

        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.cancel(pendingIntent);
    }
}
