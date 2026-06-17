package vn.medisense.app.utils;

import android.content.Context;
import android.util.Log;

import vn.medisense.app.BuildConfig;
import vn.medisense.app.api.DrugInteractionResponse;
import vn.medisense.app.api.GeminiApiService;
import vn.medisense.app.api.GeminiRequest;
import vn.medisense.app.api.GeminiResponse;
import vn.medisense.app.database.Medication;
import vn.medisense.app.utils.NetworkUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * SafetyChecker - Kiểm tra tương tác thuốc sử dụng Gemini AI
 * 
 * Class này xử lý logic gọi API Gemini để kiểm tra:
 * - Tương tác thuốc-thuốc (Drug-Drug Interaction)
 * - Tương tác thuốc-thực phẩm (Drug-Food Interaction)
 * - Phản ứng phụ nghiêm trọng
 * 
 * Tất cả các thao tác đều bất đồng bộ để không làm treo UI Thread.
 */
public class SafetyChecker {
    private static final String TAG = "SafetyChecker";
    private static final String API_KEY = (BuildConfig.GEMINI_API_KEY != null && !BuildConfig.GEMINI_API_KEY.isEmpty()) 
            ? BuildConfig.GEMINI_API_KEY : "openrouter-active";
    private final Context context;
    private final GeminiApiService apiService;
    private final Gson gson;

    public SafetyChecker(Context context) {
        this.context = context.getApplicationContext();
        this.apiService = vn.medisense.app.utils.NetworkModule.getGeminiService();
        this.gson = new Gson();
    }

