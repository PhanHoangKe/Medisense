package vn.medisense.app.utils;

import android.util.Log;
import vn.medisense.app.api.GeminiApiService;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton NetworkModule để quản lý các lớp mạng (Retrofit)
 * Dùng chung toàn app, tránh tải lại liên tục gây Memory Leak.
 */
public class NetworkModule {
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/";
    private static GeminiApiService geminiApiService;

    public static synchronized GeminiApiService getGeminiService() {
        if (geminiApiService == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(new OpenRouterInterceptor())
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            geminiApiService = retrofit.create(GeminiApiService.class);
        }
        return geminiApiService;
    }

    /**
     * Interceptor chuyển đổi request/response giữa cấu trúc Gemini và OpenRouter
     */
    private static class OpenRouterInterceptor implements Interceptor {
        private static final String OPENROUTER_KEY = vn.medisense.app.BuildConfig.OPENROUTER_KEY;
        private final Gson gson = new Gson();


        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            
            // Chỉ can thiệp vào các đường dẫn chứa "generateContent"
            if (!originalRequest.url().encodedPath().contains("generateContent")) {
                return chain.proceed(originalRequest);
            }

            String prompt = "";
            try {
                RequestBody body = originalRequest.body();
                if (body != null) {
                    okio.Buffer buffer = new okio.Buffer();
                    body.writeTo(buffer);
                    String bodyStr = buffer.readUtf8();
                    
                    JsonObject geminiReqObj = gson.fromJson(bodyStr, JsonObject.class);
                    if (geminiReqObj != null && geminiReqObj.has("contents")) {
                        JsonArray contents = geminiReqObj.getAsJsonArray("contents");
                        if (contents != null && contents.size() > 0) {
                            JsonObject firstContent = contents.get(0).getAsJsonObject();
                            if (firstContent != null && firstContent.has("parts")) {
                                JsonArray parts = firstContent.getAsJsonArray("parts");
                                if (parts != null && parts.size() > 0) {
                                    prompt = parts.get(0).getAsJsonObject().get("text").getAsString();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("OpenRouterInterceptor", "Lỗi phân tích request Gemini", e);
            }

            // Xây dựng request body cho OpenRouter
            JsonObject openRouterReq = new JsonObject();
            openRouterReq.addProperty("model", "google/gemini-2.5-flash");
            
            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            messages.add(message);
            openRouterReq.add("messages", messages);

            String openRouterReqJson = gson.toJson(openRouterReq);

            // Tạo request mới trỏ tới OpenRouter
            Request newRequest = originalRequest.newBuilder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .header("Authorization", "Bearer " + OPENROUTER_KEY)
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://medisense.vn")
                    .header("X-Title", "MediSense")
                    .post(RequestBody.create(MediaType.parse("application/json"), openRouterReqJson))
                    .build();

            // Thực thi request
            Response response = chain.proceed(newRequest);
            if (!response.isSuccessful()) {
                return response;
            }

            // Phân tích response từ OpenRouter
            String responseContent = "";
            try {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    String responseBodyStr = responseBody.string();
                    JsonObject openRouterRespObj = gson.fromJson(responseBodyStr, JsonObject.class);
                    if (openRouterRespObj != null && openRouterRespObj.has("choices")) {
                        JsonArray choices = openRouterRespObj.getAsJsonArray("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonObject firstChoice = choices.get(0).getAsJsonObject();
                            if (firstChoice != null && firstChoice.has("message")) {
                                JsonObject msgObj = firstChoice.getAsJsonObject("message");
                                if (msgObj != null && msgObj.has("content")) {
                                    responseContent = msgObj.get("content").getAsString();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("OpenRouterInterceptor", "Lỗi phân tích response OpenRouter", e);
            }

            // Xây dựng response body tương thích với GeminiResponse
            JsonObject geminiResp = new JsonObject();
            JsonArray candidates = new JsonArray();
            JsonObject candidate = new JsonObject();
            JsonObject content = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            
            part.addProperty("text", responseContent);
            parts.add(part);
            content.add("parts", parts);
            candidate.add("content", content);
            candidates.add(candidate);
            geminiResp.add("candidates", candidates);

            String geminiRespJson = gson.toJson(geminiResp);

            // Trả về response giả lập tương thích với cấu trúc của GeminiResponse
            return response.newBuilder()
                    .body(ResponseBody.create(MediaType.parse("application/json"), geminiRespJson))
                    .build();
        }
    }
}

