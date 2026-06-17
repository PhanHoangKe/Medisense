package vn.medisense.app.ui;

import android.os.Bundle;
import android.util.Log;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import vn.medisense.app.R;
import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.MedicationDao;
import vn.medisense.app.database.SideEffectLog;
import vn.medisense.app.databinding.ActivityMedicationDetailBinding;
import vn.medisense.app.utils.StockManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import vn.medisense.app.repository.MedicationRepository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình chi tiết thuốc
 * Hiển thị thông tin thuốc và quản lý kho
 */
public class MedicationDetailActivity extends AppCompatActivity {
    
    public static final String EXTRA_MEDICATION_ID = "medication_id";
    
    private ActivityMedicationDetailBinding binding;
    private MedicationDao medicationDao;
    private MedicationRepository medicationRepository;
    private FirebaseFirestore db;
    private PillImageAdapter pillImageAdapter;
    private SideEffectLogAdapter sideEffectLogAdapter; // Thêm adapter cho side effects
    private final List<PillImageItem> pillImages = new ArrayList<>();
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private int medicationId;
    private Medication currentMedication;

    private static final String TAG = "MedicationDetail";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        binding = ActivityMedicationDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        medicationDao = AppDatabase.getInstance(getApplicationContext()).medicationDao();
        medicationRepository = new MedicationRepository(getApplicationContext());
        db = FirebaseFirestore.getInstance();

        pillImageAdapter = new PillImageAdapter(pillImages);
        binding.recyclerPillImages.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPillImages.setAdapter(pillImageAdapter);
        
        // Setup side effect adapter
        sideEffectLogAdapter = new SideEffectLogAdapter();
        binding.recyclerSideEffects.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerSideEffects.setAdapter(sideEffectLogAdapter);
        
