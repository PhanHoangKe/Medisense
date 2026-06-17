package vn.medisense.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Lớp quản lý định vị bệnh nhân và đồng bộ vị trí lên Firestore.
 * Trích xuất từ MainActivity để tăng tính mô-đun và tuân thủ nguyên tắc Single Responsibility.
 */
public class PatientLocationManager {
    private static final String TAG = "PatientLocationManager";
    public static final int LOCATION_PERMISSION_REQUEST_CODE = 200;

    private final Activity activity;
    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;
    private boolean isTracking = false;

    public PatientLocationManager(@NonNull Activity activity) {
        this.activity = activity;
    }

    /**
     * Bắt đầu nhận cập nhật vị trí
     */
    public void startLocationUpdates() {
        SharedPreferences prefs = activity.getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE);
        String role = prefs.getString("userRole", null);
        if ("caregiver".equals(role)) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(activity, new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            if (locationClient == null) {
                locationClient = LocationServices.getFusedLocationProviderClient(activity);
            }

            locationClient.getLastLocation().addOnSuccessListener(activity, location -> {
                if (location != null) {
                    updatePatientLocationInFirestore(location.getLatitude(), location.getLongitude());
                }
            });

            LocationRequest locationRequest = LocationRequest.create()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setInterval(60000)
                .setFastestInterval(30000);

            if (locationCallback == null) {
                locationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(@NonNull LocationResult locationResult) {
                        for (android.location.Location location : locationResult.getLocations()) {
                            if (location != null) {
                                updatePatientLocationInFirestore(location.getLatitude(), location.getLongitude());
                            }
                        }
                    }
                };
            }

            if (!isTracking) {
                locationClient.requestLocationUpdates(locationRequest, locationCallback, android.os.Looper.getMainLooper());
                isTracking = true;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Lỗi quyền vị trí: " + e.getMessage());
        }
    }

    /**
     * Dừng nhận cập nhật vị trí
     */
    public void stopLocationUpdates() {
        if (locationClient != null && locationCallback != null && isTracking) {
            locationClient.removeLocationUpdates(locationCallback);
            isTracking = false;
        }
    }

    private void updatePatientLocationInFirestore(double latitude, double longitude) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);
        locationData.put("locationTimestamp", System.currentTimeMillis());

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.getUid())
            .update(locationData)
            .addOnFailureListener(e -> Log.e(TAG, "Lỗi cập nhật vị trí lên Firestore: " + e.getMessage()));
    }
}
