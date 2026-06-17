package vn.medisense.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.medisense.app.databinding.FragmentCaregiverDashboardBinding;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.widget.TextView;
import java.util.Calendar;
import vn.medisense.app.R;
import vn.medisense.app.database.VitalSign;

public class CaregiverDashboardActivity extends AppCompatActivity {
    private FragmentCaregiverDashboardBinding binding;
    private DatabaseReference familyRef;
    private ValueEventListener syncListener;
    private CaregiverLogAdapter adapter;

    // Vitals Integration
    private PatientVitalAdapter vitalsAdapter;
    private ListenerRegistration vitalsListener;
    private String patientUid;
    private com.google.android.gms.maps.GoogleMap googleMap;
    private com.google.firebase.firestore.ListenerRegistration locationListener;
    private com.google.firebase.firestore.ListenerRegistration sosListener;
    private int currentSelectedTab = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FragmentCaregiverDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        vn.medisense.app.utils.RoleGuardHelper.checkUserRole(this, "caregiver",
                new vn.medisense.app.utils.RoleGuardHelper.RoleCheckCallback() {
                    @Override
                    public void onRoleValid() {
                        initializeDashboard(savedInstanceState);
                    }

                    @Override
                    public void onRoleInvalid() {
                        finish();
                    }
                });
    }

    private void initializeDashboard(@Nullable Bundle savedInstanceState) {
        // 1. Setup Medication Logs Adapter
        adapter = new CaregiverLogAdapter();
        binding.recyclerCaregiverLogs.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerCaregiverLogs.setAdapter(adapter);

        // 2. Setup Vitals Adapter
        vitalsAdapter = new PatientVitalAdapter(new ArrayList<>());
        binding.recyclerPatientVitals.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPatientVitals.setAdapter(vitalsAdapter);

        // 3. Setup Button Event Listeners
        binding.btnSettings.setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, SettingsActivity.class));
        });

        binding.btnConnectPatient.setOnClickListener(
                v -> startActivity(new android.content.Intent(this, CaregiverPairingActivity.class)));

        binding.btnDisconnect.setOnClickListener(v -> showDisconnectDialog());

        // 4. Setup MapView
        if (binding.mapView != null) {
            binding.mapView.onCreate(savedInstanceState);
            binding.mapView.getMapAsync(map -> {
                googleMap = map;
                googleMap.getUiSettings().setZoomControlsEnabled(true);
                googleMap.getUiSettings().setMyLocationButtonEnabled(false);
            });
        }

        // 5. Setup TabLayout Switching
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentSelectedTab = tab.getPosition();
                updateTabViewsVisibility();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        updateTabViewsVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Kiểm tra lại mỗi khi màn hình hiển thị lại để cập nhật trạng thái kết nối
        listenToFirebaseLogs();
        if (binding != null && binding.mapView != null) {
            binding.mapView.onResume();
        }
    }

    private void listenToFirebaseLogs() {
        SharedPreferences prefs = getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE);
        String monitoringCode = prefs.getString("monitoringCode", null);

        if (monitoringCode == null || monitoringCode.isEmpty()) {
            binding.progressBar.setVisibility(View.GONE);
            patientUid = null;
            stopListeningToVitals();
            updateTabViewsVisibility();
            return;
        }

        binding.layoutEmptyState.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.cardMonitoredInfo.setVisibility(View.VISIBLE);
        binding.tvMonitoredInfo.setText("Đang giám sát: " + monitoringCode);
        loadMonitoredPatientInfo(monitoringCode);

        familyRef = FirebaseDatabase.getInstance().getReference().child("families").child(monitoringCode).child("logs");
        syncListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed())
                    return;

                binding.progressBar.setVisibility(View.GONE);
                List<LogData> currentLogs = new ArrayList<>();
                int takenToday = 0;
                int missedToday = 0;
                int skippedToday = 0;
                int snoozedToday = 0;
                int pendingToday = 0;

                long startOfToday = getStartOfTodayMillis();

                for (DataSnapshot logSnapshot : snapshot.getChildren()) {
                    String status = logSnapshot.child("status").getValue(String.class);
                    Long actionTime = logSnapshot.child("actionTime").getValue(Long.class);
                    Long scheduledTime = logSnapshot.child("scheduledTime").getValue(Long.class);
                    String medName = logSnapshot.child("medicationName").getValue(String.class);
                    Boolean isLateFlag = logSnapshot.child("isLate").getValue(Boolean.class);
                    String skipReason = logSnapshot.child("skipReason").getValue(String.class);
                    Long stockRem = logSnapshot.child("stockRemaining").getValue(Long.class);

                    if (status == null || status.isEmpty()) {
                        Boolean isTaken = logSnapshot.child("isTaken").getValue(Boolean.class);
                        if (isTaken != null && isTaken) {
                            status = vn.medisense.app.database.Reminder.STATUS_TAKEN;
                        } else {
                            long sched = scheduledTime != null ? scheduledTime : 0L;
                            long grace = vn.medisense.app.database.Reminder.GRACE_PERIOD_MS;
                            if (sched > 0 && System.currentTimeMillis() > sched + grace) {
                                status = vn.medisense.app.database.Reminder.STATUS_MISSED;
                            } else {
                                status = vn.medisense.app.database.Reminder.STATUS_PENDING;
                            }
                        }
                        if (actionTime == null) {
                            actionTime = logSnapshot.child("timestamp").getValue(Long.class);
                        }
                    }

                    if (medName == null)
                        continue;

                    boolean isLate = isLateFlag != null && isLateFlag;
                    long aTime = actionTime != null ? actionTime : 0L;
                    long sTime = scheduledTime != null ? scheduledTime : 0L;
                    int stock = stockRem != null ? stockRem.intValue() : -1;

                    LogData logData = new LogData(medName, aTime, status,
                            isLate, skipReason, stock, sTime);
                    currentLogs.add(logData);

                    long refTime = aTime > 0 ? aTime : sTime;
                    if (refTime >= startOfToday) {
                        switch (status) {
                            case vn.medisense.app.database.Reminder.STATUS_TAKEN:
                                takenToday++;
                                break;
                            case vn.medisense.app.database.Reminder.STATUS_MISSED:
                                missedToday++;
                                break;
                            case vn.medisense.app.database.Reminder.STATUS_SKIPPED:
                                skippedToday++;
                                break;
                            case vn.medisense.app.database.Reminder.STATUS_SNOOZED:
                                snoozedToday++;
                                break;
                            default:
                                pendingToday++;
                                break;
                        }
                    }
                }

                Collections.sort(currentLogs, (o1, o2) -> Long.compare(o2.time, o1.time));

                if (adapter != null) {
                    adapter.setLogs(currentLogs);
                }

                // Cập nhật thống kê hôm nay
                TextView tvMotto = findViewById(vn.medisense.app.R.id.tvMotto);
                if (tvMotto != null) {
                    StringBuilder sb = new StringBuilder("Hôm nay: ");
                    sb.append("✓ ").append(takenToday).append(" đã uống");
                    if (missedToday > 0)
                        sb.append(" · ✗ ").append(missedToday).append(" bỏ lỡ");
                    if (skippedToday > 0)
                        sb.append(" · ⊘ ").append(skippedToday).append(" bỏ qua");
                    if (snoozedToday > 0)
                        sb.append(" · ⏰ ").append(snoozedToday).append(" nhắc lại");
                    if (pendingToday > 0)
                        sb.append(" · ○ ").append(pendingToday).append(" chờ");
                    tvMotto.setText(sb.toString());
                }

                updateTabViewsVisibility();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFinishing() || isDestroyed())
                    return;

                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(CaregiverDashboardActivity.this, "Lỗi Firebase: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        };

        familyRef.addValueEventListener(syncListener);
    }

    private void startListeningToVitals(String patientId) {
        stopListeningToVitals();

        vitalsListener = FirebaseFirestore.getInstance()
                .collection("VitalSigns")
                .whereEqualTo("patientId", patientId)
                .addSnapshotListener((value, error) -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (error != null) {
                        Toast.makeText(this, "Lỗi tải chỉ số sinh tồn: " + error.getMessage(), Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }

                    if (value != null) {
                        List<VitalSign> allVitals = new ArrayList<>();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                            String type = doc.getString("type");
                            Object val1Raw = doc.get("value");
                            Object val2Raw = doc.get("value2");
                            Double val1Obj = val1Raw instanceof Number ? ((Number) val1Raw).doubleValue() : null;
                            Double val2Obj = val2Raw instanceof Number ? ((Number) val2Raw).doubleValue() : null;
                            Long tsObj = doc.getLong("timestamp");
                            String note = doc.getString("note");

                            float val1 = val1Obj != null ? val1Obj.floatValue() : 0f;
                            float val2 = val2Obj != null ? val2Obj.floatValue() : 0f;
                            long timestamp = tsObj != null ? tsObj : 0L;

                            if (type != null && timestamp > 0) {
                                allVitals.add(new VitalSign(type, val1, val2, timestamp, note));
                            }
                        }

                        // Group vitals by type
                        Map<String, List<VitalSign>> grouped = new HashMap<>();
                        for (VitalSign v : allVitals) {
                            if (!grouped.containsKey(v.type)) {
                                grouped.put(v.type, new ArrayList<>());
                            }
                            grouped.get(v.type).add(v);
                        }

                        List<PatientVitalAdapter.PatientVitalData> vitalsDataList = new ArrayList<>();
                        String[] supportedTypes = { "blood_pressure", "heart_rate", "blood_sugar" };
                        String[] displayNames = { "Huyết áp", "Nhịp tim", "Đường huyết" };

                        for (int i = 0; i < supportedTypes.length; i++) {
                            String type = supportedTypes[i];
                            List<VitalSign> list = grouped.get(type);
                            if (list == null) {
                                list = new ArrayList<>();
                            }
                            vitalsDataList.add(new PatientVitalAdapter.PatientVitalData(type, displayNames[i], list));
                        }

                        if (vitalsAdapter != null) {
                            vitalsAdapter.setData(vitalsDataList);
                        }
                        updateTabViewsVisibility();
                    }
                });
    }

    private void stopListeningToVitals() {
        if (vitalsListener != null) {
            vitalsListener.remove();
            vitalsListener = null;
        }
    }

    private void startListeningToLocation(String patientId) {
        stopListeningToLocation();

        locationListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(patientId)
                .addSnapshotListener((snapshot, error) -> {
                    if (isFinishing() || isDestroyed())
                        return;
                    if (error != null)
                        return;

                    if (snapshot != null && snapshot.exists()) {
                        Object latRaw = snapshot.get("latitude");
                        Object lngRaw = snapshot.get("longitude");
                        Double latVal = latRaw instanceof Number ? ((Number) latRaw).doubleValue() : null;
                        Double lngVal = lngRaw instanceof Number ? ((Number) lngRaw).doubleValue() : null;
                        Long tsObj = snapshot.getLong("locationTimestamp");

                        if (latVal != null && lngVal != null) {
                            double latitude = latVal;
                            double longitude = lngVal;
                            long timestamp = tsObj != null ? tsObj : 0L;
                            updatePatientMarkerOnMap(latitude, longitude, timestamp);
                        }
                    }
                });
    }

    private void stopListeningToLocation() {
        if (locationListener != null) {
            locationListener.remove();
            locationListener = null;
        }
    }

    private void startListeningToSos(String patientId, String phoneNumber) {
        stopListeningToSos();

        sosListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("SOSRequests")
                .whereEqualTo("patientId", patientId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (isFinishing() || isDestroyed())
                        return;
                    if (error != null)
                        return;

                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        binding.cardSosAlert.setVisibility(View.VISIBLE);

                        binding.btnSosViewMap.setOnClickListener(v -> {
                            com.google.android.material.tabs.TabLayout.Tab tab = binding.tabLayout.getTabAt(2);
                            if (tab != null) {
                                tab.select();
                            }
                        });

                        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                            binding.btnSosCall.setVisibility(View.VISIBLE);
                            binding.btnSosCall.setOnClickListener(v -> {
                                android.content.Intent callIntent = new android.content.Intent(
                                        android.content.Intent.ACTION_DIAL);
                                callIntent.setData(android.net.Uri.parse("tel:" + phoneNumber));
                                startActivity(callIntent);
                            });
                        } else {
                            binding.btnSosCall.setVisibility(View.GONE);
                        }

                        com.google.android.material.tabs.TabLayout.Tab tab = binding.tabLayout.getTabAt(2);
                        if (tab != null) {
                            tab.setText("🚨 Vị trí SOS");
                        }
                    } else {
                        binding.cardSosAlert.setVisibility(View.GONE);
                        com.google.android.material.tabs.TabLayout.Tab tab = binding.tabLayout.getTabAt(2);
                        if (tab != null) {
                            tab.setText("Vị trí");
                        }
                    }
                });
    }

    private void stopListeningToSos() {
        if (sosListener != null) {
            sosListener.remove();
            sosListener = null;
        }
    }

    private void updatePatientMarkerOnMap(double latitude, double longitude, long timestamp) {
        if (googleMap == null)
            return;

        com.google.android.gms.maps.model.LatLng patientLatLng = new com.google.android.gms.maps.model.LatLng(latitude,
                longitude);
        googleMap.clear();
        googleMap.addMarker(new com.google.android.gms.maps.model.MarkerOptions()
                .position(patientLatLng)
                .title("Người thân")
                .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory
                        .defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED)));

        googleMap.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(patientLatLng, 15f));

        if (timestamp > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm - dd/MM/yyyy",
                    java.util.Locale.getDefault());
            binding.tvLastLocationTime.setText("Cập nhật lúc: " + sdf.format(new java.util.Date(timestamp)));
        } else {
            binding.tvLastLocationTime.setText("Cập nhật lần cuối: Vừa xong");
        }

        new Thread(() -> {
            try {
                android.location.Geocoder geocoder = new android.location.Geocoder(this, java.util.Locale.getDefault());
                java.util.List<android.location.Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String addressLine = addresses.get(0).getAddressLine(0);
                    runOnUiThread(() -> {
                        if (binding != null) {
                            binding.tvAddress.setText(addressLine);
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        if (binding != null) {
                            binding.tvAddress.setText("Tọa độ: " + latitude + ", " + longitude);
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (binding != null) {
                        binding.tvAddress.setText("Tọa độ: " + latitude + ", " + longitude);
                    }
                });
            }
        }).start();
    }

    private void updateTabViewsVisibility() {
        if (binding == null)
            return;

        SharedPreferences prefs = getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE);
        String monitoringCode = prefs.getString("monitoringCode", null);

        if (monitoringCode == null || monitoringCode.isEmpty()) {
            binding.tabLayout.setVisibility(View.GONE);
            binding.recyclerCaregiverLogs.setVisibility(View.GONE);
            binding.layoutNoLogs.setVisibility(View.GONE);
            binding.recyclerPatientVitals.setVisibility(View.GONE);
            binding.layoutNoVitals.setVisibility(View.GONE);
            findViewById(R.id.tvMotto).setVisibility(View.GONE);
            binding.cardMonitoredInfo.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        binding.layoutEmptyState.setVisibility(View.GONE);
        binding.tabLayout.setVisibility(View.VISIBLE);
        findViewById(R.id.tvMotto).setVisibility(View.VISIBLE);
        binding.cardMonitoredInfo.setVisibility(View.VISIBLE);

        if (currentSelectedTab == 0) {
            // Tab uống thuốc
            binding.recyclerPatientVitals.setVisibility(View.GONE);
            binding.layoutNoVitals.setVisibility(View.GONE);
            binding.layoutLocationMap.setVisibility(View.GONE);

            if (adapter != null && adapter.getItemCount() > 0) {
                binding.recyclerCaregiverLogs.setVisibility(View.VISIBLE);
                binding.layoutNoLogs.setVisibility(View.GONE);
            } else {
                binding.recyclerCaregiverLogs.setVisibility(View.GONE);
                binding.layoutNoLogs.setVisibility(View.VISIBLE);
            }
        } else if (currentSelectedTab == 1) {
            // Tab chỉ số sinh tồn
            binding.recyclerCaregiverLogs.setVisibility(View.GONE);
            binding.layoutNoLogs.setVisibility(View.GONE);
            binding.layoutLocationMap.setVisibility(View.GONE);

            binding.recyclerPatientVitals.setVisibility(View.VISIBLE);
            binding.layoutNoVitals.setVisibility(View.GONE);
        } else {
            // Tab vị trí
            binding.recyclerCaregiverLogs.setVisibility(View.GONE);
            binding.layoutNoLogs.setVisibility(View.GONE);
            binding.recyclerPatientVitals.setVisibility(View.GONE);
            binding.layoutNoVitals.setVisibility(View.GONE);

            binding.layoutLocationMap.setVisibility(View.VISIBLE);
            if (binding.mapView != null) {
                binding.mapView.onResume();
            }
        }
    }

    @Override
    protected void onPause() {
        if (binding != null && binding.mapView != null) {
            binding.mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (binding != null && binding.mapView != null) {
            binding.mapView.onSaveInstanceState(outState);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (binding != null && binding.mapView != null) {
            binding.mapView.onLowMemory();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListeningToVitals();
        stopListeningToLocation();
        stopListeningToSos();
        if (binding != null && binding.mapView != null) {
            binding.mapView.onDestroy();
        }
        if (familyRef != null && syncListener != null) {
            familyRef.removeEventListener(syncListener);
        }
        if (binding != null) {
            if (binding.recyclerCaregiverLogs != null) {
                binding.recyclerCaregiverLogs.setAdapter(null);
            }
            if (binding.recyclerPatientVitals != null) {
                binding.recyclerPatientVitals.setAdapter(null);
            }
        }
        adapter = null;
        vitalsAdapter = null;
        binding = null;
    }

    private void disconnectCaregiver() {
        SharedPreferences prefs = getSharedPreferences("MediSensePrefs", Context.MODE_PRIVATE);
        String monitoringCode = prefs.getString("monitoringCode", null);

        prefs.edit()
                .remove("monitoringCode")
                .remove("userRole")
                .apply();

        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance()
                .getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance()
                    .collection("users").document(user.getUid())
                    .update("monitoringCode", null, "role", "patient");
            FirebaseDatabase.getInstance().getReference()
                    .child("userBindings").child(user.getUid()).child("monitoringCode")
                    .setValue(null);
            if (monitoringCode != null) {
                FirebaseDatabase.getInstance().getReference()
                        .child("families").child(monitoringCode).child("caregivers").child(user.getUid())
                        .setValue(null);
            }
        }

        stopListeningToVitals();
        stopListeningToLocation();
        stopListeningToSos();
        patientUid = null;

        if (familyRef != null && syncListener != null) {
            familyRef.removeEventListener(syncListener);
        }

        if (adapter != null) {
            adapter.setLogs(new ArrayList<>());
        }

        Toast.makeText(this, "Đã ngắt kết nối.", Toast.LENGTH_SHORT).show();
        updateTabViewsVisibility();
    }

    private long getStartOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void showDisconnectDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Ngắt kết nối giám sát?")
                .setMessage(
                        "Bạn sẽ không nhận được thông báo nhắc nhở và lịch sử uống thuốc của người thân nữa. Bạn có chắc chắn muốn ngắt kết nối?")
                .setPositiveButton("Ngắt kết nối", (dialog, which) -> disconnectCaregiver())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void loadMonitoredPatientInfo(String monitoringCode) {
        if (monitoringCode == null || monitoringCode.isEmpty()) {
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("familyCode", monitoringCode)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        com.google.firebase.firestore.DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        patientUid = doc.getId();

                        String fullName = doc.getString("fullName");
                        if (fullName == null || fullName.trim().isEmpty()) {
                            fullName = doc.getString("name");
                        }

                        if (fullName != null && !fullName.trim().isEmpty()) {
                            binding.tvMonitoredInfo.setText("Đang giám sát: " + fullName.trim());
                        } else {
                            binding.tvMonitoredInfo.setText("Đang giám sát: " + monitoringCode);
                        }

                        String phone = doc.getString("phone");
                        if (phone == null || phone.trim().isEmpty()) {
                            phone = doc.getString("phoneNumber");
                        }
                        startListeningToVitals(patientUid);
                        startListeningToLocation(patientUid);
                        startListeningToSos(patientUid, phone);
                    } else {
                        binding.tvMonitoredInfo.setText("Mã kết nối: " + monitoringCode);
                        patientUid = null;
                        stopListeningToVitals();
                        stopListeningToLocation();
                        stopListeningToSos();
                    }
                    updateTabViewsVisibility();
                })
                .addOnFailureListener(error -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    binding.tvMonitoredInfo.setText("Mã kết nối: " + monitoringCode);
                    stopListeningToLocation();
                    updateTabViewsVisibility();
                });
    }
}
