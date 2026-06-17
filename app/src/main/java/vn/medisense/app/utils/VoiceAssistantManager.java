package vn.medisense.app.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import vn.medisense.app.api.GeminiApiService;
import vn.medisense.app.api.GeminiRequest;
import vn.medisense.app.api.GeminiResponse;
import vn.medisense.app.api.VoiceCommandResponse;
import vn.medisense.app.BuildConfig;
import vn.medisense.app.database.Medication;
import vn.medisense.app.database.Reminder;
import vn.medisense.app.database.ReminderWithMedication;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * VoiceAssistantManager - Quản lý trợ lý giọng nói
 * 
 * Tính năng:
 * - Speech-to-Text (STT): Chuyển giọng nói thành văn bản
 * - Natural Language Understanding: Phân tích ý định bằng Gemini AI
 * - Text-to-Speech (TTS): Đọc phản hồi cho người dùng
 * - Tự động cập nhật database
 */
public class VoiceAssistantManager {
    
    private static final String TAG = "VoiceAssistantManager";
    private static final String GEMINI_API_KEY = (BuildConfig.GEMINI_API_KEY != null && !BuildConfig.GEMINI_API_KEY.isEmpty()) 
            ? BuildConfig.GEMINI_API_KEY : "openrouter-active";
    
    private final Context context;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;
    private VoiceAssistantCallback callback;
    private final Gson gson;
    private final GeminiApiService apiService;

    private final android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable speechTimeoutRunnable;

    private void cancelSpeechTimeout() {
        if (speechTimeoutRunnable != null) {
            timeoutHandler.removeCallbacks(speechTimeoutRunnable);
            speechTimeoutRunnable = null;
        }
    }

    public VoiceAssistantManager(Context context) {
        this.context = context;
        this.gson = new Gson();
        
        // Khởi tạo Retrofit cho Gemini API qua NetworkModule
        apiService = vn.medisense.app.utils.NetworkModule.getGeminiService();
        
        initializeTTS();
    }

