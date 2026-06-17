package vn.medisense.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

import vn.medisense.app.R;
import vn.medisense.app.utils.ProfileManager;

public class MedicalIdService extends Service {

    public static final String ACTION_TRIGGER_SOS = "vn.medisense.app.action.TRIGGER_SOS";
    private static final String CHANNEL_ID = "sos_medical_id_channel";
    private static final int NOTIFICATION_ID = 999;
    private ProfileManager profileManager;

    @Override
    public void onCreate() {
        super.onCreate();
        profileManager = new ProfileManager(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Kiểm tra xem intent có yêu cầu gửi tín hiệu SOS khẩn cấp hay không
        if (intent != null && ACTION_TRIGGER_SOS.equals(intent.getAction())) {
            triggerSosRequest();
            return START_STICKY;
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null || !profileManager.isSosEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        return START_STICKY;
    }

    private Notification createNotification() {
        String bloodType = profileManager.getBloodType();
        String allergies = profileManager.getAllergies();
        String medications = profileManager.getMedications();
        String iceContact = profileManager.getIceContact();

        if (bloodType.isEmpty()) bloodType = "N/A";
        if (allergies.isEmpty()) allergies = "Không có";
        
        if (medications.isEmpty()) {
            medications = "Không có";
        } else if (medications.length() > 100) {
            medications = medications.substring(0, 97) + "...";
        }
        
        if (iceContact.isEmpty()) iceContact = "N/A";

        RemoteViews customView = new RemoteViews(getPackageName(), R.layout.notification_sos_id);
        customView.setTextViewText(R.id.tvBloodType, bloodType);
        customView.setTextViewText(R.id.tvAllergies, allergies);
        customView.setTextViewText(R.id.tvMedications, medications);
        customView.setTextViewText(R.id.tvIceContact, iceContact);

        if (!iceContact.equals("N/A")) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + iceContact));
            
            PendingIntent callPendingIntent = PendingIntent.getActivity(
                    this, 
                    0, 
                    callIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            customView.setOnClickPendingIntent(R.id.btnCallIce, callPendingIntent);
        }

        // Tạo PendingIntent kích hoạt gửi cứu hộ SOS khẩn cấp lên Firestore
        Intent sosIntent = new Intent(this, MedicalIdService.class);
        sosIntent.setAction(ACTION_TRIGGER_SOS);
        PendingIntent sosPendingIntent = PendingIntent.getService(
                this,
                1,
                sosIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        customView.setOnClickPendingIntent(R.id.btnSendSos, sosPendingIntent);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(customView)
                .setCustomBigContentView(customView)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build();
    }

    /**
     * Thực hiện gửi yêu cầu cấp cứu khẩn cấp (SOS) lên Firestore
     */
    private void triggerSosRequest() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            return;
        }

        String patientId = auth.getCurrentUser().getUid();

        // Tạo document trên collection SOSRequests để kích hoạt Cloud Function thông báo bác sĩ
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> sosData = new HashMap<>();
        sosData.put("patientId", patientId);
        sosData.put("timestamp", FieldValue.serverTimestamp());
        sosData.put("status", "pending");

        db.collection("SOSRequests")
                .add(sosData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getApplicationContext(), "Tín hiệu SOS đã được gửi tới bác sĩ thành công!", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getApplicationContext(), "Không thể gửi tín hiệu SOS: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "SOS Medical ID",
                    NotificationManager.IMPORTANCE_HIGH
            );
            serviceChannel.setDescription("Màn hình khóa thông tin y tế khẩn cấp");
            serviceChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
