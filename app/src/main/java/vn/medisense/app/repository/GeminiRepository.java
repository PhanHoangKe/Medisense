package vn.medisense.app.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import vn.medisense.app.BuildConfig;
import vn.medisense.app.api.GeminiApiService;
import vn.medisense.app.api.GeminiRequest;
import vn.medisense.app.api.GeminiResponse;
import vn.medisense.app.api.GeminiVisionRequest;
import vn.medisense.app.api.ParsedMedicationInfo;
import vn.medisense.app.api.ParsedPrescriptionItem;
import vn.medisense.app.api.ParsedPrescriptionResult;
import vn.medisense.app.database.Medication;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Repository quản lý việc gọi Gemini API
 * Xử lý parsing và error handling
 */
public class GeminiRepository {
    /*
     * "Tôi gặp lỗi sau khi thêm tính năng Nhận diện viên thuốc: Tính năng Quét đơn
     * thuốc cũ báo 'Dữ liệu không hợp lệ'. Hãy giúp tôi kiểm tra và sửa lại code
     * theo các bước sau:
     * 
     * Phân loại Task: Đảm bảo tôi có 2 phương thức riêng biệt: scanPrescription (để
     * đọc đơn thuốc nhiều dòng) và identifySinglePill (để soi 1 viên thuốc). Không
     * được dùng chung 1 Prompt cho cả hai.
     * 
     * Kiểm tra Prompt cũ: Cập nhật lại Prompt cho scanPrescription. Nhắc AI phải
     * trả về đúng mảng JSON gồm: name, dosage, frequency, time.
     * 
     * Khởi tạo dữ liệu mặc định: Khi lưu thuốc từ đơn thuốc (OCR), hãy đảm bảo các
     * trường mới thêm (isCritical, currentStock, totalStock) được gán giá trị mặc
     * định (ví dụ: isCritical = false, stock = 0) để không gây lỗi khi chèn vào
     * Room Database.
     * 
     * Log dữ liệu: Thêm Log.d để in ra chuỗi JSON mà Gemini trả về trước khi Parse.
     * Nếu JSON không đúng định dạng, hãy viết thêm một hàm cleanJsonString để loại
     * bỏ các ký tự thừa (như json ... ) trước khi chuyển thành Object.
     * 
     * Hãy viết lại đoạn code Java này để cả 2 tính năng hoạt động độc lập và ổn
     * định."
     */
    private static final String TAG = "GeminiRepository";

    /**
     * Prompt OCR đơn thuốc — yêu cầu AI trả về confidence, needsReview,
     * missingFields.
     * Không tự bịa tên thuốc nếu không đọc rõ.
     */
    private static final String PROMPT_SCAN_TEMPLATE = "Bạn là trợ lý y tế hỗ trợ đọc đơn thuốc. Nhiệm vụ: trích xuất thông tin từ văn bản OCR.\n"
            + "QUAN TRỌNG: Không tự bịa tên thuốc nếu không đọc rõ. Nếu không chắc, đặt needsReview=true.\n"
            + "Không chẩn đoán bệnh, không kê đơn, không khuyên thay đổi liều.\n\n"
            + "Văn bản OCR:\n%s\n\n"
            + "Trả về DUY NHẤT một JSON hợp lệ (không markdown, không text thêm):\n"
            + "{\n"
            + "  \"diagnosis\": \"Chẩn đoán nếu có, để rỗng nếu không\",\n"
            + "  \"doctorAdvice\": \"Lời dặn bác sĩ nếu có, để rỗng nếu không\",\n"
            + "  \"medications\": [\n"
            + "    {\n"
            + "      \"name\": \"Tên thuốc và hàm lượng. Nếu không đọc rõ: để rỗng, KHÔNG bịa\",\n"
            + "      \"dosage\": \"Liều dùng (vd: 1 viên). Để rỗng nếu không có\",\n"
            + "      \"frequency\": \"Số lần/ngày dạng chuỗi (vd: '2 lần')\",\n"
            + "      \"durationDays\": 0,\n"
            + "      \"totalQuantity\": 0,\n"
            + "      \"timeDoses\": [{\"time\": \"08:00\", \"dose\": \"1 viên\"}],\n"
            + "      \"notes\": \"Ghi chú nếu có\",\n"
            + "      \"mealContext\": \"BEFORE_MEAL hoặc AFTER_MEAL hoặc NONE\",\n"
            + "      \"offsetMinutes\": 0,\n"
            + "      \"specificShifts\": \"Morning,Night (nếu có)\",\n"
            + "      \"confidence\": 0.9,\n"
            + "      \"needsReview\": false,\n"
            + "      \"missingFields\": []\n"
            + "    }\n"
            + "  ]\n"
            + "}\n"
            + "Quy tắc:\n"
            + "- confidence: 0.0–1.0. Nếu tên thuốc mờ/không rõ: confidence < 0.6, needsReview=true\n"
            + "- missingFields: liệt kê field còn thiếu, vd [\"dosage\",\"frequency\"]\n"
            + "- Nếu không đọc được gì hữu ích: trả về medications=[]\n";

