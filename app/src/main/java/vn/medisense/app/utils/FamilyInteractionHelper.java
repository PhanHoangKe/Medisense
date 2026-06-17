package vn.medisense.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import vn.medisense.app.database.ReminderWithMedication;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class FamilyInteractionHelper {

    /**
     * Đồng bộ sự kiện uống/bỏ qua thuốc lên Firebase Realtime DB cho caregiver.
     * Gửi đầy đủ: medicationName, timestamp, status, isLate, skipReason, stockRemaining.
     */
    public static void logMedicationTakeEvent(Context context, ReminderWithMedication item, boolean isChecked) {
        logMedicationTakeEvent(context, item, isChecked, null);
    }

    /**
     * Đồng bộ sự kiện uống/bỏ qua thuốc lên Firebase Realtime DB cho caregiver.
     */
    public static void logMedicationTakeEvent(Context context, ReminderWithMedication item,
                                               boolean isChecked, String skipReason) {
        SharedPreferences prefs = context.getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE);
        String familyCode = prefs.getString("familyCode", null);
        String userRole   = prefs.getString("userRole", null);

        android.util.Log.d("FamilyInteraction", "logMedicationTakeEvent called: familyCode=" + familyCode + ", userRole=" + userRole);

        if (familyCode == null || !"patient".equals(userRole)) {
            android.util.Log.w("FamilyInteraction", "Returned early: familyCode is null or userRole is not patient");
            return;
        }
        if (item == null || item.reminder == null || item.medication == null) return;

        long scheduledTime = item.reminder.reminderTime;
        long currentTime   = System.currentTimeMillis();
        // Coi là trễ nếu uống sau giờ hẹn + 30 phút
        boolean isLate = isChecked && scheduledTime > 0
                && currentTime > (scheduledTime + 30L * 60L * 1000L);

        // Xác định status để gửi cho caregiver
        String statusToLog;
        if (isChecked) {
            statusToLog = vn.medisense.app.database.Reminder.STATUS_TAKEN;
        } else if (skipReason != null && !skipReason.isEmpty()) {
            statusToLog = vn.medisense.app.database.Reminder.STATUS_SKIPPED;
        } else {
            statusToLog = vn.medisense.app.database.Reminder.STATUS_MISSED;
        }

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference()
                .child("families")
                .child(familyCode)
                .child("logs")
                .child(String.valueOf(item.reminder.id));

        Map<String, Object> logData = new HashMap<>();
        logData.put("medicationName", item.medication.name);
        logData.put("reminderId",     item.reminder.id);
        logData.put("scheduledTime",  scheduledTime);
        logData.put("actionTime",     currentTime);
        logData.put("status",         statusToLog);
        logData.put("isTaken",        isChecked);
        logData.put("isLate",         isLate);
        logData.put("stockRemaining", item.medication.currentStock);

        if (skipReason != null && !skipReason.isEmpty()) {
            logData.put("skipReason", skipReason);
        }

        ref.setValue(logData)
            .addOnSuccessListener(aVoid -> android.util.Log.d("FamilyInteraction", "Sync medication log successfully to Realtime DB path: families/" + familyCode + "/logs/" + item.reminder.id))
            .addOnFailureListener(e -> {
                android.util.Log.e("FamilyInteraction", "Failed to sync medication log to Realtime DB", e);
                Toast.makeText(context, "Lỗi đồng bộ lịch uống thuốc: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });

        // Gửi tín hiệu cổ vũ nếu đã uống
        if (isChecked) {
            DatabaseReference cheerRef = FirebaseDatabase.getInstance().getReference()
                    .child("families").child(familyCode).child("cheers").child("latest");
            cheerRef.child("patientSent").setValue(true);
            cheerRef.child("caregiverResponded").setValue(false);
            cheerRef.child("timestamp").setValue(currentTime)
                .addOnFailureListener(e -> android.util.Log.e("FamilyInteraction", "Failed to send cheer event to Realtime DB", e));
        }
    }

    /**
     * Lắng nghe tín hiệu động viên (Family Cheers) từ Firebase
     */
    public static void listenForFamilyCheers(Activity activity, ImageView imgFamilyCheer) {
        SharedPreferences prefs = activity.getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE);
        String familyCode = prefs.getString("familyCode", null);
        String userRole = prefs.getString("userRole", null);

        if (familyCode == null || familyCode.isEmpty()) return;

        DatabaseReference cheerRef = FirebaseDatabase.getInstance().getReference()
                .child("families").child(familyCode).child("cheers").child("latest");

        cheerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                Boolean patientSent = snapshot.child("patientSent").getValue(Boolean.class);
                Boolean caregiverResponded = snapshot.child("caregiverResponded").getValue(Boolean.class);

                if (patientSent == null || caregiverResponded == null) return;

                if ("patient".equals(userRole)) {
                    if (patientSent && caregiverResponded) {
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, "Người thân vừa gửi lời khen cho bạn! ❤️", Toast.LENGTH_LONG).show();
                            
                            cheerRef.child("patientSent").setValue(false);
                            cheerRef.child("caregiverResponded").setValue(false);
                            
                            if (imgFamilyCheer != null) {
                                imgFamilyCheer.setVisibility(android.view.View.VISIBLE);
                                imgFamilyCheer.animate().scaleX(1.5f).scaleY(1.5f).setDuration(500).withEndAction(() -> {
                                    imgFamilyCheer.animate().scaleX(1.0f).scaleY(1.0f).setDuration(500).withEndAction(() -> {
                                        imgFamilyCheer.setVisibility(android.view.View.GONE);
                                    });
                                }).start();
                            }
                        });
                    }
                } else if ("caregiver".equals(userRole)) {
                    if (patientSent && !caregiverResponded) {
                        activity.runOnUiThread(() -> {
                            if (imgFamilyCheer != null) {
                                imgFamilyCheer.setVisibility(android.view.View.VISIBLE);
                                imgFamilyCheer.setOnClickListener(v -> {
                                    cheerRef.child("caregiverResponded").setValue(true);
                                    imgFamilyCheer.setVisibility(android.view.View.GONE);
                                    Toast.makeText(activity, "Đã gửi lời khen!", Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    } else {
                        activity.runOnUiThread(() -> {
                            if (imgFamilyCheer != null) imgFamilyCheer.setVisibility(android.view.View.GONE);
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore
            }
        });
    }
}