    /**
     * Khởi tạo Text-to-Speech
     */
    private void initializeTTS() {
        textToSpeech = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("vi", "VN"));
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Ngôn ngữ Tiếng Việt không được hỗ trợ, sử dụng mặc định của hệ thống");
                    textToSpeech.setLanguage(Locale.getDefault());
                }
                isTtsReady = true;
                Log.d(TAG, "Khởi tạo TTS thành công");
            } else {
                Log.e(TAG, "Khởi tạo TTS thất bại");
            }
        });
    }

    /**
     * Bắt đầu lắng nghe giọng nói
     */
    public void startListening(VoiceAssistantCallback callback) {
        this.callback = callback;
        
        // Cancel any existing timeout
        cancelSpeechTimeout();
        
        // Setup timeout of 8 seconds to detect if recognizer hangs
        speechTimeoutRunnable = () -> {
            Log.e(TAG, "SpeechRecognizer connection timeout. No callbacks received in 8 seconds.");
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.cancel();
                    speechRecognizer.destroy();
                    speechRecognizer = null;
                } catch (Exception e) {
                    Log.e(TAG, "Error cancelling speech recognizer on timeout", e);
                }
            }
            if (callback != null) {
                callback.onError("Không thể kết nối dịch vụ giọng nói. Vui lòng nhập bằng bàn phím.");
            }
        };
        timeoutHandler.postDelayed(speechTimeoutRunnable, 8000);
        
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    cancelSpeechTimeout();
                    callback.onError("Thiết bị không hỗ trợ nhận dạng giọng nói");
                    return;
                }
                
                // Khởi tạo SpeechRecognizer
                if (speechRecognizer != null) {
                    try {
                        speechRecognizer.destroy();
                    } catch (Exception e) {
                        Log.w(TAG, "Lỗi khi destroy SpeechRecognizer cũ", e);
                    }
                }
                
                // Thử khởi tạo SpeechRecognizer mặc định của hệ thống trước để tối ưu hóa tương thích
                try {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                } catch (Exception e) {
                    Log.w(TAG, "Cannot create default SpeechRecognizer, falling back to Google specifically", e);
                    try {
                        android.content.ComponentName serviceComponent = android.content.ComponentName.unflattenFromString(
                                "com.google.android.googlequicksearchbox/com.google.android.voicesearch.service.SpeechRecognitionService");
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context, serviceComponent);
                    } catch (Exception ex) {
                        Log.e(TAG, "All SpeechRecognizer creation attempts failed", ex);
                        throw ex;
                    }
                }
                
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        Log.d(TAG, "Sẵn sàng nhận dạng giọng nói");
                        cancelSpeechTimeout();
                        callback.onListeningStarted();
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                        Log.d(TAG, "Bắt đầu nói");
                        cancelSpeechTimeout();
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) {
                        // RMS change can still trigger before ready on some SDK versions, but let's not cancel timeout on noise alone
                        callback.onVolumeChanged(rmsdB);
                    }

                    @Override
                    public void onBufferReceived(byte[] buffer) {}

                    @Override
                    public void onEndOfSpeech() {
                        Log.d(TAG, "Kết thúc nói");
                        cancelSpeechTimeout();
                    }

                    @Override
                    public void onError(int error) {
                        cancelSpeechTimeout();
                        String errorMessage = getErrorMessage(error);
                        Log.e(TAG, "Lỗi nhận dạng giọng nói: " + errorMessage + " (mã lỗi: " + error + ")");
                        callback.onError(errorMessage);
                    }

                    @Override
                    public void onResults(Bundle results) {
                        cancelSpeechTimeout();
                        ArrayList<String> matches = results.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String spokenText = matches.get(0);
                            Log.d(TAG, "Văn bản nhận dạng: " + spokenText);
                            callback.onTextRecognized(spokenText);
                            // Xử lý với AI
                            processWithAI(spokenText);
                        } else {
                            callback.onError("Không nhận diện được giọng nói. Vui lòng thử lại.");
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        // Do not cancel timeout here as speech is still in progress
                        ArrayList<String> matches = partialResults.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            callback.onPartialResult(matches.get(0));
                        }
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) {}
                });
                
                // Cấu hình intent
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN");
                intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "vi-VN");
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                
                // Xử lý môi trường ồn - tối ưu hóa thời gian phản hồi (phải sử dụng kiểu Long)
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
                intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
                
                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                cancelSpeechTimeout();
                Log.e(TAG, "Lỗi nghiêm trọng khi khởi chạy SpeechRecognizer", e);
                callback.onError("Không thể khởi chạy bộ nhận dạng: " + e.getMessage());
            }
        });
    }

    /**
     * Dừng lắng nghe
     */
    public void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    /**
     * Xử lý câu hỏi văn bản trực tiếp (fallback từ bàn phím)
     */
    public void processTextQuery(String text, VoiceAssistantCallback callback) {
        this.callback = callback;
        if (text == null || text.trim().isEmpty()) {
            callback.onError("Câu hỏi trống");
            return;
        }
        processWithAI(text.trim());
    }

    /**
     * Xử lý văn bản với Gemini AI.
     * Kiểm tra internet và API key trước khi gọi.
     */
    private void processWithAI(String spokenText) {
        callback.onProcessing();

        // Kiểm tra internet
        if (!vn.medisense.app.utils.NetworkUtils.isNetworkAvailable(context)) {
            String offlineMsg = context.getString(vn.medisense.app.R.string.ai_offline_message);
            speak(offlineMsg);
            callback.onError(offlineMsg);
            return;
        }

        // Kiểm tra API key
        if (GEMINI_API_KEY == null || GEMINI_API_KEY.isEmpty()) {
            String noKeyMsg = context.getString(vn.medisense.app.R.string.ai_not_configured);
            speak(noKeyMsg);
            callback.onError(noKeyMsg);
            return;
        }

        callback.getTodayMedications(medications -> {
            String prompt = buildPrompt(medications, spokenText);

            GeminiRequest request = new GeminiRequest(prompt);
            Call<GeminiResponse> call = apiService.generateContent(GEMINI_API_KEY, request);

            call.enqueue(new Callback<GeminiResponse>() {
                @Override
                public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                    Log.d(TAG, "Phản hồi API: code=" + response.code() + ", thành công=" + response.isSuccessful());
                    if (response.isSuccessful() && response.body() != null) {
                        String responseText = response.body().getFirstText();
                        Log.d(TAG, "Nội dung phản hồi API: " + responseText);
                        if (responseText != null && !responseText.trim().isEmpty()) {
                            VoiceCommandResponse commandResponse = parseResponse(responseText);
                            handleCommandResponse(commandResponse);
                        } else {
                            String errMsg = context.getString(vn.medisense.app.R.string.ai_error_parse);
                            speak(errMsg);
                            callback.onError(errMsg);
                        }
                    } else {
                        // Fallback: trả lời đơn giản nếu API lỗi
                        String fallbackAnswer = "Đã nhận được yêu cầu của bạn. Nếu bạn hỏi về lịch uống thuốc, hãy kiểm tra danh sách thuốc trên màn hình chính.";
                        speak(fallbackAnswer);
                        callback.onQueryResponse(fallbackAnswer);
                    }
                }

                @Override
                public void onFailure(Call<GeminiResponse> call, Throwable t) {
                    Log.e(TAG, "Gọi API trợ lý giọng nói thất bại", t);
                    // Fallback: trả lời đơn giản nếu API lỗi
                    String fallbackAnswer = "Đã nhận được yêu cầu của bạn. Nếu bạn hỏi về lịch uống thuốc, hãy kiểm tra danh sách thuốc trên màn hình chính.";
                    speak(fallbackAnswer);
                    callback.onQueryResponse(fallbackAnswer);
                }
            });
        });
    }

    /**
     * Xây dựng prompt an toàn cho Voice Assistant.
     * Giới hạn intent: QUERY_SCHEDULE | MARK_TAKEN | OPEN_SCREEN | DOSAGE_ADVICE | UNKNOWN.
     * Không cho phép tư vấn liều lượng.
     */
    private String buildPrompt(List<ReminderWithMedication> medications, String spokenText) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Bạn là trợ lý nhắc thuốc. Nhiệm vụ: phân loại yêu cầu người dùng.\n");
        prompt.append("QUAN TRỌNG:\n");
        prompt.append("- KHÔNG tư vấn liều lượng, không khuyên thay đổi thuốc.\n");
        prompt.append("- Nếu người dùng hỏi về liều lượng/có nên uống thêm/đổi thuốc → action=DOSAGE_ADVICE.\n");
        prompt.append("- Chỉ hỗ trợ: hỏi lịch uống thuốc và báo đã uống.\n\n");

        // Danh sách thuốc hôm nay với status mới
        prompt.append("Danh sách thuốc hôm nay:\n");
        if (medications.isEmpty()) {
            prompt.append("- Không có thuốc nào\n");
        } else {
            for (int i = 0; i < medications.size(); i++) {
                ReminderWithMedication item = medications.get(i);
                if (item.reminder == null) continue;
                String time   = String.format("%02d:%02d",
                        getHourFromMillis(item.reminder.reminderTime),
                        getMinuteFromMillis(item.reminder.reminderTime));
                String status = item.reminder.status != null ? item.reminder.status : "PENDING";
                String name   = item.medication != null ? item.medication.name : "Thuốc";
                prompt.append(String.format("%d. %s - %s (%s)\n", i + 1, name, time, status));
            }
        }

        prompt.append("\nNgười dùng vừa nói: \"").append(spokenText).append("\"\n\n");

        prompt.append("Phân loại intent và trả về JSON (không markdown):\n\n");

        prompt.append("1. Hỏi lịch uống thuốc → { \"action\": \"QUERY_SCHEDULE\", \"answer\": \"...\" }\n");
        prompt.append("2. Báo đã uống thuốc cụ thể → { \"action\": \"MARK_TAKEN\", \"medName\": \"tên thuốc\", \"answer\": \"...\" }\n");
        prompt.append("   Lưu ý: medName phải khớp CHÍNH XÁC với tên trong danh sách trên.\n");
        prompt.append("   Nếu không khớp rõ ràng → action=UNKNOWN.\n");
        prompt.append("3. Mở màn hình → { \"action\": \"OPEN_SCREEN\", \"screen\": \"ADD_MEDICATION|HEALTH|EMERGENCY\", \"answer\": \"...\" }\n");
        prompt.append("4. Hỏi về liều lượng/có nên uống thêm/đổi thuốc → { \"action\": \"DOSAGE_ADVICE\", \"answer\": \"Tôi không thể tư vấn về liều lượng. Vui lòng hỏi bác sĩ hoặc dược sĩ.\" }\n");
        prompt.append("5. Không hiểu → { \"action\": \"UNKNOWN\", \"answer\": \"Tôi chưa rõ ý bạn, bạn có thể nói lại không?\" }\n\n");
        prompt.append("Trả về CHÍNH XÁC JSON, không thêm text khác.");

        return prompt.toString();
    }

    /**
     * Parse response từ Gemini AI
     */
    private VoiceCommandResponse parseResponse(String responseText) {
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
            
            return gson.fromJson(cleanedJson, VoiceCommandResponse.class);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Phân tích cú pháp phản hồi thất bại: " + responseText, e);
            VoiceCommandResponse fallback = new VoiceCommandResponse();
            fallback.action = "UNKNOWN";
            fallback.answer = "Xin lỗi, tôi không hiểu yêu cầu của bạn.";
            return fallback;
        }
    }

    /**
     * Xử lý phản hồi từ AI.
     * DOSAGE_ADVICE: chặn, trả lời an toàn.
     * UPDATE: kiểm tra tên thuốc khớp rõ ràng trước khi cập nhật DB.
     */
    private void handleCommandResponse(VoiceCommandResponse response) {
        if (response == null || response.action == null) {
            callback.onUnknownCommand("Xin lỗi, tôi không hiểu yêu cầu của bạn.");
            return;
        }

        String action = normalizeAction(response.action);
        switch (action) {
            case "QUERY_SCHEDULE":
                speak(response.answer);
                callback.onQueryResponse(response.answer);
                break;

            case "MARK_TAKEN":
                if (response.medName == null || response.medName.trim().isEmpty()) {
                    // AI không xác định được tên thuốc → không cập nhật
                    String noMatch = context.getString(
                            vn.medisense.app.R.string.voice_medication_not_found);
                    speak(noMatch);
                    callback.onUnknownCommand(noMatch);
                    return;
                }
                // Kiểm tra tên thuốc có khớp rõ ràng không trước khi cập nhật
                checkAndUpdateMedication(response.medName, response.answer);
                break;

            case "OPEN_SCREEN":
                String screen = response.screen != null ? response.screen.trim() : "";
                if (screen.isEmpty()) {
                    String fallback = context.getString(
                            vn.medisense.app.R.string.voice_open_screen_unknown);
                    speak(fallback);
                    callback.onUnknownCommand(fallback);
                } else {
                    speak(response.answer);
                    callback.onOpenScreen(screen, response.answer);
                }
                break;

            case "DOSAGE_ADVICE":
                // Chặn tư vấn liều lượng — trả lời an toàn
                String safeAnswer = context.getString(
                        vn.medisense.app.R.string.voice_dosage_advice_blocked);
                speak(safeAnswer);
                callback.onUnknownCommand(safeAnswer);
                break;

            case "UNKNOWN":
            default:
                String unknownAnswer = response.answer != null
                        ? response.answer
                        : "Tôi chưa rõ ý bạn, bạn có thể nói lại không?";
                speak(unknownAnswer);
                callback.onUnknownCommand(unknownAnswer);
                break;
        }
    }

    private String normalizeAction(String action) {
        if (action == null) return "UNKNOWN";
        switch (action) {
            case "QUERY":
                return "QUERY_SCHEDULE";
            case "UPDATE":
                return "MARK_TAKEN";
            default:
                return action;
        }
    }

    /**
     * Kiểm tra tên thuốc từ voice có khớp rõ ràng với danh sách hôm nay không.
     * Nếu khớp đúng 1 thuốc → gọi callback.onUpdateRequest() để MainActivity xử lý qua ReminderStatusHelper.
     * Nếu khớp nhiều thuốc tên gần giống → yêu cầu người dùng chọn.
     * Nếu không khớp → thông báo không tìm thấy.
     */
    private void checkAndUpdateMedication(String medName, String aiAnswer) {
        callback.getTodayMedications(medications -> {
            if (medications == null || medications.isEmpty()) {
                String msg = context.getString(vn.medisense.app.R.string.voice_medication_not_found);
                speak(msg);
                callback.onUnknownCommand(msg);
                return;
            }

            // Tìm các thuốc khớp (exact hoặc contains, case-insensitive)
            List<ReminderWithMedication> exactMatches = new java.util.ArrayList<>();
            List<ReminderWithMedication> partialMatches = new java.util.ArrayList<>();
            String lowerMedName = medName.toLowerCase(java.util.Locale.ROOT);

            for (ReminderWithMedication item : medications) {
                if (item.medication == null || item.reminder == null) continue;
                // Chỉ bỏ qua những liều thực sự đã uống (STATUS_TAKEN)
                String status = item.reminder.status != null
                        ? item.reminder.status : vn.medisense.app.database.Reminder.STATUS_PENDING;
                if (vn.medisense.app.database.Reminder.STATUS_TAKEN.equals(status)) {
                    continue;
                }

                String itemName = item.medication.name.toLowerCase(java.util.Locale.ROOT);
                if (itemName.equals(lowerMedName)) {
                    exactMatches.add(item);
                } else if (itemName.contains(lowerMedName) || lowerMedName.contains(itemName)) {
                    partialMatches.add(item);
                }
            }

            if (exactMatches.size() == 1) {
                // Khớp chính xác 1 thuốc → cập nhật
                speak(aiAnswer);
                callback.onUpdateRequest(exactMatches.get(0).medication.name, aiAnswer);

            } else if (exactMatches.size() > 1 || partialMatches.size() > 1) {
                // Nhiều thuốc tên gần giống → yêu cầu người dùng chọn
                String ambiguous = context.getString(
                        vn.medisense.app.R.string.voice_ambiguous_medication);
                speak(ambiguous);
                java.util.List<ReminderWithMedication> options = new java.util.ArrayList<>();
                options.addAll(exactMatches);
                options.addAll(partialMatches);
                callback.onAmbiguousMedications(options, ambiguous);

            } else if (partialMatches.size() == 1) {
                // Khớp một phần với 1 thuốc → cập nhật
                speak(aiAnswer);
                callback.onUpdateRequest(partialMatches.get(0).medication.name, aiAnswer);

            } else {
                // Không tìm thấy
                String notFound = context.getString(
                        vn.medisense.app.R.string.voice_medication_not_found);
                speak(notFound);
                callback.onUnknownCommand(notFound);
            }
        });
    }

    /**
     * Đọc văn bản bằng TTS
     */
    public void speak(String text) {
        if (isTtsReady && textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            Log.d(TAG, "Đang đọc: " + text);
        } else {
            Log.w(TAG, "TTS chưa sẵn sàng");
        }
    }

    /**
     * Dừng TTS
     */
    public void stopSpeaking() {
        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
    }

    /**
     * Giải phóng tài nguyên
     */
    public void destroy() {
        cancelSpeechTimeout();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }

    /**
     * Lấy thông báo lỗi từ error code
     */
    private String getErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Lỗi ghi âm. Vui lòng kiểm tra microphone.";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Lỗi ứng dụng.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Chưa cấp quyền ghi âm.";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Lỗi mạng. Vui lòng kiểm tra kết nối internet.";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Hết thời gian kết nối.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "Không nghe rõ. Vui lòng nói lại.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Hệ thống đang bận. Vui lòng thử lại.";
            case SpeechRecognizer.ERROR_SERVER:
                return "Lỗi server.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "Không phát hiện giọng nói. Vui lòng nói to hơn.";
            default:
                return "Lỗi không xác định.";
        }
    }

    /**
     * Utility methods
     */
    private int getHourFromMillis(long millis) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return cal.get(java.util.Calendar.HOUR_OF_DAY);
    }

    private int getMinuteFromMillis(long millis) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return cal.get(java.util.Calendar.MINUTE);
    }

    /**
     * Callback interface
     */
    public interface VoiceAssistantCallback {
        void onListeningStarted();
        void onVolumeChanged(float volume);
        void onPartialResult(String partialText);
        void onTextRecognized(String text);
        void onProcessing();
        void getTodayMedications(MedicationListCallback callback);
        void onQueryResponse(String answer);
        void onUpdateRequest(String medicationName, String answer);
        void onOpenScreen(String screen, String answer);
        void onAmbiguousMedications(List<ReminderWithMedication> options, String answer);
        void onUnknownCommand(String answer);
        void onError(String errorMessage);
    }

    /**
     * Callback để lấy danh sách thuốc
     */
    public interface MedicationListCallback {
        void onMedicationsLoaded(List<ReminderWithMedication> medications);
    }
}
