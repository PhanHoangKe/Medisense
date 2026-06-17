package vn.medisense.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import vn.medisense.app.databinding.ActivityTravelPlannerBinding;
import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.Medication;
import vn.medisense.app.repository.GeminiRepository;

/**
 * Activity tạo kế hoạch chuẩn bị thuốc cho chuyến du lịch
 * Sử dụng AI để tính toán số lượng thuốc cần mang theo
 */
public class TravelPlannerActivity extends AppCompatActivity {

    private ActivityTravelPlannerBinding binding;
    private GeminiRepository geminiRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int daysCount = 3; // Mặc định 3 ngày

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTravelPlannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        geminiRepository = new GeminiRepository();

        setupUI();
        setupListeners();
    }

    private void setupUI() {
        updateDaysDisplay();
        binding.cardResult.setVisibility(View.GONE);
    }

    private void setupListeners() {
        binding.buttonBack.setOnClickListener(v -> finish());

        // Tăng/giảm số ngày
        binding.buttonDecrease.setOnClickListener(v -> {
            if (daysCount > 1) {
                daysCount--;
                updateDaysDisplay();
            }
        });

        binding.buttonIncrease.setOnClickListener(v -> {
            if (daysCount < 30) { // Giới hạn tối đa 30 ngày
                daysCount++;
                updateDaysDisplay();
            }
        });

        // Tạo kế hoạch
        binding.buttonGenerate.setOnClickListener(v -> generateTravelPlan());

        // Copy kết quả
        binding.buttonCopyResult.setOnClickListener(v -> copyResultToClipboard());
    }

    private void updateDaysDisplay() {
        binding.textDaysValue.setText(daysCount + " ngày");
    }

    private void generateTravelPlan() {
        String destination = binding.inputDestination.getText() != null 
                ? binding.inputDestination.getText().toString().trim() 
                : "";

        // Hiển thị loading
        binding.buttonGenerate.setEnabled(false);
        binding.buttonGenerate.setText("Đang tạo kế hoạch...");
        binding.cardResult.setVisibility(View.VISIBLE);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.textResult.setText("AI đang phân tích và tạo kế hoạch cho bạn...");

        // Load danh sách thuốc từ database
        executor.execute(() -> {
            List<Medication> medications = AppDatabase.getInstance(this)
                    .medicationDao()
                    .getAllMedicationsSync();

            if (medications == null || medications.isEmpty()) {
                runOnUiThread(() -> {
                    binding.buttonGenerate.setEnabled(true);
                    binding.buttonGenerate.setText("🤖 Tạo kế hoạch với AI");
                    binding.progressBar.setVisibility(View.GONE);
                    binding.textResult.setText("⚠️ Bạn chưa có thuốc nào trong danh sách.\n\nVui lòng thêm thuốc trước khi tạo kế hoạch du lịch.");
                });
                return;
            }

            // Gọi API AI
            geminiRepository.generateTravelPlan(daysCount, destination, medications, 
                new GeminiRepository.GeminiCallback() {
                    @Override
                    public void onSuccess(String result) {
                        runOnUiThread(() -> {
                            binding.buttonGenerate.setEnabled(true);
                            binding.buttonGenerate.setText("🤖 Tạo kế hoạch với AI");
                            binding.progressBar.setVisibility(View.GONE);
                            binding.textResult.setText(formatMarkdownResult(result));
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            binding.buttonGenerate.setEnabled(true);
                            binding.buttonGenerate.setText("🤖 Tạo kế hoạch với AI");
                            binding.progressBar.setVisibility(View.GONE);
                            binding.textResult.setText("❌ Lỗi: " + errorMessage + 
                                    "\n\nVui lòng kiểm tra kết nối mạng và thử lại.");
                        });
                    }
                });
        });
    }

    /**
     * Format kết quả markdown từ AI để hiển thị đẹp hơn
     */
    private String formatMarkdownResult(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "Không có kết quả";
        }

        // Loại bỏ markdown code blocks nếu có
        String result = markdown.trim();
        if (result.startsWith("```")) {
            result = result.replaceAll("```markdown\\n?", "")
                          .replaceAll("```\\n?", "");
        }

        // Thay thế markdown heading bằng emoji
        result = result.replaceAll("(?m)^#{1,3}\\s*", "📌 ");
        
        // Thay thế bold markdown
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        
        // Thay thế bullet points
        result = result.replaceAll("(?m)^[*-]\\s+", "  • ");

        return result.trim();
    }

    private void copyResultToClipboard() {
        String text = binding.textResult.getText().toString();
        if (text.isEmpty() || text.startsWith("AI đang") || text.startsWith("⚠️")) {
            Toast.makeText(this, "Chưa có kết quả để sao chép", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Kế hoạch du lịch", text);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, "✓ Đã sao chép vào clipboard", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