        medicationId = getIntent().getIntExtra(EXTRA_MEDICATION_ID, -1);
        if (medicationId == -1) {
            Toast.makeText(this, "Lỗi: Không tìm thấy thuốc", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        loadMedicationDetails();
        
        binding.buttonRestock.setOnClickListener(v -> showRestockDialog());
        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonDelete.setOnClickListener(v -> showDeleteDialog());
        binding.buttonReportSideEffect.setOnClickListener(v -> showSideEffectSheet());

        // Edit medication button
        binding.buttonEditMedication.setOnClickListener(v -> {
            if (currentMedication == null) return;
            android.content.Intent intent = new android.content.Intent(this, vn.medisense.app.ui.AddMedicationActivity.class);
            intent.putExtra(AddMedicationActivity.EXTRA_EDIT_MEDICATION_ID, currentMedication.id);
            startActivity(intent);
        });
    }

    private void showSideEffectSheet() {
        if (currentMedication == null) return;
        SideEffectBottomSheetFragment sheet = SideEffectBottomSheetFragment.newInstance(
                currentMedication.id, currentMedication.name);
        sheet.show(getSupportFragmentManager(), "SideEffectBottomSheetFragment");
        
        // Reload side effects sau khi đóng bottom sheet
        sheet.getParentFragmentManager().setFragmentResultListener(
            "side_effect_saved", 
            this, 
            (requestKey, result) -> loadSideEffectHistory()
        );
    }

    private void loadMedicationDetails() {
        dbExecutor.execute(() -> {
            currentMedication = medicationDao.getMedicationByIdSync(medicationId);
            
            if (currentMedication == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Không tìm thấy thuốc", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }
            
            runOnUiThread(() -> updateUI());
        });
    }

    private void updateUI() {
        if (currentMedication == null) return;
        
        // Thông tin cơ bản
        binding.textMedicationName.setText(currentMedication.name);
        binding.textDosage.setText(currentMedication.dosage);
        binding.textFrequency.setText(currentMedication.frequency + " lần/ngày");
        
        // tải hình ảnh thuốc nếu có
        if (currentMedication.imagePath != null && !currentMedication.imagePath.isEmpty()) {
            binding.imgMedication.setVisibility(View.VISIBLE);
            com.bumptech.glide.Glide.with(this)
                    .load(currentMedication.imagePath)
                    .placeholder(android.R.drawable.ic_menu_report_image)
                    .into(binding.imgMedication);
        } else {
            binding.imgMedication.setVisibility(View.GONE);
        }
        binding.textInstructions.setText(
            TextUtils.isEmpty(currentMedication.instructions) 
                ? "Không có hướng dẫn" 
                : currentMedication.instructions
        );
        
        // Thông tin kho
        int current = currentMedication.currentStock;
        int total = currentMedication.totalStock;
        int threshold = currentMedication.lowStockThreshold;
        
        binding.textStockInfo.setText(String.format("Còn %d/%d viên", current, total));
        
        // ProgressBar
        binding.progressStock.setMax(total);
        binding.progressStock.setProgress(current);
        
        // Màu sắc theo mức độ
        int color;
        if (current == 0) {
            color = 0xFFD32F2F; // Đỏ - Hết thuốc
            binding.textStockStatus.setText("🚨 Đã hết thuốc!");
            binding.textStockStatus.setTextColor(color);
        } else if (current <= threshold) {
            color = 0xFFF57C00; // Cam - Sắp hết
            binding.textStockStatus.setText("⚠️ Sắp hết thuốc");
            binding.textStockStatus.setTextColor(color);
        } else {
            color = 0xFF4CAF50; // Xanh - Đủ thuốc
            binding.textStockStatus.setText("✓ Đủ thuốc");
            binding.textStockStatus.setTextColor(color);
        }
        
        binding.progressStock.setProgressTintList(
            android.content.res.ColorStateList.valueOf(color)
        );
        
        // Hiển thị ngưỡng cảnh báo
        binding.textThreshold.setText(
            String.format("Ngưỡng cảnh báo: %d viên", threshold)
        );

        loadPillImageHistory();
        loadSideEffectHistory(); // Thêm load side effects
    }

    private void loadSideEffectHistory() {
        dbExecutor.execute(() -> {
            List<SideEffectLog> logs = AppDatabase.getInstance(this)
                    .sideEffectLogDao()
                    .getLogsForMedication(medicationId);
            
            runOnUiThread(() -> {
                if (logs != null && !logs.isEmpty()) {
                    binding.textSideEffectEmpty.setVisibility(View.GONE);
                    binding.recyclerSideEffects.setVisibility(View.VISIBLE);
                    sideEffectLogAdapter.setLogs(logs);
                } else {
                    binding.textSideEffectEmpty.setVisibility(View.VISIBLE);
                    binding.recyclerSideEffects.setVisibility(View.GONE);
                }
            });
        });
    }

    private void loadPillImageHistory() {
        String medId = String.valueOf(medicationId);
        binding.textPillImageEmpty.setVisibility(View.GONE);

        db.collection("PillImages")
            .whereEqualTo("medId", medId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                pillImages.clear();
                querySnapshot.getDocuments().forEach(doc -> {
                    String url = doc.getString("url");
                    Long ts = doc.getLong("timestamp");
                    if (url != null && ts != null) {
                        pillImages.add(new PillImageItem(url, ts));
                    }
                });
                pillImageAdapter.notifyDataSetChanged();
                if (pillImages.isEmpty()) {
                    binding.textPillImageEmpty.setVisibility(View.VISIBLE);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to load pill images", e);
                binding.textPillImageEmpty.setVisibility(View.VISIBLE);
            });
    }

    private void showRestockDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_restock, null);
        TextInputEditText inputQuantity = dialogView.findViewById(R.id.inputRestockQuantity);
        
        new MaterialAlertDialogBuilder(this)
            .setTitle("Nhập thêm thuốc")
            .setMessage("Nhập số lượng thuốc bạn muốn thêm vào kho:")
            .setView(dialogView)
            .setPositiveButton("Xác nhận", (dialog, which) -> {
                String quantityText = inputQuantity.getText() != null 
                    ? inputQuantity.getText().toString().trim() 
                    : "";
                
                if (TextUtils.isEmpty(quantityText)) {
                    Toast.makeText(this, "Vui lòng nhập số lượng", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                try {
                    int quantity = Integer.parseInt(quantityText);
                    if (quantity <= 0) {
                        Toast.makeText(this, "Số lượng phải lớn hơn 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    restockMedication(quantity);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Số lượng không hợp lệ", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Hủy", null)
            .show();
    }

    private void showDeleteDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Xóa thuốc")
                .setMessage("Bạn có chắc chắn muốn xóa thuốc này? Hành động không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    dbExecutor.execute(() -> {
                        medicationRepository.deleteMedicationSync(medicationId);
                        runOnUiThread(() -> {
                            Toast.makeText(MedicationDetailActivity.this, "Đã xóa thuốc", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void restockMedication(int quantity) {
        StockManager.restockMedication(
            this, 
            medicationId, 
            quantity, 
            new StockManager.RestockCallback() {
                @Override
                public void onSuccess(Medication medication) {
                    runOnUiThread(() -> {
                        currentMedication = medication;
                        updateUI();
                        Toast.makeText(
                            MedicationDetailActivity.this, 
                            String.format("Đã thêm %d viên vào kho", quantity), 
                            Toast.LENGTH_SHORT
                        ).show();
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> {
                        Toast.makeText(
                            MedicationDetailActivity.this, 
                            errorMessage, 
                            Toast.LENGTH_SHORT
                        ).show();
                    });
                }
            }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdown();
    }
}