    /**
     * Kiểm tra tương tác thuốc bất đồng bộ.
     * Kiểm tra internet trước khi gọi API.
     */
    public void checkDrugInteraction(
            List<Medication> existingMedications,
            String newMedicationName,
            SafetyCheckCallback callback) {

        // Kiểm tra internet
        if (!NetworkUtils.isNetworkAvailable(context)) {
            DrugInteractionResponse offline = new DrugInteractionResponse();
            offline.hasWarning = false;
            offline.interactionSummary = context.getString(
                    vn.medisense.app.R.string.ai_offline_message);
            callback.onOffline(offline);
            return;
        }

        if (API_KEY == null || API_KEY.isEmpty()) {
            DrugInteractionResponse noKey = new DrugInteractionResponse();
            noKey.hasWarning = false;
            noKey.interactionSummary = context.getString(
                    vn.medisense.app.R.string.ai_not_configured);
            callback.onOffline(noKey);
            return;
        }

        String prompt = buildPrompt(existingMedications, newMedicationName);
        GeminiRequest request = new GeminiRequest(prompt);

        Call<GeminiResponse> call = apiService.generateContent(API_KEY, request);
        call.enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseText = response.body().getFirstText();
                    if (responseText != null && !responseText.trim().isEmpty()) {
                        DrugInteractionResponse result = parseResponse(responseText);
                        callback.onSuccess(result);
                    } else {
                        callback.onError(context.getString(vn.medisense.app.R.string.ai_error_parse));
                    }
                } else {
                    callback.onError("Lỗi API: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                String errorMsg = (t instanceof IOException)
                        ? context.getString(vn.medisense.app.R.string.ai_offline_message)
                        : "Lỗi không xác định: " + t.getMessage();
                Log.e(TAG, "Gọi API thất bại", t);
                callback.onError(errorMsg);
            }
        });
    }

    /**
     * Xây dựng prompt an toàn y tế cho Gemini AI.
     * Dùng ngôn ngữ "có khả năng", "cần kiểm tra" — không dùng "chắc chắn nguy hiểm".
     */
    private String buildPrompt(List<Medication> existingMedications, String newMedicationName) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Bạn là trợ lý dược lý hỗ trợ tham khảo. Nhiệm vụ của bạn là kiểm tra khả năng tương tác thuốc.\n");
        prompt.append("QUAN TRỌNG: Không được chẩn đoán bệnh, không kê đơn, không khuyên thay đổi liều. Chỉ cung cấp thông tin tham khảo.\n\n");

        if (existingMedications == null || existingMedications.isEmpty()) {
            prompt.append("Người dùng chưa có thuốc nào trong hệ thống.\n");
        } else {
            prompt.append("Danh sách thuốc người dùng đang dùng:\n");
            for (int i = 0; i < existingMedications.size(); i++) {
                Medication med = existingMedications.get(i);
                prompt.append(String.format("%d. %s", i + 1, med.name));
                if (med.dosage != null && !med.dosage.isEmpty()) {
                    prompt.append(String.format(" (%s)", med.dosage));
                }
                prompt.append("\n");
            }
        }

        prompt.append("\nThuốc mới muốn thêm: ").append(newMedicationName).append("\n\n");

        prompt.append("Hãy kiểm tra:\n");
        prompt.append("1. Có khả năng tương tác thuốc-thuốc nào không?\n");
        prompt.append("2. Có khả năng phản ứng phụ đáng lưu ý không?\n");
        prompt.append("3. Có thực phẩm nào nên tránh khi dùng thuốc này không? (bưởi, sữa, rượu, cà phê...)\n\n");

        prompt.append("Trả về JSON hợp lệ (không markdown, không text thêm):\n");
        prompt.append("{\n");
        prompt.append("  \"hasWarning\": true/false,\n");
        prompt.append("  \"severity\": \"LOW\" hoặc \"MEDIUM\" hoặc \"HIGH\",\n");
        prompt.append("  \"interactionSummary\": \"Mô tả ngắn gọn bằng tiếng Việt, dùng từ 'có khả năng', 'cần kiểm tra', 'nên hỏi bác sĩ/dược sĩ'. Không dùng 'chắc chắn nguy hiểm'.\",\n");
        prompt.append("  \"foodWarnings\": \"Thực phẩm cần tránh nếu có, để rỗng nếu không có\",\n");
        prompt.append("  \"recommendedAction\": \"Hành động khuyến nghị ngắn gọn\",\n");
        prompt.append("  \"disclaimer\": \"Thông tin chỉ mang tính tham khảo, không thay thế tư vấn bác sĩ/dược sĩ.\"\n");
        prompt.append("}");

        return prompt.toString();
    }

    /**
     * Parse response từ Gemini AI thành DrugInteractionResponse
     */
    private DrugInteractionResponse parseResponse(String responseText) {
        try {
            // Loại bỏ markdown code block hoặc văn bản thừa nếu có
            String cleanedJson = responseText.trim();
            
            // Tìm và trích xuất khối JSON nằm giữa cặp dấu ngoặc nhọn { ... }
            int firstBrace = cleanedJson.indexOf('{');
            int lastBrace = cleanedJson.lastIndexOf('}');
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                cleanedJson = cleanedJson.substring(firstBrace, lastBrace + 1);
            } else {
                // Hậu thuẫn loại bỏ tag ```json thủ công nếu không tìm thấy ngoặc nhọn đúng quy cách
                if (cleanedJson.startsWith("```json")) {
                    cleanedJson = cleanedJson.substring(7);
                }
                if (cleanedJson.startsWith("```")) {
                    cleanedJson = cleanedJson.substring(3);
                }
                if (cleanedJson.endsWith("```")) {
                    cleanedJson = cleanedJson.substring(0, cleanedJson.length() - 3);
                }
            }
            cleanedJson = cleanedJson.trim();
            
            // Phân tích cú pháp JSON sang đối tượng Java
            DrugInteractionResponse response = gson.fromJson(cleanedJson, DrugInteractionResponse.class);
            
            // Kiểm tra tính hợp lệ của phản hồi
            if (response == null) {
                response = new DrugInteractionResponse();
                response.hasWarning = false;
                response.message = "Không thể phân tích kết quả từ AI";
            }
            
            return response;
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Phân tích cú pháp JSON phản hồi thất bại: " + responseText, e);
            DrugInteractionResponse fallback = new DrugInteractionResponse();
            fallback.hasWarning = false;
            fallback.message = "Lỗi phân tích dữ liệu từ AI";
            return fallback;
        }
    }

    /**
     * Callback interface cho kết quả kiểm tra.
     * onOffline: không có mạng hoặc API key chưa cấu hình — trả về response rỗng để UI cho phép lưu thủ công.
     */
    public interface SafetyCheckCallback {
        void onSuccess(DrugInteractionResponse result);
        void onError(String errorMessage);
        /** Gọi khi offline hoặc API key thiếu — result.interactionSummary chứa thông báo hiển thị */
        default void onOffline(DrugInteractionResponse result) {
            // Mặc định: xử lý như onError với message từ result
            onError(result.interactionSummary);
        }
    }
}
