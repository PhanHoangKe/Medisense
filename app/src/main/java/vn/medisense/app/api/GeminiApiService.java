package vn.medisense.app.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Retrofit service interface cho Gemini API
 * Định nghĩa endpoint để gọi AI sinh nội dung
 */
public interface GeminiApiService {
    /**
     * Gọi Gemini API để sinh nội dung từ prompt
     * 
     * @param apiKey API key để xác thực
     * @param request Request body chứa prompt
     * @return Response chứa nội dung được sinh ra
     */
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    Call<GeminiResponse> generateContent(
            @Query("key") String apiKey,
            @Body GeminiRequest request);

    /**
     * Gọi Gemini API để sinh nội dung đa phương thức từ prompt và hình ảnh
     *
     * @param apiKey API key để xác thực
     * @param request Request body chứa prompt và hình ảnh
     * @return Response chứa nội dung được sinh ra
     */
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    Call<GeminiResponse> generateVisionContent(
            @Query("key") String apiKey,
            @Body GeminiVisionRequest request);
}
