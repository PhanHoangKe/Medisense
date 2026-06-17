package vn.medisense.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import vn.medisense.app.R;
import vn.medisense.app.database.AppDatabase;
import vn.medisense.app.database.VitalSign;
import vn.medisense.app.databinding.BottomSheetHealthTrendsBinding;
import vn.medisense.app.repository.GeminiRepository;

/**
 * Bottom sheet phân tích xu hướng sức khỏe bằng AI
 */
public class HealthTrendsBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetHealthTrendsBinding binding;
    private GeminiRepository geminiRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int selectedDays = 7; // Mặc định 7 ngày

    public static HealthTrendsBottomSheet newInstance() {
        return new HealthTrendsBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetHealthTrendsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        geminiRepository = new GeminiRepository();
        setupListeners();
    }

    private void setupListeners() {
        binding.buttonClose.setOnClickListener(v -> dismiss());

        // Chọn khoảng thời gian
        binding.chipGroupPeriod.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chip7Days)) {
                selectedDays = 7;
            } else if (checkedIds.contains(R.id.chip30Days)) {
                selectedDays = 30;
            } else if (checkedIds.contains(R.id.chip90Days)) {
                selectedDays = 90;
            }
        });

        // Button phân tích
        binding.buttonAnalyze.setOnClickListener(v -> analyzeHealthTrends());

        // Button sao chép
        binding.buttonCopy.setOnClickListener(v -> copyResultToClipboard());
    }

    private void analyzeHealthTrends() {
        // Hiển thị loading
        binding.buttonAnalyze.setEnabled(false);
        binding.buttonAnalyze.setText("Đang phân tích...");
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.scrollResult.setVisibility(View.GONE);

        // Tính timestamp bắt đầu
        long now = System.currentTimeMillis();
        long startTime = now - (selectedDays * 24L * 60 * 60 * 1000);

        // Load dữ liệu vital signs
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            
            List<VitalSign> bloodPressureData = db.vitalSignDao()
                    .getVitalSignsByTypeAndTimeRange("blood_pressure", startTime, now);
            List<VitalSign> heartRateData = db.vitalSignDao()
                    .getVitalSignsByTypeAndTimeRange("heart_rate", startTime, now);
            List<VitalSign> bloodSugarData = db.vitalSignDao()
                    .getVitalSignsByTypeAndTimeRange("blood_sugar", startTime, now);
            List<VitalSign> weightData = db.vitalSignDao()
                    .getVitalSignsByTypeAndTimeRange("weight", startTime, now);

            // Kiểm tra có dữ liệu không
            int totalDataPoints = 
                    (bloodPressureData != null ? bloodPressureData.size() : 0) +
                    (heartRateData != null ? heartRateData.size() : 0) +
                    (bloodSugarData != null ? bloodSugarData.size() : 0) +
                    (weightData != null ? weightData.size() : 0);

            if (totalDataPoints == 0) {
                requireActivity().runOnUiThread(() -> {
                    binding.buttonAnalyze.setEnabled(true);
                    binding.buttonAnalyze.setText("🤖 Phân tích bằng AI");
                    binding.progressBar.setVisibility(View.GONE);
                    binding.scrollResult.setVisibility(View.VISIBLE);
                    binding.textResult.setText("⚠️ Không có dữ liệu sức khỏe trong " + 
                            selectedDays + " ngày qua.\n\n" +
                            "Vui lòng ghi nhận chỉ số sức khỏe trước khi phân tích.");
                });
                return;
            }

            // Tạo prompt cho AI
            String prompt = buildAnalysisPrompt(bloodPressureData, heartRateData, 
                    bloodSugarData, weightData, selectedDays);

            // Gọi API AI
            geminiRepository.assessHealthTrends(prompt, new GeminiRepository.GeminiCallback() {
                @Override
                public void onSuccess(String result) {
                    requireActivity().runOnUiThread(() -> {
                        binding.buttonAnalyze.setEnabled(true);
                        binding.buttonAnalyze.setText("🤖 Phân tích bằng AI");
                        binding.progressBar.setVisibility(View.GONE);
                        binding.scrollResult.setVisibility(View.VISIBLE);
                        binding.textResult.setText(formatMarkdownResult(result));
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    requireActivity().runOnUiThread(() -> {
                        binding.buttonAnalyze.setEnabled(true);
                        binding.buttonAnalyze.setText("🤖 Phân tích bằng AI");
                        binding.progressBar.setVisibility(View.GONE);
                        binding.scrollResult.setVisibility(View.VISIBLE);
                        binding.textResult.setText("❌ Lỗi: " + errorMessage + 
                                "\n\nVui lòng kiểm tra kết nối mạng và thử lại.");
                    });
                }
            });
        });
    }

    /**
     * Tạo prompt phân tích cho AI
     */
    private String buildAnalysisPrompt(List<VitalSign> bloodPressure, List<VitalSign> heartRate,
                                       List<VitalSign> bloodSugar, List<VitalSign> weight,
                                       int days) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        StringBuilder prompt = new StringBuilder();

        prompt.append("Bạn là bác sĩ AI chuyên phân tích sức khỏe cho ứng dụng MediSense.\n\n");
        prompt.append("Nhiệm vụ: Phân tích xu hướng sức khỏe của người dùng trong ").append(days).append(" ngày qua.\n");
        prompt.append("QUAN TRỌNG: Không chẩn đoán bệnh, không kê đơn thuốc. Chỉ đưa ra nhận xét xu hướng và khuyến nghị chung.\n\n");

        // Thêm dữ liệu huyết áp
        if (bloodPressure != null && !bloodPressure.isEmpty()) {
            prompt.append("📊 Huyết áp (").append(bloodPressure.size()).append(" lần đo):\n");
            for (VitalSign v : bloodPressure) {
                prompt.append(String.format("  %s: %d/%d mmHg\n", 
                        dateFormat.format(new Date(v.timestamp)), (int)v.value, (int)v.value2));
            }
            prompt.append("\n");
        }

        // Thêm dữ liệu nhịp tim
        if (heartRate != null && !heartRate.isEmpty()) {
            prompt.append("❤️ Nhịp tim (").append(heartRate.size()).append(" lần đo):\n");
            for (VitalSign v : heartRate) {
                prompt.append(String.format("  %s: %d bpm\n", 
                        dateFormat.format(new Date(v.timestamp)), (int)v.value));
            }
            prompt.append("\n");
        }

        // Thêm dữ liệu đường huyết
        if (bloodSugar != null && !bloodSugar.isEmpty()) {
            prompt.append("🩸 Đường huyết (").append(bloodSugar.size()).append(" lần đo):\n");
            for (VitalSign v : bloodSugar) {
                prompt.append(String.format("  %s: %.1f mg/dL\n", 
                        dateFormat.format(new Date(v.timestamp)), v.value));
            }
            prompt.append("\n");
        }

        // Thêm dữ liệu cân nặng
        if (weight != null && !weight.isEmpty()) {
            prompt.append("⚖️ Cân nặng (").append(weight.size()).append(" lần đo):\n");
            for (VitalSign v : weight) {
                prompt.append(String.format("  %s: %.1f kg\n", 
                        dateFormat.format(new Date(v.timestamp)), v.value));
            }
            prompt.append("\n");
        }

        prompt.append("\nHãy phân tích:\n");
        prompt.append("1. **Xu hướng các chỉ số**: Tăng/giảm/ổn định, có quy luật nào không?\n");
        prompt.append("2. **Điểm đáng chú ý**: Chỉ số bất thường hoặc dao động lớn\n");
        prompt.append("3. **Khuyến nghị**: Lời khuyên chung về theo dõi và lối sống (không chẩn đoán, không kê đơn)\n");
        prompt.append("4. **Khi nào cần gặp bác sĩ**: Dấu hiệu cần khám ngay\n\n");
        prompt.append("Trả lời bằng Tiếng Việt, ngắn gọn, thân thiện, sử dụng markdown và emoji cho dễ đọc.");

        return prompt.toString();
    }

    /**
     * Format kết quả markdown từ AI
     */
    private String formatMarkdownResult(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "Không có kết quả";
        }

        String result = markdown.trim();
        if (result.startsWith("```")) {
            result = result.replaceAll("```markdown\\n?", "")
                          .replaceAll("```\\n?", "");
        }

        result = result.replaceAll("(?m)^#{1,3}\\s*", "📌 ");
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        result = result.replaceAll("(?m)^[*-]\\s+", "  • ");

        return result.trim();
    }

    private void copyResultToClipboard() {
        String text = binding.textResult.getText().toString();
        if (text.isEmpty() || text.startsWith("⚠️") || text.startsWith("❌")) {
            Toast.makeText(getContext(), "Chưa có kết quả để sao chép", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) 
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Phân tích sức khỏe", text);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(getContext(), "✓ Đã sao chép vào clipboard", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
        binding = null;
    }
}
