package vn.medisense.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import vn.medisense.app.R;
import vn.medisense.app.database.ActivityLog;
import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.MoodLog;
import vn.medisense.app.database.VitalSign;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TreatmentActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private ImageButton btnSettings;
    
    // Headers và Cards
    private LinearLayout headerMeds, cardMeds;
    private LinearLayout headerMeasure, cardMeasure;
    private LinearLayout headerActivity, cardActivity;
    private LinearLayout headerMood, cardMood;
    
    private ImageView chevronMeds, chevronMeasure, chevronActivity, chevronMood;
    
    // Empty state layouts
    private LinearLayout layoutEmptyMeds, layoutEmptyMeasure;
    private LinearLayout layoutEmptyActivity, layoutEmptyMood;
    
    // Add buttons
    private ImageButton btnAddMeds, btnAddMeasure, btnAddActivity, btnAddMood;
    private Button btnGetStarted;
    
    // Lists và Adapters
    private RecyclerView recyclerMeds, recyclerMeasure;
    private RecyclerView recyclerActivity, recyclerMood; // Thêm RecyclerView mới
    private MedicationAdapter medAdapter;
    private MeasureAdapter measureAdapter;
    private ActivityAdapter activityAdapter; // Thêm adapter mới
    private MoodAdapter moodAdapter; // Thêm adapter mới
    
    private AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_treatment);
        
        db = AppDatabase.getInstance(this);
        
        initViews();
        setupToolbar();
        setupClickListeners();
        setupRecyclerViews();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }
    
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        btnSettings = findViewById(R.id.btnSettings);
        
        headerMeds = findViewById(R.id.headerMeds);
        cardMeds = findViewById(R.id.cardMeds);
        headerMeasure = findViewById(R.id.headerMeasure);
        cardMeasure = findViewById(R.id.cardMeasure);
        headerActivity = findViewById(R.id.headerActivity);
        cardActivity = findViewById(R.id.cardActivity);
        headerMood = findViewById(R.id.headerMood);
        cardMood = findViewById(R.id.cardMood);
        
        chevronMeds = findViewById(R.id.chevronMeds);
        chevronMeasure = findViewById(R.id.chevronMeasure);
        chevronActivity = findViewById(R.id.chevronActivity);
        chevronMood = findViewById(R.id.chevronMood);
        
        layoutEmptyMeds = findViewById(R.id.layoutEmptyMeds);
        layoutEmptyMeasure = findViewById(R.id.layoutEmptyMeasure);
        layoutEmptyActivity = findViewById(R.id.layoutEmptyActivity);
        layoutEmptyMood = findViewById(R.id.layoutEmptyMood);
        
        btnAddMeds = findViewById(R.id.btnAddMeds);
        btnAddMeasure = findViewById(R.id.btnAddMeasure);
        btnAddActivity = findViewById(R.id.btnAddActivity);
        btnAddMood = findViewById(R.id.btnAddMood);
        btnGetStarted = findViewById(R.id.btnGetStarted);
        
        recyclerMeds = findViewById(R.id.recyclerMeds);
        recyclerMeasure = findViewById(R.id.recyclerMeasure);
        recyclerActivity = findViewById(R.id.recyclerActivity); // Thêm RecyclerView
        recyclerMood = findViewById(R.id.recyclerMood); // Thêm RecyclerView
    }
    
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Liệu trình");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }
    
    private void setupClickListeners() {
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        
        // Collapsible sections
        headerMeds.setOnClickListener(v -> toggleSection(cardMeds, chevronMeds));
        headerMeasure.setOnClickListener(v -> toggleSection(cardMeasure, chevronMeasure));
        headerActivity.setOnClickListener(v -> toggleSection(cardActivity, chevronActivity));
        headerMood.setOnClickListener(v -> toggleSection(cardMood, chevronMood));
        
        // Add action triggers
        btnAddMeds.setOnClickListener(v -> addNewMedication());
        btnGetStarted.setOnClickListener(v -> addNewMedication());
        
        btnAddMeasure.setOnClickListener(v -> {
            Intent intent = new Intent(this, HealthTrackerActivity.class);
            startActivity(intent);
        });
        
        btnAddActivity.setOnClickListener(v -> showActivitySelectionDialog());
        btnAddMood.setOnClickListener(v -> showMoodSelectionDialog());
        
        // Long press trên settings để mở Travel Planner
        btnSettings.setOnLongClickListener(v -> {
            showToolsMenu();
            return true;
        });
    }
    
    private void showToolsMenu() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🛠️ Công cụ")
                .setItems(new String[]{"🧳 Chuẩn bị du lịch"}, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, TravelPlannerActivity.class));
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
    
    private void toggleSection(View card, ImageView chevron) {
        if (card.getVisibility() == View.VISIBLE) {
            // Đang mở → Đóng lại
            card.setVisibility(View.GONE);
            chevron.animate().rotation(90).setDuration(200).start(); // Mũi tên lên
        } else {
            // Đang đóng → Mở ra
            card.setVisibility(View.VISIBLE);
            chevron.animate().rotation(-90).setDuration(200).start(); // Mũi tên xuống
        }
    }
    
    private void addNewMedication() {
        Intent intent = new Intent(this, AddMedicationActivity.class);
        startActivity(intent);
    }
    
    private void setupRecyclerViews() {
        recyclerMeds.setLayoutManager(new LinearLayoutManager(this));
        medAdapter = new MedicationAdapter();
        recyclerMeds.setAdapter(medAdapter);
        
        recyclerMeasure.setLayoutManager(new LinearLayoutManager(this));
        measureAdapter = new MeasureAdapter();
        recyclerMeasure.setAdapter(measureAdapter);
        
        // Thêm RecyclerView cho Activity
        recyclerActivity.setLayoutManager(new LinearLayoutManager(this));
        activityAdapter = new ActivityAdapter();
        recyclerActivity.setAdapter(activityAdapter);
        
        // Thêm RecyclerView cho Mood
        recyclerMood.setLayoutManager(new LinearLayoutManager(this));
        moodAdapter = new MoodAdapter();
        recyclerMood.setAdapter(moodAdapter);
    }
    
    private void loadData() {
        executor.execute(() -> {
            List<Medication> meds = db.medicationDao().getAllMedicationsSync();
            
            // Fetch các latest vital signs cho each type
            List<VitalSign> vitals = new ArrayList<>();
            String[] types = {"blood_pressure", "heart_rate", "blood_sugar", "weight"};
            for (String type : types) {
                VitalSign latest = db.vitalSignDao().getLatestVitalSign(type);
                if (latest != null) {
                    vitals.add(latest);
                }
            }
            
            // Tải Activity Logs
            List<ActivityLog> activities = db.activityLogDao().getAllSync();
            
            // Tải Mood Logs
            List<MoodLog> moods = db.moodLogDao().getAllSync();
            
            runOnUiThread(() -> {
                // Cập nhật Medications
                if (meds != null && !meds.isEmpty()) {
                    layoutEmptyMeds.setVisibility(View.GONE);
                    recyclerMeds.setVisibility(View.VISIBLE);
                    medAdapter.setMedications(meds);
                } else {
                    layoutEmptyMeds.setVisibility(View.VISIBLE);
                    recyclerMeds.setVisibility(View.GONE);
                }
                
                // Cập nhật Vital Signs
                if (!vitals.isEmpty()) {
                    layoutEmptyMeasure.setVisibility(View.GONE);
                    recyclerMeasure.setVisibility(View.VISIBLE);
                    measureAdapter.setVitals(vitals);
                } else {
                    layoutEmptyMeasure.setVisibility(View.VISIBLE);
                    recyclerMeasure.setVisibility(View.GONE);
                }
                
                // Cập nhật Activities
                if (!activities.isEmpty()) {
                    layoutEmptyActivity.setVisibility(View.GONE);
                    recyclerActivity.setVisibility(View.VISIBLE);
                    activityAdapter.setActivities(activities);
                } else {
                    layoutEmptyActivity.setVisibility(View.VISIBLE);
                    recyclerActivity.setVisibility(View.GONE);
                }
                
                // Cập nhật Moods
                if (!moods.isEmpty()) {
                    layoutEmptyMood.setVisibility(View.GONE);
                    recyclerMood.setVisibility(View.VISIBLE);
                    moodAdapter.setMoods(moods);
                } else {
                    layoutEmptyMood.setVisibility(View.VISIBLE);
                    recyclerMood.setVisibility(View.GONE);
                }
            });
        });
    }
    
    private void showActivitySelectionDialog() {
        String[] activities = {"Đi bộ", "Chạy bộ", "Tập Yoga", "Đạp xe", "Thiền"};
        new AlertDialog.Builder(this)
                .setTitle("Thêm hoạt động thể chất")
                .setItems(activities, (dialog, which) -> {
                    String activityName = activities[which];
                    // Lưu vào database
                    executor.execute(() -> {
                        ActivityLog log = new ActivityLog(activityName, System.currentTimeMillis(), "");
                        db.activityLogDao().insert(log);
                        // Reload data
                        loadData();
                    });
                    Toast.makeText(this, "Đã thêm hoạt động: " + activityName, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
    
    private void showMoodSelectionDialog() {
        String[] moods = {"Rất tốt 😊", "Bình thường 😐", "Mệt mỏi 😫", "Đau đầu 🤕", "Sốt / Nóng trong người 🥵"};
        new AlertDialog.Builder(this)
                .setTitle("Ghi nhận tâm trạng & Triệu chứng")
                .setItems(moods, (dialog, which) -> {
                    String moodName = moods[which];
                    // Lưu vào database
                    executor.execute(() -> {
                        MoodLog log = new MoodLog(moodName, System.currentTimeMillis(), "");
                        db.moodLogDao().insert(log);
                        // Reload data
                        loadData();
                    });
                    Toast.makeText(this, "Đã ghi nhận: " + moodName, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
    
    // --- Adapters ---
    
    private class MedicationAdapter extends RecyclerView.Adapter<MedicationAdapter.ViewHolder> {
        private List<Medication> medications = new ArrayList<>();
        
        public void setMedications(List<Medication> medications) {
            this.medications = medications;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_treatment_med, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Medication med = medications.get(position);
            holder.tvName.setText(med.name);
            
            String dosage = med.dosage != null ? med.dosage : "";
            String instruction = med.instructions != null ? med.instructions : "";
            String detailStr = dosage + (instruction.isEmpty() ? "" : " • " + instruction);
            holder.tvDetails.setText(detailStr);
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(TreatmentActivity.this, MedicationDetailActivity.class);
                intent.putExtra(MedicationDetailActivity.EXTRA_MEDICATION_ID, med.id);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return medications.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDetails;
            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvMedName);
                tvDetails = itemView.findViewById(R.id.tvMedDetails);
            }
        }
    }
    
    private class MeasureAdapter extends RecyclerView.Adapter<MeasureAdapter.ViewHolder> {
        private List<VitalSign> vitals = new ArrayList<>();
        
        public void setVitals(List<VitalSign> vitals) {
            this.vitals = vitals;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_treatment_measure, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VitalSign vital = vitals.get(position);
            
            String displayType = vital.type;
            String valStr = "";
            
            if ("blood_pressure".equalsIgnoreCase(vital.type)) {
                displayType = "Huyết áp";
                valStr = (int)vital.value + "/" + (int)vital.value2 + " mmHg";
            } else if ("heart_rate".equalsIgnoreCase(vital.type)) {
                displayType = "Nhịp tim";
                valStr = (int)vital.value + " bpm";
            } else if ("blood_sugar".equalsIgnoreCase(vital.type)) {
                displayType = "Đường huyết";
                valStr = vital.value + " mg/dL";
            } else if ("weight".equalsIgnoreCase(vital.type)) {
                displayType = "Cân nặng";
                valStr = vital.value + " kg";
            }
            
            holder.tvName.setText(displayType);
            
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
            String dateStr = sdf.format(new Date(vital.timestamp));
            holder.tvDetails.setText(valStr + " • Đo lúc: " + dateStr);
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(TreatmentActivity.this, HealthTrackerActivity.class);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return Math.min(vitals.size(), 5);
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDetails;
            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvVitalName);
                tvDetails = itemView.findViewById(R.id.tvVitalDetails);
            }
        }
    }
    
    // ActivityAdapter - Adapter cho hoạt động thể chất
    private class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ViewHolder> {
        private List<ActivityLog> activities = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        
        public void setActivities(List<ActivityLog> activities) {
            this.activities = activities;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_treatment_activity, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ActivityLog activity = activities.get(position);
            holder.tvActivityName.setText(activity.activityName);
            holder.tvActivityTime.setText(dateFormat.format(new Date(activity.timestamp)));
        }

        @Override
        public int getItemCount() {
            return Math.min(activities.size(), 10); // Giới hạn 10 items
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvActivityName, tvActivityTime;
            ViewHolder(View itemView) {
                super(itemView);
                tvActivityName = itemView.findViewById(R.id.tvActivityName);
                tvActivityTime = itemView.findViewById(R.id.tvActivityTime);
            }
        }
    }
    
    // MoodAdapter - Adapter cho tâm trạng & triệu chứng
    private class MoodAdapter extends RecyclerView.Adapter<MoodAdapter.ViewHolder> {
        private List<MoodLog> moods = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        
        public void setMoods(List<MoodLog> moods) {
            this.moods = moods;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_treatment_mood, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MoodLog mood = moods.get(position);
            holder.tvMoodName.setText(mood.moodName);
            holder.tvMoodTime.setText(dateFormat.format(new Date(mood.timestamp)));
        }

        @Override
        public int getItemCount() {
            return Math.min(moods.size(), 10); // Giới hạn 10 items
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvMoodName, tvMoodTime;
            ViewHolder(View itemView) {
                super(itemView);
                tvMoodName = itemView.findViewById(R.id.tvMoodName);
                tvMoodTime = itemView.findViewById(R.id.tvMoodTime);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