    private final GeminiApiService apiService;
    private final Gson gson;

    /**
     * Interface callback để trả kết quả phân tích
     */
    public interface PrescriptionCallback {
        void onAnalysisSuccess(@NonNull ParsedPrescriptionResult result);

        void onAnalysisError(@NonNull String errorMessage);
    }

    public interface GeminiCallback {
        void onSuccess(@NonNull String result);

        void onError(@NonNull String errorMessage);
    }

    public GeminiRepository() {
        this.apiService = vn.medisense.app.utils.NetworkModule.getGeminiService();
        this.gson = new Gson();
    }

    /**
     * Phân tích văn bản đơn thuốc (nhiều dòng)
     *
     * @param extractedText Văn bản đã được trích xuất (OCR) từ ảnh
     * @param callback      Callback nhận kết quả trả về
     */
    public void scanPrescription(@NonNull String extractedText, @NonNull PrescriptionCallback callback) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onAnalysisError("API Key chưa được cấu hình. Vui lòng kiểm tra local.properties");
            return;
        }

        String prompt = String.format(PROMPT_SCAN_TEMPLATE, extractedText);
        GeminiRequest request = new GeminiRequest(prompt);

        apiService.generateContent(apiKey, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(@NonNull Call<GeminiResponse> call,
                    @NonNull Response<GeminiResponse> response) {
                handleScanResponse(response, callback);
            }

            @Override
            public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                callback.onAnalysisError("Lỗi kết nối mạng: " + t.getMessage());
            }
        });
    }

    /**
     * Xử lý phản hồi (response) từ việc quét đơn thuốc
     */
    private void handleScanResponse(@NonNull Response<GeminiResponse> response,
            @NonNull PrescriptionCallback callback) {
        if (!response.isSuccessful()) {
            callback.onAnalysisError("Lỗi API (HTTP " + response.code() + ")");
            return;
        }

        GeminiResponse body = response.body();
        if (body == null) {
            callback.onAnalysisError("Phản hồi từ máy chủ bị trống");
            return;
        }

        String jsonText = body.getFirstText();
        if (jsonText == null || jsonText.trim().isEmpty()) {
            callback.onAnalysisError("AI không trả về kết quả");
            return;
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Scan JSON thô: " + jsonText);
        }
        parseScanJsonToMedicationInfo(jsonText, callback);
    }

    /**
     * Phân tích (parse) chuỗi JSON quét đơn thuốc thành danh sách
     * ParsedMedicationInfo
     */
    private void parseScanJsonToMedicationInfo(@NonNull String jsonText,
            @NonNull PrescriptionCallback callback) {
        try {
            String cleanedJson = cleanJsonString(jsonText);
            String jsonObjectStr = extractJsonObject(cleanedJson);
            if (jsonObjectStr == null) {
                jsonObjectStr = cleanedJson; // fallback
            }
            vn.medisense.app.api.RawGeminiPrescriptionResult rawResult = gson.fromJson(jsonObjectStr,
                    vn.medisense.app.api.RawGeminiPrescriptionResult.class);

            if (rawResult == null || rawResult.medications == null || rawResult.medications.isEmpty()) {
                callback.onAnalysisError("Dữ liệu không hợp lệ hoặc rỗng");
                return;
            }

            List<ParsedMedicationInfo> mappedMedications = new ArrayList<>();
            for (ParsedPrescriptionItem item : rawResult.medications) {
                if (item == null) {
                    continue;
                }

                String safeName = (item.name == null || item.name.trim().isEmpty()
                        || item.name.equalsIgnoreCase("thuốc mới quét")
                        || item.name.equalsIgnoreCase("unknown")
                        || item.name.equalsIgnoreCase("thuốc mới"))
                                ? "" // Để rỗng — UI sẽ chặn lưu nhanh
                                : item.name.trim();
                String safeDosage = (item.dosage == null || item.dosage.trim().isEmpty()
                        || item.dosage.equalsIgnoreCase("unknown")) ? "" : item.dosage.trim();

                List<String> fallbackTimes = new java.util.ArrayList<>();
                if (item.timeDoses != null && !item.timeDoses.isEmpty()) {
                    for (vn.medisense.app.api.ParsedTimeDose td : item.timeDoses) {
                        if (td.time != null && !td.time.trim().isEmpty()) {
                            fallbackTimes.add(td.time.trim());
                        }
                    }
                    if (safeDosage.equals("1 viên") && item.timeDoses.get(0).dose != null
                            && !item.timeDoses.get(0).dose.trim().isEmpty()) {
                        safeDosage = item.timeDoses.get(0).dose.trim();
                    }
                }

                if (fallbackTimes.isEmpty()) {
                    fallbackTimes = getFallbackTimes(item.frequency, item.time);
                }

                int frequencyValue = Math.max(1, fallbackTimes.size());

                ParsedMedicationInfo info = new ParsedMedicationInfo();
                info.name = safeName;
                info.dosage = safeDosage;
                info.frequency = frequencyValue;
                info.times = fallbackTimes;
                info.durationDays = item.durationDays > 0 ? item.durationDays : 1;
                info.notes = item.notes != null ? item.notes : "";
                info.totalQuantity = Math.max(0, item.totalQuantity);
                info.dosagePerIntake = 1;

                info.mealContext = item.mealContext != null ? item.mealContext : "NONE";
                info.offsetMinutes = item.offsetMinutes;
                info.specificShifts = item.specificShifts;

                // ── Fields an toàn y tế mới ───────────────────────────────────
                // confidence từ AI (0.0–1.0), default 1.0 nếu không có
                info.confidence = (item.confidence > 0f) ? item.confidence : 1.0f;
                info.needsReview = item.needsReview;
                info.missingFields = item.missingFields;

                // Nếu tên rỗng → bắt buộc needsReview
                if (safeName.isEmpty()) {
                    info.needsReview = true;
                    if (info.missingFields == null)
                        info.missingFields = new java.util.ArrayList<>();
                    if (!info.missingFields.contains("name"))
                        info.missingFields.add("name");
                }
                // Nếu dosage rỗng → đánh dấu thiếu
                if (safeDosage.isEmpty()) {
                    if (info.missingFields == null)
                        info.missingFields = new java.util.ArrayList<>();
                    if (!info.missingFields.contains("dosage"))
                        info.missingFields.add("dosage");
                }
                // Nếu durationDays không có → đánh dấu thiếu
                if (item.durationDays <= 0) {
                    if (info.missingFields == null)
                        info.missingFields = new java.util.ArrayList<>();
                    if (!info.missingFields.contains("durationDays"))
                        info.missingFields.add("durationDays");
                }
                // Nếu frequency không rõ và không có timeDoses → đánh dấu thiếu
                if ((item.frequency == null || item.frequency.trim().isEmpty())
                        && (item.timeDoses == null || item.timeDoses.isEmpty())) {
                    if (info.missingFields == null)
                        info.missingFields = new java.util.ArrayList<>();
                    if (!info.missingFields.contains("frequency"))
                        info.missingFields.add("frequency");
                }

                if (info.missingFields != null && !info.missingFields.isEmpty()) {
                    info.needsReview = true;
                }

                mappedMedications.add(info);
            }

            if (mappedMedications.isEmpty()) {
                callback.onAnalysisError("Dữ liệu không hợp lệ hoặc thiếu trường bắt buộc");
                return;
            }

            ParsedPrescriptionResult finalResult = new ParsedPrescriptionResult();
            finalResult.diagnosis = rawResult.diagnosis;
            finalResult.doctorAdvice = rawResult.doctorAdvice;
            finalResult.medications = mappedMedications;

            callback.onAnalysisSuccess(finalResult);
        } catch (JsonSyntaxException e) {
            callback.onAnalysisError("Lỗi phân tích JSON: " + e.getMessage());
        } catch (Exception e) {
            callback.onAnalysisError("Lỗi không xác định: " + e.getMessage());
        }
    }

    /**
     * Làm sạch chuỗi JSON, loại bỏ khối code block hoặc tiền tố không cần thiết
     */
    @NonNull
    private String cleanJsonString(@NonNull String jsonText) {
        String cleaned = jsonText.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.replaceAll("(?i)^json", "").trim();
        return cleaned.trim();
    }

    @Nullable
    private String extractJsonObject(@NonNull String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    /**
     * Lấy API key từ BuildConfig
     * Trong production, key sẽ được inject từ local.properties
     */
    @Nullable
    private String getApiKey() {
        try {
            // Sử dụng reflection để lấy GEMINI_API_KEY từ BuildConfig
            // Điều này cho phép key được inject từ local.properties
            String key = (String) BuildConfig.class.getField("GEMINI_API_KEY").get(null);
            if (key == null || key.isEmpty()) {
                return "openrouter-active";
            }
            return key;
        } catch (Exception e) {
            // Fallback: trả về openrouter-active nếu không tìm thấy
            return "openrouter-active";
        }
    }

    /**
     * Lấy danh sách giờ uống dự phòng dựa trên tần suất (frequency) hoặc thời gian
     * (time)
     */
    @NonNull
    private List<String> getFallbackTimes(@Nullable String frequency, @Nullable String time) {
        String timeText = time != null ? time.trim() : "";
        if (!timeText.isEmpty() &&
                !timeText.equalsIgnoreCase("unknown") &&
                !timeText.equalsIgnoreCase("chưa rõ") &&
                !timeText.toLowerCase().contains("chua ro") &&
                !timeText.equalsIgnoreCase("null")) {
            return new ArrayList<>(Collections.singletonList(timeText));
        }

        String freqText = frequency != null ? frequency.toLowerCase() : "";
        if (freqText.contains("2 lan")) {
            return new ArrayList<>(List.of("08:00", "20:00"));
        }
        if (freqText.contains("3 lan")) {
            return new ArrayList<>(List.of("08:00", "14:00", "20:00"));
        }
        if (freqText.contains("1 lan") || freqText.contains("1 time") || freqText.contains("1")) {
            return new ArrayList<>(Collections.singletonList("08:00"));
        }
        return new ArrayList<>(Collections.singletonList("08:00"));
    }

    public void generateTravelPlan(int days, @NonNull String notes, @NonNull List<Medication> medications, @NonNull GeminiCallback callback) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("API Key chưa được cấu hình. Vui lòng kiểm tra local.properties");
            return;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là trợ lý chuẩn bị y tế và đóng gói thuốc du lịch thông minh cho người dùng MediSense.\n");
        prompt.append("Người dùng chuẩn bị đi du lịch:\n");
        prompt.append("- Số ngày đi: ").append(days).append(" ngày\n");
        if (!notes.isEmpty()) {
            prompt.append("- Điểm đến / Ghi chú đặc biệt: ").append(notes).append("\n");
        }
        prompt.append("\nDanh sách thuốc hiện tại của họ:\n");
        for (int i = 0; i < medications.size(); i++) {
            vn.medisense.app.database.Medication med = medications.get(i);
            prompt.append(String.format(java.util.Locale.getDefault(),
                    "%d. %s - Liều dùng mỗi lần: %s (%d viên) - Tần suất: %d lần/ngày - Ghi chú: %s\n",
                    i + 1, med.name, med.dosage, med.dosagePerIntake, med.frequency,
                    (med.instructions != null ? med.instructions : "Không có")));
        }

        prompt.append("\nHãy tạo một kế hoạch chuẩn bị thuốc chi tiết, an toàn và chuyên nghiệp:\n");
        prompt.append("1. **Danh sách đóng gói thuốc chi tiết (Bắt buộc phải tính toán)**:\n");
        prompt.append("   - Tính số viên thuốc tối thiểu cần cho ").append(days).append(" ngày (bằng công thức: số viên mỗi lần * tần suất uống/ngày * số ngày).\n");
        prompt.append("   - Khuyên mang thêm lượng thuốc dự phòng cho 2-3 ngày phòng trường hợp khẩn cấp (safety backup).\n");
        prompt.append("   - Tính toán tổng số viên thực tế cần mang theo (đã bao gồm dự phòng) cho từng loại thuốc.\n");
        prompt.append("2. **Hướng dẫn đóng gói & Bảo quản**:\n");
        prompt.append("   - Hướng dẫn cách phân chia, sắp xếp thuốc (sử dụng hộp chia thuốc, giữ nguyên bao bì gốc, v.v.).\n");
        prompt.append("   - Nhắc nhở quan trọng về việc mang theo đơn thuốc của bác sĩ và giữ thuốc trong hành lý xách tay (không ký gửi).\n");
        prompt.append("3. **Lời khuyên sức khỏe & Xử lý tình huống**:\n");
        prompt.append("   - Lời khuyên cụ thể dựa trên điểm đến/ghi chú đặc biệt (ví dụ: thay đổi múi giờ, khí hậu nóng/lạnh, thuốc bổ sung cần mang theo như Oresol, thuốc chống say xe, v.v.).\n\n");
        prompt.append("Hãy trả lời bằng Tiếng Việt, ngắn gọn, súc tích, định dạng markdown rõ ràng, thân thiện và chuyên nghiệp.");

        GeminiRequest request = new GeminiRequest(prompt.toString());
        apiService.generateContent(apiKey, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String text = response.body().getFirstText();
                    if (text != null && !text.isEmpty()) {
                        callback.onSuccess(text);
                    } else {
                        callback.onError("Không nhận được phản hồi từ AI.");
                    }
                } else {
                    callback.onError("Lỗi API (HTTP " + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                callback.onError("Lỗi kết nối mạng: " + t.getMessage());
            }
        });
    }

    public void assessHealthTrends(@NonNull String promptContent, @NonNull GeminiCallback callback) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("API Key chưa được cấu hình. Vui lòng kiểm tra local.properties");
            return;
        }

        GeminiRequest request = new GeminiRequest(promptContent);
        apiService.generateContent(apiKey, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String text = response.body().getFirstText();
                    if (text != null && !text.isEmpty()) {
                        callback.onSuccess(text);
                    } else {
                        callback.onError("Không nhận được phản hồi từ AI.");
                    }
                } else {
                    callback.onError("Lỗi API (HTTP " + response.code() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                callback.onError("Lỗi kết nối mạng: " + t.getMessage());
            }
        });
    }
}
